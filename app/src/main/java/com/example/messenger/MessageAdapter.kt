package com.example.messenger

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.text.method.LinkMovementMethod
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
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
import com.example.messenger.picker.DateUtils
import com.example.messenger.states.AvatarState
import com.luck.picture.lib.config.PictureMimeType
import com.luck.picture.lib.config.SelectMimeType
import com.luck.picture.lib.entity.LocalMedia
import com.masoudss.lib.SeekBarOnProgressChanged
import com.masoudss.lib.WaveformSeekBar
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import com.example.messenger.states.MessageUi
import com.example.messenger.states.ReplyState
import com.example.messenger.states.VoiceState
import com.example.messenger.utils.formatTime

interface MessageActionListener {
    fun onMessageClick(message: Message, itemView: View, isSender: Boolean)
    fun onMessageClickImage(message: Message, itemView: View, localMedias: ArrayList<LocalMedia>, isSender: Boolean)
    fun onMessageLongClick(messageId: Int)
    fun onImagesClick(images: ArrayList<LocalMedia>, position: Int)
    fun onUnsentMessageClick(message: Message, itemView: View)
    fun onCodeOpenClick(message: Message)
    fun onReplyClick(referenceId: Int)
    fun onSelected(messageId: Int)
    fun onVoiceClick(messageId: Int)
    fun onVoiceSeek(messageId: Int, progress: Int)
}


class MessageDiffCallback : DiffUtil.ItemCallback<MessageUi>() {
    override fun areItemsTheSame(oldItem: MessageUi, newItem: MessageUi): Boolean {
        return oldItem.message.id == newItem.message.id
    }

    override fun areContentsTheSame(oldItem: MessageUi, newItem: MessageUi): Boolean {
        return oldItem == newItem
    }

    override fun getChangePayload(oldItem: MessageUi, newItem: MessageUi): Any? {
        return if (!oldItem.message.isRead && newItem.message.isRead) "isRead" else null
    }
}

class MessageAdapter(
    private val actionListener: MessageActionListener,
    private val currentUserId: Int,
    private val context: Context,
    private val isGroup: Boolean,
    private val canDelete: Boolean
) : ListAdapter<MessageUi, RecyclerView.ViewHolder>(MessageDiffCallback()) {
    var canLongClick: Boolean = true

    override fun getItemCount(): Int = currentList.size

    override fun getItemId(position: Int): Long { // Исключает потенциальные баги с индексацией
        return getItem(position).message.id.toLong()
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
        const val PAYLOAD_PROGRESS = "progress"
        const val PAYLOAD_PAUSE = "pause"
        const val PAYLOAD_STOP = "stop"
    }

    override fun getItemViewType(position: Int): Int {
        val message = getItem(position)?.message ?: return -1
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
        if (payloads.isNotEmpty()) {
            payloads.forEach { payload ->
                when(payload) {
                    is Pair<*, *> -> {
                        if (payload.first == PAYLOAD_PROGRESS) {
                            val progress = payload.second as Int
                            if (holder is MessagesViewHolderVoiceReceiver) holder.updateProgress(progress)
                            else if (holder is MessagesViewHolderVoiceSender) holder.updateProgress(progress)
                            return
                        }
                    }
                    PAYLOAD_PAUSE -> {
                        if (holder is MessagesViewHolderVoiceReceiver) holder.playerPause()
                        else if (holder is MessagesViewHolderVoiceSender) holder.playerPause()
                        return
                    }
                    PAYLOAD_STOP -> {
                        if (holder is MessagesViewHolderVoiceReceiver) holder.playerStop()
                        else if (holder is MessagesViewHolderVoiceSender) holder.playerStop()
                        return
                    }
                    "isRead" -> {
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
                }
            }
        } else onBindViewHolder(holder, position)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position) ?: return

        if (item.isHighlighted) {
            holder.itemView.setBackgroundColor(ContextCompat.getColor(context, R.color.chatAnswerHighlight))
            return // todo тест, возможно так нельзя
        } else holder.itemView.setBackgroundColor(Color.TRANSPARENT)

        when (holder) {
            is MessagesViewHolderReceiver -> holder.bind(item)
            is MessagesViewHolderSender -> holder.bind(item)
            is MessagesViewHolderVoiceReceiver -> holder.bind(item)
            is MessagesViewHolderVoiceSender -> holder.bind(item)
            is MessagesViewHolderFileReceiver -> holder.bind(item)
            is MessagesViewHolderFileSender -> holder.bind(item)
            is MessagesViewHolderTextImageReceiver -> holder.bind(item)
            is MessagesViewHolderTextImageSender -> holder.bind(item)
            is MessagesViewHolderTextImagesReceiver -> holder.bind(item)
            is MessagesViewHolderTextImagesSender -> holder.bind(item)
            is MessagesViewHolderCodeReceiver -> holder.bind(item)
            is MessagesViewHolderCodeSender -> holder.bind(item)
        }
    }

    // ViewHolder для текстовых сообщений получателя
    inner class MessagesViewHolderReceiver(private val binding: ItemMessageReceiverBinding) : RecyclerView.ViewHolder(binding.root) {

        private var messageSave: Message? = null

        init {
            binding.root.setOnClickListener {
                messageSave?.let {
                    if(!canLongClick && canDelete) {
                        actionListener.onSelected(it.id)
                    } else actionListener.onMessageClick(it, itemView, false)
                }
            }
            binding.root.setOnLongClickListener {
                if(canLongClick && canDelete) {
                    messageSave?.let {
                        actionListener.onMessageLongClick(it.id)
                    }
                }
                true
            }
            binding.checkbox.setOnClickListener {
                messageSave?.let { actionListener.onSelected(it.id) }
            }
            binding.answerLayout.root.setOnClickListener {
                messageSave?.referenceToMessageId?.let { actionListener.onReplyClick(it) }
            }
        }

        fun bind(ui: MessageUi) {
            messageSave = ui.message

            binding.checkbox.isVisible = ui.isShowCheckbox
            when(val state = ui.replyState) {
                is ReplyState.Loading -> {
                    binding.answerLayout.root.setBackgroundResource(R.drawable.answer_background)
                    binding.answerLayout.root.isVisible = true
                    binding.answerLayout.answerMessage.text = "..."
                }
                is ReplyState.Ready -> {
                    binding.answerLayout.root.setBackgroundResource(R.drawable.answer_background)
                    binding.answerLayout.root.isVisible = true
                    binding.answerLayout.answerMessage.text = state.previewText
                    binding.answerLayout.answerUsername.text = state.username
                    state.previewImagePath?.let {
                        Glide.with(binding.answerLayout.answerImageView)
                            .load(it)
                            .centerCrop()
                            .into(binding.answerLayout.answerImageView)
                    }
                }
                is ReplyState.Error -> {
                    binding.answerLayout.root.setBackgroundResource(R.drawable.answer_background)
                    binding.answerLayout.root.isVisible = true
                    binding.answerLayout.answerMessage.text = "Сообщение недоступно"
                }
                null -> binding.answerLayout.root.isVisible = false
            }

            if(ui.message.isForwarded) {
                binding.forwardLayout.root.isVisible = true
                binding.forwardLayout.root.setBackgroundResource(R.drawable.answer_background)
                binding.forwardLayout.forwardUsername.text = ui.message.usernameAuthorOriginal
            } else binding.forwardLayout.root.isVisible = false

            binding.messageReceiverTextView.text = ui.parsedText
            if(ui.message.isUrl == true) binding.messageReceiverTextView.movementMethod = LinkMovementMethod.getInstance()

            if(ui.formattedDate != "") {
                binding.dateTextView.isVisible = true
                binding.dateTextView.text = ui.formattedDate
            } else {
                binding.dateTextView.isVisible = false
                binding.space.isVisible = false
            }

            binding.timeTextView.text = ui.formattedTime
            binding.editTextView.isVisible = ui.message.isEdited
            if(isGroup) {
                binding.userNameTextView.isVisible = ui.showUsername
                ui.username?.let { binding.userNameTextView.text = it }
                when(val state = ui.avatarState) {
                    is AvatarState.Loading -> {
                        binding.photoImageView.isVisible = true
                    }
                    is AvatarState.Ready -> {
                        binding.photoImageView.isVisible = true
                        Glide.with(binding.photoImageView)
                            .load(state.uri)
                            .apply(RequestOptions.circleCropTransform())
                            .into(binding.photoImageView)
                    }
                    is AvatarState.Error -> {
                        binding.photoImageView.isVisible = true
                    }
                    null -> {
                        binding.photoImageView.isVisible = ui.showAvatar
                        binding.spaceAvatar.isVisible = !ui.showAvatar
                    }
                }
            } else {
                binding.spaceAvatar.isVisible = false
                binding.photoImageView.isVisible = false
                binding.userNameTextView.isVisible = false
            }
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
                        !canLongClick -> actionListener.onSelected(it.id)
                        else -> actionListener.onMessageClick(it, itemView, true)
                    }
                }
            }
            binding.root.setOnLongClickListener {
                if(canLongClick && messageSave?.isUnsent != true) {
                    messageSave?.let {
                        actionListener.onMessageLongClick(it.id)
                    }
                }
                true
            }
            binding.checkbox.setOnClickListener {
                messageSave?.let { actionListener.onSelected(it.id) }
            }
            binding.answerLayout.root.setOnClickListener {
                messageSave?.referenceToMessageId?.let { actionListener.onReplyClick(it) }
            }
        }

        fun updateReadStatus() {
            binding.icCheck.visibility = View.INVISIBLE
            binding.icCheck2.isVisible = true
            binding.icCheck2.bringToFront()
        }

        fun bind(ui: MessageUi) {
            messageSave = ui.message

            binding.checkbox.isVisible = ui.isShowCheckbox && ui.message.isUnsent != true
            when(val state = ui.replyState) {
                is ReplyState.Loading -> {
                    binding.answerLayout.root.setBackgroundResource(R.drawable.answer_background_sender)
                    binding.answerLayout.root.isVisible = true
                    binding.answerLayout.answerMessage.text = "..."
                }
                is ReplyState.Ready -> {
                    binding.answerLayout.root.setBackgroundResource(R.drawable.answer_background_sender)
                    binding.answerLayout.root.isVisible = true
                    binding.answerLayout.answerMessage.text = state.previewText
                    binding.answerLayout.answerUsername.text = state.username
                    state.previewImagePath?.let {
                        Glide.with(binding.answerLayout.answerImageView)
                            .load(it)
                            .centerCrop()
                            .into(binding.answerLayout.answerImageView)
                    }
                }
                is ReplyState.Error -> {
                    binding.answerLayout.root.setBackgroundResource(R.drawable.answer_background)
                    binding.answerLayout.root.isVisible = true
                    binding.answerLayout.answerMessage.text = "Сообщение недоступно"
                }
                null -> binding.answerLayout.root.isVisible = false
            }

            if(ui.message.isForwarded) {
                binding.forwardLayout.root.isVisible = true
                binding.forwardLayout.root.setBackgroundResource(R.drawable.answer_background_sender)
                binding.forwardLayout.forwardUsername.text = ui.message.usernameAuthorOriginal
            } else binding.forwardLayout.root.isVisible = false

            binding.messageSenderTextView.text = ui.parsedText
            if(ui.message.isUrl == true) binding.messageSenderTextView.movementMethod = LinkMovementMethod.getInstance()

            if(ui.message.isUnsent == true) {
                with(binding) {
                    timeTextView.text = "----"
                    dateTextView.isVisible = false
                    icCheck.visibility = View.INVISIBLE
                    icCheck2.visibility = View.INVISIBLE
                    editTextView.isVisible = false
                    icError.isVisible = true
                }
            } else {
                binding.icError.isVisible = false
                if(ui.formattedDate != "") {
                    binding.dateTextView.isVisible = true
                    binding.dateTextView.text = ui.formattedDate
                } else {
                    binding.dateTextView.isVisible = false
                    binding.space.isVisible = false
                }
                binding.timeTextView.text = ui.formattedTime

                if (ui.message.isRead) {
                    updateReadStatus()
                } else {
                    binding.icCheck.isVisible = true
                    binding.icCheck2.visibility = View.INVISIBLE
                }
                binding.editTextView.isVisible = ui.message.isEdited
            }
        }
    }

    inner class MessagesViewHolderVoiceReceiver(private val binding: ItemVoiceReceiverBinding) : RecyclerView.ViewHolder(binding.root) {
        private var isPlaying: Boolean = false
        private var messageSave: Message? = null
        private var lastDurationStr: String? = null

        init {
            binding.playButton.setOnClickListener {
                messageSave?.let {
                    actionListener.onVoiceClick(it.id)
                }
            }
            binding.waveformSeekBar.onProgressChanged = object : SeekBarOnProgressChanged {
                override fun onProgressChanged(waveformSeekBar: WaveformSeekBar, progress: Float, fromUser: Boolean) {
                    if (fromUser) {
                        messageSave?.let {
                            actionListener.onVoiceSeek(it.id, progress.toInt())
                        }
                    }
                }
            }
            binding.root.setOnClickListener {
                messageSave?.let {
                    if(!canLongClick && canDelete) {
                        actionListener.onSelected(it.id)
                    } else actionListener.onMessageClick(it, itemView, false)
                }
            }
            binding.root.setOnLongClickListener {
                if(canLongClick && canDelete) {
                    messageSave?.let {
                        actionListener.onMessageLongClick(it.id)
                    }
                }
                true
            }
            binding.checkbox.setOnClickListener {
                messageSave?.let { actionListener.onSelected(it.id) }
            }
            binding.answerLayout.root.setOnClickListener {
                messageSave?.referenceToMessageId?.let { actionListener.onReplyClick(it) }
            }
        }

        fun updateProgress(progress: Int) {
            if(!isPlaying) {
                binding.playButton.setImageResource(R.drawable.ic_pause)
                isPlaying = true
            }
            binding.waveformSeekBar.progress = progress.toFloat()
            binding.timeVoiceTextView.text = formatTime(progress.toLong())
        }

        fun playerPause() {
            isPlaying = false
            binding.playButton.setImageResource(R.drawable.ic_play)
        }

        fun playerStop() {
            isPlaying = false
            binding.playButton.setImageResource(R.drawable.ic_play)
            binding.waveformSeekBar.progress = 0f
            binding.timeVoiceTextView.text = lastDurationStr ?: "0"
        }

        fun bind(ui: MessageUi) {
            messageSave = ui.message

            binding.checkbox.isVisible = ui.isShowCheckbox
            when(val state = ui.replyState) {
                is ReplyState.Loading -> {
                    binding.answerLayout.root.setBackgroundResource(R.drawable.answer_background)
                    binding.answerLayout.root.isVisible = true
                    binding.answerLayout.answerMessage.text = "..."
                }
                is ReplyState.Ready -> {
                    binding.answerLayout.root.setBackgroundResource(R.drawable.answer_background)
                    binding.answerLayout.root.isVisible = true
                    binding.answerLayout.answerMessage.text = state.previewText
                    binding.answerLayout.answerUsername.text = state.username
                    state.previewImagePath?.let {
                        Glide.with(binding.answerLayout.answerImageView)
                            .load(it)
                            .centerCrop()
                            .into(binding.answerLayout.answerImageView)
                    }
                }
                is ReplyState.Error -> {
                    binding.answerLayout.root.setBackgroundResource(R.drawable.answer_background)
                    binding.answerLayout.root.isVisible = true
                    binding.answerLayout.answerMessage.text = "Сообщение недоступно"
                }
                null -> binding.answerLayout.root.isVisible = false
            }

            if(ui.message.isForwarded) {
                binding.forwardLayout.root.isVisible = true
                binding.forwardLayout.root.setBackgroundResource(R.drawable.answer_background)
                binding.forwardLayout.forwardUsername.text = ui.message.usernameAuthorOriginal
            } else binding.forwardLayout.root.isVisible = false

            if(ui.formattedDate != "") {
                binding.dateTextView.isVisible = true
                binding.dateTextView.text = ui.formattedDate
            } else {
                binding.dateTextView.isVisible = false
                binding.space.isVisible = false
            }

            binding.timeTextView.text = ui.formattedTime
            binding.editTextView.isVisible = ui.message.isEdited
            if(isGroup) {
                binding.userNameTextView.isVisible = ui.showUsername
                ui.username?.let { binding.userNameTextView.text = it }
                when(val state = ui.avatarState) {
                    is AvatarState.Loading -> {
                        binding.photoImageView.isVisible = true
                    }
                    is AvatarState.Ready -> {
                        binding.photoImageView.isVisible = true
                        Glide.with(binding.photoImageView)
                            .load(state.uri)
                            .apply(RequestOptions.circleCropTransform())
                            .into(binding.photoImageView)
                    }
                    is AvatarState.Error -> {
                        binding.photoImageView.isVisible = true
                    }
                    null -> {
                        binding.photoImageView.isVisible = ui.showAvatar
                        binding.spaceAvatar.isVisible = !ui.showAvatar
                    }
                }
            } else {
                binding.spaceAvatar.isVisible = false
                binding.photoImageView.isVisible = false
                binding.userNameTextView.isVisible = false
            }

            when (val state = ui.voiceState) {
                is VoiceState.Loading -> {
                    binding.progressBar.isVisible = true
                    binding.playButton.isVisible = false
                    binding.errorImageView.isVisible = false
                }
                is VoiceState.Ready -> {
                    binding.progressBar.isVisible = false
                    binding.playButton.isVisible = true
                    binding.errorImageView.isVisible = false
                    binding.waveformSeekBar.setSampleFrom(ui.message.waveform?.toIntArray() ?: intArrayOf())
                    binding.waveformSeekBar.maxProgress = state.duration.toFloat()
                    val st = formatTime(state.duration)
                    binding.timeVoiceTextView.text = st
                    lastDurationStr = st
                }
                is VoiceState.Error -> {
                    binding.progressBar.isVisible = false
                    binding.playButton.isVisible = false
                    binding.errorImageView.isVisible = true
                }
                null -> Unit
            }
        }
    }

    inner class MessagesViewHolderVoiceSender(private val binding: ItemVoiceSenderBinding) : RecyclerView.ViewHolder(binding.root) {
        private var isPlaying: Boolean = false
        private var messageSave: Message? = null
        private var lastDurationStr: String? = null

        init {
            binding.playButton.setOnClickListener {
                messageSave?.let {
                    actionListener.onVoiceClick(it.id)
                }
            }
            binding.waveformSeekBar.onProgressChanged = object : SeekBarOnProgressChanged {
                override fun onProgressChanged(waveformSeekBar: WaveformSeekBar, progress: Float, fromUser: Boolean) {
                    if (fromUser) {
                        messageSave?.let {
                            actionListener.onVoiceSeek(it.id, progress.toInt())
                        }
                    }
                }
            }
            binding.root.setOnClickListener {
                messageSave?.let {
                    when {
                        it.isUnsent == true -> actionListener.onUnsentMessageClick(it, itemView)
                        !canLongClick -> actionListener.onSelected(it.id)
                        else -> actionListener.onMessageClick(it, itemView, true)
                    }
                }
            }
            binding.root.setOnLongClickListener {
                if(canLongClick && messageSave?.isUnsent != true) {
                    messageSave?.let {
                        actionListener.onMessageLongClick(it.id)
                    }
                }
                true
            }
            binding.checkbox.setOnClickListener {
                messageSave?.let { actionListener.onSelected(it.id) }
            }
            binding.answerLayout.root.setOnClickListener {
                messageSave?.referenceToMessageId?.let { actionListener.onReplyClick(it) }
            }
        }

        fun updateProgress(progress: Int) {
            if(!isPlaying) {
                binding.playButton.setImageResource(R.drawable.ic_pause)
                isPlaying = true
            }
            binding.waveformSeekBar.progress = progress.toFloat()
            binding.timeVoiceTextView.text = formatTime(progress.toLong())
        }

        fun playerPause() {
            isPlaying = false
            binding.playButton.setImageResource(R.drawable.ic_play)
        }

        fun playerStop() {
            isPlaying = false
            binding.playButton.setImageResource(R.drawable.ic_play)
            binding.waveformSeekBar.progress = 0f
            binding.timeVoiceTextView.text = lastDurationStr ?: "0"
        }

        fun updateReadStatus() {
            binding.icCheck.visibility = View.INVISIBLE
            binding.icCheck2.isVisible = true
            binding.icCheck2.bringToFront()
        }

        fun bind(ui: MessageUi) {
            messageSave = ui.message

            binding.checkbox.isVisible = ui.isShowCheckbox && ui.message.isUnsent != true
            when(val state = ui.replyState) {
                is ReplyState.Loading -> {
                    binding.answerLayout.root.setBackgroundResource(R.drawable.answer_background_sender)
                    binding.answerLayout.root.isVisible = true
                    binding.answerLayout.answerMessage.text = "..."
                }
                is ReplyState.Ready -> {
                    binding.answerLayout.root.setBackgroundResource(R.drawable.answer_background_sender)
                    binding.answerLayout.root.isVisible = true
                    binding.answerLayout.answerMessage.text = state.previewText
                    binding.answerLayout.answerUsername.text = state.username
                    state.previewImagePath?.let {
                        Glide.with(binding.answerLayout.answerImageView)
                            .load(it)
                            .centerCrop()
                            .into(binding.answerLayout.answerImageView)
                    }
                }
                is ReplyState.Error -> {
                    binding.answerLayout.root.setBackgroundResource(R.drawable.answer_background)
                    binding.answerLayout.root.isVisible = true
                    binding.answerLayout.answerMessage.text = "Сообщение недоступно"
                }
                null -> binding.answerLayout.root.isVisible = false
            }

            if(ui.message.isForwarded) {
                binding.forwardLayout.root.isVisible = true
                binding.forwardLayout.root.setBackgroundResource(R.drawable.answer_background_sender)
                binding.forwardLayout.forwardUsername.text = ui.message.usernameAuthorOriginal
            } else binding.forwardLayout.root.isVisible = false

            if(ui.message.isUnsent == true) {
                with(binding) {
                    timeTextView.text = "----"
                    dateTextView.isVisible = false
                    icCheck.visibility = View.INVISIBLE
                    icCheck2.visibility = View.INVISIBLE
                    editTextView.isVisible = false
                    icError.isVisible = true
                }
            } else {
                binding.icError.isVisible = false
                if(ui.formattedDate != "") {
                    binding.dateTextView.isVisible = true
                    binding.dateTextView.text = ui.formattedDate
                } else {
                    binding.dateTextView.isVisible = false
                    binding.space.isVisible = false
                }

                binding.timeTextView.text = ui.formattedTime
                if (ui.message.isRead) {
                    updateReadStatus()
                } else {
                    binding.icCheck.isVisible = true
                    binding.icCheck2.visibility = View.INVISIBLE
                }
                binding.editTextView.isVisible = ui.message.isEdited
            }
            when (val state = ui.voiceState) {
                is VoiceState.Loading -> {
                    binding.progressBar.isVisible = true
                    binding.playButton.isVisible = false
                    binding.errorImageView.isVisible = false
                }
                is VoiceState.Ready -> {
                    binding.progressBar.isVisible = false
                    binding.playButton.isVisible = true
                    binding.errorImageView.isVisible = false
                    binding.waveformSeekBar.setSampleFrom(ui.message.waveform?.toIntArray() ?: intArrayOf())
                    binding.waveformSeekBar.maxProgress = state.duration.toFloat()
                    val st = formatTime(state.duration)
                    binding.timeVoiceTextView.text = st
                    lastDurationStr = st
                }
                is VoiceState.Error -> {
                    binding.progressBar.isVisible = false
                    binding.playButton.isVisible = false
                    binding.errorImageView.isVisible = true
                }
                null -> Unit
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
