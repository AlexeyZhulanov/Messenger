package com.example.messenger.retrofit.source.news

import com.example.messenger.model.News

interface NewsSource {

    suspend fun sendNews(text: String? = null, images: List<String>? = null,
                         voices: List<String>? = null, files: List<String>? = null) : String

    suspend fun getNews(pageIndex: Int, pageSize: Int) : List<News>

    suspend fun editNews(newsId: Int, text: String? = null, images: List<String>? = null,
                            voices: List<String>? = null, files: List<String>? = null) : String

    suspend fun deleteNews(newsId: Int) : String
}