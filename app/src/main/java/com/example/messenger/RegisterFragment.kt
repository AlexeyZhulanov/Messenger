package com.example.messenger

import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.example.messenger.databinding.FragmentRegisterBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class RegisterFragment : Fragment() {
    private lateinit var binding: FragmentRegisterBinding
    private var drawableStart : Drawable? = null
    private var drawableEnd : Drawable? = null
    private val alf = ('a'..'z') + ('A'..'Z') + ('0'..'9') + '!' + '$' + '@' + '.'
    private val alf2 = ('А'..'Я') + ('а'..'я') + alf + ' '
    private val viewModel: RegisterViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentRegisterBinding.inflate(inflater, container, false)
        val typedValue = TypedValue()
        context?.theme?.resolveAttribute(android.R.attr.colorPrimary, typedValue, true)
        val colorPrimary = typedValue.data
        drawableStart = ContextCompat.getDrawable(requireContext(), R.drawable.ic_lock)?.apply {
            setTint(colorPrimary)
        }
        drawableEnd = ContextCompat.getDrawable(requireContext(), R.drawable.ic_check_two)?.apply {
            setTint(colorPrimary)
        }
        binding.errorTextView.visibility = View.INVISIBLE
        binding.registerButton.setOnClickListener {
            if (validateInputs()) {
                val name = binding.name2.text.toString()
                val username = binding.username2.text.toString()
                val password = binding.password2.text.toString()
                viewModel.register(name, username, password, {
                    parentFragmentManager.beginTransaction()
                        .replace(R.id.fragmentContainer, LoginFragment(), "LOGIN_FRAGMENT_TAG2")
                        .addToBackStack(null)
                        .commit()
                }, { errorMessage ->
                    binding.errorTextView.text = errorMessage
                    binding.errorTextView.visibility = View.VISIBLE
                })
            }
        }
        binding.signupText.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, LoginFragment(), "LOGIN_FRAGMENT_TAG2")
                .addToBackStack(null)
                .commit()
        }

        binding.name2.addTextChangedListener(object: TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                checkCharSequence(s, binding.name2, alf)
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        binding.username2.addTextChangedListener(object: TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                checkCharSequence(s, binding.username2, alf2)
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        binding.password2.addTextChangedListener(object: TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                checkCharSequence(s, binding.password2, alf)
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        binding.passwordRepeat.addTextChangedListener(object: TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                checkCharSequence(s, binding.passwordRepeat, alf)
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        return binding.root
    }

    private fun validateInputs(): Boolean {
        return when {
            binding.name2.text.isEmpty() -> {
                binding.errorTextView.text = "Ошибка: Пустая строка логина"
                false
            }
            binding.username2.text.isEmpty() -> {
                binding.errorTextView.text = "Ошибка: Пустая строка имени пользователя"
                false
            }
            binding.password2.text.isEmpty() -> {
                binding.errorTextView.text = "Ошибка: Пустая строка пароля"
                false
            }
            binding.passwordRepeat.text.isEmpty() -> {
                binding.errorTextView.text = "Ошибка: Пустая строка повтора пароля"
                false
            }
            binding.password2.text.toString() != binding.passwordRepeat.text.toString() -> {
                binding.errorTextView.text = "Ошибка: Пароли не совпадают"
                false
            }
            isInvalid(binding.name2.text.toString(), alf) -> {
                binding.errorTextView.text = "Ошибка: Недопустимые символы в первой строке"
                false
            }
            isInvalid(binding.username2.text.toString(), alf2) -> {
                binding.errorTextView.text = "Ошибка: Недопустимые символы во второй строке"
                false
            }
            isInvalid(binding.password2.text.toString(), alf) -> {
                binding.errorTextView.text = "Ошибка: Недопустимые символы в третьей строке"
                false
            }
            isInvalid(binding.passwordRepeat.text.toString(), alf) -> {
                binding.errorTextView.text = "Ошибка: Недопустимые символы в четвертой строке"
                false
            }
            else -> true
        }.also { binding.errorTextView.visibility = if (it) View.INVISIBLE else View.VISIBLE }
    }

    private fun isInvalid(text: String, list: List<Char>): Boolean {
        text.forEach {
            if(it !in list) return true
        }
        return false
    }

    private fun checkCharSequence(s: CharSequence?, editText: EditText, list: List<Char>) {
        if(s.isNullOrEmpty()) {
            hideDrawableEnd(editText)
        } else {
            var bool = true
            for(it in s) {
                if(it !in list) {
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