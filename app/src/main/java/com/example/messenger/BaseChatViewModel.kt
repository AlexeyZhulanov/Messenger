package com.example.messenger

import android.annotation.SuppressLint
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Base64
import android.util.Log
import android.view.View
import android.widget.ImageView
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.example.messenger.di.IoDispatcher
import com.example.messenger.model.ConversationSettings
import com.example.messenger.model.FileManager
import com.example.messenger.model.ImageUtils
import com.example.messenger.model.Message
import com.example.messenger.model.MessengerService
import com.example.messenger.model.RetrofitService
import com.example.messenger.model.WebSocketService
import com.example.messenger.security.ChatKeyManager
import com.example.messenger.security.TinkAesGcmHelper
import com.luck.picture.lib.config.PictureMimeType
import com.luck.picture.lib.entity.LocalMedia
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.URLConnection
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs

abstract class BaseChatViewModel(
    protected val messengerService: MessengerService,
    protected val retrofitService: RetrofitService,
    protected val fileManager: FileManager,
    protected val webSocketService: WebSocketService,
    @IoDispatcher protected val ioDispatcher: CoroutineDispatcher
) : ViewModel() {
    protected var convId: Int = -1
    private var isGroup: Int = 0
    private var disableRefresh: Boolean = false
    private var pendingRefresh: Boolean = false
    @SuppressLint("StaticFieldLeak")
    protected lateinit var recyclerView: RecyclerView
    protected var lastMessageDate: String = ""
    private var debounceJob: Job? = null
    private val readMessageIds = mutableSetOf<Int>()
    private val chatKeyManager = ChatKeyManager()
    protected var tinkAesGcmHelper: TinkAesGcmHelper? = null
    private val imageUtils = ImageUtils()

    protected val searchBy = MutableLiveData("")
    protected val currentPage = MutableStateFlow(0)

    private val _initFlow = MutableSharedFlow<Unit>()
    val initFlow: SharedFlow<Unit> = _initFlow

    protected val _newMessageFlow = MutableSharedFlow<Pair<Message, String>?>(extraBufferCapacity = 5)
    val newMessageFlow: SharedFlow<Pair<Message, String>?> = _newMessageFlow

    protected val _typingState = MutableStateFlow<Pair<Boolean, String?>>(Pair(false, null))
    val typingState: StateFlow<Pair<Boolean, String?>> get() = _typingState

    protected val _deleteState = MutableStateFlow(0)
    val deleteState: StateFlow<Int> get() = _deleteState

    protected val _readMessagesFlow = MutableSharedFlow<List<Int>>(extraBufferCapacity = 5)
    val readMessagesFlow: SharedFlow<List<Int>> get() = _readMessagesFlow

    private val _unsentMessageFlow = MutableStateFlow<Message?>(null)
    val unsentMessageFlow: StateFlow<Message?> get() = _unsentMessageFlow

    protected val _editMessageFlow = MutableStateFlow<Message?>(null)
    val editMessageFlow: StateFlow<Message?> get() = _editMessageFlow


    fun updateLastDate(time: Long) {
        val greenwichMessageDate = Calendar.getInstance().apply {
            timeInMillis = time
        }
        val localNow = Calendar.getInstance()
        this.lastMessageDate = if(isToday(localNow, greenwichMessageDate)) "" else formatMessageDate(time)
    }

    fun loadNextPage() {
        currentPage.value += 1
    }

    fun isFirstPage() : Boolean = currentPage.value == 0

    fun refresh() {
        if (disableRefresh) {
            pendingRefresh = true
            return
        }
        currentPage.value = 0
        this.searchBy.postValue(this.searchBy.value)
    }

    fun stopRefresh() {
        disableRefresh = true
    }

    fun startRefresh() {
        disableRefresh = false
        if (pendingRefresh) {
            pendingRefresh = false
            refresh() // Отложенное обновление
        }
    }

    fun setConvInfo(convId: Int, isGroup: Int, chatKey: String, userId: Int) {
        val chatTypeString = if(isGroup == 0) "dialog" else "group"
        val aead = chatKeyManager.getAead(convId, chatTypeString)
        if(aead != null) {
            tinkAesGcmHelper = TinkAesGcmHelper(aead)
        } else {
            if(chatKey != "") {
                val wrappedKey = Base64.decode(chatKey, Base64.NO_WRAP)
                chatKeyManager.unwrapChatKeyForSave(wrappedKey, convId, chatTypeString, userId)
                val newAead = chatKeyManager.getAead(convId, chatTypeString)
                if(newAead != null) {
                    tinkAesGcmHelper = TinkAesGcmHelper(newAead)
                } else Log.d("testErrorTinkInit", "newAead is null")
            } else Log.d("testErrorTinkInit", "chatKey is null")
        }
        this.convId = convId
        this.isGroup = isGroup

        viewModelScope.launch {
            _initFlow.emit(Unit)
        }

        updateLastSession()
    }

    fun bindRecyclerView(recyclerView: RecyclerView) {
        this.recyclerView = recyclerView
    }

    private fun updateLastSession() {
        viewModelScope.launch {
            delay(2000)
            try {
                retrofitService.updateLastSession()
            } catch (e: Exception) { return@launch }
        }
    }

    private fun highlightItem(position: Int) {
        val adapter = recyclerView.adapter
        if (adapter is MessageAdapter) {
            recyclerView.post {
                adapter.highlightPosition(position)
            }
        }
    }

    fun markMessagesAsRead(visibleMessages: List<Message>) {
        readMessageIds.addAll(visibleMessages.map { it.id })
        debounceJob?.cancel()
        debounceJob = viewModelScope.launch {
            delay(3000) // Wait 3 seconds (debounce server requests)
            if (readMessageIds.isNotEmpty()) {
                sendReadMessagesToServer(readMessageIds.toList())
                readMessageIds.clear()
            }
        }
    }

    private suspend fun sendReadMessagesToServer(messageIds: List<Int>) {
        try {
            if(isGroup == 0) retrofitService.markMessagesAsRead(convId, messageIds)
            else retrofitService.markGroupMessagesAsRead(convId, messageIds)
        } catch (e: Exception) {
            Log.e("ReadMessagesError", "Failed to send read messages: ${e.message}")
        }
    }

    fun smartScrollToPosition(targetPosition: Int) {
        recyclerView.clearOnScrollListeners()
        val currentPos = (recyclerView.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition()

        if (currentPos >= targetPosition) {
            // Целевая позиция уже на экране
            recyclerView.smoothScrollToPosition(targetPosition)
            recyclerView.post { highlightItem(targetPosition) }
            return
        }

        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)

                val lastVisiblePosition = (recyclerView.layoutManager as LinearLayoutManager).findLastVisibleItemPosition()

                if (lastVisiblePosition >= targetPosition) {
                    // Достигли целевой позиции, остановим скролл
                    recyclerView.removeOnScrollListener(this)
                    recyclerView.post { highlightItem(targetPosition) }
                } else {
                    // Если не достигли целевой позиции, продолжаем скролл
                    recyclerView.smoothScrollToPosition(lastVisiblePosition + 1)
                }
            }
        })

        // Начинаем скролл
        if(abs(targetPosition - currentPos) > 10)
            recyclerView.smoothScrollToPosition(currentPos + 10)
        else {
            recyclerView.smoothScrollToPosition(targetPosition)
            recyclerView.post { highlightItem(targetPosition) }
        }
    }

    fun searchMessagesInDialog(query: String) {
        if(this.searchBy.value == query) return
        this.searchBy.value = query
    }

    suspend fun getConvSettings(): ConversationSettings {
        return if(isGroup == 0) retrofitService.getDialogSettings(convId)
        else retrofitService.getGroupSettings(convId)
    }

    suspend fun deleteMessages(ids: List<Int>): Boolean {
        val response = try {
            if(isGroup == 0) retrofitService.deleteMessages(convId, ids)
            else retrofitService.deleteGroupMessages(convId, ids)
        } catch (e: Exception) { return false }
        return response
    }

    private fun encryptText(text: String?) : String? {
        Log.d("testEncryptTink", text.toString())
        val res = text?.let { tinkAesGcmHelper?.encryptText(it) }
        Log.d("testEncryptTink", res.toString())
        return res
    }

    fun sendMessage(text: String?, images: List<String>?, voice: String?, file: String?,
                    referenceToMessageId: Int?, isForwarded: Boolean,
                    usernameAuthorOriginal: String?, localFilePaths: List<String>?) {
        viewModelScope.launch {
            val flag = if (!localFilePaths.isNullOrEmpty()) { false }
            else {
                try {
                    val encryptedText = encryptText(text)
                    if(isGroup == 0) retrofitService.sendMessage(convId, encryptedText, images, voice, file,
                        referenceToMessageId, isForwarded, usernameAuthorOriginal)
                    else retrofitService.sendGroupMessage(convId, encryptedText, images, voice, file,
                        referenceToMessageId, isForwarded, usernameAuthorOriginal)
                } catch (e: Exception) { false }
            }
            if(!flag) {
                var mes = Message(id = 0, idSender = -5, text = text, images = images, voice = voice, file = file,
                    referenceToMessageId = referenceToMessageId, isForwarded = isForwarded, usernameAuthorOriginal = usernameAuthorOriginal,
                    timestamp = 0, isEdited = false, isUnsent = true, localFilePaths = localFilePaths)
                val id = if(isGroup == 0) messengerService.insertUnsentMessage(convId, mes)
                else messengerService.insertUnsentMessageGroup(convId, mes)
                mes = mes.copy(id = id)
                _unsentMessageFlow.value = mes
            }
        }
    }

    suspend fun editMessage(messageId: Int, text: String?, images: List<String>?, voice: String?, file: String?) : Boolean {
        try {
            if(isGroup == 0) retrofitService.editMessage(convId, messageId, text, images, voice, file)
            else retrofitService.editGroupMessage(convId, messageId, text, images, voice, file)
            return true
        } catch (e: Exception) {
            return false
        }
    }

    suspend fun findMessage(idMessage: Int): Pair<Message, Int> {
        return if(isGroup == 0) retrofitService.findMessage(idMessage, convId)
        else retrofitService.findGroupMessage(idMessage, convId)
    }

    suspend fun getUnsentMessages(): List<Message>? {
        return if(isGroup == 0) messengerService.getUnsentMessages(convId)
        else messengerService.getUnsentMessagesGroup(convId)
    }

    suspend fun deleteUnsentMessage(messageId: Int) {
        messengerService.deleteUnsentMessage(messageId)
    }

    suspend fun sendUnsentMessage(mes: Message) : Boolean {
        val flag = try {
            val text = mes.text
            val encryptedText = encryptText(text)
            if(isGroup == 0) retrofitService.sendMessage(convId, encryptedText, mes.images, mes.voice,
                mes.file, mes.referenceToMessageId, mes.isForwarded, mes.usernameAuthorOriginal)
            else retrofitService.sendGroupMessage(convId, encryptedText, mes.images, mes.voice,
                mes.file, mes.referenceToMessageId, mes.isForwarded, mes.usernameAuthorOriginal)
        } catch (e: Exception) { false }
        return flag
    }

    suspend fun uploadPhoto(photo: File, context: Context, isVideo: Boolean): Pair<String, Boolean> {
        return try {
            val tempDir = context.cacheDir
            val tempFile = File(tempDir, photo.name)

            tinkAesGcmHelper?.encryptFile(photo, tempFile)

            val path = retrofitService.uploadPhoto(convId, tempFile, isGroup)

            val name = path.substringBeforeLast(".")

            val previewFile = if(isVideo) {
                imageUtils.createVideoPreview(photo, name, 300, 300)
            } else imageUtils.createImagePreview(context, photo, name, 300, 300)

            retrofitService.uploadPhotoPreview(convId, previewFile, isGroup)

            tempFile.delete()
            Pair(path, true)
        } catch (e: Exception) {
            Log.d("testUploadError", e.message.toString())
            Pair("", false)
        }
    }

    fun isVideoFile(file: File): Boolean {
        // Проверка по MIME-типу
        val mimeType = URLConnection.guessContentTypeFromName(file.name)
        if (mimeType?.startsWith("video/") == true) {
            return true
        }

        // Проверка по расширению
        val videoExtensions = setOf("mp4", "mkv", "avi", "mov", "flv", "wmv", "webm")
        val fileExtension = file.extension.lowercase()
        return fileExtension in videoExtensions
    }

    suspend fun uploadAudio(audio: File, context: Context): Pair<String, Boolean> {
        return try {
            val tempDir = context.cacheDir
            val tempFile = File(tempDir, audio.name)

            tinkAesGcmHelper?.encryptFile(audio, tempFile)

            val path = retrofitService.uploadAudio(convId, tempFile, isGroup)
            tempFile.delete()
            Pair(path, true)
        } catch (e: Exception) {
            Log.d("testUploadError", e.message.toString())
            Pair("", false)
        }
    }

    suspend fun uploadFile(file: File, context: Context): Pair<String, Boolean> {
        return try {
            val tempDir = context.cacheDir
            val tempFile = File(tempDir, file.name)

            tinkAesGcmHelper?.encryptFile(file, tempFile)

            val path = retrofitService.uploadFile(convId, tempFile, isGroup)
            tempFile.delete()
            Pair(path, true)
        } catch (e: Exception) {
            Log.d("testUploadError", e.message.toString())
            Pair("", false)
        }
    }

    suspend fun downloadFile(context: Context, folder: String, filename: String): String {
        val downloadedFilePath = retrofitService.downloadFile(context, folder, convId, filename, isGroup)
        val downloadedFile = File(downloadedFilePath)

        // Создаем временный файл для расшифровки
        val tempDir = context.cacheDir
        val tempFile = File(tempDir, "decrypted_${downloadedFile.name}")

        tinkAesGcmHelper?.decryptFile(downloadedFile, tempFile)

        // Заменяем исходный файл на расшифрованный
        downloadedFile.delete()
        tempFile.renameTo(downloadedFile)

        return downloadedFile.absolutePath
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

    fun fManagerIsExistAvatar(fileName: String): Boolean {
        return fileManager.isExistAvatar(fileName)
    }

    fun fManagerGetAvatarPath(fileName: String): String {
        return fileManager.getAvatarFilePath(fileName)
    }

    suspend fun fManagerSaveAvatar(fileName: String, fileData: ByteArray) = withContext(ioDispatcher) {
        fileManager.saveAvatarFile(fileName, fileData)
    }

    fun fManagerIsExistUnsent(fileName: String): Boolean {
        return fileManager.isExistUnsentMessage(fileName)
    }

    suspend fun fManagerDeleteUnsent(files: List<String>) = withContext(ioDispatcher) {
        fileManager.deleteFilesUnsent(files)
    }

    fun fManagerGetFilePathUnsent(fileName: String): String {
        return fileManager.getUnsentFilePath(fileName)
    }

    suspend fun fManagerSaveFileUnsent(fileName: String, fileData: ByteArray) = withContext(ioDispatcher) {
        fileManager.saveUnsentFile(fileName, fileData)
    }

    suspend fun fManagerGetFile(filePath: String): File? = withContext(ioDispatcher) {
        return@withContext fileManager.getFileFromPath(filePath)
    }

    fun formatMessageTime(timestamp: Long?): String {
        if (timestamp == null) return "-"

        val greenwichMessageDate = Calendar.getInstance().apply {
            timeInMillis = timestamp
        }
        val dateFormatToday = SimpleDateFormat("HH:mm", Locale.getDefault())
        return dateFormatToday.format(greenwichMessageDate.time)
    }

    fun formatMessageDate(timestamp: Long?): String {
        if (timestamp == null) return ""

        val greenwichMessageDate = Calendar.getInstance().apply {
            timeInMillis = timestamp
        }
        val dateFormatMonthDay = SimpleDateFormat("d MMMM", Locale.getDefault())
        val dateFormatYear = SimpleDateFormat("d MMMM yyyy", Locale.getDefault())
        val localNow = Calendar.getInstance()

        return when {
            isToday(localNow, greenwichMessageDate) -> dateFormatMonthDay.format(greenwichMessageDate.time)
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

    fun imageSet(image: String, imageView: ImageView, context: Context) {
        viewModelScope.launch {
            val filePathTemp = async {
                if (fManagerIsExist(image)) {
                    return@async fManagerGetFilePath(image)
                } else {
                    try {
                        return@async downloadFile(context, "photos", image)
                    } catch (e: Exception) {
                        return@async null
                    }
                }
            }
            val first = filePathTemp.await()
            if (first != null) {
                val file = File(first)
                if (file.exists()) {
                    val uri = Uri.fromFile(file)
                    imageView.visibility = View.VISIBLE
                    Glide.with(context)
                        .load(uri)
                        .centerCrop()
                        .placeholder(R.color.app_color_f6)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .into(imageView)
                }
            }
        }
    }

    suspend fun isNotificationsEnabled(): Boolean {
        val type = isGroup == 1
        return messengerService.isNotificationsEnabled(convId, type)
    }

    suspend fun downloadAvatar(context: Context, filename: String): String {
        return retrofitService.downloadAvatar(context, filename)
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

    fun formatTime(milliseconds: Long): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(milliseconds)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(milliseconds) % 60
        return String.format(Locale.ROOT,"%02d:%02d", minutes, seconds)
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

    fun processDateDuplicates(list: MutableList<Pair<Message, String>>): MutableList<Pair<Message, String>> {
        val seenDates = mutableSetOf<String>()
        for (i in list.indices.reversed()) {
            val pair = list[i]
            if (pair.second.isNotEmpty() && pair.second in seenDates) {
                list[i] = pair.copy(second = "")
            } else {
                seenDates.add(pair.second)
            }
        }
        return list
    }

    fun avatarSet(avatar: String, imageView: ImageView, context: Context) {
        viewModelScope.launch {
            if (avatar != "") {
                val filePathTemp = async {
                    if (fManagerIsExistAvatar(avatar)) {
                        return@async Pair(fManagerGetAvatarPath(avatar), true)
                    } else {
                        try {
                            return@async Pair(downloadAvatar(context, avatar), false)
                        } catch (e: Exception) {
                            return@async Pair(null, true)
                        }
                    }
                }
                val (first, second) = filePathTemp.await()
                if (first != null) {
                    val file = File(first)
                    if (file.exists()) {
                        if (!second) fManagerSaveAvatar(avatar, file.readBytes())
                        val uri = Uri.fromFile(file)
                        imageView.imageTintList = null
                        Glide.with(context)
                            .load(uri)
                            .apply(RequestOptions.circleCropTransform())
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .into(imageView)
                    }
                }
            }
        }
    }

}