package com.example.messenger.retrofit.entities.groups

import com.example.messenger.retrofit.entities.messages.UserEntity

data class GetGroupMembersResponseEntity(
    val members: List<UserEntity>
)