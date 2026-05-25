package com.reader.android.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// Stitch MCP-aligned: Minimalist-Utilitarian, flat, cool neutral palette
// Design: "disappearing UI", no card containers, 1px hairlines, no shadows
@Immutable
data class ReaderColors(
    val paperBg: Color,
    val bodyText: Color,
    val controlInk: Color,
    val primary: Color,
    val bottomBarBg: Color,
    val floatingControlBg: Color,
    val floatingControlBgAlt: Color,
    val quickButtonBg: Color,
    val controlBorder: Color,
    val handoffBoundary: Color,
    val mutedTrack: Color,
    val softTopBg: Color,
    val metaBg: Color
) {
    companion object {
        val Day = ReaderColors(
            paperBg = Color(0xFFF8F9FF),       // surface
            bodyText = Color(0xFF181C22),       // on-surface
            controlInk = Color(0xFF404752),     // on-surface-variant
            primary = Color(0xFF0061A4),        // Stitch primary
            bottomBarBg = Color(0xFFF1F3FC),    // surface-container-low
            floatingControlBg = Color(0xFFFFFFFF), // surface-lowest
            floatingControlBgAlt = Color(0xFFEBEEF6), // surface-container
            quickButtonBg = Color(0xFFF1F3FC),  // surface-container-low
            controlBorder = Color(0xFFBFC7D4),  // outline-variant
            handoffBoundary = Color(0x18707883), // outline @ 10%
            mutedTrack = Color(0xFFDFE2EA),     // surface-variant
            softTopBg = Color(0xF0F8F9FF),      // surface @ 94%
            metaBg = Color(0xFFEBEEF6)          // surface-container
        )

        val Night = ReaderColors(
            paperBg = Color(0xFF121212),
            bodyText = Color(0xFFE0E0E0),
            controlInk = Color(0xFFBFC7D4),
            primary = Color(0xFF2196F3),        // primary-container
            bottomBarBg = Color(0xFF1E1E1E),
            floatingControlBg = Color(0xFF2D2D2D),
            floatingControlBgAlt = Color(0xFF252525),
            quickButtonBg = Color(0xFF252525),
            controlBorder = Color(0x1EBFC7D4),
            handoffBoundary = Color(0x18707883),
            mutedTrack = Color(0x1EBFC7D4),
            softTopBg = Color(0xF0121212),
            metaBg = Color(0xFF1E1E1E)
        )
    }
}

enum class ReaderVisualMode {
    Day,
    Night
}
