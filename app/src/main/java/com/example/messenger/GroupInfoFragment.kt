package com.example.messenger

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.viewModels
import com.example.messenger.model.Group
import com.example.messenger.model.MediaItem
import com.example.messenger.model.User

class GroupInfoFragment(
    private val group: Group,
    private val groupName : String,
    private val members: List<User>
) : BaseInfoFragment() {
    override val viewModel: GroupInfoViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewModel.setConvInfo(group.id)
        super.onViewCreated(view, savedInstanceState)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        super.onCreateView(inflater, container, savedInstanceState)
        val size = members.size
        with(binding) {
            nickTextView.visibility = View.GONE
            nickWordTextView.visibility = View.GONE
            copyImageView.visibility = View.GONE
            loadButton.visibility = View.GONE
            addMembersTextView.visibility = View.VISIBLE
            addUsersImageView.visibility = View.VISIBLE
            buttonMembers.visibility = View.VISIBLE
            buttonMembers.setOnClickListener {
                loadMoreMediaItems(3, 0) { success ->
                    if(success) {
                        if(selectedType != MediaItem.TYPE_USER) {
                            buttonMedia.setTextColor(colorAccent)
                            buttonFiles.setTextColor(colorAccent)
                            buttonAudio.setTextColor(colorAccent)
                            buttonMembers.setTextColor(colorPrimary)
                            selectedType = 3
                            currentPage = 1
                            isCanDoPagination = false
                        }
                    }
                }
            }
            addUsersImageView.setOnClickListener {
                showAddMember()
            }
            lastSessionTextView.text = when(size) {
                0 -> ""
                1 -> "1 участник"
                in 2..4 -> "$size участника"
                else -> "$size участников"
            }
        }

        return binding.root
    }

    override fun getAvatarString(): String = group.avatar ?: ""

    override fun getUpperName(): String = groupName

    override fun getCanDelete(): Boolean = group.canDelete

    override fun getInterval(): Int = group.autoDeleteInterval

    override fun getMembers(): List<User> = members

    private fun showAddMember() {
        // Inflate the custom layout for the dialog
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_item, null)

        // Create the AlertDialog
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setPositiveButton("Добавить") { dialogInterface, _ ->
                val input = dialogView.findViewById<EditText>(R.id.dialog_input).text.toString()
                // todo viewmodel call
                dialogInterface.dismiss()
            }
            .setNegativeButton("Назад") { dialogInterface, _ ->
                dialogInterface.dismiss()
            }
            .create()

        dialog.show()
    }
}