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
import androidx.paging.map
import com.example.messenger.di.IoDispatcher
import com.example.messenger.di.MainDispatcher
import com.example.messenger.model.FileManager
import com.example.messenger.model.MessengerService
import com.example.messenger.model.News
import com.example.messenger.model.NewsPagingSource
import com.example.messenger.model.RetrofitService
import com.example.messenger.security.ChatKeyManager
import com.example.messenger.security.TinkAesGcmHelper
import com.example.messenger.states.AudioPlaybackState
import com.example.messenger.states.FileItem
import com.example.messenger.states.FilesState
import com.example.messenger.states.ImageItem
import com.example.messenger.states.ImagesState
import com.example.messenger.states.NewsAttachmentsState
import com.example.messenger.states.NewsUi
import com.example.messenger.states.VoiceItem
import com.example.messenger.states.VoicesState
import com.example.messenger.utils.takeSampleAlt
import com.linc.amplituda.Amplituda
import com.luck.picture.lib.config.PictureMimeType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Collections
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

@HiltViewModel
class NewsViewModel @Inject constructor(
    private val messengerService: MessengerService,
    private val retrofitService: RetrofitService,
    private val fileManager: FileManager,
    private val amplituda: Amplituda,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    @param:MainDispatcher private val mainDispatcher: CoroutineDispatcher
) : ViewModel() {

    private var tinkAesGcmHelper: TinkAesGcmHelper? = null
    private val chatKeyManager = ChatKeyManager()

    private val refreshTrigger = MutableLiveData(Unit)

    private val preloadSemaphore = Semaphore(4)

    private val imagesCache = ConcurrentHashMap<Int, ImagesState>()
    private val voicesCache = ConcurrentHashMap<Int, VoicesState>()
    private val filesCache = ConcurrentHashMap<Int, FilesState>()

    private val imagesLoading = Collections.newSetFromMap(ConcurrentHashMap<Int, Boolean>())
    private val voicesLoading = Collections.newSetFromMap(ConcurrentHashMap<Int, Boolean>())
    private val filesLoading  = Collections.newSetFromMap(ConcurrentHashMap<Int, Boolean>())

    private val _attachmentsState = MutableStateFlow<Map<Int, NewsAttachmentsState>>(emptyMap())
    val attachmentsState = _attachmentsState.asStateFlow()

    private val _audioPlaybackState = MutableStateFlow(AudioPlaybackState())
    val audioPlaybackState = _audioPlaybackState.asStateFlow()

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val pagingFlow = refreshTrigger.asFlow()
        .debounce(500)
        .flatMapLatest {
            Pager(PagingConfig(pageSize = 10, initialLoadSize = 10, prefetchDistance = 3)) {
            NewsPagingSource(retrofitService, messengerService, fileManager, tinkAesGcmHelper)
            }.flow
        }.map { pagingData ->
            pagingData.map { news ->
                val uiModel = toNewsUi(news)
                preloadNewsAttachments(uiModel, true)
                uiModel
            }
        }.cachedIn(viewModelScope)


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
            } catch (_: Exception) { return@launch }
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
        } catch (_: Exception) { 0 }
    }

    private fun formatMessageNews(timestamp: Long?): String {
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

    private fun formatFileSize(size: Long): String {
        val kb = 1024.0
        val mb = kb * 1024
        return when {
            size < kb -> "$size B"
            size < mb -> "${(size / kb).toInt()} KB"
            else -> String.format(Locale.ROOT, "%.2f MB", size / mb)
        }
    }

    private suspend fun downloadNews(filename: String): String = withContext(ioDispatcher) {
        val downloadedFilePath = try {
            retrofitService.downloadNews(filename)
        } catch (_: Exception) { return@withContext "" }
        val downloadedFile = File(downloadedFilePath)
        return@withContext tinkAesGcmHelper?.let {
            it.decryptFile(downloadedFile, downloadedFile)
            downloadedFile.absolutePath
        } ?: ""
    }

    suspend fun uploadNews(file: File, context: Context): Pair<String, Boolean> = withContext(ioDispatcher) {
        val path = try {
            val tempDir = context.cacheDir
            val tempFile = File(tempDir, file.name)
            tinkAesGcmHelper?.let {
                it.encryptFile(file, tempFile)
                val pt = retrofitService.uploadNews(file)
                tempFile.delete()
                pt
            } ?: return@withContext Pair("", false)
        } catch (_: Exception) {
            return@withContext Pair("", false)
        }
        return@withContext Pair(path, true)
    }

    private fun encryptText(text: String?) : String? {
        val txt = text?.let { tinkAesGcmHelper?.encryptText(it) }
        Log.d("testEnctyptNewsText", txt.toString())
        return txt
    }

    suspend fun sendNews(headerText: String?, text: String?, images: List<String>?,
                         voices: List<String>?, files: List<String>?): Boolean {
        return try {
            val bodyText = encryptText(text)
            val headText = encryptText(headerText) ?: "Новая новость"
            retrofitService.sendNews(headText, bodyText, images, voices, files)
        } catch (_: Exception) { false }
    }

    suspend fun editNews(newsId: Int, headerText: String?, text: String?, images: List<String>?,
                         voices: List<String>?, files: List<String>?): Boolean {
        return try {
            val bodyText = encryptText(text)
            val headText = encryptText(headerText) ?: "Новая новость"
            retrofitService.editNews(newsId, headText, bodyText, images, voices, files)
        } catch (_: Exception) { false }
    }

    suspend fun deleteNews(newsId: Int): Boolean {
        return try {
            retrofitService.deleteNews(newsId)
        } catch (_: Exception) { false }
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

    private fun toNewsUi(news: News): NewsUi {
        val state = _attachmentsState.value[news.id]
        return NewsUi(
            news = news,
            formattedDate = formatMessageNews(news.timestamp),

            imagesState = if (!news.images.isNullOrEmpty()) {
                state?.imagesState ?: ImagesState.Loading
            } else null,
            filesState = if (!news.files.isNullOrEmpty()) {
                state?.filesState ?: FilesState.Loading
            } else null,
            voicesState = if (!news.voices.isNullOrEmpty()) {
                state?.voicesState ?: VoicesState.Loading
            } else null
        )
    }

    fun preloadNewsAttachments(ui: NewsUi, isNeedSave: Boolean) {
        preloadImages(ui, isNeedSave)
        preloadFiles(ui, isNeedSave)
        preloadVoices(ui, isNeedSave)
    }

    private fun preloadImages(ui: NewsUi, isNeedSave: Boolean) {
        val id = ui.news.id
        val images = ui.news.images ?: return

        if (_attachmentsState.value[id]?.imagesState != null) return

        if (imagesCache.containsKey(id)) {
            updateState(id) { it.copy(imagesState = imagesCache[id]) }
            return
        }

        if (!imagesLoading.add(id)) {
            return
        }

        viewModelScope.launch {
            try {
                val result = preloadSemaphore.withPermit {
                    withContext(ioDispatcher) {
                        try {
                            val items = images.map { name ->

                                val localPath =
                                    if (fileManager.isExistNews(name))
                                        fileManager.getNewsFilePath(name)
                                    else {
                                        val path = downloadNews(name)
                                        if(isNeedSave) fileManager.saveNewsFile(name, File(path).readBytes())
                                        path
                                    }

                                val (mime, duration) = fileToPrepareLocalMedia(localPath)
                                ImageItem(localPath, mime, duration)
                            }
                            ImagesState.Ready(items)
                        } catch (_: Exception) {
                            ImagesState.Error
                        }
                    }
                }
                imagesCache[id] = result
                updateState(id) { it.copy(imagesState = result) }
            }
            finally {
                imagesLoading.remove(id)
            }
        }
    }

    private fun preloadVoices(ui: NewsUi, isNeedSave: Boolean) {
        val id = ui.news.id
        val voices = ui.news.voices ?: return

        if (_attachmentsState.value[id]?.voicesState != null) return

        if (voicesCache.containsKey(id)) {
            updateState(id) { it.copy(voicesState = voicesCache[id]) }
            return
        }

        if (!voicesLoading.add(id)) {
            return
        }

        viewModelScope.launch {
            try {
                val result = preloadSemaphore.withPermit {
                    withContext(ioDispatcher) {
                        try {
                            val items = voices.map { name ->
                                val localPath =
                                    if (fileManager.isExistNews(name))
                                        fileManager.getNewsFilePath(name)
                                    else {
                                        val path = downloadNews(name)
                                        if(isNeedSave) fileManager.saveNewsFile(name, File(path).readBytes())
                                        path
                                    }

                                val duration = readDuration(localPath)
                                val sample = takeSampleAlt(amplituda, localPath)
                                VoiceItem(localPath, duration, sample.toList())
                            }
                            VoicesState.Ready(items)
                        } catch (_: Exception) {
                            VoicesState.Error
                        }
                    }
                }
                voicesCache[id] = result
                updateState(id) { it.copy(voicesState = result) }
            }
            finally {
                voicesLoading.remove(id)
            }
        }
    }

    private fun preloadFiles(ui: NewsUi, isNeedSave: Boolean) {
        val id = ui.news.id
        val files = ui.news.files ?: return

        if (_attachmentsState.value[id]?.filesState != null) return

        if (filesCache.containsKey(id)) {
            updateState(id) { it.copy(filesState = filesCache[id]) }
            return
        }

        if (!filesLoading.add(id)) {
            return
        }

        viewModelScope.launch {
            try {
                val result = preloadSemaphore.withPermit {
                    withContext(ioDispatcher) {
                        try {
                            val filePaths = files.map { name ->
                                if (fileManager.isExistNews(name))
                                    fileManager.getNewsFilePath(name)
                                else {
                                    val path = downloadNews(name)
                                    if(isNeedSave) fileManager.saveNewsFile(name, File(path).readBytes())
                                    path
                                }
                            }
                            val items = filePaths.map {
                                val file = File(it)
                                FileItem(
                                    localPath = it,
                                    fileName = file.name,
                                    fileSize = formatFileSize(file.length())
                                )
                            }
                            FilesState.Ready(items)
                        } catch (_: Exception) {
                            FilesState.Error
                        }
                    }
                }
                filesCache[id] = result
                updateState(id) { it.copy(filesState = result) }
            }
            finally {
                filesLoading.remove(id)
            }
        }
    }

    private suspend fun fileToPrepareLocalMedia(filePath: String): Pair<String, Long> = withContext(ioDispatcher) {
        val file = File(filePath)

        val type = when (file.extension.lowercase(Locale.ROOT)) {
            "jpg", "jpeg" -> PictureMimeType.ofJPEG()
            "png" -> PictureMimeType.ofPNG()
            "mp4" -> PictureMimeType.ofMP4()
            "avi" -> PictureMimeType.ofAVI()
            "gif" -> PictureMimeType.ofGIF()
            else -> PictureMimeType.MIME_TYPE_AUDIO // Или другой тип по умолчанию
        }

        val duration = if (type == PictureMimeType.MIME_TYPE_VIDEO) {
            getVideoDuration(file)
        } else 0 // Для изображений длительность обычно равна 0

        return@withContext type to duration
    }

    private fun readDuration(localPath: String): Long {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(localPath)
            val duration =
                retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_DURATION
                )?.toLong() ?: 0L
            retriever.release()
            duration
        } catch (_: Exception) {
            0L
        }
    }

    private fun updateState(newsId: Int, transform: (NewsAttachmentsState) -> NewsAttachmentsState) {
        _attachmentsState.update { currentMap ->
            val newMap = currentMap.toMutableMap()
            val oldState = newMap[newsId] ?: NewsAttachmentsState()
            newMap[newsId] = transform(oldState)
            newMap
        }
    }

    fun updateAudioState(path: String?, isPlaying: Boolean, progress: Int) {
        _audioPlaybackState.value = AudioPlaybackState(path, isPlaying, progress)
    }

    fun updateAudioProgress(progress: Int) {
        _audioPlaybackState.value = _audioPlaybackState.value.copy(progress = progress)
    }
}