package com.reader.android.ui.reader

import com.reader.android.data.bridge.ReaderError
import com.reader.android.data.bridge.ReaderErrorCode

object ReaderTocFacadeErrorMapper {

    fun mapError(error: ReaderError): ReaderTocBookmarkState =
        ReaderTocBookmarkState(
            entries = emptyList(),
            volumeInfo = errorMessage(error),
            activeTab = "目录"
        )

    fun mapException(e: Throwable): ReaderTocBookmarkState =
        ReaderTocBookmarkState(
            entries = emptyList(),
            volumeInfo = "目录加载失败",
            activeTab = "目录"
        )

    fun emptyToc(): ReaderTocBookmarkState =
        ReaderTocBookmarkState(
            entries = emptyList(),
            volumeInfo = "暂无章节",
            activeTab = "目录"
        )

    private fun errorMessage(error: ReaderError): String = when (error.code) {
        ReaderErrorCode.NETWORK -> "网络连接失败"
        ReaderErrorCode.TIMEOUT -> "加载超时"
        ReaderErrorCode.PARSE -> "目录解析失败"
        ReaderErrorCode.NOT_FOUND -> "未找到目录"
        ReaderErrorCode.UNKNOWN -> error.message ?: "目录加载失败"
        else -> "目录不可用"
    }
}
