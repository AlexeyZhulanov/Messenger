package com.example.messenger

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.example.messenger.databinding.ItemMessengerBinding
import com.example.messenger.model.Conversation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

interface MessengerActionListener {
    fun onConversationClicked(conversation: Conversation, index: Int)
}

class MessengerAdapter(
    private val messengerViewModel: MessengerViewModel,
    private val context: Context,
    private val actionListener: MessengerActionListener
) : RecyclerView.Adapter<MessengerAdapter.MessengerViewHolder>(), View.OnClickListener {
    var conversations: List<Conversation> = emptyList()
        @SuppressLint("NotifyDataSetChanged")
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    private var index = 0
    private var uiScope = CoroutineScope(Dispatchers.Main)

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
            // todo с этим подумать как отобразить фото, файл, аудио
            lastMessageTextView.text = conversation.lastMessage?.text ?: "Вложение"
            dateText.text = formatMessageDate(conversation.lastMessage?.timestamp)
            if(conversation.lastMessage?.isRead == true) icCheck2.visibility = View.VISIBLE
            else icCheck.visibility = View.VISIBLE
            uiScope.launch {
                val avatar = conversation.otherUser?.avatar ?: ""
                if (avatar != "") {
                    val filePathTemp = async {
                        if (messengerViewModel.fManagerIsExistAvatar(avatar)) {
                            return@async Pair(messengerViewModel.fManagerGetAvatarPath(avatar), true)
                        } else {
                            try {
                                return@async Pair(messengerViewModel.downloadAvatar(context, avatar), false)
                            } catch (e: Exception) {
                                return@async Pair(null, true)
                            }
                        }
                    }
                    val (first, second) = filePathTemp.await()
                    if (first != null) {
                        val file = File(first)
                        if (file.exists()) {
                            if (!second) messengerViewModel.fManagerSaveAvatar(avatar, file.readBytes())
                            val uri = Uri.fromFile(file)
                            photoImageView.imageTintList = null
                            Glide.with(context)
                                .load(uri)
                                .apply(RequestOptions.circleCropTransform())
                                .diskCacheStrategy(DiskCacheStrategy.ALL)
                                .into(photoImageView)
                        }
                    }
                }
            }
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