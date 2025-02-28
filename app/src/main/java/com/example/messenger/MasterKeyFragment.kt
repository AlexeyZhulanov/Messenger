package com.example.messenger

import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.messenger.databinding.FragmentMasterKeyBinding

class MasterKeyFragment : Fragment() {

    private lateinit var binding: FragmentMasterKeyBinding


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentMasterKeyBinding.inflate(inflater, container, false)
        with(binding.masterKeyEditText) {
            filters = arrayOf(
                InputFilter.LengthFilter(24), // 20 символов + 4 тире
                InputFilter { source, _, _, _, _, _ ->
                    // Запрещаем ввод тире вручную
                    if (source.toString().contains("-")) {
                        return@InputFilter ""
                    }
                    null
                }
            )
            addTextChangedListener(object: TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    if (s.isNullOrEmpty()) return

                    val text = s.toString()
                    val selection = selectionStart

                    // Удаляем лишние тире
                    val cleanedText = StringBuilder()
                    text.forEachIndexed { index, char ->
                        if (char != '-' || index == 4 || index == 9 || index == 14 || index == 19) {
                            cleanedText.append(char)
                        }
                    }

                    if (cleanedText.length > 4 && cleanedText[4] != '-') {
                        cleanedText.insert(4, "-")
                    }
                    if (cleanedText.length > 9 && cleanedText[9] != '-') {
                        cleanedText.insert(9, "-")
                    }
                    if (cleanedText.length > 14 && cleanedText[14] != '-') {
                        cleanedText.insert(14, "-")
                    }
                    if (cleanedText.length > 19 && cleanedText[19] != '-') {
                        cleanedText.insert(19, "-")
                    }

                    // Если текст изменился, обновляем EditText
                    if (text != cleanedText.toString()) {
                        removeTextChangedListener(this)
                        setText(cleanedText.toString())
                        setSelection(selection.coerceAtMost(cleanedText.length))
                        addTextChangedListener(this)
                    }
                }

                override fun afterTextChanged(s: Editable?) {}
            })
        }
        return binding.root
    }

}