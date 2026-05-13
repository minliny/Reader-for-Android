package com.reader.android.data.bridge

import com.reader.android.data.model.BookInfo
import com.reader.android.data.model.BookSource
import com.reader.android.data.model.ContentPage
import com.reader.android.data.model.SearchQuery
import com.reader.android.data.model.SearchResultItem
import com.reader.android.data.model.TOCItem

interface CoreBridge {
    suspend fun search(query: SearchQuery, source: BookSource): List<SearchResultItem>
    suspend fun getBookInfo(detailUrl: String, source: BookSource): BookInfo
    suspend fun getTOC(tocUrl: String, source: BookSource): List<TOCItem>
    suspend fun getContent(contentUrl: String, source: BookSource): ContentPage
}

class FakeCoreBridge : CoreBridge {

    override suspend fun search(query: SearchQuery, source: BookSource): List<SearchResultItem> {
        return listOf(
            SearchResultItem(
                name = "一剑独尊",
                author = "青鸾峰上",
                coverUrl = null,
                detailUrl = "https://example.com/book/1",
                kind = "玄幻",
                wordCount = "320万字",
                latestChapter = "第1024章 决战",
                intro = "剑气纵横三万里，一剑光寒十九洲。",
                sourceName = source.sourceName
            ),
            SearchResultItem(
                name = "仙逆",
                author = "耳根",
                coverUrl = null,
                detailUrl = "https://example.com/book/2",
                kind = "仙侠",
                wordCount = "650万字",
                latestChapter = "第2088章 道",
                intro = "顺为凡，逆则仙。",
                sourceName = source.sourceName
            )
        )
    }

    override suspend fun getBookInfo(detailUrl: String, source: BookSource): BookInfo {
        return BookInfo(
            name = "一剑独尊",
            author = "青鸾峰上",
            intro = "剑气纵横三万里，一剑光寒十九洲。少年叶玄持剑下山，从此踏上一条无敌之路。",
            kind = "玄幻",
            coverUrl = null,
            tocUrl = "https://example.com/book/1/toc",
            wordCount = "320万字",
            latestChapter = "第1024章 决战",
            updateTime = "2026-05-14",
            origin = source.sourceName
        )
    }

    override suspend fun getTOC(tocUrl: String, source: BookSource): List<TOCItem> {
        return listOf(
            TOCItem(title = "第一章 下山", url = "https://example.com/book/1/ch1"),
            TOCItem(title = "第二章 初遇", url = "https://example.com/book/1/ch2"),
            TOCItem(title = "第三章 觉醒", url = "https://example.com/book/1/ch3"),
            TOCItem(
                title = "第一卷 少年游",
                url = "",
                children = listOf(
                    TOCItem(title = "第四章 试剑", url = "https://example.com/book/1/ch4"),
                    TOCItem(title = "第五章 离别", url = "https://example.com/book/1/ch5")
                )
            )
        )
    }

    override suspend fun getContent(contentUrl: String, source: BookSource): ContentPage {
        return ContentPage(
            content = "　　秋风萧瑟，落叶漫天。\n\n　　少年背着长剑，独自走在山间小道上。他的目光坚定，步伐沉稳。\n\n　　" +
                "师父说过，山下有他需要找的人，也有他需要杀的人。他不急，因为他知道自己终将找到答案。\n\n　　" +
                "远处传来钟声，一座古城渐渐浮现在地平线上。",
            title = "第一章 下山",
            nextPageUrl = "https://example.com/book/1/ch2"
        )
    }
}
