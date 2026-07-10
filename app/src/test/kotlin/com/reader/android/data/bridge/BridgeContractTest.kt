package com.reader.android.data.bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BridgeContractTest {

    // ── BridgeResult sealed class ──

    @Test
    fun `BridgeResult Success wraps data`() {
        val result: BridgeResult<String> = BridgeResult.Success("hello")
        assertEquals("hello", (result as BridgeResult.Success).data)
    }

    @Test
    fun `BridgeResult Failure wraps error`() {
        val error = ReaderError(
            code = ReaderErrorCode.NETWORK,
            stage = ReaderFailureStage.SEARCH,
            message = "timeout"
        )
        val result: BridgeResult<String> = BridgeResult.Failure(error)
        assertTrue(result is BridgeResult.Failure)
        val failure = result as BridgeResult.Failure
        assertEquals(ReaderErrorCode.NETWORK, failure.error.code)
        assertEquals(ReaderFailureStage.SEARCH, failure.error.stage)
        assertEquals("timeout", failure.error.message)
    }

    // ── ReaderErrorCode completeness ──

    @Test
    fun `all 7 ReaderErrorCode values are defined`() {
        val codes = ReaderErrorCode.entries
        assertEquals(7, codes.size)
        val expectedCodes = setOf(
            "NETWORK", "PARSE", "TIMEOUT", "NOT_FOUND",
            "UNAUTHORIZED", "FORBIDDEN", "UNKNOWN"
        )
        codes.forEach { code ->
            assertTrue("Missing error code: ${code.name}", expectedCodes.contains(code.name))
        }
    }

    // ── ReaderFailureStage completeness ──

    @Test
    fun `all 4 ReaderFailureStage values are defined`() {
        val stages = ReaderFailureStage.entries
        assertEquals(4, stages.size)
        val expectedStages = setOf("SEARCH", "TOC", "CONTENT", "BOOK_INFO")
        stages.forEach { stage ->
            assertTrue("Missing stage: ${stage.name}", expectedStages.contains(stage.name))
        }
    }

    // ── ReaderError construction ──

    @Test
    fun `ReaderError with all fields`() {
        val error = ReaderError(
            code = ReaderErrorCode.PARSE,
            stage = ReaderFailureStage.CONTENT,
            message = "HTML parse failed",
            sourceName = "笔趣阁"
        )
        assertEquals(ReaderErrorCode.PARSE, error.code)
        assertEquals(ReaderFailureStage.CONTENT, error.stage)
        assertEquals("HTML parse failed", error.message)
        assertEquals("笔趣阁", error.sourceName)
    }

    @Test
    fun `ReaderError with optional fields null`() {
        val error = ReaderError(
            code = ReaderErrorCode.UNKNOWN,
            stage = ReaderFailureStage.BOOK_INFO
        )
        assertEquals(null, error.message)
        assertEquals(null, error.sourceName)
    }
}
