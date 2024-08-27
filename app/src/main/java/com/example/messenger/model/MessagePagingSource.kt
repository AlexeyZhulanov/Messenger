package com.example.messenger.model

import android.content.SharedPreferences
import android.util.Log
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.example.messenger.PREF_THEME
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

class MessagePagingSource(
    private val retrofitService: RetrofitService,
    private val messengerService: MessengerService,
    private val dialogId: Int,
    private val query: String,
    isFirst: Boolean,
    private val fileManager: FileManager
) : PagingSource<Int, Pair<Message, String>>() {

    private var flag = isFirst

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Pair<Message, String>> {
        return try {
            val pageIndex = params.key ?: getInitialPageIndex()
            val serverPageIndex = abs(getInitialPageIndex() - pageIndex)
            val messages = if (query == "") {
                try {
                    retrofitService.getMessages(dialogId, serverPageIndex, params.loadSize)
                } catch (e: Exception) {
                    if(flag) {
                        flag = false
                        messengerService.getMessages(dialogId)
                    } else throw e
                }
            } else {
                retrofitService.searchMessagesInDialog(dialogId, query)
            }
            if(serverPageIndex == 0)
                messengerService.replaceMessages(dialogId, messages, fileManager)

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
            LoadResult.Page(
                data = messageDatePairs.reversed(),
                prevKey = if (pageIndex == getInitialPageIndex()) null else pageIndex + 1,
                nextKey = if (messages.size == params.loadSize) pageIndex - 1 else null
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, Pair<Message, String>>): Int? {
        val anchorPosition = state.anchorPosition ?: return null
        val page = state.closestPageToPosition(anchorPosition) ?: return null
        return page.prevKey?.plus(1) ?: page.nextKey?.minus(1)
    }
    private fun getInitialPageIndex(): Int {
        return 10000
    }

    private fun formatMessageDate(timestamp: Long?): String {
        if (timestamp == null) return ""

        // Приведение серверного времени (МСК GMT+3) к GMT
        val greenwichMessageDate = Calendar.getInstance().apply {
            timeInMillis = timestamp - 10800000
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
