package com.reader.android.data.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeConfigTest {

    @Test
    fun `default day theme has white background`() {
        val config = ThemeConfig()
        assertEquals(0xFFFFFFFF, config.backgroundColor)
        assertEquals(0xFF000000, config.textColor)
    }

    @Test
    fun `default night theme has dark background`() {
        val pair = ThemePair()
        assertEquals(0xFF1E1E1E, pair.nightTheme.backgroundColor)
        assertEquals(0xFFE0E0E0, pair.nightTheme.textColor)
    }

    @Test
    fun `day and night themes have different backgrounds`() {
        val pair = ThemePair()
        assertNotEquals(pair.dayTheme.backgroundColor, pair.nightTheme.backgroundColor)
    }

    @Test
    fun `day and night themes have different text colors`() {
        val pair = ThemePair()
        assertNotEquals(pair.dayTheme.textColor, pair.nightTheme.textColor)
    }

    @Test
    fun `custom ThemeConfig accepts all fields`() {
        val config = ThemeConfig(
            backgroundColor = 0xFFF5F5DC,
            textColor = 0xFF333333,
            accentColor = 0xFFFF5722,
            backgroundAlpha = 0.85f
        )
        assertEquals(0xFFF5F5DC, config.backgroundColor)
        assertEquals(0xFF333333, config.textColor)
        assertEquals(0xFFFF5722, config.accentColor)
        assertEquals(0.85f, config.backgroundAlpha)
    }

    @Test
    fun `ThemePair can be constructed with custom configs`() {
        val day = ThemeConfig(backgroundColor = 0xFFFFF8E1)
        val night = ThemeConfig(backgroundColor = 0xFF121212)
        val pair = ThemePair(dayTheme = day, nightTheme = night)
        assertEquals(0xFFFFF8E1, pair.dayTheme.backgroundColor)
        assertEquals(0xFF121212, pair.nightTheme.backgroundColor)
    }

    @Test
    fun `backgroundAlpha ranges from 0 to 1`() {
        val opaque = ThemeConfig(backgroundAlpha = 1.0f)
        val transparent = ThemeConfig(backgroundAlpha = 0.0f)
        assertEquals(1.0f, opaque.backgroundAlpha)
        assertEquals(0.0f, transparent.backgroundAlpha)
    }
}
