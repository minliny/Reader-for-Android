package com.reader.android.ui.reader

import com.reader.android.data.bridge.ReaderError
import com.reader.android.data.bridge.ReaderErrorCode

data class ContentErrorState(
    val message: String,
    val retryable: Boolean = true
)

object ContentFacadeErrorMapper {

    fun mapError(error: ReaderError): ContentErrorState =
        ContentErrorState(userMessage(error), retryable(error.code))

    fun mapException(e: Throwable): ContentErrorState =
        ContentErrorState("内容加载失败", retryable = true)

    fun missingUrl(): ContentErrorState =
        ContentErrorState("无效的章节链接", retryable = false)

    fun emptyContent(): ContentErrorState =
        ContentErrorState("本章暂无内容", retryable = false)

    private fun userMessage(error: ReaderError): String = when (error.code) {
        ReaderErrorCode.NETWORK -> "网络连接失败，请检查网络"
        ReaderErrorCode.TIMEOUT -> "加载超时，请重试"
        ReaderErrorCode.PARSE -> "正文解析失败"
        ReaderErrorCode.NOT_FOUND -> "未找到章节内容"
        ReaderErrorCode.UNKNOWN -> error.message ?: "内容加载失败"
        else -> "内容不可用"
    }

    private fun retryable(code: ReaderErrorCode): Boolean = when (code) {
        ReaderErrorCode.NETWORK, ReaderErrorCode.TIMEOUT, ReaderErrorCode.UNKNOWN -> true
        else -> false
    }
}
