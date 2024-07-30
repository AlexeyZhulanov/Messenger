package com.example.messenger

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
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
        (activity as AppCompatActivity?)!!.setSupportActionBar(binding.toolbar) //adds a button
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
}