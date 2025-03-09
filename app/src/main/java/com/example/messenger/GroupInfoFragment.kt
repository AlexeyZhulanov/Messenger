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
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.io.File

@AndroidEntryPoint
class GroupInfoFragment(
    private val group: Group,
    private val members: List<User>,
    private val currentUser: User
) : BaseInfoFragment() {

    private val alf = ('a'..'z') + ('A'..'Z') + ('0'..'9') + ('А'..'Я') + ('а'..'я') + ('!') + ('$') + (' ')

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

    override fun getCurrentUserId(): Int = currentUser.id

    override fun getGroupOwnerId(): Int = group.createdBy

    override fun deleteUserFromGroup(user: User) {
        showDeleteUserDialog(user.username) {
            lifecycleScope.launch {
                val success = viewModel.deleteUserFromGroup(user.id)
                val txt = if(success) "Участник успешно удален" else "Не удалось удалить"
                Toast.makeText(requireContext(), txt, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showDeleteUserDialog(username: String, onDeleteClick: () -> Unit) {
        AlertDialog.Builder(requireContext())
            .setMessage("Вы уверены, что хотите удалить пользователя $username из группы?")
            .setPositiveButton("Удалить") { _, _ -> onDeleteClick() }
            .setNegativeButton("Назад") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun showAddMember() {
        // Inflate the custom layout for the dialog
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_item, null)

        // Create the AlertDialog
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setPositiveButton("Добавить") { dialogInterface, _ ->
                val input = dialogView.findViewById<EditText>(R.id.dialog_input).text.toString()
                lifecycleScope.launch {
                    viewModel.addMember(group.key, input, currentUser.id) { message ->
                        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                    }
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
                    lifecycleScope.launch {
                        val res = async { filePickerManager.openFilePicker(isCircle = true, isFreeStyleCrop = false, arrayListOf()) }
                        val photo = res.await()
                        if(photo.isNotEmpty()) {
                            val path = async { viewModel.uploadAvatar(File(photo[0].availablePath)) }
                            val success = async { viewModel.updateGroupAvatar(path.await()) }
                            if(success.await()) {
                                Toast.makeText(requireContext(), "Аватарка установлена, полностью перезайдите чтобы увидеть", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    true
                }
                R.id.item_edit_avatar -> {
                    val fileTemp = fileUpdate
                    if (fileTemp != null) {
                        lifecycleScope.launch {
                            val res = async { filePickerManager.openFilePicker(isCircle = true, isFreeStyleCrop = false, arrayListOf(viewModel.fileToLocalMedia(fileTemp))) }
                            val photo = res.await()
                            if(photo.isNotEmpty()) {
                                val path = async { viewModel.uploadAvatar(File(photo[0].availablePath)) }
                                val success = async { viewModel.updateGroupAvatar(path.await()) }
                                if(success.await()) {
                                    Toast.makeText(requireContext(), "Аватарка изменена, полностью перезайдите чтобы увидеть", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    } else {
                        Toast.makeText(requireContext(), "Нельзя редактировать пустоту!", Toast.LENGTH_SHORT).show()
                    }
                    true
                }
                R.id.item_delete_avatar -> {
                    if(group.avatar != null) {
                        lifecycleScope.launch {
                            val success = async { viewModel.updateGroupAvatar("delete") }
                            if(success.await()) {
                                Toast.makeText(requireContext(), "Аватарка удалена, полностью перезайдите чтобы увидеть", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        Toast.makeText(requireContext(), "Аватарки и так нет!", Toast.LENGTH_SHORT).show()
                    }
                    true
                }
                R.id.item_rename -> {
                    showAddDialog(group.name)
                    true
                }
                else -> false
            }
        }
        popupMenu.show()
    }

    private fun showAddDialog(name: String) {
        val dialogView = layoutInflater.inflate(R.layout.group_add_item, null)
        val editText = dialogView.findViewById<EditText>(R.id.group_input)
        editText.setText(name)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setPositiveButton("Изменить") { dialogInterface, _ ->
                lifecycleScope.launch {
                    val input = editText.text.toString()
                    input.forEach {
                        if(it !in alf) {
                            dialogInterface.dismiss()
                            Toast.makeText(requireContext(), "Недопустимые символы в названии", Toast.LENGTH_SHORT).show()
                            return@launch
                        }
                    }
                    val success = async { viewModel.updateGroupName(input) }
                    if(success.await()) Toast.makeText(requireContext(), "Название изменено, полностью перезайдите чтобы увидеть", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Назад") { dialogInterface, _ ->
                dialogInterface.dismiss()
            }
            .create()

        dialog.show()
    }
}