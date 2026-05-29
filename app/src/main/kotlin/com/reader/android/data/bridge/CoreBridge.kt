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
        return if (query.keyword.contains("凡人修仙传")) {
            listOf(
                SearchResultItem(
                    name = "凡人修仙传",
                    author = "忘语",
                    coverUrl = null,
                    detailUrl = "/34811",
                    kind = "仙侠",
                    wordCount = "760万字",
                    latestChapter = "第两千四百一十二章 回归",
                    intro = "一个普通的山村穷小子，偶然之下，进入到当地一个江湖小门派，成了一名记名弟子。他以这样的身份，如何在门派中立足？",
                    sourceName = source.sourceName
                ),
                SearchResultItem(
                    name = "凡人修仙传之仙界篇",
                    author = "忘语",
                    coverUrl = null,
                    detailUrl = "/34812",
                    kind = "仙侠",
                    wordCount = "450万字",
                    latestChapter = "终章",
                    intro = "凡人修仙传仙界篇，韩立飞升仙界之后的故事。",
                    sourceName = source.sourceName
                )
            )
        } else {
            listOf(
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
    }

    override suspend fun getBookInfo(detailUrl: String, source: BookSource): BookInfo {
        return if (detailUrl.contains("34811")) {
            BookInfo(
                name = "凡人修仙传",
                author = "忘语",
                intro = "一个普通的山村穷小子，偶然之下，进入到当地一个江湖小门派，成了一名记名弟子。他以这样的身份，如何在门派中立足？如何以平庸的资质进入到修仙者的行列？从而笑傲三界之中！",
                kind = "仙侠",
                coverUrl = null,
                tocUrl = "/34811",
                wordCount = "760万字",
                latestChapter = "第两千四百一十二章 回归",
                updateTime = "2026-05-28",
                origin = source.sourceName
            )
        } else {
            BookInfo(
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
    }

    override suspend fun getTOC(tocUrl: String, source: BookSource): List<TOCItem> {
        return listOf(
            TOCItem(
                title = "第一卷 七玄门风云",
                url = "",
                children = listOf(
                    TOCItem(title = "第一章 山边小村", url = "/34811/1"),
                    TOCItem(title = "第二章 青牛镇", url = "/34811/2"),
                    TOCItem(title = "第三章 七玄门", url = "/34811/3")
                )
            ),
            TOCItem(
                title = "第二卷 初踏修仙路",
                url = "",
                children = listOf(
                    TOCItem(title = "第四章 神秘小瓶", url = "/34811/4"),
                    TOCItem(title = "第五章 黄枫谷", url = "/34811/5")
                )
            )
        )
    }

    override suspend fun getContent(contentUrl: String, source: BookSource): ContentPage {
        return ContentPage(
            content = "　　韩立出身在一个偏远的山村，家中排行老四，村里人都叫他\"二愣子\"。\n\n　　" +
                "这一日，韩立正在后山砍柴，忽然听到一声闷响。他循声望去，只见不远处的地面上，竟然裂开了一道缝隙，" +
                "里面隐隐有光芒透出。\n\n　　韩立吓了一跳，但他从小就胆子大，犹豫片刻后，还是忍不住走上前去。\n\n　　" +
                "裂缝之中，竟然是一个古朴的木质小瓶，上面刻着一些他看不懂的纹路。韩立将小瓶捡起，觉得入手温润，" +
                "不像是凡物。\n\n　　他将小瓶揣进怀里，匆匆下山。他不知道的是，这个小瓶，将会改变他的一生。",
            title = "第一章 山边小村",
            nextPageUrl = "/34811/2"
        )
    }
}
