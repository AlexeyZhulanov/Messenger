package com.example.messenger

import android.content.Context
import android.media.MediaMetadataRetriever
import androidx.lifecycle.ViewModel
import com.example.messenger.di.IoDispatcher
import com.example.messenger.model.ChatSettings
import com.example.messenger.model.FileManager
import com.example.messenger.model.MessengerService
import com.example.messenger.model.RetrofitService
import com.example.messenger.model.WebSocketService
import com.luck.picture.lib.config.PictureMimeType
import com.luck.picture.lib.entity.LocalMedia
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

abstract class BaseInfoViewModel(
    protected val messengerService: MessengerService,
    protected val retrofitService: RetrofitService,
    protected val fileManager: FileManager,
    protected val webSocketService: WebSocketService,
    @IoDispatcher protected val ioDispatcher: CoroutineDispatcher
) : ViewModel() {
    protected var convId: Int = -1
    private var isGroup: Int = 0
    private val tempFiles = mutableSetOf<String>()

    fun fManagerIsExistAvatar(fileName: String): Boolean {
        return fileManager.isExistAvatar(fileName)
    }

    fun fManagerGetAvatarPath(fileName: String): String {
        return fileManager.getAvatarFilePath(fileName)
    }

    suspend fun fManagerSaveAvatar(fileName: String, fileData: ByteArray) = withContext(ioDispatcher) {
        fileManager.saveAvatarFile(fileName, fileData)
    }

    suspend fun downloadAvatar(context: Context, filename: String): String {
        return retrofitService.downloadAvatar(context, filename)
    }

    suspend fun isNotificationsEnabled(): Boolean {
        val type = isGroup == 1
        return messengerService.isNotificationsEnabled(convId, type)
    }

    suspend fun turnNotifications() {
        val type = isGroup == 1
        val isEnabled = messengerService.isNotificationsEnabled(convId, type)
        if(isEnabled) messengerService.insertChatSettings(ChatSettings(convId, type))
        else messengerService.deleteChatSettings(convId, type)
    }

    suspend fun toggleCanDeleteDialog() {
        if(isGroup == 0) retrofitService.toggleDialogCanDelete(convId)
        else retrofitService.toggleGroupCanDelete(convId)
    }

    suspend fun getMediaPreviews(page: Int): List<String>? {
        return retrofitService.getMedias(convId, page, isGroup)
    }

    suspend fun getFiles(page: Int): List<String>? {
        return retrofitService.getFiles(convId, page, isGroup)
    }

    suspend fun getAudios(page: Int): List<String>? {
        return retrofitService.getAudios(convId, page, isGroup)
    }

    suspend fun getPreview(context: Context, filename: String): String {
        return retrofitService.getMediaPreview(context, convId, filename, isGroup)
    }

    suspend fun deleteAllMessages() {
        if(isGroup == 0) retrofitService.deleteDialogMessages(convId)
        else retrofitService.deleteGroupMessagesAll(convId)
        //_deleteState.value = 1 // todo подумать над этим
    }

    suspend fun deleteConv() {
        if(isGroup == 0) retrofitService.deleteDialog(convId) else retrofitService.deleteGroup(convId)
    }

    suspend fun updateAutoDeleteInterval(interval: Int) {
        if(isGroup == 0) retrofitService.updateAutoDeleteInterval(convId, interval)
        else retrofitService.updateGroupAutoDeleteInterval(convId, interval)
    }

    fun addTempFile(filename: String) = tempFiles.add(filename)

    fun clearTempFiles() {
        fileManager.deleteFilesMessage(tempFiles.toList())
        tempFiles.clear()
    }

    fun fManagerIsExist(fileName: String): Boolean {
        return fileManager.isExistMessage(fileName)
    }

    fun fManagerGetFilePath(fileName: String): String {
        return fileManager.getMessageFilePath(fileName)
    }

    suspend fun fManagerSaveFile(fileName: String, fileData: ByteArray) = withContext(ioDispatcher) {
        fileManager.saveMessageFile(fileName, fileData)
    }

    suspend fun downloadFile(context: Context, folder: String, filename: String): String {
        return retrofitService.downloadFile(context, folder, convId, filename, isGroup)
    }

    fun downloadFileJava(context: Context, folder: String, filename: String): String {
        return runBlocking {
            retrofitService.downloadFile(context, folder, convId, filename, isGroup)
        }
    }

    fun fManagerIsExistJava(filename: String): Boolean {
        return fileManager.isExistMessage(filename)
    }

    fun fManagerGetFilePathJava(fileName: String): String {
        return fileManager.getMessageFilePath(fileName)
    }

    fun fManagerSaveFileJava(fileName: String, fileData: ByteArray) {
        fileManager.saveMessageFile(fileName, fileData)
    }

    fun formatFileSize(size: Long): String {
        val kb = 1024.0
        val mb = kb * 1024
        return when {
            size < kb -> "$size B"
            size < mb -> "${(size / kb).toInt()} KB"
            else -> String.format(Locale.ROOT, "%.2f MB", size / mb)
        }
    }

    fun formatTime(milliseconds: Long): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(milliseconds)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(milliseconds) % 60
        return String.format(Locale.ROOT,"%02d:%02d", minutes, seconds)
    }

    fun parseOriginalFilename(filepath: String): String {
        val filename = File(filepath).name
        val regex = Regex("(.*)_([0-9]+)s:([a-zA-Z0-9]+)\\.jpg$")
        val matchResult = regex.find(filename)

        return if (matchResult != null) {
            "${matchResult.groupValues[1]}.${matchResult.groupValues[3]}"
        } else {
            filename
        }
    }

    fun parseDuration(filepath: String): String? {
        val filename = File(filepath).name
        val regex = Regex("(.*)_([0-9]+)s:([a-zA-Z0-9]+)\\.jpg$")
        val matchResult = regex.find(filename)

        return if (matchResult != null) {
            val durationInSeconds = matchResult.groupValues[2].toInt()
            formatDuration(durationInSeconds)
        } else {
            null
        }
    }

    private fun formatDuration(durationInSeconds: Int): String {
        val minutes = durationInSeconds / 60
        val seconds = durationInSeconds % 60
        return String.format(Locale.ROOT,"%02d:%02d", minutes, seconds)
    }

    fun fileToLocalMedia(file: File): LocalMedia {
        val localMedia = LocalMedia()

        // Установите путь файла
        localMedia.path = file.absolutePath

        // Определите MIME тип файла на основе его расширения
        localMedia.mimeType = when (file.extension.lowercase(Locale.ROOT)) {
            "jpg", "jpeg" -> PictureMimeType.ofJPEG()
            "png" -> PictureMimeType.ofPNG()
            "mp4" -> PictureMimeType.ofMP4()
            "avi" -> PictureMimeType.ofAVI()
            "gif" -> PictureMimeType.ofGIF()
            else -> PictureMimeType.MIME_TYPE_AUDIO // Или другой тип по умолчанию
        }

        // Установите дополнительные свойства
        localMedia.isCompressed = false // Или true, если вы хотите сжать изображение
        localMedia.isCut = false // Если это изображение было обрезано
        localMedia.isOriginal = false // Если это оригинальный файл

        if (localMedia.mimeType == PictureMimeType.MIME_TYPE_VIDEO) {
            // Получаем длительность видео
            val duration = getVideoDuration(file)
            localMedia.duration = duration
        } else {
            localMedia.duration = 0 // Для изображений длительность обычно равна 0
        }

        return localMedia
    }

    private fun getVideoDuration(file: File): Long {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            return durationStr?.toLongOrNull() ?: 0
        } catch (e: Exception) {
            e.printStackTrace()
            return 0
        } finally {
            retriever.release()
        }
    }
}