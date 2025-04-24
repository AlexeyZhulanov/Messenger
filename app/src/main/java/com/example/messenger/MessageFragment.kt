package com.example.messenger

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.messenger.model.Dialog
import com.example.messenger.model.Message
import com.example.messenger.model.User
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MessageFragment(
    private val dialog: Dialog,
    currentUser: User,
    isFromNotification: Boolean
) : BaseChatFragment(currentUser, isFromNotification) {
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
                        if(dialog.unreadCount in 4..29) registerInitialListObserver()
                        val mes = viewModel.getUnsentMessages()
                        val summaryPagingData = if(mes != null) {
                            val pair = mes.map { Triple(it, "", "") }
                            pair + pagingData
                        } else pagingData
                        adapter.submitList(summaryPagingData)
                        val firstItem = pagingData.firstOrNull()?.first
                        if(firstItem != null) viewModel.updateLastDate(firstItem.timestamp)
                    }
                    else {
                        val updatedList = adapter.currentList.toMutableList()
                        updatedList.addAll(pagingData)
                        viewModel.processDateDuplicates(updatedList)
                        adapter.submitList(updatedList)
                    }
                } else {
                    isStopPagination = true
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
        viewModel.setConvInfo(dialog.id, 0, dialog.key ?: "", currentUser.id, requireContext())
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

    override fun composeAnswer(message: Message) {
        binding.answerUsername.text = dialog.otherUser.username
        answerMessage = Pair(message.id, dialog.otherUser.username)
    }

    override fun getMembers(): List<User> = emptyList()
    override fun setupAdapterDialog() {
        setupAdapter(emptyList())
    }

    override fun isGroup(): Boolean = false

    override fun canDelete(): Boolean = dialog.canDelete

    override fun getUnreadCount(): Int = dialog.unreadCount
}
