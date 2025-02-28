package com.example.messenger.security

import com.google.crypto.tink.Aead
import java.security.PrivateKey
import android.util.Base64
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import java.security.KeyFactory
import java.security.spec.PKCS8EncodedKeySpec

class PrivateKeyEncryptor(private val masterKey: ByteArray) {

    init {
        // Register all AEAD key types with the Tink runtime.
        AeadConfig.register()
    }

    private val aead: Aead = KeysetHandle.generateNew(KeyTemplates.get("AES256_GCM")).getPrimitive(
        RegistryConfiguration.get(), Aead::class.java)

    /**
     * Шифрует приватный ключ [privateKey] с использованием master-key.
     */
    fun encryptPrivateKey(privateKey: PrivateKey): String {
        val privateKeyBytes = privateKey.encoded
        val cipherBytes = aead.encrypt(privateKeyBytes, masterKey)
        return Base64.encodeToString(cipherBytes, Base64.NO_WRAP)
    }

    /**
     * Расшифровывает приватный ключ из [encryptedKey] с использованием master-key.
     */
    fun decryptPrivateKey(encryptedKey: String): PrivateKey {
        val cipherBytes = Base64.decode(encryptedKey, Base64.NO_WRAP)
        val decryptedBytes = aead.decrypt(cipherBytes, masterKey)
        val keySpec = PKCS8EncodedKeySpec(decryptedBytes)
        return KeyFactory.getInstance("RSA").generatePrivate(keySpec)
    }
}
