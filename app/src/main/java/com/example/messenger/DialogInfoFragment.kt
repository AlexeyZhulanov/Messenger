package com.example.messenger

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.messenger.model.Dialog
import com.example.messenger.model.User
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class DialogInfoFragment(
    private val dialog: Dialog,
    private val lastSessionString : String
) : BaseInfoFragment() {

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
}