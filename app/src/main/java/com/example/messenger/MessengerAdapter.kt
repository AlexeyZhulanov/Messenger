package com.example.messenger

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.messenger.databinding.ItemMessengerBinding
import com.example.messenger.model.Conversation
import com.example.messenger.states.AvatarState
import com.example.messenger.states.ConversationUi

interface MessengerActionListener {
    fun onConversationClicked(conversation: Conversation)
}

class ConversationDiffCallback : DiffUtil.ItemCallback<ConversationUi>() {
    override fun areItemsTheSame(oldItem: ConversationUi, newItem: ConversationUi): Boolean {
        return oldItem.conversation.id == newItem.conversation.id
    }

    override fun areContentsTheSame(oldItem: ConversationUi, newItem: ConversationUi): Boolean {
        return oldItem == newItem
    }
}

class MessengerAdapter(
    private val actionListener: MessengerActionListener
) : ListAdapter<ConversationUi, MessengerAdapter.MessengerViewHolder>(ConversationDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessengerViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemMessengerBinding.inflate(inflater, parent, false)
        return MessengerViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MessengerViewHolder, position: Int) {
        val ui = getItem(position) ?: return
        holder.bind(ui)
    }

    inner class MessengerViewHolder(val binding: ItemMessengerBinding) : RecyclerView.ViewHolder(binding.root) {
        private var conversationSave: Conversation? = null

        init {
            binding.root.setOnClickListener {
                conversationSave?.let { actionListener.onConversationClicked(it) }
            }
        }

        fun bind(ui: ConversationUi) {
            val conversation = ui.conversation
            conversationSave = conversation
            binding.userNameTextView.text = if(conversation.type == "dialog")
                conversation.otherUser?.username ?: "Имя не указано"
            else conversation.name

            with(binding) {
                if(conversation.lastMessage.isRead != null) {
                    dateText.isVisible = true
                    dateText.text = ui.dateText
                    when {
                        conversation.lastMessage.senderName != null -> {
                            icCheck2.isVisible = false
                            icCheck.isVisible = false
                        }
                        conversation.lastMessage.isRead -> {
                            icCheck2.isVisible = true
                            icCheck.isVisible = false
                        }
                        else -> {
                            icCheck2.isVisible = false
                            icCheck.isVisible = true
                        }
                    }
                    if(conversation.unreadCount > 0) {
                        unreadText.isVisible = true
                        unreadText.text = conversation.unreadCount.toString()
                    } else unreadText.isVisible = false
                    val txt = conversation.lastMessage.text ?: "[Вложение]"
                    lastMessageTextView.text =
                        if(conversation.type == "group" && conversation.lastMessage.senderName != null) {
                       "${conversation.lastMessage.senderName}: $txt"
                    } else txt
                } else {
                    lastMessageTextView.text = "Сообщений пока нет"
                    icCheck.isVisible = false
                    icCheck2.isVisible = false
                    dateText.isVisible = false
                    unreadText.isVisible = false
                }
            }
            when(val state = ui.avatarState) {
                is AvatarState.Loading -> {
                    binding.progressBar.isVisible = true
                    binding.errorImageView.isVisible = false
                }
                is AvatarState.Ready -> {
                    binding.progressBar.isVisible = false
                    binding.errorImageView.isVisible = false
                    binding.photoImageView.imageTintList = null
                    Glide.with(binding.photoImageView)
                        .load(state.localPath)
                        .circleCrop()
                        .dontAnimate()
                        .into(binding.photoImageView)
                }
                is AvatarState.Error -> {
                    binding.progressBar.isVisible = false
                    binding.errorImageView.isVisible = true
                }
                null -> {
                    binding.progressBar.isVisible = false
                    binding.errorImageView.isVisible = false
                }
            }
        }
    }
}