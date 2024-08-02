package com.example.messenger

import android.annotation.SuppressLint
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.messenger.databinding.ItemImageReceiverBinding
import com.example.messenger.databinding.ItemImageSenderBinding
import com.example.messenger.databinding.ItemImagesReceiverBinding
import com.example.messenger.databinding.ItemImagesSenderBinding
import com.example.messenger.databinding.ItemMessageReceiverBinding
import com.example.messenger.databinding.ItemMessageSenderBinding
import com.example.messenger.model.Conversation
import com.example.messenger.model.ConversationSettings
import com.example.messenger.model.Dialog
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
) : RecyclerView.Adapter<RecyclerView.ViewHolder>(), View.OnClickListener, View.OnLongClickListener {

    var messages: List<Message> = emptyList()
        @SuppressLint("NotifyDataSetChanged")
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    private var dates = mutableSetOf<String>()
    var canLongClick: Boolean = true
    private var checkedPositions: MutableSet<Int> = mutableSetOf()
    lateinit var dialogSettings: ConversationSettings

    fun getDeleteList(): List<Int> {
        val list = mutableListOf<Int>()
        for(i in checkedPositions) list.add(messages[i].id)
        return list
    }

    fun clearPositions() {
        canLongClick = true
        checkedPositions.clear()
    }

    private fun savePosition(message: Message) {
        for (i in messages.indices) {
            if (messages[i] == message) {
                if (i in checkedPositions) {
                    checkedPositions.remove(i)
                } else {
                    checkedPositions.add(i)
                }
                break
            }
        }
    }

    override fun onClick(v: View) {
        val message = v.tag as Message
        if(v.id == R.id.checkbox) {
            if(!canLongClick) {
                savePosition(message)
            }
        } else
        if(!canLongClick) {
            savePosition(message)
            notifyDataSetChanged()
        }
    }

    override fun onLongClick(v: View?): Boolean {
        if(canLongClick) {
            val message = v?.tag as Message
            savePosition(message)
            actionListener.onMessageLongClick(message, v)
            canLongClick = false
            notifyDataSetChanged()
        }
        return true
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
        val message = messages[position]
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
        val message = messages[position]
        when (holder) {
            is MessagesViewHolderReceiver -> holder.bind(message)
            is MessagesViewHolderSender -> holder.bind(message)
            is MessagesViewHolderImageReceiver -> holder.bind(message)
            is MessagesViewHolderImageSender -> holder.bind(message)
            is MessagesViewHolderImagesReceiver -> holder.bind(message)
            is MessagesViewHolderImagesSender -> holder.bind(message)
        }
    }

    override fun getItemCount(): Int = messages.size

    // ViewHolder для текстовых сообщений получателя
    inner class MessagesViewHolderReceiver(private val binding: ItemMessageReceiverBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(message: Message) {
            binding.messageReceiverTextView.text = message.text
            binding.checkbox.visibility = View.GONE
            val (time, date) = formatMessageDate(message.timestamp)
            if(date !in dates) {
                dates.add(date)
                binding.dateTextView.text = date
            } else binding.dateTextView.visibility = View.GONE
            binding.timeTextView.text = time
            if (message.isRead) {
                binding.icCheck.visibility = View.INVISIBLE
                binding.icCheck2.visibility = View.VISIBLE
            }
            binding.root.setOnClickListener { actionListener.onMessageClick(message, itemView) }
            binding.root.setOnLongClickListener {
                actionListener.onMessageLongClick(message, itemView)
                true
            }
            if(!canLongClick && dialogSettings.canDelete) {
                binding.checkbox.visibility = View.VISIBLE
            }
        }
    }

    // ViewHolder для текстовых сообщений отправителя
    inner class MessagesViewHolderSender(private val binding: ItemMessageSenderBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(message: Message) {
            binding.messageSenderTextView.text = message.text
            binding.checkbox.visibility = View.GONE
            val (time, date) = formatMessageDate(message.timestamp)
            if(date !in dates) {
                dates.add(date)
                binding.dateTextView.text = date
            } else binding.dateTextView.visibility = View.GONE
            binding.timeTextView.text = time
            if (message.isRead) {
                binding.icCheck.visibility = View.INVISIBLE
                binding.icCheck2.visibility = View.VISIBLE
            }
            binding.root.setOnClickListener { actionListener.onMessageClick(message, itemView) }
            binding.root.setOnLongClickListener {
                actionListener.onMessageLongClick(message, itemView)
                true
            }
            if(!canLongClick) {
                binding.checkbox.visibility = View.VISIBLE
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

    private fun formatMessageDate(timestamp: Long?): Pair<String, String> {
        if (timestamp == null) return Pair("-", "")

        // Приведение серверного времени (МСК GMT+3) к GMT
        val greenwichMessageDate = Calendar.getInstance().apply {
            timeInMillis = timestamp - 10800000
        }
        val dateFormatToday = SimpleDateFormat("HH:mm", Locale.getDefault())
        val dateFormatDayMonth = SimpleDateFormat("d MMM", Locale.getDefault())
        val dateFormatYear = SimpleDateFormat("d.MM.yyyy", Locale.getDefault())
        val localNow = Calendar.getInstance()

        return when {
            isToday(localNow, greenwichMessageDate) -> Pair(
                dateFormatToday.format(greenwichMessageDate.time),
                dateFormatDayMonth.format(greenwichMessageDate.time)
            )
            isThisYear(localNow, greenwichMessageDate) -> Pair(
                dateFormatToday.format(greenwichMessageDate.time),
                dateFormatDayMonth.format(greenwichMessageDate.time)
            )
            else -> Pair(
                dateFormatToday.format(greenwichMessageDate.time),
                dateFormatYear.format(greenwichMessageDate.time)
            )
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
