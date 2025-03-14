package com.example.messenger

import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import androidx.core.content.ContextCompat
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
    private var drawableStart : Drawable? = null
    private var drawableEnd : Drawable? = null
    private val alf = ('a'..'z') + ('A'..'Z') + ('0'..'9') + ('!') + ('$')

    @Suppress("DEPRECATION")
    override fun onStart() {
        super.onStart()
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentBottomSheetPasswordBinding.inflate(inflater, container, false)
        val typedValue = TypedValue()
        context?.theme?.resolveAttribute(android.R.attr.colorPrimary, typedValue, true)
        val colorPrimary = typedValue.data
        drawableStart = ContextCompat.getDrawable(requireContext(), R.drawable.ic_lock)?.apply {
            setTint(colorPrimary)
        }
        drawableEnd = ContextCompat.getDrawable(requireContext(), R.drawable.ic_check_two)?.apply {
            setTint(colorPrimary)
        }
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
                binding.errorTextView.text = "Ошибка: Пустая строка старого пароля"
                false
            }
            binding.newPassword.text.isEmpty() -> {
                binding.errorTextView.text = "Ошибка: Пустая строка нового пароля"
                false
            }
            binding.passwordRepeat.text.isEmpty() -> {
                binding.errorTextView.text = "Ошибка: Пустая строка повтора пароля"
                false
            }
            isInvalid(binding.oldPassword.text.toString()) -> {
                binding.errorTextView.text = "Ошибка: Недопустимые символы в первой строке"
                false
            }
            isInvalid(binding.newPassword.text.toString()) -> {
                binding.errorTextView.text = "Ошибка: Недопустимые символы во второй строке"
                false
            }
            isInvalid(binding.passwordRepeat.text.toString()) -> {
                binding.errorTextView.text = "Ошибка: Недопустимые символы в третьей строке"
                false
            }
            else -> true
        }.also { binding.errorTextView.visibility = if (it) View.INVISIBLE else View.VISIBLE }
    }

    private fun isInvalid(text: String): Boolean {
        text.forEach {
            if(it !in alf) return true
        }
        return false
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