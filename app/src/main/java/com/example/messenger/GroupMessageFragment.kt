package com.example.messenger

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.messenger.model.Group
import com.example.messenger.model.User
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class GroupMessageFragment(
    private val group: Group,
    currentUser: User
) : BaseChatFragment(currentUser) {

    override val viewModel: GroupMessageViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val lastSession: TextView = view.findViewById(R.id.lastSessionTextView)
        lifecycleScope.launch {
            viewModel.membersCount.collectLatest {
                lastSession.text = when(it) {
                    0 -> "" // если не удалось получить, то не отображаем
                    1 -> "1 участник"
                    in 2..4 -> "$it участника"
                    else -> "$it участников"
                }
            }
        }
        viewModel.fetchMembersList()
    }

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