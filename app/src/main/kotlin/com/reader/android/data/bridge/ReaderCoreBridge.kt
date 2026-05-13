package com.reader.android.data.bridge

import com.reader.android.data.model.BookInfo
import com.reader.android.data.model.BookSource
import com.reader.android.data.model.ContentPage
import com.reader.android.data.model.SearchQuery
import com.reader.android.data.model.SearchResultItem
import com.reader.android.data.model.TOCItem

// ── Error taxonomy matching Reader-Core MappedReaderError ──

enum class ReaderErrorCode {
    NETWORK,
    PARSE,
    TIMEOUT,
    NOT_FOUND,
    UNAUTHORIZED,
    FORBIDDEN,
    UNKNOWN
}

enum class ReaderFailureStage {
    SEARCH,
    TOC,
    CONTENT,
    BOOK_INFO
}

data class ReaderError(
    val code: ReaderErrorCode,
    val stage: ReaderFailureStage,
    val message: String? = null,
    val sourceName: String? = null
)

// ── Bridge result types ──

sealed class BridgeResult<out T> {
    data class Success<T>(val data: T) : BridgeResult<T>()
    data class Failure(val error: ReaderError) : BridgeResult<Nothing>()
}

// ── ReaderCoreBridge contract ──

interface ReaderCoreBridge {
    suspend fun search(
        query: SearchQuery,
        source: BookSource
    ): BridgeResult<List<SearchResultItem>>

    suspend fun getBookInfo(
        detailUrl: String,
        source: BookSource
    ): BridgeResult<BookInfo>

    suspend fun getTOC(
        tocUrl: String,
        source: BookSource
    ): BridgeResult<List<TOCItem>>

    suspend fun getContent(
        contentUrl: String,
        source: BookSource
    ): BridgeResult<ContentPage>
}
