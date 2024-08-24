package com.example.messenger.model

import androidx.paging.PagingSource
import androidx.paging.PagingState

class MessagePagingSource(
    private val retrofitService: RetrofitService,
    private val dialogId: Int,
    private val countMsg: Int?,
    private val query: String? = null
) : PagingSource<Int, Message>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Message> {
        return try {
            val pageIndex = params.key ?: 0
            val messages = if (query.isNullOrEmpty()) {
                val start = if(countMsg != null && (countMsg - (pageIndex+1)*params.loadSize > 0))
                    countMsg - (pageIndex+1) * params.loadSize else 0
                val end = if(countMsg != null) countMsg - pageIndex * params.loadSize else -1
                retrofitService.getMessages(dialogId, start, end)
            } else {
                retrofitService.searchMessagesInDialog(dialogId, query)
            }

            LoadResult.Page(
                data = messages,
                prevKey = if (pageIndex == 0) null else pageIndex - 1,
                nextKey = if (messages.size == params.loadSize) pageIndex + 1 else null
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, Message>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            state.closestPageToPosition(anchorPosition)?.prevKey?.plus(1) ?: 0
        }
    }
}
