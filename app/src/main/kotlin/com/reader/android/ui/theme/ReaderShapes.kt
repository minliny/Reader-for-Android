package com.reader.android.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

// Stitch-aligned: standard UI radius 4dp, chips 2dp, never full pills
@Immutable
data class ReaderShapes(
    val small: Shape,
    val medium: Shape,
    val large: Shape,
    val card: Shape,
    val chip: Shape,
    val bottomSheet: Shape,
    val floatingControl: Shape,
    val quickCircle: Shape,
    val readerOverlay: Shape
) {
    companion object {
        val Default = ReaderShapes(
            small = RoundedCornerShape(4.dp),
            medium = RoundedCornerShape(6.dp),
            large = RoundedCornerShape(8.dp),
            card = RoundedCornerShape(4.dp),
            chip = RoundedCornerShape(2.dp),
            bottomSheet = RoundedCornerShape(8.dp),
            floatingControl = RoundedCornerShape(6.dp),
            quickCircle = RoundedCornerShape(6.dp),
            readerOverlay = RoundedCornerShape(8.dp)
        )
    }
}
