package com.reader.android.ui.fixtures

object ReaderUiFixtures {

    val bookshelfBooks = listOf(
        FixtureBook("fixture-book-1", "深空信号", "林间", 0.42f),
        FixtureBook("fixture-book-2", "纸上群山", "南溪", 0.16f)
    )

    val searchResults = listOf(
        FixtureSearchResult("fixture-search-1", "一剑独尊", "青鸾峰上", "玄幻"),
        FixtureSearchResult("fixture-search-2", "仙逆", "耳根", "仙侠")
    )

    val bookDetail = FixtureBookDetail(
        id = "fixture-detail-1",
        title = "一剑独尊",
        author = "青鸾峰上",
        intro = "少年持剑下山。"
    )

    val readerChapter = FixtureReaderChapter(
        title = "第一章 下山",
        contentPreview = "秋风萧瑟，少年背着长剑走向山下。",
        progress = 0.25f
    )

    val sources = listOf(
        FixtureSource("fixture-source-1", "笔趣阁", "中文", enabled = true),
        FixtureSource("fixture-source-2", "备用书源", "备用", enabled = false)
    )

    val rssSources = listOf(
        FixtureRssSource("fixture-rss-1", "订阅源 1", 2),
        FixtureRssSource("fixture-rss-2", "订阅源 2", 1)
    )

    val webDavStatus = FixtureWebDavStatus(
        configured = false,
        serverLabel = "未配置"
    )

    val globalErrors = listOf(
        FixtureGlobalState("fixture-error", "加载失败", "示例错误状态"),
        FixtureGlobalState("fixture-offline", "当前离线", "示例离线状态")
    )
}

data class FixtureBook(
    val id: String,
    val title: String,
    val author: String,
    val progress: Float
)

data class FixtureSearchResult(
    val id: String,
    val title: String,
    val author: String,
    val category: String
)

data class FixtureBookDetail(
    val id: String,
    val title: String,
    val author: String,
    val intro: String
)

data class FixtureReaderChapter(
    val title: String,
    val contentPreview: String,
    val progress: Float
)

data class FixtureSource(
    val id: String,
    val name: String,
    val group: String,
    val enabled: Boolean
)

data class FixtureRssSource(
    val id: String,
    val title: String,
    val updateCount: Int
)

data class FixtureWebDavStatus(
    val configured: Boolean,
    val serverLabel: String
)

data class FixtureGlobalState(
    val id: String,
    val title: String,
    val message: String
)
