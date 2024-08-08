package com.example.messenger

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.messenger.databinding.FragmentChoosePickBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

interface ChoosePickListener {
    fun onGalleryClick()
    fun onFileClick()
}

class ChoosePickFragment(
    private val choosePickListener: ChoosePickListener
) : BottomSheetDialogFragment() {

    private lateinit var binding: FragmentChoosePickBinding
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentChoosePickBinding.inflate(inflater, container, false)
        binding.imagePick.setOnClickListener {
            choosePickListener.onGalleryClick()
            dismiss()
        }
        binding.filePick.setOnClickListener {
            choosePickListener.onFileClick()
            dismiss()
        }
        return binding.root
    }
}