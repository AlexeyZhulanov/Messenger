package com.example.messenger.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.messenger.room.entities.NewsDbEntity

@Dao
interface NewsDao {
    @Transaction
    @Query("SELECT * FROM news ORDER BY timestamp DESC")
    suspend fun getNews(): List<NewsDbEntity>

    @Transaction
    suspend fun replaceNews(newNews: List<NewsDbEntity>) {
        deleteAllNews()
        insertNews(newNews)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNews(news: List<NewsDbEntity>)

    @Query("DELETE FROM news")
    suspend fun deleteAllNews()
}