package com.example.messenger

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.example.messenger.databinding.FragmentMessengerBinding
import com.example.messenger.model.Conversation
import com.example.messenger.model.Message
import com.example.messenger.model.User
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.io.File

@AndroidEntryPoint
class MessengerFragment : Fragment() {
    private lateinit var binding: FragmentMessengerBinding
    private lateinit var adapter: MessengerAdapter
    private lateinit var preferences: SharedPreferences
    private var currentUser: User? = null
    private var forwardFlag: Boolean = false
    private var forwardMessages: List<Message>? = null
    private var forwardUsernames: List<String>? = null
    private var updateJob: Job? = null
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
        val toolbar: Toolbar = view.findViewById(R.id.toolbar)
        (activity as AppCompatActivity).setSupportActionBar(toolbar)
        (activity as AppCompatActivity).supportActionBar?.setDisplayShowTitleEnabled(false)
        val avatarImageView: ImageView = view.findViewById(R.id.toolbar_avatar)
        avatarImageView.setOnClickListener {
            Toast.makeText(context, "Avatar clicked!", Toast.LENGTH_SHORT).show()
        }
        val titleTextView: TextView = view.findViewById(R.id.toolbar_title)
        titleTextView.setOnClickListener {
            Toast.makeText(context, "Title clicked!", Toast.LENGTH_SHORT).show()
        }
        val checkImageView: ImageView = view.findViewById(R.id.ic_options)
        checkImageView.setOnClickListener {
            showPopupMenu(it, R.menu.popup_menu_check)
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
        preferences = requireContext().getSharedPreferences(APP_PREFERENCES, Context.MODE_PRIVATE)
        val wallpaper = preferences.getString(PREF_WALLPAPER, "")
        if(wallpaper != "") {
            val resId = resources.getIdentifier(wallpaper, "drawable", requireContext().packageName)
            if(resId != 0)
                binding.messengerLayout.background = ContextCompat.getDrawable(requireContext(), resId)
        }
        binding.button.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, NewsFragment(uriGlobal, currentUser?.id), "NEWS_FRAGMENT_TAG")
                .addToBackStack(null)
                .commit()
        }
        setupRecyclerView()
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        updateJob?.cancel()
    }

    override fun onPause() {
        super.onPause()
        updateJob?.cancel()
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
            adapter.conversations = conversations
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
                                messengerViewModel.forwardMessages(list, list2, conversation.toDialog().id)
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
                                messengerViewModel.forwardGroupMessages(list, list2, conversation.toGroup().id)
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
                R.id.menu_item1 -> {
                    parentFragmentManager.beginTransaction()
                        .replace(R.id.fragmentContainer, SettingsFragment(currentUser ?: User(0, "", "")), "SETTINGS_FRAGMENT_TAG")
                        .addToBackStack(null)
                        .commit()
                    true
                }
                R.id.menu_item2 -> {
                    logout()
                    true
                }
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

    private fun applyMenuTextColor(menu: Menu, color: Int) { // todo не используется
        for (i in 0 until menu.size()) {
            val menuItem = menu.getItem(i)
            val spannableTitle = SpannableString(menuItem.title)
            spannableTitle.setSpan(ForegroundColorSpan(color), 0, spannableTitle.length, 0)
            menuItem.title = spannableTitle
        }
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