package com.minliny.reader.android.corebridge

import com.minliny.reader.android.corebridge.model.BookInfo
import com.minliny.reader.android.corebridge.model.BookSource
import com.minliny.reader.android.corebridge.model.ContentPage
import com.minliny.reader.android.corebridge.model.SearchResultItem
import com.minliny.reader.android.corebridge.model.TOCItem

class FakeCoreBridge : ReaderCoreBridge {
    
    override suspend fun search(bookSource: BookSource, keyword: String): List<SearchResultItem> {
        return listOf(
            SearchResultItem("示例小说 1", "作者 A", "https://example.com/book1"),
            SearchResultItem("示例小说 2", "作者 B", "https://example.com/book2")
        )
    }

    override suspend fun getBookInfo(bookSource: BookSource, bookUrl: String): BookInfo {
        return BookInfo(
            title = "示例小说",
            author = "示例作者",
            intro = "这是一本示例小说的简介内容"
        )
    }

    override suspend fun getTOC(bookSource: BookSource, tocUrl: String): List<TOCItem> {
        return listOf(
            TOCItem("第一章", "https://example.com/ch1"),
            TOCItem("第二章", "https://example.com/ch2"),
            TOCItem("第三章", "https://example.com/ch3")
        )
    }

    override suspend fun getContent(bookSource: BookSource, contentUrl: String): ContentPage {
        return ContentPage(
            content = "这是章节的示例内容。\n\n第二段落的内容。"
        )
    }
}
