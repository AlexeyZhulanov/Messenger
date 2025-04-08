package com.example.messenger

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.example.messenger.databinding.DialogGitlabSettingsBinding
import com.example.messenger.model.Repo


interface OnSaveButtonGitlabClickListener {
    fun onSaveButtonClicked(id: Int, list: List<Boolean?>)
}

class SaveGitlabSettingsDialogFragment : DialogFragment() {

    private lateinit var binding: DialogGitlabSettingsBinding
    private var saveButtonClickListener: OnSaveButtonGitlabClickListener? = null
    private var repo: Repo? = null

    fun setSaveButtonClickListener(listener: OnSaveButtonGitlabClickListener) {
        this.saveButtonClickListener = listener
    }

    fun setRepo(repo: Repo) {
        this.repo = repo
    }

    override fun onStart() {
        super.onStart()
        // Настройка размеров диалога
        val width = resources.displayMetrics.widthPixels
        val height = ViewGroup.LayoutParams.WRAP_CONTENT
        dialog?.window?.setLayout(width, height)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = DialogGitlabSettingsBinding.inflate(inflater, container, false)
        with(binding) {
            hookPushTextView.isChecked = repo?.isHookPush ?: false
            hookMergeTextView.isChecked = repo?.isHookMerge ?: false
            hookTagTextView.isChecked = repo?.isHookTag ?: false
            hookIssueTextView.isChecked = repo?.isHookIssue ?: false
            hookNoteTextView.isChecked = repo?.isHookNote ?: false
            hookReleaseTextView.isChecked = repo?.isHookRelease ?: false

            hookPushTextView.setOnClickListener { hookPushTextView.toggle() }
            hookMergeTextView.setOnClickListener { hookMergeTextView.toggle() }
            hookTagTextView.setOnClickListener { hookTagTextView.toggle() }
            hookIssueTextView.setOnClickListener { hookIssueTextView.toggle() }
            hookNoteTextView.setOnClickListener { hookNoteTextView.toggle() }
            hookReleaseTextView.setOnClickListener { hookReleaseTextView.toggle() }

            saveButton.setOnClickListener {
                saveButtonClickListener?.onSaveButtonClicked(repo?.projectId ?: -1, listOf(
                    if(repo?.isHookPush != hookPushTextView.isChecked) hookPushTextView.isChecked else null,
                    if(repo?.isHookMerge != hookMergeTextView.isChecked) hookMergeTextView.isChecked else null,
                    if(repo?.isHookTag != hookTagTextView.isChecked) hookTagTextView.isChecked else null,
                    if(repo?.isHookIssue != hookIssueTextView.isChecked) hookIssueTextView.isChecked else null,
                    if(repo?.isHookNote != hookNoteTextView.isChecked) hookNoteTextView.isChecked else null,
                    if(repo?.isHookRelease != hookReleaseTextView.isChecked) hookReleaseTextView.isChecked else null
                ))
                dismiss()
            }
        }
        return binding.root
    }
}