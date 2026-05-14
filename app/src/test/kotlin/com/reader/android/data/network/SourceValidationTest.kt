package com.reader.android.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceValidationTest {

    @Test
    fun `Success holds source name and URL`() {
        val result = SourceValidationResult.Success("测试源", "http://test.com")
        assertTrue(result is SourceValidationResult.Success)
        assertEquals("测试源", result.sourceName)
        assertEquals("http://test.com", result.sourceUrl)
    }

    @Test
    fun `Warning holds warnings list`() {
        val result = SourceValidationResult.Warning("源", "http://s.com",
            listOf("searchUrl is empty", "charset not specified"))
        assertTrue(result is SourceValidationResult.Warning)
        assertEquals(2, result.warnings.size)
    }

    @Test
    fun `Error holds errors list`() {
        val result = SourceValidationResult.Error("源", "bad-url",
            listOf("Invalid URL format", "sourceUrl is missing"))
        assertTrue(result is SourceValidationResult.Error)
        assertEquals(2, result.errors.size)
    }

    @Test
    fun `validation results are sealed subclasses`() {
        val success: SourceValidationResult = SourceValidationResult.Success("", "")
        val warning: SourceValidationResult = SourceValidationResult.Warning("", "", emptyList())
        val error: SourceValidationResult = SourceValidationResult.Error("", "", emptyList())
        // All three are valid SourceValidationResult instances
        assertTrue(success is SourceValidationResult)
        assertTrue(warning is SourceValidationResult)
        assertTrue(error is SourceValidationResult)
    }
}
