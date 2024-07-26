package com.example.messenger.retrofit.entities.messages

import com.example.messenger.model.UserShort

data class GetUsersResponseEntity(
    val users: List<User>
) {
    fun toUsersShort() : List<UserShort> {
        val list = mutableListOf<UserShort>()
        users.forEach {
            list.add(it.toUserShort())
        }
        return list
    }
}

data class User(
    val id: Int,
    val name: String
) {
    fun toUserShort() : UserShort = UserShort(id = id, name = name)
}