package io.reader.android.ui

import androidx.compose.ui.unit.Constraints
import kotlin.math.roundToInt

/** Rejects unrepresentable full-width layouts without changing image or anchor geometry. */
internal object ReaderMangaImageLayoutAdmission {
    fun accepts(width: Int, height: Int, contentWidthPx: Int): Boolean {
        if (width <= 0 || height <= 0 || contentWidthPx <= 0) return false
        // Match AspectRatioNode.tryMaxWidth in the pinned Compose implementation.
        val ratio = width.toFloat() / height
        if (!ratio.isFinite() || ratio <= 0f) return false
        val scaledHeight = contentWidthPx / ratio
        if (!scaledHeight.isFinite() || scaledHeight <= 0f) return false
        val heightPx = scaledHeight.roundToInt()
        if (heightPx <= 0) return false
        return try {
            Constraints.fixed(contentWidthPx, heightPx)
            true
        } catch (_: IllegalArgumentException) {
            false
        }
    }
}
