package com.example.messenger

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.messenger.codeview.syntax.LanguageManager
import com.example.messenger.codeview.syntax.LanguageName
import com.example.messenger.codeview.syntax.ThemeName
import com.example.messenger.databinding.ItemCodeReceiverBinding
import com.example.messenger.databinding.ItemCodeSenderBinding
import com.example.messenger.databinding.ItemFileReceiverBinding
import com.example.messenger.databinding.ItemFileSenderBinding
import com.example.messenger.databinding.ItemMessageReceiverBinding
import com.example.messenger.databinding.ItemMessageSenderBinding
import com.example.messenger.databinding.ItemTextImageReceiverBinding
import com.example.messenger.databinding.ItemTextImageSenderBinding
import com.example.messenger.databinding.ItemTextImagesReceiverBinding
import com.example.messenger.databinding.ItemTextImagesSenderBinding
import com.example.messenger.databinding.ItemVoiceReceiverBinding
import com.example.messenger.databinding.ItemVoiceSenderBinding
import com.example.messenger.model.Message
import com.example.messenger.model.User
import com.example.messenger.picker.DateUtils
import com.luck.picture.lib.config.PictureMimeType
import com.luck.picture.lib.config.SelectMimeType
import com.luck.picture.lib.entity.LocalMedia
import com.masoudss.lib.SeekBarOnProgressChanged
import com.masoudss.lib.WaveformSeekBar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import androidx.core.net.toUri

interface MessageActionListener {
    fun onMessageClick(message: Message, itemView: View, isSender: Boolean)
    fun onMessageClickImage(message: Message, itemView: View, localMedias: ArrayList<LocalMedia>, isSender: Boolean)
    fun onMessageLongClick(itemView: View)
    fun onImagesClick(images: ArrayList<LocalMedia>, position: Int)
    fun onUnsentMessageClick(message: Message, itemView: View)
    fun onCodeOpenClick(message: Message)
}


class MessageDiffCallback : DiffUtil.ItemCallback<Triple<Message, String, String>>() {
    override fun areItemsTheSame(oldItem: Triple<Message, String, String>, newItem: Triple<Message, String, String>): Boolean {
        return oldItem.first.id == newItem.first.id
    }

    override fun areContentsTheSame(oldItem: Triple<Message, String, String>, newItem: Triple<Message, String, String>): Boolean {
        return oldItem.first == newItem.first
    }

    override fun getChangePayload(oldItem: Triple<Message, String, String>, newItem: Triple<Message, String, String>): Any? {
        return if (!oldItem.first.isRead && newItem.first.isRead) "isRead" else null
    }
}

class MessageAdapter(
    private val actionListener: MessageActionListener,
    private val currentUserId: Int,
    private val context: Context,
    private val messageViewModel: BaseChatViewModel,
    private val isGroup: Boolean,
    private val canDelete: Boolean
) : ListAdapter<Triple<Message, String, String>, RecyclerView.ViewHolder>(MessageDiffCallback()) {
    var members: Map<Int, Pair<String?, String?>?> = mapOf()
    var membersFull: List<User> = listOf()
    var canLongClick: Boolean = true
    private var checkedPositions: MutableSet<Int> = mutableSetOf()
    private var checkedMessageIds: MutableSet<Int> = mutableSetOf()
    private var mapPositions: MutableMap<Int, Boolean> = mutableMapOf()
    private var highlightedPosition: Int? = null
    private val uiScopeMain = CoroutineScope(Dispatchers.Main)
    private val linkPattern = Regex("""\[([^]]+)]\((https?://[^)]+)\)|(https?://\S+)""")


    fun addNewMessages(messages: List<Triple<Message, String, String>>) {
        val updatedList = currentList.toMutableList()
        var needNotify = false
        if(isGroup) {
            val firstItem = updatedList.firstOrNull()?.first
            var firstItemId = firstItem?.id ?: -10
            val firstItemSenderId = firstItem?.idSender ?: -10
            messages.forEachIndexed { index, message ->
                val messageIdSender = message.first.idSender
                val messageId = message.first.id
                if(firstItemId != -10) {
                    val info = members[firstItemId]
                    if(messageIdSender != currentUserId) {
                        if(messageIdSender == firstItemSenderId && message.second == "") {
                            val second: String = info?.second ?: ""
                            members += messageId to (null to second)
                            members += firstItemId to (info?.first to null)
                            if(index == 0) needNotify = true
                        } else {
                            val member = membersFull.find { it.id == messageIdSender }
                            val avatar = member?.avatar ?: ""
                            members += messageId to (member?.username to avatar)
                        }
                    }
                } else {
                    val member = membersFull.find { it.id == messageIdSender }
                    members += messageId to (member?.username to member?.avatar)
                }
                firstItemId = message.first.id
            }
        }
        updatedList.addAll(0, messages)
        submitList(updatedList) {
            if(needNotify) notifyItemChanged(messages.size, "isAvatar")
        }
    }

    fun addUnsentMessage(message: Triple<Message, String, String>) {
        val updatedList = currentList.toMutableList()
        updatedList.add(0, message)
        submitList(updatedList)
    }

    fun deleteUnsentMessage(message: Message) {
        val updatedList = currentList.toMutableList()
        updatedList.remove(Triple(message, "", ""))
        submitList(updatedList)
    }

    fun getItemNotProtected(position: Int) : Triple<Message, String, String> = getItem(position)

    override fun getItemCount(): Int = currentList.size

    override fun getItemId(position: Int): Long { // Исключает потенциальные баги с индексацией
        return getItem(position).first.id.toLong()
    }

    fun getDeleteList(): List<Int> = checkedMessageIds.toList()

    fun getForwardList(): List<Pair<Message, Boolean>> {
        val list = mutableListOf<Pair<Message, Boolean>>()
        checkedPositions.forEach {
            val message = getItem(it)?.first
            if(message != null) {
                list.add(Pair(message, mapPositions[it] ?: true))
            }
        }
        return list
    }

    fun updateMessagesAsRead(listIds: List<Int>) {
        if(listIds.isNotEmpty()) {
            val currentPagingData = currentList
            val updatedPagingData = currentPagingData.map { pair ->
                if (pair.first.id in listIds && pair.first.idSender == currentUserId) {
                    pair.copy(first = pair.first.copy(isRead = true))
                } else {
                    pair
                }
            }
            if(updatedPagingData == currentPagingData) return
            submitList(updatedPagingData)
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun clearPositions() {
        canLongClick = true
        checkedPositions.clear()
        checkedMessageIds.clear()
        mapPositions.clear()
        notifyDataSetChanged()
    }

    private fun getItemPositionWithId(idMessage: Int): Int {
        return currentList.indexOfLast { it.first.id == idMessage }
    }

    private fun savePosition(messageId: Int, isSender: Boolean) {
        val position = getItemPositionWithId(messageId)
        if (messageId in checkedMessageIds) {
            checkedMessageIds.remove(messageId)
            checkedPositions.remove(position)
            mapPositions.remove(position)
        } else {
            checkedMessageIds.add(messageId)
            checkedPositions.add(position)
            mapPositions[position] = isSender
        }
        notifyItemChanged(position)
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun onLongClick(messageId: Int, isSender: Boolean) {
        savePosition(messageId, isSender)
        canLongClick = false
        notifyDataSetChanged()
    }

    companion object {
        private const val TYPE_TEXT_RECEIVER = 0
        private const val TYPE_TEXT_SENDER = 1
        private const val TYPE_VOICE_RECEIVER = 2
        private const val TYPE_VOICE_SENDER = 3
        private const val TYPE_FILE_RECEIVER = 4
        private const val TYPE_FILE_SENDER = 5
        private const val TYPE_TEXT_IMAGE_RECEIVER = 6
        private const val TYPE_TEXT_IMAGE_SENDER = 7
        private const val TYPE_TEXT_IMAGES_RECEIVER = 8
        private const val TYPE_TEXT_IMAGES_SENDER = 9
        private const val TYPE_CODE_RECEIVER = 10
        private const val TYPE_CODE_SENDER = 11
    }

    override fun getItemViewType(position: Int): Int {
        val message = getItem(position)?.first ?: return -1
        if(message.idSender == currentUserId || message.idSender == -5) { // -5 is unsent message
            return when {
                message.images?.isNotEmpty() == true -> {
                    when {
                        message.images?.size == 1 -> TYPE_TEXT_IMAGE_SENDER
                        else -> TYPE_TEXT_IMAGES_SENDER
                    }
                }
                message.text?.isNotEmpty() == true -> TYPE_TEXT_SENDER
                else -> {
                    when {
                        message.file?.isNotEmpty() == true -> TYPE_FILE_SENDER
                        message.voice?.isNotEmpty() == true -> TYPE_VOICE_SENDER
                        else -> TYPE_CODE_SENDER
                    }
                }
            }
        } else {
            return when {
                message.images?.isNotEmpty() == true -> {
                    when {
                        message.images?.size == 1 -> TYPE_TEXT_IMAGE_RECEIVER
                        else -> TYPE_TEXT_IMAGES_RECEIVER
                    }
                }
                message.text?.isNotEmpty() == true -> TYPE_TEXT_RECEIVER
                else -> {
                    when {
                        message.file?.isNotEmpty() == true -> TYPE_FILE_RECEIVER
                        message.voice?.isNotEmpty() == true -> TYPE_VOICE_RECEIVER
                        else -> TYPE_CODE_RECEIVER
                    }
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_TEXT_RECEIVER -> MessagesViewHolderReceiver(
                ItemMessageReceiverBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
            TYPE_TEXT_SENDER -> MessagesViewHolderSender(
                ItemMessageSenderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
            TYPE_VOICE_RECEIVER -> MessagesViewHolderVoiceReceiver(
                ItemVoiceReceiverBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
            TYPE_VOICE_SENDER -> MessagesViewHolderVoiceSender(
                ItemVoiceSenderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
            TYPE_FILE_RECEIVER -> MessagesViewHolderFileReceiver(
                ItemFileReceiverBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
            TYPE_FILE_SENDER -> MessagesViewHolderFileSender(
                ItemFileSenderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
            TYPE_TEXT_IMAGE_RECEIVER -> MessagesViewHolderTextImageReceiver(
                ItemTextImageReceiverBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
            TYPE_TEXT_IMAGE_SENDER -> MessagesViewHolderTextImageSender(
                ItemTextImageSenderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
            TYPE_TEXT_IMAGES_RECEIVER -> MessagesViewHolderTextImagesReceiver(
                ItemTextImagesReceiverBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
            TYPE_TEXT_IMAGES_SENDER -> MessagesViewHolderTextImagesSender(
                ItemTextImagesSenderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
            TYPE_CODE_RECEIVER -> MessagesViewHolderCodeReceiver(
                ItemCodeReceiverBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
            TYPE_CODE_SENDER -> MessagesViewHolderCodeSender(
                ItemCodeSenderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, payloads: MutableList<Any>) {
        when {
            payloads.contains("isRead") -> {
                when(holder) {
                    is MessagesViewHolderSender -> holder.updateReadStatus()
                    is MessagesViewHolderVoiceSender -> holder.updateReadStatus()
                    is MessagesViewHolderFileSender -> holder.updateReadStatus()
                    is MessagesViewHolderTextImageSender -> holder.updateReadStatus()
                    is MessagesViewHolderTextImagesSender -> holder.updateReadStatus()
                    is MessagesViewHolderCodeSender -> holder.updateReadStatus()
                }
                return
            }
            payloads.contains("isAvatar") -> {
                when(holder) {
                    is MessagesViewHolderReceiver -> holder.updateAvatar()
                    is MessagesViewHolderVoiceReceiver -> holder.updateAvatar()
                    is MessagesViewHolderFileReceiver -> holder.updateAvatar()
                    is MessagesViewHolderTextImageReceiver -> holder.updateAvatar()
                    is MessagesViewHolderTextImagesReceiver -> holder.updateAvatar()
                    is MessagesViewHolderCodeReceiver -> holder.updateAvatar()
                }
                return
            }
            else -> onBindViewHolder(holder, position)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position) ?: return
        val message = item.first
        val date = item.second
        val time = item.third

        var flagText = false
        if(!message.text.isNullOrEmpty()) flagText = true
        val isInLast30 = position >= itemCount - 30
        val isAnswer = message.referenceToMessageId != null
        if (position == highlightedPosition) {
            holder.itemView.setBackgroundColor(ContextCompat.getColor(context, R.color.chatAnswerHighlight))
            // Убираем подсветку через 1 секунду
            holder.itemView.postDelayed({
                holder.itemView.setBackgroundColor(Color.TRANSPARENT)
            }, 1300)
        } else holder.itemView.setBackgroundColor(Color.TRANSPARENT)

        when (holder) {
            is MessagesViewHolderReceiver -> holder.bind(message, date, time, position, isAnswer)

            is MessagesViewHolderSender -> holder.bind(message, date, time, position, isAnswer)

            is MessagesViewHolderVoiceReceiver -> holder.bind(message, date, time, position, isInLast30, isAnswer)

            is MessagesViewHolderVoiceSender -> holder.bind(message, date, time, position, isInLast30, isAnswer)

            is MessagesViewHolderFileReceiver -> holder.bind(message, date, time, position, isInLast30, isAnswer)

            is MessagesViewHolderFileSender -> holder.bind(message, date, time, position, isInLast30, isAnswer)

            is MessagesViewHolderTextImageReceiver -> holder.bind(message, date, time, position, flagText, isInLast30, isAnswer)

            is MessagesViewHolderTextImageSender -> holder.bind(message, date, time, position, flagText, isInLast30, isAnswer)

            is MessagesViewHolderTextImagesReceiver -> holder.bind(message, date, time, position, flagText, isInLast30, isAnswer)

            is MessagesViewHolderTextImagesSender -> holder.bind(message, date, time, position, flagText, isInLast30, isAnswer)

            is MessagesViewHolderCodeReceiver -> holder.bind(message, date, time, position)

            is MessagesViewHolderCodeSender -> holder.bind(message, date, time, position)
        }
    }

    fun highlightPosition(position: Int) {
        highlightedPosition = position
        notifyItemChanged(position)
    }

    private inline fun <reified T : ViewBinding> handleAnswerLayout(binder: T, message: Message, isSender: Boolean): Int {
        // choose viewHolder type
        val binding = when(binder) {
            is ItemMessageReceiverBinding -> binder.answerLayout
            is ItemMessageSenderBinding -> binder.answerLayout
            is ItemVoiceReceiverBinding -> binder.answerLayout
            is ItemVoiceSenderBinding -> binder.answerLayout
            is ItemFileReceiverBinding -> binder.answerLayout
            is ItemFileSenderBinding -> binder.answerLayout
            is ItemTextImageReceiverBinding -> binder.answerLayout
            is ItemTextImageSenderBinding -> binder.answerLayout
            is ItemTextImagesReceiverBinding -> binder.answerLayout
            is ItemTextImagesSenderBinding -> binder.answerLayout
            else -> throw IllegalArgumentException("Unknown binding type")
        }
        binding.root.visibility = View.VISIBLE
        if(isSender) binding.root.setBackgroundResource(R.drawable.answer_background_sender) else binding.root.setBackgroundResource(R.drawable.answer_background)
        binding.answerUsername.text = message.usernameAuthorOriginal
        val tmpId = message.referenceToMessageId
        return if(tmpId == null) {
            binding.answerMessage.text = "??????????"
            10
        } else {
            val chk = getItemPositionWithId(tmpId)
            if(chk == -1) {
                uiScopeMain.launch {
                    val mes = async { messageViewModel.findMessage(tmpId) }
                    val (m, p) = mes.await() ?: Pair(null, 0)
                    if(m?.images != null) {
                        messageViewModel.imageSet(m.images!!.first(), binding.answerImageView, context)
                    }
                    binding.answerMessage.text = when {
                        m?.text != null -> m.text
                        m?.images != null -> "Фотография"
                        m?.file != null -> m.file
                        m?.voice != null -> "Голосовое сообщение"
                        else -> "?????????"
                    }
                    binding.root.setOnClickListener {
                        messageViewModel.smartScrollToPosition(p)
                    }
                }
                10
            } else {
                val m = getItem(chk)?.first
                if(m?.images != null) {
                    uiScopeMain.launch {
                        messageViewModel.imageSet(m.images!!.first(), binding.answerImageView, context)
                    }
                }
                binding.answerMessage.text = when {
                    m?.text != null -> m.text
                    m?.images != null -> "Фотография"
                    m?.file != null -> m.file
                    m?.voice != null -> "Голосовое сообщение"
                    else -> "?????????"
                }
                binding.root.setOnClickListener {
                    messageViewModel.smartScrollToPosition(chk)
                }
                binding.answerMessage.text.length // return len
            }
        }
    }

    private fun parseMessageWithLinks(text: String): SpannableStringBuilder {
        if (!text.contains("""\[[^]]+]\(https?://[^)]+\)""".toRegex())
            && !text.contains("https?://\\S+".toRegex())
        ) {
            return SpannableStringBuilder(text)
        }

        val spannable = SpannableStringBuilder(text)
        val matches = linkPattern.findAll(text).toList()

        // Обрабатываем ссылки с конца к началу, чтобы индексы не сдвигались
        matches.reversed().forEach { match ->
            when {
                // Именованные ссылки: [текст](URL)
                match.groups[1] != null && match.groups[2] != null -> {
                    val (linkText, url) = match.destructured
                    val start = match.range.first
                    val end = match.range.last + 1

                    if (start <= end && end <= spannable.length) {
                        spannable.replace(start, end, linkText)
                        applyLinkStyle(spannable, start, start + linkText.length, url)
                    }
                }
                // Обычные URL: https://...
                match.groups[3] != null -> {
                    val url = match.groups[3]!!.value
                    val start = match.range.first
                    val end = match.range.last + 1

                    if (start <= end && end <= spannable.length) {
                        applyLinkStyle(spannable, start, end, url)
                    }
                }
            }
        }
        return spannable
    }

    private fun applyLinkStyle(spannable: SpannableStringBuilder, start: Int, end: Int, url: String) {
        spannable.setSpan(
            object : ClickableSpan() {
                override fun onClick(widget: View) {
                    openUrl(url)
                }
                override fun updateDrawState(ds: TextPaint) {
                    super.updateDrawState(ds)
                    ds.color = Color.CYAN
                    ds.isUnderlineText = true
                }
            },
            start, end,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
    }

    private fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, url.toUri())
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, "Невозможно открыть ссылку", Toast.LENGTH_SHORT).show()
        }
    }

    // ViewHolder для текстовых сообщений получателя
    inner class MessagesViewHolderReceiver(private val binding: ItemMessageReceiverBinding) : RecyclerView.ViewHolder(binding.root) {

        private var messageSave: Message? = null

        init {
            binding.root.setOnClickListener {
                messageSave?.let {
                    if(!canLongClick && canDelete) {
                        savePosition(it.id, false)
                    } else actionListener.onMessageClick(it, itemView, false)
                }
            }
            binding.root.setOnLongClickListener {
                if(canLongClick && canDelete) {
                    messageSave?.let {
                        onLongClick(it.id, false)
                        actionListener.onMessageLongClick(itemView)
                    }
                }
                true
            }
            binding.checkbox.setOnClickListener {
                messageSave?.let { savePosition(it.id, false) }
            }
        }

        fun updateAvatar() {
            binding.photoImageView.visibility = View.GONE
            binding.spaceAvatar.visibility = View.VISIBLE
        }

        fun bind(message: Message, date: String, time: String, position: Int, isAnswer: Boolean) {
            messageSave = message

            if(!canLongClick && canDelete) {
                if(!binding.checkbox.isVisible) binding.checkbox.visibility = View.VISIBLE
                binding.checkbox.isChecked = position in checkedPositions
            } else binding.checkbox.visibility = View.GONE

            if(isAnswer) {
                val ansSize = handleAnswerLayout(binding, message, false)
                val textLen = message.text?.length ?: 0
                if(textLen < 10 && ansSize > 10) {
                    val layoutParams = binding.customMessageLayout.layoutParams as ConstraintLayout.LayoutParams
                    layoutParams.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                    binding.customMessageLayout.layoutParams = layoutParams
                }
            } else binding.answerLayout.root.visibility = View.GONE

            if(message.isForwarded) {
                binding.forwardLayout.root.visibility = View.VISIBLE
                binding.forwardLayout.root.setBackgroundResource(R.drawable.answer_background)
                binding.forwardLayout.forwardUsername.text = message.usernameAuthorOriginal
                val textLen = message.text?.length ?: 0
                val fwdLen = message.usernameAuthorOriginal?.length ?: 0
                if(textLen <= 3 || (fwdLen > 10 && textLen < 8)) {
                    val layoutParams = binding.customMessageLayout.layoutParams as ConstraintLayout.LayoutParams
                    layoutParams.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                    binding.customMessageLayout.layoutParams = layoutParams
                }
            } else binding.forwardLayout.root.visibility = View.GONE

            message.text?.let {
                if(message.isUrl == true) {
                    val processedText = parseMessageWithLinks(it)
                    binding.messageReceiverTextView.text = processedText
                    binding.messageReceiverTextView.movementMethod = LinkMovementMethod.getInstance()
                } else binding.messageReceiverTextView.text = it
            }

            if(date != "") {
                binding.dateTextView.visibility = View.VISIBLE
                binding.dateTextView.text = date
            } else {
                binding.dateTextView.visibility = View.GONE
                binding.space.visibility = View.GONE
            }

            binding.timeTextView.text = time
            if(isGroup) {
                val user = members[message.id]
                if(user != null) {
                    if(user.first != null) {
                        binding.userNameTextView.visibility = View.VISIBLE
                        binding.userNameTextView.text = user.first
                    } else binding.userNameTextView.visibility = View.GONE
                    if(user.second != null) {
                        binding.photoImageView.visibility = View.VISIBLE
                        binding.spaceAvatar.visibility = View.GONE
                        if(user.second != "") messageViewModel.avatarSet(user.second ?: "", binding.photoImageView, context)
                    } else {
                        binding.spaceAvatar.visibility = View.VISIBLE
                        binding.photoImageView.visibility = View.GONE
                    }
                } else {
                    binding.spaceAvatar.visibility = View.VISIBLE
                    binding.photoImageView.visibility = View.GONE
                    binding.userNameTextView.visibility = View.GONE
                }
            } else {
                binding.spaceAvatar.visibility = View.GONE
                binding.photoImageView.visibility = View.GONE
                binding.userNameTextView.visibility = View.GONE
            }

            if(message.isEdited) binding.editTextView.visibility = View.VISIBLE
            else binding.editTextView.visibility = View.GONE
        }
    }

    // ViewHolder для текстовых сообщений отправителя
    inner class MessagesViewHolderSender(private val binding: ItemMessageSenderBinding) : RecyclerView.ViewHolder(binding.root) {

        private var messageSave: Message? = null

        init {
            binding.root.setOnClickListener {
                messageSave?.let {
                    when {
                        it.isUnsent == true -> actionListener.onUnsentMessageClick(it, itemView)
                        !canLongClick -> savePosition(it.id, true)
                        else -> actionListener.onMessageClick(it, itemView, true)
                    }
                }
            }
            binding.root.setOnLongClickListener {
                if(canLongClick) {
                    messageSave?.let {
                        onLongClick(it.id, true)
                        actionListener.onMessageLongClick(itemView)
                    }
                }
                true
            }
            binding.checkbox.setOnClickListener {
                messageSave?.let { savePosition(it.id, true) }
            }
        }

        fun updateReadStatus() {
            binding.icCheck.visibility = View.INVISIBLE
            binding.icCheck2.visibility = View.VISIBLE
            binding.icCheck2.bringToFront()
        }

        fun bind(message: Message, date: String, time: String, position: Int, isAnswer: Boolean) {
            messageSave = message

            if(isAnswer) {
                val ansSize = handleAnswerLayout(binding, message, true)
                val textLen = message.text?.length ?: 0
                if(textLen < 10 && ansSize > 10) {
                    val layoutParams = binding.customMessageLayout.layoutParams as ConstraintLayout.LayoutParams
                    layoutParams.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                    binding.customMessageLayout.layoutParams = layoutParams
                }
            } else binding.answerLayout.root.visibility = View.GONE

            if(message.isForwarded) {
                binding.forwardLayout.root.visibility = View.VISIBLE
                binding.forwardLayout.root.setBackgroundResource(R.drawable.answer_background_sender)
                binding.forwardLayout.forwardUsername.text = message.usernameAuthorOriginal
                val textLen = message.text?.length ?: 0
                val fwdLen = message.usernameAuthorOriginal?.length ?: 0
                if(textLen <= 3 || (fwdLen > 10 && textLen < 8)) {
                    val layoutParams = binding.customMessageLayout.layoutParams as ConstraintLayout.LayoutParams
                    layoutParams.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                    binding.customMessageLayout.layoutParams = layoutParams
                }
            } else binding.forwardLayout.root.visibility = View.GONE

            if(message.isUnsent == true) {
                with(binding) {
                    timeTextView.text = "----"
                    dateTextView.visibility = View.GONE
                    icCheck.visibility = View.INVISIBLE
                    icCheck2.visibility = View.INVISIBLE
                    editTextView.visibility = View.GONE
                    icError.visibility = View.VISIBLE
                }
            } else {
                if(!canLongClick) {
                    if(!binding.checkbox.isVisible) binding.checkbox.visibility = View.VISIBLE
                    binding.checkbox.isChecked = position in checkedPositions
                } else binding.checkbox.visibility = View.GONE

                binding.icError.visibility = View.GONE
                if(date != "") {
                    binding.dateTextView.visibility = View.VISIBLE
                    binding.dateTextView.text = date
                } else {
                    binding.dateTextView.visibility = View.GONE
                    binding.space.visibility = View.GONE
                }

                binding.timeTextView.text = time

                if (message.isRead) {
                    updateReadStatus()
                } else {
                    binding.icCheck.visibility = View.VISIBLE
                    binding.icCheck2.visibility = View.INVISIBLE
                }
                if(message.isEdited) binding.editTextView.visibility = View.VISIBLE
                else binding.editTextView.visibility = View.GONE
            }
            message.text?.let {
                if(message.isUrl == true) {
                    val processedText = parseMessageWithLinks(it)
                    binding.messageSenderTextView.text = processedText
                    binding.messageSenderTextView.movementMethod = LinkMovementMethod.getInstance()
                } else binding.messageSenderTextView.text = it
            }
        }
    }

    class CustomLayoutManager : RecyclerView.LayoutManager() {

        private var columnWidth = 0
        private var rowHeight = 0

        override fun onLayoutChildren(recycler: RecyclerView.Recycler, state: RecyclerView.State) {
            detachAndScrapAttachedViews(recycler)

            if (itemCount == 0) return

            // Определяем количество колонок
            val columns = when (itemCount) {
                1 -> 1
                in 2..5 -> 2
                else -> 3
            }

            // Определяем количество строк
            val rows = when (itemCount) {
                1 -> 1
                in 2..3 -> 2
                in 4..8 -> 3
                else -> 4
            }

            columnWidth = width / columns
            rowHeight = height / rows

            // Матрица занятых клеток
            val occupiedCells = Array(rows) { BooleanArray(columns) { false } }

            for (i in 0 until itemCount) {
                val view = recycler.getViewForPosition(i)
                var spanSizeW = 1
                var spanSizeH = 1

                // Логика определения размеров элемента
                when (itemCount) {
                    2 -> spanSizeH = 2
                    3 -> if (i == 0) spanSizeW = 2
                    4 -> if (i == 0) spanSizeH = 3
                    5 -> if (i == 0) spanSizeH = 2
                    6 -> if (i == 0) spanSizeW = 3 else if (i == 1) spanSizeW = 2
                    7 -> if (i == 0) spanSizeH = 2 else if (i == 1) spanSizeW = 2
                    8 -> if (i == 0) spanSizeW = 2
                    9 -> if (i == 0) spanSizeW = 3 else if (i == 1) spanSizeW = 2
                    10 -> if (i == 0) spanSizeH = 2 else if (i == 1) spanSizeW = 2
                }

                // Находим первую доступную позицию
                var positionFound = false
                for (row in 0 until rows) {
                    for (col in 0 until columns) {
                        if (canPlaceItem(row, col, spanSizeW, spanSizeH, occupiedCells)) {
                            placeItem(view, row, col, spanSizeW, spanSizeH, occupiedCells)
                            positionFound = true
                            break
                        }
                    }
                    if (positionFound) break
                }
            }
        }

        private fun canPlaceItem(row: Int, col: Int, spanSizeW: Int, spanSizeH: Int, occupiedCells: Array<BooleanArray>): Boolean {
            for (r in row until row + spanSizeH) {
                for (c in col until col + spanSizeW) {
                    if (r >= occupiedCells.size || c >= occupiedCells[0].size || occupiedCells[r][c]) {
                        return false
                    }
                }
            }
            return true
        }

        private fun placeItem(view: View, row: Int, col: Int, spanSizeW: Int, spanSizeH: Int, occupiedCells: Array<BooleanArray>) {
            val left = col * columnWidth
            val top = row * rowHeight
            val right = left + columnWidth * spanSizeW
            val bottom = top + rowHeight * spanSizeH

            // Отмечаем клетки как занятые
            for (r in row until row + spanSizeH) {
                for (c in col until col + spanSizeW) {
                    occupiedCells[r][c] = true
                }
            }

            // Измеряем и размещаем view
            val widthSpec = View.MeasureSpec.makeMeasureSpec(right - left, View.MeasureSpec.EXACTLY)
            val heightSpec = View.MeasureSpec.makeMeasureSpec(bottom - top, View.MeasureSpec.EXACTLY)
            view.measure(widthSpec, heightSpec)
            addView(view)

            val outRect = Rect()
            calculateItemDecorationsForChild(view, outRect)

            layoutDecorated(
                view,
                left + outRect.left,
                top + outRect.top,
                right - outRect.right,
                bottom - outRect.bottom
            )
        }

        override fun generateDefaultLayoutParams(): RecyclerView.LayoutParams {
            return RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
    }

    class AdaptiveGridSpacingItemDecoration(
        private val spacing: Int,
        private val includeEdge: Boolean
    ) : RecyclerView.ItemDecoration() {

        override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
            val position = (view.layoutParams as RecyclerView.LayoutParams).bindingAdapterPosition
            if (position == RecyclerView.NO_POSITION) return

            val itemCount = state.itemCount

            val columns = when (itemCount) {
                in 2..5 -> 2
                else -> 3
            }

            val rows = when (itemCount) {
                in 2..3 -> 2
                in 4..8 -> 3
                else -> 4
            }

            val column = position % columns
            val row = position / columns

            val horizontalSpacing = spacing
            val verticalSpacing = spacing

            if (includeEdge) {
                outRect.left = if (column == 0) horizontalSpacing else horizontalSpacing / 2
                outRect.right = if (column == columns - 1) horizontalSpacing else horizontalSpacing / 2
                outRect.top = if (row == 0) verticalSpacing else verticalSpacing / 2
                outRect.bottom = if (row == rows - 1) verticalSpacing else verticalSpacing / 2
            } else {
                outRect.left = horizontalSpacing / 2
                outRect.right = horizontalSpacing / 2
                outRect.top = if (row > 0) verticalSpacing / 2 else 0
                outRect.bottom = if (row < rows - 1) verticalSpacing / 2 else 0
            }
        }
    }

    inner class MessagesViewHolderVoiceReceiver(private val binding: ItemVoiceReceiverBinding) : RecyclerView.ViewHolder(binding.root) {
        private var isPlaying: Boolean = false
        private val handler = Handler(Looper.getMainLooper())
        private var messageSave: Message? = null

        private var mediaPlayer: MediaPlayer = MediaPlayer()
        private var updateSeekBarRunnable: Runnable = object : Runnable {
            override fun run() {
                if (isPlaying && mediaPlayer.isPlaying) {
                    val currentPosition = mediaPlayer.currentPosition.toFloat()
                    binding.waveformSeekBar.progress = currentPosition
                    binding.timeVoiceTextView.text = messageViewModel.formatTime(currentPosition.toLong())
                    handler.postDelayed(this, 100)
                }
            }
        }

        init {
            binding.playButton.setOnClickListener {
                if (!isPlaying) {
                    mediaPlayer.start()
                    binding.playButton.setImageResource(R.drawable.ic_pause)
                    isPlaying = true
                    handler.post(updateSeekBarRunnable)
                } else {
                    mediaPlayer.pause()
                    binding.playButton.setImageResource(R.drawable.ic_play)
                    isPlaying = false
                    handler.removeCallbacks(updateSeekBarRunnable)
                }
            }
            mediaPlayer.setOnCompletionListener {
                binding.playButton.setImageResource(R.drawable.ic_play)
                binding.waveformSeekBar.progress = 0f
                binding.timeVoiceTextView.text = messageViewModel.formatTime(mediaPlayer.duration.toLong())
                isPlaying = false
                handler.removeCallbacks(updateSeekBarRunnable)
            }
            binding.waveformSeekBar.onProgressChanged = object : SeekBarOnProgressChanged {
                override fun onProgressChanged(waveformSeekBar: WaveformSeekBar, progress: Float, fromUser: Boolean) {
                    if (fromUser) {
                        mediaPlayer.seekTo(progress.toInt())
                        binding.timeVoiceTextView.text = messageViewModel.formatTime(progress.toLong())
                    }
                }
            }
            binding.root.setOnClickListener {
                messageSave?.let {
                    if(!canLongClick && canDelete) {
                        savePosition(it.id, false)
                    } else actionListener.onMessageClick(it, itemView, false)
                }
            }
            binding.root.setOnLongClickListener {
                if(canLongClick && canDelete) {
                    messageSave?.let {
                        onLongClick(it.id, false)
                        actionListener.onMessageLongClick(itemView)
                    }
                }
                true
            }
            binding.checkbox.setOnClickListener {
                messageSave?.let { savePosition(it.id, false) }
            }
        }

        fun updateAvatar() {
            binding.photoImageView.visibility = View.GONE
            binding.spaceAvatar.visibility = View.VISIBLE
        }

        fun bind(message: Message, date: String, time: String, position: Int, isInLast30: Boolean, isAnswer: Boolean) {
            messageSave = message

            binding.playButton.visibility = View.VISIBLE

            if(!canLongClick && canDelete) {
                if(!binding.checkbox.isVisible) binding.checkbox.visibility = View.VISIBLE
                binding.checkbox.isChecked = position in checkedPositions
            } else binding.checkbox.visibility = View.GONE

            if(isAnswer) handleAnswerLayout(binding, message, false)
            else binding.answerLayout.root.visibility = View.GONE

            if(message.isForwarded) {
                binding.forwardLayout.root.visibility = View.VISIBLE
                binding.forwardLayout.root.setBackgroundResource(R.drawable.answer_background)
                binding.forwardLayout.forwardUsername.text = message.usernameAuthorOriginal
            } else binding.forwardLayout.root.visibility = View.GONE

            if(date != "") {
                binding.dateTextView.visibility = View.VISIBLE
                binding.dateTextView.text = date
            } else {
                binding.dateTextView.visibility = View.GONE
                binding.space.visibility = View.GONE
            }

            binding.timeTextView.text = time
            if(isGroup) {
                val user = members[message.id]
                if(user != null) {
                    if(user.first != null) {
                        binding.userNameTextView.visibility = View.VISIBLE
                        binding.userNameTextView.text = user.first
                    } else binding.userNameTextView.visibility = View.GONE
                    if(user.second != null) {
                        binding.photoImageView.visibility = View.VISIBLE
                        binding.spaceAvatar.visibility = View.GONE
                        if(user.second != "") messageViewModel.avatarSet(user.second ?: "", binding.photoImageView, context)
                    } else {
                        binding.spaceAvatar.visibility = View.VISIBLE
                        binding.photoImageView.visibility = View.GONE
                    }
                } else {
                    binding.spaceAvatar.visibility = View.VISIBLE
                    binding.photoImageView.visibility = View.GONE
                    binding.userNameTextView.visibility = View.GONE
                }
            } else {
                binding.spaceAvatar.visibility = View.GONE
                binding.photoImageView.visibility = View.GONE
                binding.userNameTextView.visibility = View.GONE
            }

            if(message.isEdited) binding.editTextView.visibility = View.VISIBLE
            else binding.editTextView.visibility = View.GONE

            uiScopeMain.launch {
                val filePathTemp = async {
                    val voice = message.voice ?: "nonWork"
                    if (messageViewModel.fManagerIsExist(voice)) {
                        return@async Pair(messageViewModel.fManagerGetFilePath(voice), true)
                    } else {
                        try {
                            return@async Pair(messageViewModel.downloadFile(context, "audio", message.voice!!), false)
                        } catch (_: Exception) {
                            return@async Pair(null, true)
                        }
                    }
                }
                val (first, second) = filePathTemp.await()
                if (first != null) {
                val file = File(first)
                if (file.exists()) {
                    if (!second && isInLast30) messageViewModel.fManagerSaveFile(message.voice!!, file.readBytes())
                    mediaPlayer.reset()
                    mediaPlayer.setDataSource(first)
                    mediaPlayer.prepare()
                    val duration = mediaPlayer.duration
                    binding.waveformSeekBar.setSampleFrom(message.waveform?.toIntArray() ?: intArrayOf())
                    binding.waveformSeekBar.maxProgress = duration.toFloat()
                    binding.timeVoiceTextView.text = messageViewModel.formatTime(duration.toLong())
                } else {
                    Log.e("VoiceError", "File does not exist: $first")
                    binding.progressBar.visibility = View.GONE
                    binding.errorImageView.visibility = View.VISIBLE
                    binding.playButton.visibility = View.GONE
                }
                } else {
                    binding.progressBar.visibility = View.GONE
                    binding.errorImageView.visibility = View.VISIBLE
                    binding.playButton.visibility = View.GONE
                }
            }
        }
    }

    inner class MessagesViewHolderVoiceSender(private val binding: ItemVoiceSenderBinding) : RecyclerView.ViewHolder(binding.root) {
        private var isPlaying: Boolean = false
        private val handler = Handler(Looper.getMainLooper())
        private var messageSave: Message? = null

        private var mediaPlayer: MediaPlayer = MediaPlayer()
        private var updateSeekBarRunnable: Runnable = object : Runnable {
            override fun run() {
                if (isPlaying && mediaPlayer.isPlaying) {
                    val currentPosition = mediaPlayer.currentPosition.toFloat()
                    binding.waveformSeekBar.progress = currentPosition
                    binding.timeVoiceTextView.text = messageViewModel.formatTime(currentPosition.toLong())
                    handler.postDelayed(this, 100)
                }
            }
        }

        init {
            binding.playButton.setOnClickListener {
                if (!isPlaying) {
                    mediaPlayer.start()
                    binding.playButton.setImageResource(R.drawable.ic_pause)
                    isPlaying = true
                    handler.post(updateSeekBarRunnable)
                } else {
                    mediaPlayer.pause()
                    binding.playButton.setImageResource(R.drawable.ic_play)
                    isPlaying = false
                    handler.removeCallbacks(updateSeekBarRunnable)
                }
            }
            mediaPlayer.setOnCompletionListener {
                binding.playButton.setImageResource(R.drawable.ic_play)
                binding.waveformSeekBar.progress = 0f
                binding.timeVoiceTextView.text = messageViewModel.formatTime(mediaPlayer.duration.toLong())
                isPlaying = false
                handler.removeCallbacks(updateSeekBarRunnable)
            }
            binding.waveformSeekBar.onProgressChanged = object : SeekBarOnProgressChanged {
                override fun onProgressChanged(waveformSeekBar: WaveformSeekBar, progress: Float, fromUser: Boolean) {
                    if (fromUser) {
                        mediaPlayer.seekTo(progress.toInt())
                        binding.timeVoiceTextView.text = messageViewModel.formatTime(progress.toLong())
                    }
                }
            }
            binding.root.setOnClickListener {
                messageSave?.let {
                    when {
                        it.isUnsent == true -> actionListener.onUnsentMessageClick(it, itemView)
                        !canLongClick -> savePosition(it.id, true)
                        else -> actionListener.onMessageClick(it, itemView, true)
                    }
                }
            }
            binding.root.setOnLongClickListener {
                if(canLongClick) {
                    messageSave?.let {
                        onLongClick(it.id, true)
                        actionListener.onMessageLongClick(itemView)
                    }
                }
                true
            }
            binding.checkbox.setOnClickListener {
                messageSave?.let { savePosition(it.id, true) }
            }
        }

        fun updateReadStatus() {
            binding.icCheck.visibility = View.INVISIBLE
            binding.icCheck2.visibility = View.VISIBLE
            binding.icCheck2.bringToFront()
        }

        fun bind(message: Message, date: String, time: String, position: Int, isInLast30: Boolean, isAnswer: Boolean) {
            messageSave = message

            binding.playButton.visibility = View.VISIBLE

            if(isAnswer) handleAnswerLayout(binding, message, true)
            else binding.answerLayout.root.visibility = View.GONE

            if(message.isForwarded) {
                binding.forwardLayout.root.visibility = View.VISIBLE
                binding.forwardLayout.root.setBackgroundResource(R.drawable.answer_background_sender)
                binding.forwardLayout.forwardUsername.text = message.usernameAuthorOriginal
            } else binding.forwardLayout.root.visibility = View.GONE

            if(message.isUnsent == true) {
                binding.timeTextView.text = "----"
                binding.dateTextView.visibility = View.GONE
                binding.icCheck.visibility = View.INVISIBLE
                binding.icCheck2.visibility = View.INVISIBLE
                binding.editTextView.visibility = View.GONE
                binding.icError.visibility = View.VISIBLE
            } else {
                binding.icError.visibility = View.GONE
                if(date != "") {
                    binding.dateTextView.visibility = View.VISIBLE
                    binding.dateTextView.text = date
                } else {
                    binding.dateTextView.visibility = View.GONE
                    binding.space.visibility = View.GONE
                }

                binding.timeTextView.text = time
                if(!canLongClick) {
                    if(!binding.checkbox.isVisible) binding.checkbox.visibility = View.VISIBLE
                    binding.checkbox.isChecked = position in checkedPositions
                    binding.checkbox.setOnClickListener {
                        savePosition(message.id, true)
                    }
                } else binding.checkbox.visibility = View.GONE

                if (message.isRead) {
                    updateReadStatus()
                } else {
                    binding.icCheck.visibility = View.VISIBLE
                    binding.icCheck2.visibility = View.INVISIBLE
                }
                if(message.isEdited) binding.editTextView.visibility = View.VISIBLE
                else binding.editTextView.visibility = View.GONE
            }
            uiScopeMain.launch {
                val filePathTemp = async {
                    if(message.isUnsent == true) {
                        return@async Pair(message.localFilePaths?.firstOrNull(), true)
                    } else {
                        val voice = message.voice ?: "nonWork"
                        if (messageViewModel.fManagerIsExist(voice)) {
                            return@async Pair(messageViewModel.fManagerGetFilePath(voice), true)
                        } else {
                            try {
                                return@async Pair(messageViewModel.downloadFile(context, "audio", message.voice!!), false)
                            } catch (_: Exception) {
                                return@async Pair(null, true)
                            }
                        }
                    }
                }
                val (first, second) = filePathTemp.await()
                if (first != null) {
                val file = File(first)
                if (file.exists()) {
                    if (!second && isInLast30) messageViewModel.fManagerSaveFile(message.voice!!, file.readBytes())
                    mediaPlayer.reset()
                    mediaPlayer.setDataSource(first)
                    mediaPlayer.prepare()
                    val duration = mediaPlayer.duration
                    binding.waveformSeekBar.setSampleFrom(message.waveform?.toIntArray() ?: intArrayOf())
                    binding.waveformSeekBar.maxProgress = duration.toFloat()
                    binding.timeVoiceTextView.text = messageViewModel.formatTime(duration.toLong())
                } else {
                    Log.e("VoiceError", "File does not exist: $first")
                    binding.progressBar.visibility = View.GONE
                    binding.errorImageView.visibility = View.VISIBLE
                    binding.playButton.visibility = View.GONE
                }
            } else {
                    binding.progressBar.visibility = View.GONE
                    binding.errorImageView.visibility = View.VISIBLE
                    binding.playButton.visibility = View.GONE
                }
            }
        }
    }

    inner class MessagesViewHolderFileReceiver(private val binding: ItemFileReceiverBinding) : RecyclerView.ViewHolder(binding.root) {
        private var messageSave: Message? = null

        init {
            binding.fileButton.setOnClickListener {
                messageSave?.let { message ->
                    val filePath = messageViewModel.fManagerGetFilePath(message.file!!)
                    val file = File(filePath)
                    if (file.exists()) {
                        try {
                            val uri: Uri = FileProvider.getUriForFile(context, context.applicationContext.packageName + ".provider", file)
                            val intent = Intent(Intent.ACTION_VIEW)
                            intent.setDataAndType(uri, context.contentResolver.getType(uri))
                            intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION

                            val chooser = Intent.createChooser(intent, "Выберите приложение для открытия файла")
                            context.startActivity(chooser)
                        } catch (e: IllegalArgumentException) {
                            e.printStackTrace()
                        }
                    }
                }
            }
            binding.root.setOnClickListener {
                messageSave?.let {
                    if(!canLongClick && canDelete) {
                        savePosition(it.id, false)
                    } else actionListener.onMessageClick(it, itemView, false)
                }
            }
            binding.root.setOnLongClickListener {
                if(canLongClick && canDelete) {
                    messageSave?.let {
                        onLongClick(it.id, false)
                        actionListener.onMessageLongClick(itemView)
                    }
                }
                true
            }
            binding.checkbox.setOnClickListener {
                messageSave?.let { savePosition(it.id, false) }
            }
        }

        fun updateAvatar() {
            binding.photoImageView.visibility = View.GONE
            binding.spaceAvatar.visibility = View.VISIBLE
        }

        fun bind(message: Message, date: String, time: String, position: Int, isInLast30: Boolean, isAnswer: Boolean) {
            messageSave = message

            if(!canLongClick && canDelete) {
                if(!binding.checkbox.isVisible) binding.checkbox.visibility = View.VISIBLE
                binding.checkbox.isChecked = position in checkedPositions
            }
            else binding.checkbox.visibility = View.GONE

            if(isAnswer) handleAnswerLayout(binding, message, false)
            else binding.answerLayout.root.visibility = View.GONE

            if(message.isForwarded) {
                binding.forwardLayout.root.visibility = View.VISIBLE
                binding.forwardLayout.root.setBackgroundResource(R.drawable.answer_background)
                binding.forwardLayout.forwardUsername.text = message.usernameAuthorOriginal
            } else binding.forwardLayout.root.visibility = View.GONE

            if(date != "") {
                binding.dateTextView.visibility = View.VISIBLE
                binding.dateTextView.text = date
            } else {
                binding.dateTextView.visibility = View.GONE
                binding.space.visibility = View.GONE
            }

            binding.timeTextView.text = time

            if(message.isEdited) binding.editTextView.visibility = View.VISIBLE
            else binding.editTextView.visibility = View.GONE
            if(isGroup) {
                val user = members[message.id]
                if(user != null) {
                    if(user.first != null) {
                        binding.userNameTextView.visibility = View.VISIBLE
                        binding.userNameTextView.text = user.first
                    } else binding.userNameTextView.visibility = View.GONE
                    if(user.second != null) {
                        binding.photoImageView.visibility = View.VISIBLE
                        binding.spaceAvatar.visibility = View.GONE
                        if(user.second != "") messageViewModel.avatarSet(user.second ?: "", binding.photoImageView, context)
                    } else {
                        binding.spaceAvatar.visibility = View.VISIBLE
                        binding.photoImageView.visibility = View.GONE
                    }
                } else {
                    binding.spaceAvatar.visibility = View.VISIBLE
                    binding.photoImageView.visibility = View.GONE
                    binding.userNameTextView.visibility = View.GONE
                }
            } else {
                binding.spaceAvatar.visibility = View.GONE
                binding.photoImageView.visibility = View.GONE
                binding.userNameTextView.visibility = View.GONE
            }

            uiScopeMain.launch {
                val filePathTemp = async {
                    if (messageViewModel.fManagerIsExist(message.file!!)) {
                        return@async Pair(messageViewModel.fManagerGetFilePath(message.file!!), true)
                    } else {
                        try {
                            return@async Pair(messageViewModel.downloadFile(context, "files", message.file!!), false)
                        } catch (_: Exception) {
                            return@async Pair(null, true)
                        }
                    }
                }
                val (first, second) = filePathTemp.await()
                if (first != null) {
                val file = File(first)
                if (file.exists()) {
                    if (!second && isInLast30) messageViewModel.fManagerSaveFile(message.file!!, file.readBytes())
                    binding.fileNameReceiverTextView.text = file.name
                    binding.fileSizeTextView.text = messageViewModel.formatFileSize(file.length())
                } else {
                    Log.e("FileError", "File does not exist: $first")
                    binding.progressBar.visibility = View.GONE
                    binding.errorImageView.visibility = View.VISIBLE
                }
            } else {
                    binding.progressBar.visibility = View.GONE
                    binding.errorImageView.visibility = View.VISIBLE
                }
            }
        }
    }

    inner class MessagesViewHolderFileSender(private val binding: ItemFileSenderBinding) : RecyclerView.ViewHolder(binding.root) {
        private var messageSave: Message? = null

        init {
            binding.fileButton.setOnClickListener {
                messageSave?.let { message ->
                    val filePath = messageViewModel.fManagerGetFilePath(message.file!!)
                    val file = File(filePath)
                    if (file.exists()) {
                        try {
                            val uri: Uri = FileProvider.getUriForFile(context, context.applicationContext.packageName + ".provider", file)
                            val intent = Intent(Intent.ACTION_VIEW)
                            intent.setDataAndType(uri, context.contentResolver.getType(uri))
                            intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION

                            val chooser = Intent.createChooser(intent, "Выберите приложение для открытия файла")
                            context.startActivity(chooser)
                        } catch (e: IllegalArgumentException) {
                            e.printStackTrace()
                        }
                    }
                }
            }
            binding.root.setOnClickListener {
                messageSave?.let {
                    when {
                        it.isUnsent == true -> actionListener.onUnsentMessageClick(it, itemView)
                        !canLongClick -> savePosition(it.id, true)
                        else -> actionListener.onMessageClick(it, itemView, true)
                    }
                }
            }
            binding.root.setOnLongClickListener {
                if(canLongClick) {
                    messageSave?.let {
                        onLongClick(it.id, true)
                        actionListener.onMessageLongClick(itemView)
                    }
                }
                true
            }
            binding.checkbox.setOnClickListener {
                messageSave?.let { savePosition(it.id, true) }
            }
        }

        fun updateReadStatus() {
            binding.icCheck.visibility = View.INVISIBLE
            binding.icCheck2.visibility = View.VISIBLE
            binding.icCheck2.bringToFront()
        }

        fun bind(message: Message, date: String, time: String, position: Int, isInLast30: Boolean, isAnswer: Boolean) {
            messageSave = message

            if(isAnswer) handleAnswerLayout(binding, message, true)
            else binding.answerLayout.root.visibility = View.GONE

            if(message.isForwarded) {
                binding.forwardLayout.root.visibility = View.VISIBLE
                binding.forwardLayout.root.setBackgroundResource(R.drawable.answer_background_sender)
                binding.forwardLayout.forwardUsername.text = message.usernameAuthorOriginal
            } else binding.forwardLayout.root.visibility = View.GONE

            if(message.isUnsent == true) {
                binding.timeTextView.text = "----"
                binding.dateTextView.visibility = View.GONE
                binding.icCheck.visibility = View.INVISIBLE
                binding.icCheck2.visibility = View.INVISIBLE
                binding.editTextView.visibility = View.GONE
                binding.icError.visibility = View.VISIBLE
            } else {
                binding.icError.visibility = View.GONE
                if(date != "") {
                    binding.dateTextView.visibility = View.VISIBLE
                    binding.dateTextView.text = date
                } else {
                    binding.dateTextView.visibility = View.GONE
                    binding.space.visibility = View.GONE
                }

                binding.timeTextView.text = time

                if(!canLongClick) {
                    if(!binding.checkbox.isVisible) binding.checkbox.visibility = View.VISIBLE
                    binding.checkbox.isChecked = position in checkedPositions
                } else binding.checkbox.visibility = View.GONE

                if (message.isRead) {
                    updateReadStatus()
                } else {
                    binding.icCheck.visibility = View.VISIBLE
                    binding.icCheck2.visibility = View.INVISIBLE
                }
                if(message.isEdited) binding.editTextView.visibility = View.VISIBLE
                else binding.editTextView.visibility = View.GONE
            }
            uiScopeMain.launch {
                val filePathTemp = async {
                    if(message.isUnsent == true) {
                        return@async Pair(message.localFilePaths?.firstOrNull(), true)
                    } else {
                        if (messageViewModel.fManagerIsExist(message.file!!)) {
                            return@async Pair(messageViewModel.fManagerGetFilePath(message.file!!), true)
                        } else {
                            try {
                                return@async Pair(messageViewModel.downloadFile(context, "files", message.file!!), false)
                            } catch (_: Exception) {
                                return@async Pair(null, true)
                            }
                        }
                    }
                }
                val (first, second) = filePathTemp.await()
                if (first != null) {
                    val file = File(first)
                    if (file.exists()) {
                        if (!second && isInLast30) messageViewModel.fManagerSaveFile(message.file!!, file.readBytes())
                        binding.fileNameSenderTextView.text = file.name
                        binding.fileSizeTextView.text = messageViewModel.formatFileSize(file.length())
                    } else {
                        Log.e("FileError", "File does not exist: $first")
                        binding.progressBar.visibility = View.GONE
                        binding.errorImageView.visibility = View.VISIBLE
                    }
                } else {
                    binding.progressBar.visibility = View.GONE
                    binding.errorImageView.visibility = View.VISIBLE
                }
            }
        }
    }

    inner class MessagesViewHolderTextImageReceiver(private val binding: ItemTextImageReceiverBinding) : RecyclerView.ViewHolder(binding.root) {
        private var localMediaSave: LocalMedia? = null
        private var messageSave: Message? = null

        init {
            binding.receiverImageView.setOnClickListener {
                localMediaSave?.let { actionListener.onImagesClick(arrayListOf(it), 0) }
            }
            binding.root.setOnClickListener {
                messageSave?.let {
                    if(!canLongClick && canDelete) {
                        savePosition(it.id, false)
                    } else {
                        localMediaSave?.let { lm -> actionListener.onMessageClickImage(it, itemView, arrayListOf(lm), false) }
                    }
                }
            }
            binding.root.setOnLongClickListener {
                if(canLongClick && canDelete) {
                    messageSave?.let {
                        onLongClick(it.id, false)
                        actionListener.onMessageLongClick(itemView)
                    }
                }
                true
            }
            binding.checkbox.setOnClickListener {
                messageSave?.let { savePosition(it.id, false) }
            }
        }

        fun updateAvatar() {
            binding.photoImageView.visibility = View.GONE
            binding.spaceAvatar.visibility = View.VISIBLE
        }

        fun bind(message: Message, date: String, time: String, position: Int, flagText: Boolean, isInLast30: Boolean, isAnswer: Boolean) {
            messageSave = message

            if(isAnswer) handleAnswerLayout(binding, message, false)
            else binding.answerLayout.root.visibility = View.GONE

            if(message.isForwarded) {
                binding.forwardLayout.root.visibility = View.VISIBLE
                binding.forwardLayout.root.setBackgroundResource(R.drawable.answer_background)
                binding.forwardLayout.forwardUsername.text = message.usernameAuthorOriginal
            } else binding.forwardLayout.root.visibility = View.GONE

            val timeTextView = if(flagText) binding.timeTextView else binding.timeTextViewImage
            val editTextView = if(flagText) binding.editTextView else binding.editTextViewImage
            with(binding) {
                if(flagText) {
                    customMessageLayout.visibility = View.VISIBLE
                    timeLayout.visibility = View.GONE
                    message.text?.let {
                        if(message.isUrl == true) {
                            val processedText = parseMessageWithLinks(it)
                            binding.messageReceiverTextView.text = processedText
                            binding.messageReceiverTextView.movementMethod = LinkMovementMethod.getInstance()
                        } else binding.messageReceiverTextView.text = it
                    }
                } else {
                    customMessageLayout.visibility = View.GONE
                    timeLayout.visibility = View.VISIBLE
                }
            }
            if(date != "") {
                binding.dateTextView.visibility = View.VISIBLE
                binding.dateTextView.text = date
            } else {
                binding.dateTextView.visibility = View.GONE
                binding.space.visibility = View.GONE
            }

            timeTextView.text = time
            if(isGroup) {
                val user = members[message.id]
                if(user != null) {
                    if(user.first != null) {
                        binding.userNameTextView.visibility = View.VISIBLE
                        binding.userNameTextView.text = user.first
                    } else binding.userNameTextView.visibility = View.GONE
                    if(user.second != null) {
                        binding.photoImageView.visibility = View.VISIBLE
                        binding.spaceAvatar.visibility = View.GONE
                        if(user.second != "") messageViewModel.avatarSet(user.second ?: "", binding.photoImageView, context)
                    } else {
                        binding.spaceAvatar.visibility = View.VISIBLE
                        binding.photoImageView.visibility = View.GONE
                    }
                } else {
                    binding.spaceAvatar.visibility = View.VISIBLE
                    binding.photoImageView.visibility = View.GONE
                    binding.userNameTextView.visibility = View.GONE
                }
            } else {
                binding.spaceAvatar.visibility = View.GONE
                binding.photoImageView.visibility = View.GONE
                binding.userNameTextView.visibility = View.GONE
            }

            if(!canLongClick && canDelete) {
                if(!binding.checkbox.isVisible) binding.checkbox.visibility = View.VISIBLE
                binding.checkbox.isChecked = position in checkedPositions
            } else binding.checkbox.visibility = View.GONE

            if(message.isEdited) editTextView.visibility = View.VISIBLE
            else editTextView.visibility = View.GONE

            uiScopeMain.launch {
                binding.progressBar.visibility = View.VISIBLE
                val filePathTemp = async {
                    if (messageViewModel.fManagerIsExist(message.images?.first() ?: "nonWork")) {
                        return@async Pair(messageViewModel.fManagerGetFilePath(message.images!!.first()), true)
                    } else {
                        try {
                            return@async Pair(messageViewModel.downloadFile(context, "photos", message.images!!.first()), false)
                        } catch (_: Exception) {
                            return@async Pair(null, true)
                        }
                    }
                }
                val (first, second) = filePathTemp.await()
                if(first != null) {
                val file = File(first)
                if (file.exists()) {
                    if (!second && isInLast30) messageViewModel.fManagerSaveFile(message.images!!.first(), file.readBytes())
                    val uri = Uri.fromFile(file)
                    val localMedia = messageViewModel.fileToLocalMedia(file)
                    localMediaSave = localMedia
                    val chooseModel = localMedia.chooseModel
                    binding.tvDuration.visibility =
                        if (PictureMimeType.isHasVideo(localMedia.mimeType)) View.VISIBLE else View.GONE
                    if (chooseModel == SelectMimeType.ofAudio()) {
                        binding.tvDuration.visibility = View.VISIBLE
                        binding.tvDuration.setCompoundDrawablesRelativeWithIntrinsicBounds(
                            com.luck.picture.lib.R.drawable.ps_ic_audio, 0, 0, 0)
                    } else {
                        binding.tvDuration.setCompoundDrawablesRelativeWithIntrinsicBounds(
                            com.luck.picture.lib.R.drawable.ps_ic_video, 0, 0, 0)
                    }
                    binding.tvDuration.text = (DateUtils.formatDurationTime(localMedia.duration))
                    if (chooseModel == SelectMimeType.ofAudio()) {
                        binding.receiverImageView.setImageResource(com.luck.picture.lib.R.drawable.ps_audio_placeholder)
                    } else {
                        Glide.with(context)
                            .load(uri)
                            .centerCrop()
                            .placeholder(R.color.app_color_f6)
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .into(binding.receiverImageView)
                    }
                    binding.progressBar.visibility = View.GONE
                } else {
                    Log.e("ImageError", "File does not exist: $first")
                    binding.progressBar.visibility = View.GONE
                    binding.errorImageView.visibility = View.VISIBLE
                }
            } else {
                    binding.progressBar.visibility = View.GONE
                    binding.errorImageView.visibility = View.VISIBLE
                }
            }
        }
    }

    inner class MessagesViewHolderTextImageSender(private val binding: ItemTextImageSenderBinding) : RecyclerView.ViewHolder(binding.root) {
        private var localMediaSave: LocalMedia? = null
        private var messageSave: Message? = null

        init {
            binding.senderImageView.setOnClickListener {
                localMediaSave?.let { actionListener.onImagesClick(arrayListOf(it), 0) }
            }
            binding.root.setOnClickListener {
                messageSave?.let {
                    when {
                        it.isUnsent == true -> actionListener.onUnsentMessageClick(it, itemView)
                        !canLongClick -> savePosition(it.id, true)
                        else -> {
                            localMediaSave?.let { lm -> actionListener.onMessageClickImage(it, itemView, arrayListOf(lm), true) }
                        }
                    }
                }
            }
            binding.root.setOnLongClickListener {
                if(canLongClick) {
                    messageSave?.let {
                        onLongClick(it.id, true)
                        actionListener.onMessageLongClick(itemView)
                    }
                }
                true
            }
            binding.checkbox.setOnClickListener {
                messageSave?.let { savePosition(it.id, true) }
            }
        }

        fun updateReadStatus() {
            with(binding) {
                if(timeLayout.isVisible) {
                    icCheckImage.visibility = View.INVISIBLE
                    icCheck2Image.visibility = View.VISIBLE
                    icCheck2Image.bringToFront()
                } else {
                    icCheck.visibility = View.INVISIBLE
                    icCheck2.visibility = View.VISIBLE
                    icCheck2.bringToFront()
                }
            }
        }

        fun bind(message: Message, date: String, time: String, position: Int, flagText: Boolean, isInLast30: Boolean, isAnswer: Boolean) {
            messageSave = message

            if(isAnswer) handleAnswerLayout(binding, message, true)
            else binding.answerLayout.root.visibility = View.GONE

            if(message.isForwarded) {
                binding.forwardLayout.root.visibility = View.VISIBLE
                binding.forwardLayout.root.setBackgroundResource(R.drawable.answer_background_sender)
                binding.forwardLayout.forwardUsername.text = message.usernameAuthorOriginal
            } else binding.forwardLayout.root.visibility = View.GONE

            val timeTextView = if(flagText) binding.timeTextView else binding.timeTextViewImage
            val editTextView = if(flagText) binding.editTextView else binding.editTextViewImage
            val icCheck = if(flagText) binding.icCheck else binding.icCheckImage
            val icCheck2 = if(flagText) binding.icCheck2 else binding.icCheck2Image
            val icError = if(flagText) binding.icError else binding.icErrorImage
            with(binding) {
                if(flagText) {
                    customMessageLayout.visibility = View.VISIBLE
                    timeLayout.visibility = View.GONE
                    message.text?.let {
                        if(message.isUrl == true) {
                            val processedText = parseMessageWithLinks(it)
                            binding.messageSenderTextView.text = processedText
                            binding.messageSenderTextView.movementMethod = LinkMovementMethod.getInstance()
                        } else binding.messageSenderTextView.text = it
                    }
                } else {
                    customMessageLayout.visibility = View.GONE
                    timeLayout.visibility = View.VISIBLE
                }
            }
            if(message.isUnsent == true) {
                binding.dateTextView.visibility = View.GONE
                timeTextView.text = "----"
                icCheck.visibility = View.INVISIBLE
                icCheck2.visibility = View.INVISIBLE
                editTextView.visibility = View.GONE
                icError.visibility = View.VISIBLE
            } else {
                icError.visibility = View.GONE
                if(date != "") {
                    binding.dateTextView.visibility = View.VISIBLE
                    binding.dateTextView.text = date
                } else {
                    binding.dateTextView.visibility = View.GONE
                    binding.space.visibility = View.GONE
                }

                timeTextView.text = time

                if(!canLongClick) {
                    if(!binding.checkbox.isVisible) binding.checkbox.visibility = View.VISIBLE
                    binding.checkbox.isChecked = position in checkedPositions
                } else binding.checkbox.visibility = View.GONE

                if (message.isRead) {
                    updateReadStatus()
                } else {
                    icCheck.visibility = View.VISIBLE
                    icCheck2.visibility = View.INVISIBLE
                }
                if(message.isEdited) editTextView.visibility = View.VISIBLE
                else editTextView.visibility = View.GONE
            }
            uiScopeMain.launch {
                binding.progressBar.visibility = View.VISIBLE
                val filePathTemp = async {
                    if(message.isUnsent == true) {
                        return@async Pair(message.localFilePaths?.firstOrNull(), true)
                    } else {
                        if (messageViewModel.fManagerIsExist(message.images?.first() ?: "nonWork")) {
                            return@async Pair(messageViewModel.fManagerGetFilePath(message.images!!.first()), true)
                        } else {
                            try {
                                return@async Pair(messageViewModel.downloadFile(context, "photos", message.images!!.first()), false)
                            } catch (_: Exception) {
                                return@async Pair(null, true)
                            }
                        }
                    }
                }
                val (first, second) = filePathTemp.await()
                if (first != null) {
                val file = File(first)
                if (file.exists()) {
                    if (!second && isInLast30) messageViewModel.fManagerSaveFile(message.images!!.first(), file.readBytes())
                    val uri = Uri.fromFile(file)
                    val localMedia = messageViewModel.fileToLocalMedia(file)
                    localMediaSave = localMedia
                    val chooseModel = localMedia.chooseModel
                    binding.tvDuration.visibility =
                        if (PictureMimeType.isHasVideo(localMedia.mimeType)) View.VISIBLE else View.GONE
                    if (chooseModel == SelectMimeType.ofAudio()) {
                        binding.tvDuration.visibility = View.VISIBLE
                        binding.tvDuration.setCompoundDrawablesRelativeWithIntrinsicBounds(com.luck.picture.lib.R.drawable.ps_ic_audio, 0, 0, 0)
                    } else {
                        binding.tvDuration.setCompoundDrawablesRelativeWithIntrinsicBounds(com.luck.picture.lib.R.drawable.ps_ic_video, 0, 0, 0)
                    }
                    binding.tvDuration.text = (DateUtils.formatDurationTime(localMedia.duration))
                    if (chooseModel == SelectMimeType.ofAudio()) {
                        binding.senderImageView.setImageResource(com.luck.picture.lib.R.drawable.ps_audio_placeholder)
                    } else {
                        Glide.with(context)
                            .load(uri)
                            .centerCrop()
                            .placeholder(R.color.app_color_f6)
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .into(binding.senderImageView)
                    }
                    binding.progressBar.visibility = View.GONE
                } else {
                    Log.e("ImageError", "File does not exist: $first")
                    binding.progressBar.visibility = View.GONE
                    binding.errorImageView.visibility = View.VISIBLE
                }
            } else {
                    binding.progressBar.visibility = View.GONE
                    binding.errorImageView.visibility = View.VISIBLE
                }
            }
        }
    }

    inner class MessagesViewHolderTextImagesReceiver(private val binding: ItemTextImagesReceiverBinding) : RecyclerView.ViewHolder(binding.root) {
        private var messageSave: Message? = null
        private var localMediasSave: ArrayList<LocalMedia> = arrayListOf()

        private val adapter = ImagesAdapter(context, object: ImagesActionListener {
            override fun onImageClicked(images: ArrayList<LocalMedia>, position: Int) {
                actionListener.onImagesClick(images, position)
            }

            override fun onLongImageClicked() {
                if(canLongClick) {
                    messageSave?.let {
                        onLongClick(it.id, false)
                        actionListener.onMessageLongClick(itemView)
                    }
                }
            }
        })

        init {
            binding.recyclerview.layoutManager = CustomLayoutManager()
            binding.recyclerview.addItemDecoration(AdaptiveGridSpacingItemDecoration(2, true))
            binding.recyclerview.setItemViewCacheSize(5)
            binding.recyclerview.adapter = adapter

            binding.root.setOnClickListener {
                messageSave?.let {
                    if(!canLongClick && canDelete) {
                        savePosition(it.id, false)
                    } else {
                        if(localMediasSave.isNotEmpty()) {
                            actionListener.onMessageClickImage(it, itemView, localMediasSave, false)
                        }
                    }
                }
            }
            binding.root.setOnLongClickListener {
                if(canLongClick && canDelete) {
                    messageSave?.let {
                        onLongClick(it.id, false)
                        actionListener.onMessageLongClick(itemView)
                    }
                }
                true
            }
            binding.checkbox.setOnClickListener {
                messageSave?.let { savePosition(it.id, false) }
            }
        }

        fun updateAvatar() {
            binding.photoImageView.visibility = View.GONE
            binding.spaceAvatar.visibility = View.VISIBLE
        }

        fun bind(message: Message, date: String, time: String, position: Int, flagText: Boolean, isInLast30: Boolean, isAnswer: Boolean) {
            messageSave = message

            if(isAnswer) handleAnswerLayout(binding, message, false)
            else binding.answerLayout.root.visibility = View.GONE

            if(message.isForwarded) {
                binding.forwardLayout.root.visibility = View.VISIBLE
                binding.forwardLayout.root.setBackgroundResource(R.drawable.answer_background)
                binding.forwardLayout.forwardUsername.text = message.usernameAuthorOriginal
            } else binding.forwardLayout.root.visibility = View.GONE

            val timeTextView = if(flagText) binding.timeTextView else binding.timeTextViewImage
            val editTextView = if(flagText) binding.editTextView else binding.editTextViewImage
            with(binding) {
                if(flagText) {
                    customMessageLayout.visibility = View.VISIBLE
                    timeLayout.visibility = View.GONE
                    message.text?.let {
                        if(message.isUrl == true) {
                            val processedText = parseMessageWithLinks(it)
                            binding.messageReceiverTextView.text = processedText
                            binding.messageReceiverTextView.movementMethod = LinkMovementMethod.getInstance()
                        } else binding.messageReceiverTextView.text = it
                    }
                } else {
                    customMessageLayout.visibility = View.GONE
                    timeLayout.visibility = View.VISIBLE
                }
            }

            if(date != "") {
                binding.dateTextView.visibility = View.VISIBLE
                binding.dateTextView.text = date
            } else {
                binding.dateTextView.visibility = View.GONE
                binding.space.visibility = View.GONE
            }

            timeTextView.text = time
            if(isGroup) {
                val user = members[message.id]
                if(user != null) {
                    if(user.first != null) {
                        binding.userNameTextView.visibility = View.VISIBLE
                        binding.userNameTextView.text = user.first
                    } else binding.userNameTextView.visibility = View.GONE
                    if(user.second != null) {
                        binding.photoImageView.visibility = View.VISIBLE
                        binding.spaceAvatar.visibility = View.GONE
                        if(user.second != "") messageViewModel.avatarSet(user.second ?: "", binding.photoImageView, context)
                    } else {
                        binding.spaceAvatar.visibility = View.VISIBLE
                        binding.photoImageView.visibility = View.GONE
                    }
                } else {
                    binding.spaceAvatar.visibility = View.VISIBLE
                    binding.photoImageView.visibility = View.GONE
                    binding.userNameTextView.visibility = View.GONE
                }
            } else {
                binding.spaceAvatar.visibility = View.GONE
                binding.photoImageView.visibility = View.GONE
                binding.userNameTextView.visibility = View.GONE
            }

            if(!canLongClick && canDelete) {
                if(!binding.checkbox.isVisible) binding.checkbox.visibility = View.VISIBLE
                binding.checkbox.isChecked = position in checkedPositions
            } else binding.checkbox.visibility = View.GONE

            if(message.isEdited) editTextView.visibility = View.VISIBLE
            else editTextView.visibility = View.GONE

            binding.progressBar.visibility = View.VISIBLE
            binding.errorImageView.visibility = View.GONE
            val semaphore = Semaphore(4)
            uiScopeMain.launch {
                val localMedias = async {
                    val medias = arrayListOf<LocalMedia>()
                    for (image in message.images!!) {
                        val filePath = async {
                            semaphore.withPermit {
                                if (messageViewModel.fManagerIsExist(image)) {
                                    Pair(messageViewModel.fManagerGetFilePath(image), true)
                                } else {
                                    try {
                                        Pair(messageViewModel.downloadFile(context, "photos", image), false)
                                    } catch (_: Exception) {
                                        Pair(null, true)
                                    }
                                }
                            }
                        }
                        val (first, second) = filePath.await()
                        if (first != null) {
                        val file = File(first)
                        if (file.exists()) {
                            if (!second && isInLast30) messageViewModel.fManagerSaveFile(image, file.readBytes())
                            medias += messageViewModel.fileToLocalMedia(file)
                        } else {
                            Log.e("ImageError", "File does not exist: $filePath")
                            binding.progressBar.visibility = View.GONE
                            binding.errorImageView.visibility = View.VISIBLE
                        }
                    } else {
                            binding.progressBar.visibility = View.GONE
                            binding.errorImageView.visibility = View.VISIBLE
                        }
                    }
                    return@async medias
                }
                localMediasSave = localMedias.await()
                adapter.images = localMedias.await()
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    inner class MessagesViewHolderTextImagesSender(private val binding: ItemTextImagesSenderBinding) : RecyclerView.ViewHolder(binding.root) {
        private var messageSave: Message? = null
        private var localMediasSave: ArrayList<LocalMedia> = arrayListOf()

        private val adapter = ImagesAdapter(context, object: ImagesActionListener {
            override fun onImageClicked(images: ArrayList<LocalMedia>, position: Int) {
                actionListener.onImagesClick(images, position)
            }

            override fun onLongImageClicked() {
                if(canLongClick) {
                    messageSave?.let {
                        onLongClick(it.id, true)
                        actionListener.onMessageLongClick(itemView)
                    }
                }
            }
        })

        init {
            binding.recyclerview.layoutManager = CustomLayoutManager()
            binding.recyclerview.addItemDecoration(AdaptiveGridSpacingItemDecoration(2, true))
            binding.recyclerview.setItemViewCacheSize(5)
            binding.recyclerview.adapter = adapter

            binding.root.setOnClickListener {
                messageSave?.let {
                    when {
                        it.isUnsent == true -> actionListener.onUnsentMessageClick(it, itemView)
                        !canLongClick -> savePosition(it.id, true)
                        else -> {
                            if(localMediasSave.isNotEmpty()) {
                                actionListener.onMessageClickImage(it, itemView, localMediasSave, true)
                            }
                        }
                    }
                }
            }
            binding.root.setOnLongClickListener {
                if(canLongClick) {
                    messageSave?.let {
                        onLongClick(it.id, true)
                        actionListener.onMessageLongClick(itemView)
                    }
                }
                true
            }
            binding.checkbox.setOnClickListener {
                messageSave?.let { savePosition(it.id, true) }
            }
        }

        fun updateReadStatus() {
            with(binding) {
                if(timeLayout.isVisible) {
                    icCheckImage.visibility = View.INVISIBLE
                    icCheck2Image.visibility = View.VISIBLE
                    icCheck2Image.bringToFront()
                } else {
                    icCheck.visibility = View.INVISIBLE
                    icCheck2.visibility = View.VISIBLE
                    icCheck2.bringToFront()
                }
            }
        }

        fun bind(message: Message, date: String, time: String, position: Int, flagText: Boolean, isInLast30: Boolean, isAnswer: Boolean) {
            messageSave = message

            if(isAnswer) handleAnswerLayout(binding, message, true)
            else binding.answerLayout.root.visibility = View.GONE

            if(message.isForwarded) {
                binding.forwardLayout.root.visibility = View.VISIBLE
                binding.forwardLayout.root.setBackgroundResource(R.drawable.answer_background_sender)
                binding.forwardLayout.forwardUsername.text = message.usernameAuthorOriginal
            } else binding.forwardLayout.root.visibility = View.GONE

            val timeTextView = if(flagText) binding.timeTextView else binding.timeTextViewImage
            val editTextView = if(flagText) binding.editTextView else binding.editTextViewImage
            val icCheck = if(flagText) binding.icCheck else binding.icCheckImage
            val icCheck2 = if(flagText) binding.icCheck2 else binding.icCheck2Image
            val icError = if(flagText) binding.icError else binding.icErrorImage
            with(binding) {
                if(flagText) {
                    customMessageLayout.visibility = View.VISIBLE
                    timeLayout.visibility = View.GONE
                    message.text?.let {
                        if(message.isUrl == true) {
                            val processedText = parseMessageWithLinks(it)
                            binding.messageSenderTextView.text = processedText
                            binding.messageSenderTextView.movementMethod = LinkMovementMethod.getInstance()
                        } else binding.messageSenderTextView.text = it
                    }
                } else {
                    customMessageLayout.visibility = View.GONE
                    timeLayout.visibility = View.VISIBLE
                }
            }
            if(message.isUnsent == true) {
                binding.dateTextView.visibility = View.GONE
                timeTextView.text = "----"
                icCheck.visibility = View.INVISIBLE
                icCheck2.visibility = View.INVISIBLE
                editTextView.visibility = View.GONE
                icError.visibility = View.VISIBLE
            } else {
                icError.visibility = View.GONE
                if(date != "") {
                    binding.dateTextView.visibility = View.VISIBLE
                    binding.dateTextView.text = date
                } else {
                    binding.dateTextView.visibility = View.GONE
                    binding.space.visibility = View.GONE
                }

                timeTextView.text = time

                if(!canLongClick) {
                    if(!binding.checkbox.isVisible) binding.checkbox.visibility = View.VISIBLE
                    binding.checkbox.isChecked = position in checkedPositions
                }
                else binding.checkbox.visibility = View.GONE

                if (message.isRead) {
                    updateReadStatus()
                } else {
                    icCheck.visibility = View.VISIBLE
                    icCheck2.visibility = View.INVISIBLE
                }
                if(message.isEdited) editTextView.visibility = View.VISIBLE
                else editTextView.visibility = View.GONE
            }
            binding.progressBar.visibility = View.VISIBLE
            binding.errorImageView.visibility = View.GONE
            val semaphore = Semaphore(4)
            uiScopeMain.launch {
                val localMedias = async {
                    val medias = arrayListOf<LocalMedia>()
                    message.images?.forEachIndexed { index, image ->
                        val filePath = async {
                            semaphore.withPermit {
                                if (message.isUnsent == true) {
                                    Pair(message.localFilePaths?.get(index), true)
                                } else {
                                    if (messageViewModel.fManagerIsExist(image)) {
                                        Pair(messageViewModel.fManagerGetFilePath(image), true)
                                    } else {
                                        try {
                                            Pair(messageViewModel.downloadFile(context, "photos", image), false)
                                        } catch (_: Exception) {
                                            Pair(null, true)
                                        }
                                    }
                                }
                            }
                        }
                        val (first, second) = filePath.await()
                        if (first != null) {
                            val file = File(first)
                            if (file.exists()) {
                                if (!second && isInLast30) messageViewModel.fManagerSaveFile(image, file.readBytes())
                                medias += messageViewModel.fileToLocalMedia(file)
                            } else {
                                Log.e("ImageError", "File does not exist: $filePath")
                                binding.progressBar.visibility = View.GONE
                                binding.errorImageView.visibility = View.VISIBLE
                            }
                        } else {
                            binding.progressBar.visibility = View.GONE
                            binding.errorImageView.visibility = View.VISIBLE
                        }
                    }
                    return@async medias
                }
                localMediasSave = localMedias.await()
                adapter.images = localMedias.await()
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    // ViewHolder для текстовых сообщений получателя
    inner class MessagesViewHolderCodeReceiver(private val binding: ItemCodeReceiverBinding) : RecyclerView.ViewHolder(binding.root) {

        private var messageSave: Message? = null

        private val jetBrainsMono: Typeface? = ResourcesCompat.getFont(context, R.font.jetbrains_mono_medium)

        init {
            binding.root.setOnClickListener {
                messageSave?.let {
                    if(!canLongClick && canDelete) {
                        savePosition(it.id, false)
                    } else actionListener.onMessageClick(it, itemView, false)
                }
            }
            binding.root.setOnLongClickListener {
                if(canLongClick && canDelete) {
                    messageSave?.let {
                        onLongClick(it.id, false)
                        actionListener.onMessageLongClick(itemView)
                    }
                }
                true
            }
            binding.checkbox.setOnClickListener {
                messageSave?.let { savePosition(it.id, false) }
            }
            binding.openFullIcon.setOnClickListener {
                messageSave?.let { actionListener.onCodeOpenClick(it) }
            }
        }

        fun updateAvatar() {
            binding.photoImageView.visibility = View.GONE
            binding.spaceAvatar.visibility = View.VISIBLE
        }

        fun bind(message: Message, date: String, time: String, position: Int) {
            messageSave = message

            if(!canLongClick && canDelete) {
                if(!binding.checkbox.isVisible) binding.checkbox.visibility = View.VISIBLE
                binding.checkbox.isChecked = position in checkedPositions
            } else binding.checkbox.visibility = View.GONE

            message.code?.let {
                val shortCode = it.lines().take(5).joinToString("\n")
                val lang = when(message.codeLanguage) {
                    "java" -> LanguageName.JAVA
                    "python" -> LanguageName.PYTHON
                    "go" -> LanguageName.GO_LANG
                    else -> null
                }
                binding.codeView.setTypeface(jetBrainsMono)
                lang?.let { lang ->
                    LanguageManager(context, binding.codeView).apply {
                        applyTheme(lang, ThemeName.MONOKAI)
                    }
                }
                binding.codeView.setText(shortCode)
            }
            binding.languageTextView.text = message.codeLanguage ?: "unknown"
            if(date != "") {
                binding.dateTextView.visibility = View.VISIBLE
                binding.dateTextView.text = date
            } else {
                binding.dateTextView.visibility = View.GONE
                binding.space.visibility = View.GONE
            }

            binding.timeTextViewImage.text = time
            if(isGroup) {
                val user = members[message.id]
                if(user != null) {
                    if(user.first != null) {
                        binding.userNameTextView.visibility = View.VISIBLE
                        binding.userNameTextView.text = user.first
                    } else binding.userNameTextView.visibility = View.GONE
                    if(user.second != null) {
                        binding.photoImageView.visibility = View.VISIBLE
                        binding.spaceAvatar.visibility = View.GONE
                        if(user.second != "") messageViewModel.avatarSet(user.second ?: "", binding.photoImageView, context)
                    } else {
                        binding.spaceAvatar.visibility = View.VISIBLE
                        binding.photoImageView.visibility = View.GONE
                    }
                } else {
                    binding.spaceAvatar.visibility = View.VISIBLE
                    binding.photoImageView.visibility = View.GONE
                    binding.userNameTextView.visibility = View.GONE
                }
            } else {
                binding.spaceAvatar.visibility = View.GONE
                binding.photoImageView.visibility = View.GONE
                binding.userNameTextView.visibility = View.GONE
            }

            if(message.isEdited) binding.editTextViewImage.visibility = View.VISIBLE
            else binding.editTextViewImage.visibility = View.GONE
        }
    }

    // ViewHolder для текстовых сообщений отправителя
    inner class MessagesViewHolderCodeSender(private val binding: ItemCodeSenderBinding) : RecyclerView.ViewHolder(binding.root) {

        private var messageSave: Message? = null

        private val jetBrainsMono: Typeface? = ResourcesCompat.getFont(context, R.font.jetbrains_mono_medium)

        init {
            binding.root.setOnClickListener {
                messageSave?.let {
                    when {
                        it.isUnsent == true -> actionListener.onUnsentMessageClick(it, itemView)
                        !canLongClick -> savePosition(it.id, true)
                        else -> actionListener.onMessageClick(it, itemView, true)
                    }
                }
            }
            binding.root.setOnLongClickListener {
                if(canLongClick) {
                    messageSave?.let {
                        onLongClick(it.id, true)
                        actionListener.onMessageLongClick(itemView)
                    }
                }
                true
            }
            binding.checkbox.setOnClickListener {
                messageSave?.let { savePosition(it.id, true) }
            }
            binding.openFullIcon.setOnClickListener {
                messageSave?.let { actionListener.onCodeOpenClick(it) }
            }
        }

        fun updateReadStatus() {
            binding.icCheckImage.visibility = View.INVISIBLE
            binding.icCheck2Image.visibility = View.VISIBLE
            binding.icCheck2Image.bringToFront()
        }

        fun bind(message: Message, date: String, time: String, position: Int) {
            messageSave = message

            if(message.isUnsent == true) {
                with(binding) {
                    timeTextViewImage.text = "----"
                    dateTextView.visibility = View.GONE
                    icCheckImage.visibility = View.INVISIBLE
                    icCheck2Image.visibility = View.INVISIBLE
                    editTextViewImage.visibility = View.GONE
                    icErrorImage.visibility = View.VISIBLE
                }
            } else {
                if(!canLongClick) {
                    if(!binding.checkbox.isVisible) binding.checkbox.visibility = View.VISIBLE
                    binding.checkbox.isChecked = position in checkedPositions
                } else binding.checkbox.visibility = View.GONE

                binding.icErrorImage.visibility = View.GONE
                if(date != "") {
                    binding.dateTextView.visibility = View.VISIBLE
                    binding.dateTextView.text = date
                } else {
                    binding.dateTextView.visibility = View.GONE
                    binding.space.visibility = View.GONE
                }

                binding.timeTextViewImage.text = time

                if (message.isRead) {
                    updateReadStatus()
                } else {
                    binding.icCheckImage.visibility = View.VISIBLE
                    binding.icCheck2Image.visibility = View.INVISIBLE
                }
                if(message.isEdited) binding.editTextViewImage.visibility = View.VISIBLE
                else binding.editTextViewImage.visibility = View.GONE
            }
            binding.languageTextView.text = message.codeLanguage ?: "unknown"
            message.code?.let {
                val shortCode = it.lines().take(5).joinToString("\n")
                val lang = when(message.codeLanguage) {
                    "java" -> LanguageName.JAVA
                    "python" -> LanguageName.PYTHON
                    "go" -> LanguageName.GO_LANG
                    else -> null
                }
                binding.codeView.setTypeface(jetBrainsMono)
                lang?.let { lang ->
                    LanguageManager(context, binding.codeView).apply {
                        applyTheme(lang, ThemeName.MONOKAI)
                    }
                }
                binding.codeView.setText(shortCode)
            }
        }
    }
}
