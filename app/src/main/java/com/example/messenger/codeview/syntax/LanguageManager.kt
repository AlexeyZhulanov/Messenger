package com.example.messenger.codeview.syntax

import android.content.Context
import com.amrdeveloper.codeview.Code
import com.amrdeveloper.codeview.CodeView


enum class LanguageName {
    JAVA,
    PYTHON,
    GO_LANG
}

enum class ThemeName {
    MONOKAI,
    NOCTIS_WHITE,
    FIVE_COLOR,
    ORANGE_BOX
}

class LanguageManager(private val context: Context, private val codeView: CodeView) {
    fun applyTheme(language: LanguageName, theme: ThemeName?) {
        when (theme) {
            ThemeName.MONOKAI -> applyMonokaiTheme(language)
            ThemeName.NOCTIS_WHITE -> applyNoctisWhiteTheme(language)
            ThemeName.FIVE_COLOR -> applyFiveColorsDarkTheme(language)
            ThemeName.ORANGE_BOX -> applyOrangeBoxTheme(language)
            else -> return
        }
    }

    fun getLanguageKeywords(language: LanguageName?): Array<String> {
        return when (language) {
            LanguageName.JAVA -> JavaLanguage.getKeywords(context)
            LanguageName.PYTHON -> PythonLanguage.getKeywords(context)
            LanguageName.GO_LANG -> GoLanguage.getKeywords(context)
            else -> arrayOf()
        }
    }

    fun getLanguageCodeList(language: LanguageName?): List<Code> {
        return when (language) {
            LanguageName.JAVA -> JavaLanguage.getCodeList(context)
            LanguageName.PYTHON -> PythonLanguage.getCodeList(context)
            LanguageName.GO_LANG -> GoLanguage.getCodeList(context)
            else -> ArrayList()
        }
    }

    fun getLanguageIndentationStarts(language: LanguageName?): Set<Char> {
        return when (language) {
            LanguageName.JAVA -> JavaLanguage.indentationStarts
            LanguageName.PYTHON -> PythonLanguage.indentationStarts
            LanguageName.GO_LANG -> GoLanguage.indentationStarts
            else -> HashSet()
        }
    }

    fun getLanguageIndentationEnds(language: LanguageName?): Set<Char> {
        return when (language) {
            LanguageName.JAVA -> JavaLanguage.indentationEnds
            LanguageName.PYTHON -> PythonLanguage.indentationEnds
            LanguageName.GO_LANG -> GoLanguage.indentationEnds
            else -> HashSet()
        }
    }

    fun getCommentStart(language: LanguageName?): String {
        return when (language) {
            LanguageName.JAVA -> JavaLanguage.commentStart
            LanguageName.PYTHON -> PythonLanguage.commentStart
            LanguageName.GO_LANG -> GoLanguage.commentStart
            else -> ""
        }
    }

    fun getCommentEnd(language: LanguageName?): String {
        return when (language) {
            LanguageName.JAVA -> JavaLanguage.commentEnd
            LanguageName.PYTHON -> PythonLanguage.commentEnd
            LanguageName.GO_LANG -> GoLanguage.commentEnd
            else -> ""
        }
    }

    private fun applyMonokaiTheme(language: LanguageName) {
        when (language) {
            LanguageName.JAVA -> JavaLanguage.applyMonokaiTheme(context, codeView)
            LanguageName.PYTHON -> PythonLanguage.applyMonokaiTheme(context, codeView)
            LanguageName.GO_LANG -> GoLanguage.applyMonokaiTheme(context, codeView)
        }
    }

    private fun applyNoctisWhiteTheme(language: LanguageName) {
        when (language) {
            LanguageName.JAVA -> JavaLanguage.applyNoctisWhiteTheme(context, codeView)
            LanguageName.PYTHON -> PythonLanguage.applyNoctisWhiteTheme(context, codeView)
            LanguageName.GO_LANG -> GoLanguage.applyNoctisWhiteTheme(context, codeView)
        }
    }

    private fun applyFiveColorsDarkTheme(language: LanguageName) {
        when (language) {
            LanguageName.JAVA -> JavaLanguage.applyFiveColorsDarkTheme(context, codeView)
            LanguageName.PYTHON -> PythonLanguage.applyFiveColorsDarkTheme(context, codeView)
            LanguageName.GO_LANG -> GoLanguage.applyFiveColorsDarkTheme(context, codeView)
        }
    }

    private fun applyOrangeBoxTheme(language: LanguageName) {
        when (language) {
            LanguageName.JAVA -> JavaLanguage.applyOrangeBoxTheme(context, codeView)
            LanguageName.PYTHON -> PythonLanguage.applyOrangeBoxTheme(context, codeView)
            LanguageName.GO_LANG -> GoLanguage.applyOrangeBoxTheme(context, codeView)
        }
    }
}