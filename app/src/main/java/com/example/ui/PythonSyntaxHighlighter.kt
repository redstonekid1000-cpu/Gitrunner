package com.example.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.graphics.Color
import java.util.regex.Pattern

class PythonSyntaxHighlighter : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val originalText = text.text
        val builder = AnnotatedString.Builder(originalText)

        val keywordColor = Color(0xFFFF5252) // Neon Red/Pink
        val commentColor = Color(0xFF64748B) // Slate Gray for comments
        val stringColor = Color(0xFF00E676) // Bright Green for strings
        val numberColor = Color(0xFFFFB300) // Vibrant Amber
        val builtinColor = Color(0xFF40C4FF) // Neon Blue for print, len, range etc

        // 1. Highlight numbers
        val numPattern = Pattern.compile("\\b\\d+\\.?\\d*\\b")
        val numMatcher = numPattern.matcher(originalText)
        while (numMatcher.find()) {
            builder.addStyle(
                SpanStyle(color = numberColor, fontWeight = FontWeight.Bold),
                numMatcher.start(),
                numMatcher.end()
            )
        }

        // 2. Highlight Python Built-ins
        val builtins = listOf("print", "len", "range", "str", "int", "float", "dict", "list", "tuple", "set", "input", "append", "open", "sys", "math", "json")
        for (builtin in builtins) {
            val wordPattern = Pattern.compile("\\b$builtin\\b")
            val wordMatcher = wordPattern.matcher(originalText)
            while (wordMatcher.find()) {
                builder.addStyle(
                    SpanStyle(color = builtinColor, fontWeight = FontWeight.Medium),
                    wordMatcher.start(),
                    wordMatcher.end()
                )
            }
        }

        // 3. Highlight Keywords
        val keywords = listOf(
            "def", "class", "import", "from", "as", "if", "elif", "else", "for", "while", "in", "return", 
            "try", "except", "finally", "with", "break", "continue", "pass", "not", "and", "or", "lambda", "global"
        )
        for (keyword in keywords) {
            val wordPattern = Pattern.compile("\\b$keyword\\b")
            val wordMatcher = wordPattern.matcher(originalText)
            while (wordMatcher.find()) {
                builder.addStyle(
                    SpanStyle(color = keywordColor, fontWeight = FontWeight.Bold),
                    wordMatcher.start(),
                    wordMatcher.end()
                )
            }
        }

        // 4. Highlight Python special Constants (True, False, None)
        val constants = listOf("True", "False", "None")
        for (const in constants) {
            val wordPattern = Pattern.compile("\\b$const\\b")
            val wordMatcher = wordPattern.matcher(originalText)
            while (wordMatcher.find()) {
                builder.addStyle(
                    SpanStyle(color = numberColor, fontWeight = FontWeight.Bold),
                    wordMatcher.start(),
                    wordMatcher.end()
                )
            }
        }

        // 5. Highlight strings (double & single quotes, including content inside print)
        val strPattern = Pattern.compile("\"[^\"]*\"|'[^']*'")
        val strMatcher = strPattern.matcher(originalText)
        while (strMatcher.find()) {
            builder.addStyle(
                SpanStyle(color = stringColor),
                strMatcher.start(),
                strMatcher.end()
            )
        }

        // 6. Highlight comments starting with #
        val commentPattern = Pattern.compile("#.*")
        val commentMatcher = commentPattern.matcher(originalText)
        while (commentMatcher.find()) {
            builder.addStyle(
                SpanStyle(color = commentColor, fontFamily = FontFamily.Monospace),
                commentMatcher.start(),
                commentMatcher.end()
            )
        }

        return TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
    }
}
