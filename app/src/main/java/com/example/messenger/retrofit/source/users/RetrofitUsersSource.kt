package com.example.messenger.retrofit.source.users

import com.example.messenger.retrofit.api.UsersApi
import com.example.messenger.retrofit.entities.users.LoginRequestEntity
import com.example.messenger.retrofit.entities.users.RegisterRequestEntity
import com.example.messenger.retrofit.entities.users.UpdatePasswordRequestEntity
import com.example.messenger.retrofit.entities.users.UpdateProfileRequestEntity
import com.example.messenger.retrofit.source.base.BaseRetrofitSource
import com.example.messenger.retrofit.source.base.RetrofitConfig

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
        usersApi.login(loginRequestEntity).accessToken
    }

    override suspend fun updateProfile(username: String?, avatar: String?): String = wrapRetrofitExceptions {
        val updateProfileRequestEntity = UpdateProfileRequestEntity(username = username, avatar = avatar)
        usersApi.updateProfile(updateProfileRequestEntity).message
    }

    override suspend fun updatePassword(password: String): String = wrapRetrofitExceptions {
        val updatePasswordRequestEntity = UpdatePasswordRequestEntity(password = password)
        usersApi.updatePassword(updatePasswordRequestEntity).message
    }

    override suspend fun updateLastSession(): String = wrapRetrofitExceptions {
        usersApi.updateLastSession().message
    }

    override suspend fun getLastSession(userId: Int): Long = wrapRetrofitExceptions {
        usersApi.getLastSession(userId).timestamp!!
    }
}