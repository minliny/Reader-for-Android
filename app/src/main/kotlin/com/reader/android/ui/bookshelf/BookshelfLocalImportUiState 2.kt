package com.reader.android.ui.bookshelf

enum class BookshelfLocalImportDisplayState {
    Default,
    Importing,
    Success,
    PartialFailed,
    Failed,
    PickerCancelled
}

data class BookshelfLocalImportIntroUiModel(
    val title: String,
    val message: String,
    val formats: List<String>,
    val primaryAction: String
)

data class BookshelfLocalImportPermissionUiModel(
    val title: String,
    val message: String,
    val footnote: String
)

data class BookshelfLocalImportFormatUiModel(
    val label: String,
    val message: String,
    val tone: String
)

data class BookshelfLocalImportFlowStepUiModel(
    val step: String,
    val label: String
)

data class BookshelfLocalImportProgressUiModel(
    val title: String,
    val message: String,
    val currentFile: String,
    val progress: Int
)

data class BookshelfLocalImportResultCountUiModel(
    val label: String,
    val value: Int,
    val tone: String
)

data class BookshelfLocalImportResultRowUiModel(
    val fileName: String,
    val status: String,
    val reason: String,
    val tone: String
)

data class BookshelfLocalImportSummaryUiModel(
    val title: String,
    val message: String,
    val counts: List<BookshelfLocalImportResultCountUiModel>
)

data class BookshelfLocalImportActionsUiModel(
    val chooseAgain: String = "重新选择",
    val done: String = "完成",
    val backToBookshelf: String = "返回书架"
)

data class BookshelfLocalImportCancelUiModel(
    val title: String,
    val message: String
)

data class BookshelfLocalImportUiState(
    val title: String = "导入本地书",
    val backLabel: String = "返回",
    val intro: BookshelfLocalImportIntroUiModel,
    val permission: BookshelfLocalImportPermissionUiModel,
    val supportedFormats: List<BookshelfLocalImportFormatUiModel>,
    val flow: List<BookshelfLocalImportFlowStepUiModel>,
    val reminderTitle: String,
    val reminderMessage: String,
    val importing: BookshelfLocalImportProgressUiModel,
    val summary: BookshelfLocalImportSummaryUiModel,
    val results: List<BookshelfLocalImportResultRowUiModel>,
    val actions: BookshelfLocalImportActionsUiModel,
    val cancelState: BookshelfLocalImportCancelUiModel,
    val displayState: BookshelfLocalImportDisplayState = BookshelfLocalImportDisplayState.Default
) {
    val isResultState: Boolean get() =
        displayState == BookshelfLocalImportDisplayState.Success ||
            displayState == BookshelfLocalImportDisplayState.PartialFailed ||
            displayState == BookshelfLocalImportDisplayState.Failed
}

object BookshelfLocalImportMapper {
    fun fromFixture(): BookshelfLocalImportUiState =
        BookshelfLocalImportUiState(
            intro = BookshelfLocalImportFixture.intro,
            permission = BookshelfLocalImportFixture.permission,
            supportedFormats = BookshelfLocalImportFixture.supportedFormats,
            flow = BookshelfLocalImportFixture.flow,
            reminderTitle = BookshelfLocalImportFixture.reminderTitle,
            reminderMessage = BookshelfLocalImportFixture.reminderMessage,
            importing = BookshelfLocalImportFixture.importing,
            summary = BookshelfLocalImportFixture.partialSummary,
            results = BookshelfLocalImportFixture.results,
            actions = BookshelfLocalImportFixture.actions,
            cancelState = BookshelfLocalImportFixture.cancelState
        )

    fun importing(): BookshelfLocalImportUiState =
        fromFixture().copy(displayState = BookshelfLocalImportDisplayState.Importing)

    fun success(): BookshelfLocalImportUiState =
        fromFixture().copy(
            displayState = BookshelfLocalImportDisplayState.Success,
            summary = BookshelfLocalImportFixture.successSummary,
            results = BookshelfLocalImportFixture.results.filter { it.tone == "success" || it.tone == "skip" }
        )

    fun partialFailed(): BookshelfLocalImportUiState =
        fromFixture().copy(displayState = BookshelfLocalImportDisplayState.PartialFailed)

    fun failed(): BookshelfLocalImportUiState =
        fromFixture().copy(
            displayState = BookshelfLocalImportDisplayState.Failed,
            summary = BookshelfLocalImportFixture.failedSummary,
            results = BookshelfLocalImportFixture.results.filter { it.tone == "failed" }
        )

    fun pickerCancelled(): BookshelfLocalImportUiState =
        fromFixture().copy(displayState = BookshelfLocalImportDisplayState.PickerCancelled)
}

object BookshelfLocalImportFixture {
    val intro = BookshelfLocalImportIntroUiModel(
        title = "选择本地书籍",
        message = "通过 Android 系统文件选择器导入文件，不会扫描整个存储。",
        formats = listOf("TXT", "EPUB", "可多选"),
        primaryAction = "选择文件"
    )

    val permission = BookshelfLocalImportPermissionUiModel(
        title = "只访问你选择的文件",
        message = "导入通过系统文件选择器授权完成。应用不会扫描全盘，也不需要“管理所有文件”权限。",
        footnote = "取消选择会直接返回书架，不作为错误处理"
    )

    val supportedFormats = listOf(
        BookshelfLocalImportFormatUiModel("TXT", "纯文本小说、章节文本", "blue"),
        BookshelfLocalImportFormatUiModel("EPUB", "标准 EPUB 电子书", "green")
    )

    val flow = listOf(
        BookshelfLocalImportFlowStepUiModel("1", "选择文件"),
        BookshelfLocalImportFlowStepUiModel("2", "解析书籍"),
        BookshelfLocalImportFlowStepUiModel("3", "加入书架")
    )

    const val reminderTitle = "导入后可在书架中继续阅读"
    const val reminderMessage = "重复文件会提示跳过；不支持或解析失败的文件会在结果页展示原因。"

    val importing = BookshelfLocalImportProgressUiModel(
        title = "正在导入",
        message = "正在解析文件，请稍候",
        currentFile = "正在解析：星海旧章.epub",
        progress = 58
    )

    val partialCounts = listOf(
        BookshelfLocalImportResultCountUiModel("已导入", 2, "success"),
        BookshelfLocalImportResultCountUiModel("跳过", 1, "skip"),
        BookshelfLocalImportResultCountUiModel("失败", 2, "failed")
    )

    val successSummary = BookshelfLocalImportSummaryUiModel(
        title = "导入完成",
        message = "已将选中的书籍加入书架",
        counts = listOf(
            BookshelfLocalImportResultCountUiModel("已导入", 3, "success"),
            BookshelfLocalImportResultCountUiModel("跳过", 1, "skip"),
            BookshelfLocalImportResultCountUiModel("失败", 0, "muted")
        )
    )

    val partialSummary = BookshelfLocalImportSummaryUiModel(
        title = "部分文件导入失败",
        message = "可以查看失败原因后重新选择",
        counts = partialCounts
    )

    val failedSummary = BookshelfLocalImportSummaryUiModel(
        title = "导入失败",
        message = "未能导入选中的文件",
        counts = listOf(
            BookshelfLocalImportResultCountUiModel("已导入", 0, "muted"),
            BookshelfLocalImportResultCountUiModel("跳过", 0, "muted"),
            BookshelfLocalImportResultCountUiModel("失败", 3, "failed")
        )
    )

    val results = listOf(
        BookshelfLocalImportResultRowUiModel("星海旧章.epub", "已导入", "", "success"),
        BookshelfLocalImportResultRowUiModel("雨线手记.txt", "已导入", "", "success"),
        BookshelfLocalImportResultRowUiModel("纸上群山.txt", "已存在，已跳过", "书架中已有同名本地书。", "skip"),
        BookshelfLocalImportResultRowUiModel("图片合集.zip", "格式不支持", "请选择 TXT 或 EPUB 文件后重新导入。", "failed"),
        BookshelfLocalImportResultRowUiModel("旧城残卷.epub", "解析失败", "文件结构不完整，可以更换文件后重新选择。", "failed")
    )

    val actions = BookshelfLocalImportActionsUiModel()

    val cancelState = BookshelfLocalImportCancelUiModel(
        title = "已取消选择",
        message = "取消系统文件选择器会返回进入前书架状态，不展示错误页。"
    )
}
