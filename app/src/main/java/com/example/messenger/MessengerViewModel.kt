package com.example.messenger

import android.util.Base64
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.messenger.di.IoDispatcher
import com.example.messenger.model.Conversation
import com.example.messenger.model.DialogAlreadyExistsException
import com.example.messenger.model.FileManager
import com.example.messenger.model.LastMessage
import com.example.messenger.model.Message
import com.example.messenger.model.MessengerService
import com.example.messenger.model.RetrofitService
import com.example.messenger.model.ShortMessage
import com.example.messenger.model.User
import com.example.messenger.model.WebSocketService
import com.example.messenger.model.appsettings.AppSettings
import com.example.messenger.security.ChatKeyManager
import com.example.messenger.security.TinkAesGcmHelper
import com.example.messenger.states.AvatarState
import com.example.messenger.states.ConversationUi
import com.example.messenger.utils.chunkedFlowLast
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.security.KeyFactory
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Arrays
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject
import kotlin.collections.set

@HiltViewModel
class MessengerViewModel @Inject constructor(
    private val messengerService: MessengerService,
    private val retrofitService: RetrofitService,
    private val fileManager: FileManager,
    private val webSocketService: WebSocketService,
    private val appSettings: AppSettings,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val _currentUser = MutableLiveData<User>()
    val currentUser: LiveData<User> = _currentUser
    private val _vacation = MutableLiveData<Pair<String, String>?>()
    val vacation: LiveData<Pair<String, String>?> = _vacation

    var isNeedFetchConversations = false

    private val _conversationsUi = MutableStateFlow<List<ConversationUi>>(emptyList())
    val conversationsUi: StateFlow<List<ConversationUi>> = _conversationsUi

    private val _userAvatar = MutableStateFlow<AvatarState>(AvatarState.Loading)
    val userAvatar: StateFlow<AvatarState> = _userAvatar

    private val chatKeyManager = ChatKeyManager()

    private val preloadSemaphore = Semaphore(4)
    private val avatarCache = mutableMapOf<String, String>()
    private val loadingAvatars = mutableSetOf<String>()

    init {
        webSocketService.reconnectIfNeeded()
        checkFCM()
        fetchVacation()
        fetchCurrentUser()
        fetchConversations()
        fetchNewMessages()
    }

    private fun fetchVacation() {
        viewModelScope.launch{
            try {
                val pair = retrofitService.getVacation()
                _vacation.postValue(pair)
            } catch (e: Exception) {
                Log.e("Connection Error", e.toString())
            }
        }
    }

    private fun fetchCurrentUser() {
        viewModelScope.launch {
            try {
                var initialUser: User? = null
                try {
                    val initUser = messengerService.getUser()
                    Log.d("testInitUser", initUser.toString())
                    initUser?.let { user ->
                        _currentUser.postValue(user)
                        initialUser = user
                        user.avatar?.let { preloadCurrentUserAvatar(it) }
                    }
                } catch (e: Exception) {Log.e("testInitUser", "Can't take user in db ${e.message}")}
                val user = retrofitService.getUser(0) // 0 - currentUser
                _currentUser.postValue(user)
                if(user != initialUser) {
                    Log.d("testUpdateCurUser", user.toString())
                    if(initialUser?.avatar != user.avatar) {
                        user.avatar?.let { preloadCurrentUserAvatar(it) }
                    }
                    messengerService.updateUser(user)
                }
            } catch (_: Exception) { return@launch }
        }
    }

    fun fetchConversations(isFromCreate: Boolean = false) {
        viewModelScope.launch {
            try {
                if(!isNeedFetchConversations && !isFromCreate) {
                    val initialConversations = messengerService.getConversations()
                    val ui = initialConversations.map { toConversationUi(it) }
                    _conversationsUi.value = ui
                    preloadAvatars(ui)
                } else isNeedFetchConversations = false
                val updatedConversations = retrofitService.getConversations()
                val decryptedConversations = mutableListOf<Conversation>()

                for(conv in updatedConversations) {
                    val lastMessageText = conv.lastMessage.text
                    if(lastMessageText != null) {
                        val aead = chatKeyManager.getAead(conv.id, conv.type)
                        if(aead != null) {
                            val tinkAesGcmHelper = TinkAesGcmHelper(aead)
                            val text = tinkAesGcmHelper.decryptText(lastMessageText)
                            val lastMessage = conv.lastMessage.copy(text = text)
                            decryptedConversations.add(conv.copy(lastMessage = lastMessage))
                        } else {
                            val lastMessage = conv.lastMessage.copy(text = "[Зашифрованное сообщение]")
                            decryptedConversations.add(conv.copy(lastMessage = lastMessage))
                        }
                    } else {
                        val lastMessage = conv.lastMessage.copy(text = "[Вложение]")
                        decryptedConversations.add(conv.copy(lastMessage = lastMessage))
                    }
                }
                val ui = decryptedConversations.map { toConversationUi(it) }
                _conversationsUi.value = ui
                preloadAvatars(ui)
                messengerService.replaceConversations(decryptedConversations)
            } catch (_: Exception) { return@launch }
        }
    }

    private fun fetchNewMessages() {
        viewModelScope.launch {
            webSocketService.notificationMessageFlow
                .buffer()
                .chunkedFlowLast(200)
                .collect { event ->
                    if (event.isEmpty()) return@collect
                    val decryptedMessages = event.mapNotNull {
                        val message = it.toShortMessage(getCurrentTime())
                        val typeStr = if(message.isGroup) "group" else "dialog"
                        val aead = chatKeyManager.getAead(message.chatId, typeStr)
                        if(aead != null) {
                            val tinkAesGcmHelper = TinkAesGcmHelper(aead)
                            message.text = message.text?.let { txt -> tinkAesGcmHelper.decryptText(txt) } ?: "[Вложение]"
                            Log.d("testMessengerNewMessage", "New Message: $message")
                            message
                        } else null
                    }
                    if(decryptedMessages.isEmpty()) return@collect
                    val currentList = _conversationsUi.value.toMutableList()
                    val shortMessages = filterLatestMessages(decryptedMessages)
                    shortMessages.forEach { shortMessage ->
                        val type = if(shortMessage.isGroup) "group" else "dialog"
                        val index = currentList.indexOfFirst { it.conversation.id == shortMessage.chatId && it.conversation.type == type }
                        val lastMessage = LastMessage(shortMessage.text, shortMessage.timestamp, false, shortMessage.senderName)
                        if(index != -1) {
                            val ui = currentList[index]
                            val conv = ui.conversation
                            val conversation = conv.copy(lastMessage = lastMessage, unreadCount = conv.unreadCount + 1)
                            val dateText = formatMessageDate(shortMessage.timestamp)
                            val newUi = ui.copy(conversation = conversation, dateText = dateText)
                            if(index != 0) {
                                currentList.removeAt(index)
                                currentList.add(0, newUi)
                            } else currentList[index] = newUi
                        } else {
                            // Если элемент не найден, добавляем его в начало списка
                            val conversation = Conversation(type, shortMessage.chatId, lastMessage = lastMessage,
                                countMsg = 1, isOwner = false, canDelete = false, autoDeleteInterval = 0, unreadCount = 1)
                            val newUi = toConversationUi(conversation)
                            currentList.add(0, newUi)
                        }
                    }
                    _conversationsUi.value = currentList
                    preloadAvatars(currentList)
                }
        }
    }

    private fun checkFCM() {
        viewModelScope.launch {
            val token = appSettings.getFCMToken()
            if(!token.isNullOrEmpty()) {
                if(appSettings.getRemember()) {
                    try {
                        retrofitService.saveFCMToken(token)
                        appSettings.setFCMToken(null)
                    } catch (_: Exception) { return@launch }
                }
            }
        }
    }

    fun stopNotifications(bool: Boolean) {
        webSocketService.setViewModelActive(bool)
    }

    fun createDialog(name: String, keyCurrentUser: String?, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val publicUserKeyString = retrofitService.getUserKey(name)

                if (!publicUserKeyString.isNullOrEmpty() && !keyCurrentUser.isNullOrEmpty()) {
                    val keyManager = ChatKeyManager()
                    val symmetricKey = keyManager.generateChatKey()

                    val publicKey1 = getPublicKey(keyCurrentUser)
                    val publicKey2 = getPublicKey(publicUserKeyString)

                    val userKeyByte1 = keyManager.wrapChatKeyForRecipient(symmetricKey, publicKey1)
                    val userKeyByte2 = keyManager.wrapChatKeyForRecipient(symmetricKey, publicKey2)

                    val userKey1 = Base64.encodeToString(userKeyByte1, Base64.NO_WRAP)
                    val userKey2 = Base64.encodeToString(userKeyByte2, Base64.NO_WRAP)

                    val newDialogId = retrofitService.createDialog(name, userKey1, userKey2)
                    keyManager.storeChatKey(newDialogId, "dialog", symmetricKey)
                    Arrays.fill(symmetricKey.encoded, 0.toByte())
                    fetchConversations(isFromCreate = true)
                } else onError("Ошибка: Пользователь ещё ни разу не заходил в мессенджер, создать диалог с ним нельзя!")
            } catch(_: DialogAlreadyExistsException) {
                onError("Ошибка: диалог с данным пользователем уже создан")
            }
            catch (e: Exception) {
                Log.d("testErrorCreateDialog", e.message.toString())
                onError("Ошибка: Нет сети!")
            }
        }
    }

    fun createGroup(name: String, keyCurrentUser: String?, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                if(!keyCurrentUser.isNullOrEmpty()) {
                    val keyManager = ChatKeyManager()
                    val symmetricKey = keyManager.generateChatKey()

                    val publicKey1 = getPublicKey(keyCurrentUser)
                    val userKeyByte1 = keyManager.wrapChatKeyForRecipient(symmetricKey, publicKey1)
                    val userKey1 = Base64.encodeToString(userKeyByte1, Base64.NO_WRAP)

                    val newGroupId = retrofitService.createGroup(name, userKey1)
                    keyManager.storeChatKey(newGroupId, "group", symmetricKey)
                    Arrays.fill(symmetricKey.encoded, 0.toByte())
                    fetchConversations(isFromCreate = true)
                }
            } catch (_: Exception) {
                onError("Ошибка: Нет сети!")
            }
        }
    }

    private fun getPublicKey(key: String): PublicKey {
        val publicKeyBytes = Base64.decode(key, Base64.NO_WRAP)
        val keySpec = X509EncodedKeySpec(publicKeyBytes)
        return KeyFactory.getInstance("RSA").generatePublic(keySpec)
    }

    fun forwardMessages(list: List<Message>?, usernames: List<String>?, id: Int, callback: (Boolean) -> Unit) {
        viewModelScope.launch {
            val aead = chatKeyManager.getAead(id, "dialog")
            if(aead != null) {
                val tinkAesGcmHelper = TinkAesGcmHelper(aead)
                list?.forEachIndexed { index, message ->
                    val encryptedText = message.text?.let { tinkAesGcmHelper.encryptText(it) }
                    if(message.usernameAuthorOriginal == null) {
                        forwardMessage(id, encryptedText, message.images, message.voice,
                            message.file, message.code, message.codeLanguage, message.isUrl,
                            message.referenceToMessageId, usernames?.get(index), message.waveform)
                    } else {
                        forwardMessage(id, encryptedText, message.images, message.voice,
                            message.file, message.code, message.codeLanguage, message.isUrl,
                            message.referenceToMessageId, message.usernameAuthorOriginal, message.waveform)
                    }
                }
                callback(true)
            } else callback(false)
        }
    }

    private suspend fun forwardMessage(idDialog: Int, text: String?, images: List<String>?,
                            voice: String?, file: String?, code: String?, codeLanguage: String?, isUrl: Boolean?, referenceToMessageId: Int?,
                            usernameAuthorOriginal: String?, waveform: List<Int>?) {
        try {
            retrofitService.sendMessage(idDialog, text, images, voice, file, code, codeLanguage, referenceToMessageId, true, isUrl, usernameAuthorOriginal, waveform)
        } catch (_: Exception) { return }
    }

    fun forwardGroupMessages(list: List<Message>?, usernames: List<String>?, id: Int, callback: (Boolean) -> Unit) {
        viewModelScope.launch {
            val aead = chatKeyManager.getAead(id, "group")
            if(aead != null) {
                val tinkAesGcmHelper = TinkAesGcmHelper(aead)
                list?.forEachIndexed { index, message ->
                    val encryptedText = message.text?.let { tinkAesGcmHelper.encryptText(it) }
                    if(message.usernameAuthorOriginal == null) {
                        forwardGroupMessage(id, encryptedText, message.images, message.voice,
                            message.file, message.code, message.codeLanguage, message.isUrl,
                            message.referenceToMessageId, usernames?.get(index), message.waveform)
                    } else {
                        forwardGroupMessage(id, encryptedText, message.images, message.voice,
                            message.file, message.code, message.codeLanguage, message.isUrl,
                            message.referenceToMessageId, message.usernameAuthorOriginal, message.waveform)
                    }
                }
                callback(true)
            } else callback(false)
        }
    }

    private suspend fun forwardGroupMessage(idGroup: Int, text: String?, images: List<String>?,
                         voice: String?, file: String?, code: String?, codeLanguage: String?,
                         isUrl: Boolean?, referenceToMessageId: Int?, usernameAuthorOriginal: String?,
                                            waveform: List<Int>?) {
        try {
            retrofitService.sendGroupMessage(idGroup, text, images, voice, file, code, codeLanguage,
                referenceToMessageId, true, isUrl, usernameAuthorOriginal, waveform)
        } catch (_: Exception) { return }
    }

    private fun getCurrentTime(): Long {
        return Instant.now().toEpochMilli()
    }

    suspend fun deleteFCMToken() : Boolean {
        return try {
            retrofitService.deleteFCMToken()
        } catch (_: Exception) { false }
    }

    fun clearCurrentUser() {
        viewModelScope.launch {
            withContext(NonCancellable) {
                Log.d("testClearUser", "OK")
                webSocketService.disconnect()
                FirebaseMessaging.getInstance().deleteToken().await()
                messengerService.deleteCurrentUser()
                appSettings.setCurrentAccessToken(null)
                appSettings.setCurrentRefreshToken(null)
                appSettings.setRemember(false)
                appSettings.setCurrentGitlabToken(null)
                appSettings.setFCMToken(null)
            }
        }
    }

    private fun toConversationUi(conv: Conversation): ConversationUi {
        val dateText = formatMessageDate(conv.lastMessage.timestamp)
        return ConversationUi(
            conversation = conv,
            dateText = dateText,
            avatarState = when {
                conv.type == "group" -> {
                    if(conv.avatar != null) AvatarState.Loading else null
                }
                else -> {
                    if(conv.otherUser?.avatar != null) AvatarState.Loading else null
                }
            }
        )
    }

    private fun preloadAvatars(list: List<ConversationUi>) {
        list.forEach { ui ->
            if(ui.avatarState != null && ui.avatarState !is AvatarState.Ready) preloadAvatar(ui)
        }
    }

    private fun preloadAvatar(ui: ConversationUi) {
        val stringId = "${ui.conversation.id}${ui.conversation.type}"

        avatarCache[stringId]?.let { path ->
            updateAvatarState(stringId, AvatarState.Ready(path))
            return
        }

        if (loadingAvatars.contains(stringId)) return
        loadingAvatars.add(stringId)

        viewModelScope.launch {
            val result = preloadSemaphore.withPermit {
                withContext(ioDispatcher) {
                    try {
                        val avatar = when {
                            ui.conversation.type == "group" -> {
                                ui.conversation.avatar ?: return@withContext AvatarState.Error
                            }
                            else -> {
                                ui.conversation.otherUser?.avatar ?: return@withContext AvatarState.Error
                            }
                        }
                        val localPath = if (fileManager.isExistAvatar(avatar)) {
                            fileManager.getAvatarFilePath(avatar)
                        } else {
                            val downloaded = retrofitService.downloadAvatar(avatar)
                            fileManager.saveAvatarFile(
                                avatar,
                                File(downloaded).readBytes()
                            )
                            downloaded
                        }
                        avatarCache[stringId] = localPath
                        AvatarState.Ready(localPath)
                    } catch (_: Exception) {
                        AvatarState.Error
                    }
                }
            }
            loadingAvatars.remove(stringId)
            updateAvatarState(stringId, result)
        }
    }

    private fun updateAvatarState(stringId: String, state: AvatarState) {
        _conversationsUi.update { list ->
            list.map {
                if ("${it.conversation.id}${it.conversation.type}" == stringId) {
                    it.copy(avatarState = state)
                } else it
            }
        }
    }

    private fun filterLatestMessages(messages: List<ShortMessage>): List<ShortMessage> {
        val result = mutableListOf<ShortMessage>()

        for (i in messages.indices.reversed()) {
            val mes = messages[i]
            if (result.none { it.chatId == mes.chatId && it.isGroup == mes.isGroup }) {
                result.add(mes)
            }
        }

        return result.reversed()
    }

    private fun formatMessageDate(timestamp: Long?): String {
        if(timestamp == null) return "-"

        val greenwichMessageDate = Calendar.getInstance().apply {
            timeInMillis = timestamp
        }
        val dateFormatToday = SimpleDateFormat("HH:mm", Locale.getDefault())
        val dateFormatDayMonth = SimpleDateFormat("d MMM", Locale.getDefault())
        val dateFormatYear = SimpleDateFormat("d.MM.yyyy", Locale.getDefault())
        val localNow = Calendar.getInstance()
        return when {
            isToday(localNow, greenwichMessageDate) -> dateFormatToday.format(greenwichMessageDate.time)
            isThisYear(localNow, greenwichMessageDate) -> dateFormatDayMonth.format(greenwichMessageDate.time)
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

    fun preloadCurrentUserAvatar(avatar: String) {
        if (avatar.isBlank()) {
            _userAvatar.value = AvatarState.Error
            return
        }
        viewModelScope.launch {
            val result = preloadSemaphore.withPermit {
                withContext(ioDispatcher) {
                    try {
                        val localPath =
                            if (fileManager.isExistAvatar(avatar)) {
                                fileManager.getAvatarFilePath(avatar)
                            } else {
                                val downloaded = retrofitService.downloadAvatar(avatar)
                                fileManager.saveAvatarFile(
                                    avatar,
                                    File(downloaded).readBytes()
                                )
                                downloaded
                            }
                        AvatarState.Ready(localPath)
                    } catch (_: Exception) {
                        AvatarState.Error
                    }
                }
            }
            _userAvatar.value = result
        }
    }
}