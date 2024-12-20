package com.example.messenger

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.Log
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
import com.example.messenger.databinding.FragmentMessengerBinding
import com.example.messenger.model.Conversation
import com.example.messenger.model.Message
import com.example.messenger.model.MessengerService
import com.example.messenger.model.RetrofitService
import com.example.messenger.model.User
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.UnknownHostException

@AndroidEntryPoint
class MessengerFragment : Fragment() {
    private lateinit var binding: FragmentMessengerBinding
    private lateinit var adapter: MessengerAdapter
    private lateinit var preferences: SharedPreferences
    private lateinit var currentUser: User
    private var forwardFlag: Boolean = false
    private var forwardMessages: List<Message>? = null
    private var forwardUsernames: List<String>? = null
    private var updateJob: Job? = null
    private val job = Job()
    private var uiScope = CoroutineScope(Dispatchers.Main + job)
    private val messengerViewModel: MessengerViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        parentFragmentManager.setFragmentResultListener("forwardMessagesRequestKey", viewLifecycleOwner) { requestKey, bundle ->
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
                .replace(R.id.fragmentContainer, NewsFragment(messengerViewModel), "NEWS_FRAGMENT_TAG")
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
        messengerViewModel.currentUser.observe(viewLifecycleOwner) { user ->
            currentUser = user
        }
    }

    private fun setupRecyclerView() {
        adapter = MessengerAdapter(object : MessengerActionListener {
            override fun onConversationClicked(conversation: Conversation, index: Int) {
                when (conversation.type) {
                    "dialog" -> {
                        if (!forwardFlag) {
                            parentFragmentManager.beginTransaction()
                                .replace(R.id.fragmentContainer, MessageFragment(conversation.toDialog(), currentUser), "MESSAGE_FRAGMENT_TAG")
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
                                .replace(R.id.fragmentContainer, GroupFragment(conversation.toGroup(), currentUser), "GROUP_FRAGMENT_TAG")
                                .addToBackStack(null)
                                .commit()
                        } else {
                            forwardFlag = false
                            if(forwardMessages != null) {
                                val list = forwardMessages
                                //messengerViewModel.forwardMessages(list, conversation.toGroup().id)
                                // todo forward Group Messages
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
                        .replace(R.id.fragmentContainer, SettingsFragment(), "SETTINGS_FRAGMENT_TAG")
                        .addToBackStack(null)
                        .commit()
                    true
                }
                R.id.menu_item2 -> {
                    parentFragmentManager.beginTransaction()
                        .replace(R.id.fragmentContainer, LoginFragment(), "LOGIN_FRAGMENT_TAG3")
                        .commit()
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

    private fun applyMenuTextColor(menu: Menu, color: Int) {
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
                messengerViewModel.createDialog(input)
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
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_item, null)

        // Create the AlertDialog
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Введите название группы")
            .setView(dialogView)
            .setPositiveButton("Создать") { dialogInterface, _ ->
                val input = dialogView.findViewById<EditText>(R.id.dialog_input).text.toString()
                messengerViewModel.createGroup(input)
                dialogInterface.dismiss()
            }
            .setNegativeButton("Назад") { dialogInterface, _ ->
                dialogInterface.dismiss()
            }
            .create()

        dialog.show()
    }
}