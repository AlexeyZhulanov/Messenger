package com.example.messenger

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.DialogFragment
import com.example.messenger.codeview.syntax.LanguageManager
import com.example.messenger.codeview.syntax.LanguageName
import com.example.messenger.codeview.syntax.ThemeName
import com.example.messenger.databinding.DialogCodePreviewBinding

class CodePreviewDialogFragment : DialogFragment() {

    private lateinit var binding: DialogCodePreviewBinding

    private var languageManager: LanguageManager? = null

    private var currentLanguage: LanguageName? = null

    override fun onStart() {
        super.onStart()
        // Настройка размеров диалога
        val width = resources.displayMetrics.widthPixels
        val height = ViewGroup.LayoutParams.WRAP_CONTENT
        dialog?.window?.setLayout(width, height)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = DialogCodePreviewBinding.inflate(inflater, container, false)
        val code = requireArguments().getString(ARG_CODE)
        val language = requireArguments().getString(ARG_LANGUAGE) ?: ""
        currentLanguage = when(language) {
            "java" -> LanguageName.JAVA
            "python" -> LanguageName.PYTHON
            "go" -> LanguageName.GO_LANG
            else -> null
        }
        configCodeView(code)
        with(binding) {
            languageNameTxt.text = language
            icClose.setOnClickListener {
                dismiss()
            }
            icIncrease.setOnClickListener {
                changeTextSize(2f)
            }
            icDecrease.setOnClickListener {
                changeTextSize(-2f)
            }
        }
        return binding.root
    }

    private fun getScaledDensity(context: Context): Float {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.resources.configuration.fontScale * context.resources.displayMetrics.density
        } else {
            @Suppress("DEPRECATION")
            context.resources.displayMetrics.scaledDensity
        }
    }

    private fun changeTextSize(bySp: Float) {
        with(binding.codeView) {
            val currentSp = textSize / getScaledDensity(requireContext())
            val newSp = currentSp + bySp

            if (newSp in 5f..40f) {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, newSp)
            }
        }
    }

    private fun configCodeView(code: String?) {
        val jetBrainsMono: Typeface? = ResourcesCompat.getFont(requireContext(), R.font.jetbrains_mono_medium)
        binding.codeView.apply {
            setTypeface(jetBrainsMono)
            // Setup Line number feature
            setEnableLineNumber(true)
            setLineNumberTextColor(Color.GRAY)
            setLineNumberTextSize(40f)
            // Setup Auto indenting feature
            setTabLength(4)
            setEnableAutoIndentation(true)
            setText(code)
            // Set block input
            keyListener = null
        }
        // Setup the language and theme with SyntaxManager helper class
        languageManager = LanguageManager(requireContext(), binding.codeView).apply {
            currentLanguage?.let {
                applyTheme(it, ThemeName.MONOKAI)
            }
        }
        configLanguageAutoIndentation()
    }

    private fun configLanguageAutoIndentation() {
        languageManager?.let {
            binding.codeView.setIndentationStarts(it.getLanguageIndentationStarts(currentLanguage))
            binding.codeView.setIndentationEnds(it.getLanguageIndentationEnds(currentLanguage))
        }
    }

    companion object {
        private const val ARG_CODE = "code"
        private const val ARG_LANGUAGE = "language"

        fun newInstance(code: String, language: String) = CodePreviewDialogFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_CODE, code)
                putString(ARG_LANGUAGE, language)
            }
        }
    }
}