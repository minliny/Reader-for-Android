package com.reader.android.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

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
            paperBg = Color(0xFFFFF8F4),
            bodyText = Color(0xFF53433F),
            controlInk = Color(0xFF3F4D52),
            primary = Color(0xFF366179),
            bottomBarBg = Color(0xFFE9DED6),
            floatingControlBg = Color(0xFFEFE2D8),
            floatingControlBgAlt = Color(0xFFEADBD0),
            quickButtonBg = Color(0xFFF7EBE1),
            controlBorder = Color(0x1F3F4D52),
            handoffBoundary = Color(0x2E3F4D52),
            mutedTrack = Color(0x293F4D52),
            softTopBg = Color(0xEBFFF8F4),
            metaBg = Color(0xEFFBF2EB)
        )

        val Night = ReaderColors(
            paperBg = Color(0xFF181F22),
            bodyText = Color(0xFFD8CCC4),
            controlInk = Color(0xFFD7E1E5),
            primary = Color(0xFF8FB6CA),
            bottomBarBg = Color(0xFF263238),
            floatingControlBg = Color(0xFF223037),
            floatingControlBgAlt = Color(0xFF2B3B43),
            quickButtonBg = Color(0xFF2F4149),
            controlBorder = Color(0x24D7E1E5),
            handoffBoundary = Color(0x2ED7E1E5),
            mutedTrack = Color(0x29D7E1E5),
            softTopBg = Color(0xEB181F22),
            metaBg = Color(0xEF1F2A2F)
        )
    }
}

enum class ReaderVisualMode {
    Day,
    Night
}
