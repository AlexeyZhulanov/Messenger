package com.example.messenger

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.messenger.model.Group
import com.example.messenger.model.LastMessage
import com.example.messenger.model.Message
import com.example.messenger.model.User
import com.example.messenger.states.MessageUi
import com.example.messenger.utils.getParcelableCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class GroupMessageFragment : BaseChatFragment() {

    private lateinit var group: Group
    override val viewModel: GroupMessageViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        group = arguments?.getParcelableCompat<Group>(ARG_GROUP) ?: Group(0, null, "",
            0, null, LastMessage(null, null, null), 0,
            0, false, canDelete = false, 0)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val list = viewModel.fetchMembersList()
        setupAdapter(list)
        lifecycleScope.launch {
            viewModel.fetchMembersList2()
            viewModel.joinGroup()
        }
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.messagesUi.collectLatest { pagingData ->
                    if(pagingData.isNotEmpty()) {
                        Log.d("testPagingFlow", "Submitting paging data")
                        if(viewModel.isFirstPage()) {
                            if(group.unreadCount in 4..29) registerInitialListObserver()
                            adapter.submitList(pagingData)
                        } else {
                            val count = adapter.itemCount
                            adapter.submitList(pagingData)
                            // Перерисовка даты над сообщением
                            adapter.notifyItemChanged(count-1)
                        }
                    } else isStopPagination = true
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
        viewModel.setConvInfo(group.id, 1, group.key ?: "", currentUser.id, requireContext())
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

    override fun isGroup(): Boolean = true

    override fun canDelete(): Boolean = group.canDelete

    override fun getUnreadCount(): Int = group.unreadCount

    override fun markReadCondition(ui: MessageUi): Message? {
        return ui.message.takeIf {
            it.idSender != currentUser.id && it.isPersonalUnread == true
        }
    }

    override fun replaceToInfoFragment() {
        parentFragmentManager.beginTransaction()
            .replace(
                R.id.fragmentContainer,
                GroupInfoFragment.newInstance(group, viewModel.currentMemberList, currentUser),
                "GROUP_INFO_FRAGMENT_TAG"
            )
            .addToBackStack(null)
            .commit()
    }

   companion object {
       private const val ARG_GROUP = "arg_group"

       fun newInstance(group: Group, currentUser: User, isFromNotification: Boolean) = GroupMessageFragment().apply {
           arguments = Bundle().apply {
               putParcelable(ARG_GROUP, group)
               putParcelable(ARG_USER, currentUser)
               putBoolean(ARG_FROM_NOTIFICATION, isFromNotification)
           }
       }
   }
}