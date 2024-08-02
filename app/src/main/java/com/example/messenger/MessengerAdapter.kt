package com.example.messenger

import android.annotation.SuppressLint
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.messenger.databinding.ItemMessengerBinding
import com.example.messenger.model.Conversation
import com.example.messenger.model.Message
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

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
            }
            else {
                userNameTextView.text = conversation.name
            }
            lastMessageTextView.text = conversation.lastMessage?.text ?: "Сообщений пока нет"
            dateText.text = formatMessageDate(conversation.lastMessage?.timestamp)
            if(conversation.lastMessage?.isRead == true) icCheck2.visibility = View.VISIBLE
            else icCheck.visibility = View.VISIBLE
        }
    }

    override fun getItemCount(): Int = conversations.size
    class MessengerViewHolder(
        val binding: ItemMessengerBinding
    ) : RecyclerView.ViewHolder(binding.root)

    private fun formatMessageDate(timestamp: Long?): String {
        if(timestamp == null) return "-"
        // Приведение серверного времени(мск GMT+3) к GMT
        val greenwichMessageDate = Calendar.getInstance().apply {
            timeInMillis = timestamp - 10800000
        }
        val dateFormatToday = SimpleDateFormat("HH:mm", Locale.getDefault())
        val dateFormatDayMonth = SimpleDateFormat("d MMM", Locale.getDefault())
        val dateFormatYear = SimpleDateFormat("d.MM.yyyy", Locale.getDefault())
        val localNow = Calendar.getInstance()
        return when {
            isToday(localNow, greenwichMessageDate) -> dateFormatToday.format(greenwichMessageDate.time)
            isThisYear(localNow, greenwichMessageDate) -> dateFormatDayMonth.format(greenwichMessageDate.time)
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