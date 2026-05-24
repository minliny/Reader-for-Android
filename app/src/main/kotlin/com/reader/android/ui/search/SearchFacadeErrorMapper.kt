package com.reader.android.ui.search

import com.reader.android.data.bridge.BridgeResult
import com.reader.android.data.bridge.ReaderError
import com.reader.android.data.bridge.ReaderErrorCode

/**
 * Maps CoreBridge errors → [SearchUiState] with safe user-facing messages.
 * Never propagates raw exception details to UI.
 */
object SearchFacadeErrorMapper {

    fun mapError(query: String, error: ReaderError): SearchUiState =
        SearchUiStateMapper.error(query, userMessage(error))

    fun mapException(query: String, e: Throwable): SearchUiState =
        SearchUiStateMapper.error(query, "搜索暂时不可用")

    // ── User-facing messages ──

    private fun userMessage(error: ReaderError): String = when (error.code) {
        ReaderErrorCode.NETWORK -> "网络连接失败，请检查网络"
        ReaderErrorCode.TIMEOUT -> "搜索超时，请重试"
        ReaderErrorCode.PARSE -> "搜索结果解析失败"
        ReaderErrorCode.NOT_FOUND -> "未找到匹配书籍"
        ReaderErrorCode.UNAUTHORIZED -> "书源需要登录"
        ReaderErrorCode.FORBIDDEN -> "书源拒绝访问"
        ReaderErrorCode.UNKNOWN -> error.message ?: "搜索失败"
    }

    fun emptyKeyword(query: String): SearchUiState =
        SearchUiStateMapper.empty(query)
}
