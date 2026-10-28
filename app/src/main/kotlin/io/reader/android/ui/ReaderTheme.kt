package io.reader.android.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.reader.android.R

object ReaderFonts {
    val Serif = FontFamily(Font(R.font.reader_serif_regular), Font(R.font.reader_serif_bold, FontWeight.Bold))
    val Sans = FontFamily(Font(R.font.reader_sans))
}

@Composable
fun ReaderTheme(theme: ReaderAppTheme, content: @Composable () -> Unit) {
    val night = theme == ReaderAppTheme.Dark || (theme == ReaderAppTheme.System && isSystemInDarkTheme())
    MaterialTheme(
        colorScheme = if (night) ReaderNightColors else ReaderDayColors,
        typography = Typography(
            headlineLarge = TextStyle(fontFamily = ReaderFonts.Serif, fontWeight = FontWeight.Bold, fontSize = 29.sp, lineHeight = 38.sp),
            headlineMedium = TextStyle(fontFamily = ReaderFonts.Serif, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 33.sp),
            titleLarge = TextStyle(fontFamily = ReaderFonts.Serif, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 28.sp),
            titleMedium = TextStyle(fontFamily = ReaderFonts.Serif, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 20.sp),
            bodyLarge = TextStyle(fontFamily = ReaderFonts.Sans, fontSize = 15.sp, lineHeight = 23.sp),
            bodyMedium = TextStyle(fontFamily = ReaderFonts.Sans, fontSize = 13.sp, lineHeight = 21.sp),
            bodySmall = TextStyle(fontFamily = ReaderFonts.Sans, fontSize = 11.sp, lineHeight = 16.sp),
            labelLarge = TextStyle(fontFamily = ReaderFonts.Sans, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 20.sp),
            labelMedium = TextStyle(fontFamily = ReaderFonts.Sans, fontWeight = FontWeight.Bold, fontSize = 11.sp, lineHeight = 18.sp),
        ), content = content,
    )
}

