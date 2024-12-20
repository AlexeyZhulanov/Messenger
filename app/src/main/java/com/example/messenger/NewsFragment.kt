package com.example.messenger

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.messenger.databinding.FragmentNewsBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NewsFragment(private val messengerViewModel: MessengerViewModel) : Fragment() {

    private lateinit var binding: FragmentNewsBinding
    private var uiScope = CoroutineScope(Dispatchers.Main)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentNewsBinding.inflate(inflater, container, false)
        uiScope.launch {
            val permission = messengerViewModel.getPermission()
            binding.statusTextView.text = if(permission == 1) "Модератор" else "Пользователь"
        }
        return binding.root
    }
}