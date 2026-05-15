package com.reader.android.data.adapter

import com.reader.android.data.bridge.ReaderErrorCode

enum class TtsErrorType { ENGINE_UNAVAILABLE, LANGUAGE_NOT_SUPPORTED, PLAYBACK_ERROR, INIT_FAILED }

data class TtsError(val type: TtsErrorType, val message: String? = null)

object TtsErrorMapper {
    fun map(error: TtsError): ReaderErrorCode = when (error.type) {
        TtsErrorType.ENGINE_UNAVAILABLE -> ReaderErrorCode.NOT_FOUND
        TtsErrorType.LANGUAGE_NOT_SUPPORTED -> ReaderErrorCode.PARSE
        TtsErrorType.PLAYBACK_ERROR -> ReaderErrorCode.UNKNOWN
        TtsErrorType.INIT_FAILED -> ReaderErrorCode.NETWORK
    }
}
