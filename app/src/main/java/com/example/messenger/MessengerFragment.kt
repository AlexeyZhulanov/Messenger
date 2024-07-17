package com.example.messenger

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.messenger.databinding.FragmentMessengerBinding
import com.example.messenger.model.Message

class MessengerFragment : Fragment() {
    private lateinit var binding: FragmentMessengerBinding
    private lateinit var adapter: MessengerAdapter

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
            override fun onMessageClicked(message: Message, index: Int) {
                TODO("Not yet implemented")
            }
        })
        val layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerview.layoutManager = layoutManager
        binding.recyclerview.adapter = adapter
        (activity as AppCompatActivity?)!!.setSupportActionBar(binding.toolbar) //adds a button
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
    }
}