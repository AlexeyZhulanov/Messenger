package com.example.messenger.model

import android.util.Log
import androidx.paging.PagingSource
import androidx.paging.PagingState


class NewsPagingSource(
    private val retrofitService: RetrofitService,
    private val messengerService: MessengerService,
    private val fileManager: FileManager
) : PagingSource<Int, News>() {

    private var flag = true

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, News> {
        return try {
            Log.d("testLoadNews", "OK")
            val pageIndex = params.key ?: 1
            val news = try {
                    retrofitService.getNews(pageIndex, params.loadSize)
                } catch (e: Exception) {
                    if(flag) {
                        flag = false
                        messengerService.getNews()
                    } else throw e
                }
            if(pageIndex == 1 && flag) messengerService.replaceNews(news, fileManager)

            LoadResult.Page(
                data = news,
                prevKey = if (pageIndex == 1) null else pageIndex - 1,
                nextKey = if (news.size == params.loadSize) pageIndex + 1 else null
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, News>): Int? {
        val anchorPosition = state.anchorPosition ?: return null
        val page = state.closestPageToPosition(anchorPosition) ?: return null
        return page.prevKey?.plus(1) ?: page.nextKey?.minus(1)
    }
}