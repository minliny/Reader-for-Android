package com.reader.android.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// Stitch HTML Tailwind config aligned: warm paper palette
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
            paperBg = Color(0xFFFFF8F4),          // surface
            bodyText = Color(0xFF1F1B17),          // on-surface
            controlInk = Color(0xFF41484C),        // on-surface-variant
            primary = Color(0xFF366179),           // primary
            bottomBarBg = Color(0xFFFBF2EB),       // surface-container-low
            floatingControlBg = Color(0xFFFBF2EB), // surface-container-low
            floatingControlBgAlt = Color(0xFFEAE1DA), // surface-variant
            quickButtonBg = Color(0xFFFBF2EB),     // surface-container-low
            controlBorder = Color(0xFFC1C7CD),     // outline-variant
            handoffBoundary = Color(0x1872787D),
            mutedTrack = Color(0xFFEAE1DA),        // surface-variant
            softTopBg = Color(0xF0FFF8F4),
            metaBg = Color(0xFFF5ECE6)             // surface-container
        )

        val Night = ReaderColors(
            paperBg = Color(0xFF121212),
            bodyText = Color(0xFFE0E0E0),
            controlInk = Color(0xFFBFC7D4),
            primary = Color(0xFF7FABC8),
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

enum class ReaderVisualMode { Day, Night }
