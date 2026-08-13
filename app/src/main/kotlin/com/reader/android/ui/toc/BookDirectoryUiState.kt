package com.reader.android.ui.toc

import com.reader.android.data.model.TOCItem
import com.reader.android.ui.adapter.ReaderIntegrationLevel

data class BookDirectorySummaryUiModel(
    val title: String,
    val sourceLabel: String,
    val chapterCount: String
)

data class BookDirectoryCurrentChapterUiModel(
    val title: String,
    val status: String = "当前阅读",
    val progress: Int = 0
)

enum class BookDirectoryChapterStatus(val label: String) {
    Unread("未读"),
    Read("已读")
}

data class BookDirectoryChapterUiModel(
    val id: String,
    val title: String,
    val status: BookDirectoryChapterStatus,
    val isNew: Boolean,
    val target: String
)

enum class BookDirectoryDisplayState {
    Default,
    Loading,
    Empty,
    Error
}

data class BookDirectoryFeedbackUiModel(
    val title: String,
    val message: String,
    val primaryAction: String = "重试",
    val secondaryAction: String = "返回详情"
)

data class BookDirectoryUiState(
    val topBarTitle: String = "目录",
    val summary: BookDirectorySummaryUiModel,
    val currentChapter: BookDirectoryCurrentChapterUiModel,
    val chapters: List<BookDirectoryChapterUiModel>,
    val displayState: BookDirectoryDisplayState = BookDirectoryDisplayState.Default,
    val feedback: BookDirectoryFeedbackUiModel? = null,
    val allowRealDataIntegration: Boolean = false,
    val boundaryLevel: ReaderIntegrationLevel = ReaderIntegrationLevel.NEEDS_ADAPTER
) {
    val hasChapterList: Boolean get() =
        displayState == BookDirectoryDisplayState.Default && chapters.isNotEmpty()
}

object BookDirectoryUiStateMapper {
    fun fromFixture(): BookDirectoryUiState =
        BookDirectoryUiState(
            summary = BookDirectoryFixture.summary,
            currentChapter = BookDirectoryFixture.currentChapter,
            chapters = BookDirectoryFixture.chapters
        )

    fun loading(): BookDirectoryUiState =
        fromFixture().copy(
            displayState = BookDirectoryDisplayState.Loading,
            chapters = emptyList(),
            feedback = BookDirectoryFixture.loadingFeedback
        )

    fun empty(): BookDirectoryUiState =
        fromFixture().copy(
            displayState = BookDirectoryDisplayState.Empty,
            chapters = emptyList(),
            feedback = BookDirectoryFixture.emptyFeedback
        )

    fun error(message: String = BookDirectoryFixture.errorFeedback.message): BookDirectoryUiState =
        fromFixture().copy(
            displayState = BookDirectoryDisplayState.Error,
            chapters = emptyList(),
            feedback = BookDirectoryFixture.errorFeedback.copy(message = message)
        )

    fun fromTocItems(
        tocItems: List<TOCItem>,
        title: String = "目录",
        sourceLabel: String = "当前来源：默认来源",
        currentChapterTitle: String = ""
    ): BookDirectoryUiState {
        val flat = flatten(tocItems)
        if (flat.isEmpty()) return empty().copy(
            summary = BookDirectorySummaryUiModel(title, sourceLabel, "共 0 章")
        )

        return BookDirectoryUiState(
            summary = BookDirectorySummaryUiModel(title, sourceLabel, "共 ${flat.size} 章"),
            currentChapter = BookDirectoryCurrentChapterUiModel(
                title = currentChapterTitle.ifBlank { flat.first().title },
                progress = 0
            ),
            chapters = flat.mapIndexed { index, item ->
                BookDirectoryChapterUiModel(
                    id = "toc-$index",
                    title = item.title.ifBlank { "未命名章节" },
                    status = if (index == 0) BookDirectoryChapterStatus.Read else BookDirectoryChapterStatus.Unread,
                    isNew = index >= flat.size - 2,
                    target = item.url
                )
            }
        )
    }

    private fun flatten(items: List<TOCItem>): List<TOCItem> =
        items.flatMap { item ->
            val self = if (item.url.isNotBlank()) listOf(item) else emptyList()
            self + flatten(item.children)
        }
}

object BookDirectoryFixture {
    val summary = BookDirectorySummaryUiModel(
        title = "长夜余火",
        sourceLabel = "当前来源：起点中文网",
        chapterCount = "共 1862 章"
    )

    val currentChapter = BookDirectoryCurrentChapterUiModel(
        title = "第 32 章 雨夜",
        progress = 38
    )

    val chapters = listOf(
        chapter(1862, "新世界", BookDirectoryChapterStatus.Unread, isNew = true),
        chapter(1861, "旧日回声", BookDirectoryChapterStatus.Unread, isNew = true),
        chapter(1860, "雨停之后", BookDirectoryChapterStatus.Unread),
        chapter(1859, "灰土边界", BookDirectoryChapterStatus.Unread),
        chapter(1858, "北方来信", BookDirectoryChapterStatus.Unread),
        chapter(1857, "火种", BookDirectoryChapterStatus.Unread),
        chapter(1856, "漫长街区", BookDirectoryChapterStatus.Read),
        chapter(1855, "另一盏灯", BookDirectoryChapterStatus.Read),
        chapter(1854, "夜行者", BookDirectoryChapterStatus.Read),
        chapter(1853, "风暴之前", BookDirectoryChapterStatus.Read),
        chapter(1852, "回到城里", BookDirectoryChapterStatus.Read),
        chapter(1851, "雨后", BookDirectoryChapterStatus.Read)
    )

    val loadingFeedback = BookDirectoryFeedbackUiModel(
        title = "正在加载目录",
        message = "保留顶部栏和摘要栏，只刷新章节列表。"
    )

    val emptyFeedback = BookDirectoryFeedbackUiModel(
        title = "暂无目录",
        message = "当前来源没有返回章节目录，可以重试或返回详情。"
    )

    val errorFeedback = BookDirectoryFeedbackUiModel(
        title = "目录加载失败",
        message = "目录仍保留当前书籍上下文，可以重试或返回详情。"
    )

    private fun chapter(
        number: Int,
        name: String,
        status: BookDirectoryChapterStatus,
        isNew: Boolean = false
    ): BookDirectoryChapterUiModel =
        BookDirectoryChapterUiModel(
            id = "chapter-$number",
            title = "第 $number 章 $name",
            status = status,
            isNew = isNew,
            target = "fixture-content-$number"
        )
}
