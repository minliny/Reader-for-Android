package com.reader.android.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class ReaderElevation(
    val none: Dp,
    val subtle: Dp,
    val card: Dp,
    val floatingControl: Dp,
    val overlay: Dp
) {
    companion object {
        val Default = ReaderElevation(
            none = 0.dp,
            subtle = 1.dp,
            card = 2.dp,
            floatingControl = 2.dp,
            overlay = 2.dp
        )
    }
}
