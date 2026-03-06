package com.example.messenger

import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.messenger.databinding.FragmentMessengerBinding
import com.example.messenger.model.Conversation
import com.example.messenger.model.Message
import com.example.messenger.model.User
import com.example.messenger.states.AvatarState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
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
        }
        observeViewModel(avatarImageView)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentMessengerBinding.inflate(inflater, container, false)

        WindowCompat.setDecorFitsSystemWindows(requireActivity().window, false)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            binding.statusBarScrim.layoutParams.height = systemBars.top
            binding.navigationBarScrim.layoutParams.height = systemBars.bottom

            binding.statusBarScrim.requestLayout()
            binding.navigationBarScrim.requestLayout()

            insets
        }

        val typedValue = TypedValue()
        requireActivity().theme.resolveAttribute(R.attr.colorBar, typedValue, true)
        val colorBar = typedValue.data
        binding.statusBarScrim.setBackgroundColor(colorBar)
        binding.navigationBarScrim.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.navigation_bar_color))

        binding.button.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, NewsFragment.newInstance(uriGlobal, currentUser), "NEWS_FRAGMENT_TAG")
                .addToBackStack(null)
                .commit()
        }
        binding.button4.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, GitlabFragment.newInstance(uriGlobal, currentUser), "GITLAB_FRAGMENT_TAG")
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

    private fun observeViewModel(avatarImageView: ImageView) {
        messengerViewModel.vacation.observe(viewLifecycleOwner) { vacation ->
            if(vacation != null) {
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragmentContainer, VacationFragment.newInstance(vacation.first, vacation.second), "VACATION_FRAGMENT_TAG")
                    .commit()
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    messengerViewModel.conversationsUi.collect { ui ->
                        Log.d("testConvFetch", "Conversations Fetched")
                        adapter.submitList(ui)
                    }
                }
                launch {
                    messengerViewModel.userAvatar.collectLatest { state ->
                        if(state is AvatarState.Ready) {
                            val file = File(state.localPath)
                            uriGlobal = Uri.fromFile(file)
                            avatarImageView.imageTintList = null
                            Glide.with(avatarImageView)
                                .load(state.localPath)
                                .circleCrop()
                                .dontAnimate()
                                .into(avatarImageView)
                        }
                    }
                }
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = MessengerAdapter(object : MessengerActionListener {
            override fun onConversationClicked(conversation: Conversation) {
                when (conversation.type) {
                    "dialog" -> {
                        if (!forwardFlag) {
                            parentFragmentManager.beginTransaction()
                                .replace(R.id.fragmentContainer, MessageFragment.newInstance(conversation.toDialog(), currentUser ?: User(0, "", ""), false), "MESSAGE_FRAGMENT_TAG")
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
                                .replace(R.id.fragmentContainer, GroupMessageFragment.newInstance(conversation.toGroup(), currentUser ?: User(0, "", ""), false), "GROUP_FRAGMENT_TAG")
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
        viewLifecycleOwner.lifecycleScope.launch {
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
            .replace(R.id.fragmentContainer, SettingsFragment.newInstance(currentUser ?: User(0, "", "")), "SETTINGS_FRAGMENT_TAG")
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