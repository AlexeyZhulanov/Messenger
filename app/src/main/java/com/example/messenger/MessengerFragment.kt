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
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.messenger.databinding.FragmentMessengerBinding
import com.example.messenger.model.Conversation
import com.example.messenger.model.ConversationsListener
import com.example.messenger.model.Message
import com.example.messenger.model.RetrofitRepository
import com.example.messenger.model.RetrofitService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MessengerFragment : Fragment() {
    private lateinit var binding: FragmentMessengerBinding
    private lateinit var adapter: MessengerAdapter
    private lateinit var preferences: SharedPreferences
    private var updateJob: Job? = null
    private val job = Job()
    private var uiScope = CoroutineScope(Dispatchers.Main + job)
    private val retrofitService: RetrofitService
        get() = Singletons.retrofitRepository as RetrofitService

    @SuppressLint("DiscouragedApi")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentMessengerBinding.inflate(inflater, container, false)
        preferences = requireContext().getSharedPreferences(APP_PREFERENCES, Context.MODE_PRIVATE)
        val wallpaper = preferences.getString(PREF_WALLPAPER, "")
        if(wallpaper != "") {
            val resId = resources.getIdentifier(wallpaper, "drawable", requireContext().packageName)
            if(resId != 0)
                binding.messengerLayout.background = ContextCompat.getDrawable(requireContext(), resId)
        }
        val toolbar: Toolbar = requireView().findViewById(R.id.toolbar)
        (activity as AppCompatActivity).setSupportActionBar(toolbar)
        val avatarImageView: ImageView = requireView().findViewById(R.id.toolbar_avatar)
        avatarImageView.setOnClickListener {
            Toast.makeText(context, "Avatar clicked!", Toast.LENGTH_SHORT).show()
        }
        val titleTextView: TextView = requireView().findViewById(R.id.toolbar_title)
        titleTextView.setOnClickListener {
            Toast.makeText(context, "Title clicked!", Toast.LENGTH_SHORT).show()
        }
        val checkImageView: ImageView = requireView().findViewById(R.id.ic_check)
        checkImageView.setOnClickListener {
            showPopupMenu(it, R.menu.popup_menu_check)
        }
        val addImageView: ImageView = requireView().findViewById(R.id.ic_add)
        addImageView.setOnClickListener {
            showPopupMenu(it, R.menu.popup_menu_add)
        }

        adapter = MessengerAdapter(object: MessengerActionListener {
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
        val layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerview.layoutManager = layoutManager
        binding.recyclerview.adapter = adapter
        retrofitService.initCompleted.observe(viewLifecycleOwner) { initCompleted ->
            if(initCompleted) {
                updateJob = lifecycleScope.launch {
                    while(isActive) {
                        adapter.conversations = retrofitService.getConversations()
                        delay(30000)
                    }
                }
            }
        }

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
    private fun showPopupMenu(view: View, menuRes: Int) {
        val popupMenu = PopupMenu(requireContext(), view)
        popupMenu.menuInflater.inflate(menuRes, popupMenu.menu)
        popupMenu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menu_item1 -> {
                    Toast.makeText(context, "Item 1 clicked", Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.menu_item2 -> {
                    Toast.makeText(context, "Item 2 clicked", Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.menu_item3 -> {
                    Toast.makeText(context, "Item 3 clicked", Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.menu_item4 -> {
                    Toast.makeText(context, "Item 4 clicked", Toast.LENGTH_SHORT).show()
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
        val dialog = AlertDialog.Builder(requireActivity().applicationContext)
            .setTitle("Введите никнейм пользователя")
            .setView(dialogView)
            .setPositiveButton("Добавить") { dialogInterface, _ ->
                val input = dialogView.findViewById<EditText>(R.id.dialog_input).text.toString()
                // Handle the "Add" button click here
                handleAddButtonClick(input)
                dialogInterface.dismiss()
            }
            .setNegativeButton("Назад") { dialogInterface, _ ->
                dialogInterface.dismiss()
            }
            .create()

        // Show the dialog
        dialog.show()
    }

    private fun handleAddButtonClick(input: String) {
        // Handle the input from the dialog
        // For example, you could add the item to a list or perform another action
        Log.d("MainActivity", "Item added: $input")
    }

}