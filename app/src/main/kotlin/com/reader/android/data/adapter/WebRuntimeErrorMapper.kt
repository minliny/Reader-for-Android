package com.reader.android.data.adapter

import com.reader.android.data.bridge.ReaderErrorCode

object WebRuntimeErrorMapper {
    fun map(jsErrorType: JsErrorType): ReaderErrorCode = when (jsErrorType) {
        JsErrorType.SYNTAX -> ReaderErrorCode.PARSE
        JsErrorType.RUNTIME -> ReaderErrorCode.PARSE
        JsErrorType.TIMEOUT -> ReaderErrorCode.TIMEOUT
        JsErrorType.SECURITY -> ReaderErrorCode.FORBIDDEN
        JsErrorType.UNKNOWN -> ReaderErrorCode.UNKNOWN
    }
}
