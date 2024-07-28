package com.example.messenger

import android.annotation.SuppressLint
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.messenger.databinding.FragmentMessageBinding
import com.example.messenger.model.Dialog
import com.example.messenger.model.MessengerService
import com.example.messenger.model.RetrofitService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MessageFragment(
    private val dialog: Dialog
) : Fragment() {
    private lateinit var binding: FragmentMessageBinding
    private val job = Job()
    private val uiScope = CoroutineScope(Dispatchers.Main + job)
    private val messengerService: MessengerService
        get() = Singletons.messengerRepository as MessengerService
    private val retrofitService: RetrofitService
        get() = Singletons.retrofitRepository as RetrofitService

    @SuppressLint("InflateParams")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentMessageBinding.inflate(inflater, container, false)

        val customActionBar = inflater.inflate(R.layout.custom_action_bar, null) as Toolbar
        customActionBar.findViewById<TextView>(R.id.userNameTextView).text = dialog.otherUser.username
//        lifecycleScope.launch {
//            customActionBar.findViewById<TextView>(R.id.lastSessionTextView) = retrofitService.getLastSession(dialog.otherUser.id)
//        }
        // todo add notifications turn off in room and here
        // todo set avatar
        (requireActivity() as AppCompatActivity).setSupportActionBar(customActionBar)
        customActionBar.findViewById<ImageView>(R.id.photoImageView).setOnClickListener {
            // todo top sheet fragment
        }

        binding.enterMessage.addTextChangedListener(object: TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if(s.isNullOrEmpty()) {
                    binding.micButton.visibility = View.VISIBLE
                    binding.enterButton.visibility = View.INVISIBLE
                } else {
                    binding.enterButton.visibility = View.VISIBLE
                    binding.micButton.visibility = View.INVISIBLE
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        binding.enterButton.setOnClickListener {
            // todo
        }
        binding.micButton.setOnClickListener {
            // todo use lib
        }
        binding.attachButton.setOnClickListener {
            // todo use lib
        }
        binding.emojiButton.setOnClickListener {
            // todo maybe use lib
        }

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
    }
}