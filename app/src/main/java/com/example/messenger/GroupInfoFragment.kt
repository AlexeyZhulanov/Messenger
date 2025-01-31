package com.example.messenger

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.lifecycle.lifecycleScope
import com.example.messenger.model.Group
import com.example.messenger.model.MediaItem
import com.example.messenger.model.User
import com.google.android.material.floatingactionbutton.FloatingActionButton
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class GroupInfoFragment(
    private val group: Group,
    private val members: List<User>
) : BaseInfoFragment() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewModel.setConvInfo(group.id, 1)
        super.onViewCreated(view, savedInstanceState)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val size = members.size
        selectedType = 3
        super.onCreateView(inflater, container, savedInstanceState)
        loadMoreMediaItems(3, 0) {}
        isCanDoPagination = false
        with(binding) {
            nickTextView.visibility = View.GONE
            nickWordTextView.visibility = View.GONE
            copyImageView.visibility = View.GONE
            loadButton.visibility = View.GONE
            addMembersTextView.visibility = View.VISIBLE
            addUsersImageView.visibility = View.VISIBLE
            buttonMembers.visibility = View.VISIBLE
            floatingActionButtonOptions.visibility = View.VISIBLE
            buttonAudio.text = getString(R.string.audioGroupText)
            buttonMedia.setTextColor(colorAccent)
            buttonMembers.setOnClickListener {
                if(selectedType != MediaItem.TYPE_USER) {
                    loadMoreMediaItems(3, 0) { success ->
                        if (success) {
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
            floatingActionButtonOptions.setOnClickListener {
                showPopupMenu(floatingActionButtonOptions)
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

    override fun getUpperName(): String = group.name

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
                lifecycleScope.launch {
                    val success = viewModel.addMember(input)
                    val str = if(success) "Пользователь успешно добавлен" else "Не удалось добавить"
                    Toast.makeText(requireContext(), str, Toast.LENGTH_SHORT).show()
                }
                dialogInterface.dismiss()
            }
            .setNegativeButton("Назад") { dialogInterface, _ ->
                dialogInterface.dismiss()
            }
            .create()

        dialog.show()
    }

    private fun showPopupMenu(fab: FloatingActionButton) {
        val popupMenu = PopupMenu(requireContext(), fab)
        popupMenu.menuInflater.inflate(R.menu.popup_menu_group_info, popupMenu.menu)
        popupMenu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.item_new_avatar -> {
                    true
                }
                R.id.item_edit_avatar -> {
                    true
                }
                R.id.item_delete_avatar -> {
                    true
                }
                R.id.item_rename -> {
                    // todo проверка на алфавит
                    true
                }
                else -> false
            }
        }
        popupMenu.show()
    }
}