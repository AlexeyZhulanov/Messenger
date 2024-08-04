package com.example.messenger

import android.annotation.SuppressLint
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.messenger.databinding.ItemImageReceiverBinding
import com.example.messenger.databinding.ItemImageSenderBinding
import com.example.messenger.databinding.ItemImagesReceiverBinding
import com.example.messenger.databinding.ItemImagesSenderBinding
import com.example.messenger.databinding.ItemMessageReceiverBinding
import com.example.messenger.databinding.ItemMessageSenderBinding
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
    }

    override fun getItemViewType(position: Int): Int {
        val message = messages.keys.elementAt(position)
        return when {
            message.idSender == otherUserId && message.images.isNullOrEmpty() && message.text?.isNotEmpty() == true -> TYPE_TEXT_RECEIVER
            message.images.isNullOrEmpty() && message.text?.isNotEmpty() == true -> TYPE_TEXT_SENDER
            message.images?.size == 1 && message.idSender == otherUserId -> TYPE_IMAGE_RECEIVER
            message.images?.size == 1 -> TYPE_IMAGE_SENDER
            message.idSender == otherUserId -> TYPE_IMAGES_RECEIVER
            else -> TYPE_IMAGES_SENDER
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
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages.keys.elementAt(position)
        when (holder) {
            is MessagesViewHolderReceiver -> holder.bind(message, position)
            is MessagesViewHolderSender -> holder.bind(message, position)
            is MessagesViewHolderImageReceiver -> holder.bind(message)
            is MessagesViewHolderImageSender -> holder.bind(message)
            is MessagesViewHolderImagesReceiver -> holder.bind(message)
            is MessagesViewHolderImagesSender -> holder.bind(message)
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
        fun bind(message: Message) {
            // Загрузите изображение и примените данные
            //Glide.with(binding.root.context).load(message.imageUrl).into(binding.imageView)
            binding.root.setOnClickListener { actionListener.onMessageClick(message, itemView) }
            binding.root.setOnLongClickListener {
                actionListener.onMessageLongClick(message, itemView)
                true
            }
        }
    }

    // ViewHolder для изображений отправителя
    inner class MessagesViewHolderImageSender(private val binding: ItemImageSenderBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(message: Message) {
            // Загрузите изображение и примените данные
            //Glide.with(binding.root.context).load(message.imageUrl).into(binding.imageView)
            binding.root.setOnClickListener { actionListener.onMessageClick(message, itemView) }
            binding.root.setOnLongClickListener {
                actionListener.onMessageLongClick(message, itemView)
                true
            }
        }
    }

    // ViewHolder для множества изображений получателя
    inner class MessagesViewHolderImagesReceiver(private val binding: ItemImagesReceiverBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(message: Message) {
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
        fun bind(message: Message) {
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
