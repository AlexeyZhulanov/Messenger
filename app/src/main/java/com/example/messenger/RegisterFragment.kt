package com.example.messenger

import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.messenger.databinding.FragmentRegisterBinding
import com.example.messenger.model.RetrofitService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

class RegisterFragment : Fragment() {
    private lateinit var binding: FragmentRegisterBinding
    private val job = Job()
    private val uiScope = CoroutineScope(Dispatchers.Main + job)
    private lateinit var drawableStart : Drawable
    private lateinit var drawableEnd : Drawable
    private val alf = ('a'..'z') + ('A'..'Z') + ('0'..'9') + ('!') + ('$')
    private val retrofitService: RetrofitService
        get() = Singletons.retrofitRepository as RetrofitService

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentRegisterBinding.inflate(inflater, container, false)
        drawableStart = ContextCompat.getDrawable(requireContext(), R.drawable.ic_lock)!!
        drawableEnd = ContextCompat.getDrawable(requireContext(), R.drawable.ic_check_circle)!!
        binding.errorTextView.visibility = View.INVISIBLE
        binding.loginButton.setOnClickListener {
            if(binding.username.text.isNotEmpty() && binding.password.text.isNotEmpty()
                && binding.name.text.isNotEmpty() && binding.passwordRepeat.text.isNotEmpty()) {
                if(binding.password.text.toString() == binding.passwordRepeat.text.toString()) {
                    uiScope.launch {
                        val name = binding.name.text.toString()
                        val username = binding.username.text.toString()
                        val password = binding.password.text.toString()
                        val b = async { retrofitService.register(name, username, password) }
                        val bool = b.await()
                        if (bool) {
                            parentFragmentManager.beginTransaction()
                                .replace(R.id.fragmentContainer, LoginFragment(), "LOGIN_FRAGMENT_TAG2")
                                .addToBackStack(null)
                                .commit()
                        } else {
                            binding.errorTextView.text = "Ошибка: Имя пользователя уже занято"
                            binding.errorTextView.visibility = View.VISIBLE
                        }
                    }
                } else {
                    binding.errorTextView.text = "Ошибка: Пароли не совпадают"
                    binding.errorTextView.visibility = View.VISIBLE
                }
            } else {
                if(binding.name.text.isEmpty()) binding.errorTextView.text = "Ошибка: Пустая строка логина"
                else if(binding.username.text.isEmpty()) binding.errorTextView.text = "Ошибка: Пустая строка имени пользователя"
                else if(binding.password.text.isEmpty()) binding.errorTextView.text = "Ошибка: Пустая строка пароля"
                else if(binding.passwordRepeat.text.isEmpty()) binding.errorTextView.text = "Ошибка: Пустая строка повтора пароля"
                binding.errorTextView.visibility = View.VISIBLE
            }
        }
        binding.signupText.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, LoginFragment(), "LOGIN_FRAGMENT_TAG2")
                .addToBackStack(null)
                .commit()
        }

        binding.name.addTextChangedListener(object: TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                checkCharSequence(s, binding.name)
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        binding.username.addTextChangedListener(object: TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                checkCharSequence(s, binding.username)
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        binding.password.addTextChangedListener(object: TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                checkCharSequence(s, binding.password)
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

        return binding.root
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

    override fun onDestroyView() {
        super.onDestroyView()
    }
}