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

    private var lastCursor: Long? = null
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

    suspend fun loadPage(pageSize: Int, currentQuery: String): List<Triple<Message, String, String>> {
        return try {
            val messages = if (currentQuery.isEmpty()) {
                try {
                    val mes = if(isDialog) retrofitService.getMessages(convId, pageSize, lastCursor)
                    else retrofitService.getGroupMessages(convId, pageSize, lastCursor)
                    mes.forEach {
                        it.text = it.text?.let { text -> tinkAesGcmHelper?.decryptText(text) }
                        it.code = it.code?.let { code -> tinkAesGcmHelper?.decryptText(code) }
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
                        } catch (_: Exception) { emptyList() }
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
            if(lastCursor == null && flag) {
                if(isDialog) messengerService.replaceMessages(convId, messages, fileManager)
                else messengerService.replaceGroupMessages(convId, messages, fileManager)
            }
            // Обновляем курсор
            lastCursor = messages.first().timestamp

            val formattedData = formatMessageTimesAndDates(messages)
            // Формирование дат для адаптера
            val dates = mutableSetOf<String>()
            val messageDatePairs = mutableListOf<Triple<Message, String, String>>()
            messages.forEach { message ->
                val (formattedTime, formattedDate) = formattedData[message.id] ?: ("-" to "-")
                if (formattedDate !in dates) {
                    messageDatePairs.add(Triple(message, formattedDate, formattedTime))
                    dates.add(formattedDate)
                } else {
                    messageDatePairs.add(Triple(message, "", formattedTime))
                }
            }
            messageDatePairs.reversed()
        } catch (e: Exception) {
            Log.e("testMessagePagingSource", "Error loading page", e)
            emptyList()
        }
    }

    fun resetCursor() {
        lastCursor = null
        flag = true
    }

    private fun formatMessageTimesAndDates(messages: List<Message>): Map<Int, Pair<String, String>> {
        val formattedData = mutableMapOf<Int, Pair<String, String>>()
        val dateFormatToday = SimpleDateFormat("HH:mm", Locale.getDefault())
        val dateFormatMonthDay = SimpleDateFormat("d MMMM", Locale.getDefault())
        val dateFormatYear = SimpleDateFormat("d MMMM yyyy", Locale.getDefault())
        val localNow = Calendar.getInstance()

        for (message in messages) {
            val timestamp = message.timestamp
            val greenwichMessageDate = Calendar.getInstance().apply {
                timeInMillis = timestamp
            }
            // Форматируем время
            val formattedTime = dateFormatToday.format(greenwichMessageDate.time)

            // Форматируем дату
            val formattedDate = when {
                isToday(localNow, greenwichMessageDate) -> dateFormatMonthDay.format(greenwichMessageDate.time)
                isThisYear(localNow, greenwichMessageDate) -> dateFormatMonthDay.format(greenwichMessageDate.time)
                else -> dateFormatYear.format(greenwichMessageDate.time)
            }
            // Сохраняем результат
            formattedData[message.id] = formattedTime to formattedDate
        }
        return formattedData
    }

    private fun isToday(now: Calendar, messageDate: Calendar): Boolean {
        return now.get(Calendar.YEAR) == messageDate.get(Calendar.YEAR) &&
                now.get(Calendar.DAY_OF_YEAR) == messageDate.get(Calendar.DAY_OF_YEAR)
    }

    private fun isThisYear(now: Calendar, messageDate: Calendar): Boolean {
        return now.get(Calendar.YEAR) == messageDate.get(Calendar.YEAR)
    }
}
