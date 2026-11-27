package io.reader.rebuild.features.reading

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TextScalarMapTest {
    @Test
    fun chapterWindowMapsSupplementaryScalarsAndCombiningMarksIndependently() {
        val map = TextScalarMap("甲𠀀😀Cafe\u0301\uFFFC乙", chapterStart = 27)
        assertEquals(listOf(0, 1, 3, 5, 6, 7, 8, 9, 10, 11, 12),
            (27L..37L).map(map::utf16))
        assertEquals(listOf(27L, 28L, 29L, 30L, 31L, 32L, 33L, 34L, 35L, 36L, 37L),
            listOf(0, 1, 3, 5, 6, 7, 8, 9, 10, 11, 12).map(map::scalar))
        assertNull(map.scalar(2))
        assertNull(map.scalar(4))
        assertEquals(28L, map.scalarAtOrBefore(2)!!)
        assertEquals(29L, map.scalarAtOrBefore(4)!!)
        assertEquals("𠀀😀Cafe\u0301", map.substring(28, 35))
        assertEquals("\uFFFC", map.substring(35, 36))
        assertNull(map.utf16(26))
        assertNull(map.utf16(38))
    }
}
