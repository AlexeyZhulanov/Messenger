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
import com.example.messenger.model.Message

interface MessageActionListener {
    fun onMessageClick(message: Message, itemView: View)
    fun onMessageLongClick(message: Message, itemView: View)
}

class MessageAdapter(
    private val actionListener: MessageActionListener,
    private val otherUserId: Int
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    var messages: List<Message> = emptyList()
        @SuppressLint("NotifyDataSetChanged")
        set(value) {
            field = value
            notifyDataSetChanged()
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
            binding.root.setOnClickListener { actionListener.onMessageClick(message, itemView) }
            binding.root.setOnLongClickListener {
                actionListener.onMessageLongClick(message, itemView)
                true
            }
        }
    }

    // ViewHolder для текстовых сообщений отправителя
    inner class MessagesViewHolderSender(private val binding: ItemMessageSenderBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(message: Message) {
            binding.messageSenderTextView.text = message.text
            binding.root.setOnClickListener { actionListener.onMessageClick(message, itemView) }
            binding.root.setOnLongClickListener {
                actionListener.onMessageLongClick(message, itemView)
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
}
