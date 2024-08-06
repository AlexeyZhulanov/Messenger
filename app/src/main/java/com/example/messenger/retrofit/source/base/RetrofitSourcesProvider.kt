package com.example.messenger.retrofit.source.base

import com.example.messenger.retrofit.source.groups.GroupsSource
import com.example.messenger.retrofit.source.groups.RetrofitGroupsSource
import com.example.messenger.retrofit.source.messages.MessagesSource
import com.example.messenger.retrofit.source.messages.RetrofitMessagesSource
import com.example.messenger.retrofit.source.uploads.RetrofitUploadsSource
import com.example.messenger.retrofit.source.uploads.UploadsSource
import com.example.messenger.retrofit.source.users.RetrofitUsersSource
import com.example.messenger.retrofit.source.users.UsersSource

class RetrofitSourcesProvider(
    private val config: RetrofitConfig
) : SourcesProvider {
    override fun getUsersSource(): UsersSource {
        return RetrofitUsersSource(config)
    }

    override fun getMessagesSource(): MessagesSource {
        return RetrofitMessagesSource(config)
    }

    override fun getGroupsSource(): GroupsSource {
        return RetrofitGroupsSource(config)
    }

    override fun getUploadsSource(): UploadsSource {
        return RetrofitUploadsSource(config)
    }
}