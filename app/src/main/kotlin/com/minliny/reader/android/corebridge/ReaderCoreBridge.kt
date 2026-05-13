package com.minliny.reader.android.corebridge

import com.minliny.reader.android.corebridge.model.BookInfo
import com.minliny.reader.android.corebridge.model.BookSource
import com.minliny.reader.android.corebridge.model.ContentPage
import com.minliny.reader.android.corebridge.model.SearchResultItem
import com.minliny.reader.android.corebridge.model.TOCItem

interface ReaderCoreBridge {
    suspend fun search(bookSource: BookSource, keyword: String): List<SearchResultItem>
    suspend fun getBookInfo(bookSource: BookSource, bookUrl: String): BookInfo
    suspend fun getTOC(bookSource: BookSource, tocUrl: String): List<TOCItem>
    suspend fun getContent(bookSource: BookSource, contentUrl: String): ContentPage
}
