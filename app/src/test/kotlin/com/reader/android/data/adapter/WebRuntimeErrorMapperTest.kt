package com.reader.android.data.adapter

import com.reader.android.data.bridge.ReaderErrorCode
import org.junit.Assert.assertEquals
import org.junit.Test

class WebRuntimeErrorMapperTest {

    @Test
    fun `SYNTAX maps to PARSE`() {
        assertEquals(ReaderErrorCode.PARSE, WebRuntimeErrorMapper.map(JsErrorType.SYNTAX))
    }

    @Test
    fun `RUNTIME maps to PARSE`() {
        assertEquals(ReaderErrorCode.PARSE, WebRuntimeErrorMapper.map(JsErrorType.RUNTIME))
    }

    @Test
    fun `TIMEOUT maps to TIMEOUT`() {
        assertEquals(ReaderErrorCode.TIMEOUT, WebRuntimeErrorMapper.map(JsErrorType.TIMEOUT))
    }

    @Test
    fun `SECURITY maps to FORBIDDEN`() {
        assertEquals(ReaderErrorCode.FORBIDDEN, WebRuntimeErrorMapper.map(JsErrorType.SECURITY))
    }

    @Test
    fun `UNKNOWN maps to UNKNOWN`() {
        assertEquals(ReaderErrorCode.UNKNOWN, WebRuntimeErrorMapper.map(JsErrorType.UNKNOWN))
    }

    @Test
    fun `all JsErrorType values are mapped`() {
        JsErrorType.entries.forEach { type ->
            val code = WebRuntimeErrorMapper.map(type)
            // Every JS error type must map to a valid ReaderErrorCode
            assert(code is ReaderErrorCode)
        }
    }
}
