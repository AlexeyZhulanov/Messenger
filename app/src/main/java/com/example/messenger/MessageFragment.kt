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
import com.example.messenger.model.Dialog
import com.example.messenger.model.Message
import com.example.messenger.model.User
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MessageFragment(
    private val dialog: Dialog,
    currentUser: User
) : BaseChatFragment(currentUser) {
    private var lastSessionString: String = ""
    override val viewModel: MessageViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewModel.setInfo(dialog.otherUser.id)
        Log.d("testCurrentUser", currentUser.toString())
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
        viewModel.setMarkScrollListener(binding.recyclerview, adapter)
        viewModel.fetchLastSession()
        val lastSession: TextView = view.findViewById(R.id.lastSessionTextView)
        viewModel.lastSessionString.observe(viewLifecycleOwner) { sessionString ->
            lastSession.text = sessionString
            lastSessionString = sessionString
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        viewModel.setConvInfo(dialog.id, 0)
        return super.onCreateView(inflater, container, savedInstanceState)
    }

    override fun sendTypingEvent(isSend: Boolean) = viewModel.sendTypingEvent(isSend)

    override fun replaceToInfoFragment() {
        parentFragmentManager.beginTransaction()
            .replace(
                R.id.fragmentContainer,
                DialogInfoFragment(dialog, lastSessionString),
                "DIALOG_INFO_FRAGMENT_TAG"
            )
            .addToBackStack(null)
            .commit()
    }

    override fun getAvatarString(): String = dialog.otherUser.avatar ?: ""

    override fun getUpperName(): String = dialog.otherUser.username

    // Дублируется здесь, но в GroupFragment дублирования не будет
    override fun getUserName(): String = dialog.otherUser.username

    override fun rememberLastMessage() {
        if (adapter.itemCount == 0) return

        // Получаем последнее видимое сообщение
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

    override fun replaceCurrentFragment() = replaceFragment(MessageFragment(dialog, currentUser))

    override fun composeAnswer(message: Message) {
        binding.answerUsername.text = dialog.otherUser.username
        answerMessage = Pair(message.id, dialog.otherUser.username)
    }

    override fun getMembers(): List<User> = emptyList()
    override fun setupAdapterDialog() {
        setupAdapter(emptyList())
    }

    override fun isGroup(): Boolean = false
}
