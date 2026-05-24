package com.reader.android.ui.prototype

enum class ReaderPrototypeGroup(val title: String) {
    APP_NAVIGATION("App / Navigation"),
    BOOKSHELF("Bookshelf"),
    SEARCH_DETAIL("Search / Detail"),
    READER("Reader"),
    SOURCE_MANAGEMENT("Source Management"),
    DISCOVER_RSS("Discover / RSS"),
    WEBDAV_SYNC("WebDAV / Sync"),
    SETTINGS("Settings"),
    GLOBAL_STATES("Global States")
}

data class ReaderPrototypeEntry(
    val id: String,
    val title: String,
    val group: ReaderPrototypeGroup,
    val description: String
)

object ReaderPrototypeCatalog {
    val entries: List<ReaderPrototypeEntry> = listOf(
        entry("app-shell", "App Shell / Main Tabs", ReaderPrototypeGroup.APP_NAVIGATION),
        entry("bookshelf-cover", "书架封面模式", ReaderPrototypeGroup.BOOKSHELF),
        entry("bookshelf-list", "书架列表模式", ReaderPrototypeGroup.BOOKSHELF),
        entry("bookshelf-empty", "书架空状态", ReaderPrototypeGroup.BOOKSHELF),
        entry("search-home", "搜索首页", ReaderPrototypeGroup.SEARCH_DETAIL),
        entry("search-results", "搜索结果", ReaderPrototypeGroup.SEARCH_DETAIL),
        entry("search-empty", "搜索空状态", ReaderPrototypeGroup.SEARCH_DETAIL),
        entry("search-error", "搜索错误状态", ReaderPrototypeGroup.SEARCH_DETAIL),
        entry("book-detail", "书籍详情", ReaderPrototypeGroup.SEARCH_DETAIL),
        entry("book-detail-toc", "书籍详情 TOC 预览", ReaderPrototypeGroup.SEARCH_DETAIL),
        entry("reader-base", "阅读页基础控制层", ReaderPrototypeGroup.READER),
        entry("reader-search", "阅读页搜索 overlay", ReaderPrototypeGroup.READER),
        entry("reader-auto-scroll", "阅读页自动翻页 overlay", ReaderPrototypeGroup.READER),
        entry("reader-replace", "阅读页内容替换 overlay", ReaderPrototypeGroup.READER),
        entry("reader-night", "阅读页夜间状态", ReaderPrototypeGroup.READER),
        entry("reader-directory", "阅读页目录/书签 overlay", ReaderPrototypeGroup.READER),
        entry("reader-tts", "阅读页朗读 overlay", ReaderPrototypeGroup.READER),
        entry("reader-appearance", "阅读页界面 overlay", ReaderPrototypeGroup.READER),
        entry("reader-settings", "阅读页设置 overlay", ReaderPrototypeGroup.READER),
        entry("source-list", "书源管理列表", ReaderPrototypeGroup.SOURCE_MANAGEMENT),
        entry("source-detail", "书源详情", ReaderPrototypeGroup.SOURCE_MANAGEMENT),
        entry("source-edit-import", "书源编辑/导入状态", ReaderPrototypeGroup.SOURCE_MANAGEMENT),
        entry("source-test-disabled-error", "书源测试/禁用/错误状态", ReaderPrototypeGroup.SOURCE_MANAGEMENT),
        entry("discover-home", "发现首页", ReaderPrototypeGroup.DISCOVER_RSS),
        entry("rss-list", "RSS 列表", ReaderPrototypeGroup.DISCOVER_RSS),
        entry("rss-detail", "RSS 详情", ReaderPrototypeGroup.DISCOVER_RSS),
        entry("rss-subscriptions", "RSS 订阅管理", ReaderPrototypeGroup.DISCOVER_RSS),
        entry("webdav-config", "WebDAV 配置", ReaderPrototypeGroup.WEBDAV_SYNC),
        entry("backup-settings", "备份设置", ReaderPrototypeGroup.WEBDAV_SYNC),
        entry("progress-sync", "阅读进度同步状态", ReaderPrototypeGroup.WEBDAV_SYNC),
        entry("remote-webdav-books", "远程 WebDAV 书籍", ReaderPrototypeGroup.WEBDAV_SYNC),
        entry("sync-error", "同步错误 / WebDAV auth error", ReaderPrototypeGroup.WEBDAV_SYNC),
        entry("global-settings", "全局设置", ReaderPrototypeGroup.SETTINGS),
        entry("state-loading", "loading 状态页", ReaderPrototypeGroup.GLOBAL_STATES),
        entry("state-empty", "empty 状态页", ReaderPrototypeGroup.GLOBAL_STATES),
        entry("state-error", "error 状态页", ReaderPrototypeGroup.GLOBAL_STATES),
        entry("state-offline", "offline 状态页", ReaderPrototypeGroup.GLOBAL_STATES),
        entry("state-permission", "permission required 状态页", ReaderPrototypeGroup.GLOBAL_STATES)
    )

    val groupedEntries: Map<ReaderPrototypeGroup, List<ReaderPrototypeEntry>> =
        entries.groupBy { it.group }

    private fun entry(id: String, title: String, group: ReaderPrototypeGroup): ReaderPrototypeEntry =
        ReaderPrototypeEntry(
            id = id,
            title = title,
            group = group,
            description = descriptionFor(group)
        )

    private fun descriptionFor(group: ReaderPrototypeGroup): String =
        when (group) {
            ReaderPrototypeGroup.APP_NAVIGATION -> "正式主导航预览"
            ReaderPrototypeGroup.BOOKSHELF -> "书架模块 UI fixture"
            ReaderPrototypeGroup.SEARCH_DETAIL -> "二级搜索与详情状态"
            ReaderPrototypeGroup.READER -> "阅读页控制层状态"
            ReaderPrototypeGroup.SOURCE_MANAGEMENT -> "书源管理状态预览"
            ReaderPrototypeGroup.DISCOVER_RSS -> "发现与 RSS 状态预览"
            ReaderPrototypeGroup.WEBDAV_SYNC -> "同步与备份状态预览"
            ReaderPrototypeGroup.SETTINGS -> "我的与全局设置入口"
            ReaderPrototypeGroup.GLOBAL_STATES -> "全局状态页预览"
        }
}
