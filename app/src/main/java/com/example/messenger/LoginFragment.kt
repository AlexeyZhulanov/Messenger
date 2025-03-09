package com.example.messenger

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.example.messenger.databinding.FragmentLoginBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class LoginFragment : Fragment() {
    private lateinit var binding: FragmentLoginBinding
    private val viewModel: LoginViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentLoginBinding.inflate(inflater, container, false)
        binding.errorTextView.visibility = View.INVISIBLE
        binding.loginButton.setOnClickListener {
            if (validateInputs()) {
                val name = binding.username.text.toString()
                val password = binding.password.text.toString()
                val remember = binding.rememberSwitch.isChecked
                viewModel.login(name, password, remember, { (containsPrivateKey, isNeedGenerate, userId) ->
                    if(containsPrivateKey) {
                        parentFragmentManager.beginTransaction()
                            .replace(R.id.fragmentContainer, MessengerFragment(), "MESSENGER_FRAGMENT_TAG2")
                            .commit()
                    } else if(isNeedGenerate) {
                        parentFragmentManager.beginTransaction()
                            .replace(R.id.fragmentContainer, MasterKeyFragment(userId), "MASTER_KEY_FRAGMENT_TAG")
                            .commit()
                    } else {
                        parentFragmentManager.beginTransaction()
                            .replace(R.id.fragmentContainer, MasterKeyEnterFragment(userId), "MASTER_KEY_ENTER_FRAGMENT_TAG")
                            .commit()
                    }
                }, { errorMessage ->
                    binding.errorTextView.text = errorMessage
                    binding.errorTextView.visibility = View.VISIBLE
                })
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

    private fun validateInputs(): Boolean {
        return when {
            binding.username.text.isEmpty() -> {
                binding.errorTextView.text = "Ошибка: Пустая строка логина"
                false
            }
            binding.password.text.isEmpty() -> {
                binding.errorTextView.text = "Ошибка: Пустая строка пароля"
                false
            }
            else -> true
        }.also { binding.errorTextView.visibility = if (it) View.INVISIBLE else View.VISIBLE }
    }
}