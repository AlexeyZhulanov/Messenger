package com.example.messenger

import android.content.Context
import android.media.MediaMetadataRetriever
import android.util.Base64
import androidx.lifecycle.ViewModel
import com.example.messenger.di.IoDispatcher
import com.example.messenger.model.ChatSettings
import com.example.messenger.model.FileManager
import com.example.messenger.model.MessengerService
import com.example.messenger.model.NoPermissionException
import com.example.messenger.model.RetrofitService
import com.example.messenger.model.UserNotFoundException
import com.example.messenger.security.ChatKeyManager
import com.example.messenger.security.TinkAesGcmHelper
import com.luck.picture.lib.config.PictureMimeType
import com.luck.picture.lib.entity.LocalMedia
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.security.KeyFactory
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import java.text.SimpleDateFormat
import java.util.Arrays
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class BaseInfoViewModel @Inject constructor(
    private val messengerService: MessengerService,
    private val retrofitService: RetrofitService,
    private val fileManager: FileManager,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {
    private var convId: Int = -1
    private var isGroup: Int = 0
    private val tempFiles = mutableSetOf<String>()

    private val chatKeyManager = ChatKeyManager()
    private var tinkAesGcmHelper: TinkAesGcmHelper? = null

    fun setConvInfo(convId: Int, isGroup: Int) {
        this.convId = convId
        this.isGroup = isGroup
        val chatTypeString = if(isGroup == 0) "dialog" else "group"
        val aead = chatKeyManager.getAead(convId, chatTypeString)
        aead?.let { tinkAesGcmHelper = TinkAesGcmHelper(it) }
    }

    suspend fun addMember(currentKeyString: String?, name: String, currentUserId: Int, callback: (String) -> Unit) {
        if(currentKeyString == null) callback("Ошибка: у вас отсутствует ключ, вы не можете добавить нового пользователя")
        if(name != "") {
            try {
                val keyString = retrofitService.getUserKey(name)
                if(!keyString.isNullOrEmpty()) {
                    val keyManager = ChatKeyManager()

                    val publicKey = getPublicKey(keyString)

                    val wrappedKey = Base64.decode(currentKeyString, Base64.NO_WRAP)
                    val symmetricKey = keyManager.unwrapChatKeyForGet(wrappedKey, currentUserId)
                    val userKeyByte = keyManager.wrapChatKeyForRecipient(symmetricKey, publicKey)
                    Arrays.fill(symmetricKey.encoded, 0.toByte())

                    val userKey = Base64.encodeToString(userKeyByte, Base64.NO_WRAP)
                    val success = retrofitService.addUserToGroup(convId, name, userKey)

                    if(success) callback("Пользователь успешно добавлен") else callback("Не удалось добавить пользователя")
                } else callback("Ошибка: Пользователь ни разу не заходил в мессенджер, невозможно его добавить в группу!")
            } catch (e: UserNotFoundException) {
                callback("Ошибка: Пользователя не существует")
            } catch (e: Exception) {
                callback("Ошибка: Нет сети!")
            }
        }
        else callback("Вы не ввели никнейм пользователя")
    }

    private fun getPublicKey(key: String): PublicKey {
        val publicKeyBytes = Base64.decode(key, Base64.NO_WRAP)
        val keySpec = X509EncodedKeySpec(publicKeyBytes)
        return KeyFactory.getInstance("RSA").generatePublic(keySpec)
    }

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
        return try {
            retrofitService.getMedias(convId, page, isGroup)
        } catch (e: Exception) { null }
    }

    suspend fun getFiles(page: Int): List<String>? {
        return try {
            retrofitService.getFiles(convId, page, isGroup)
        } catch (e: Exception) { null }
    }

    suspend fun getAudios(page: Int): List<String>? {
        return try {
            retrofitService.getAudios(convId, page, isGroup)
        } catch (e: Exception) { null }
    }

    suspend fun getPreview(context: Context, filename: String): String {
        val previewPath = try {
            retrofitService.getMediaPreview(context, convId, filename, isGroup)
        } catch (e: Exception) {
            return ""
        }
        return decryptPath(previewPath)
    }

    private fun decryptPath(path: String): String {
        if(tinkAesGcmHelper == null) return ""

        val downloadedFile = File(path)

        tinkAesGcmHelper?.decryptFile(downloadedFile, downloadedFile)

        return downloadedFile.absolutePath
    }

    suspend fun deleteAllMessages(): Boolean {
        return try {
            if(isGroup == 0) retrofitService.deleteDialogMessages(convId)
            else retrofitService.deleteGroupMessagesAll(convId)
        } catch (e: Exception) { false }
    }

    suspend fun deleteConv(): Pair<Boolean, String> {
        return try {
            if(isGroup == 0) {
                retrofitService.deleteDialog(convId) to "Ошибка: Нет сети!"
            } else retrofitService.deleteGroup(convId) to "Ошибка: Нет сети!"
        } catch (e: NoPermissionException) {
            false to "Ошибка: Только создатель группы может её удалить"
        }
        catch (e: Exception) {
            false to "Ошибка: Нет сети!"
        }
    }

    suspend fun updateAutoDeleteInterval(interval: Int): Boolean {
        return try {
            if(isGroup == 0) retrofitService.updateAutoDeleteInterval(convId, interval)
            else retrofitService.updateGroupAutoDeleteInterval(convId, interval)
        } catch (e: Exception) { false }
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
        val downloadedFilePath = retrofitService.downloadFile(context, folder, convId, filename, isGroup)
        return decryptPath(downloadedFilePath)
    }

    fun downloadFileJava(context: Context, folder: String, filename: String): String {
        return runBlocking {
            val downloadedFilePath = retrofitService.downloadFile(context, folder, convId, filename, isGroup)
            decryptPath(downloadedFilePath)
        }
    }

    fun fManagerSaveFileJava(fileName: String, fileData: ByteArray) {
        fileManager.saveMessageFile(fileName, fileData)
    }

    suspend fun deleteUserFromGroup(userId: Int): Boolean {
        return try {
            retrofitService.deleteUserFromGroup(convId, userId)
        } catch (e: Exception) { false }
    }

    suspend fun updateGroupAvatar(avatar: String): Boolean {
        return try {
            retrofitService.updateGroupAvatar(convId, avatar)
        } catch (e: Exception) { false }
    }

    suspend fun updateGroupName(name: String): Boolean {
        return try {
            retrofitService.editGroupName(convId, name)
        } catch (e: Exception) { false }
    }

    suspend fun uploadAvatar(avatar: File): String {
        return try {
            retrofitService.uploadAvatar(avatar)
        } catch (e: Exception) { "" }
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

    fun formatUserSessionDate(timestamp: Long?): String {
        if (timestamp == null) return "Никогда не был в сети"

        val greenwichSessionDate = Calendar.getInstance().apply {
            timeInMillis = timestamp
        }
        val now = Calendar.getInstance()

        val diffInMillis = now.timeInMillis - greenwichSessionDate.timeInMillis
        val diffInMinutes = (diffInMillis / 60000).toInt()

        val dateFormatTime = SimpleDateFormat("HH:mm", Locale.getDefault())
        val dateFormatDayMonth = SimpleDateFormat("d MMM", Locale.getDefault())
        val dateFormatYear = SimpleDateFormat("d.MM.yyyy", Locale.getDefault())

        return when {
            diffInMinutes < 2 -> "в сети"
            diffInMinutes < 5 -> "был в сети только что"
            diffInMinutes in 5..20 -> "был в сети $diffInMinutes минут назад"
            diffInMinutes % 10 == 1 && diffInMinutes in 21..59 -> "был в сети $diffInMinutes минуту назад"
            diffInMinutes % 10 in 2..4 && diffInMinutes in 21..59 -> "был в сети $diffInMinutes минуты назад"
            diffInMinutes < 60 -> "был в сети $diffInMinutes минут назад"
            diffInMinutes < 120 -> "был в сети час назад"
            diffInMinutes < 180 -> "был в сети два часа назад"
            diffInMinutes < 240 -> "был в сети три часа назад"
            diffInMinutes < 1440 -> "был в сети в ${dateFormatTime.format(greenwichSessionDate.time)}"
            else -> {
                // Проверка года
                val currentYear = now.get(Calendar.YEAR)
                val sessionYear = greenwichSessionDate.get(Calendar.YEAR)
                if (currentYear == sessionYear) {
                    "был в сети ${dateFormatDayMonth.format(greenwichSessionDate.time)}"
                } else {
                    "был в сети ${dateFormatYear.format(greenwichSessionDate.time)}"
                }
            }
        }
    }
}