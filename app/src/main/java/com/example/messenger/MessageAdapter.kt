package com.example.messenger

import android.annotation.SuppressLint
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
    fun onMessageClick(message: Message)
    fun onMessageLongClick(message: Message)
}

class MessageAdapter(
    private val actionListener: MessageActionListener,
    private val senderId: Int
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
            message.idSender == senderId && message.images?.size == 0 && message.text?.length != 0 -> TYPE_TEXT_SENDER
            message.images?.size == 0 && message.text?.length != 0 -> TYPE_TEXT_RECEIVER
            message.images?.size == 1 && message.idSender == senderId -> TYPE_IMAGE_SENDER
            message.images?.size == 1 -> TYPE_IMAGE_RECEIVER
            message.idSender == senderId -> TYPE_IMAGES_SENDER
            else  -> TYPE_IMAGES_RECEIVER
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_TEXT_RECEIVER -> MessagesViewHolder(
                ItemMessageReceiverBinding.inflate(LayoutInflater.from(parent.context), parent, false).root
            )
            TYPE_TEXT_SENDER -> MessagesViewHolder(
                ItemMessageSenderBinding.inflate(LayoutInflater.from(parent.context), parent, false).root
            )
            TYPE_IMAGE_RECEIVER -> MessagesViewHolder(
                ItemImageReceiverBinding.inflate(LayoutInflater.from(parent.context), parent, false).root
            )
            TYPE_IMAGE_SENDER -> MessagesViewHolder(
                ItemImageSenderBinding.inflate(LayoutInflater.from(parent.context), parent, false).root
            )
            TYPE_IMAGES_RECEIVER -> MessagesViewHolder(
                ItemImagesReceiverBinding.inflate(LayoutInflater.from(parent.context), parent, false).root
            )
            TYPE_IMAGES_SENDER -> MessagesViewHolder(
                ItemImagesSenderBinding.inflate(LayoutInflater.from(parent.context), parent, false).root
            )
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]
        when (holder) {
            is MessagesViewHolder -> {
                when (getItemViewType(position)) {
                    TYPE_TEXT_RECEIVER -> holder.bindTextReceiver(message)
                    TYPE_TEXT_SENDER -> holder.bindTextSender(message)
                    TYPE_IMAGE_RECEIVER -> holder.bindImageReceiver(message)
                    TYPE_IMAGE_SENDER -> holder.bindImageSender(message)
                    TYPE_IMAGES_RECEIVER -> holder.bindImagesReceiver(message)
                    TYPE_IMAGES_SENDER -> holder.bindImagesSender(message)
                }
            }
        }
    }

    override fun getItemCount(): Int = messages.size

    inner class MessagesViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        private val messageReceiverBinding = ItemMessageReceiverBinding.bind(itemView)
        private val messageSenderBinding = ItemMessageSenderBinding.bind(itemView)
        private val imageReceiverBinding = ItemImageReceiverBinding.bind(itemView)
        private val imageSenderBinding = ItemImageSenderBinding.bind(itemView)
        private val imagesReceiverBinding = ItemImagesReceiverBinding.bind(itemView)
        private val imagesSenderBinding = ItemImagesSenderBinding.bind(itemView)

        fun bindTextReceiver(message: Message) {
            messageReceiverBinding.apply {
                messageReceiverTextView.text = message.text
                root.setOnClickListener { actionListener.onMessageClick(message) }
                root.setOnLongClickListener {
                    actionListener.onMessageLongClick(message)
                    true
                }
            }
        }

        fun bindTextSender(message: Message) {
            messageSenderBinding.apply {
                messageSenderTextView.text = message.text
                root.setOnClickListener { actionListener.onMessageClick(message) }
                root.setOnLongClickListener {
                    actionListener.onMessageLongClick(message)
                    true
                }
            }
        }

        fun bindImageReceiver(message: Message) {
            imageReceiverBinding.apply {
                // Загрузите изображение и примените данные
                //Glide.with(root.context).load(message.imageUrl).into(imageView)
                root.setOnClickListener { actionListener.onMessageClick(message) }
                root.setOnLongClickListener {
                    actionListener.onMessageLongClick(message)
                    true
                }
            }
        }

        fun bindImageSender(message: Message) {
            imageSenderBinding.apply {
                // Загрузите изображение и примените данные
                //Glide.with(root.context).load(message.imageUrl).into(imageView)
                root.setOnClickListener { actionListener.onMessageClick(message) }
                root.setOnLongClickListener {
                    actionListener.onMessageLongClick(message)
                    true
                }
            }
        }

        fun bindImagesReceiver(message: Message) {
            imagesReceiverBinding.apply {
                val adapter = ImagesAdapter(message.images ?: emptyList())
                recyclerview.layoutManager = GridLayoutManager(root.context, 3)
                recyclerview.adapter = adapter
                root.setOnClickListener { actionListener.onMessageClick(message) }
                root.setOnLongClickListener {
                    actionListener.onMessageLongClick(message)
                    true
                }
            }
        }

        fun bindImagesSender(message: Message) {
            imagesSenderBinding.apply {
                val adapter = ImagesAdapter(message.images ?: emptyList())
                recyclerview.layoutManager = GridLayoutManager(root.context, 3)
                recyclerview.adapter = adapter
                root.setOnClickListener { actionListener.onMessageClick(message) }
                root.setOnLongClickListener {
                    actionListener.onMessageLongClick(message)
                    true
                }
            }
        }
    }
}