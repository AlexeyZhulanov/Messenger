package com.example.messenger

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
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
import com.example.messenger.model.MessengerService
import com.example.messenger.model.RetrofitService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.UnknownHostException

@AndroidEntryPoint
class MessengerFragment : Fragment() {
    private lateinit var binding: FragmentMessengerBinding
    private lateinit var adapter: MessengerAdapter
    private lateinit var preferences: SharedPreferences
    private var updateJob: Job? = null
    private val job = Job()
    private var uiScope = CoroutineScope(Dispatchers.Main + job)
    private val messengerViewModel: MessengerViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
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
        messengerViewModel.conversations.observe(viewLifecycleOwner) { conversations ->
            adapter.conversations = conversations
        }
    }

    private fun setupRecyclerView() {
        adapter = MessengerAdapter(object : MessengerActionListener {
            override fun onConversationClicked(conversation: Conversation, index: Int) {
                when (conversation.type) {
                    "dialog" -> {
                        parentFragmentManager.beginTransaction()
                            .replace(R.id.fragmentContainer, MessageFragment(conversation.toDialog()), "MESSAGE_FRAGMENT_TAG")
                            .addToBackStack(null)
                            .commit()
                    }
                    "group" -> {
                        parentFragmentManager.beginTransaction()
                            .replace(R.id.fragmentContainer, GroupFragment(conversation.toGroup()), "GROUP_FRAGMENT_TAG")
                            .addToBackStack(null)
                            .commit()
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
            .setTitle("Введите никнейм пользователя")
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