package com.example.messenger.retrofit.source.base

import com.example.messenger.retrofit.source.groups.GroupsSource
import com.example.messenger.retrofit.source.messages.MessagesSource
import com.example.messenger.retrofit.source.users.UsersSource

interface SourcesProvider {
    fun getUsersSource() : UsersSource

    fun getMessagesSource() : MessagesSource

    fun getGroupsSource() : GroupsSource
}