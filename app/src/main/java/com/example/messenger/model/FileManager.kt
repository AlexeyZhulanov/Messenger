package com.example.messenger.model

import android.content.Context
import java.io.File

class FileManager(private val context: Context) {

    // Получаем директорию для хранения файлов сообщений
    private fun getMessageDirectory(): File {
        return getOrCreateDirectory("conversations")
    }

    // Получаем директорию для хранения файлов неотправленных сообщений
    private fun getUnsentMessageDirectory(): File {
        return getOrCreateDirectory("unsent_messages")
    }

    private fun getAvatarsDirectory(): File {
        return getOrCreateDirectory("avatars")
    }

    private fun getNewsDirectory(): File {
        return getOrCreateDirectory("news")
    }

    // Вспомогательная функция для создания директории
    private fun getOrCreateDirectory(name: String): File {
        val dir = File(context.filesDir, name)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    // Сохранение файла в указанную директорию
    private fun saveFile(directory: File, fileName: String, fileData: ByteArray): File {
        val file = File(directory, fileName)
        file.writeBytes(fileData)
        return file
    }

    fun saveMessageFile(fileName: String, fileData: ByteArray): File {
        return saveFile(getMessageDirectory(), fileName, fileData)
    }

    fun saveUnsentFile(fileName: String, fileData: ByteArray): File {
        return saveFile(getUnsentMessageDirectory(), fileName, fileData)
    }

    fun saveAvatarFile(fileName: String, fileData: ByteArray): File {
        return saveFile(getAvatarsDirectory(), fileName, fileData)
    }

    fun saveNewsFile(fileName: String, fileData: ByteArray): File {
        return saveFile(getNewsDirectory(), fileName, fileData)
    }

    // Проверка на существование файла
    fun isExistMessage(fileName: String): Boolean {
        val dir = getMessageDirectory()
        val file = File(dir, fileName)
        return file.exists()
    }

    fun isExistUnsentMessage(fileName: String): Boolean {
        val dir = getUnsentMessageDirectory()
        val file = File(dir, fileName)
        return file.exists()
    }

    fun isExistAvatar(fileName: String): Boolean {
        val dir = getAvatarsDirectory()
        val file = File(dir, fileName)
        return file.exists()
    }

    fun isExistNews(fileName: String): Boolean {
        val dir = getNewsDirectory()
        val file = File(dir, fileName)
        return file.exists()
    }

    // Удаление файла
    private fun deleteFile(file: File): Boolean {
        return file.delete()
    }

    fun deleteFilesMessage(files: List<String>) {
        val dir = getMessageDirectory()
        files.forEach { filename ->
            deleteFile(File(dir, filename))
        }
    }

    fun deleteFilesUnsent(files: List<String>) {
        val dir = getUnsentMessageDirectory()
        files.forEach { filename ->
            deleteFile(File(dir, filename))
        }
    }

    private fun cleanupDirectory(directory: File, usedFiles: Set<String>) {
        val allFiles = directory.listFiles()?.toList() ?: emptyList()
        allFiles.forEach { file ->
            if (file.name !in usedFiles) {
                deleteFile(file)
            }
        }
    }

    fun cleanupUnusedMessageFiles(usedFiles: Set<String>) {
        cleanupDirectory(getMessageDirectory(), usedFiles)
    }

    fun cleanupUnusedUnsentFiles(usedFiles: Set<String>) {
        cleanupDirectory(getUnsentMessageDirectory(), usedFiles)
    }

    fun cleanupAvatarFiles(usedFiles: Set<String>) {
        cleanupDirectory(getAvatarsDirectory(), usedFiles)
    }

    fun cleanupNewsFiles(usedFiles: Set<String>) {
        cleanupDirectory(getNewsDirectory(), usedFiles)
    }

    fun getMessageFilePath(fileName: String): String {
        return File(getMessageDirectory(), fileName).absolutePath
    }

    fun getUnsentFilePath(fileName: String): String {
        return File(getUnsentMessageDirectory(), fileName).absolutePath
    }

    fun getAvatarFilePath(fileName: String): String {
        return File(getAvatarsDirectory(), fileName).absolutePath
    }

    fun getNewsFilePath(fileName: String): String {
        return File(getNewsDirectory(), fileName).absolutePath
    }

    // Получение файла по filePath
    fun getFileFromPath(filePath: String): File? {
        val file = File(filePath)
        return if (file.exists()) file else null
    }
}