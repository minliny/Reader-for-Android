package com.reader.android.ui.bookshelf

enum class BookshelfSortFilterSectionMode {
    Single,
    Multi
}

enum class BookshelfSortFilterDisplayState {
    Default,
    Selected,
    Empty,
    Error
}

data class BookshelfGroupChipUiModel(
    val label: String,
    val active: Boolean
)

data class BookshelfSortFilterBackdropBookUiModel(
    val title: String,
    val meta: String
)

data class BookshelfSortFilterOptionUiModel(
    val label: String,
    val type: String,
    val active: Boolean
)

data class BookshelfSortFilterSectionUiModel(
    val title: String,
    val mode: BookshelfSortFilterSectionMode,
    val options: List<BookshelfSortFilterOptionUiModel>
)

data class BookshelfSortFilterFeedbackUiModel(
    val title: String,
    val message: String,
    val primaryAction: String,
    val secondaryAction: String
)

data class BookshelfSortFilterUiState(
    val statusTime: String = "9:41",
    val backdropTitle: String = "书架",
    val groups: List<BookshelfGroupChipUiModel>,
    val backdropBooks: List<BookshelfSortFilterBackdropBookUiModel>,
    val sheetTitle: String = "排序与筛选",
    val sections: List<BookshelfSortFilterSectionUiModel>,
    val resetLabel: String = "重置",
    val applyLabel: String = "应用",
    val displayState: BookshelfSortFilterDisplayState = BookshelfSortFilterDisplayState.Default,
    val feedback: BookshelfSortFilterFeedbackUiModel? = null,
    val toastMessage: String? = null
) {
    val isFeedbackState: Boolean get() =
        displayState == BookshelfSortFilterDisplayState.Empty ||
            displayState == BookshelfSortFilterDisplayState.Error
}

object BookshelfSortFilterMapper {
    fun fromFixture(): BookshelfSortFilterUiState =
        BookshelfSortFilterUiState(
            groups = BookshelfSortFilterFixture.groups,
            backdropBooks = BookshelfSortFilterFixture.backdropBooks,
            sections = BookshelfSortFilterFixture.defaultSections()
        )

    fun selected(): BookshelfSortFilterUiState =
        fromFixture().copy(
            displayState = BookshelfSortFilterDisplayState.Selected,
            sections = BookshelfSortFilterFixture.selectedSections(),
            toastMessage = BookshelfSortFilterFixture.successToast
        )

    fun empty(): BookshelfSortFilterUiState =
        fromFixture().copy(
            displayState = BookshelfSortFilterDisplayState.Empty,
            feedback = BookshelfSortFilterFixture.emptyFeedback
        )

    fun error(message: String = BookshelfSortFilterFixture.errorFeedback.message): BookshelfSortFilterUiState =
        fromFixture().copy(
            displayState = BookshelfSortFilterDisplayState.Error,
            feedback = BookshelfSortFilterFixture.errorFeedback.copy(message = message)
        )

    fun fromBookshelfState(
        bookshelfState: BookshelfUiState,
        sortBy: BookshelfGroupFilterSort.SortBy = BookshelfGroupFilterSort.SortBy.RECENT
    ): BookshelfSortFilterUiState {
        val activeGroup = bookshelfState.selectedGroup
        val groups = bookshelfState.availableGroups.ifEmpty { BookshelfGroupFilterSort.groups() }.map {
            BookshelfGroupChipUiModel(label = it, active = it == activeGroup)
        }
        val filteredBooks = BookshelfGroupFilterSort.apply(bookshelfState.books, activeGroup, sortBy)
        return fromFixture().copy(
            groups = groups,
            backdropBooks = filteredBooks.map { book ->
                BookshelfSortFilterBackdropBookUiModel(
                    title = book.title,
                    meta = listOf(
                        book.sourceName,
                        book.currentChapterTitle,
                        book.cacheState.label
                    ).joinToString(" · ")
                )
            },
            sections = sectionsWithActiveSort(sortBy.label)
        )
    }

    private fun sectionsWithActiveSort(sortLabel: String): List<BookshelfSortFilterSectionUiModel> =
        BookshelfSortFilterFixture.defaultSections().map { section ->
            if (section.title != "排序方式") return@map section
            section.copy(options = section.options.map { option ->
                option.copy(active = option.label == sortLabel)
            })
        }
}

object BookshelfSortFilterFixture {
    val groups = listOf(
        BookshelfGroupChipUiModel("全部", active = true),
        BookshelfGroupChipUiModel("长篇追读", active = false),
        BookshelfGroupChipUiModel("资料", active = false),
        BookshelfGroupChipUiModel("未分组", active = false)
    )

    val backdropBooks = listOf(
        BookshelfSortFilterBackdropBookUiModel("深空信号", "最近阅读 · 有更新 · 已缓存"),
        BookshelfSortFilterBackdropBookUiModel("纸上群山", "网络书 · 未缓存"),
        BookshelfSortFilterBackdropBookUiModel("雨线手记", "本地书 · 已缓存")
    )

    val emptyFeedback = BookshelfSortFilterFeedbackUiModel(
        title = "筛选后没有书籍",
        message = "当前条件没有匹配结果，可以重置后重新筛选。",
        primaryAction = "重置",
        secondaryAction = "返回"
    )

    val errorFeedback = BookshelfSortFilterFeedbackUiModel(
        title = "操作失败，请重试",
        message = "已保留当前排序和筛选选择，可以重新应用。",
        primaryAction = "重试",
        secondaryAction = "取消"
    )

    const val successToast = "保存成功"

    fun defaultSections(): List<BookshelfSortFilterSectionUiModel> =
        listOf(
            BookshelfSortFilterSectionUiModel(
                title = "排序方式",
                mode = BookshelfSortFilterSectionMode.Single,
                options = listOf(
                    option("最近阅读", "recent-read", active = true),
                    option("最近更新", "recent-update"),
                    option("加入时间", "added-at"),
                    option("书名", "title")
                )
            ),
            BookshelfSortFilterSectionUiModel(
                title = "排序顺序",
                mode = BookshelfSortFilterSectionMode.Single,
                options = listOf(
                    option("降序", "desc", active = true),
                    option("升序", "asc")
                )
            ),
            BookshelfSortFilterSectionUiModel(
                title = "筛选条件",
                mode = BookshelfSortFilterSectionMode.Multi,
                options = listOf(
                    option("全部", "all", active = true),
                    option("本地书", "local"),
                    option("网络书", "network", active = true),
                    option("有更新", "updated", active = true),
                    option("已缓存", "cached")
                )
            )
        )

    fun selectedSections(): List<BookshelfSortFilterSectionUiModel> =
        defaultSections().map { section ->
            section.copy(options = section.options.map { option ->
                val active = when (section.title) {
                    "排序方式" -> option.type == "recent-update"
                    "排序顺序" -> option.type == "asc"
                    "筛选条件" -> option.type == "local" || option.type == "cached"
                    else -> option.active
                }
                option.copy(active = active)
            })
        }

    private fun option(
        label: String,
        type: String,
        active: Boolean = false
    ): BookshelfSortFilterOptionUiModel =
        BookshelfSortFilterOptionUiModel(label = label, type = type, active = active)
}
