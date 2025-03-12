package com.example.messenger

import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.messenger.databinding.FragmentMasterKeyEnterBinding
import com.example.messenger.model.RetrofitService
import com.example.messenger.security.BouncyCastleHelper
import com.example.messenger.security.ChatKeyManager
import com.example.messenger.security.PrivateKeyEncryptor
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.security.KeyFactory
import java.security.KeyPair
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.Arrays
import javax.inject.Inject

@AndroidEntryPoint
class MasterKeyEnterFragment(private val userId: Int) : Fragment() {

    private lateinit var binding: FragmentMasterKeyEnterBinding
    private var pair: Pair<String?, String?> = null to null

    @Inject
    lateinit var retrofitService: RetrofitService

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentMasterKeyEnterBinding.inflate(inflater, container, false)

        setupEditText()

        lifecycleScope.launch {
            pair = retrofitService.getKeys()
        }

        binding.doneButton.setOnClickListener {
            binding.doneButton.isEnabled = false
            val text = binding.masterKeyEditText.text.toString().replace("-","")
            if(text.length in 16..20) {
                Log.d("testMasterKey", text)
                val publicKeyString = pair.first
                val privateKeyEncryptedString = pair.second
                if(publicKeyString != null && privateKeyEncryptedString != null) {
                    try {
                        val encryptor = PrivateKeyEncryptor(text)
                        val keyManager = ChatKeyManager()
                        val publicKey = getPublicKey(publicKeyString)
                        val privateKey = encryptor.decryptPrivateKey(privateKeyEncryptedString)
                        val certificate = BouncyCastleHelper().createSelfSignedCertificate(KeyPair(publicKey, privateKey))
                        keyManager.savePrivateKey(userId, privateKey, certificate)
                        Arrays.fill(privateKey.encoded, 0.toByte())
                        goToMessengerFragment()
                    } catch (e: Exception) {
                        Log.d("testErrorMasterEnter", e.message.toString())
                        showErrorTextView("Вы ввели неверный ключ")
                        binding.doneButton.isEnabled = true
                    }

                } else {
                    showErrorTextView("Не удалось подгрузить ключи, Нет сети!")
                    lifecycleScope.launch {
                        pair = retrofitService.getKeys()
                    }
                    binding.doneButton.isEnabled = true
                }
            } else {
                showErrorTextView("Ошибка, длина ключа должна быть от 16 до 20 символов")
                binding.doneButton.isEnabled = true
            }
        }

        return binding.root
    }

    private fun getPublicKey(key: String): PublicKey {
        val publicKeyBytes = Base64.decode(key, Base64.NO_WRAP)
        val keySpec = X509EncodedKeySpec(publicKeyBytes)
        return KeyFactory.getInstance("RSA").generatePublic(keySpec)
    }

    private fun goToMessengerFragment() {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, MessengerFragment(), "MESSENGER_FRAGMENT_TAG4")
            .commit()
    }

    private fun showErrorTextView(text: String) {
        binding.statusText.visibility = View.VISIBLE
        binding.statusText.text = text
    }

    private fun setupEditText() {
        with(binding.masterKeyEditText) {
            filters = arrayOf(InputFilter.LengthFilter(24))

            addTextChangedListener(object: TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    if (s.isNullOrEmpty()) return

                    val text = s.toString()
                    val selection = selectionStart
                    var dashCounter = 0

                    // Удаляем лишние тире
                    val cleanedText = StringBuilder()
                    text.forEachIndexed { index, char ->
                        if (char != '-' || index == 4 || index == 9 || index == 14 || index == 19) {
                            cleanedText.append(char)
                        }
                    }

                    if (cleanedText.length > 4 && cleanedText[4] != '-') {
                        cleanedText.insert(4, "-")
                        dashCounter++
                    }
                    if (cleanedText.length > 9 && cleanedText[9] != '-') {
                        cleanedText.insert(9, "-")
                        dashCounter++
                    }
                    if (cleanedText.length > 14 && cleanedText[14] != '-') {
                        cleanedText.insert(14, "-")
                        dashCounter++
                    }
                    if (cleanedText.length > 19 && cleanedText[19] != '-') {
                        cleanedText.insert(19, "-")
                        dashCounter++
                    }

                    // Если текст изменился, обновляем EditText
                    if (text != cleanedText.toString()) {
                        removeTextChangedListener(this)
                        setText(cleanedText.toString())
                        setSelection(selection.coerceAtMost(cleanedText.length) + dashCounter)
                        addTextChangedListener(this)
                    }
                }

                override fun afterTextChanged(s: Editable?) {}
            })
        }
    }
}