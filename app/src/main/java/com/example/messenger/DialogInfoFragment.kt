package com.example.messenger

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import com.example.messenger.model.Dialog

class DialogInfoFragment(
    private val dialog: Dialog,
    private val lastSessionString : String,
    private val messageViewModel: MessageViewModel // todo убрать
) : BaseInfoFragment() {

    override val viewModel: DialogInfoViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        super.onCreateView(inflater, container, savedInstanceState)
        binding.nickTextView.text = dialog.otherUser.name
        binding.copyImageView.setOnClickListener {
            val clipboard = context?.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("label", dialog.otherUser.name)
            clipboard.setPrimaryClip(clip)
        }
        return binding.root
    }

    override fun getAvatarString(): String = dialog.otherUser.avatar ?: ""

    override fun getUpperName(): String = dialog.otherUser.username

    override fun getSessionName(): String = lastSessionString

    override fun getCanDelete(): Boolean = dialog.canDelete

    override fun getInterval(): Int = dialog.autoDeleteInterval
}