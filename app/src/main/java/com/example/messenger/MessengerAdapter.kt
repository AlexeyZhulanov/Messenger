package com.example.messenger

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.messenger.databinding.ItemMessengerBinding
import com.example.messenger.model.Message

interface MessengerActionListener {
    fun onMessageClicked(message: Message, index: Int)
}

class MessengerAdapter(
    private val actionListener: MessengerActionListener
) : RecyclerView.Adapter<MessengerAdapter.MessengerViewHolder>(), View.OnClickListener {
    var messages: List<Message> = emptyList()
        @SuppressLint("NotifyDataSetChanged")
        set(value) {
            field = value
            notifyDataSetChanged()
        }


    override fun onClick(v: View) {
        val message = v.tag as Message
        // todo
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessengerViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemMessengerBinding.inflate(inflater, parent, false)
        binding.root.setOnClickListener(this) //list<message> element
        return MessengerViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MessengerViewHolder, position: Int) {
        val message = messages[position]
        with(holder.binding) {
            holder.itemView.tag = message
            // todo
        }
    }

    override fun getItemCount(): Int = messages.size
    class MessengerViewHolder(
        val binding: ItemMessengerBinding
    ) : RecyclerView.ViewHolder(binding.root)
}