package com.example.messenger.model

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MessagePagingSource(
    private val retrofitService: RetrofitService,
    private val messengerService: MessengerService,
    private val convId: Int,
    private val query: String,
    private val fileManager: FileManager,
    private val isDialog: Boolean = true
) {

    private var flag = true

    suspend fun loadPage(pageIndex: Int, pageSize: Int): List<Pair<Message, String>> {
        return try {
            val messages = if (query.isEmpty()) {
                try {
                    if(isDialog) retrofitService.getMessages(convId, pageIndex, pageSize)
                    else retrofitService.getGroupMessages(convId, pageIndex, pageSize)
                } catch (e: Exception) {
                    if(flag) {
                        flag = false
                        if(isDialog) messengerService.getMessages(convId)
                        else messengerService.getGroupMessages(convId)
                    } else throw e
                }
            } else {
                if(isDialog) retrofitService.searchMessagesInDialog(convId, query)
                else retrofitService.searchMessagesInGroup(convId, query)
            }
            if(pageIndex == 0 && flag) {
                if(isDialog) messengerService.replaceMessages(convId, messages, fileManager)
                else messengerService.replaceGroupMessages(convId, messages, fileManager)
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
