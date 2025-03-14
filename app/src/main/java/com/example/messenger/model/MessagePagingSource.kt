package com.example.messenger.model

import android.util.Log
import com.example.messenger.security.TinkAesGcmHelper
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MessagePagingSource(
    private val retrofitService: RetrofitService,
    private val messengerService: MessengerService,
    private val convId: Int,
    private val fileManager: FileManager,
    private val tinkAesGcmHelper: TinkAesGcmHelper?,
    private val isDialog: Boolean = true
) {

    private var flag = true
    private var invertedIndex: InvertedIndex? = null
    private val searchCache = object : LinkedHashMap<String, List<Message>>(MAX_CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<Message>>): Boolean {
            return size > MAX_CACHE_SIZE
        }
    }

    companion object {
        private const val MAX_CACHE_SIZE = 50 // 50 queries
    }

    suspend fun loadPage(pageIndex: Int, pageSize: Int, currentQuery: String): List<Pair<Message, String>> {
        return try {
            val messages = if (currentQuery.isEmpty()) {
                try {
                    val mes = if(isDialog) retrofitService.getMessages(convId, pageIndex, pageSize)
                    else retrofitService.getGroupMessages(convId, pageIndex, pageSize)
                    mes.forEach {
                        it.text = it.text?.let { text -> tinkAesGcmHelper?.decryptText(text) }
                    }
                    mes
                } catch (e: Exception) {
                    if(flag) {
                        flag = false
                        if(isDialog) messengerService.getMessages(convId)
                        else messengerService.getGroupMessages(convId)
                    } else throw e
                }
            } else {
                searchCache[currentQuery] ?: run {
                    if (invertedIndex == null) {
                        val allMessages = try {
                            if (isDialog) retrofitService.searchMessagesInDialog(convId)
                            else retrofitService.searchMessagesInGroup(convId)
                        } catch (e: Exception) { emptyList() }
                        allMessages.forEach {
                            it.text = it.text?.let { text -> tinkAesGcmHelper?.decryptText(text) } ?: ""
                        }
                        if(allMessages.isNotEmpty()) invertedIndex = InvertedIndex(allMessages)
                    }
                    val result = invertedIndex?.searchMessages(currentQuery) ?: emptyList()
                    if(result.isNotEmpty()) searchCache[currentQuery] = result
                    result
                }
            }

            // Формирование дат для адаптера
            val dates = mutableSetOf<String>()
            val messageDatePairs = mutableListOf<Pair<Message, String>>()
            messages.forEach {
                val tTime = formatMessageDate(it.timestamp)
                if (tTime !in dates) {
                    messageDatePairs.add(it to tTime)
                    dates.add(tTime)
                } else {
                    messageDatePairs.add(it to "")
                }
            }

            if(pageIndex == 0 && flag) {
                if(isDialog) messengerService.replaceMessages(convId, messages, fileManager)
                else messengerService.replaceGroupMessages(convId, messages, fileManager)
            }
            messageDatePairs.reversed()
        } catch (e: Exception) {
            Log.e("MessagePagingSource", "Error loading page", e)
            emptyList()
        }
    }

    private fun formatMessageDate(timestamp: Long?): String {
        if (timestamp == null) return ""

        val greenwichMessageDate = Calendar.getInstance().apply {
            timeInMillis = timestamp
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
