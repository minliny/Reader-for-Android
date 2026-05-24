package com.reader.android.ui.detail

import com.reader.android.data.bridge.ReaderError
import com.reader.android.data.bridge.ReaderErrorCode

object BookDetailFacadeErrorMapper {

    fun mapError(error: ReaderError): BookDetailUiState =
        BookDetailUiStateMapper.error(userMessage(error))

    fun mapException(e: Throwable): BookDetailUiState =
        BookDetailUiStateMapper.error("书籍详情暂时不可用")

    fun emptyDetailUrl(): BookDetailUiState =
        BookDetailUiStateMapper.error("无效的书籍链接")

    private fun userMessage(error: ReaderError): String = when (error.code) {
        ReaderErrorCode.NETWORK -> "网络连接失败，请检查网络"
        ReaderErrorCode.TIMEOUT -> "加载超时，请重试"
        ReaderErrorCode.PARSE -> "详情解析失败"
        ReaderErrorCode.NOT_FOUND -> "未找到该书籍"
        ReaderErrorCode.UNAUTHORIZED -> "书源需要登录"
        ReaderErrorCode.FORBIDDEN -> "书源拒绝访问"
        ReaderErrorCode.UNKNOWN -> error.message ?: "加载失败"
    }
}
