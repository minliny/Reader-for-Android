package com.reader.android.ui.reader

enum class ReadingTocBookmarkDisplayState {
    Default,
    Bookmark,
    Search,
    Empty,
    Loading,
    Error,
    MoreMenu
}

data class ReadingTocBookmarkStatusUiModel(
    val left: String,
    val time: String
)

data class ReadingTopControlUiModel(
    val bookTitle: String,
    val sourceLine: String,
    val sourceActionLabel: String
)

data class ReadingTocPanelTabUiModel(
    val label: String,
    val type: String
)

data class ReadingTocPanelUiModel(
    val title: String,
    val meta: String,
    val fullDirectoryLabel: String,
    val searchPlaceholder: String,
    val tabs: List<ReadingTocPanelTabUiModel>,
    val currentVolume: String,
    val returnProgressLabel: String,
    val filterLabel: String
)

data class ReadingChapterRowUiModel(
    val title: String,
    val status: String,
    val current: Boolean,
    val read: Boolean
)

data class ReadingBookmarkRowUiModel(
    val chapter: String,
    val excerpt: String,
    val location: String,
    val time: String
)

data class ReadingTocSearchUiModel(
    val query: String,
    val resultsLabel: String,
    val results: List<ReadingChapterRowUiModel>
)

data class ReadingMoreMenuItemUiModel(
    val label: String,
    val description: String
)

data class ReadingTocMoreMenuUiModel(
    val title: String,
    val items: List<ReadingMoreMenuItemUiModel>
)

data class ReadingModuleNavItemUiModel(
    val label: String,
    val type: String,
    val active: Boolean
)

data class ReadingBrightnessUiModel(
    val title: String,
    val value: Int,
    val autoLabel: String,
    val modeLabel: String
)

data class ReadingBottomReadoutUiModel(
    val progress: String,
    val chapter: String
)

data class ReadingTocBookmarkUiState(
    val status: ReadingTocBookmarkStatusUiModel,
    val topControl: ReadingTopControlUiModel,
    val reading: ReaderShellTextUiModel,
    val panel: ReadingTocPanelUiModel,
    val chapters: List<ReadingChapterRowUiModel>,
    val bookmarks: List<ReadingBookmarkRowUiModel>,
    val search: ReadingTocSearchUiModel,
    val moreMenu: ReadingTocMoreMenuUiModel,
    val loading: ReaderShellFeedbackUiModel,
    val empty: ReaderShellFeedbackUiModel,
    val error: ReaderShellFeedbackUiModel,
    val moduleNav: List<ReadingModuleNavItemUiModel>,
    val brightness: ReadingBrightnessUiModel,
    val bottomReadout: ReadingBottomReadoutUiModel,
    val displayState: ReadingTocBookmarkDisplayState = ReadingTocBookmarkDisplayState.Default
) {
    val activeTabType: String get() =
        if (displayState == ReadingTocBookmarkDisplayState.Bookmark ||
            displayState == ReadingTocBookmarkDisplayState.Empty
        ) {
            "bookmark"
        } else {
            "directory"
        }

    val visibleChapters: List<ReadingChapterRowUiModel> get() =
        if (displayState == ReadingTocBookmarkDisplayState.Search) search.results else chapters

    val visibleBookmarks: List<ReadingBookmarkRowUiModel> get() =
        if (displayState == ReadingTocBookmarkDisplayState.Empty) emptyList() else bookmarks
}

object ReadingTocBookmarkMapper {
    fun fromFixture(): ReadingTocBookmarkUiState =
        ReadingTocBookmarkUiState(
            status = ReadingTocBookmarkStatusUiModel(
                left = "长夜余火 · 第 32 章",
                time = "10:30"
            ),
            topControl = ReadingTopControlUiModel(
                bookTitle = "长夜余火",
                sourceLine = "起点中文网 · 第 32 章",
                sourceActionLabel = "换源"
            ),
            reading = ReaderShellTextUiModel(
                title = "",
                paragraphs = listOf(
                    "雨，下了一整夜。",
                    "城市像是被泼了一盆凉水，街灯昏黄，水面反着光，连呼吸都带着潮气。",
                    "他站在巷口，手里攥着那封信。纸已经被雨水润湿，边角微微卷起，字迹却依旧清晰。",
                    "“等你回来。”",
                    "没有署名，只有短短四个字。",
                    "他笑了笑，笑得很轻，像是自言自语。然后转身，沿着雨声走进夜里。"
                )
            ),
            panel = ReadingTocPanelUiModel(
                title = "目录",
                meta = "阅读 38% · 共 128 章",
                fullDirectoryLabel = "完整目录 >",
                searchPlaceholder = "搜索章节",
                tabs = listOf(
                    ReadingTocPanelTabUiModel("目录", "directory"),
                    ReadingTocPanelTabUiModel("书签", "bookmark")
                ),
                currentVolume = "当前卷：雨城",
                returnProgressLabel = "回到进度",
                filterLabel = "筛选：未读"
            ),
            chapters = ReadingTocBookmarkFixture.chapters,
            bookmarks = ReadingTocBookmarkFixture.bookmarks,
            search = ReadingTocSearchUiModel(
                query = "雨",
                resultsLabel = "搜索到 3 个章节",
                results = listOf(
                    ReadingChapterRowUiModel("第 31 章 沉街", "已读", current = false, read = true),
                    ReadingChapterRowUiModel("第 32 章 雨夜", "当前 · 38%", current = true, read = true),
                    ReadingChapterRowUiModel("第 37 章 雨停", "未读", current = false, read = false)
                )
            ),
            moreMenu = ReadingTocMoreMenuUiModel(
                title = "更多",
                items = listOf(
                    ReadingMoreMenuItemUiModel("缓存当前卷", "仅缓存当前目录卷"),
                    ReadingMoreMenuItemUiModel("只看未读", "筛选未读章节")
                )
            ),
            loading = ReaderShellFeedbackUiModel(
                title = "正在加载",
                message = "正在加载目录和书签，请稍候。"
            ),
            empty = ReaderShellFeedbackUiModel(
                title = "暂无书签",
                message = "在阅读页长按文字或使用书签入口后，会在这里显示。",
                primaryAction = "返回目录"
            ),
            error = ReaderShellFeedbackUiModel(
                title = "加载失败，请重试",
                message = "目录加载失败，已保留当前阅读位置。",
                primaryAction = "重试"
            ),
            moduleNav = listOf(
                ReadingModuleNavItemUiModel("目录", "directory", active = true),
                ReadingModuleNavItemUiModel("朗读", "tts", active = false),
                ReadingModuleNavItemUiModel("界面", "appearance", active = false),
                ReadingModuleNavItemUiModel("设置", "settings", active = false)
            ),
            brightness = ReadingBrightnessUiModel(
                title = "亮度",
                value = 38,
                autoLabel = "A",
                modeLabel = "系统"
            ),
            bottomReadout = ReadingBottomReadoutUiModel(
                progress = "38%",
                chapter = "第 32/128 章"
            )
        )

    fun bookmark(): ReadingTocBookmarkUiState =
        fromFixture().copy(displayState = ReadingTocBookmarkDisplayState.Bookmark)

    fun search(): ReadingTocBookmarkUiState =
        fromFixture().copy(displayState = ReadingTocBookmarkDisplayState.Search)

    fun empty(): ReadingTocBookmarkUiState =
        fromFixture().copy(displayState = ReadingTocBookmarkDisplayState.Empty)

    fun loading(): ReadingTocBookmarkUiState =
        fromFixture().copy(displayState = ReadingTocBookmarkDisplayState.Loading)

    fun error(): ReadingTocBookmarkUiState =
        fromFixture().copy(displayState = ReadingTocBookmarkDisplayState.Error)

    fun moreMenu(): ReadingTocBookmarkUiState =
        fromFixture().copy(displayState = ReadingTocBookmarkDisplayState.MoreMenu)
}

object ReadingTocBookmarkFixture {
    val chapters = listOf(
        ReadingChapterRowUiModel("第 29 章 风声", "缓存", current = false, read = false),
        ReadingChapterRowUiModel("第 30 章 桥灯", "已读", current = false, read = true),
        ReadingChapterRowUiModel("第 31 章 沉街", "已读", current = false, read = true),
        ReadingChapterRowUiModel("第 32 章 雨夜", "当前 · 38%", current = true, read = true),
        ReadingChapterRowUiModel("第 33 章 灯下人", "未读", current = false, read = false),
        ReadingChapterRowUiModel("第 34 章 旧巷", "未读", current = false, read = false),
        ReadingChapterRowUiModel("第 35 章 纸船", "未读", current = false, read = false),
        ReadingChapterRowUiModel("第 36 章 回声", "未读", current = false, read = false),
        ReadingChapterRowUiModel("第 37 章 雨停", "未读", current = false, read = false),
        ReadingChapterRowUiModel("第 38 章 清晨", "未读", current = false, read = false)
    )

    val bookmarks = listOf(
        ReadingBookmarkRowUiModel(
            chapter = "第 32 章 雨夜",
            excerpt = "“等你回来。”",
            location = "38%",
            time = "10:12"
        ),
        ReadingBookmarkRowUiModel(
            chapter = "第 31 章 沉街",
            excerpt = "街灯在水里拉出很长的影子。",
            location = "35%",
            time = "昨天"
        )
    )
}
