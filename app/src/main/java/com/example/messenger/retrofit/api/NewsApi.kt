package com.example.messenger.retrofit.api

import com.example.messenger.retrofit.entities.ResponseEntityMessageAnswer
import com.example.messenger.retrofit.entities.news.News
import com.example.messenger.retrofit.entities.news.SendNewsRequestEntity
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface NewsApi {
    @POST("news")
    suspend fun sendNews(
        @Body sendNewsRequestEntity: SendNewsRequestEntity
    ) : ResponseEntityMessageAnswer

    @GET("news")
    suspend fun getNews(
        @Query("page") pageIndex: Int,
        @Query("size") pageSize: Int
    ) : List<News>

    @PUT("news/{news_id}")
    suspend fun editNews(
        @Path("news_id") newsId: Int,
        @Body sendNewsRequestEntity: SendNewsRequestEntity
    ) : ResponseEntityMessageAnswer

    @DELETE("news/{news_id}")
    suspend fun deleteNews(
        @Path("news_id") newsId: Int
    ) : ResponseEntityMessageAnswer
}