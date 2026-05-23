package com.reader.android.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@Immutable
data class ReaderTypography(
    val appTitle: TextStyle,
    val pageTitle: TextStyle,
    val sectionTitle: TextStyle,
    val bookTitle: TextStyle,
    val bookMeta: TextStyle,
    val readerBody: TextStyle,
    val readerChapterTitle: TextStyle,
    val readerControlLabel: TextStyle,
    val readerSmallMeta: TextStyle,
    val stateTitle: TextStyle,
    val stateMessage: TextStyle
) {
    companion object {
        val Default = ReaderTypography(
            appTitle = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold),
            pageTitle = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold),
            sectionTitle = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold),
            bookTitle = TextStyle(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold),
            bookMeta = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal),
            readerBody = TextStyle(fontSize = 18.sp, lineHeight = 31.sp, fontWeight = FontWeight.Normal),
            readerChapterTitle = TextStyle(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold),
            readerControlLabel = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
            readerSmallMeta = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal),
            stateTitle = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold),
            stateMessage = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal)
        )
    }
}
