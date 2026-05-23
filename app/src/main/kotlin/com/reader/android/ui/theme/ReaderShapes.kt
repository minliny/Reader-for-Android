package com.reader.android.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

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
            small = RoundedCornerShape(8.dp),
            medium = RoundedCornerShape(12.dp),
            large = RoundedCornerShape(16.dp),
            card = RoundedCornerShape(16.dp),
            chip = RoundedCornerShape(50),
            bottomSheet = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            floatingControl = RoundedCornerShape(50),
            quickCircle = CircleShape,
            readerOverlay = RoundedCornerShape(22.dp)
        )
    }
}
