package com.reader.android.data.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class FontConfigTest {

    @Test
    fun `default config uses system font`() {
        assertEquals(FontFamily.SYSTEM, FontConfig.DEFAULT.family)
        assertNull(FontConfig.DEFAULT.customTypeface)
    }

    @Test
    fun `preset SERIF uses serif family`() {
        assertEquals(FontFamily.SERIF, FontConfig.SERIF.family)
    }

    @Test
    fun `preset SANS uses sans-serif family`() {
        assertEquals(FontFamily.SANS_SERIF, FontConfig.SANS.family)
    }

    @Test
    fun `preset MONO uses mono family`() {
        assertEquals(FontFamily.MONO, FontConfig.MONO.family)
    }

    @Test
    fun `custom typeface sets name`() {
        val config = FontConfig.custom("LXGW WenKai")
        assertEquals("LXGW WenKai", config.customTypeface)
    }

    @Test
    fun `FontFamily enum has 4 values`() {
        assertEquals(4, FontFamily.entries.size)
    }

    @Test
    fun `FontFamily display names are Chinese`() {
        assertEquals("系统默认", FontFamily.SYSTEM.displayName)
        assertEquals("宋体/衬线", FontFamily.SERIF.displayName)
        assertEquals("黑体/无衬线", FontFamily.SANS_SERIF.displayName)
        assertEquals("等宽", FontFamily.MONO.displayName)
    }
}
