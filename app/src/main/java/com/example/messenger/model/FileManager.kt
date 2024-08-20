package com.example.messenger.model

import android.content.Context
import java.io.File

class FileManager(private val context: Context) {

    // Получаем директорию для хранения файлов сообщений
    private fun getMessageDirectory(dirId: Int): File {
        val dir = File(context.filesDir, "conversations/$dirId") // todo как-то разделить на dialog и group
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    // Сохранение файла в директорию сообщений
    fun saveFile(dialogId: Int, fileName: String, fileData: ByteArray): File {
        val dir = getMessageDirectory(dialogId)
        val file = File(dir, fileName)
        file.writeBytes(fileData)
        return file
    }

    // Проверка на существование файла
    fun isExist(dialogId: Int, fileName: String): Boolean {
        val dir = getMessageDirectory(dialogId)
        val file = File(dir, fileName)
        return file.exists()
    }

    // Получение списка всех файлов в директории сообщений
    fun getAllFiles(dialogId: Int): List<File> {
        val dir = getMessageDirectory(dialogId)
        return dir.listFiles()?.toList() ?: emptyList()
    }

    // Удаление файла
    fun deleteFile(file: File): Boolean {
        return file.delete()
    }

    // Удаление всех файлов, не используемых в новых сообщениях
    fun cleanupUnusedFiles(dialogId: Int, usedFiles: Set<String>) {
        val allFiles = getAllFiles(dialogId)
        allFiles.forEach { file ->
            if (file.name !in usedFiles) {
                deleteFile(file)
            }
        }
    }
}