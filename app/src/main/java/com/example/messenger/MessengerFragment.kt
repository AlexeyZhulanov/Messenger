package com.example.messenger

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.messenger.databinding.FragmentMessengerBinding
import com.example.messenger.model.Conversation
import com.example.messenger.model.ConversationsListener
import com.example.messenger.model.Message
import com.example.messenger.model.RetrofitService

class MessengerFragment : Fragment() {
    private lateinit var binding: FragmentMessengerBinding
    private lateinit var adapter: MessengerAdapter

    private val retrofitService: RetrofitService
        get() = Singletons.retrofitService

    @SuppressLint("DiscouragedApi")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentMessengerBinding.inflate(inflater, container, false)
        val prefs = requireContext().getSharedPreferences(APP_PREFERENCES, Context.MODE_PRIVATE)
        val wallpaper = prefs.getString(PREF_WALLPAPER, "")
        if(wallpaper != "") {
            val resId = resources.getIdentifier(wallpaper, "drawable", requireContext().packageName)
            if(resId != 0)
                binding.alarmLayout.background = ContextCompat.getDrawable(requireContext(), resId)
        }
        adapter = MessengerAdapter(object: MessengerActionListener {
            override fun onMessageClicked(conversation: Conversation, index: Int) {
                Toast.makeText(context, conversation.type, Toast.LENGTH_SHORT).show()
            }
        })
        val layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerview.layoutManager = layoutManager
        binding.recyclerview.adapter = adapter
        retrofitService.addListener(conversationsListener)
        (activity as AppCompatActivity?)!!.setSupportActionBar(binding.toolbar) //adds a button
        return binding.root
    }

    private val conversationsListener: ConversationsListener = {
        adapter.conversations = it
    }

    override fun onDestroyView() {
        super.onDestroyView()
    }
}