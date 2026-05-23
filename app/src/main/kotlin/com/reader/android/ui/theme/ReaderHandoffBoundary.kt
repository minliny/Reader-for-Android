package com.reader.android.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.debugInspectorInfo
import androidx.compose.ui.unit.dp

fun Modifier.readerHandoffBoundary(
    enabled: Boolean = false,
    label: String? = null
): Modifier {
    if (!enabled) return this
    return this.then(
        Modifier.drawBehind {
            val strokeWidth = 1.dp.toPx()
            drawRoundRect(
                color = ReaderColors.Day.handoffBoundary,
                size = Size(size.width, size.height),
                cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
                style = Stroke(
                    width = strokeWidth,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))
                )
            )
        }
    )
}

@Suppress("unused")
private val readerHandoffBoundaryInspector = debugInspectorInfo {
    name = "readerHandoffBoundary"
    properties["defaultEnabled"] = false
    properties["shape"] = RoundedCornerShape(8.dp)
}
