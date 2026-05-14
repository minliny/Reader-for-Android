package com.reader.android.data.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlphaConfigTest {

    @Test
    fun `valid alpha values construct successfully`() {
        val a = AlphaConfig(0.5f)
        assertEquals(0.5f, a.value)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `alpha above 1_0 throws`() {
        AlphaConfig(1.5f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `alpha below 0_0 throws`() {
        AlphaConfig(-0.1f)
    }

    @Test
    fun `safe clamps out-of-range values`() {
        assertEquals(1.0f, AlphaConfig.safe(2.0f).value)
        assertEquals(0.0f, AlphaConfig.safe(-1.0f).value)
    }

    @Test
    fun `isOpaque for value 1_0`() {
        assertTrue(AlphaConfig.OPAQUE.isOpaque)
        assertFalse(AlphaConfig.HALF.isOpaque)
    }

    @Test
    fun `isTransparent for value 0_0`() {
        assertTrue(AlphaConfig.TRANSPARENT.isTransparent)
        assertFalse(AlphaConfig.HALF.isTransparent)
    }

    @Test
    fun `percentage conversion`() {
        assertEquals(100, AlphaConfig.OPAQUE.percentage)
        assertEquals(50, AlphaConfig.HALF.percentage)
        assertEquals(0, AlphaConfig.TRANSPARENT.percentage)
    }

    @Test
    fun `preset values`() {
        assertEquals(1.0f, AlphaConfig.OPAQUE.value)
        assertEquals(0.7f, AlphaConfig.SEMI_TRANSPARENT.value)
        assertEquals(0.5f, AlphaConfig.HALF.value)
        assertEquals(0.3f, AlphaConfig.LIGHT.value)
        assertEquals(0.0f, AlphaConfig.TRANSPARENT.value)
    }
}
