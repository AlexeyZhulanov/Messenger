package com.example.messenger

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.messenger.databinding.FragmentMasterKeyBinding
import com.example.messenger.model.RetrofitService
import com.example.messenger.security.BouncyCastleHelper
import com.example.messenger.security.ChatKeyManager
import com.example.messenger.security.PrivateKeyEncryptor
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.KeyPair
import java.util.Arrays
import javax.inject.Inject

@AndroidEntryPoint
class MasterKeyFragment(private val userId: Int) : Fragment(), OnSaveButtonClickListener {

    private lateinit var binding: FragmentMasterKeyBinding
    private var savedMasterKey = ""
    private var wasConnectionError = false
    private var publicKeyString = ""
    private var encryptedPrivateKey = ""

    @Inject
    lateinit var retrofitService: RetrofitService

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentMasterKeyBinding.inflate(inflater, container, false)

        setupEditText()

        binding.doneButton.setOnClickListener {
            if(!wasConnectionError) {
                val text = binding.masterKeyEditText.text.toString().replace("-","")
                if(text.length in 16..20) {
                    savedMasterKey = text
                    showCountdownDialog()
                } else showErrorTextView("Ошибка: длина ключа должна быть от 16 до 20 символов")
            } else {
                saveKeys()
            }
        }
        binding.copyButton.setOnClickListener {
            val text = binding.masterKeyEditText.text.toString().replace("-","")
            if(text.length in 16..20) {
                val clipboard = context?.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("label", text)
                clipboard.setPrimaryClip(clip)
            } else showErrorTextView("Ошибка: нельзя скопировать неправильный ключ")
        }
        binding.generateButton.setOnClickListener {
            binding.masterKeyEditText.setText(generateRandomKey())
            binding.masterKeyEditText.setSelection(19)
        }
        return binding.root
    }

    private fun generateRandomKey(length: Int = 16): String {
        val allowedChars = ('A'..'Z') + ('a'..'z') + ('0'..'9')
        return (1..length)
            .map { allowedChars.random() }
            .joinToString("")
    }

    override fun onSaveButtonClicked() {
        saveKeys()
    }

    private fun saveKeys() {
        binding.doneButton.isEnabled = false
        val keyManager = ChatKeyManager()
        val (publicKey, privateKey) = keyManager.generateKeyPair()
        val encryptor = PrivateKeyEncryptor(savedMasterKey)
        encryptedPrivateKey = encryptor.encryptPrivateKey(privateKey)
        publicKeyString = Base64.encodeToString(publicKey.encoded, Base64.NO_WRAP)
        val certificate = BouncyCastleHelper().createSelfSignedCertificate(KeyPair(publicKey, privateKey))
        lifecycleScope.launch {
            val success = withContext(NonCancellable) {
                return@withContext retrofitService.saveUserKeys(publicKeyString, encryptedPrivateKey)
            }
            if(success) {
                keyManager.savePrivateKey(userId, privateKey, certificate)
                Arrays.fill(privateKey.encoded, 0.toByte())
                goToMessengerFragment()
            } else {
                Arrays.fill(privateKey.encoded, 0.toByte())
                showErrorTextView("Ошибка: Нет сети!")
                wasConnectionError = true
                binding.masterKeyEditText.isEnabled = false
                publicKeyString = ""
                encryptedPrivateKey = ""
                binding.doneButton.isEnabled = true
            }
        }
    }

    private fun showCountdownDialog() {
        val dialog = SaveKeyDialogFragment()
        dialog.setSaveButtonClickListener(this)
        dialog.show(parentFragmentManager, "SaveKeyDialogFragment")
    }

    private fun goToMessengerFragment() {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, MessengerFragment(), "MESSENGER_FRAGMENT_TAG3")
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