package com.example.messenger.retrofit.source.news

import com.example.messenger.model.News
import com.example.messenger.retrofit.api.NewsApi
import com.example.messenger.retrofit.entities.news.SendNewsRequestEntity
import com.example.messenger.retrofit.source.base.BaseRetrofitSource
import com.example.messenger.retrofit.source.base.RetrofitConfig

class RetrofitNewsSource(
    config: RetrofitConfig
) : BaseRetrofitSource(config), NewsSource {

    private val newsApi = retrofit.create(NewsApi::class.java)

    override suspend fun sendNews(text: String?, images: List<String>?, voices: List<String>?,
                                  files: List<String>?): String = wrapRetrofitExceptions {
        val sendNewsRequestEntity = SendNewsRequestEntity(text = text, images = images,
            voices = voices, files = files)
        newsApi.sendNews(sendNewsRequestEntity).message
    }

    override suspend fun getNews(pageIndex: Int, pageSize: Int): List<News> = wrapRetrofitExceptions {
        val response = newsApi.getNews(pageIndex, pageSize)
        response.map { it.toNews() }
    }

    override suspend fun editNews(newsId: Int, text: String?, images: List<String>?,
        voices: List<String>?, files: List<String>?): String = wrapRetrofitExceptions {
        val sendNewsRequestEntity = SendNewsRequestEntity(text = text, images = images,
            voices = voices, files = files)
        newsApi.editNews(newsId, sendNewsRequestEntity).message
    }

    override suspend fun deleteNews(newsId: Int): String = wrapRetrofitExceptions {
        newsApi.deleteNews(newsId).message
    }
}