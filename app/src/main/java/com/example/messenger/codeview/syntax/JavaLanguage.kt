package com.example.messenger.codeview.syntax

import android.content.Context
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import com.amrdeveloper.codeview.Code
import com.amrdeveloper.codeview.CodeView
import com.amrdeveloper.codeview.Keyword
import com.example.messenger.R
import java.util.regex.Pattern

object JavaLanguage {
    //Language Keywords
    private val PATTERN_KEYWORDS: Pattern = Pattern.compile(
        "\\b(abstract|boolean|break|byte|case|catch" +
                "|char|class|continue|default|do|double|else" +
                "|enum|extends|final|finally|float|for|if" +
                "|implements|import|instanceof|int|interface" +
                "|long|native|new|null|package|private|protected" +
                "|public|return|short|static|strictfp|super|switch" +
                "|synchronized|this|throw|transient|try|void|volatile|while)\\b"
    )

    private val PATTERN_BUILTINS: Pattern = Pattern.compile("[,:;[->]{}()]")
    private val PATTERN_SINGLE_LINE_COMMENT: Pattern = Pattern.compile("//[^\\n]*")
    private val PATTERN_MULTI_LINE_COMMENT: Pattern =
        Pattern.compile("/\\*[^*]*\\*+(?:[^/*][^*]*\\*+)*/")
    private val PATTERN_ATTRIBUTE: Pattern = Pattern.compile("\\.[a-zA-Z0-9_]+")
    private val PATTERN_OPERATION: Pattern =
        Pattern.compile(":|==|>|<|!=|>=|<=|->|=|%|-|-=|%=|\\+|\\+=|\\^|&|\\|::|\\?|\\*")
    private val PATTERN_GENERIC: Pattern = Pattern.compile("<[a-zA-Z0-9,<>]+>")
    private val PATTERN_ANNOTATION: Pattern = Pattern.compile("@.[a-zA-Z0-9]+")
    private val PATTERN_TODO_COMMENT: Pattern = Pattern.compile("//TODO[^\n]*")
    private val PATTERN_NUMBERS: Pattern = Pattern.compile("\\b(\\d*[.]?\\d+)\\b")
    private val PATTERN_CHAR: Pattern = Pattern.compile("'(.*?)'")
    private val PATTERN_STRING: Pattern = Pattern.compile("\"(.*?)\"")
    private val PATTERN_HEX: Pattern = Pattern.compile("0x[0-9a-fA-F]+")

    private fun Context.getColorCompat(@ColorRes colorRes: Int): Int {
        return ContextCompat.getColor(this, colorRes)
    }
    
    fun applyMonokaiTheme(context: Context, codeView: CodeView) {
        codeView.resetSyntaxPatternList()
        codeView.resetHighlighter()

        //View Background
        codeView.setBackgroundColor(context.getColorCompat(R.color.monokia_pro_black))

        //Syntax Colors
        codeView.addSyntaxPattern(PATTERN_HEX, context.getColorCompat(R.color.monokia_pro_purple))
        codeView.addSyntaxPattern(PATTERN_CHAR, context.getColorCompat(R.color.monokia_pro_green))
        codeView.addSyntaxPattern(PATTERN_STRING, context.getColorCompat(R.color.monokia_pro_orange))
        codeView.addSyntaxPattern(PATTERN_NUMBERS, context.getColorCompat(R.color.monokia_pro_purple))
        codeView.addSyntaxPattern(PATTERN_KEYWORDS, context.getColorCompat(R.color.monokia_pro_pink))
        codeView.addSyntaxPattern(PATTERN_BUILTINS, context.getColorCompat(R.color.monokia_pro_white))
        codeView.addSyntaxPattern(
            PATTERN_SINGLE_LINE_COMMENT,
            context.getColorCompat(R.color.monokia_pro_grey)
        )
        codeView.addSyntaxPattern(
            PATTERN_MULTI_LINE_COMMENT,
            context.getColorCompat(R.color.monokia_pro_grey)
        )
        codeView.addSyntaxPattern(PATTERN_ANNOTATION, context.getColorCompat(R.color.monokia_pro_pink))
        codeView.addSyntaxPattern(PATTERN_ATTRIBUTE, context.getColorCompat(R.color.monokia_pro_sky))
        codeView.addSyntaxPattern(PATTERN_GENERIC, context.getColorCompat(R.color.monokia_pro_pink))
        codeView.addSyntaxPattern(PATTERN_OPERATION, context.getColorCompat(R.color.monokia_pro_pink))
        //Default Color
        codeView.setTextColor(context.getColorCompat(R.color.monokia_pro_white))

        codeView.addSyntaxPattern(PATTERN_TODO_COMMENT, context.getColorCompat(R.color.gold))

        codeView.reHighlightSyntax()
    }

    fun applyNoctisWhiteTheme(context: Context, codeView: CodeView) {
        codeView.resetSyntaxPatternList()
        codeView.resetHighlighter()

        //View Background
        codeView.setBackgroundColor(context.getColorCompat(R.color.noctis_white))

        //Syntax Colors
        codeView.addSyntaxPattern(PATTERN_HEX, context.getColorCompat(R.color.noctis_purple))
        codeView.addSyntaxPattern(PATTERN_CHAR, context.getColorCompat(R.color.noctis_green))
        codeView.addSyntaxPattern(PATTERN_STRING, context.getColorCompat(R.color.noctis_green))
        codeView.addSyntaxPattern(PATTERN_NUMBERS, context.getColorCompat(R.color.noctis_purple))
        codeView.addSyntaxPattern(PATTERN_KEYWORDS, context.getColorCompat(R.color.noctis_pink))
        codeView.addSyntaxPattern(PATTERN_BUILTINS, context.getColorCompat(R.color.noctis_dark_blue))
        codeView.addSyntaxPattern(
            PATTERN_SINGLE_LINE_COMMENT,
            context.getColorCompat(R.color.noctis_grey)
        )
        codeView.addSyntaxPattern(
            PATTERN_MULTI_LINE_COMMENT,
            context.getColorCompat(R.color.noctis_grey)
        )
        codeView.addSyntaxPattern(PATTERN_ANNOTATION, context.getColorCompat(R.color.monokia_pro_pink))
        codeView.addSyntaxPattern(PATTERN_ATTRIBUTE, context.getColorCompat(R.color.noctis_blue))
        codeView.addSyntaxPattern(PATTERN_GENERIC, context.getColorCompat(R.color.monokia_pro_pink))
        codeView.addSyntaxPattern(PATTERN_OPERATION, context.getColorCompat(R.color.monokia_pro_pink))

        //Default Color
        codeView.setTextColor(context.getColorCompat(R.color.noctis_orange))

        codeView.addSyntaxPattern(PATTERN_TODO_COMMENT, context.getColorCompat(R.color.gold))

        codeView.reHighlightSyntax()
    }

    fun applyFiveColorsDarkTheme(context: Context, codeView: CodeView) {
        codeView.resetSyntaxPatternList()
        codeView.resetHighlighter()

        //View Background
        codeView.setBackgroundColor(context.getColorCompat(R.color.five_dark_black))

        //Syntax Colors
        codeView.addSyntaxPattern(PATTERN_HEX, context.getColorCompat(R.color.five_dark_purple))
        codeView.addSyntaxPattern(PATTERN_CHAR, context.getColorCompat(R.color.five_dark_yellow))
        codeView.addSyntaxPattern(PATTERN_STRING, context.getColorCompat(R.color.five_dark_yellow))
        codeView.addSyntaxPattern(PATTERN_NUMBERS, context.getColorCompat(R.color.five_dark_purple))
        codeView.addSyntaxPattern(PATTERN_KEYWORDS, context.getColorCompat(R.color.five_dark_purple))
        codeView.addSyntaxPattern(PATTERN_BUILTINS, context.getColorCompat(R.color.five_dark_white))
        codeView.addSyntaxPattern(
            PATTERN_SINGLE_LINE_COMMENT,
            context.getColorCompat(R.color.five_dark_grey)
        )
        codeView.addSyntaxPattern(
            PATTERN_MULTI_LINE_COMMENT,
            context.getColorCompat(R.color.five_dark_grey)
        )
        codeView.addSyntaxPattern(PATTERN_ANNOTATION, context.getColorCompat(R.color.five_dark_purple))
        codeView.addSyntaxPattern(PATTERN_ATTRIBUTE, context.getColorCompat(R.color.five_dark_blue))
        codeView.addSyntaxPattern(PATTERN_GENERIC, context.getColorCompat(R.color.five_dark_purple))
        codeView.addSyntaxPattern(PATTERN_OPERATION, context.getColorCompat(R.color.five_dark_purple))

        //Default Color
        codeView.setTextColor(context.getColorCompat(R.color.five_dark_white))

        codeView.addSyntaxPattern(PATTERN_TODO_COMMENT, context.getColorCompat(R.color.gold))

        codeView.reHighlightSyntax()
    }

    fun applyOrangeBoxTheme(context: Context, codeView: CodeView) {
        codeView.resetSyntaxPatternList()
        codeView.resetHighlighter()

        //View Background
        codeView.setBackgroundColor(context.getColorCompat(R.color.orange_box_black))

        //Syntax Colors
        codeView.addSyntaxPattern(PATTERN_HEX, context.getColorCompat(R.color.gold))
        codeView.addSyntaxPattern(PATTERN_CHAR, context.getColorCompat(R.color.orange_box_orange2))
        codeView.addSyntaxPattern(PATTERN_STRING, context.getColorCompat(R.color.orange_box_orange2))
        codeView.addSyntaxPattern(PATTERN_NUMBERS, context.getColorCompat(R.color.five_dark_purple))
        codeView.addSyntaxPattern(PATTERN_KEYWORDS, context.getColorCompat(R.color.orange_box_orange1))
        codeView.addSyntaxPattern(PATTERN_BUILTINS, context.getColorCompat(R.color.orange_box_grey))
        codeView.addSyntaxPattern(
            PATTERN_SINGLE_LINE_COMMENT,
            context.getColorCompat(R.color.orange_box_dark_grey)
        )
        codeView.addSyntaxPattern(
            PATTERN_MULTI_LINE_COMMENT,
            context.getColorCompat(R.color.orange_box_dark_grey)
        )
        codeView.addSyntaxPattern(
            PATTERN_ANNOTATION,
            context.getColorCompat(R.color.orange_box_orange1)
        )
        codeView.addSyntaxPattern(PATTERN_ATTRIBUTE, context.getColorCompat(R.color.orange_box_orange3))
        codeView.addSyntaxPattern(PATTERN_GENERIC, context.getColorCompat(R.color.orange_box_orange1))
        codeView.addSyntaxPattern(PATTERN_OPERATION, context.getColorCompat(R.color.gold))

        //Default Color
        codeView.setTextColor(context.getColorCompat(R.color.five_dark_white))

        codeView.addSyntaxPattern(PATTERN_TODO_COMMENT, context.getColorCompat(R.color.gold))

        codeView.reHighlightSyntax()
    }

    fun getKeywords(context: Context): Array<String> {
        return context.resources.getStringArray(R.array.java_keywords)
    }

    fun getCodeList(context: Context): List<Code> {
        val codeList: MutableList<Code> = ArrayList()
        val keywords = getKeywords(context)
        for (keyword in keywords) {
            codeList.add(Keyword(keyword))
        }
        return codeList
    }

    val indentationStarts: Set<Char>
        get() {
            val characterSet: MutableSet<Char> = HashSet()
            characterSet.add('{')
            return characterSet
        }

    val indentationEnds: Set<Char>
        get() {
            val characterSet: MutableSet<Char> = HashSet()
            characterSet.add('}')
            return characterSet
        }

    val commentStart: String
        get() = "//"

    val commentEnd: String
        get() = ""
}