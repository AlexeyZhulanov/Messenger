package com.example.messenger.model

import androidx.paging.PagingSource
import androidx.paging.PagingState
import kotlin.math.abs

class MessagePagingSource(
    private val retrofitService: RetrofitService,
    private val messengerService: MessengerService,
    private val dialogId: Int,
    private val query: String? = null,
    private val isFirst: Boolean
) : PagingSource<Int, Message>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Message> {
        return try {
            val pageIndex = params.key ?: getInitialPageIndex()
            val serverPageIndex = abs(getInitialPageIndex() - pageIndex)
            val messages = if (query.isNullOrEmpty()) {
                retrofitService.getMessages(dialogId, serverPageIndex, params.loadSize)
            } else {
                retrofitService.searchMessagesInDialog(dialogId, query)
            }

            LoadResult.Page(
                data = messages.reversed(),
                prevKey = if (pageIndex == getInitialPageIndex()) null else pageIndex + 1,
                nextKey = if (messages.size == params.loadSize) pageIndex - 1 else null
            )
        } catch (e: Exception) {
            if (isFirst) {
            val pageIndex = 9000
            if (query.isNullOrEmpty()) {
                val messages = messengerService.getMessages(dialogId)
                LoadResult.Page(
                    data = messages.reversed(),
                    prevKey = null,
                    nextKey = if (messages.size == params.loadSize) pageIndex - 1 else null
                )
            } else LoadResult.Error(e)
            }
            else LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, Message>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            state.closestPageToPosition(anchorPosition)?.prevKey?.plus(1)
        }
    }
    private fun getInitialPageIndex(): Int {
        return 10000
    }
}
