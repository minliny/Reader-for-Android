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
    fun `FontFamily enum covers all six contract font families`() {
        assertEquals(6, FontFamily.entries.size)
    }

    @Test
    fun `FontFamily display names follow Reader 2 appearance labels`() {
        assertEquals("系统", FontFamily.SYSTEM.displayName)
        assertEquals("宋体", FontFamily.SERIF.displayName)
        assertEquals("黑体", FontFamily.SANS_SERIF.displayName)
        assertEquals("等宽", FontFamily.MONO.displayName)
        assertEquals("楷体", FontFamily.KAI.displayName)
        assertEquals("仿宋", FontFamily.FANGSONG.displayName)
    }
}
