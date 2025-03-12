package com.example.messenger

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Base64
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import com.example.messenger.di.IoDispatcher
import com.example.messenger.di.MainDispatcher
import com.example.messenger.model.FileManager
import com.example.messenger.model.MessengerService
import com.example.messenger.model.NewsPagingSource
import com.example.messenger.model.RetrofitService
import com.example.messenger.security.ChatKeyManager
import com.example.messenger.security.TinkAesGcmHelper
import com.luck.picture.lib.config.PictureMimeType
import com.luck.picture.lib.entity.LocalMedia
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class NewsViewModel @Inject constructor(
    private val messengerService: MessengerService,
    private val retrofitService: RetrofitService,
    private val fileManager: FileManager,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    @MainDispatcher private val mainDispatcher: CoroutineDispatcher
) : ViewModel() {

    private var tinkAesGcmHelper: TinkAesGcmHelper? = null
    private val chatKeyManager = ChatKeyManager()

    private val refreshTrigger = MutableLiveData(Unit)


    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val pagingFlow = refreshTrigger.asFlow()
        .debounce(500)
        .flatMapLatest {
            Pager(PagingConfig(pageSize = 10, initialLoadSize = 10, prefetchDistance = 3)) {
            NewsPagingSource(retrofitService, messengerService, fileManager, tinkAesGcmHelper)
            }.flow.cachedIn(viewModelScope)
        }


    fun refresh() {
        Log.d("testRefresh", "TRIGGERED")
        this.refreshTrigger.postValue(this.refreshTrigger.value)
    }

    fun setEncryptHelper(userId: Int?) {
        viewModelScope.launch {
            chatKeyManager.getAead(0, "news")?.let { aead ->
                tinkAesGcmHelper = TinkAesGcmHelper(aead)
                return@launch
            }
            val wrappedKeyString = try {
                retrofitService.getNewsKey() ?: run {
                    Log.d("testErrorTinkInit", "News key is null")
                    return@launch
                }
            } catch (e: Exception) { return@launch }
            val wrappedKey = Base64.decode(wrappedKeyString, Base64.NO_WRAP)

            val id = userId ?: messengerService.getUser()?.id ?: run {
                try {
                    retrofitService.getUser(0).id
                } catch (e: Exception) {
                    Log.d("testErrorTinkInit", "Failed to fetch user ID: ${e.message}")
                    return@launch
                }
            }
            chatKeyManager.unwrapNewsKey(wrappedKey, 0, "news", id)
            chatKeyManager.getAead(0, "news")?.also { newAead ->
                tinkAesGcmHelper = TinkAesGcmHelper(newAead)
            } ?: Log.d("testErrorTinkInit", "newAead is null")
        }
    }

    suspend fun getPermission() : Int {
        return try {
            retrofitService.getPermission()
        } catch (e: Exception) {
            0
        }
    }

    fun formatMessageNews(timestamp: Long?): String {
        if (timestamp == null) return ""

        val greenwichMessageDate = Calendar.getInstance().apply {
            timeInMillis = timestamp
        }
        val dateFormatToday = SimpleDateFormat("HH:mm", Locale.getDefault())
        val dateFormatMonthDay = SimpleDateFormat("d MMMM", Locale.getDefault())
        val dateFormatYear = SimpleDateFormat("d MMMM yyyy", Locale.getDefault())
        val localNow = Calendar.getInstance()

        return when {
            isToday(localNow, greenwichMessageDate) -> dateFormatToday.format(greenwichMessageDate.time)
            isThisYear(localNow, greenwichMessageDate) -> dateFormatMonthDay.format(greenwichMessageDate.time)
            else -> dateFormatYear.format(greenwichMessageDate.time)
        }
    }

    private fun isToday(now: Calendar, messageDate: Calendar): Boolean {
        return now.get(Calendar.YEAR) == messageDate.get(Calendar.YEAR) &&
                now.get(Calendar.DAY_OF_YEAR) == messageDate.get(Calendar.DAY_OF_YEAR)
    }

    private fun isThisYear(now: Calendar, messageDate: Calendar): Boolean {
        return now.get(Calendar.YEAR) == messageDate.get(Calendar.YEAR)
    }

    fun formatTime(milliseconds: Long): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(milliseconds)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(milliseconds) % 60
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

    fun formatFileSize(size: Long): String {
        val kb = 1024.0
        val mb = kb * 1024
        return when {
            size < kb -> "$size B"
            size < mb -> "${(size / kb).toInt()} KB"
            else -> String.format(Locale.ROOT, "%.2f MB", size / mb)
        }
    }

    fun fManagerIsExistNews(fileName: String): Boolean {
        return fileManager.isExistNews(fileName)
    }

    fun fManagerGetFilePathNews(fileName: String): String {
        return fileManager.getNewsFilePath(fileName)
    }

    suspend fun fManagerSaveFileNews(fileName: String, fileData: ByteArray) = withContext(ioDispatcher) {
        fileManager.saveNewsFile(fileName, fileData)
    }

    suspend fun downloadNews(context: Context, filename: String): String {
        val downloadedFilePath = retrofitService.downloadNews(context, filename)
        val downloadedFile = File(downloadedFilePath)
        return tinkAesGcmHelper?.let {
            it.decryptFile(downloadedFile, downloadedFile)
            downloadedFile.absolutePath
        } ?: ""
    }

    suspend fun uploadNews(file: File, context: Context): Pair<String, Boolean> {
        val path = try {
            val tempDir = context.cacheDir
            val tempFile = File(tempDir, file.name)
            tinkAesGcmHelper?.let {
                it.encryptFile(file, tempFile)
                val pt = retrofitService.uploadNews(file)
                tempFile.delete()
                pt
            } ?: return Pair("", false)
        } catch (e: Exception) {
            return Pair("", false)
        }
        return Pair(path, true)
    }

    private fun encryptText(text: String?) : String? {
        val txt = text?.let { tinkAesGcmHelper?.encryptText(it) }
        Log.d("testEnctyptNewsText", txt.toString())
        return txt
    }

    suspend fun sendNews(headerText: String, text: String?, images: List<String>?,
                         voices: List<String>?, files: List<String>?): Boolean {
        return try {
            val bodyText = encryptText(text)
            val headText = encryptText(headerText) ?: "Новая новость"
            retrofitService.sendNews(headText, bodyText, images, voices, files)
        } catch (e: Exception) { false }
    }

    suspend fun editNews(newsId: Int, headerText: String, text: String?, images: List<String>?,
                         voices: List<String>?, files: List<String>?): Boolean {
        return try {
            val bodyText = encryptText(text)
            val headText = encryptText(headerText) ?: "Новая новость"
            retrofitService.editNews(newsId, headText, bodyText, images, voices, files)
        } catch (e: Exception) { false }
    }

    suspend fun deleteNews(newsId: Int): Boolean {
        return try {
            retrofitService.deleteNews(newsId)
        } catch (e: Exception) { false }
    }

    private fun getFileName(uri: Uri, context: Context): String {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    result = it.getString(it.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != -1) {
                result = result?.substring(cut!! + 1)
            }
        }
        return result ?: "temp_file"
    }

    fun getFileFromContentUri(context: Context, contentUri: Uri): File? {
        val projection = arrayOf(MediaStore.Video.Media.DATA)
        val cursor = context.contentResolver.query(contentUri, projection, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val columnIndex = it.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                val filePath = it.getString(columnIndex)
                return File(filePath)
            }
        }
        return null
    }

    fun uriToFile(uri: Uri, context: Context): File? {
        val fileName = getFileName(uri, context)
        // Создаем временный файл в кэше приложения
        val tempFile = File(context.cacheDir, fileName)

        try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val outputStream = FileOutputStream(tempFile)
            inputStream?.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }
            return tempFile
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    suspend fun uploadFiles(list: List<File>, context: Context): List<String>? = withContext(mainDispatcher) {
        if (list.isNotEmpty()) {
            val listTemp = mutableListOf<String>()
            for (item in list) {
                val (path, f) = uploadNews(item, context)
                listTemp += path
                if (!f) break
            }
            return@withContext if (listTemp.size == list.size) listTemp else null
        }
        return@withContext null
    }

}