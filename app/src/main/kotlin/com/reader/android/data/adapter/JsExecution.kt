package com.reader.android.data.adapter

data class JsRequest(
    val code: String,
    val contextUrl: String,
    val sourceUrl: String
)

data class JsResponse(
    val result: String?,
    val success: Boolean,
    val error: JsError? = null
)

data class JsError(
    val type: JsErrorType,
    val message: String,
    val line: Int? = null
)

enum class JsErrorType {
    SYNTAX,
    RUNTIME,
    TIMEOUT,
    SECURITY,
    UNKNOWN
}
