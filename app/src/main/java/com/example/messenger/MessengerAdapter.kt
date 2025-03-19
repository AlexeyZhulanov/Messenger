package com.example.messenger

import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.example.messenger.databinding.ItemMessengerBinding
import com.example.messenger.model.Conversation
import com.example.messenger.model.LastMessage
import com.example.messenger.model.ShortMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

interface MessengerActionListener {
    fun onConversationClicked(conversation: Conversation, index: Int)
}

class ConversationDiffCallback : DiffUtil.ItemCallback<Conversation>() {
    override fun areItemsTheSame(oldItem: Conversation, newItem: Conversation): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: Conversation, newItem: Conversation): Boolean {
        return oldItem == newItem
    }
}

class MessengerAdapter(
    private val messengerViewModel: MessengerViewModel,
    private val context: Context,
    private val actionListener: MessengerActionListener
) : ListAdapter<Conversation, MessengerAdapter.MessengerViewHolder>(ConversationDiffCallback()), View.OnClickListener {

    private var index = 0
    private var uiScope = CoroutineScope(Dispatchers.Main)

    fun updateConversations(shortMessagesStart: List<ShortMessage>) {
        val currentList = currentList.toMutableList()
        val shortMessages = filterLatestMessages(shortMessagesStart)

        for(shortMessage in shortMessages) {
            val type = if(shortMessage.isGroup) "group" else "dialog"
            val index = currentList.indexOfFirst { it.id == shortMessage.chatId && it.type == type }
            val text = if (shortMessage.text == null) "Вложение" else "${shortMessage.senderName}: ${shortMessage.text}"
            val lastMessage = LastMessage(text, shortMessage.timestamp, false)

            if (index != -1) {
                val conv = getItem(index)
                val conversation = conv.copy(lastMessage = lastMessage, unreadCount = conv.unreadCount + 1)
                // Обновляем элемент
                currentList[index] = conversation

                // Если элемент не первый, перемещаем его на первое место
                if (index != 0) {
                    currentList.removeAt(index)
                    currentList.add(0, conversation)
                }
            } else {
                // Если элемент не найден, добавляем его в начало списка
                val conversation = Conversation(type, shortMessage.chatId, lastMessage = lastMessage,
                    countMsg = 1, isOwner = false, canDelete = false, autoDeleteInterval = 0, unreadCount = 1)
                currentList.add(0, conversation)
            }
        }
        submitList(currentList)
    }

    override fun onClick(v: View) {
        val conversation = v.tag as Conversation
        for (i in currentList.indices) {
            if(currentList[i] == conversation) {
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
        val conversation = getItem(position)
        with(holder.binding) {
            holder.itemView.tag = conversation
            userNameTextView.text = if(conversation.type == "dialog")
                conversation.otherUser?.username ?: "Имя не указано"
            else conversation.name

            if(conversation.lastMessage.isRead != null) {
                dateText.visibility = View.VISIBLE
                dateText.text = formatMessageDate(conversation.lastMessage.timestamp)
                if (conversation.lastMessage.isRead == true) icCheck2.visibility = View.VISIBLE
                else icCheck.visibility = View.VISIBLE
                if(conversation.unreadCount > 0) {
                    val unreadCnt = conversation.unreadCount.toString()
                    unreadText.visibility = View.VISIBLE
                    unreadText.text = unreadCnt
                } else unreadText.visibility = View.GONE
                lastMessageTextView.text = conversation.lastMessage.text ?: "[Вложение]"
            } else {
                lastMessageTextView.text = "Сообщений пока нет"
                icCheck.visibility = View.GONE
                icCheck2.visibility = View.GONE
                dateText.visibility = View.GONE
                unreadText.visibility = View.GONE
            }

            val avatar = conversation.otherUser?.avatar ?: conversation.avatar ?: ""
            if (avatar != "") {
                uiScope.launch {
                    // Проверяем, актуален ли ViewHolder
                    if (holder.itemView.tag == conversation) {
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

                        // Проверяем, актуален ли ViewHolder перед обновлением UI
                        if (holder.itemView.tag == conversation && first != null) {
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
    }

    class MessengerViewHolder(
        val binding: ItemMessengerBinding
    ) : RecyclerView.ViewHolder(binding.root)

    private fun formatMessageDate(timestamp: Long?): String {
        if(timestamp == null) return "-"

        val greenwichMessageDate = Calendar.getInstance().apply {
            timeInMillis = timestamp
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

    private fun filterLatestMessages(messages: List<ShortMessage>): List<ShortMessage> {
        val result = mutableListOf<ShortMessage>()

        for (i in messages.indices.reversed()) {
            val mes = messages[i]
            if (result.none { it.chatId == mes.chatId && it.isGroup == mes.isGroup }) {
                result.add(mes)
            }
        }

        return result.reversed()
    }
}