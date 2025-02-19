package com.example.messenger.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.messenger.room.entities.UserDbEntity

@Dao
interface UserDao {
    @Query("SELECT * FROM current_user LIMIT 1")
    suspend fun getUser(): UserDbEntity?

    @Update
    suspend fun updateUser(userDbEntity: UserDbEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(userDbEntity: UserDbEntity)

    @Query("DELETE FROM current_user")
    suspend fun deleteAllUsers()
}