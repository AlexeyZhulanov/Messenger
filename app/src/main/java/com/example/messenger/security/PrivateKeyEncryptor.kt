package com.example.messenger.security

import android.util.Base64
import java.security.Key
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.spec.PKCS8EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class PrivateKeyEncryptor(private val masterKey: String) {

    companion object {
        private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128 // Длина тега аутентификации в битах
        private const val GCM_IV_LENGTH = 12   // Длина IV (Initialization Vector) в байтах
    }

    private val secretKey: Key by lazy {
        // Хешируем masterKey с помощью SHA-256
        val keyBytes = hashWithSHA256(masterKey.toByteArray(Charsets.UTF_8))
        SecretKeySpec(keyBytes, "AES")
    }

    /**
     * Хеширует данные с помощью SHA-256.
     */
    private fun hashWithSHA256(data: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(data)
    }

    /**
     * Шифрует приватный ключ [privateKey] с использованием master-key.
     */
    fun encryptPrivateKey(privateKey: PrivateKey): String {
        val privateKeyBytes = privateKey.encoded
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)

        // Генерируем случайный IV (Initialization Vector)
        val iv = ByteArray(GCM_IV_LENGTH)
        SecureRandom().nextBytes(iv)

        // Инициализируем шифрование
        val parameterSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec)

        // Шифруем данные
        val cipherBytes = cipher.doFinal(privateKeyBytes)

        // Объединяем IV и зашифрованные данные
        val result = ByteArray(iv.size + cipherBytes.size)
        System.arraycopy(iv, 0, result, 0, iv.size)
        System.arraycopy(cipherBytes, 0, result, iv.size, cipherBytes.size)

        // Возвращаем результат в Base64
        return Base64.encodeToString(result, Base64.NO_WRAP)
    }

    /**
     * Расшифровывает приватный ключ из [encryptedKey] с использованием master-key.
     */
    fun decryptPrivateKey(encryptedKey: String): PrivateKey {
        val encryptedData = Base64.decode(encryptedKey, Base64.NO_WRAP)

        // Извлекаем IV и зашифрованные данные
        val iv = ByteArray(GCM_IV_LENGTH)
        System.arraycopy(encryptedData, 0, iv, 0, iv.size)

        val cipherBytes = ByteArray(encryptedData.size - iv.size)
        System.arraycopy(encryptedData, iv.size, cipherBytes, 0, cipherBytes.size)

        // Инициализируем расшифровку
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        val parameterSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec)

        // Расшифровываем данные
        val decryptedBytes = cipher.doFinal(cipherBytes)

        // Преобразуем расшифрованные данные в приватный ключ
        val keySpec = PKCS8EncodedKeySpec(decryptedBytes)
        return KeyFactory.getInstance("RSA").generatePrivate(keySpec)
    }
}