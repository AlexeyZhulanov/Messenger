package com.example.messenger

import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import com.example.messenger.databinding.FragmentBottomSheetPasswordBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint

interface BottomSheetListener {
    fun onChangePassword()
}

@AndroidEntryPoint
class BottomSheetPasswordFragment(
    private val settingsViewModel: SettingsViewModel,
    private val bottomSheetListener: BottomSheetListener
) : BottomSheetDialogFragment() {
    private lateinit var binding: FragmentBottomSheetPasswordBinding
    private lateinit var drawableStart : Drawable
    private lateinit var drawableEnd : Drawable
    private val alf = ('a'..'z') + ('A'..'Z') + ('0'..'9') + ('!') + ('$')

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentBottomSheetPasswordBinding.inflate(inflater, container, false)

        binding.oldPassword.addTextChangedListener(object: TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                checkCharSequence(s, binding.oldPassword)
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        binding.newPassword.addTextChangedListener(object: TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                checkCharSequence(s, binding.newPassword)
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        binding.passwordRepeat.addTextChangedListener(object: TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                checkCharSequence(s, binding.passwordRepeat)
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        binding.confirmButton.setOnClickListener {
            if (validateInputs()) {
                val oldPassword = binding.oldPassword.text.toString()
                val newPassword = binding.newPassword.text.toString()
                val passwordRepeat = binding.passwordRepeat.text.toString()
                if(newPassword == passwordRepeat) {
                    settingsViewModel.updatePassword(oldPassword, newPassword, {
                        bottomSheetListener.onChangePassword()
                        dismiss()
                    }, { errorMessage ->
                        binding.errorTextView.text = errorMessage
                        binding.errorTextView.visibility = View.VISIBLE
                    })
                } else {
                    binding.errorTextView.text = "Ошибка: Новый пароль не совпадает"
                    binding.errorTextView.visibility = View.VISIBLE
                }
            }
        }

        binding.cancelButton.setOnClickListener {
            dismiss()
        }

        return binding.root
    }

    private fun validateInputs(): Boolean {
        return when {
            binding.oldPassword.text.isEmpty() -> {
                binding.errorTextView.text = "Ошибка: Пустая строка логина"
                false
            }
            binding.newPassword.text.isEmpty() -> {
                binding.errorTextView.text = "Ошибка: Пустая строка имени пользователя"
                false
            }
            binding.passwordRepeat.text.isEmpty() -> {
                binding.errorTextView.text = "Ошибка: Пустая строка повтора пароля"
                false
            }
            else -> true
        }.also { binding.errorTextView.visibility = if (it) View.INVISIBLE else View.VISIBLE }
    }

    private fun checkCharSequence(s: CharSequence?, editText: EditText) {
        if(s.isNullOrEmpty()) {
            hideDrawableEnd(editText)
        } else {
            var bool = true
            for(it in s) {
                if(it !in alf) {
                    hideDrawableEnd(editText)
                    bool = false
                    break
                }
            }
            if(bool) showDrawableEnd(editText)
        }
    }

    private fun hideDrawableEnd(editText: EditText) {
        editText.setCompoundDrawablesWithIntrinsicBounds(
            drawableStart,
            null,
            null,
            null
        )
    }

    private fun showDrawableEnd(editText: EditText) {
        editText.setCompoundDrawablesWithIntrinsicBounds(
            drawableStart,
            null,
            drawableEnd,
            null
        )
    }
}