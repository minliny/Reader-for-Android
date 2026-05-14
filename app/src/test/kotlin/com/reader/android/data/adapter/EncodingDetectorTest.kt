package com.reader.android.data.adapter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EncodingDetectorTest {

    @Test
    fun `detect UTF-8 BOM with high confidence`() {
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte(), 'a'.code.toByte())
        val result = EncodingDetector.detect(bytes)
        assertEquals("UTF-8", result.encoding)
        assertEquals(1.0f, result.confidence)
        assertTrue(result.hasBom)
    }

    @Test
    fun `detect UTF-16LE BOM`() {
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 'a'.code.toByte(), 0x00)
        val result = EncodingDetector.detect(bytes)
        assertEquals("UTF-16LE", result.encoding)
        assertTrue(result.hasBom)
    }

    @Test
    fun `valid UTF-8 without BOM has high confidence`() {
        val bytes = "Hello 世界".toByteArray(Charsets.UTF_8)
        val result = EncodingDetector.detect(bytes)
        assertEquals("UTF-8", result.encoding)
        assertTrue(result.confidence >= 0.8f)
    }

    @Test
    fun `high byte ratio suggests GBK`() {
        // Create bytes with many high bytes (simulating GBK text)
        val bytes = ByteArray(20) { 0xA1.toByte() } // all high bytes
        val result = EncodingDetector.detect(bytes)
        assertEquals("GBK", result.encoding)
    }
}
