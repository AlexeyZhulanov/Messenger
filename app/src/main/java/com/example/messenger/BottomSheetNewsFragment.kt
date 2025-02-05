package com.example.messenger

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import com.example.messenger.databinding.FragmentNewsCreateBinding
import com.example.messenger.model.News
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

interface BottomSheetNewsListener {
    fun onPostSent()
}

class BottomSheetNewsFragment(
    private val newsViewModel: NewsViewModel,
    private val currentNews: News? = null,
    private val bottomSheetNewsListener: BottomSheetNewsListener
) : BottomSheetDialogFragment() {
    private lateinit var binding: FragmentNewsCreateBinding

    @Suppress("DEPRECATION")
    override fun onStart() {
        super.onStart()
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentNewsCreateBinding.inflate(inflater, container, false)
        if(currentNews != null) {
            binding.header.text = "Редактирование поста"
            // todo установка всех параметров из currentNews
        }

        binding.cancelButton.setOnClickListener {
            dismiss()
        }
        binding.confirmButton.setOnClickListener {
            // todo отправка запроса
            bottomSheetNewsListener.onPostSent()
        }
        return binding.root
    }
}