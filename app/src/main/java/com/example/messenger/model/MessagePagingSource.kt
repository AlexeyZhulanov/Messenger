package com.example.messenger.model

import androidx.paging.PagingSource
import androidx.paging.PagingState

class MessagePagingSource(
    private val retrofitService: RetrofitService,
    private val dialogId: Int,
    private val query: String? = null
) : PagingSource<Int, Message>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Message> {
        return try {
            val nextPageNumber = params.key ?: 1
            val messages = if (query.isNullOrEmpty()) {
                retrofitService.getMessages(dialogId, nextPageNumber, params.loadSize)
            } else {
                retrofitService.searchMessagesInDialog(dialogId, query)
            }

            LoadResult.Page(
                data = messages,
                prevKey = if (nextPageNumber == 1) null else nextPageNumber - 1, // если первая страница, то нет предыдущей
                nextKey = if (messages.isEmpty()) null else nextPageNumber + 1 // если сообщений нет, то нет следующей страницы
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, Message>): Int? {
        // Возвращаем ключ для обновления списка
        return state.anchorPosition?.let { anchorPosition ->
            state.closestPageToPosition(anchorPosition)?.prevKey?.plus(1) ?: 0
        }
    }
}
