package com.example.messenger.retrofit.source.users

import com.example.messenger.retrofit.api.UsersApi
import com.example.messenger.retrofit.entities.users.LoginRequestEntity
import com.example.messenger.retrofit.entities.users.RegisterRequestEntity
import com.example.messenger.retrofit.entities.users.UpdatePasswordRequestEntity
import com.example.messenger.retrofit.entities.users.UpdateProfileRequestEntity
import com.example.messenger.retrofit.source.BaseRetrofitSource
import com.example.messenger.retrofit.source.RetrofitConfig

class RetrofitUsersSource(
    config: RetrofitConfig
) : BaseRetrofitSource(config), UsersSource {

    private val usersApi = retrofit.create(UsersApi::class.java)
    override suspend fun register(name: String, username: String, password: String): String = wrapRetrofitExceptions {
        val registerRequestEntity = RegisterRequestEntity(name = name, username = username, password = password)
        usersApi.register(registerRequestEntity).message
    }

    override suspend fun login(name: String, password: String): String = wrapRetrofitExceptions {
        val loginRequestEntity = LoginRequestEntity(name = name, password = password)
        usersApi.login(loginRequestEntity).token
    }

    override suspend fun updateProfile(token: String, username: String?, avatar: String?): String = wrapRetrofitExceptions {
        val updateProfileRequestEntity = UpdateProfileRequestEntity(username = username, avatar = avatar)
        usersApi.updateProfile(token, updateProfileRequestEntity).message
    }

    override suspend fun updatePassword(token: String, password: String): String = wrapRetrofitExceptions {
        val updatePasswordRequestEntity = UpdatePasswordRequestEntity(password = password)
        usersApi.updatePassword(token, updatePasswordRequestEntity).message
    }

    override suspend fun updateLastSession(token: String): String = wrapRetrofitExceptions {
        usersApi.updateLastSession(token).message
    }

    override suspend fun getLastSession(token: String, userId: Int): Long = wrapRetrofitExceptions {
        usersApi.getLastSession(userId, token).timestamp!!
    }
}