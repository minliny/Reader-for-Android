package com.reader.android.data.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorModelTest {

    @Test
    fun `ARGB decomposition is correct`() {
        val color = ColorModel(0xAABBCCDD)
        assertEquals(0xAA, color.alpha)
        assertEquals(0xBB, color.red)
        assertEquals(0xCC, color.green)
        assertEquals(0xDD, color.blue)
    }

    @Test
    fun `fromHex parses 6-char hex with implied alpha`() {
        val color = ColorModel.fromHex("#FF8844")
        assertNotNull(color)
        assertEquals(0xFF, color!!.alpha) // implied opaque
        assertEquals(0xFF, color.red)
        assertEquals(0x88, color.green)
        assertEquals(0x44, color.blue)
    }

    @Test
    fun `fromHex parses 8-char hex with explicit alpha`() {
        val color = ColorModel.fromHex("#80FF8844")
        assertNotNull(color)
        assertEquals(0x80, color!!.alpha)
    }

    @Test
    fun `fromHex returns null for invalid input`() {
        assertNull(ColorModel.fromHex("notacolor"))
        assertNull(ColorModel.fromHex("#GGG"))
    }

    @Test
    fun `toHexString produces valid format`() {
        val color = ColorModel(0x80FF8844)
        assertEquals("#80FF8844", color.toHexString())
    }

    @Test
    fun `isDark returns true for dark colors`() {
        assertTrue(ColorModel.DARK_BG.isDark)
        assertTrue(ColorModel.BLACK.isDark)
    }

    @Test
    fun `isDark returns false for light colors`() {
        assertFalse(ColorModel.WHITE.isDark)
        assertFalse(ColorModel.LIGHT_TEXT.isDark)
    }

    @Test
    fun `preset colors are defined`() {
        assertEquals(0xFFFFFFFF, ColorModel.WHITE.argb)
        assertEquals(0xFF000000, ColorModel.BLACK.argb)
        assertEquals(0xFF6200EE, ColorModel.DEFAULT_ACCENT.argb)
        assertEquals(0xFFBB86FC, ColorModel.NIGHT_ACCENT.argb)
    }

    @Test
    fun `fromHex without hash prefix works`() {
        val color = ColorModel.fromHex("FF8844")
        assertNotNull(color)
        assertEquals(0xFF, color!!.red)
    }
}
