package com.example.messenger

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.messenger.databinding.ItemMessengerBinding
import com.example.messenger.model.Conversation
import com.example.messenger.model.Message

interface MessengerActionListener {
    fun onConversationClicked(conversation: Conversation, index: Int)
}

class MessengerAdapter(
    private val actionListener: MessengerActionListener
) : RecyclerView.Adapter<MessengerAdapter.MessengerViewHolder>(), View.OnClickListener {
    var conversations: List<Conversation> = emptyList()
        @SuppressLint("NotifyDataSetChanged")
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    private var index = 0


    override fun onClick(v: View) {
        val conversation = v.tag as Conversation
        for (i in conversations.indices) {
            if(conversations[i] == conversation) {
                index = i
                break
            }
        }
        actionListener.onConversationClicked(conversation, index)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessengerViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemMessengerBinding.inflate(inflater, parent, false)
        binding.root.setOnClickListener(this) //list<conversation> element
        return MessengerViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MessengerViewHolder, position: Int) {
        val conversation = conversations[position]
        with(holder.binding) {
            holder.itemView.tag = conversation
            if(conversation.type == "dialog") {
                userNameTextView.text = conversation.otherUser?.username ?: "Имя не указано"
                lastMessageTextView.text = conversation.lastMessage?.text ?: "Сообщений пока нет"
                dateText.text = conversation.lastMessage?.timestamp.toString()
            }
            else {
                userNameTextView.text = conversation.name
                lastMessageTextView.text = conversation.lastMessage?.text ?: "Сообщений пока нет"
                dateText.text = conversation.lastMessage?.timestamp.toString()
            }
        }
    }

    override fun getItemCount(): Int = conversations.size
    class MessengerViewHolder(
        val binding: ItemMessengerBinding
    ) : RecyclerView.ViewHolder(binding.root)
}