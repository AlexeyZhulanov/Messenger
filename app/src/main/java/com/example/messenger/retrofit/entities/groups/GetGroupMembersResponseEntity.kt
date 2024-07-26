package com.example.messenger.retrofit.entities.groups

import com.example.messenger.model.User
import com.example.messenger.retrofit.entities.messages.UserEntity

data class GetGroupMembersResponseEntity(
    val members: List<UserEntity>
) {
    fun toUsers() : List<User> {
        val list = mutableListOf<User>()
        members.forEach {
            list.add(it.toUser())
        }
        return list
    }
}