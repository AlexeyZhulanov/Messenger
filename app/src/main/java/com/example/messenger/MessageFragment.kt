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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.messenger.model.Dialog
import com.example.messenger.model.LastMessage
import com.example.messenger.model.Message
import com.example.messenger.model.User
import com.example.messenger.states.MessageUi
import com.example.messenger.utils.getParcelableCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MessageFragment : BaseChatFragment() {

    private lateinit var dialog: Dialog
    private var lastSessionString: String = ""
    override val viewModel: MessageViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dialog = arguments?.getParcelableCompat<Dialog>(ARG_DIALOG) ?: Dialog(0, null,
            User(0, "", ""), LastMessage(null, null, null),
            0, 0, false, canDelete = false, 0)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewModel.setInfo(dialog.otherUser.id)
        Log.d("testCurrentUser", currentUser.toString())
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.messagesUi.collectLatest { pagingData ->
                    if(pagingData.isNotEmpty()) {
                        Log.d("testPagingFlow", "Submitting paging data")
                        if(viewModel.isFirstPage()) {
                            if(dialog.unreadCount in 4..29) registerInitialListObserver()
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
        setMarkScrollListener()
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
                DialogInfoFragment.newInstance(dialog, lastSessionString),
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

    override fun markReadCondition(ui: MessageUi): Message? {
        return ui.message.takeIf {
            it.idSender == dialog.otherUser.id && !it.isRead
        }
    }

    companion object {
        private const val ARG_DIALOG = "arg_dialog"

        fun newInstance(dialog: Dialog, currentUser: User, isFromNotification: Boolean) = MessageFragment().apply {
            arguments = Bundle().apply {
                putParcelable(ARG_DIALOG, dialog)
                putParcelable(ARG_USER, currentUser)
                putBoolean(ARG_FROM_NOTIFICATION, isFromNotification)
            }
        }
    }
}
