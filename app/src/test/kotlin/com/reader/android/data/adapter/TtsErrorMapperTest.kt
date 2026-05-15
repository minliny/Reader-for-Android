package com.reader.android.data.adapter

import com.reader.android.data.bridge.ReaderErrorCode
import org.junit.Assert.assertEquals
import org.junit.Test

class TtsErrorMapperTest {

    @Test
    fun `ENGINE_UNAVAILABLE maps to NOT_FOUND`() {
        assertEquals(ReaderErrorCode.NOT_FOUND, TtsErrorMapper.map(TtsError(TtsErrorType.ENGINE_UNAVAILABLE)))
    }

    @Test
    fun `LANGUAGE_NOT_SUPPORTED maps to PARSE`() {
        assertEquals(ReaderErrorCode.PARSE, TtsErrorMapper.map(TtsError(TtsErrorType.LANGUAGE_NOT_SUPPORTED)))
    }

    @Test
    fun `PLAYBACK_ERROR maps to UNKNOWN`() {
        assertEquals(ReaderErrorCode.UNKNOWN, TtsErrorMapper.map(TtsError(TtsErrorType.PLAYBACK_ERROR)))
    }

    @Test
    fun `INIT_FAILED maps to NETWORK`() {
        assertEquals(ReaderErrorCode.NETWORK, TtsErrorMapper.map(TtsError(TtsErrorType.INIT_FAILED)))
    }
}
