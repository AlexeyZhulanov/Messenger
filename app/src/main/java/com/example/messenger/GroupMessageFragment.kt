package com.example.messenger

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.messenger.model.Group
import com.example.messenger.model.Message
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
        val list = viewModel.fetchMembersList()
        setupAdapter(list)
        lifecycleScope.launch {
            viewModel.fetchMembersList2()
            viewModel.joinGroup()
        }
        super.onViewCreated(view, savedInstanceState)
        lifecycleScope.launch {
            viewModel.pagingDataFlow.collectLatest { pagingData ->
                if(pagingData.isNotEmpty()) {
                    Log.d("testPagingFlow", "Submitting paging data")
                    if(viewModel.isFirstPage()) {
                        val mes = viewModel.getUnsentMessages()
                        val summaryPagingData = if(mes != null) {
                            val pair = mes.map { Pair(it, "") }
                            pair + pagingData
                        } else pagingData
                        adapter.submitList(summaryPagingData)
                        val firstItem = pagingData.firstOrNull()?.first
                        if(firstItem != null) viewModel.updateLastDate(firstItem.timestamp)
                    }
                    else {
                        val updatedList = adapter.currentList.toMutableList()
                        updatedList.addAll(pagingData)
                        adapter.submitList(updatedList)
                    }
                } else {
                    isStopPagination = true
                }
            }
        }
        lifecycleScope.launch {
            val lastReadMessageId = viewModel.getLastMessageId()
            if(lastReadMessageId != -1) {
                val position = findPositionById(lastReadMessageId)
                if(position != -1) {
                    binding.recyclerview.scrollToPosition(position)
                } else {
                    val pos = viewModel.getPreviousMessageId(lastReadMessageId)
                    if(pos != -1) binding.recyclerview.scrollToPosition(pos)
                }
            }
        }
        viewModel.setMarkScrollListener(binding.recyclerview, adapter, currentUser.id)
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
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        viewModel.setConvInfo(group.id, 1)
        return super.onCreateView(inflater, container, savedInstanceState)
    }

    override fun composeAnswer(message: Message) {
        val username = viewModel.currentMemberList.find { it.id == message.idSender }?.username
        binding.answerUsername.text = username ?: ""
        answerMessage = Pair(message.id, username ?: "")
    }

    override fun getAvatarString(): String = group.avatar ?: ""

    override fun getUpperName(): String = group.name

    override fun getUserName(): String = "" // func only for dialog

    override fun getMembers(): List<User> {
        val list = viewModel.currentMemberList
        Log.d("testMemberList", list.toString())
        return list
    }

    override fun setupAdapterDialog() {}

    override fun sendTypingEvent(isSend: Boolean) = viewModel.sendTypingEvent(isSend)

    //override fun getConvId(): Int = group.id

    //override fun getIsGroup(): Int = 1

    override fun replaceToInfoFragment() {
        parentFragmentManager.beginTransaction()
            .replace(
                R.id.fragmentContainer,
                GroupInfoFragment(group, viewModel.currentMemberList),
                "GROUP_INFO_FRAGMENT_TAG"
            )
            .addToBackStack(null)
            .commit()
    }

    override fun replaceCurrentFragment() = replaceFragment(GroupMessageFragment(group, currentUser))

    override fun rememberLastMessage() {
        if (adapter.itemCount == 0) return

        val layoutManager = binding.recyclerview.layoutManager as LinearLayoutManager
        val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()
        val firstMessageId = if (firstVisibleItemPosition != RecyclerView.NO_POSITION && firstVisibleItemPosition < 25) {
            val firstMessage = adapter.getItemNotProtected(firstVisibleItemPosition).first
            if(firstMessage.isRead) {
                adapter.getItemNotProtected(0).first.id
            } else {
                firstMessage.id
            }
        } else {
            adapter.getItemNotProtected(0).first.id
        }
        viewModel.saveLastMessage(firstMessageId)
    }

}