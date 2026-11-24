package fr.nekotv

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.graphics.Color

object SearchHighlighting {
    fun highlight(text: String, query: String): AnnotatedString {
        if (query.isBlank()) return AnnotatedString(text)
        
        val normalizedText = Normalize.normalizeTitle(text)
        val normalizedQuery = Normalize.normalizeTitle(query)
        
        if (normalizedText.isEmpty() || normalizedQuery.isEmpty()) return AnnotatedString(text)
        
        val startIndex = normalizedText.indexOf(normalizedQuery)
        if (startIndex == -1) return AnnotatedString(text)
        
        val endIndex = startIndex + normalizedQuery.length
        
        return buildAnnotatedString {
            append(text.substring(0, startIndex))
            withStyle(style = SpanStyle(background = Color(0xFFFBC02D), color = Color.Black, fontWeight = FontWeight.Bold)) {
                append(text.substring(startIndex, endIndex))
            }
            append(text.substring(endIndex))
        }
    }
}
