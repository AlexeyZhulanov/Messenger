package com.example.messenger

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextPaint
import android.text.style.ClickableSpan
import android.util.Base64
import android.util.Log
import android.view.View
import android.widget.ImageView
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.messenger.di.IoDispatcher
import com.example.messenger.model.FileManager
import com.example.messenger.utils.ImageUtils
import com.example.messenger.model.Message
import com.example.messenger.model.MessengerService
import com.example.messenger.model.RetrofitService
import com.example.messenger.model.WebSocketService
import com.example.messenger.model.appsettings.AppSettings
import com.example.messenger.security.ChatKeyManager
import com.example.messenger.security.TinkAesGcmHelper
import com.example.messenger.states.FileState
import com.example.messenger.states.ImageState
import com.example.messenger.states.ImagesState
import com.example.messenger.states.MessageUi
import com.example.messenger.states.VoiceState
import com.luck.picture.lib.config.PictureMimeType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.URLConnection
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import androidx.core.net.toUri
import com.example.messenger.states.AvatarState
import com.example.messenger.states.ReplyPreview
import com.example.messenger.states.ReplyState
import com.example.messenger.utils.chunkedFlowLast
import kotlinx.coroutines.flow.buffer
import kotlin.collections.plus

abstract class BaseChatViewModel(
    protected val messengerService: MessengerService,
    protected val retrofitService: RetrofitService,
    protected val fileManager: FileManager,
    protected val webSocketService: WebSocketService,
    private val appSettings: AppSettings,
    @param:IoDispatcher protected val ioDispatcher: CoroutineDispatcher
) : ViewModel() {
    protected var convId: Int = -1
    private var isGroup: Int = 0
    protected var currentUserId: Int = -1
    protected var disableRefresh: Boolean = false
    protected var pendingRefresh: Boolean = false
    protected var isLoadingPage = false
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

    protected val _arrowTriggerFlow = MutableSharedFlow<Unit>()
    val arrowTriggerFlow: SharedFlow<Unit> = _arrowTriggerFlow

    protected val _scrollTriggerFlow = MutableSharedFlow<Unit>()
    val scrollTriggerFlow: SharedFlow<Unit> = _scrollTriggerFlow

    protected val _typingState = MutableStateFlow<Pair<Boolean, String?>>(Pair(false, null))
    val typingState: StateFlow<Pair<Boolean, String?>> get() = _typingState

    protected val _deleteState = MutableStateFlow(0)
    val deleteState: StateFlow<Int> get() = _deleteState

    protected val _messagesUi = MutableStateFlow<List<MessageUi>>(emptyList())
    val messagesUi: StateFlow<List<MessageUi>> = _messagesUi

    private val linkPattern = Regex("""\[([^]]+)]\((https?://[^)]+)\)|(https?://\S+)""")

    private val preloadSemaphore = Semaphore(3)
    private val voiceCache = mutableMapOf<Int, VoiceState>()
    private val fileCache = mutableMapOf<Int, FileState>()
    private val imageCache = mutableMapOf<Int, ImageState>()
    private val imagesCache = mutableMapOf<Int, ImagesState>()
    private val loadingIds = mutableSetOf<Int>()
    private val avatarCache = mutableMapOf<String, String>()
    private val avatarLoading = mutableSetOf<String>()
    private val replyCache = mutableMapOf<Int, ReplyState>()
    private val replyLoading = mutableSetOf<Int>()

    abstract fun applyGroupDisplayInfo(list: List<MessageUi>): List<MessageUi>
    abstract fun preloadAttachments(list: List<MessageUi>)

    init {
        viewModelScope.launch {
            webSocketService.newMessageFlow
                .buffer()
                .chunkedFlowLast(200)
                .collect { newMessages ->
                    if(!disableRefresh) {
                        if (newMessages.isEmpty()) return@collect
                        val decrypted = newMessages.map { mes ->
                            mes.text?.let { tinkAesGcmHelper?.decryptText(it) }
                            mes
                        }
                        Log.d("testSocketsMessage", "New Messages: $newMessages")
                        val unreadMessages = decrypted.filter {
                            if(it.isPersonalUnread == null) {
                                currentUserId != it.idSender && !it.isRead
                            } else {
                                currentUserId != it.idSender && it.isPersonalUnread == true
                            }
                        }
                        if (unreadMessages.isNotEmpty()) {
                            markMessagesAsRead(unreadMessages)
                        }
                        val newUi = decrypted.map {mes ->
                            val newMessageTriple =
                                if(lastMessageDate == "") Triple(mes,"", formatMessageTime(mes.timestamp))
                                else Triple(mes,formatMessageDate(mes.timestamp), formatMessageTime(mes.timestamp))
                            toMessageUi(newMessageTriple, true)
                        }
                        newUi.lastOrNull()?.let { updateLastDate(it.message.timestamp) }
                        _messagesUi.update { old ->
                            applyGroupDisplayInfo(newUi + old)
                        }
                        preloadAttachments(newUi)
                        _arrowTriggerFlow.emit(Unit)
                    } else pendingRefresh = true
                }
        }
        viewModelScope.launch {
            webSocketService.editMessageFlow.collect { message ->
                if(!disableRefresh) {
                    message.text = message.text?.let { tinkAesGcmHelper?.decryptText(it) }
                    Log.d("testSocketsMessage", "Edited Message: $message")
                    _messagesUi.update { old ->
                        val idx = old.indexOfFirst { it.message.id == message.id }
                        if(idx != -1) {
                            val element = old[idx]
                            val triple = Triple(element.message, element.formattedDate, element.formattedTime)
                            val newUi = toMessageUi(triple, true)
                            preloadAttachments(listOf(newUi))
                            old.toMutableList().apply {
                                this[idx] = newUi.copy(
                                    username = element.username,
                                    showUsername = element.showUsername,
                                    showAvatar = element.showAvatar,
                                    avatarState = element.avatarState
                                )
                            }
                        } else old
                    }
                } else pendingRefresh = true
            }
        }
        viewModelScope.launch {
            webSocketService.deleteMessageFlow.collect { event ->
                if(!disableRefresh) {
                    Log.d("testSocketsMessage", "Deleted messages ids: ${event.deletedMessagesIds}")
                    clearSelection()
                    _messagesUi.update { list ->
                        list.filterNot { it.message.id in event.deletedMessagesIds }
                    }
                    //_scrollTriggerFlow.emit(Unit)
                } else pendingRefresh = true
            }
        }
        viewModelScope.launch {
            webSocketService.readMessageFlow.collect { event ->
                Log.d("testSocketsMessage", "Messages read: ${event.messagesReadIds}")
                _messagesUi.update { old ->
                    old.map { ui ->
                        if(ui.message.id in event.messagesReadIds) {
                            ui.copy(message = ui.message.copy(isRead = true))
                        } else ui
                    }
                }
            }
        }
        viewModelScope.launch {
            webSocketService.deleteAllMessageFlow.collect {
                Log.d("testSocketsMessage", "All messages deleted")
                _deleteState.value = 1
                refresh()
            }
        }
        viewModelScope.launch {
            webSocketService.dialogDeletedFlow.collect {
                Log.d("testSocketsMessage", "Dialog deleted")
                _deleteState.value = 2
            }
        }
    }

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
        this.searchBy.value = ""
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

    fun setConvInfo(convId: Int, isGroup: Int, chatKey: String, userId: Int, context: Context) {
        this.convId = convId
        this.isGroup = isGroup
        this.currentUserId = userId

        val chatTypeString = if(isGroup == 0) "dialog" else "group"
        val aead = chatKeyManager.getAead(convId, chatTypeString)
        if(aead != null) {
            tinkAesGcmHelper = TinkAesGcmHelper(aead)
        } else {
            if(chatKey != "") {
                val wrappedKey = Base64.decode(chatKey, Base64.NO_WRAP)
                try {
                    chatKeyManager.unwrapChatKeyForSave(wrappedKey, convId, chatTypeString, userId)
                } catch (e: Exception) {
                    Log.d("testPrivateKeyMissed", e.message.toString())
                    logout(context)
                }
                val newAead = chatKeyManager.getAead(convId, chatTypeString)
                if(newAead != null) {
                    tinkAesGcmHelper = TinkAesGcmHelper(newAead)
                } else Log.d("testErrorTinkInit", "newAead is null")
            } else Log.d("testErrorTinkInit", "chatKey is null")
        }

        viewModelScope.launch {
            _initFlow.emit(Unit)
        }

        updateLastSession()
    }

    private fun logout(context: Context) {
        appSettings.setCurrentAccessToken(null)
        appSettings.setCurrentRefreshToken(null)
        appSettings.setRemember(false)
        context.sendBroadcast(Intent("com.example.messenger.LOGOUT"))
    }

    private fun updateLastSession() {
        viewModelScope.launch {
            delay(2000)
            try {
                retrofitService.updateLastSession()
            } catch (_: Exception) { return@launch }
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

    fun searchMessagesInDialog(query: String) {
        if(this.searchBy.value == query) return
        this.searchBy.value = query
    }

    suspend fun deleteMessages(ids: List<Int>): Boolean {
        val response = try {
            if(isGroup == 0) retrofitService.deleteMessages(convId, ids)
            else retrofitService.deleteGroupMessages(convId, ids)
        } catch (_: Exception) { return false }
        return response
    }

    private fun encryptText(text: String?) : String? {
        Log.d("testEncryptTink", text.toString())
        val res = text?.let { tinkAesGcmHelper?.encryptText(it) }
        Log.d("testEncryptTink", res.toString())
        return res
    }

    private fun encryptCode(code: String?) : String? {
        return code?.let { tinkAesGcmHelper?.encryptText(it) }
    }

    fun sendMessage(text: String?, images: List<String>?, voice: String?, file: String?, code: String?,
                    codeLanguage: String?, referenceToMessageId: Int?, isForwarded: Boolean,
                    isUrl: Boolean?, usernameAuthorOriginal: String?, localFilePaths: List<String>?,
                    waveform: List<Int>?) {
        viewModelScope.launch {
            val flag = if (!localFilePaths.isNullOrEmpty()) { false }
            else {
                try {
                    val encryptedText = encryptText(text)
                    val encryptedCode = encryptCode(code)
                    if(isGroup == 0) retrofitService.sendMessage(convId, encryptedText, images, voice,
                        file, encryptedCode, codeLanguage, referenceToMessageId, isForwarded, isUrl, usernameAuthorOriginal, waveform)
                    else retrofitService.sendGroupMessage(convId, encryptedText, images, voice, file,
                        encryptedCode, codeLanguage, referenceToMessageId, isForwarded, isUrl, usernameAuthorOriginal, waveform)
                } catch (_: Exception) { false }
            }
            if(!flag) {
                var mes = Message(id = 0, idSender = -5, text = text, images = images, voice = voice,
                    file = file, code = code, codeLanguage = codeLanguage, referenceToMessageId = referenceToMessageId,
                    isForwarded = isForwarded, usernameAuthorOriginal = usernameAuthorOriginal, timestamp = 0,
                    isEdited = false, isUrl = isUrl, isUnsent = true, localFilePaths = localFilePaths)
                val id = if(isGroup == 0) messengerService.insertUnsentMessage(convId, mes)
                else messengerService.insertUnsentMessageGroup(convId, mes)
                mes = mes.copy(id = id)
                _messagesUi.update { old ->
                    val newUi = toMessageUi(Triple(mes, "", ""), false)
                    applyGroupDisplayInfo(listOf(newUi) + old)
                }
                _scrollTriggerFlow.emit(Unit)
            }
        }
    }

    suspend fun editMessage(messageId: Int, text: String?, images: List<String>?, voice: String?,
                            file: String?, code: String?, codeLanguage: String?, isUrl: Boolean?) : Boolean {
        try {
            val encryptedText = encryptText(text)
            val encryptedCode = encryptCode(code)
            if(isGroup == 0) retrofitService.editMessage(convId, messageId, encryptedText, images, voice, file, encryptedCode, codeLanguage, isUrl)
            else retrofitService.editGroupMessage(convId, messageId, encryptedText, images, voice, file, encryptedCode, codeLanguage, isUrl)
            return true
        } catch (_: Exception) {
            return false
        }
    }

    suspend fun findMessage(idMessage: Int): Pair<Message, Int>? {
        return try {
            if(isGroup == 0) retrofitService.findMessage(idMessage, convId)
            else retrofitService.findGroupMessage(idMessage, convId)
        } catch (_: Exception) { null }
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
            val code = mes.code
            val encryptedCode = encryptCode(code)
            if(isGroup == 0) retrofitService.sendMessage(convId, encryptedText, mes.images, mes.voice,
                mes.file, encryptedCode, mes.codeLanguage, mes.referenceToMessageId, mes.isForwarded, mes.isUrl, mes.usernameAuthorOriginal, mes.waveform)
            else retrofitService.sendGroupMessage(convId, encryptedText, mes.images, mes.voice,
                mes.file, encryptedCode, mes.codeLanguage, mes.referenceToMessageId, mes.isForwarded, mes.isUrl, mes.usernameAuthorOriginal, mes.waveform)
        } catch (_: Exception) { false }
        return flag
    }

    suspend fun uploadPhoto(photo: File, context: Context, isVideo: Boolean): Pair<String, Boolean> {
        return try {
            val name = photo.name.substringBeforeLast(".")

            val previewFile = if(isVideo) {
                imageUtils.createVideoPreview(context, photo, name, 300, 300)
            } else imageUtils.createImagePreview(context, photo, name, 300, 300)

            val path = if(isVideo) {
                withContext(ioDispatcher) {
                    val tempDir = context.cacheDir
                    val tempFile = File(tempDir, photo.name)
                    tinkAesGcmHelper?.encryptFile(photo, tempFile)
                    val p = retrofitService.uploadPhoto(convId, tempFile, isGroup)
                    tempFile.delete()
                    return@withContext p
                }
            } else {
                withContext(ioDispatcher) {
                    tinkAesGcmHelper?.encryptFile(photo, photo)
                    val p = retrofitService.uploadPhoto(convId, photo, isGroup)
                    photo.delete()
                    return@withContext p
                }
            }
            tinkAesGcmHelper?.encryptFile(previewFile, previewFile)
            retrofitService.uploadPhotoPreview(convId, previewFile, isGroup)
            previewFile.delete()
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

            val path = withContext(ioDispatcher) {
                tinkAesGcmHelper?.encryptFile(audio, tempFile)
                return@withContext retrofitService.uploadAudio(convId, tempFile, isGroup)
            }
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

            val path = withContext(ioDispatcher) {
                tinkAesGcmHelper?.encryptFile(file, tempFile)
                return@withContext retrofitService.uploadFile(convId, tempFile, isGroup)
            }
            tempFile.delete()
            Pair(path, true)
        } catch (e: Exception) {
            Log.d("testUploadError", e.message.toString())
            Pair("", false)
        }
    }

    suspend fun downloadFile(folder: String, filename: String): String = withContext(ioDispatcher) {
        val downloadedFilePath = retrofitService.downloadFile(folder, convId, filename, isGroup)
        val downloadedFile = File(downloadedFilePath)

        tinkAesGcmHelper?.decryptFile(downloadedFile, downloadedFile)

        return@withContext downloadedFile.absolutePath
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

    fun formatMessageTime(timestamp: Long): String {
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

    // todo это нужно отсюда перенести
    fun imageSet(image: String, imageView: ImageView, context: Context) {
        viewModelScope.launch {
            val filePathTemp = async {
                if (fileManager.isExistMessage(image)) {
                    return@async fileManager.getMessageFilePath(image)
                } else {
                    try {
                        return@async downloadFile("photos", image)
                    } catch (_: Exception) {
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

    suspend fun downloadAvatar(filename: String): String {
        return retrofitService.downloadAvatar(filename)
    }

    suspend fun fileToPrepareLocalMedia(filePath: String): Pair<String, Long> = withContext(ioDispatcher) {
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

    fun processDateDuplicates(list: List<MessageUi>): List<MessageUi> {
        val seenDates = mutableSetOf<String>()

        return list.asReversed().map { ui ->
            if (ui.formattedDate.isNotEmpty() && ui.formattedDate in seenDates) {
                ui.copy(formattedDate = "")
            } else {
                seenDates.add(ui.formattedDate)
                ui
            }
        }.asReversed()
    }

    fun getWallpaper(isDark: Boolean): String {
        return if(isDark) appSettings.getDarkWallpaper() else appSettings.getLightWallpaper()
    }
        // todo на всякий случай сохранил
//    fun avatarSet(avatar: String, imageView: ImageView, context: Context) {
//        if (avatar != "") {
//            viewModelScope.launch {
//                val uriCached = avatarCache[avatar]
//                if(uriCached != null) {
//                    imageView.imageTintList = null
//                    Glide.with(context)
//                        .load(uriCached)
//                        .apply(RequestOptions.circleCropTransform())
//                        .diskCacheStrategy(DiskCacheStrategy.ALL)
//                        .into(imageView)
//                } else {
//                    val filePathTemp = async {
//                        if (fManagerIsExistAvatar(avatar)) {
//                            return@async Pair(fManagerGetAvatarPath(avatar), true)
//                        } else {
//                            try {
//                                return@async Pair(downloadAvatar(avatar), false)
//                            } catch (_: Exception) {
//                                return@async Pair(null, true)
//                            }
//                        }
//                    }
//                    val (first, second) = filePathTemp.await()
//                    if (first != null) {
//                        val file = File(first)
//                        if (file.exists()) {
//                            if (!second) fManagerSaveAvatar(avatar, file.readBytes())
//                            val uri = Uri.fromFile(file)
//                            avatarCache[avatar] = uri
//                            imageView.imageTintList = null
//                            Glide.with(context)
//                                .load(uri)
//                                .apply(RequestOptions.circleCropTransform())
//                                .diskCacheStrategy(DiskCacheStrategy.ALL)
//                                .into(imageView)
//                        }
//                    }
//                }
//            }
//        }
//    }

    protected fun toMessageUi(triple: Triple<Message, String, String>, isFirstPage: Boolean): MessageUi {
        val (message, date, time) = triple

        val parsedText = message.text?.let {
            if (message.isUrl == true) parseMessageWithLinks(it) else it
        }

        return MessageUi(
            message = message,
            formattedDate = date,
            formattedTime = time,
            parsedText = parsedText,
            isFirstPage = isFirstPage,
            voiceState = if (message.voice != null) VoiceState.Loading else null,
            fileState = if (message.file != null) FileState.Loading else null,
            imageState = if (message.images?.size == 1) ImageState.Loading else null,
            imagesState = if ((message.images?.size ?: 0) > 1) ImagesState.Loading else null,
            replyState = if(message.referenceToMessageId != null) ReplyState.Loading else null
        )
    }

    protected fun preloadVoice(ui: MessageUi) {
        val id = ui.message.id

        // Уже загружено?
        if (voiceCache.containsKey(id)) {
            updateVoiceState(id, voiceCache[id]!!)
            return
        }
        // Уже загружается?
        if (loadingIds.contains(id)) return

        loadingIds.add(id)
        viewModelScope.launch {
            val result = preloadSemaphore.withPermit {
                withContext(ioDispatcher) {
                    try {
                        val voiceName = ui.message.voice ?: return@withContext VoiceState.Error
                        val localPath = when {
                            ui.message.isUnsent == true -> {
                                ui.message.localFilePaths?.firstOrNull() ?: return@withContext VoiceState.Error
                            }
                            fileManager.isExistMessage(voiceName) -> {
                                fileManager.getMessageFilePath(voiceName)
                            }
                            else -> {
                                val path = downloadFile("audio", voiceName)
                                if(ui.isFirstPage) {
                                    fileManager.saveMessageFile(voiceName, File(path).readBytes())
                                }
                                path
                            }
                        }
                        val duration = readDuration(localPath)
                        VoiceState.Ready(localPath, duration)
                    } catch (_: Exception) {
                        VoiceState.Error
                    }
                }
            }
            voiceCache[id] = result
            loadingIds.remove(id)
            updateVoiceState(id, result)
        }
    }

    private fun updateVoiceState(id: Int, state: VoiceState) {
        _messagesUi.update { list ->
            list.map {
                if (it.message.id == id) {
                    it.copy(voiceState = state)
                } else it
            }
        }
    }

    protected fun preloadFile(ui: MessageUi) {
        val id = ui.message.id

        if (fileCache.containsKey(id)) {
            updateFileState(id, fileCache[id]!!)
            return
        }
        if (loadingIds.contains(id)) return
        loadingIds.add(id)

        viewModelScope.launch {
            val result = preloadSemaphore.withPermit {
                withContext(ioDispatcher) {
                    try {
                        val fileName = ui.message.file ?: return@withContext FileState.Error
                        val localPath = when {
                            ui.message.isUnsent == true -> {
                                ui.message.localFilePaths?.firstOrNull() ?: return@withContext FileState.Error
                            }
                            fileManager.isExistMessage(fileName) -> {
                                fileManager.getMessageFilePath(fileName)
                            }
                            else -> {
                                val path = downloadFile("files", fileName)
                                if(ui.isFirstPage) {
                                    fileManager.saveMessageFile(fileName, File(path).readBytes())
                                }
                                path
                            }
                        }
                        val file = File(localPath)

                        FileState.Ready(
                            localPath = localPath,
                            fileName = file.name,
                            fileSize = formatFileSize(file.length())
                        )
                    } catch (_: Exception) {
                        FileState.Error
                    }
                }
            }
            fileCache[id] = result
            loadingIds.remove(id)
            updateFileState(id, result)
        }
    }

    private fun updateFileState(id: Int, state: FileState) {
        _messagesUi.update { list ->
            list.map {
                if (it.message.id == id) {
                    it.copy(fileState = state)
                } else it
            }
        }
    }

    protected fun preloadImage(ui: MessageUi) {
        val id = ui.message.id

        if (imageCache.containsKey(id)) {
            updateImageState(id, imageCache[id]!!)
            return
        }
        if (loadingIds.contains(id)) return
        loadingIds.add(id)

        viewModelScope.launch {
            val result = preloadSemaphore.withPermit {
                withContext(ioDispatcher) {
                    try {
                        val imageName = ui.message.images?.first() ?: return@withContext ImageState.Error
                        val localPath = when {
                            ui.message.isUnsent == true -> {
                                ui.message.localFilePaths?.firstOrNull() ?: return@withContext ImageState.Error
                            }
                            fileManager.isExistMessage(imageName) -> {
                                fileManager.getMessageFilePath(imageName)
                            }
                            else -> {
                                val path = downloadFile("photos", imageName)
                                if(ui.isFirstPage) {
                                    fileManager.saveMessageFile(imageName, File(path).readBytes())
                                }
                                path
                            }
                        }
                        val (mimeType, duration) = fileToPrepareLocalMedia(localPath)
                        ImageState.Ready(localPath, mimeType, duration)
                    } catch (_: Exception) {
                        ImageState.Error
                    }
                }
            }
            imageCache[id] = result
            loadingIds.remove(id)
            updateImageState(id, result)
        }
    }

    private fun updateImageState(id: Int, state: ImageState) {
        _messagesUi.update { list ->
            list.map {
                if (it.message.id == id) {
                    it.copy(imageState = state)
                } else it
            }
        }
    }

    protected fun preloadImages(ui: MessageUi) {
        val id = ui.message.id

        if (imagesCache.containsKey(id)) {
            updateImagesState(id, imagesCache[id]!!)
            return
        }
        if (loadingIds.contains(id)) return
        loadingIds.add(id)

        viewModelScope.launch {
            val result = preloadSemaphore.withPermit {
                withContext(ioDispatcher) {
                    try {
                        val imageNames = ui.message.images ?: return@withContext ImagesState.Error

                        val localPaths = imageNames.mapIndexed { index, imageName ->
                            when {
                                ui.message.isUnsent == true -> {
                                    ui.message.localFilePaths?.get(index) ?: return@withContext ImagesState.Error
                                }
                                fileManager.isExistMessage(imageName) -> {
                                    fileManager.getMessageFilePath(imageName)
                                }
                                else -> {
                                    val path = downloadFile("photos", imageName)
                                    if(ui.isFirstPage) {
                                        fileManager.saveMessageFile(imageName, File(path).readBytes())
                                    }
                                    path
                                }
                            }
                        }
                        val processed = localPaths.map { fileToPrepareLocalMedia(it) }
                        val (mimeTypes, durations) = processed.map { it.first } to processed.map { it.second }
                        ImagesState.Ready(localPaths, mimeTypes, durations)
                    } catch (_: Exception) {
                        ImagesState.Error
                    }
                }
            }
            imagesCache[id] = result
            loadingIds.remove(id)
            updateImagesState(id, result)
        }
    }

    private fun updateImagesState(id: Int, state: ImagesState) {
        _messagesUi.update { list ->
            list.map {
                if (it.message.id == id) {
                    it.copy(imagesState = state)
                } else it
            }
        }
    }

    protected fun preloadAvatar(messageId: Int, avatar: String) {
        if (avatar.isBlank()) return

        // Уже есть?
        avatarCache[avatar]?.let { path ->
            updateAvatarState(messageId, AvatarState.Ready(path))
            return
        }

        // Уже грузится?
        if (avatarLoading.contains(avatar)) return

        avatarLoading.add(avatar)

        viewModelScope.launch {
            val result = preloadSemaphore.withPermit {
                withContext(ioDispatcher) {
                    try {
                        val localPath =
                            if (fileManager.isExistAvatar(avatar)) {
                                fileManager.getAvatarFilePath(avatar)
                            } else {
                                val downloaded = downloadAvatar(avatar)
                                fileManager.saveAvatarFile(
                                    avatar,
                                    File(downloaded).readBytes()
                                )
                                downloaded
                            }

                        avatarCache[avatar] = localPath
                        AvatarState.Ready(localPath)
                    } catch (_: Exception) {
                        AvatarState.Error
                    }
                }
            }

            avatarLoading.remove(avatar)
            updateAvatarState(messageId, result)
        }
    }

    private fun updateAvatarState(messageId: Int, state: AvatarState) {
        _messagesUi.update { list ->
            list.map {
                if (it.message.id == messageId) {
                    it.copy(avatarState = state)
                } else it
            }
        }
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

    protected fun parseMessageWithLinks(text: String): CharSequence {
        if (!text.contains("""\[[^]]+]\(https?://[^)]+\)""".toRegex())
            && !text.contains("https?://\\S+".toRegex())
        ) {
            return text
        }

        val spannable = SpannableStringBuilder(text)
        val matches = linkPattern.findAll(text).toList()

        matches.reversed().forEach { match ->
            when {
                match.groups[1] != null && match.groups[2] != null -> {
                    val (linkText, url) = match.destructured
                    val start = match.range.first
                    val end = match.range.last + 1

                    spannable.replace(start, end, linkText)
                    applyLinkStyle(spannable, start, start + linkText.length, url)
                }

                match.groups[3] != null -> {
                    val url = match.groups[3]!!.value
                    val start = match.range.first
                    val end = match.range.last + 1

                    applyLinkStyle(spannable, start, end, url)
                }
            }
        }
        return spannable
    }

    private fun applyLinkStyle(spannable: SpannableStringBuilder, start: Int, end: Int, url: String) {
        spannable.setSpan(
            object : ClickableSpan() {
                override fun onClick(widget: View) {
                    widget.context.startActivity(
                        Intent(Intent.ACTION_VIEW, url.toUri())
                    )
                }
                override fun updateDrawState(ds: TextPaint) {
                    ds.color = Color.CYAN
                    ds.isUnderlineText = true
                }
            }, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
    }

    protected fun preloadReply(ui: MessageUi) {
        val refId = ui.message.referenceToMessageId ?: return

        // Уже готово?
        replyCache[refId]?.let { cached ->
            updateReplyState(ui.message.id, cached)
            return
        }
        // Уже загружается?
        if (replyLoading.contains(refId)) return

        replyLoading.add(refId)

        viewModelScope.launch {
            val result = preloadSemaphore.withPermit {
                withContext(ioDispatcher) {
                    try {
                        val existing = _messagesUi.value.firstOrNull { it.message.id == refId }
                        if (existing != null) {
                            val preview = buildReplyPreview(existing.message)

                            val imagePath = preview.imageName?.let {
                                preloadReplyImage(it)
                            }
                            return@withContext ReplyState.Ready(
                                referenceMessageId = refId,
                                previewText = preview.text,
                                previewImagePath = imagePath,
                                username = preview.username
                            )
                        }
                        val local = findMessage(refId)?.first
                            ?: return@withContext ReplyState.Error

                        val preview = buildReplyPreview(local)

                        val imagePath = preview.imageName?.let {
                            preloadReplyImage(it)
                        }
                        ReplyState.Ready(
                            referenceMessageId = refId,
                            previewText = preview.text,
                            previewImagePath = imagePath,
                            username = preview.username
                        )

                    } catch (_: Exception) {
                        ReplyState.Error
                    }
                }
            }
            replyCache[refId] = result
            replyLoading.remove(refId)
            updateReplyState(ui.message.id, result)
        }
    }

    private suspend fun preloadReplyImage(imageName: String): String? = withContext(ioDispatcher) {
        try {
            if (fileManager.isExistMessage(imageName)) {
                fileManager.getMessageFilePath(imageName)
            } else {
                downloadFile("photos", imageName)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun buildReplyPreview(message: Message): ReplyPreview {
        val text = when {
            message.text != null -> message.text!!
            message.images != null -> "Фотография"
            message.file != null -> message.file!!
            message.voice != null -> "Голосовое сообщение"
            else -> "Сообщение"
        }
        val name = message.images?.firstOrNull()

        return ReplyPreview(
            text = text,
            imageName = name,
            username = message.usernameAuthorOriginal
        )
    }

    private fun updateReplyState(messageId: Int, state: ReplyState) {
        _messagesUi.update { list ->
            list.map {
                if (it.message.id == messageId)
                    it.copy(replyState = state)
                else it
            }
        }
    }

    fun highlightMessage(id: Int) {
        _messagesUi.update { list ->
            list.map {
                it.copy(isHighlighted = it.message.id == id)
            }
        }
        viewModelScope.launch {
            delay(1300)
            _messagesUi.update { list ->
                list.map { it.copy(isHighlighted = false) }
            }
        }
    }

    fun toggleSelection(messageId: Int) {
        _messagesUi.update { list ->
            list.map {
                if (it.message.id == messageId)
                    it.copy(isSelected = !it.isSelected)
                else it
            }
        }
    }

    fun getSelectedMessages(): List<Message> {
        return _messagesUi.value
            .filter { it.isSelected }
            .map { it.message }
    }

    fun toggleCheckboxes(messageId: Int) {
        _messagesUi.update { list ->
            list.map {
                if (it.message.id == messageId)
                    it.copy(isShowCheckbox = true, isSelected = true)
                else it.copy(isShowCheckbox = true)
            }
        }
    }

    fun clearSelection() {
        _messagesUi.update { list ->
            list.map { it.copy(isSelected = false, isShowCheckbox = false) }
        }
    }
}