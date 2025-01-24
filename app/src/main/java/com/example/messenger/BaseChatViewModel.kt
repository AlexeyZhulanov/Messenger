package com.example.messenger

import android.annotation.SuppressLint
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
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
import com.example.messenger.model.ChatSettings
import com.example.messenger.model.ConversationSettings
import com.example.messenger.model.FileManager
import com.example.messenger.model.Message
import com.example.messenger.model.MessengerService
import com.example.messenger.model.RetrofitService
import com.example.messenger.model.WebSocketService
import com.luck.picture.lib.config.PictureMimeType
import com.luck.picture.lib.entity.LocalMedia
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs

open class BaseChatViewModel(
    protected val messengerService: MessengerService,
    protected val retrofitService: RetrofitService,
    protected val fileManager: FileManager,
    protected val webSocketService: WebSocketService,
    protected val ioDispatcher: CoroutineDispatcher
) : ViewModel() {
    protected var convId: Int = -1
    protected var isGroup: Int = 0
    protected var isFirst = true
    private var disableRefresh: Boolean = false
    private var pendingRefresh: Boolean = false
    @SuppressLint("StaticFieldLeak")
    protected lateinit var recyclerView: RecyclerView
    protected var lastMessageDate: String = ""
    private var debounceJob: Job? = null

    protected val searchBy = MutableLiveData("")
    protected val currentPage = MutableStateFlow(0)

    protected val _newMessageFlow = MutableStateFlow<Pair<Message, String>?>(null)
    val newMessageFlow: StateFlow<Pair<Message, String>?> = _newMessageFlow

    protected val _typingState = MutableStateFlow(false)
    val typingState: StateFlow<Boolean> get() = _typingState

    protected val _deleteState = MutableStateFlow(0)
    val deleteState: StateFlow<Int> get() = _deleteState

    protected val _readMessagesFlow = MutableStateFlow<List<Int>>(emptyList())
    val readMessagesFlow: StateFlow<List<Int>> get() = _readMessagesFlow

    private val _unsentMessageFlow = MutableStateFlow<Message?>(null)
    val unsentMessageFlow: StateFlow<Message?> get() = _unsentMessageFlow

    protected val _editMessageFlow = MutableStateFlow<Message?>(null)
    val editMessageFlow: StateFlow<Message?> get() = _editMessageFlow

    private val tempFiles = mutableSetOf<String>()

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
        isFirst = false
        _newMessageFlow.value = null
        currentPage.value = 0
        _unsentMessageFlow.value = null
        _editMessageFlow.value = null
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

    open fun setConvInfo(convId: Int, otherUserId: Int, isGroup: Int) {
        this.convId = convId
        this.isGroup = isGroup
        updateLastSession()
    }

    fun bindRecyclerView(recyclerView: RecyclerView) {
        this.recyclerView = recyclerView
    }

    private fun updateLastSession() {
        viewModelScope.launch {
            delay(2000)
            try {
                retrofitService.updateLastSession(convId, isGroup)
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

    protected fun markMessagesAsRead(visibleMessages: List<Message>) {
        debounceJob?.cancel() // Отменяем предыдущий запрос, если он был

        debounceJob = viewModelScope.launch {
            delay(3000) // Задержка перед отправкой

            val messageIds = visibleMessages.map { it.id }
            if (messageIds.isNotEmpty()) {
                sendReadMessagesToServer(messageIds)
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

    fun sendMessage(text: String?, images: List<String>?, voice: String?, file: String?,
                    referenceToMessageId: Int?, isForwarded: Boolean,
                    usernameAuthorOriginal: String?, localFilePaths: List<String>?) {
        viewModelScope.launch {
            val flag = if (!localFilePaths.isNullOrEmpty()) { false }
            else {
                try {
                    if(isGroup == 0) retrofitService.sendMessage(convId, text, images, voice, file,
                        referenceToMessageId, isForwarded, usernameAuthorOriginal)
                    else retrofitService.sendGroupMessage(convId, text, images, voice, file,
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

    suspend fun updateAutoDeleteInterval(interval: Int) {
        if(isGroup == 0) retrofitService.updateAutoDeleteInterval(convId, interval)
        else retrofitService.updateGroupAutoDeleteInterval(convId, interval)
    }

    suspend fun toggleCanDeleteDialog() {
        if(isGroup == 0) retrofitService.toggleDialogCanDelete(convId)
        else retrofitService.toggleGroupCanDelete(convId)
    }

    suspend fun deleteAllMessages() {
        if(isGroup == 0) retrofitService.deleteDialogMessages(convId)
        else retrofitService.deleteGroupMessagesAll(convId)
        _deleteState.value = 1
    }

    suspend fun deleteConv() {
        if(isGroup == 0) retrofitService.deleteDialog(convId) else retrofitService.deleteGroup(convId)
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
            if(isGroup == 0) retrofitService.sendMessage(convId, mes.text, mes.images, mes.voice,
                mes.file, mes.referenceToMessageId, mes.isForwarded, mes.usernameAuthorOriginal)
            else retrofitService.sendGroupMessage(convId, mes.text, mes.images, mes.voice,
                mes.file, mes.referenceToMessageId, mes.isForwarded, mes.usernameAuthorOriginal)
        } catch (e: Exception) { false }
        return flag
    }

    suspend fun uploadPhoto(photo: File): Pair<String, Boolean> {
        val path = try {
            retrofitService.uploadPhoto(convId, photo, isGroup)
        } catch (e: Exception) {
            return Pair("", false)
        }
        return Pair(path, true)
    }

    suspend fun uploadAudio(audio: File): Pair<String, Boolean> {
        val path = try {
            retrofitService.uploadAudio(convId, audio, isGroup)
        } catch (e: Exception) {
            return Pair("", false)
        }
        return Pair(path, true)
    }

    suspend fun uploadFile(file: File): Pair<String, Boolean> {
        val path = try {
            retrofitService.uploadFile(convId, file, isGroup)
        } catch (e: Exception) {
            return Pair("", false)
        }
        return Pair(path, true)
    }

    suspend fun downloadFile(context: Context, folder: String, filename: String): String {
        return retrofitService.downloadFile(context, folder, convId, filename, isGroup)
    }

    fun downloadFileJava(context: Context, folder: String, filename: String): String {
        return runBlocking {
            retrofitService.downloadFile(context, folder, convId, filename, isGroup)
        }
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

    fun fManagerIsExistJava(filename: String): Boolean {
        return fileManager.isExistMessage(filename)
    }

    fun fManagerGetFilePathJava(fileName: String): String {
        return fileManager.getMessageFilePath(fileName)
    }

    fun fManagerSaveFileJava(fileName: String, fileData: ByteArray) {
        fileManager.saveMessageFile(fileName, fileData)
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

    suspend fun turnNotifications() {
        val type = isGroup == 1
        val isEnabled = messengerService.isNotificationsEnabled(convId, type)
        if(isEnabled) messengerService.insertChatSettings(ChatSettings(convId, type))
        else messengerService.deleteChatSettings(convId, type)
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

    suspend fun downloadAvatar(context: Context, filename: String): String {
        return retrofitService.downloadAvatar(context, filename)
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

    fun addTempFile(filename: String) = tempFiles.add(filename)

    fun clearTempFiles() {
        fileManager.deleteFilesMessage(tempFiles.toList())
        tempFiles.clear()
    }
}