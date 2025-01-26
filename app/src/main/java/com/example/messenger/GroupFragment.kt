package com.example.messenger

import androidx.fragment.app.viewModels
import com.example.messenger.model.Group
import com.example.messenger.model.User
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class GroupFragment(
    private val group: Group,
    currentUser: User
) : BaseChatFragment(currentUser) {

    override val viewModel: GroupMessageViewModel by viewModels()

    override fun composeAnswer(messageId: Int) {
        TODO("Not yet implemented")
    }

    override fun getAvatarString(): String {
        TODO("Not yet implemented")
    }

    override fun getUpperName(): String {
        TODO("Not yet implemented")
    }

    override fun getUserName(): String {
        TODO("Not yet implemented")
    }

    override fun sendTypingEvent(isSend: Boolean) {
        TODO("Not yet implemented")
    }

    override fun replaceToInfoFragment() {
        TODO("Not yet implemented")
    }

    override fun replaceCurrentFragment() {
        TODO("Not yet implemented")
    }

    override fun rememberLastMessage() {
        TODO("Not yet implemented")
    }

}