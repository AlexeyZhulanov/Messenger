package com.example.messenger.model

import android.util.Log
import androidx.paging.PagingSource
import androidx.paging.PagingState
import kotlin.math.abs

class MessagePagingSource(
    private val retrofitService: RetrofitService,
    private val messengerService: MessengerService,
    private val dialogId: Int,
    private val query: String,
    isFirst: Boolean,
    private val fileManager: FileManager
) : PagingSource<Int, Message>() {

    private var flag = isFirst

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Message> {
        return try {
            Log.d("testSourceSearch", query.toString())
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

            LoadResult.Page(
                data = messages.reversed(),
                prevKey = if (pageIndex == getInitialPageIndex()) null else pageIndex + 1,
                nextKey = if (messages.size == params.loadSize) pageIndex - 1 else null
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, Message>): Int? {
        val anchorPosition = state.anchorPosition ?: return null
        val page = state.closestPageToPosition(anchorPosition) ?: return null
        return page.prevKey?.plus(1) ?: page.nextKey?.minus(1)
    }
    private fun getInitialPageIndex(): Int {
        return 10000
    }
}
