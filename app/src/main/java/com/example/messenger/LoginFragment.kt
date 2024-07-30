package com.example.messenger

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.messenger.databinding.FragmentLoginBinding
import com.example.messenger.model.Conversation
import com.example.messenger.model.MessengerService
import com.example.messenger.model.RetrofitService
import com.example.messenger.model.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

class LoginFragment : Fragment() {
    private lateinit var binding: FragmentLoginBinding
    private val job = Job()
    private val uiScope = CoroutineScope(Dispatchers.Main + job)
    private val messengerService: MessengerService
        get() = Singletons.messengerRepository as MessengerService

    private val retrofitService: RetrofitService
        get() = Singletons.retrofitRepository as RetrofitService

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentLoginBinding.inflate(inflater, container, false)
        binding.errorTextView.visibility = View.INVISIBLE
        binding.loginButton.setOnClickListener {
            if(binding.username.text.isNotEmpty() && binding.password.text.isNotEmpty()) {
                uiScope.launch {
                    val name = binding.username.text.toString()
                    val password = binding.password.text.toString()
                    val cont = async(Dispatchers.Main) { retrofitService.login(name, password) }
                    if (cont.await()) {
                        val remember = if(binding.rememberSwitch.isChecked) 1 else 0
                        val settings = Settings(0, remember, name, password)
                        Log.d("testBeforeRoom", "OK")
                        messengerService.updateSettings(settings)
                        Log.d("testAfterRoom", "OK")
                        parentFragmentManager.beginTransaction()
                            .replace(R.id.fragmentContainer, MessengerFragment(), "MESSENGER_FRAGMENT_TAG2")
                            .commit()
                    } else {
                        binding.errorTextView.text = "Ошибка: Неверный логин или пароль"
                        binding.errorTextView.visibility = View.VISIBLE
                    }
                }
            } else {
                if(binding.username.text.isEmpty()) binding.errorTextView.text = "Ошибка: Пустая строка логина"
                else
                if(binding.password.text.isEmpty()) binding.errorTextView.text = "Ошибка: Пустая строка пароля"
                binding.errorTextView.visibility = View.VISIBLE
            }
        }
        binding.signupText.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, RegisterFragment(), "REGISTER_FRAGMENT_TAG2")
                .addToBackStack(null)
                .commit()
        }

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
    }
}