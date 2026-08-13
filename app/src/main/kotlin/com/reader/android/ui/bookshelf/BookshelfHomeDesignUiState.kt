package com.reader.android.ui.bookshelf

enum class BookshelfHomeDisplayState {
    Default,
    Filtering,
    Loading,
    Empty
}

data class BookshelfHomeStatusUiModel(
    val time: String,
    val battery: String
)

data class BookshelfHomeTopBarUiModel(
    val title: String
)

data class BookshelfHomeGroupUiModel(
    val label: String,
    val active: Boolean
)

data class BookshelfHomeBookUiModel(
    val title: String,
    val author: String,
    val chapter: String,
    val progress: Float,
    val progressLabel: String = "${(progress.coerceIn(0f, 1f) * 100).toInt()}%"
)

data class BookshelfRecentUpdateUiModel(
    val title: String,
    val chapter: String,
    val unread: Boolean
)

data class BookshelfHomeFeedbackUiModel(
    val title: String,
    val message: String,
    val primaryAction: String,
    val secondaryAction: String
)

data class BookshelfHomeFeedbackSetUiModel(
    val loading: BookshelfHomeFeedbackUiModel,
    val empty: BookshelfHomeFeedbackUiModel,
    val noUpdates: BookshelfHomeFeedbackUiModel
)

data class BookshelfHomeUiState(
    val status: BookshelfHomeStatusUiModel,
    val topBar: BookshelfHomeTopBarUiModel,
    val groups: List<BookshelfHomeGroupUiModel>,
    val continueReading: BookshelfHomeBookUiModel?,
    val recentUpdates: List<BookshelfRecentUpdateUiModel>,
    val books: List<BookshelfHomeBookUiModel>,
    val feedback: BookshelfHomeFeedbackSetUiModel,
    val bottomNavLabels: List<String>,
    val displayState: BookshelfHomeDisplayState = BookshelfHomeDisplayState.Default
)

object BookshelfHomeMapper {
    fun fromFixture(): BookshelfHomeUiState =
        BookshelfHomeUiState(
            status = BookshelfHomeStatusUiModel(
                time = "10:30",
                battery = "82%"
            ),
            topBar = BookshelfHomeTopBarUiModel("书架"),
            groups = listOf(
                BookshelfHomeGroupUiModel("全部", active = true),
                BookshelfHomeGroupUiModel("默认", active = false),
                BookshelfHomeGroupUiModel("本地书", active = false),
                BookshelfHomeGroupUiModel("追更", active = false)
            ),
            continueReading = BookshelfHomeBookUiModel(
                title = "长夜余火",
                author = "爱潜水的乌贼",
                chapter = "第 32 章 雨夜",
                progress = 0.38f,
                progressLabel = "38%"
            ),
            recentUpdates = listOf(
                BookshelfRecentUpdateUiModel(
                    title = "诡秘之主",
                    chapter = "第 1426 章",
                    unread = true
                ),
                BookshelfRecentUpdateUiModel(
                    title = "明朝那些事儿",
                    chapter = "第 218 章",
                    unread = true
                )
            ),
            books = listOf(
                BookshelfHomeBookUiModel("长夜余火", "爱潜水的乌贼", "第 32 章 雨夜", 0.32f, "32%"),
                BookshelfHomeBookUiModel("明朝那些事儿", "当年明月", "第 218 章", 0.58f, "58%"),
                BookshelfHomeBookUiModel("三体", "刘慈欣", "65%", 0.65f, "65%"),
                BookshelfHomeBookUiModel("人间词话", "王国维", "已读完", 1f, "100%"),
                BookshelfHomeBookUiModel("诡秘之主", "爱潜水的乌贼", "第 1426 章", 0.58f, "58%"),
                BookshelfHomeBookUiModel("Android 札记", "本地文档", "Compose 动画", 1f, "100%")
            ),
            feedback = BookshelfHomeFeedbackSetUiModel(
                loading = BookshelfHomeFeedbackUiModel(
                    title = "正在加载书架",
                    message = "封面网格加载中，顶部入口和主导航保持可用。",
                    primaryAction = "",
                    secondaryAction = ""
                ),
                empty = BookshelfHomeFeedbackUiModel(
                    title = "书架暂无书籍",
                    message = "可以搜索书籍，也可以导入本地书。",
                    primaryAction = "搜索书籍",
                    secondaryAction = "导入本地书"
                ),
                noUpdates = BookshelfHomeFeedbackUiModel(
                    title = "最近没有更新",
                    message = "可以刷新，或切换到全部书籍继续阅读。",
                    primaryAction = "刷新",
                    secondaryAction = "查看全部"
                )
            ),
            bottomNavLabels = listOf("书架", "发现", "RSS", "设置")
        )

    fun filtering(): BookshelfHomeUiState =
        fromFixture().copy(
            displayState = BookshelfHomeDisplayState.Filtering,
            groups = fromFixture().groups.map { group ->
                group.copy(active = group.label == "追更")
            },
            books = fromFixture().books.filter { book ->
                book.title == "长夜余火" || book.title == "诡秘之主" || book.title == "明朝那些事儿"
            },
            continueReading = fromFixture().continueReading?.copy(chapter = "追更 · 第 32 章 雨夜")
        )

    fun loading(): BookshelfHomeUiState =
        fromFixture().copy(
            displayState = BookshelfHomeDisplayState.Loading,
            continueReading = null,
            recentUpdates = emptyList(),
            books = emptyList()
        )

    fun empty(): BookshelfHomeUiState =
        fromFixture().copy(
            displayState = BookshelfHomeDisplayState.Empty,
            continueReading = null,
            recentUpdates = emptyList(),
            books = emptyList()
        )
}
