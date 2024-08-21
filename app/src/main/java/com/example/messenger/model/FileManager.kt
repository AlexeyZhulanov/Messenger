package com.example.messenger.model

import android.content.Context
import java.io.File

class FileManager(private val context: Context) {

    // Получаем директорию для хранения файлов сообщений
    private fun getMessageDirectory(): File {
        val dir = File(context.filesDir, "conversations")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    // Сохранение файла в директорию сообщений
    fun saveFile(fileName: String, fileData: ByteArray): File {
        val dir = getMessageDirectory()
        val file = File(dir, fileName)
        file.writeBytes(fileData)
        return file
    }

    // Проверка на существование файла
    fun isExist(fileName: String): Boolean {
        val dir = getMessageDirectory()
        val file = File(dir, fileName)
        return file.exists()
    }

    // Получение списка всех файлов в директории сообщений
    fun getAllFiles(): List<File> {
        val dir = getMessageDirectory()
        return dir.listFiles()?.toList() ?: emptyList()
    }

    // Удаление файла
    fun deleteFile(file: File): Boolean {
        return file.delete()
    }

    // Удаление всех файлов, не используемых в новых сообщениях
    fun cleanupUnusedFiles(usedFiles: Set<String>) {
        val allFiles = getAllFiles()
        allFiles.forEach { file ->
            if (file.name !in usedFiles) {
                deleteFile(file)
            }
        }
    }
    // Получение пути к определенному файлу
    fun getFilePath(fileName: String): String {
        val dir = getMessageDirectory()
        val file = File(dir, fileName)
        return file.absolutePath
    }
}