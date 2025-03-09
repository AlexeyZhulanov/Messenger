package com.example.messenger.security

import android.security.keystore.KeyProperties
import android.security.keystore.KeyProtection
import android.util.Log
import com.google.crypto.tink.Aead
import com.google.crypto.tink.integration.android.AndroidKeystoreKmsClient
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.PrivateKey
import java.security.PublicKey
import java.security.cert.X509Certificate
import java.security.spec.MGF1ParameterSpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource


/**
 * Этот класс генерирует симметричный AES‑GCM ключ для каждого чата и хранит его в Android Keystore.
 * Он также предоставляет методы для «обёртывания» (wrap) и «развёртывания» (unwrap) ключа с использованием асимметричного шифрования.
 */
class ChatKeyManager {

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_URI_PREFIX = "android-keystore://"
        private const val ASYMMETRIC_TRANSFORMATION = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding"

        fun containsPrivateKey(userId: Int): Boolean {
            val alias = "user_${userId}_key_pair"
            val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            return keyStore.containsAlias(alias)
        }
    }

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private val oaepParameterSpec = OAEPParameterSpec(
        "SHA-256", // Основная хеш-функция
        "MGF1",    // Алгоритм MGF1
        MGF1ParameterSpec.SHA1, // Хеш-функция для MGF1
        PSource.PSpecified.DEFAULT // Параметр label (не используется)
    )

    /**
     * Генерирует AES‑GCM ключ для чата.
     */
    fun generateChatKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES)
        keyGenerator.init(256) // 256-битный ключ
        return keyGenerator.generateKey()
    }

    /**
     * Возвращает Aead для заданного чата, используя Android Keystore.
     * Если ключ для указанного alias отсутствует, возвращает null.
     */
    fun getAead(chatId: Int, chatType: String): Aead? {
        val alias = "chat_${chatId}_$chatType"

        if (!keyStore.containsAlias(alias)) {
            return null
        }
        val keyUri = "$KEY_URI_PREFIX$alias"
        val kmsClient = AndroidKeystoreKmsClient()

        return kmsClient.getAead(keyUri)
    }

    /**
     * Генерирует пару ключей (публичный и приватный).
     * Они не сохраняются в Android Keystore, так как приватный ключ не получится оттуда достать.
     */
    fun generateKeyPair(): Pair<PublicKey, PrivateKey> {
        val keyPairGenerator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA)
        keyPairGenerator.initialize(2048) // Размер ключа
        val keyPair = keyPairGenerator.generateKeyPair()
        return keyPair.public to keyPair.private
    }

    /**
     * Возвращает приватный ключ для заданного [userId].
     */
    private fun getPrivateKey(userId: Int): PrivateKey? {
        val alias = "user_${userId}_key_pair"

        return if (keyStore.containsAlias(alias)) {
            val entry = keyStore.getEntry(alias, null) as KeyStore.PrivateKeyEntry
            entry.privateKey
        } else {
            null
        }
    }

    /**
     * Сохраняет существующий приватный ключ в Android Keystore.
     * [userId] — уникальный идентификатор для ключей.
     * [privateKey] — приватный ключ, который нужно сохранить.
     * [certificate] - сертификат, без которого не получится сохранить ключ.
     */
    fun savePrivateKey(userId: Int, privateKey: PrivateKey, certificate: X509Certificate) {
        val alias = "user_${userId}_key_pair"
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)

        // Создаем защиту для ключа
        val keyProtection = KeyProtection.Builder(KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
            .setUserAuthenticationRequired(false) // Не требует биометрию при использовании
            .build()

        // Создаем запись для приватного ключа с сертификатом
        val privateKeyEntry = KeyStore.PrivateKeyEntry(privateKey, arrayOf(certificate))

        // Сохраняем запись в Keystore
        keyStore.setEntry(alias, privateKeyEntry, keyProtection)
    }

    /**
     * Сохраняет симметричный ключ в Android Keystore.
     */
    fun storeChatKey(chatId: Int, chatType: String, secretKey: SecretKey) {
        try {
            val alias = "chat_${chatId}_$chatType"
            if (keyStore.containsAlias(alias)) {
                keyStore.deleteEntry(alias)
            }

            val keyProtection = KeyProtection.Builder(
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(false) // Не требует биометрию при использовании
                .build()

            val entry = KeyStore.SecretKeyEntry(secretKey)

            keyStore.setEntry(alias, entry, keyProtection)

        } catch (e: KeyStoreException) {
            Log.e("ChatKeyManager", "Failed to store symmetric key: ${e.message}")
            throw e
        }
    }

    /**
     * Возвращает симметричный ключ для заданного чата, если он существует.
     */
    private fun getChatKey(chatId: Int, chatType: String): SecretKey? {
        val alias = "chat_${chatId}_$chatType"
        return if (keyStore.containsAlias(alias)) keyStore.getKey(alias, null) as SecretKey else null
    }

    /**
     * «Заворачивает» (wrap) симметричный ключ для заданного чата с использованием публичного ключа получателя.
     * Функция выполняется вне Android Keystore, так как оттуда невозможно явно достать симметричный ключ.
     */
    fun wrapChatKeyForRecipient(secretKey: SecretKey, recipientPublicKey: PublicKey): ByteArray {
        val cipher = Cipher.getInstance(ASYMMETRIC_TRANSFORMATION)
        cipher.init(Cipher.WRAP_MODE, recipientPublicKey, oaepParameterSpec)
        return cipher.wrap(secretKey)
    }

    /**
     * «Разворачивает» (unwrap) симметричный ключ из [wrappedKey] с использованием приватного ключа получателя
     * и возвращает его.
     */
    fun unwrapChatKeyForGet(wrappedKey: ByteArray, userId: Int): SecretKey {
        val privateKey = getPrivateKey(userId) ?: throw IllegalStateException("Private key not found")

        val cipher = Cipher.getInstance(ASYMMETRIC_TRANSFORMATION)
        cipher.init(Cipher.UNWRAP_MODE, privateKey, oaepParameterSpec)

        return cipher.unwrap(wrappedKey, KeyProperties.KEY_ALGORITHM_AES, Cipher.SECRET_KEY) as SecretKey
    }

    /**
     * «Разворачивает» (unwrap) симметричный ключ из [wrappedKey] с использованием приватного ключа получателя
     * и сохраняет его в Android Keystore.
     */
    fun unwrapChatKeyForSave(wrappedKey: ByteArray, chatId: Int, chatType: String, userId: Int) {
        val privateKey = getPrivateKey(userId) ?: throw IllegalStateException("Private key not found")
        Log.d("testChatKeyManager", "Private key algorithm: ${privateKey.algorithm}")
        Log.d("testChatKeyManager", "wrappedKey size: ${wrappedKey.size}")

        val cipher = Cipher.getInstance(ASYMMETRIC_TRANSFORMATION)
        cipher.init(Cipher.UNWRAP_MODE, privateKey, oaepParameterSpec)
        val chatKey = cipher.unwrap(wrappedKey, KeyProperties.KEY_ALGORITHM_AES, Cipher.SECRET_KEY) as SecretKey
        storeChatKey(chatId, chatType, chatKey)
    }

    fun unwrapNewsKey(wrappedKey: ByteArray, chatId: Int, chatType: String, userId: Int) {
        val privateKey = getPrivateKey(userId) ?: throw IllegalStateException("Private key not found")

        // Логирование для диагностики
        Log.d("testChatKeyManager", "Wrapped key: ${wrappedKey.joinToString("") { "%02x".format(it) }}")
        Log.d("testChatKeyManager", "Private key algorithm: ${privateKey.algorithm}")

        try {

            // Инициализируем Cipher с параметрами OAEP
            val cipher = Cipher.getInstance(ASYMMETRIC_TRANSFORMATION)
            cipher.init(Cipher.UNWRAP_MODE, privateKey, oaepParameterSpec)

            // Разворачиваем ключ
            val chatKey = cipher.unwrap(wrappedKey, KeyProperties.KEY_ALGORITHM_AES, Cipher.SECRET_KEY) as SecretKey
            storeChatKey(chatId, chatType, chatKey)
        } catch (e: Exception) {
            Log.e("testChatKeyManager", "Failed to unwrap key: ${e.message}")
            throw e
        }
    }
}
