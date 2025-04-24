package com.example.messenger

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.amrdeveloper.codeview.Code
import com.example.messenger.codeview.plugins.CommentManager
import com.example.messenger.codeview.plugins.SourcePositionListener
import com.example.messenger.codeview.plugins.UndoRedoManager
import com.example.messenger.codeview.syntax.LanguageManager
import com.example.messenger.codeview.syntax.LanguageName
import com.example.messenger.codeview.syntax.ThemeName
import com.example.messenger.databinding.FragmentCodeBinding
import com.example.messenger.model.Message
import com.example.messenger.model.getParcelableCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.regex.Pattern


class CodeFragment : Fragment() {

    private lateinit var binding: FragmentCodeBinding
    private var message: Message? = null
    private val baseChatViewModel: BaseChatViewModel by viewModels()

    private var languageManager: LanguageManager? = null
    private var commentManager: CommentManager? = null
    private var undoRedoManager: UndoRedoManager? = null

    private val isEdit = message != null
    private var currentLanguage = if(isEdit) {
        when(message?.codeLanguage) {
            "python" -> LanguageName.PYTHON
            "go" -> LanguageName.GO_LANG
            else -> LanguageName.JAVA
        }
    } else LanguageName.JAVA
    private var currentTheme = ThemeName.MONOKAI

    private val useModernAutoCompleteAdapter = true // todo можно добавить опцию выбора

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        message = arguments?.getParcelableCompat<Message>(ARG_MESSAGE)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().window.statusBarColor = ContextCompat.getColor(requireContext(), R.color.colorBar)
        val toolbarContainer: FrameLayout = view.findViewById(R.id.toolbar_container)
        val defaultToolbar = LayoutInflater.from(context)
            .inflate(R.layout.toolbar_code, toolbarContainer, false)
        toolbarContainer.addView(defaultToolbar)

        val langStr = when(currentLanguage) {
            LanguageName.JAVA -> "java"
            LanguageName.PYTHON -> "python"
            LanguageName.GO_LANG -> "go"
        }
        val icSend: ImageView = view.findViewById(R.id.ic_send)
        if(isEdit) {
            icSend.visibility = View.INVISIBLE
            val icEdit: ImageView = view.findViewById(R.id.ic_edit)
            icEdit.visibility = View.VISIBLE
            icEdit.setOnClickListener {
                val code = binding.codeView.text.toString()
                message?.let {
                    lifecycleScope.launch {
                        val success = baseChatViewModel.editMessage(it.id, null,
                            null, null, null, code, langStr, null)

                        if(success) requireActivity().onBackPressedDispatcher.onBackPressed() // return to chat fragment
                        else Toast.makeText(requireContext(), "Не удалось редактировать код", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } else {
            icSend.setOnClickListener {
                val code = binding.codeView.text.toString()

                 baseChatViewModel.sendMessage(null, null, null, null, code,
                     langStr, null, false, null, null, null)

                requireActivity().onBackPressedDispatcher.onBackPressed() // return to chat fragment
            }
        }
        val icRedo: ImageView = view.findViewById(R.id.ic_redo)
        icRedo.setOnClickListener {
            undoRedoManager?.redo()
        }
        val icUndo: ImageView = view.findViewById(R.id.ic_undo)
        icUndo.setOnClickListener {
            undoRedoManager?.undo()
        }
        val backArrow: ImageView = view.findViewById(R.id.back_arrow)
        backArrow.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentCodeBinding.inflate(inflater, container, false)
        configCodeView()
        configCodeViewPlugins()
        binding.optionsButton.setOnClickListener {
            showPopupMenu(it, R.menu.code_menu)
        }
        binding.icDecrease.setOnClickListener {
            changeTextSize(-2f)
        }
        binding.icIncrease.setOnClickListener {
            changeTextSize(2f)
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

    private fun configCodeView() {
        // Change default font to JetBrains Mono font
        val jetBrainsMono: Typeface? = ResourcesCompat.getFont(requireContext(), R.font.jetbrains_mono_medium)

        // Setup auto pair complete
        val pairCompleteMap: MutableMap<Char, Char> = HashMap()
        pairCompleteMap['{'] = '}'
        pairCompleteMap['['] = ']'
        pairCompleteMap['('] = ')'
        pairCompleteMap['<'] = '>'
        pairCompleteMap['"'] = '"'
        pairCompleteMap['\''] = '\''

        binding.codeView.apply {
            setTypeface(jetBrainsMono)
            // Setup Line number feature
            setEnableLineNumber(true)
            setLineNumberTextColor(Color.GRAY)
            setLineNumberTextSize(45f)
            // Setup highlighting current line
            setEnableHighlightCurrentLine(true)
            // Setup Auto indenting feature
            setTabLength(4)
            setEnableAutoIndentation(true)
            // Setup pair complete
            setPairCompleteMap(pairCompleteMap)
            enablePairComplete(true)
            enablePairCompleteCenterCursor(true)
        }
        message?.code?.let {
            binding.codeView.setText(it)
        }
        // Setup the language and theme with SyntaxManager helper class
        languageManager = LanguageManager(requireContext(), binding.codeView).apply {
            applyTheme(currentLanguage, currentTheme)
        }
        // Setup the auto complete and auto indenting for the current language
        configLanguageAutoComplete()
        configLanguageAutoIndentation()
    }

    private fun configLanguageAutoComplete() {
        if (useModernAutoCompleteAdapter) {
            // Load the code list (keywords and snippets) for the current language
            val codeList: List<Code?> = languageManager?.getLanguageCodeList(currentLanguage) ?: return

            // Use CodeViewAdapter or custom one
            val adapter = CustomCodeViewAdapter(requireContext(), codeList)

            // Add the odeViewAdapter to the CodeView
            binding.codeView.setAdapter(adapter)
        } else {
            val languageKeywords = languageManager?.getLanguageKeywords(currentLanguage) ?: return

            // Custom list item xml layout
            val layoutId: Int = R.layout.item_code_suggestion

            // TextView id to put suggestion on it
            val viewId: Int = R.id.suggestItemTextView
            val adapter: ArrayAdapter<String> = ArrayAdapter(requireContext(), layoutId, viewId, languageKeywords)

            // Add the ArrayAdapter to the CodeView
            binding.codeView.setAdapter(adapter)
        }
    }

    private fun configLanguageAutoIndentation() {
        languageManager?.let {
            binding.codeView.setIndentationStarts(it.getLanguageIndentationStarts(currentLanguage))
            binding.codeView.setIndentationEnds(it.getLanguageIndentationEnds(currentLanguage))
        }
    }

    private fun configCodeViewPlugins() {
        commentManager = CommentManager(binding.codeView)
        configCommentInfo()

        undoRedoManager = UndoRedoManager(binding.codeView).apply {
            connect()
        }
        configLanguageName()
        binding.sourcePositionTxt.text = getString(R.string.source_position, 0, 0)
        configSourcePositionListener()
    }

    private fun configCommentInfo() {
        commentManager?.let { cm ->
            languageManager?.let { lm ->
                cm.setCommentStart(lm.getCommentStart(currentLanguage))
                cm.setCommentEnd(lm.getCommentEnd(currentLanguage))
            }
        }
    }

    private fun configLanguageName() {
        binding.languageNameTxt.text = currentLanguage.name.lowercase(Locale.getDefault())
    }

    private fun configSourcePositionListener() {
        val sourcePositionListener = SourcePositionListener(binding.codeView)
        sourcePositionListener.setOnPositionChanged { line: Int, column: Int ->
            binding.sourcePositionTxt.text = getString(R.string.source_position, line, column)
        }
    }

    private fun changeTheEditorLanguage(languageId: Int) {
        val oldLanguage = currentLanguage
        when (languageId) {
            R.id.language_java -> currentLanguage = LanguageName.JAVA
            R.id.language_python -> currentLanguage = LanguageName.PYTHON
            R.id.language_go -> currentLanguage = LanguageName.GO_LANG
        }

        if (currentLanguage != oldLanguage) {
            languageManager?.applyTheme(currentLanguage, currentTheme)
            configLanguageName()
            configLanguageAutoComplete()
            configLanguageAutoIndentation()
            configCommentInfo()
        }
    }

    private fun changeTheEditorTheme(themeId: Int) {
        val oldTheme = currentTheme
        when (themeId) {
            R.id.theme_monokia -> currentTheme = ThemeName.MONOKAI
            R.id.theme_noctics -> currentTheme = ThemeName.NOCTIS_WHITE
            R.id.theme_five_color -> currentTheme = ThemeName.FIVE_COLOR
            R.id.theme_orange_box -> currentTheme = ThemeName.ORANGE_BOX
        }

        if (currentTheme != oldTheme) {
            languageManager?.applyTheme(currentLanguage, currentTheme)
        }
    }

    private fun toggleRelativeLineNumber() {
        var isRelativeLineNumberEnabled: Boolean = binding.codeView.isLineRelativeNumberEnabled
        isRelativeLineNumberEnabled = !isRelativeLineNumberEnabled
        binding.codeView.setEnableRelativeLineNumber(isRelativeLineNumberEnabled)
    }

    private fun launchEditorButtonSheet() {
        val dialog = BottomSheetDialog(requireContext())
        dialog.setContentView(R.layout.fragment_bottom_sheet_code)
        dialog.window?.setDimAmount(0f)

        val searchEdit = dialog.findViewById<EditText>(R.id.search_edit)
        val replacementEdit = dialog.findViewById<EditText>(R.id.replacement_edit)

        val findPrevAction = dialog.findViewById<ImageButton>(R.id.find_prev_action)
        val findNextAction = dialog.findViewById<ImageButton>(R.id.find_next_action)
        val replacementAction = dialog.findViewById<ImageButton>(R.id.replace_action)

        searchEdit?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {
            }

            override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {
            }

            override fun afterTextChanged(editable: Editable) {
                val text = editable.toString().trim { it <= ' ' }
                if (text.isEmpty()) binding.codeView.clearMatches()
                binding.codeView.findMatches(Pattern.quote(text))
            }
        })

        findPrevAction?.setOnClickListener {
            binding.codeView.findPrevMatch()
        }

        findNextAction?.setOnClickListener {
            binding.codeView.findNextMatch()
        }

        replacementAction?.setOnClickListener {
            val regex = searchEdit?.text.toString()
            val replacement = replacementEdit?.text.toString()
            binding.codeView.replaceAllMatches(regex, replacement)
        }

        dialog.setOnDismissListener { binding.codeView.clearMatches() }
        dialog.show()
    }

    private fun showPopupMenu(view: View, menuRes: Int) {
        val popupMenu = PopupMenu(requireContext(), view)
        popupMenu.menuInflater.inflate(menuRes, popupMenu.menu)
        popupMenu.setOnMenuItemClickListener { item ->
            when(item.groupId) {
                R.id.group_languages -> {
                    changeTheEditorLanguage(item.itemId)
                    true
                }
                R.id.group_themes -> {
                    changeTheEditorTheme(item.itemId)
                    true
                }
                else -> {
                    when(item.itemId) {
                        R.id.findMenu -> {
                            launchEditorButtonSheet()
                            true
                        }
                        R.id.comment -> {
                            commentManager?.commentSelected()
                            true
                        }
                        R.id.un_comment -> {
                            commentManager?.unCommentSelected()
                            true
                        }
                        R.id.clearText -> {
                            binding.codeView.setText("")
                            true
                        }
                        R.id.toggle_relative_line_number -> {
                            toggleRelativeLineNumber()
                            true
                        }
                        else -> false
                    }
                }
            }
        }
        popupMenu.show()
    }

    companion object {
        private const val ARG_MESSAGE = "arg_message"

        fun newInstance(message: Message? = null) = CodeFragment().apply {
            arguments = Bundle().apply {
                putParcelable(ARG_MESSAGE, message)
            }
        }
    }
}