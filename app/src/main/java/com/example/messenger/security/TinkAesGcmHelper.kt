package com.example.messenger.security

import android.util.Base64
import com.google.crypto.tink.Aead
import java.io.File
import java.io.FileOutputStream


/**
 * Класс для шифрования и расшифровки сообщений и файлов, используя переданный [Aead].
 */
class TinkAesGcmHelper(private val aead: Aead) {

    /**
     * Шифрует строку [plainText] с дополнительными данными [associatedData] (необязательно)
     * и возвращает зашифрованный текст в формате Base64.
     */
    fun encryptText(plainText: String, associatedData: String = ""): String {
        val plaintextBytes = plainText.toByteArray(Charsets.UTF_8)
        val adBytes = associatedData.toByteArray(Charsets.UTF_8)
        val cipherBytes = aead.encrypt(plaintextBytes, adBytes)
        return Base64.encodeToString(cipherBytes, Base64.NO_WRAP)
    }

    /**
     * Расшифровывает строку [cipherText] (формат Base64) с дополнительными данными [associatedData]
     * и возвращает исходный текст.
     */
    fun decryptText(cipherText: String, associatedData: String = ""): String {
        val cipherBytes = Base64.decode(cipherText, Base64.NO_WRAP)
        val adBytes = associatedData.toByteArray(Charsets.UTF_8)
        val decryptedBytes = aead.decrypt(cipherBytes, adBytes)
        return String(decryptedBytes, Charsets.UTF_8)
    }

    /**
     * Шифрует файл [inputFile] и записывает результат в [outputFile].
     * [associatedData] – дополнительные данные (необязательно).
     */
    fun encryptFile(inputFile: File, outputFile: File, associatedData: ByteArray = ByteArray(0)) {
        val inputBytes = inputFile.readBytes()
        val cipherBytes = aead.encrypt(inputBytes, associatedData)
        FileOutputStream(outputFile).use { it.write(cipherBytes) }
    }

    /**
     * Расшифровывает файл [inputFile] и записывает результат в [outputFile].
     * [associatedData] – дополнительные данные (необязательно).
     */
    fun decryptFile(inputFile: File, outputFile: File, associatedData: ByteArray = ByteArray(0)) {
        val cipherBytes = inputFile.readBytes()
        val plainBytes = aead.decrypt(cipherBytes, associatedData)
        FileOutputStream(outputFile).use { it.write(plainBytes) }
    }
}
