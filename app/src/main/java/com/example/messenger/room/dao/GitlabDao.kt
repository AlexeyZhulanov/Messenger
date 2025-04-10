package com.example.messenger.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.messenger.room.entities.GitlabDbEntity

@Dao
interface GitlabDao {
    @Transaction
    @Query("SELECT * FROM gitlab")
    suspend fun getRepos(): List<GitlabDbEntity>

    @Transaction
    suspend fun replaceRepos(newRepos: List<GitlabDbEntity>) {
        deleteAllRepos()
        insertRepos(newRepos)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRepos(repos: List<GitlabDbEntity>)

    @Query("DELETE FROM gitlab")
    suspend fun deleteAllRepos()
}