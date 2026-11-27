package io.reader.rebuild.features.reading

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.text.TextPaint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StaticLayoutTest {
    @Test
    fun usesNativeLineBreaksAndRestoresTheSameScalarAcrossReflow() {
        val source = "甲𠀀😀Cafe\u0301。\n" + "山河墨纸，段落中的阅读位置。\n".repeat(18)
        val narrow = layout(source, 84, 20f)
        val wide = layout(source, 240, 28f)
        assertTrue(narrow.pages.size > 1)
        assertTrue(wide.pages.size > 1)
        assertEquals(27L, narrow.pages.first().start)
        assertEquals(27L, wide.pages.first().start)
        assertEquals(narrow.coordinates.chapterEnd, narrow.pages.last().end)
        assertEquals(wide.coordinates.chapterEnd, wide.pages.last().end)
        val anchor = 52L
        for (engine in listOf(narrow, wide)) {
            val current = checkNotNull(engine.pageFor(anchor))
            assertTrue(anchor in current.start until current.end)
            assertEquals("。", engine.coordinates.substring(35, 36))
            assertEquals("𠀀😀Cafe\u0301", engine.coordinates.substring(28, 35))
            assertNotNull(engine.hit(current, 0f, 0f))
        }
        val bitmap = Bitmap.createBitmap(84, 70, Bitmap.Config.ARGB_8888)
        try {
            narrow.draw(Canvas(bitmap), narrow.pages.first(), ReadingSelection(28, 35), Color.YELLOW)
            assertTrue((0 until 70).any { y -> (0 until 84).any { x -> bitmap.getPixel(x, y) != Color.TRANSPARENT } })
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun productionSurfaceUsesBundledTypefaceAndKeepsSelectionAndAnchorOnResize() {
        val surface = StaticLayoutReadingSurface(RuntimeEnvironment.getApplication())
        val chapter = TextSurfaceChapter("甲𠀀😀Cafe\u0301。\n" + "段落中的阅读位置。\n".repeat(12), 27L)
        surface.layout(0, 0, 140, 150)
        surface.open(chapter, 28L, ReadingStyle(horizontalInset = 0))
        surface.currentPosition { assertEquals(ReadingPosition(28L, "𠀀"), it) }
        surface.select(28L, 35L) { assertEquals(ReadingSelection(28L, 35L), it) }
        surface.restore(29L) { assertEquals(ReadingPosition(29L, "😀"), it) }
        surface.reflow(ReadingStyle(fontSize = 23.0, lineHeight = 46.0, horizontalInset = 0), null)
        surface.layout(0, 0, 260, 150)
        surface.currentPosition { assertEquals(ReadingPosition(29L, "😀"), it) }
        surface.currentSelection { assertEquals(ReadingSelection(28L, 35L), it) }
        surface.scrolling = true
        surface.currentPosition { assertEquals(ReadingPosition(29L, "😀"), it) }
        surface.currentSelection { assertEquals(ReadingSelection(28L, 35L), it) }
        surface.scrolling = false
        surface.currentPosition { assertEquals(ReadingPosition(29L, "😀"), it) }
        surface.restore(chapter.coordinates.chapterEnd) { assertEquals(null, it) }
        surface.currentPosition { assertEquals(ReadingPosition(29L, "😀"), it) }
        surface.retire()
        surface.currentPosition { assertEquals(null, it) }
        println("ROBOLECTRIC_STATIC_LAYOUT api=${RuntimeEnvironment.getApiLevel()} anchor=29 selection=28..35 font=18→23 width=140→260 modes=page,scroll,page")
    }

    private fun layout(source: String, width: Int, fontPx: Float) = StaticTextLayout.create(
        chapter = TextSurfaceChapter(source, chapterStartScalar = 27),
        width = width,
        pageHeight = 70,
        paint = TextPaint().apply { textSize = fontPx; color = Color.BLACK },
        lineHeightPx = fontPx * 1.5f,
        paragraphGapPx = 4,
    )
}
