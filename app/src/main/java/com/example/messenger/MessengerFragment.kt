package com.example.messenger

import android.annotation.SuppressLint
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.example.messenger.databinding.FragmentMessengerBinding
import com.example.messenger.model.Conversation
import com.example.messenger.model.Message
import com.example.messenger.model.User
import com.example.messenger.model.chunkedFlowLast
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.launch
import java.io.File

@AndroidEntryPoint
class MessengerFragment : Fragment() {
    private lateinit var binding: FragmentMessengerBinding
    private lateinit var adapter: MessengerAdapter
    private var currentUser: User? = null
    private var forwardFlag: Boolean = false
    private var forwardMessages: List<Message>? = null
    private var forwardUsernames: List<String>? = null
    private var uriGlobal: Uri? = null
    private val messengerViewModel: MessengerViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        parentFragmentManager.setFragmentResultListener("forwardMessagesRequestKey", viewLifecycleOwner) { _, bundle ->
            val messages: ArrayList<Message>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                bundle.getParcelableArrayList("forwardedMessages", Message::class.java)
            } else {
                @Suppress("DEPRECATION")
                bundle.getParcelableArrayList("forwardedMessages")
            }
            val usernames: ArrayList<String>? = bundle.getStringArrayList("forwardedUsernames")
            forwardMessages = messages
            forwardUsernames = usernames
            forwardFlag = true
        }
        requireActivity().window.statusBarColor = ContextCompat.getColor(requireContext(), R.color.colorBar)
        val toolbarContainer: FrameLayout = view.findViewById(R.id.toolbar_container)
        val defaultToolbar = LayoutInflater.from(context)
            .inflate(R.layout.toolbar_custom, toolbarContainer, false)
        toolbarContainer.addView(defaultToolbar)
        val avatarImageView: ImageView = view.findViewById(R.id.toolbar_avatar)
        avatarImageView.setOnClickListener {
            goToSettingsFragment()
        }
        val titleTextView: TextView = view.findViewById(R.id.toolbar_title)
        titleTextView.setOnClickListener {
            goToSettingsFragment()
        }
        val addImageView: ImageView = view.findViewById(R.id.ic_add)
        addImageView.setOnClickListener {
            showPopupMenu(it, R.menu.popup_menu_add)
        }
        messengerViewModel.currentUser.observe(viewLifecycleOwner) { user ->
            if(user.publicKey == null) {
                // Если при создании ключей пользователь не отправил их на сервер, то отправляем его на релогин
                logout()
            }
            currentUser = user
            lifecycleScope.launch {
                val avatar = user?.avatar ?: ""
                if (avatar != "") {
                    val filePathTemp = async {
                        if (messengerViewModel.fManagerIsExistAvatar(avatar)) {
                            return@async Pair(messengerViewModel.fManagerGetAvatarPath(avatar), true)
                        } else {
                            try {
                                return@async Pair(messengerViewModel.downloadAvatar(requireContext(), avatar), false)
                            } catch (e: Exception) {
                                return@async Pair(null, true)
                            }
                        }
                    }
                    val (first, second) = filePathTemp.await()
                    if (first != null) {
                        val file = File(first)
                        if (file.exists()) {
                            if (!second) messengerViewModel.fManagerSaveAvatar(avatar, file.readBytes())
                            val uri = Uri.fromFile(file)
                            uriGlobal = uri
                            avatarImageView.imageTintList = null
                            Glide.with(requireContext())
                                .load(uri)
                                .apply(RequestOptions.circleCropTransform())
                                .diskCacheStrategy(DiskCacheStrategy.ALL)
                                .into(avatarImageView)
                        }
                    }
                }
            }
        }
        observeViewModel()
    }

    @SuppressLint("DiscouragedApi")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentMessengerBinding.inflate(inflater, container, false)

        binding.button.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, NewsFragment(uriGlobal, currentUser), "NEWS_FRAGMENT_TAG")
                .addToBackStack(null)
                .commit()
        }
        if(messengerViewModel.isNeedFetchConversations) messengerViewModel.fetchConversations() // При повторном заходе во фрагмент
        setupRecyclerView()
        messengerViewModel.stopNotifications(true)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        messengerViewModel.isNeedFetchConversations = true
        messengerViewModel.stopNotifications(false)
    }

    private fun observeViewModel() {
        messengerViewModel.vacation.observe(viewLifecycleOwner) { vacation ->
            if(vacation != null) {
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragmentContainer, VacationFragment(vacation.first, vacation.second), "VACATION_FRAGMENT_TAG")
                    .commit()
            }
        }
        messengerViewModel.conversations.observe(viewLifecycleOwner) { conversations ->
            Log.d("testConvFetch", "Conversations Fetched")
            adapter.submitList(conversations)
        }
        lifecycleScope.launch {
            messengerViewModel.newMessageFlow
                .buffer()
                .chunkedFlowLast(200) // custom func
                .collect { newMessages ->
                    if(newMessages.isNotEmpty()) {
                        adapter.updateConversations(newMessages)
                    }
                }
        }
    }

    private fun setupRecyclerView() {
        adapter = MessengerAdapter(messengerViewModel, requireContext(), object : MessengerActionListener {
            override fun onConversationClicked(conversation: Conversation, index: Int) {
                when (conversation.type) {
                    "dialog" -> {
                        if (!forwardFlag) {
                            parentFragmentManager.beginTransaction()
                                .replace(R.id.fragmentContainer, MessageFragment(conversation.toDialog(), currentUser ?: User(0, "", ""), false), "MESSAGE_FRAGMENT_TAG")
                                .addToBackStack(null)
                                .commit()
                        } else {
                            forwardFlag = false
                            if(forwardMessages != null) {
                                val list = forwardMessages
                                val list2 = forwardUsernames
                                messengerViewModel.forwardMessages(list, list2, conversation.toDialog().id) { success ->
                                    val textMessage = if(success) "Сообщения успешно пересланы" else "Не удалось переслать сообщения, возможно нет ключа шифрования, либо нет сети"
                                    Toast.makeText(requireContext(), textMessage, Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                    "group" -> {
                        if (!forwardFlag) {
                            parentFragmentManager.beginTransaction()
                                .replace(R.id.fragmentContainer, GroupMessageFragment(conversation.toGroup(), currentUser ?: User(0, "", ""), false), "GROUP_FRAGMENT_TAG")
                                .addToBackStack(null)
                                .commit()
                        } else {
                            forwardFlag = false
                            if(forwardMessages != null) {
                                val list = forwardMessages
                                val list2 = forwardUsernames
                                messengerViewModel.forwardGroupMessages(list, list2, conversation.toGroup().id) { success ->
                                    val textMessage = if(success) "Сообщения успешно пересланы" else "Не удалось переслать сообщения, возможно нет ключа шифрования, либо нет сети"
                                    Toast.makeText(requireContext(), textMessage, Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                    else -> {
                        Toast.makeText(requireContext(), "Unknown conversation type", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
        binding.recyclerview.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerview.adapter = adapter
    }

    private fun showPopupMenu(view: View, menuRes: Int) {
        val popupMenu = PopupMenu(requireContext(), view)
        popupMenu.menuInflater.inflate(menuRes, popupMenu.menu)
        popupMenu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menu_item3 -> {
                    showAddDialog()
                    true
                }
                R.id.menu_item4 -> {
                    showAddGroup()
                    true
                }
                else -> false
            }
        }
        popupMenu.show()
    }

    private fun logout() {
        lifecycleScope.launch {
            val success = messengerViewModel.deleteFCMToken()
            if(success) {
                messengerViewModel.clearCurrentUser()
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragmentContainer, LoginFragment(), "LOGIN_FRAGMENT_TAG3")
                    .commit()
            } else Toast.makeText(requireContext(), "Не удалось выйти из аккаунта, нет сети", Toast.LENGTH_SHORT).show()
        }
    }

    private fun goToSettingsFragment() {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, SettingsFragment(currentUser ?: User(0, "", "")), "SETTINGS_FRAGMENT_TAG")
            .addToBackStack(null)
            .commit()
    }

    private fun showAddDialog() {
        // Inflate the custom layout for the dialog
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_item, null)

        // Create the AlertDialog
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setPositiveButton("Добавить") { dialogInterface, _ ->
                val input = dialogView.findViewById<EditText>(R.id.dialog_input).text.toString()
                messengerViewModel.createDialog(input, currentUser?.publicKey) { message ->
                    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                }
                dialogInterface.dismiss()
            }
            .setNegativeButton("Назад") { dialogInterface, _ ->
                dialogInterface.dismiss()
            }
            .create()

        dialog.show()
    }

    private fun showAddGroup() {
        // Inflate the custom layout for the dialog
        val dialogView = layoutInflater.inflate(R.layout.group_add_item, null)

        // Create the AlertDialog
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setPositiveButton("Создать") { dialogInterface, _ ->
                val input = dialogView.findViewById<EditText>(R.id.group_input).text.toString()
                messengerViewModel.createGroup(input, currentUser?.publicKey) { message ->
                    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                }
                dialogInterface.dismiss()
            }
            .setNegativeButton("Назад") { dialogInterface, _ ->
                dialogInterface.dismiss()
            }
            .create()

        dialog.show()
    }
}