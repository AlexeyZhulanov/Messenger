package com.example.messenger

import android.annotation.SuppressLint
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import android.view.View
import android.widget.ImageView
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.messenger.di.IoDispatcher
import com.example.messenger.model.ChatSettings
import com.example.messenger.model.ConversationSettings
import com.example.messenger.model.DeletedMessagesEvent
import com.example.messenger.model.DialogCreatedEvent
import com.example.messenger.model.DialogDeletedEvent
import com.example.messenger.model.DialogMessagesAllDeleted
import com.example.messenger.model.FileManager
import com.example.messenger.model.Message
import com.example.messenger.model.MessagePagingSource
import com.example.messenger.model.MessengerService
import com.example.messenger.model.ReadMessagesEvent
import com.example.messenger.model.RetrofitService
import com.example.messenger.model.TypingEvent
import com.example.messenger.model.UserSessionUpdatedEvent
import com.example.messenger.model.WebSocketListenerInterface
import com.example.messenger.model.WebSocketService
import com.luck.picture.lib.config.PictureMimeType
import com.luck.picture.lib.entity.LocalMedia
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.math.abs

@HiltViewModel
class MessageViewModel @Inject constructor(
    private val messengerService: MessengerService,
    private val retrofitService: RetrofitService,
    private val fileManager: FileManager,
    private val webSocketService: WebSocketService,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ViewModel(), WebSocketListenerInterface {

    private val _lastSessionString = MutableLiveData<String>()
    val lastSessionString: LiveData<String> get() = _lastSessionString

    private var dialogId: Int = -1
    private var otherUserId: Int = -1
    private var isFirst = true
    private var disableRefresh: Boolean = false
    private var pendingRefresh: Boolean = false
    @SuppressLint("StaticFieldLeak")
    private lateinit var recyclerView: RecyclerView
    private var lastMessageDate: String = ""
    private var debounceJob: Job? = null

    private val searchBy = MutableLiveData("")
    private val currentPage = MutableStateFlow(0)

    private val _newMessageFlow = MutableStateFlow<Pair<Message, String>?>(null)
    val newMessageFlow: StateFlow<Pair<Message, String>?> = _newMessageFlow

    private val _typingState = MutableStateFlow(false)
    val typingState: StateFlow<Boolean> get() = _typingState

    private val _deleteState = MutableStateFlow(0)
    val deleteState: StateFlow<Int> get() = _deleteState

    private val _readMessagesFlow = MutableStateFlow<List<Int>>(emptyList())
    val readMessagesFlow: StateFlow<List<Int>> get() = _readMessagesFlow

    private val _unsentMessageFlow = MutableStateFlow<Message?>(null)
    val unsentMessageFlow: StateFlow<Message?> get() = _unsentMessageFlow

    private val _editMessageFlow = MutableStateFlow<Message?>(null)
    val editMessageFlow: StateFlow<Message?> get() = _editMessageFlow

    private val tempFiles = mutableSetOf<String>()

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val pagingDataFlow = searchBy.asFlow()
        .debounce(500)
        .flatMapLatest { searchQuery ->
            currentPage.flatMapLatest { page ->
                flow {
                    val pageSize = 30
                    val messages = MessagePagingSource(retrofitService, messengerService, dialogId,
                        searchQuery, isFirst, fileManager).loadPage(page, pageSize)
                    emit(messages)
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

    init {
        webSocketService.setListener(this)
        webSocketService.connect()
    }

    fun setDialogInfo(dialogId: Int, otherUserId: Int) {
        this.dialogId = dialogId
        this.otherUserId = otherUserId
        joinDialog()
        updateLastSession()
    }

    fun setRecyclerView(recyclerView: RecyclerView) {
        this.recyclerView = recyclerView
    }

    fun fetchLastSession() {
        viewModelScope.launch {
            try {
                val session = retrofitService.getLastSession(otherUserId)
                _lastSessionString.value = formatUserSessionDate(session)
            } catch (e: Exception) {
                _lastSessionString.value = "Unknown"
            }
        }
    }

    private fun updateLastSession() {
        viewModelScope.launch {
            delay(2000)
            try {
                retrofitService.updateLastSession(dialogId)
            } catch (e: Exception) { return@launch }
        }
    }

    fun setMarkScrollListener(recyclerView: RecyclerView, adapter: MessageAdapter) {
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                val lastVisibleItemPosition = layoutManager.findLastVisibleItemPosition()
                val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()

                // Проверяем видимые элементы от последнего к первому
                if (lastVisibleItemPosition != RecyclerView.NO_POSITION && firstVisibleItemPosition != RecyclerView.NO_POSITION) {
                    val visibleMessages = (lastVisibleItemPosition downTo firstVisibleItemPosition).mapNotNull { position ->
                        adapter.getItemNotProtected(position).first.takeIf { it.idSender == otherUserId && !it.isRead }
                    }
                    markMessagesAsRead(visibleMessages)
                }
            }
        })
    }

    fun markMessagesAsRead(visibleMessages: List<Message>) {
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
            // Отправляем список прочитанных сообщений на сервер
            retrofitService.markMessagesAsRead(dialogId, messageIds)
        } catch (e: Exception) {
            Log.e("ReadMessagesError", "Failed to send read messages: ${e.message}")
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

    suspend fun getDialogSettings(idDialog: Int): ConversationSettings {
        return retrofitService.getDialogSettings(idDialog)
    }

    suspend fun deleteMessages(ids: List<Int>): Boolean {
        val response = try {
            retrofitService.deleteMessages(dialogId, ids)
        } catch (e: Exception) { return false }
        return response
    }

    suspend fun uploadPhoto(photo: File): Pair<String, Boolean> {
        val path = try {
            retrofitService.uploadPhoto(dialogId, photo)
        } catch (e: Exception) {
            return Pair("", false)
        }
        return Pair(path, true)
    }

    suspend fun uploadAudio(audio: File): Pair<String, Boolean> {
        val path = try {
            retrofitService.uploadAudio(dialogId, audio)
        } catch (e: Exception) {
            return Pair("", false)
        }
        return Pair(path, true)
    }

    suspend fun uploadFile(file: File): Pair<String, Boolean> {
        val path = try {
            retrofitService.uploadFile(dialogId, file)
        } catch (e: Exception) {
            return Pair("", false)
        }
        return Pair(path, true)
    }

    fun sendMessage(text: String?, images: List<String>?, voice: String?, file: String?,
                            referenceToMessageId: Int?, isForwarded: Boolean,
                            usernameAuthorOriginal: String?, localFilePaths: List<String>?) {
        viewModelScope.launch {
        val flag = if (!localFilePaths.isNullOrEmpty()) { false }
        else {
            try {
                retrofitService.sendMessage(dialogId, text, images, voice, file, referenceToMessageId, isForwarded, usernameAuthorOriginal)
            } catch (e: Exception) { false }
        }
        if(!flag) {
            var mes = Message(id = 0, idSender = -5, text = text, images = images, voice = voice, file = file,
                referenceToMessageId = referenceToMessageId, isForwarded = isForwarded, usernameAuthorOriginal = usernameAuthorOriginal,
                timestamp = 0, isEdited = false, isUnsent = true, localFilePaths = localFilePaths)
            val id = messengerService.insertUnsentMessage(dialogId, mes)
            mes = mes.copy(id = id)
            _unsentMessageFlow.value = mes
        }
        }
    }

    suspend fun editMessage(messageId: Int, text: String?, images: List<String>?, voice: String?, file: String?) : Boolean {
        try {
            retrofitService.editMessage(dialogId, messageId, text, images, voice, file)
            return true
        } catch (e: Exception) {
            return false
        }
    }

    suspend fun downloadFile(context: Context, folder: String, filename: String): String {
        return retrofitService.downloadFile(context, folder, dialogId, filename)
    }

    fun downloadFileJava(context: Context, folder: String, filename: String): String {
        return runBlocking {
            retrofitService.downloadFile(context, folder, dialogId, filename)
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


    suspend fun findMessage(idMessage: Int): Pair<Message, Int> {
        return retrofitService.findMessage(idMessage, dialogId)
    }

    suspend fun updateAutoDeleteInterval(interval: Int) {
        retrofitService.updateAutoDeleteInterval(dialogId, interval)
    }

    fun formatMessageTime(timestamp: Long?): String {
        if (timestamp == null) return "-"

        val greenwichMessageDate = Calendar.getInstance().apply {
            timeInMillis = timestamp
        }
        val dateFormatToday = SimpleDateFormat("HH:mm", Locale.getDefault())
        return dateFormatToday.format(greenwichMessageDate.time)
    }

    private fun formatMessageDate(timestamp: Long?): String {
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

    private fun formatUserSessionDate(timestamp: Long?): String {
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

    fun saveLastMessage(id: Int) {
        viewModelScope.launch {
            val temp = messengerService.getLastReadMessage(dialogId)
            if(temp != null) messengerService.updateLastReadMessage(dialogId, id)
            else messengerService.saveLastReadMessage(dialogId, id)
        }
    }

    suspend fun getLastMessageId(): Int {
        return messengerService.getLastReadMessage(dialogId)?.second ?: -1
    }

    suspend fun getPreviousMessageId(id: Int): Int {
        return messengerService.getPreviousMessage(dialogId, id)?.id ?: -1
    }

    private fun joinDialog() {
        val joinData = JSONObject()
        joinData.put("dialog_id", dialogId)
        webSocketService.send("join_dialog", joinData)
    }

    private fun leaveDialog() {
        val leaveData = JSONObject()
        leaveData.put("dialog_id", dialogId)
        webSocketService.send("leave_dialog", leaveData)
    }

    fun sendTypingEvent(flag: Boolean) {
        val typingData = JSONObject()
        typingData.put("dialog_id", dialogId)
        if(flag) {
            webSocketService.send("typing", typingData)
        } else {
            webSocketService.send("stop_typing", typingData)
        }
    }

    override fun onNewMessage(message: Message) {
        Log.d("testSocketsMessage", "New Message: $message")
        val newMessagePair = if(lastMessageDate == "") message to "" else message to formatMessageDate(message.timestamp)
        _newMessageFlow.value = newMessagePair
    }

    override fun onEditedMessage(message: Message) {
        Log.d("testSocketsMessage", "Edited Message: $message")
        _editMessageFlow.value = message
        recyclerView.adapter?.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                recyclerView.scrollToPosition(0)
            }
        })
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onMessagesDeleted(deletedMessagesEvent: DeletedMessagesEvent) {
        Log.d("testSocketsMessage", "Deleted messages")
        val adapter = recyclerView.adapter
        if(adapter is MessageAdapter)
            viewModelScope.launch { adapter.clearPositions() }
        refresh()
        recyclerView.adapter?.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                recyclerView.scrollToPosition(0)
            }
        })
    }

    override fun onDialogCreated(dialogCreatedEvent: DialogCreatedEvent) {
        Log.d("testSocketsMessage", "Dialog created")
    }

    override fun onDialogDeleted(dialogDeletedEvent: DialogDeletedEvent) {
        Log.d("testSocketsMessage", "Dialog deleted")
        _deleteState.value = 2
    }

    override fun onUserSessionUpdated(userSessionUpdatedEvent: UserSessionUpdatedEvent) {
        Log.d("testSocketsMessage", "Session updated")
        val sessionString = formatUserSessionDate(userSessionUpdatedEvent.lastSession)
        _lastSessionString.postValue(sessionString)
    }

    override fun onStartTyping(typingEvent: TypingEvent) {
        Log.d("testSocketsMessage", "Typing started")
        _typingState.value = true
    }

    override fun onStopTyping(typingEvent: TypingEvent) {
        Log.d("testSocketsMessage", "Typing stopped")
        _typingState.value = false
    }

    override fun onMessagesRead(readMessagesEvent: ReadMessagesEvent) {
        Log.d("testSocketsMessage", "Messages read")
        _readMessagesFlow.value = readMessagesEvent.messagesReadIds
    }

    override fun onAllMessagesDeleted(dialogMessagesAllDeleted: DialogMessagesAllDeleted) {
        Log.d("testSocketsMessage", "All messages deleted")
        refresh()
    }

    override fun onUserJoinedDialog(dialogId: Int, userId: Int) {
        Log.d("testSocketsMessage", "User $userId joined Dialog $dialogId")
    }

    override fun onUserLeftDialog(dialogId: Int, userId: Int) {
        Log.d("testSocketsMessage", "User $userId left Dialog $dialogId")
    }

    suspend fun isNotificationsEnabled(dialogId: Int): Boolean {
        return messengerService.isNotificationsEnabled(dialogId, false)
    }

    suspend fun turnNotifications(dialogId: Int) {
        val isEnabled = messengerService.isNotificationsEnabled(dialogId, false)
        if(isEnabled) messengerService.insertChatSettings(ChatSettings(dialogId, false))
        else messengerService.deleteChatSettings(dialogId, false)
    }

    suspend fun toggleCanDeleteDialog(dialogId: Int) {
        retrofitService.toggleDialogCanDelete(dialogId)
    }

    suspend fun deleteAllMessages(dialogId: Int) {
        retrofitService.deleteDialogMessages(dialogId)
        _deleteState.value = 1
    }

    suspend fun deleteDialog(dialogId: Int) {
        retrofitService.deleteDialog(dialogId)
    }

    suspend fun getMediaPreviews(page: Int): List<String>? {
        return retrofitService.getMedias(dialogId, page)
    }

    suspend fun getFiles(page: Int): List<String>? {
        return retrofitService.getFiles(dialogId, page)
    }

    suspend fun getAudios(page: Int): List<String>? {
        return retrofitService.getAudios(dialogId, page)
    }

    suspend fun getPreview(context: Context, filename: String): String {
        return retrofitService.getMediaPreview(context, dialogId, filename)
    }

    suspend fun getUnsentMessages(): List<Message>? {
        return messengerService.getUnsentMessages(dialogId)
    }

    suspend fun deleteUnsentMessage(messageId: Int) {
        messengerService.deleteUnsentMessage(messageId)
    }

    suspend fun sendUnsentMessage(mes: Message) : Boolean {
        val flag = try {
            retrofitService.sendMessage(dialogId, mes.text, mes.images, mes.voice, mes.file, mes.referenceToMessageId, mes.isForwarded, mes.usernameAuthorOriginal)
        } catch (e: Exception) { false }
        return flag
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

    override fun onCleared() {
        leaveDialog()
        super.onCleared()
    }
}