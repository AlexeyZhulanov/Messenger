package com.example.messenger

import android.annotation.SuppressLint
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.messenger.databinding.ItemFileReceiverBinding
import com.example.messenger.databinding.ItemFileSenderBinding
import com.example.messenger.databinding.ItemImageReceiverBinding
import com.example.messenger.databinding.ItemImageSenderBinding
import com.example.messenger.databinding.ItemImagesReceiverBinding
import com.example.messenger.databinding.ItemImagesSenderBinding
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

interface MessageActionListener {
    fun onMessageClick(message: Message, itemView: View)
    fun onMessageLongClick(message: Message, itemView: View)
}

class MessageAdapter(
    private val actionListener: MessageActionListener,
    private val otherUserId: Int
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    var messages: Map<Message, String> = emptyMap()
        @SuppressLint("NotifyDataSetChanged")
        set(value) {
            val dates = mutableSetOf<String>()
            val newMessages = mutableMapOf<Message, String>()
            for((message, _) in value) {
                val date = formatMessageDate(message.timestamp)
                if(date !in dates) {
                    dates.add(date)
                    newMessages[message] = date
                } else {
                    newMessages[message] = ""
                }
            }
            field = newMessages
            Log.d("testAdapterSet", "$field")
            notifyDataSetChanged()
        }

    var canLongClick: Boolean = true
    private var checkedPositions: MutableSet<Int> = mutableSetOf()
    lateinit var dialogSettings: ConversationSettings

    fun getDeleteList(): List<Int> {
        val list = mutableListOf<Int>()
        checkedPositions.forEach { list.add(messages.keys.elementAt(it).id) }
        return list
    }

    @SuppressLint("NotifyDataSetChanged")
    fun clearPositions() {
        canLongClick = true
        checkedPositions.clear()
    }

    private fun savePosition(message: Message) {
        val position = messages.keys.indexOf(message)
        if (position in checkedPositions) {
            checkedPositions.remove(position)
        } else {
            checkedPositions.add(position)
        }
        notifyItemChanged(position)
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun onLongClick(message: Message) {
        if(canLongClick) {
            savePosition(message)
            canLongClick = false
            notifyDataSetChanged()
        }
    }

    companion object {
        private const val TYPE_TEXT_RECEIVER = 0
        private const val TYPE_TEXT_SENDER = 1
        private const val TYPE_IMAGE_RECEIVER = 2
        private const val TYPE_IMAGE_SENDER = 3
        private const val TYPE_IMAGES_RECEIVER = 4
        private const val TYPE_IMAGES_SENDER = 5
        private const val TYPE_VOICE_RECEIVER = 6
        private const val TYPE_VOICE_SENDER = 7
        private const val TYPE_FILE_RECEIVER = 8
        private const val TYPE_FILE_SENDER = 9
        private const val TYPE_TEXT_IMAGE_RECEIVER = 10
        private const val TYPE_TEXT_IMAGE_SENDER = 11
        private const val TYPE_TEXT_IMAGES_RECEIVER = 12
        private const val TYPE_TEXT_IMAGES_SENDER = 13
    }

    override fun getItemViewType(position: Int): Int {
        val message = messages.keys.elementAt(position)
        if(message.idSender == otherUserId) {
            return when {
                message.text?.isNotEmpty() == true -> {
                    when {
                        message.images.isNullOrEmpty() -> TYPE_TEXT_RECEIVER
                        message.images?.size == 1 -> TYPE_TEXT_IMAGE_RECEIVER
                        else -> TYPE_TEXT_IMAGES_RECEIVER
                    }
                }
                else -> {
                    when {
                        message.images?.size == 1 -> TYPE_IMAGE_RECEIVER
                        message.voice?.isNotEmpty() == true -> TYPE_VOICE_RECEIVER
                        message.file?.isNotEmpty() == true -> TYPE_FILE_RECEIVER
                        else -> TYPE_IMAGES_RECEIVER
                    }
                }
            }
        } else {
            return when {
                message.text?.isNotEmpty() == true -> {
                    when {
                        message.images.isNullOrEmpty() -> TYPE_TEXT_SENDER
                        message.images?.size == 1 -> TYPE_TEXT_IMAGE_SENDER
                        else -> TYPE_TEXT_IMAGES_SENDER
                    }
                }
                else -> {
                    when {
                        message.images?.size == 1 -> TYPE_IMAGE_SENDER
                        message.voice?.isNotEmpty() == true -> TYPE_VOICE_SENDER
                        message.file?.isNotEmpty() == true -> TYPE_FILE_SENDER
                        else -> TYPE_IMAGES_SENDER
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
            TYPE_IMAGE_RECEIVER -> MessagesViewHolderImageReceiver(
                ItemImageReceiverBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
            TYPE_IMAGE_SENDER -> MessagesViewHolderImageSender(
                ItemImageSenderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
            TYPE_IMAGES_RECEIVER -> MessagesViewHolderImagesReceiver(
                ItemImagesReceiverBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
            TYPE_IMAGES_SENDER -> MessagesViewHolderImagesSender(
                ItemImagesSenderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
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
        val message = messages.keys.elementAt(position)
        when (holder) {
            is MessagesViewHolderReceiver -> holder.bind(message, position)
            is MessagesViewHolderSender -> holder.bind(message, position)
            is MessagesViewHolderImageReceiver -> holder.bind(message, position)
            is MessagesViewHolderImageSender -> holder.bind(message, position)
            is MessagesViewHolderImagesReceiver -> holder.bind(message, position)
            is MessagesViewHolderImagesSender -> holder.bind(message, position)
            is MessagesViewHolderVoiceReceiver -> holder.bind(message, position)
            is MessagesViewHolderVoiceSender -> holder.bind(message, position)
            is MessagesViewHolderFileReceiver -> holder.bind(message, position)
            is MessagesViewHolderFileSender -> holder.bind(message, position)
            is MessagesViewHolderTextImageReceiver -> holder.bind(message, position)
            is MessagesViewHolderTextImageSender -> holder.bind(message, position)
            is MessagesViewHolderTextImagesReceiver -> holder.bind(message, position)
            is MessagesViewHolderTextImagesSender -> holder.bind(message, position)
        }
    }

    override fun getItemCount(): Int = messages.size

    // ViewHolder для текстовых сообщений получателя
    inner class MessagesViewHolderReceiver(private val binding: ItemMessageReceiverBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(message: Message, position: Int) {
            binding.messageReceiverTextView.text = message.text
            val time = formatMessageTime(message.timestamp)
            val date = messages.values.elementAt(position)
            if(date != "") {
                binding.dateTextView.visibility = View.VISIBLE
                binding.dateTextView.text = date
            } else binding.dateTextView.visibility = View.GONE
            binding.timeTextView.text = time
            if(!canLongClick && dialogSettings.canDelete) {
                if(!binding.checkbox.isVisible) binding.checkbox.visibility = View.VISIBLE
                binding.checkbox.isChecked = position in checkedPositions
                binding.checkbox.setOnClickListener {
                    savePosition(message)
                }
            }
            else { binding.checkbox.visibility = View.GONE }
            if (message.isRead) {
                binding.icCheck.visibility = View.INVISIBLE
                binding.icCheck2.visibility = View.VISIBLE
            }
            if(message.isEdited) binding.editTextView.visibility = View.VISIBLE
            binding.root.setOnClickListener {
                if(!canLongClick) {
                    savePosition(message)
                }
                else
                    actionListener.onMessageClick(message, itemView)
            }
            binding.root.setOnLongClickListener {
                if(canLongClick) {
                    onLongClick(message)
                    actionListener.onMessageLongClick(message, itemView)
                }
                true
            }
        }
    }

    // ViewHolder для текстовых сообщений отправителя
    inner class MessagesViewHolderSender(private val binding: ItemMessageSenderBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(message: Message, position: Int) {
            binding.messageSenderTextView.text = message.text
            val time = formatMessageTime(message.timestamp)
            val date = messages.values.elementAt(position)
            if(date != "") {
                binding.dateTextView.visibility = View.VISIBLE
                binding.dateTextView.text = date
            } else binding.dateTextView.visibility = View.GONE
            binding.timeTextView.text = time
            if(!canLongClick) {
                if(!binding.checkbox.isVisible) binding.checkbox.visibility = View.VISIBLE
                binding.checkbox.isChecked = position in checkedPositions
                binding.checkbox.setOnClickListener {
                    savePosition(message)
                }
            }
            else { binding.checkbox.visibility = View.GONE }
            if (message.isRead) {
                binding.icCheck.visibility = View.INVISIBLE
                binding.icCheck2.visibility = View.VISIBLE
            }
            if(message.isEdited) binding.editTextView.visibility = View.VISIBLE
            binding.root.setOnClickListener {
                if(!canLongClick) {
                    savePosition(message)
                }
                else
                    actionListener.onMessageClick(message, itemView)
            }
            binding.root.setOnLongClickListener {
                if(canLongClick) {
                    onLongClick(message)
                    actionListener.onMessageLongClick(message, itemView)
                }
                true
            }
        }
    }

    // ViewHolder для изображений получателя
    inner class MessagesViewHolderImageReceiver(private val binding: ItemImageReceiverBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(message: Message, position: Int) {
            //Glide.with(binding.root.context).load(message.images?.first()).into(binding.receiverImageView)
            val time = formatMessageTime(message.timestamp)
            val date = messages.values.elementAt(position)
            if(date != "") {
                binding.dateTextView.visibility = View.VISIBLE
                binding.dateTextView.text = date
            } else binding.dateTextView.visibility = View.GONE
            binding.timeTextView.text = time
            if(!canLongClick && dialogSettings.canDelete) {
                if(!binding.checkbox.isVisible) binding.checkbox.visibility = View.VISIBLE
                binding.checkbox.isChecked = position in checkedPositions
                binding.checkbox.setOnClickListener {
                    savePosition(message)
                }
            }
            else { binding.checkbox.visibility = View.GONE }
            if (message.isRead) {
                binding.icCheck.visibility = View.INVISIBLE
                binding.icCheck2.visibility = View.VISIBLE
            }
            if(message.isEdited) binding.editTextView.visibility = View.VISIBLE
            binding.root.setOnClickListener {
                if(!canLongClick) {
                    savePosition(message)
                }
                else
                    actionListener.onMessageClick(message, itemView)
            }
            binding.receiverImageView.setOnClickListener {
                // todo show image full screen
            }
            binding.root.setOnLongClickListener {
                if(canLongClick) {
                    onLongClick(message)
                    actionListener.onMessageLongClick(message, itemView)
                }
                true
            }
        }
    }

    // ViewHolder для изображений отправителя
    inner class MessagesViewHolderImageSender(private val binding: ItemImageSenderBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(message: Message, position: Int) {
            //Glide.with(binding.root.context).load(message.images?.first()).into(binding.senderImageView)
            val time = formatMessageTime(message.timestamp)
            val date = messages.values.elementAt(position)
            if(date != "") {
                binding.dateTextView.visibility = View.VISIBLE
                binding.dateTextView.text = date
            } else binding.dateTextView.visibility = View.GONE
            binding.timeTextView.text = time
            if(!canLongClick) {
                if(!binding.checkbox.isVisible) binding.checkbox.visibility = View.VISIBLE
                binding.checkbox.isChecked = position in checkedPositions
                binding.checkbox.setOnClickListener {
                    savePosition(message)
                }
            }
            else { binding.checkbox.visibility = View.GONE }
            if (message.isRead) {
                binding.icCheck.visibility = View.INVISIBLE
                binding.icCheck2.visibility = View.VISIBLE
            }
            if(message.isEdited) binding.editTextView.visibility = View.VISIBLE
            binding.root.setOnClickListener {
                if(!canLongClick) {
                    savePosition(message)
                }
                else
                    actionListener.onMessageClick(message, itemView)
            }
            binding.senderImageView.setOnClickListener {
                // todo show image full screen
            }
            binding.root.setOnLongClickListener {
                if(canLongClick) {
                    onLongClick(message)
                    actionListener.onMessageLongClick(message, itemView)
                }
                true
            }
        }
    }

    // ViewHolder для множества изображений получателя
    inner class MessagesViewHolderImagesReceiver(private val binding: ItemImagesReceiverBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(message: Message, position: Int) {
            val adapter = ImagesAdapter(message.images ?: emptyList())
            binding.recyclerview.layoutManager = GridLayoutManager(binding.root.context, 3)
            binding.recyclerview.adapter = adapter
            binding.root.setOnClickListener { actionListener.onMessageClick(message, itemView) }
            binding.root.setOnLongClickListener {
                actionListener.onMessageLongClick(message, itemView)
                true
            }
        }
    }

    // ViewHolder для множества изображений отправителя
    inner class MessagesViewHolderImagesSender(private val binding: ItemImagesSenderBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(message: Message, position: Int) {
            val adapter = ImagesAdapter(message.images ?: emptyList())
            binding.recyclerview.layoutManager = GridLayoutManager(binding.root.context, 3)
            binding.recyclerview.adapter = adapter
            binding.root.setOnClickListener { actionListener.onMessageClick(message, itemView) }
            binding.root.setOnLongClickListener {
                actionListener.onMessageLongClick(message, itemView)
                true
            }
        }
    }

    inner class MessagesViewHolderVoiceReceiver(private val binding: ItemVoiceReceiverBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(message: Message, position: Int) {

        }
    }

    inner class MessagesViewHolderVoiceSender(private val binding: ItemVoiceSenderBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(message: Message, position: Int) {

        }
    }

    inner class MessagesViewHolderFileReceiver(private val binding: ItemFileReceiverBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(message: Message, position: Int) {

        }
    }

    inner class MessagesViewHolderFileSender(private val binding: ItemFileSenderBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(message: Message, position: Int) {

        }
    }

    inner class MessagesViewHolderTextImageReceiver(private val binding: ItemTextImageReceiverBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(message: Message, position: Int) {

        }
    }

    inner class MessagesViewHolderTextImageSender(private val binding: ItemTextImageSenderBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(message: Message, position: Int) {

        }
    }

    inner class MessagesViewHolderTextImagesReceiver(private val binding: ItemTextImagesReceiverBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(message: Message, position: Int) {

        }
    }

    inner class MessagesViewHolderTextImagesSender(private val binding: ItemTextImagesSenderBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(message: Message, position: Int) {

        }
    }

    private fun formatMessageTime(timestamp: Long?): String {
        if (timestamp == null) return "-"

        // Приведение серверного времени (МСК GMT+3) к GMT
        val greenwichMessageDate = Calendar.getInstance().apply {
            timeInMillis = timestamp - 10800000
        }
        val dateFormatToday = SimpleDateFormat("HH:mm", Locale.getDefault())
        return dateFormatToday.format(greenwichMessageDate.time)
    }

    private fun formatMessageDate(timestamp: Long?): String {
        if (timestamp == null) return ""

        // Приведение серверного времени (МСК GMT+3) к GMT
        val greenwichMessageDate = Calendar.getInstance().apply {
            timeInMillis = timestamp - 10800000
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
}
