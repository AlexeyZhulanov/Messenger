package com.example.messenger

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Rect
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
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
import com.example.messenger.model.ConversationSettings
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

interface MessageActionListener {
    fun onMessageClick(message: Message, itemView: View, isSender: Boolean)
    fun onMessageClickImage(message: Message, itemView: View, localMedias: ArrayList<LocalMedia>, isSender: Boolean)
    fun onMessageLongClick(itemView: View)
    fun onImagesClick(images: ArrayList<LocalMedia>, position: Int)
    fun onUnsentMessageClick(message: Message, itemView: View)
    fun onUnsentMessagesAdd()
}


class MessageDiffCallback : DiffUtil.ItemCallback<Pair<Message, String>>() {
    override fun areItemsTheSame(oldItem: Pair<Message, String>, newItem: Pair<Message, String>): Boolean {
        return oldItem.first.id == newItem.first.id
    }

    override fun areContentsTheSame(oldItem: Pair<Message, String>, newItem: Pair<Message, String>): Boolean {
        return oldItem.first == newItem.first
    }
}

class MessageAdapter(
    private val actionListener: MessageActionListener,
    private val currentUserId: Int,
    private val context: Context,
    private val messageViewModel: BaseChatViewModel,
    private val members: List<User>
) : ListAdapter<Pair<Message, String>, RecyclerView.ViewHolder>(MessageDiffCallback()) {

    var canLongClick: Boolean = true
    private var checkedPositions: MutableSet<Int> = mutableSetOf()
    private var mapPositions: MutableMap<Int, Boolean> = mutableMapOf()
    var dialogSettings: ConversationSettings = ConversationSettings()
    private var highlightedPosition: Int? = null
    private val uiScopeMain = CoroutineScope(Dispatchers.Main)
    private val isGroup = members.isNotEmpty()


    fun addNewMessage(message: Pair<Message, String>) {
        val updatedList = currentList.toMutableList()
        updatedList.add(0, message)
        submitList(updatedList)
    }

    fun deleteUnsentMessage(message: Message) {
        val updatedList = currentList.toMutableList()
        updatedList.remove(Pair(message, ""))
        submitList(updatedList)
    }

    fun getItemNotProtected(position: Int) : Pair<Message, String> = getItem(position)

    override fun getItemCount(): Int = currentList.size

    fun getDeleteList(): List<Int> {
        val list = mutableListOf<Int>()
        checkedPositions.forEach { idx ->
            val message = getItem(idx)?.first
            if(message != null) {
                list.add(message.id)
            }
        }
        return list
    }

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
            var startPosition = -1
            val updatedPagingData = currentPagingData.mapIndexed { index, pair ->
                if (pair.first.id in listIds) {
                    pair.first.isRead = true
                    if (startPosition == -1) startPosition = index
                }
                pair
            }
            uiScopeMain.launch {
                submitList(updatedPagingData)
                //messageList = updatedPagingData.toMutableList()
                //notifyItemRangeChanged(startPosition, listIds.size)
                Log.d("testMarkReadMessages", "startpos: $startPosition, size: ${listIds.size}")
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun clearPositions() {
        canLongClick = true
        checkedPositions.clear()
        notifyDataSetChanged()
    }

    private fun getItemPositionWithId(idMessage: Int): Int {
        return currentList.indexOfLast { it.first.id == idMessage }
    }

    private fun savePosition(messageId: Int, isSender: Boolean) {
        val position = getItemPositionWithId(messageId)
        if (position in checkedPositions) {
            checkedPositions.remove(position)
            mapPositions.remove(position)
        } else {
            checkedPositions.add(position)
            mapPositions[position] = isSender
        }
        notifyItemChanged(position)
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun onLongClick(messageId: Int, isSender: Boolean) {
        if(canLongClick) {
            savePosition(messageId, isSender)
            canLongClick = false
            notifyDataSetChanged()
        }
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
                        else -> TYPE_VOICE_SENDER
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
                        else -> TYPE_VOICE_RECEIVER
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
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        var message: Message
        var date: String
        try {
            message = getItem(position)?.first ?: return
            date = getItem(position)?.second ?: return
        } catch (_: IndexOutOfBoundsException) {
            Toast.makeText(context, "Ошибка индексации", Toast.LENGTH_SHORT).show()
            message = getItem(position - 1)?.first ?: return
            date = getItem(position - 1)?.second ?: return
        }
        var flagText = false
        if(!message.text.isNullOrEmpty()) flagText = true
        val isInLast30 = position >= itemCount - 30
        val isAnswer = message.referenceToMessageId != null
        if (position == highlightedPosition) {
            holder.itemView.setBackgroundColor(Color.YELLOW)
            // Убираем подсветку через 1 секунду
            holder.itemView.postDelayed({
                holder.itemView.setBackgroundColor(Color.TRANSPARENT)
            }, 1300)
        } else {
            holder.itemView.setBackgroundColor(Color.TRANSPARENT)
        }
        when (holder) {
            is MessagesViewHolderReceiver ->  {
                holder.bind(message, date, position, isAnswer)
                if (!isAnswer) holder.clearAnswerLayout()
            }
            is MessagesViewHolderSender -> {
                holder.bind(message, date, position, isAnswer)
                if (!isAnswer) holder.clearAnswerLayout()
            }
            is MessagesViewHolderVoiceReceiver -> {
                holder.bind(message, date, position, isInLast30, isAnswer)
                if (!isAnswer) holder.clearAnswerLayout()
            }
            is MessagesViewHolderVoiceSender -> {
                holder.bind(message, date, position, isInLast30, isAnswer)
                if (!isAnswer) holder.clearAnswerLayout()
            }
            is MessagesViewHolderFileReceiver -> {
                holder.bind(message, date, position, isInLast30, isAnswer)
                if (!isAnswer) holder.clearAnswerLayout()
            }
            is MessagesViewHolderFileSender -> {
                holder.bind(message, date, position, isInLast30, isAnswer)
                if (!isAnswer) holder.clearAnswerLayout()
            }
            is MessagesViewHolderTextImageReceiver -> {
                holder.bind(message, date, position, flagText, isInLast30, isAnswer)
                if (!isAnswer) holder.clearAnswerLayout()
            }
            is MessagesViewHolderTextImageSender -> {
                holder.bind(message, date, position, flagText, isInLast30, isAnswer)
                if (!isAnswer) holder.clearAnswerLayout()
            }
            is MessagesViewHolderTextImagesReceiver -> {
                holder.bind(message, date, position, flagText, isInLast30, isAnswer)
                if (!isAnswer) holder.clearAnswerLayout()
            }
            is MessagesViewHolderTextImagesSender -> {
                holder.bind(message, date, position, flagText, isInLast30, isAnswer)
                if (!isAnswer) holder.clearAnswerLayout()
            }
        }
    }

    fun highlightPosition(position: Int) {
        highlightedPosition = position
        notifyItemChanged(position)
    }

    private inline fun <reified T : ViewBinding> handleAnswerLayout(binder: T, message: Message) {
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
        binding.answerUsername.text = message.usernameAuthorOriginal
        val tmpId = message.referenceToMessageId
        if(tmpId == null) {
            binding.answerMessage.text = "??????????"
        } else {
            val chk = getItemPositionWithId(tmpId)
            if(chk == -1) {
                uiScopeMain.launch {
                    val mes = async { messageViewModel.findMessage(tmpId) }
                    val (m, p) = mes.await()
                    if(m.images != null) {
                        messageViewModel.imageSet(m.images!!.first(), binding.answerImageView, context)
                    }
                    binding.answerMessage.text = when {
                        m.text != null -> m.text
                        m.images != null -> "Фотография"
                        m.file != null -> m.file
                        m.voice != null -> "Голосовое сообщение"
                        else -> "?????????"
                    }
                    binding.root.setOnClickListener {
                        messageViewModel.smartScrollToPosition(p)
                    }
                }
            } else {
                val m = getItem(chk)?.first
                uiScopeMain.launch {
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
                        messageViewModel.smartScrollToPosition(chk)
                    }
                }
            }
        }
    }

    // ViewHolder для текстовых сообщений получателя
    inner class MessagesViewHolderReceiver(private val binding: ItemMessageReceiverBinding) : RecyclerView.ViewHolder(binding.root) {
        fun clearAnswerLayout() {
            with(binding) {
                answerLayout.root.visibility = View.GONE
                answerLayout.answerMessage.text = ""
                answerLayout.answerUsername.text = ""
                answerLayout.answerImageView.setImageDrawable(null)
            }
        }
        fun bind(message: Message, date: String, position: Int, isAnswer: Boolean) {
            if(isAnswer) {
                handleAnswerLayout(binding, message)
                if(binding.customMessageLayout.width < binding.answerLayout.root.width) {
                    val layoutParams = binding.customMessageLayout.layoutParams as ConstraintLayout.LayoutParams
                    layoutParams.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                    binding.customMessageLayout.layoutParams = layoutParams
                }
            }
            if(message.isForwarded) {
                binding.forwardLayout.root.visibility = View.VISIBLE
                binding.forwardLayout.forwardUsername.text = message.usernameAuthorOriginal
                if(binding.customMessageLayout.width < binding.forwardLayout.root.width) {
                    val layoutParams = binding.customMessageLayout.layoutParams as ConstraintLayout.LayoutParams
                    layoutParams.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                    binding.customMessageLayout.layoutParams = layoutParams
                }
            } else {
                binding.forwardLayout.root.visibility = View.GONE
                binding.forwardLayout.forwardUsername.text = ""
            }
            binding.messageReceiverTextView.text = message.text
            val time = messageViewModel.formatMessageTime(message.timestamp)
            if(date != "") {
                binding.dateTextView.visibility = View.VISIBLE
                binding.dateTextView.text = date
            } else {
                binding.dateTextView.visibility = View.GONE
            }
            binding.timeTextView.text = time
            if(isGroup) {
                val user = members.find { it.id == message.idSender } ?: User(0, "","Unknown", "")
                binding.photoImageView.visibility = View.VISIBLE
                binding.userNameTextView.visibility = View.VISIBLE
                binding.userNameTextView.text = user.username
                val avatar = user.avatar
                if(avatar != null && avatar != "") messageViewModel.avatarSet(avatar, binding.photoImageView, context)
            }
            if(!canLongClick && dialogSettings.canDelete) {
                if(!binding.checkbox.isVisible) binding.checkbox.visibility = View.VISIBLE
                binding.checkbox.isChecked = position in checkedPositions
                binding.checkbox.setOnClickListener {
                    savePosition(message.id, false)
                }
            }
            else { binding.checkbox.visibility = View.GONE }
            if(message.isEdited) binding.editTextView.visibility = View.VISIBLE
            else binding.editTextView.visibility = View.GONE
            binding.root.setOnClickListener {
                if(!canLongClick) {
                    savePosition(message.id, false)
                }
                else
                    actionListener.onMessageClick(message, itemView, false)
            }
            binding.root.setOnLongClickListener {
                if(canLongClick) {
                    onLongClick(message.id, false)
                    actionListener.onMessageLongClick(itemView)
                }
                true
            }
        }
    }

    // ViewHolder для текстовых сообщений отправителя
    inner class MessagesViewHolderSender(private val binding: ItemMessageSenderBinding) : RecyclerView.ViewHolder(binding.root) {
        fun clearAnswerLayout() {
            with(binding) {
                answerLayout.root.visibility = View.GONE
                answerLayout.answerMessage.text = ""
                answerLayout.answerUsername.text = ""
                answerLayout.answerImageView.setImageDrawable(null)
            }
        }
        fun bind(message: Message, date: String, position: Int, isAnswer: Boolean) {
            if(isAnswer) {
                handleAnswerLayout(binding, message)
                if(binding.customMessageLayout.width < binding.answerLayout.root.width) {
                    val layoutParams = binding.customMessageLayout.layoutParams as ConstraintLayout.LayoutParams
                    layoutParams.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                    binding.customMessageLayout.layoutParams = layoutParams
                }
            }
            if(message.isForwarded) {
                binding.forwardLayout.root.visibility = View.VISIBLE
                binding.forwardLayout.forwardUsername.text = message.usernameAuthorOriginal
                // если длина custom message меньше fwd layout, то растягиваем текст custom на ширину fwd
                if(binding.customMessageLayout.width < binding.forwardLayout.root.width) {
                    val layoutParams = binding.customMessageLayout.layoutParams as ConstraintLayout.LayoutParams
                    layoutParams.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                    binding.customMessageLayout.layoutParams = layoutParams
                }
            } else {
                binding.forwardLayout.root.visibility = View.GONE
                binding.forwardLayout.forwardUsername.text = ""
            }
            binding.messageSenderTextView.text = message.text
            if(message.isUnsent == true) {
                binding.timeTextView.text = "----"
                binding.dateTextView.visibility = View.GONE
                binding.icCheck.visibility = View.INVISIBLE
                binding.icCheck2.visibility = View.INVISIBLE
                binding.editTextView.visibility = View.GONE
                binding.icError.visibility = View.VISIBLE
                binding.root.setOnClickListener {
                    actionListener.onUnsentMessageClick(message, itemView)
                }
            } else {
                binding.icError.visibility = View.GONE
                val time = messageViewModel.formatMessageTime(message.timestamp)
                if(date != "") {
                    binding.dateTextView.visibility = View.VISIBLE
                    binding.dateTextView.text = date
                } else {
                    binding.dateTextView.visibility = View.GONE
                }
                binding.timeTextView.text = time
                if(!canLongClick) {
                    if(!binding.checkbox.isVisible) binding.checkbox.visibility = View.VISIBLE
                    binding.checkbox.isChecked = position in checkedPositions
                    binding.checkbox.setOnClickListener {
                        savePosition(message.id, true)
                    }
                }
                else { binding.checkbox.visibility = View.GONE }
                if (message.isRead) {
                    binding.icCheck.visibility = View.INVISIBLE
                    binding.icCheck2.visibility = View.VISIBLE
                } else {
                    binding.icCheck.visibility = View.VISIBLE
                    binding.icCheck2.visibility = View.INVISIBLE
                }
                if(message.isEdited) binding.editTextView.visibility = View.VISIBLE
                else binding.editTextView.visibility = View.GONE
                binding.root.setOnClickListener {
                    if(!canLongClick) {
                        savePosition(message.id, true)
                    }
                    else
                        actionListener.onMessageClick(message, itemView, true)
                }
                binding.root.setOnLongClickListener {
                    if(canLongClick) {
                        onLongClick(message.id, true)
                        actionListener.onMessageLongClick(itemView)
                    }
                    true
                }
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
                in 2..5 -> 2
                else -> 3
            }

            // Определяем количество строк
            val rows = when (itemCount) {
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
        fun clearAnswerLayout() {
            with(binding) {
                answerLayout.root.visibility = View.GONE
                answerLayout.answerMessage.text = ""
                answerLayout.answerUsername.text = ""
                answerLayout.answerImageView.setImageDrawable(null)
            }
        }
        fun bind(message: Message, date: String, position: Int, isInLast30: Boolean, isAnswer: Boolean) {
            if(isAnswer) handleAnswerLayout(binding, message)
            if(message.isForwarded) {
                binding.forwardLayout.root.visibility = View.VISIBLE
                binding.forwardLayout.forwardUsername.text = message.usernameAuthorOriginal
            } else {
                binding.forwardLayout.root.visibility = View.GONE
                binding.forwardLayout.forwardUsername.text = ""
            }
            binding.playButton.visibility = View.VISIBLE
            uiScopeMain.launch {
                val filePathTemp = async {
                    val voice = message.voice ?: "nonWork"
                    if (messageViewModel.fManagerIsExist(voice)) {
                        return@async Pair(messageViewModel.fManagerGetFilePath(voice), true)
                    } else {
                        try {
                            return@async Pair(messageViewModel.downloadFile(context, "audio", message.voice!!), false)
                        } catch (e: Exception) {
                            return@async Pair(null, true)
                        }
                    }
                }
                val (first, second) = filePathTemp.await()
                if (first != null) {
                val file = File(first)
                if (file.exists()) {
                    if (!second && isInLast30) messageViewModel.fManagerSaveFile(message.voice!!, file.readBytes())
                    val mediaPlayer = MediaPlayer().apply {
                        setDataSource(first)
                        prepare()
                    }
                    val duration = mediaPlayer.duration
                    binding.waveformSeekBar.setSampleFrom(file)
                    binding.waveformSeekBar.maxProgress = duration.toFloat()
                    binding.timeVoiceTextView.text = messageViewModel.formatTime(duration.toLong())

                    val updateSeekBarRunnable = object : Runnable {
                        override fun run() {
                            if (isPlaying && mediaPlayer.isPlaying) {
                                val currentPosition = mediaPlayer.currentPosition.toFloat()
                                binding.waveformSeekBar.progress = currentPosition
                                binding.timeVoiceTextView.text = messageViewModel.formatTime(currentPosition.toLong())
                                handler.postDelayed(this, 100)
                            }
                        }
                    }
                    binding.playButton.setOnClickListener {
                        if (!isPlaying) {
                            mediaPlayer.start()
                            binding.playButton.setImageResource(R.drawable.ic_pause)
                            isPlaying = true
                            handler.post(updateSeekBarRunnable) // Запуск обновления SeekBar
                        } else {
                            mediaPlayer.pause()
                            binding.playButton.setImageResource(R.drawable.ic_play)
                            isPlaying = false
                            handler.removeCallbacks(updateSeekBarRunnable) // Остановка обновления SeekBar
                        }
                    }
                    // Обработка изменения положения SeekBar
                    binding.waveformSeekBar.onProgressChanged = object : SeekBarOnProgressChanged {
                        override fun onProgressChanged(waveformSeekBar: WaveformSeekBar, progress: Float, fromUser: Boolean) {
                            if (fromUser) {
                                mediaPlayer.seekTo(progress.toInt())
                                binding.timeVoiceTextView.text = messageViewModel.formatTime(progress.toLong())
                            }
                        }
                    }
                    mediaPlayer.setOnCompletionListener {
                        binding.playButton.setImageResource(R.drawable.ic_play)
                        binding.waveformSeekBar.progress = 0f
                        binding.timeVoiceTextView.text = messageViewModel.formatTime(duration.toLong())
                        isPlaying = false
                        handler.removeCallbacks(updateSeekBarRunnable)
                    }
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
            val time = messageViewModel.formatMessageTime(message.timestamp)
            if(date != "") {
                binding.dateTextView.visibility = View.VISIBLE
                binding.dateTextView.text = date
            } else {
                binding.dateTextView.visibility = View.GONE
            }
            binding.timeTextView.text = time
            if(isGroup) {
                val user = members.find { it.id == message.idSender } ?: User(0, "","Unknown", "")
                binding.photoImageView.visibility = View.VISIBLE
                binding.userNameTextView.visibility = View.VISIBLE
                binding.userNameTextView.text = user.username
                val avatar = user.avatar
                if(avatar != null && avatar != "") messageViewModel.avatarSet(avatar, binding.photoImageView, context)
            }
            if(!canLongClick) {
                if(!binding.checkbox.isVisible) binding.checkbox.visibility = View.VISIBLE
                binding.checkbox.isChecked = position in checkedPositions
                binding.checkbox.setOnClickListener {
                    savePosition(message.id, false)
                }
            }
            else { binding.checkbox.visibility = View.GONE }
            if(message.isEdited) binding.editTextView.visibility = View.VISIBLE
            else binding.editTextView.visibility = View.GONE
            binding.root.setOnClickListener {
                if(!canLongClick) {
                    savePosition(message.id, false)
                }
                else
                    actionListener.onMessageClick(message, itemView, false)
            }
            binding.root.setOnLongClickListener {
                if(canLongClick) {
                    onLongClick(message.id, false)
                    actionListener.onMessageLongClick(itemView)
                }
                true
            }
        }
    }

    inner class MessagesViewHolderVoiceSender(private val binding: ItemVoiceSenderBinding) : RecyclerView.ViewHolder(binding.root) {
        private var isPlaying: Boolean = false
        private val handler = Handler(Looper.getMainLooper())
        fun clearAnswerLayout() {
            with(binding) {
                answerLayout.root.visibility = View.GONE
                answerLayout.answerMessage.text = ""
                answerLayout.answerUsername.text = ""
                answerLayout.answerImageView.setImageDrawable(null)
            }
        }
        fun bind(message: Message, date: String, position: Int, isInLast30: Boolean, isAnswer: Boolean) {
            if(isAnswer) handleAnswerLayout(binding, message)
            if(message.isForwarded) {
                binding.forwardLayout.root.visibility = View.VISIBLE
                binding.forwardLayout.forwardUsername.text = message.usernameAuthorOriginal
            } else {
                binding.forwardLayout.root.visibility = View.GONE
                binding.forwardLayout.forwardUsername.text = ""
            }
            binding.playButton.visibility = View.VISIBLE
            uiScopeMain.launch {
                val filePathTemp = async {
                    if(message.isUnsent == true) {
                        return@async Pair(message.localFilePaths?.first(), true)
                    } else {
                        val voice = message.voice ?: "nonWork"
                        if (messageViewModel.fManagerIsExist(voice)) {
                            return@async Pair(messageViewModel.fManagerGetFilePath(voice), true)
                        } else {
                            try {
                                return@async Pair(messageViewModel.downloadFile(context, "audio", message.voice!!), false)
                            } catch (e: Exception) {
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
                    val mediaPlayer = MediaPlayer().apply {
                        setDataSource(first)
                        prepare()
                    }
                    val duration = mediaPlayer.duration
                    binding.waveformSeekBar.setSampleFrom(file)
                    binding.waveformSeekBar.maxProgress = duration.toFloat()
                    binding.timeVoiceTextView.text = messageViewModel.formatTime(duration.toLong())

                    val updateSeekBarRunnable = object : Runnable {
                        override fun run() {
                            if (isPlaying && mediaPlayer.isPlaying) {
                                val currentPosition = mediaPlayer.currentPosition.toFloat()
                                binding.waveformSeekBar.progress = currentPosition
                                binding.timeVoiceTextView.text = messageViewModel.formatTime(currentPosition.toLong())
                                handler.postDelayed(this, 100)
                            }
                        }
                    }
                    binding.playButton.setOnClickListener {
                        if (!isPlaying) {
                            mediaPlayer.start()
                            binding.playButton.setImageResource(R.drawable.ic_pause)
                            isPlaying = true
                            handler.post(updateSeekBarRunnable) // Запуск обновления SeekBar
                        } else {
                            mediaPlayer.pause()
                            binding.playButton.setImageResource(R.drawable.ic_play)
                            isPlaying = false
                            handler.removeCallbacks(updateSeekBarRunnable) // Остановка обновления SeekBar
                        }
                    }
                    // Обработка изменения положения SeekBar
                    binding.waveformSeekBar.onProgressChanged = object : SeekBarOnProgressChanged {
                        override fun onProgressChanged(waveformSeekBar: WaveformSeekBar, progress: Float, fromUser: Boolean) {
                            if (fromUser) {
                                mediaPlayer.seekTo(progress.toInt())
                                binding.timeVoiceTextView.text = messageViewModel.formatTime(progress.toLong())
                            }
                        }
                    }
                    mediaPlayer.setOnCompletionListener {
                        binding.playButton.setImageResource(R.drawable.ic_play)
                        binding.waveformSeekBar.progress = 0f
                        binding.timeVoiceTextView.text = messageViewModel.formatTime(duration.toLong())
                        isPlaying = false
                        handler.removeCallbacks(updateSeekBarRunnable)
                    }
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
            if(message.isUnsent == true) {
                binding.timeTextView.text = "----"
                binding.dateTextView.visibility = View.GONE
                binding.icCheck.visibility = View.INVISIBLE
                binding.icCheck2.visibility = View.INVISIBLE
                binding.editTextView.visibility = View.GONE
                binding.icError.visibility = View.VISIBLE
                binding.root.setOnClickListener {
                    actionListener.onUnsentMessageClick(message, itemView)
                }
            } else {
                binding.icError.visibility = View.GONE
                val time = messageViewModel.formatMessageTime(message.timestamp)
                if(date != "") {
                    binding.dateTextView.visibility = View.VISIBLE
                    binding.dateTextView.text = date
                } else {
                    binding.dateTextView.visibility = View.GONE
                }
                binding.timeTextView.text = time
                if(!canLongClick) {
                    if(!binding.checkbox.isVisible) binding.checkbox.visibility = View.VISIBLE
                    binding.checkbox.isChecked = position in checkedPositions
                    binding.checkbox.setOnClickListener {
                        savePosition(message.id, true)
                    }
                }
                else { binding.checkbox.visibility = View.GONE }
                if (message.isRead) {
                    binding.icCheck.visibility = View.INVISIBLE
                    binding.icCheck2.visibility = View.VISIBLE
                } else {
                    binding.icCheck.visibility = View.VISIBLE
                    binding.icCheck2.visibility = View.INVISIBLE
                }
                if(message.isEdited) binding.editTextView.visibility = View.VISIBLE
                else binding.editTextView.visibility = View.GONE
                binding.root.setOnClickListener {
                    if(!canLongClick) {
                        savePosition(message.id, true)
                    }
                    else
                        actionListener.onMessageClick(message, itemView, true)
                }
                binding.root.setOnLongClickListener {
                    if(canLongClick) {
                        onLongClick(message.id, true)
                        actionListener.onMessageLongClick(itemView)
                    }
                    true
                }
            }
        }
    }

    inner class MessagesViewHolderFileReceiver(private val binding: ItemFileReceiverBinding) : RecyclerView.ViewHolder(binding.root) {
        fun clearAnswerLayout() {
            with(binding) {
                answerLayout.root.visibility = View.GONE
                answerLayout.answerMessage.text = ""
                answerLayout.answerUsername.text = ""
                answerLayout.answerImageView.setImageDrawable(null)
            }
        }
        fun bind(message: Message, date: String, position: Int, isInLast30: Boolean, isAnswer: Boolean) {
            if(isAnswer) handleAnswerLayout(binding, message)
            if(message.isForwarded) {
                binding.forwardLayout.root.visibility = View.VISIBLE
                binding.forwardLayout.forwardUsername.text = message.usernameAuthorOriginal
            } else {
                binding.forwardLayout.root.visibility = View.GONE
                binding.forwardLayout.forwardUsername.text = ""
            }
            uiScopeMain.launch {
                val filePathTemp = async {
                    if (messageViewModel.fManagerIsExist(message.file!!)) {
                        return@async Pair(messageViewModel.fManagerGetFilePath(message.file!!), true)
                    } else {
                        try {
                            return@async Pair(messageViewModel.downloadFile(context, "files", message.file!!), false)
                        } catch (e: Exception) {
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
                    binding.fileButton.setOnClickListener {
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
            val time = messageViewModel.formatMessageTime(message.timestamp)
            if(date != "") {
                binding.dateTextView.visibility = View.VISIBLE
                binding.dateTextView.text = date
            } else {
                binding.dateTextView.visibility = View.GONE
            }
            binding.timeTextView.text = time
            if(isGroup) {
                val user = members.find { it.id == message.idSender } ?: User(0, "","Unknown", "")
                binding.photoImageView.visibility = View.VISIBLE
                binding.userNameTextView.visibility = View.VISIBLE
                binding.userNameTextView.text = user.username
                val avatar = user.avatar
                if(avatar != null && avatar != "") messageViewModel.avatarSet(avatar, binding.photoImageView, context)
            }
            if(!canLongClick) {
                if(!binding.checkbox.isVisible) binding.checkbox.visibility = View.VISIBLE
                binding.checkbox.isChecked = position in checkedPositions
                binding.checkbox.setOnClickListener {
                    savePosition(message.id, false)
                }
            }
            else { binding.checkbox.visibility = View.GONE }
            if(message.isEdited) binding.editTextView.visibility = View.VISIBLE
            else binding.editTextView.visibility = View.GONE
            binding.root.setOnClickListener {
                if(!canLongClick) {
                    savePosition(message.id, false)
                }
                else
                    actionListener.onMessageClick(message, itemView, false)
            }
            binding.root.setOnLongClickListener {
                if(canLongClick) {
                    onLongClick(message.id, false)
                    actionListener.onMessageLongClick(itemView)
                }
                true
            }
        }
    }

    inner class MessagesViewHolderFileSender(private val binding: ItemFileSenderBinding) : RecyclerView.ViewHolder(binding.root) {
        fun clearAnswerLayout() {
            with(binding) {
                answerLayout.root.visibility = View.GONE
                answerLayout.answerMessage.text = ""
                answerLayout.answerUsername.text = ""
                answerLayout.answerImageView.setImageDrawable(null)
            }
        }
        fun bind(message: Message, date: String, position: Int, isInLast30: Boolean, isAnswer: Boolean) {
            if(isAnswer) handleAnswerLayout(binding, message)
            if(message.isForwarded) {
                binding.forwardLayout.root.visibility = View.VISIBLE
                binding.forwardLayout.forwardUsername.text = message.usernameAuthorOriginal
            } else {
                binding.forwardLayout.root.visibility = View.GONE
                binding.forwardLayout.forwardUsername.text = ""
            }
            uiScopeMain.launch {
                val filePathTemp = async {
                    if(message.isUnsent == true) {
                        return@async Pair(message.localFilePaths?.first(), true)
                    } else {
                        if (messageViewModel.fManagerIsExist(message.file!!)) {
                            return@async Pair(messageViewModel.fManagerGetFilePath(message.file!!), true)
                        } else {
                            try {
                                return@async Pair(messageViewModel.downloadFile(context, "files", message.file!!), false)
                            } catch (e: Exception) {
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
                        binding.fileButton.setOnClickListener {
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
            if(message.isUnsent == true) {
                binding.timeTextView.text = "----"
                binding.dateTextView.visibility = View.GONE
                binding.icCheck.visibility = View.INVISIBLE
                binding.icCheck2.visibility = View.INVISIBLE
                binding.editTextView.visibility = View.GONE
                binding.icError.visibility = View.VISIBLE
                binding.root.setOnClickListener {
                    actionListener.onUnsentMessageClick(message, itemView)
                }
            } else {
                binding.icError.visibility = View.GONE
                val time = messageViewModel.formatMessageTime(message.timestamp)
                if(date != "") {
                    binding.dateTextView.visibility = View.VISIBLE
                    binding.dateTextView.text = date
                } else {
                    binding.dateTextView.visibility = View.GONE
                }
                binding.timeTextView.text = time
                if(!canLongClick) {
                    if(!binding.checkbox.isVisible) binding.checkbox.visibility = View.VISIBLE
                    binding.checkbox.isChecked = position in checkedPositions
                    binding.checkbox.setOnClickListener {
                        savePosition(message.id, true)
                    }
                }
                else { binding.checkbox.visibility = View.GONE }
                if (message.isRead) {
                    binding.icCheck.visibility = View.INVISIBLE
                    binding.icCheck2.visibility = View.VISIBLE
                } else {
                    binding.icCheck.visibility = View.VISIBLE
                    binding.icCheck2.visibility = View.INVISIBLE
                }
                if(message.isEdited) binding.editTextView.visibility = View.VISIBLE
                else binding.editTextView.visibility = View.GONE
                binding.root.setOnClickListener {
                    if(!canLongClick) {
                        savePosition(message.id, true)
                    }
                    else
                        actionListener.onMessageClick(message, itemView, true)
                }
                binding.root.setOnLongClickListener {
                    if(canLongClick) {
                        onLongClick(message.id, true)
                        actionListener.onMessageLongClick(itemView)
                    }
                    true
                }
            }
        }
    }
    inner class MessagesViewHolderTextImageReceiver(private val binding: ItemTextImageReceiverBinding) : RecyclerView.ViewHolder(binding.root) {
        private var filePath: String = ""
        fun clearAnswerLayout() {
            with(binding) {
                answerLayout.root.visibility = View.GONE
                answerLayout.answerMessage.text = ""
                answerLayout.answerUsername.text = ""
                answerLayout.answerImageView.setImageDrawable(null)
            }
        }
        fun bind(message: Message, date: String, position: Int, flagText: Boolean, isInLast30: Boolean, isAnswer: Boolean) {
            if(isAnswer) handleAnswerLayout(binding, message)
            if(message.isForwarded) {
                binding.forwardLayout.root.visibility = View.VISIBLE
                binding.forwardLayout.forwardUsername.text = message.usernameAuthorOriginal
            } else {
                binding.forwardLayout.root.visibility = View.GONE
                binding.forwardLayout.forwardUsername.text = ""
            }
            val timeTextView = if(flagText) binding.timeTextView else binding.timeTextViewImage
            val editTextView = if(flagText) binding.editTextView else binding.editTextViewImage
            if(flagText) {
                binding.customMessageLayout.visibility = View.VISIBLE
                binding.timeLayout.visibility = View.GONE
                binding.messageReceiverTextView.text = message.text
            } else {
                binding.customMessageLayout.visibility = View.GONE
                binding.timeLayout.visibility = View.VISIBLE
            }

            uiScopeMain.launch {
                binding.progressBar.visibility = View.VISIBLE
                val filePathTemp = async {
                    if (messageViewModel.fManagerIsExist(message.images?.first() ?: "nonWork")) {
                        return@async Pair(messageViewModel.fManagerGetFilePath(message.images!!.first()), true)
                    } else {
                        try {
                            return@async Pair(messageViewModel.downloadFile(context, "photos", message.images!!.first()), false)
                        } catch (e: Exception) {
                            return@async Pair(null, true)
                        }
                    }
                }
                val (first, second) = filePathTemp.await()
                if(first != null) {
                val file = File(first)
                    filePath = first
                if (file.exists()) {
                    if (!second && isInLast30) messageViewModel.fManagerSaveFile(message.images!!.first(), file.readBytes())
                    val uri = Uri.fromFile(file)
                    val localMedia = messageViewModel.fileToLocalMedia(file)
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
                    binding.receiverImageView.setOnClickListener {
                        actionListener.onImagesClick(arrayListOf(localMedia), 0)
                    }
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
            val time = messageViewModel.formatMessageTime(message.timestamp)
            if(date != "") {
                binding.dateTextView.visibility = View.VISIBLE
                binding.dateTextView.text = date
            } else {
                binding.dateTextView.visibility = View.GONE
            }
            timeTextView.text = time
            if(isGroup) {
                val user = members.find { it.id == message.idSender } ?: User(0, "","Unknown", "")
                binding.photoImageView.visibility = View.VISIBLE
                binding.userNameTextView.visibility = View.VISIBLE
                binding.userNameTextView.text = user.username
                val avatar = user.avatar
                if(avatar != null && avatar != "") messageViewModel.avatarSet(avatar, binding.photoImageView, context)
            }
            if(!canLongClick && dialogSettings.canDelete) {
                if(!binding.checkbox.isVisible) binding.checkbox.visibility = View.VISIBLE
                binding.checkbox.isChecked = position in checkedPositions
                binding.checkbox.setOnClickListener {
                    savePosition(message.id, false)
                }
            }
            else { binding.checkbox.visibility = View.GONE }
            if(message.isEdited) editTextView.visibility = View.VISIBLE
            else editTextView.visibility = View.GONE
            binding.root.setOnClickListener {
                if(!canLongClick) {
                    savePosition(message.id, false)
                }
                else
                    actionListener.onMessageClickImage(message, itemView, arrayListOf(messageViewModel.fileToLocalMedia(File(filePath))), false)
            }
            binding.root.setOnLongClickListener {
                if(canLongClick) {
                    onLongClick(message.id, false)
                    actionListener.onMessageLongClick(itemView)
                }
                true
            }
        }
    }

    inner class MessagesViewHolderTextImageSender(private val binding: ItemTextImageSenderBinding) : RecyclerView.ViewHolder(binding.root) {
        private var filePath: String = ""
        fun clearAnswerLayout() {
            with(binding) {
                answerLayout.root.visibility = View.GONE
                answerLayout.answerMessage.text = ""
                answerLayout.answerUsername.text = ""
                answerLayout.answerImageView.setImageDrawable(null)
            }
        }
        fun bind(message: Message, date: String, position: Int, flagText: Boolean, isInLast30: Boolean, isAnswer: Boolean) {
            if(isAnswer) handleAnswerLayout(binding, message)
            if(message.isForwarded) {
                binding.forwardLayout.root.visibility = View.VISIBLE
                binding.forwardLayout.forwardUsername.text = message.usernameAuthorOriginal
            } else {
                binding.forwardLayout.root.visibility = View.GONE
                binding.forwardLayout.forwardUsername.text = ""
            }
            val timeTextView = if(flagText) binding.timeTextView else binding.timeTextViewImage
            val editTextView = if(flagText) binding.editTextView else binding.editTextViewImage
            val icCheck = if(flagText) binding.icCheck else binding.icCheckImage
            val icCheck2 = if(flagText) binding.icCheck2 else binding.icCheck2Image
            val icError = if(flagText) binding.icError else binding.icErrorImage
            if(flagText) {
                binding.customMessageLayout.visibility = View.VISIBLE
                binding.timeLayout.visibility = View.GONE
                binding.messageSenderTextView.text = message.text
            } else {
                binding.customMessageLayout.visibility = View.GONE
                binding.timeLayout.visibility = View.VISIBLE
            }
            uiScopeMain.launch {
                binding.progressBar.visibility = View.VISIBLE
                val filePathTemp = async {
                    if(message.isUnsent == true) {
                        return@async Pair(message.localFilePaths?.first(), true)
                    } else {
                        if (messageViewModel.fManagerIsExist(message.images?.first() ?: "nonWork")) {
                            return@async Pair(messageViewModel.fManagerGetFilePath(message.images!!.first()), true)
                        } else {
                            try {
                                return@async Pair(messageViewModel.downloadFile(context, "photos", message.images!!.first()), false)
                            } catch (e: Exception) {
                                return@async Pair(null, true)
                            }
                        }
                    }
                }
                val (first, second) = filePathTemp.await()
                if (first != null) {
                val file = File(first)
                filePath = first
                if (file.exists()) {
                    if (!second && isInLast30) messageViewModel.fManagerSaveFile(message.images!!.first(), file.readBytes())
                    val uri = Uri.fromFile(file)
                    val localMedia = messageViewModel.fileToLocalMedia(file)
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
                    binding.senderImageView.setOnClickListener {
                        actionListener.onImagesClick(arrayListOf(localMedia), 0)
                    }
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
            if(message.isUnsent == true) {
                binding.dateTextView.visibility = View.GONE
                timeTextView.text = "----"
                icCheck.visibility = View.INVISIBLE
                icCheck2.visibility = View.INVISIBLE
                editTextView.visibility = View.GONE
                icError.visibility = View.VISIBLE
                binding.root.setOnClickListener {
                    actionListener.onUnsentMessageClick(message, itemView)
                }
            } else {
                icError.visibility = View.GONE
                val time = messageViewModel.formatMessageTime(message.timestamp)
                if(date != "") {
                    binding.dateTextView.visibility = View.VISIBLE
                    binding.dateTextView.text = date
                } else {
                    binding.dateTextView.visibility = View.GONE
                }
                timeTextView.text = time
                if(!canLongClick) {
                    if(!binding.checkbox.isVisible) binding.checkbox.visibility = View.VISIBLE
                    binding.checkbox.isChecked = position in checkedPositions
                    binding.checkbox.setOnClickListener {
                        savePosition(message.id, true)
                    }
                }
                else { binding.checkbox.visibility = View.GONE }
                if (message.isRead) {
                    icCheck.visibility = View.INVISIBLE
                    icCheck2.visibility = View.VISIBLE
                } else {
                    icCheck.visibility = View.VISIBLE
                    icCheck2.visibility = View.INVISIBLE
                }
                if(message.isEdited) editTextView.visibility = View.VISIBLE
                else editTextView.visibility = View.GONE
                binding.root.setOnClickListener {
                    if(!canLongClick) {
                        savePosition(message.id, true)
                    }
                    else
                        actionListener.onMessageClickImage(message, itemView, arrayListOf(messageViewModel.fileToLocalMedia(File(filePath))), true)
                }
                binding.root.setOnLongClickListener {
                    if(canLongClick) {
                        onLongClick(message.id, true)
                        actionListener.onMessageLongClick(itemView)
                    }
                    true
                }
            }
        }
    }

    inner class MessagesViewHolderTextImagesReceiver(private val binding: ItemTextImagesReceiverBinding) : RecyclerView.ViewHolder(binding.root) {

        private lateinit var mes: Message

        private val adapter = ImagesAdapter(context, object: ImagesActionListener {
            override fun onImageClicked(images: ArrayList<LocalMedia>, position: Int) {
                actionListener.onImagesClick(images, position)
            }

            override fun onLongImageClicked(position: Int) {
                if(canLongClick) {
                    onLongClick(mes.id, false)
                    actionListener.onMessageLongClick(itemView)
                }
            }
        })

        private var filePathsForClick: List<String> = listOf()
        init {
            binding.recyclerview.layoutManager = CustomLayoutManager()
            binding.recyclerview.addItemDecoration(AdaptiveGridSpacingItemDecoration(2, true))
            binding.recyclerview.adapter = adapter
        }
        fun clearAnswerLayout() {
            with(binding) {
                answerLayout.root.visibility = View.GONE
                answerLayout.answerMessage.text = ""
                answerLayout.answerUsername.text = ""
                answerLayout.answerImageView.setImageDrawable(null)
            }
        }
        fun bind(message: Message, date: String, position: Int, flagText: Boolean, isInLast30: Boolean, isAnswer: Boolean) {
            if(isAnswer) handleAnswerLayout(binding, message)
            if(message.isForwarded) {
                binding.forwardLayout.root.visibility = View.VISIBLE
                binding.forwardLayout.forwardUsername.text = message.usernameAuthorOriginal
            } else {
                binding.forwardLayout.root.visibility = View.GONE
                binding.forwardLayout.forwardUsername.text = ""
            }
            filePathsForClick = emptyList()
            mes = message
            val timeTextView = if(flagText) binding.timeTextView else binding.timeTextViewImage
            val editTextView = if(flagText) binding.editTextView else binding.editTextViewImage
            if(flagText) {
                binding.customMessageLayout.visibility = View.VISIBLE
                binding.timeLayout.visibility = View.GONE
                binding.messageReceiverTextView.text = message.text
            } else {
                binding.customMessageLayout.visibility = View.GONE
                binding.timeLayout.visibility = View.VISIBLE
            }
            binding.progressBar.visibility = View.VISIBLE
            val semaphore = Semaphore(4) // todo возможно 4 следует заменить на другое количество
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
                                    } catch (e: Exception) {
                                        Pair(null, true)
                                    }
                                }
                            }
                        }
                        val (first, second) = filePath.await()
                        if (first != null) {
                        val file = File(first)
                        filePathsForClick += first
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
                adapter.images = localMedias.await()
                binding.progressBar.visibility = View.GONE
            }
            val time = messageViewModel.formatMessageTime(message.timestamp)
            if(date != "") {
                binding.dateTextView.visibility = View.VISIBLE
                binding.dateTextView.text = date
            } else {
                binding.dateTextView.visibility = View.GONE
            }
            timeTextView.text = time
            if(isGroup) {
                val user = members.find { it.id == message.idSender } ?: User(0, "","Unknown", "")
                binding.photoImageView.visibility = View.VISIBLE
                binding.userNameTextView.visibility = View.VISIBLE
                binding.userNameTextView.text = user.username
                val avatar = user.avatar
                if(avatar != null && avatar != "") messageViewModel.avatarSet(avatar, binding.photoImageView, context)
            }
            if(!canLongClick) {
                if(!binding.checkbox.isVisible) binding.checkbox.visibility = View.VISIBLE
                binding.checkbox.isChecked = position in checkedPositions
                binding.checkbox.setOnClickListener {
                    savePosition(message.id, false)
                }
            }
            else { binding.checkbox.visibility = View.GONE }
            if(message.isEdited) editTextView.visibility = View.VISIBLE
            else editTextView.visibility = View.GONE
            binding.root.setOnClickListener {
                if(!canLongClick) {
                    savePosition(message.id, false)
                }
                else {
                    val medias: ArrayList<LocalMedia> = filePathsForClick.map { messageViewModel.fileToLocalMedia(File(it)) } as ArrayList<LocalMedia>
                    actionListener.onMessageClickImage(message, itemView, medias, false)
                }
            }
            binding.root.setOnLongClickListener {
                if(canLongClick) {
                    onLongClick(message.id, false)
                    actionListener.onMessageLongClick(itemView)
                }
                true
            }
        }
    }

    inner class MessagesViewHolderTextImagesSender(private val binding: ItemTextImagesSenderBinding) : RecyclerView.ViewHolder(binding.root) {

        private lateinit var mes: Message

        private val adapter = ImagesAdapter(context, object: ImagesActionListener {
            override fun onImageClicked(images: ArrayList<LocalMedia>, position: Int) {
                actionListener.onImagesClick(images, position)
            }

            override fun onLongImageClicked(position: Int) {
                if(canLongClick) {
                    onLongClick(mes.id, true)
                    actionListener.onMessageLongClick(itemView)
                }
            }
        })

        private var filePathsForClick: List<String> = listOf()
        init {
            binding.recyclerview.layoutManager = CustomLayoutManager()
            binding.recyclerview.addItemDecoration(AdaptiveGridSpacingItemDecoration(2, true))
            binding.recyclerview.adapter = adapter
        }
        fun clearAnswerLayout() {
            with(binding) {
                answerLayout.root.visibility = View.GONE
                answerLayout.answerMessage.text = ""
                answerLayout.answerUsername.text = ""
                answerLayout.answerImageView.setImageDrawable(null)
            }
        }
        fun bind(message: Message, date: String, position: Int, flagText: Boolean, isInLast30: Boolean, isAnswer: Boolean) {
            if(isAnswer) handleAnswerLayout(binding, message)
            if(message.isForwarded) {
                binding.forwardLayout.root.visibility = View.VISIBLE
                binding.forwardLayout.forwardUsername.text = message.usernameAuthorOriginal
            } else {
                binding.forwardLayout.root.visibility = View.GONE
                binding.forwardLayout.forwardUsername.text = ""
            }
            filePathsForClick = emptyList()
            mes = message
            val timeTextView = if(flagText) binding.timeTextView else binding.timeTextViewImage
            val editTextView = if(flagText) binding.editTextView else binding.editTextViewImage
            val icCheck = if(flagText) binding.icCheck else binding.icCheckImage
            val icCheck2 = if(flagText) binding.icCheck2 else binding.icCheck2Image
            val icError = if(flagText) binding.icError else binding.icErrorImage
            if(flagText) {
                binding.customMessageLayout.visibility = View.VISIBLE
                binding.timeLayout.visibility = View.GONE
                binding.messageSenderTextView.text = message.text
            } else {
                binding.customMessageLayout.visibility = View.GONE
                binding.timeLayout.visibility = View.VISIBLE
            }
            binding.errorImageView.visibility = View.GONE
            val semaphore = Semaphore(4) // todo возможно 4 следует заменить на другое количество
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
                                        } catch (e: Exception) {
                                            Pair(null, true)
                                        }
                                    }
                                }
                            }
                        }
                        val (first, second) = filePath.await()
                        if (first != null) {
                            val file = File(first)
                            filePathsForClick += first
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
                adapter.images = localMedias.await()
                binding.progressBar.visibility = View.GONE
            }
            if(message.isUnsent == true) {
                binding.dateTextView.visibility = View.GONE
                timeTextView.text = "----"
                icCheck.visibility = View.INVISIBLE
                icCheck2.visibility = View.INVISIBLE
                editTextView.visibility = View.GONE
                icError.visibility = View.VISIBLE
                binding.root.setOnClickListener {
                    actionListener.onUnsentMessageClick(message, itemView)
                }
            } else {
                icError.visibility = View.GONE
                val time = messageViewModel.formatMessageTime(message.timestamp)
                if(date != "") {
                    binding.dateTextView.visibility = View.VISIBLE
                    binding.dateTextView.text = date
                } else {
                    binding.dateTextView.visibility = View.GONE
                }
                timeTextView.text = time
                if(!canLongClick) {
                    if(!binding.checkbox.isVisible) binding.checkbox.visibility = View.VISIBLE
                    binding.checkbox.isChecked = position in checkedPositions
                    binding.checkbox.setOnClickListener {
                        savePosition(message.id, true)
                    }
                }
                else { binding.checkbox.visibility = View.GONE }
                if (message.isRead) {
                    icCheck.visibility = View.INVISIBLE
                    icCheck2.visibility = View.VISIBLE
                } else {
                    icCheck.visibility = View.VISIBLE
                    icCheck2.visibility = View.INVISIBLE
                }
                if(message.isEdited) editTextView.visibility = View.VISIBLE
                else editTextView.visibility = View.GONE
                binding.root.setOnClickListener {
                    if(!canLongClick) {
                        savePosition(message.id, true)
                    }
                    else {
                        val medias: ArrayList<LocalMedia> = filePathsForClick.map { messageViewModel.fileToLocalMedia(File(it)) } as ArrayList<LocalMedia>
                        actionListener.onMessageClickImage(message, itemView, medias, true)
                    }
                }
                binding.root.setOnLongClickListener {
                    if(canLongClick) {
                        onLongClick(message.id,true)
                        actionListener.onMessageLongClick(itemView)
                    }
                    true
                }
            }
        }
    }
}
