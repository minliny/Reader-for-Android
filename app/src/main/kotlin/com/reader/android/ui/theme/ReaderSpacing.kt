package com.reader.android.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class ReaderSpacing(
    val xs: Dp,
    val sm: Dp,
    val md: Dp,
    val lg: Dp,
    val xl: Dp,
    val screenPadding: Dp,
    val cardPadding: Dp,
    val listGap: Dp,
    val controlGap: Dp,
    val bottomBarHeight: Dp,
    val bottomSafeGap: Dp,
    val floatingPageControlHeight: Dp,
    val quickCircleSize: Dp,
    val quickCircleGap: Dp,
    val readerOverlayTopGap: Dp,
    val readerOverlayBottomGap: Dp,
    val brightnessDockInset: Dp
) {
    companion object {
        val Default = ReaderSpacing(
            xs = 8.dp,
            sm = 12.dp,
            md = 16.dp,
            lg = 24.dp,
            xl = 48.dp,
            screenPadding = 16.dp,
            cardPadding = 14.dp,
            listGap = 10.dp,
            controlGap = 16.dp,
            bottomBarHeight = 68.dp,
            bottomSafeGap = 8.dp,
            floatingPageControlHeight = 52.dp,
            quickCircleSize = 48.dp,
            quickCircleGap = 20.dp,
            readerOverlayTopGap = 14.dp,
            readerOverlayBottomGap = 14.dp,
            brightnessDockInset = 12.dp
        )
    }
}
