package io.reader.android.ui

import androidx.compose.ui.unit.Constraints
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderMangaImageLayoutAdmissionTest {
    @Test fun admitsOnlyRepresentableOriginalAspectRatioAtTheActualContentWidth() {
        assertFalse(ReaderMangaImageLayoutAdmission.accepts(32, 32768, 1080))
        assertTrue(ReaderMangaImageLayoutAdmission.accepts(256, 512, 1080))
        assertTrue(ReaderMangaImageLayoutAdmission.accepts(32, 32768, 32))
        assertFalse(ReaderMangaImageLayoutAdmission.accepts(32, 32768, 3240))
        assertFalse(ReaderMangaImageLayoutAdmission.accepts(0, 512, 1080))
        assertFalse(ReaderMangaImageLayoutAdmission.accepts(256, -1, 1080))
        assertFalse(ReaderMangaImageLayoutAdmission.accepts(256, 512, 0))

        // Exercise the real packed-constraint edge after the same Float ratio rounding.
        val edgeWidth = 8190
        for (originalHeight in listOf(262142, 262143)) {
            val roundedHeight = (edgeWidth / (edgeWidth.toFloat() / originalHeight)).roundToInt()
            val platformAccepts = try {
                Constraints.fixed(edgeWidth, roundedHeight)
                true
            } catch (_: IllegalArgumentException) {
                false
            }
            assertEquals(originalHeight == 262142, platformAccepts)
            assertEquals(platformAccepts,
                ReaderMangaImageLayoutAdmission.accepts(edgeWidth, originalHeight, edgeWidth))
        }
    }
}
