package com.example.messenger

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.messenger.model.Dialog
import com.example.messenger.model.LastMessage
import com.example.messenger.model.User
import com.example.messenger.model.getParcelableCompat
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class DialogInfoFragment : BaseInfoFragment() {

    private lateinit var dialog: Dialog
    private var lastSessionString: String = "Unknown"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dialog = arguments?.getParcelableCompat<Dialog>(ARG_DIALOG) ?: Dialog(0, null,
            User(0, "", ""), LastMessage(null, null, null),
            0, 0, false, canDelete = false, 0)
        lastSessionString = requireArguments().getString(ARG_LAST_SESSION) ?: "Unknown"
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewModel.setConvInfo(dialog.id, 0)
        super.onViewCreated(view, savedInstanceState)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        super.onCreateView(inflater, container, savedInstanceState)
        binding.nickTextView.text = dialog.otherUser.name
        binding.lastSessionTextView.text = lastSessionString
        binding.buttonAudio.text = "Голосовые"
        binding.copyImageView.setOnClickListener {
            val clipboard = context?.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("label", dialog.otherUser.name)
            clipboard.setPrimaryClip(clip)
        }
        return binding.root
    }

    override fun getAvatarString(): String = dialog.otherUser.avatar ?: ""

    override fun getUpperName(): String = dialog.otherUser.username

    override fun getIsOwner(): Boolean = dialog.isOwner

    override fun getCanDelete(): Boolean = dialog.canDelete

    override fun getInterval(): Int = dialog.autoDeleteInterval

    override fun getMembers(): List<User> = emptyList()

    override fun getCurrentUserId(): Int = 0 // only for group

    override fun getGroupOwnerId(): Int = 0 // only for group

    override fun deleteUserFromGroup(user: User) {} // only for group

    companion object {
        private const val ARG_DIALOG = "arg_dialog"
        private const val ARG_LAST_SESSION = "arg_last_session"

        fun newInstance(dialog: Dialog, lastSessionString: String) = DialogInfoFragment().apply {
            arguments = Bundle().apply {
                putParcelable(ARG_DIALOG, dialog)
                putString(ARG_LAST_SESSION, lastSessionString)
            }
        }
    }
}