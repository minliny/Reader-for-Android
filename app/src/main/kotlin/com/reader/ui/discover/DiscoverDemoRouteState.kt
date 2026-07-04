package com.reader.ui.discover

import com.reader.ui.demo.demoCoverUrlForTitle

object DiscoverDemoRouteIds {
    const val CONTROL = "discover-control"
    const val SORT = "discover-sort"
    const val ENTRY_RANKING = "discover-entry-ranking"
    const val ENTRY_BESTSELLER = "discover-entry-bestseller"
    const val ENTRY_CATEGORY = "discover-entry-category"
    const val ENTRY_FINISHED = "discover-entry-finished"
    const val ENTRY_LATEST = "discover-entry-latest"
    const val ENTRY_NEW = "discover-entry-new"
    const val ENTRY_BOOKLIST = "discover-entry-booklist"
    const val FILTER_KEYWORD = "discover-filter-keyword"
    const val FILTER_MALE = "discover-filter-male"
    const val FILTER_FEMALE = "discover-filter-female"
    const val SORT_POPULARITY = "discover-sort-popularity"
    const val SORT_UPDATE = "discover-sort-update"
    const val SORT_COLLECTION = "discover-sort-collection"
    const val SORT_FINISHED = "discover-sort-finished"
    const val SORT_WORDS = "discover-sort-words"
    const val NO_RESULTS = "discover-no-results"
    const val LOADING = "discover-loading"
    const val REFRESHING = "discover-refreshing"
    const val INFINITE_LOADING = "discover-infinite-loading"
    const val PAGE_TWO = "discover-page-two"
    const val CACHE_CONFIRM = "discover-cache-confirm"
    const val CACHE_TOAST = "discover-cache-toast"
    const val LOGIN_RETURN = "discover-login-return"
    const val SWITCHING_SOURCE = "discover-switching-source"
    const val SWITCHED_SOURCE = "discover-switched-source"
    const val ENTRY_ERROR = "discover-entry-error"
    const val EMPTY = "discover-empty"
    const val ERROR = "discover-error"
    const val SOURCE_LOGIN = "discover-source-login"
    const val RULE_TEST = "discover-rule-test"
    const val SOURCE_BULK = "discover-source-bulk"

    val routeIds: Set<String> = linkedSetOf(
        CONTROL,
        SORT,
        ENTRY_RANKING,
        ENTRY_BESTSELLER,
        ENTRY_CATEGORY,
        ENTRY_FINISHED,
        ENTRY_LATEST,
        ENTRY_NEW,
        ENTRY_BOOKLIST,
        FILTER_KEYWORD,
        FILTER_MALE,
        FILTER_FEMALE,
        SORT_POPULARITY,
        SORT_UPDATE,
        SORT_COLLECTION,
        SORT_FINISHED,
        SORT_WORDS,
        NO_RESULTS,
        LOADING,
        REFRESHING,
        INFINITE_LOADING,
        PAGE_TWO,
        CACHE_CONFIRM,
        CACHE_TOAST,
        LOGIN_RETURN,
        SWITCHING_SOURCE,
        SWITCHED_SOURCE,
        ENTRY_ERROR,
        EMPTY,
        ERROR,
        SOURCE_LOGIN,
        RULE_TEST,
        SOURCE_BULK
    )
}

enum class DiscoverDemoPage {
    Main,
    SourceLogin,
    RuleTest,
    SourceBulk
}

enum class DiscoverDemoMainMode {
    Content,
    NoResults,
    Empty,
    Error
}

enum class DiscoverDemoControlMode {
    Normal,
    Switching,
    EntryError
}

data class DiscoverDemoRouteState(
    val routeId: String,
    val page: DiscoverDemoPage,
    val title: String,
    val source: DiscoverDemoSourceState = DiscoverDemoSourceState(),
    val entries: List<String> = defaultDiscoverEntries,
    val activeEntry: String = defaultDiscoverEntries.first(),
    val activeFilter: String = "男频",
    val activeSort: String = "人气",
    val total: Int = 18,
    val mainMode: DiscoverDemoMainMode = DiscoverDemoMainMode.Content,
    val controlExpanded: Boolean = false,
    val controlMode: DiscoverDemoControlMode = DiscoverDemoControlMode.Normal,
    val sortOpen: Boolean = false,
    val loading: Boolean = false,
    val refreshingText: String? = null,
    val infiniteLoading: Boolean = false,
    val pageTwo: Boolean = false,
    val mutedResults: Boolean = false,
    val toast: String? = null,
    val message: DiscoverDemoMessageState? = null,
    val dialog: DiscoverDemoDialogState? = null,
    val books: List<DiscoverDemoBookState> = emptyList(),
    val actions: List<DiscoverDemoAction> = emptyList(),
    val bottomActions: List<DiscoverDemoAction> = emptyList(),
    val fields: List<DiscoverDemoFieldState> = emptyList(),
    val bulkSources: List<DiscoverDemoSourceRowState> = emptyList()
)

data class DiscoverDemoSourceState(
    val name: String = "优书网",
    val meta: String = "默认分组 · 已启用发现 · 120ms",
    val status: String = "已启用发现",
    val speed: String = "120ms"
)

data class DiscoverDemoBookState(
    val title: String,
    val author: String,
    val kind: String,
    val latest: String,
    val intro: String,
    val inShelf: Boolean,
    val bookUrl: String,
    val coverUrl: String = demoCoverUrlForTitle(title)
)

data class DiscoverDemoAction(
    val label: String,
    val target: String,
    val primary: Boolean = false
)

data class DiscoverDemoMessageState(
    val title: String,
    val body: String,
    val actions: List<DiscoverDemoAction>
)

data class DiscoverDemoDialogState(
    val title: String,
    val body: String,
    val actions: List<DiscoverDemoAction>
)

data class DiscoverDemoFieldState(
    val label: String,
    val value: String
)

data class DiscoverDemoSourceRowState(
    val name: String,
    val meta: String,
    val tone: DiscoverDemoTone,
    val selected: Boolean
)

enum class DiscoverDemoTone {
    Good,
    Warn,
    Muted,
    Loading
}

private val defaultDiscoverEntries = listOf("排行榜", "分类", "完本", "最新", "书单")
private val switchedDiscoverEntries = listOf("畅销", "分类", "新书", "完本")

private val entryRouteMap = mapOf(
    DiscoverDemoRouteIds.ENTRY_RANKING to "排行榜",
    DiscoverDemoRouteIds.ENTRY_BESTSELLER to "畅销",
    DiscoverDemoRouteIds.ENTRY_CATEGORY to "分类",
    DiscoverDemoRouteIds.ENTRY_FINISHED to "完本",
    DiscoverDemoRouteIds.ENTRY_LATEST to "最新",
    DiscoverDemoRouteIds.ENTRY_NEW to "新书",
    DiscoverDemoRouteIds.ENTRY_BOOKLIST to "书单"
)

private val filterRouteMap = mapOf(
    DiscoverDemoRouteIds.FILTER_KEYWORD to "关键词",
    DiscoverDemoRouteIds.FILTER_MALE to "男频",
    DiscoverDemoRouteIds.FILTER_FEMALE to "女频"
)

private val sortRouteMap = mapOf(
    DiscoverDemoRouteIds.SORT_POPULARITY to "人气",
    DiscoverDemoRouteIds.SORT_UPDATE to "更新",
    DiscoverDemoRouteIds.SORT_COLLECTION to "收藏",
    DiscoverDemoRouteIds.SORT_FINISHED to "完本",
    DiscoverDemoRouteIds.SORT_WORDS to "字数"
)

private val totalByRoute = mapOf(
    DiscoverDemoRouteIds.ENTRY_CATEGORY to 32,
    DiscoverDemoRouteIds.ENTRY_FINISHED to 21,
    DiscoverDemoRouteIds.ENTRY_LATEST to 27,
    DiscoverDemoRouteIds.ENTRY_BOOKLIST to 14,
    DiscoverDemoRouteIds.FILTER_KEYWORD to 9,
    DiscoverDemoRouteIds.FILTER_FEMALE to 16,
    DiscoverDemoRouteIds.SORT_UPDATE to 25,
    DiscoverDemoRouteIds.SORT_COLLECTION to 19,
    DiscoverDemoRouteIds.SORT_FINISHED to 21,
    DiscoverDemoRouteIds.SORT_WORDS to 23
)

private val totalBySort = mapOf(
    "更新" to 25,
    "收藏" to 19,
    "完本" to 21,
    "字数" to 23
)

fun discoverDemoRouteIds(): Set<String> = DiscoverDemoRouteIds.routeIds

fun discoverEntryRouteForLabel(label: String): String = when (label) {
    "排行榜" -> DiscoverDemoRouteIds.ENTRY_RANKING
    "畅销" -> DiscoverDemoRouteIds.ENTRY_BESTSELLER
    "分类" -> DiscoverDemoRouteIds.ENTRY_CATEGORY
    "完本" -> DiscoverDemoRouteIds.ENTRY_FINISHED
    "最新" -> DiscoverDemoRouteIds.ENTRY_LATEST
    "新书" -> DiscoverDemoRouteIds.ENTRY_NEW
    "书单" -> DiscoverDemoRouteIds.ENTRY_BOOKLIST
    else -> "discover"
}

fun discoverFilterRouteForLabel(label: String): String = when (label) {
    "关键词" -> DiscoverDemoRouteIds.FILTER_KEYWORD
    "男频" -> DiscoverDemoRouteIds.FILTER_MALE
    "女频" -> DiscoverDemoRouteIds.FILTER_FEMALE
    else -> "discover"
}

fun discoverSortRouteForLabel(label: String): String = when (label) {
    "人气" -> DiscoverDemoRouteIds.SORT_POPULARITY
    "更新" -> DiscoverDemoRouteIds.SORT_UPDATE
    "收藏" -> DiscoverDemoRouteIds.SORT_COLLECTION
    "完本" -> DiscoverDemoRouteIds.SORT_FINISHED
    "字数" -> DiscoverDemoRouteIds.SORT_WORDS
    else -> "discover"
}

fun discoverDemoRouteState(routeId: String): DiscoverDemoRouteState? {
    if (routeId !in DiscoverDemoRouteIds.routeIds) return null

    return when (routeId) {
        DiscoverDemoRouteIds.SOURCE_LOGIN -> discoverSourceLoginRouteState()
        DiscoverDemoRouteIds.RULE_TEST -> discoverRuleTestRouteState()
        DiscoverDemoRouteIds.SOURCE_BULK -> discoverSourceBulkRouteState()
        else -> discoverMainRouteState(routeId)
    }
}

fun DiscoverDemoRouteState.actionTargets(): Set<String> =
    buildSet {
        actions.mapTo(this) { it.target }
        bottomActions.mapTo(this) { it.target }
        message?.actions?.mapTo(this) { it.target }
        dialog?.actions?.mapTo(this) { it.target }
    }

fun discoverDemoActionTargets(routeId: String): Set<String> =
    discoverDemoRouteState(routeId)?.actionTargets().orEmpty()

private fun discoverMainRouteState(routeId: String): DiscoverDemoRouteState {
    val switched = routeId == DiscoverDemoRouteIds.SWITCHED_SOURCE
    val entries = if (switched) switchedDiscoverEntries else defaultDiscoverEntries
    val routedEntry = entryRouteMap[routeId]
    val activeEntry = routedEntry?.takeIf { it in entries } ?: entries.first()
    val activeFilter = filterRouteMap[routeId] ?: "男频"
    val activeSort = sortRouteMap[routeId] ?: if (switched) "更新" else "人气"
    val source = when (routeId) {
        DiscoverDemoRouteIds.SWITCHED_SOURCE -> DiscoverDemoSourceState(
            name = "起点导入",
            meta = "正版 · 已启用发现 · 180ms",
            speed = "180ms"
        )
        DiscoverDemoRouteIds.ERROR -> DiscoverDemoSourceState(
            name = "优书网",
            meta = "排行榜 · 解析失败"
        )
        else -> DiscoverDemoSourceState()
    }
    val loading = routeId == DiscoverDemoRouteIds.LOADING
    val noResults = routeId == DiscoverDemoRouteIds.NO_RESULTS
    val mainMode = when (routeId) {
        DiscoverDemoRouteIds.EMPTY -> DiscoverDemoMainMode.Empty
        DiscoverDemoRouteIds.ERROR -> DiscoverDemoMainMode.Error
        DiscoverDemoRouteIds.NO_RESULTS -> DiscoverDemoMainMode.NoResults
        else -> DiscoverDemoMainMode.Content
    }

    return DiscoverDemoRouteState(
        routeId = routeId,
        page = DiscoverDemoPage.Main,
        title = "发现",
        source = source,
        entries = entries,
        activeEntry = activeEntry,
        activeFilter = activeFilter,
        activeSort = activeSort,
        total = when {
            switched -> 24
            routeId == DiscoverDemoRouteIds.PAGE_TWO -> 38
            else -> totalByRoute[routeId] ?: totalBySort[activeSort] ?: 18
        },
        mainMode = mainMode,
        controlExpanded = routeId in setOf(
            DiscoverDemoRouteIds.CONTROL,
            DiscoverDemoRouteIds.CACHE_CONFIRM,
            DiscoverDemoRouteIds.SWITCHING_SOURCE,
            DiscoverDemoRouteIds.ENTRY_ERROR
        ),
        controlMode = when (routeId) {
            DiscoverDemoRouteIds.SWITCHING_SOURCE -> DiscoverDemoControlMode.Switching
            DiscoverDemoRouteIds.ENTRY_ERROR -> DiscoverDemoControlMode.EntryError
            else -> DiscoverDemoControlMode.Normal
        },
        sortOpen = routeId == DiscoverDemoRouteIds.SORT,
        loading = loading,
        refreshingText = when (routeId) {
            DiscoverDemoRouteIds.REFRESHING -> "正在刷新当前列表"
            DiscoverDemoRouteIds.LOGIN_RETURN -> "登录成功，正在刷新当前发现入口"
            else -> null
        },
        infiniteLoading = routeId == DiscoverDemoRouteIds.INFINITE_LOADING,
        pageTwo = routeId == DiscoverDemoRouteIds.PAGE_TWO,
        mutedResults = routeId in setOf(DiscoverDemoRouteIds.SWITCHING_SOURCE, DiscoverDemoRouteIds.ENTRY_ERROR),
        toast = if (routeId == DiscoverDemoRouteIds.CACHE_TOAST) "已清除优书网发现缓存" else null,
        message = discoverMainMessageState(routeId),
        dialog = if (routeId == DiscoverDemoRouteIds.CACHE_CONFIRM) {
            DiscoverDemoDialogState(
                title = "清除发现缓存？",
                body = "将清除优书网的发现入口缓存，不影响书架和阅读进度。",
                actions = listOf(
                    DiscoverDemoAction("取消", DiscoverDemoRouteIds.CONTROL),
                    DiscoverDemoAction("确认清除", DiscoverDemoRouteIds.CACHE_TOAST, primary = true)
                )
            )
        } else {
            null
        },
        books = if (loading || noResults || routeId == DiscoverDemoRouteIds.EMPTY) {
            emptyList()
        } else {
            discoverDemoBooks(routeId)
        }
    )
}

private fun discoverMainMessageState(routeId: String): DiscoverDemoMessageState? = when (routeId) {
    DiscoverDemoRouteIds.EMPTY -> DiscoverDemoMessageState(
        title = "当前没有启用发现的书源",
        body = "启用发现后，可以在这里浏览书源提供的排行榜、分类和书单。",
        actions = listOf(
            DiscoverDemoAction("去书源管理", "source-management"),
            DiscoverDemoAction("导入书源", "source-import-options")
        )
    )
    DiscoverDemoRouteIds.ERROR -> DiscoverDemoMessageState(
        title = "发现入口解析失败",
        body = "当前入口返回异常，已保留上一批缓存结果。你可以重试、刷新入口、编辑源或切换书源。",
        actions = listOf(
            DiscoverDemoAction("重试", DiscoverDemoRouteIds.REFRESHING, primary = true),
            DiscoverDemoAction("切换书源", DiscoverDemoRouteIds.CONTROL),
            DiscoverDemoAction("编辑源", DiscoverDemoRouteIds.RULE_TEST)
        )
    )
    DiscoverDemoRouteIds.NO_RESULTS -> DiscoverDemoMessageState(
        title = "当前条件没有发现结果",
        body = "可以重置筛选、切换入口，或刷新当前书源。",
        actions = listOf(
            DiscoverDemoAction("重置筛选", "discover"),
            DiscoverDemoAction("切换入口", DiscoverDemoRouteIds.CONTROL),
            DiscoverDemoAction("刷新", DiscoverDemoRouteIds.REFRESHING, primary = true)
        )
    )
    else -> null
}

private fun discoverSourceLoginRouteState(): DiscoverDemoRouteState =
    DiscoverDemoRouteState(
        routeId = DiscoverDemoRouteIds.SOURCE_LOGIN,
        page = DiscoverDemoPage.SourceLogin,
        title = "书源登录",
        source = DiscoverDemoSourceState(name = "轻小说文库", meta = "需登录 · 发现可用", status = "需登录"),
        actions = listOf(
            DiscoverDemoAction("打开网页登录", DiscoverDemoRouteIds.LOGIN_RETURN, primary = true),
            DiscoverDemoAction("保存登录信息", DiscoverDemoRouteIds.LOGIN_RETURN),
            DiscoverDemoAction("重新检测", DiscoverDemoRouteIds.CONTROL)
        ),
        bottomActions = listOf(
            DiscoverDemoAction("返回控制层", DiscoverDemoRouteIds.CONTROL),
            DiscoverDemoAction("完成刷新", DiscoverDemoRouteIds.LOGIN_RETURN, primary = true)
        )
    )

private fun discoverRuleTestRouteState(): DiscoverDemoRouteState =
    DiscoverDemoRouteState(
        routeId = DiscoverDemoRouteIds.RULE_TEST,
        page = DiscoverDemoPage.RuleTest,
        title = "发现规则测试",
        fields = listOf(
            DiscoverDemoFieldState("exploreUrl", "@js: 首页入口 + 分类入口"),
            DiscoverDemoFieldState("bookList", ".result-list li"),
            DiscoverDemoFieldState("name", ".book-title@text"),
            DiscoverDemoFieldState("author", ".author@text"),
            DiscoverDemoFieldState("kind", ".tag@text"),
            DiscoverDemoFieldState("intro", ".intro@text"),
            DiscoverDemoFieldState("lastChapter", ".last@text"),
            DiscoverDemoFieldState("coverUrl", "img@src"),
            DiscoverDemoFieldState("bookUrl", "a@href")
        ),
        actions = listOf(DiscoverDemoAction("测试入口", "run-discover-rule-test", primary = true)),
        bottomActions = listOf(
            DiscoverDemoAction("测试入口", "run-discover-rule-test"),
            DiscoverDemoAction("保存", DiscoverDemoRouteIds.CONTROL, primary = true)
        )
    )

private fun discoverSourceBulkRouteState(): DiscoverDemoRouteState =
    DiscoverDemoRouteState(
        routeId = DiscoverDemoRouteIds.SOURCE_BULK,
        page = DiscoverDemoPage.SourceBulk,
        title = "发现源管理",
        actions = listOf(
            DiscoverDemoAction("取消", DiscoverDemoRouteIds.CONTROL),
            DiscoverDemoAction("全选", "select-all-discover-sources")
        ),
        bottomActions = listOf(
            DiscoverDemoAction("启用", "enable-discover-sources", primary = true),
            DiscoverDemoAction("禁用", "disable-discover-sources"),
            DiscoverDemoAction("刷新", "refresh-discover-sources")
        ),
        bulkSources = listOf(
            DiscoverDemoSourceRowState("优书网", "默认分组 · 120ms · 已启用发现", DiscoverDemoTone.Good, true),
            DiscoverDemoSourceRowState("起点导入", "正版 · 180ms · 已启用发现", DiscoverDemoTone.Good, true),
            DiscoverDemoSourceRowState("轻小说文库", "需登录 · 发现可用", DiscoverDemoTone.Warn, true),
            DiscoverDemoSourceRowState("本地聚合源", "维护中 · 暂停发现", DiscoverDemoTone.Muted, false),
            DiscoverDemoSourceRowState("失效示例源", "解析失败 · exploreUrl 异常", DiscoverDemoTone.Warn, false)
        )
    )

private fun discoverDemoBooks(routeId: String): List<DiscoverDemoBookState> {
    val base = if (routeId == DiscoverDemoRouteIds.SWITCHED_SOURCE) {
        listOf(
            DiscoverDemoBookState("诡秘之主", "爱潜水的乌贼", "奇幻 · 完本", "最新：番外已整理", "克莱恩在迷雾中醒来，新的线索沿着塔罗会延伸。", true, "fixture://discover/mystery-lord"),
            DiscoverDemoBookState("纸上城市", "默认分组", "都市 · 连载", "最新：第 18 章", "城市被写在纸页上，所有路口都藏着旧书源的暗号。", false, "fixture://discover/paper-city"),
            DiscoverDemoBookState("灯塔与雾", "书源同步", "悬疑 · 连载", "最新：第 51 章", "雾气吞没海岸线，灯塔的记录仍在夜里闪烁。", false, "fixture://discover/lighthouse"),
            DiscoverDemoBookState("群星之间", "本地导入", "科幻 · 连载", "最新：第 12 章", "星舰穿过静默航道，旧文明的坐标重新亮起。", true, "fixture://discover/stars")
        )
    } else {
        listOf(
            DiscoverDemoBookState("长夜余火", "爱潜水的乌贼", "科幻 · 连载", "最新：第 32 章 雨夜", "雨声在窗外连成一片，旧世界的线索在夜里慢慢浮出。", true, "fixture://discover/long-night"),
            DiscoverDemoBookState("诡秘之主", "爱潜水的乌贼", "奇幻 · 完本", "最新：番外已整理", "蒸汽、塔罗与旧日秘密交织，适合继续追读。", true, "fixture://discover/mystery-lord"),
            DiscoverDemoBookState("三体", "刘慈欣", "科幻 · 完本", "最新：三部曲合集", "文明在宇宙暗处相互凝视，微小选择带来巨大回声。", false, "fixture://discover/three-body"),
            DiscoverDemoBookState("明朝那些事儿", "当年明月", "历史 · 完本", "最新：全集校对", "用更轻松的方式重新翻开明朝人物与权力线索。", false, "fixture://discover/ming"),
            DiscoverDemoBookState("纸上城市", "默认分组", "都市 · 连载", "最新：第 12 章", "纸页边缘折起，城市的名字开始变化。", false, "fixture://discover/paper-city")
        )
    }
    val extra = if (routeId in setOf(DiscoverDemoRouteIds.PAGE_TWO, DiscoverDemoRouteIds.INFINITE_LOADING)) {
        listOf(
            DiscoverDemoBookState("旧日回响", "离线书库", "奇幻 · 连载", "最新：第 18 章", "旧日钟声从废墟里传回，缓存章节仍可打开。", false, "fixture://discover/old-echo")
        )
    } else {
        emptyList()
    }
    return base + extra
}
