package com.example.messenger.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.google.crypto.tink.Aead
import com.google.crypto.tink.integration.android.AndroidKeystoreKmsClient
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.util.Arrays
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey


/**
 * Этот класс генерирует симметричный AES‑GCM ключ для каждого чата и хранит его в Android Keystore.
 * Он также предоставляет методы для «обёртывания» (wrap) и «развёртывания» (unwrap) ключа с использованием асимметричного шифрования.
 */
class ChatKeyManager {

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_URI_PREFIX = "android-keystore://"
        private const val ASYMMETRIC_TRANSFORMATION = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding"
    }

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    /**
     * Генерирует и сохраняет симметричный AES‑GCM ключ для чата, если ключа ещё нет.
     * [chatId] и [chatType] (например, "dialog" или "group") используются для формирования alias.
     */
    fun generateAndStoreChatKey(chatId: String, chatType: String) {
        val alias = "chat_${chatId}_$chatType"
        if (!keyStore.containsAlias(alias)) {
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            val parameterSpec = KeyGenParameterSpec.Builder(alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            keyGenerator.init(parameterSpec)
            keyGenerator.generateKey()
        }
    }

    /**
     * Возвращает Aead для заданного чата, используя Android Keystore.
     */
    fun getAead(chatId: String, chatType: String): Aead {
        val alias = "chat_${chatId}_$chatType"
        val keyUri = "$KEY_URI_PREFIX$alias"

        val kmsClient = AndroidKeystoreKmsClient()

        return kmsClient.getAead(keyUri)
    }

    /**
     * Генерирует пару ключей (публичный и приватный) и сохраняет их в Android Keystore.
     * [userId] — уникальный идентификатор для ключей.
     */
    fun generateKeyPair(userId: Int) {
        val alias = "user_${userId}_key_pair"

        val keyPairGenerator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_RSA,
            ANDROID_KEYSTORE
        )

        val keySpec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
            .build()

        keyPairGenerator.initialize(keySpec)
        keyPairGenerator.generateKeyPair()
    }

    /**
     * Возвращает публичный ключ для заданного [userId].
     */
    fun getPublicKey(userId: Int): PublicKey? {
        val alias = "user_${userId}_key_pair"

        return if (keyStore.containsAlias(alias)) {
            val entry = keyStore.getEntry(alias, null) as KeyStore.PrivateKeyEntry
            entry.certificate.publicKey
        } else {
            null
        }
    }

    /**
     * Возвращает приватный ключ для заданного [userId].
     * Этот метод предназначен для внутреннего использования.
     */
    fun getPrivateKey(userId: Int): PrivateKey? {
        val alias = "user_${userId}_key_pair"

        return if (keyStore.containsAlias(alias)) {
            val entry = keyStore.getEntry(alias, null) as KeyStore.PrivateKeyEntry
            entry.privateKey
        } else {
            null
        }
    }

    /**
     * Сохраняет симметричный ключ в Android Keystore.
     */
    private fun storeChatKey(alias: String, secretKey: SecretKey) {
        val entry = KeyStore.SecretKeyEntry(secretKey)
        keyStore.setEntry(alias, entry, null)
    }

    /**
     * Возвращает симметричный ключ для заданного чата, если он существует.
     */
    private fun getChatKey(chatId: String, chatType: String): SecretKey? {
        val alias = "chat_${chatId}_$chatType"
        return if (keyStore.containsAlias(alias)) keyStore.getKey(alias, null) as SecretKey else null
    }

    /**
     * «Заворачивает» (wrap) симметричный ключ для заданного чата с использованием публичного ключа получателя.
     * Это позволяет безопасно передать ключ.
     */
    fun wrapChatKeyForRecipient(chatId: String, chatType: String, recipientPublicKey: PublicKey): ByteArray {
        val chatKey = getChatKey(chatId, chatType) ?: throw IllegalStateException("Chat key not found")

        val cipher = Cipher.getInstance(ASYMMETRIC_TRANSFORMATION)
        cipher.init(Cipher.WRAP_MODE, recipientPublicKey)
        val wrappedKey = cipher.wrap(chatKey)

        // Очищаем ключ из памяти (дополнительная мера безопасности)
        Arrays.fill(chatKey.encoded, 0.toByte())

        return wrappedKey
    }

    /**
     * «Разворачивает» (unwrap) симметричный ключ из [wrappedKey] с использованием приватного ключа получателя
     * и сохраняет его в Android Keystore.
     */
    fun unwrapChatKey(wrappedKey: ByteArray, chatId: String, chatType: String, userId: Int) {
        val alias = "chat_${chatId}_$chatType"
        val privateKey = getPrivateKey(userId) ?: throw IllegalStateException("Private key not found")

        val cipher = Cipher.getInstance(ASYMMETRIC_TRANSFORMATION)
        cipher.init(Cipher.UNWRAP_MODE, privateKey)
        val chatKey = cipher.unwrap(wrappedKey, KeyProperties.KEY_ALGORITHM_AES, Cipher.SECRET_KEY) as SecretKey
        storeChatKey(alias, chatKey)
        // Очищаем ключ из памяти (дополнительная мера безопасности)
        Arrays.fill(chatKey.encoded, 0.toByte())
    }

}
