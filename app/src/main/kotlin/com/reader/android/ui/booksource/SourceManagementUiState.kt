package com.reader.android.ui.booksource

import com.reader.android.data.model.BookSource
import com.reader.android.ui.adapter.ReaderIntegrationLevel

enum class SourceUiStatus(val label: String) {
    Enabled("已启用"),
    Disabled("已禁用"),
    Error("需检查")
}

enum class SourceImportMode(val label: String) {
    JsonText("JSON 文本"),
    CustomUrl("自定义 URL"),
    RepositoryUrl("仓库 URL")
}

enum class SourceImportStatus {
    Idle,
    BlankJson,
    Ready,
    Imported,
    Error
}

enum class SourceTestStatus {
    Idle,
    Loading,
    Success,
    Failure
}

data class SourceTestUiState(
    val status: SourceTestStatus = SourceTestStatus.Idle,
    val sourceId: String? = null,
    val message: String = "尚未测试"
)

data class SourceImportUiState(
    val mode: SourceImportMode = SourceImportMode.JsonText,
    val status: SourceImportStatus = SourceImportStatus.Idle,
    val message: String = "选择导入方式",
    val importedCount: Int = 0,
    val input: String = ""
)

data class SourceUrlInputUiState(
    val mode: SourceImportMode,
    val input: String,
    val isValid: Boolean,
    val message: String
)

data class SourceUiModel(
    val id: String,
    val name: String,
    val url: String,
    val group: String,
    val enabled: Boolean,
    val status: SourceUiStatus,
    val searchRuleStatus: String,
    val tocRuleStatus: String,
    val contentRuleStatus: String,
    val lastTestResult: SourceTestUiState = SourceTestUiState(),
    val errorMessage: String? = null
)

data class SourceManagementUiState(
    val sources: List<SourceUiModel>,
    val isLoading: Boolean = false,
    val importState: SourceImportUiState = SourceImportUiState(),
    val testState: SourceTestUiState = SourceTestUiState(),
    val notice: String? = null,
    val selectedSourceId: String? = null,
    val fakeRealModeLabel: String = "fake repository fallback",
    val emptyMessage: String = "点击右下角按钮导入书源",
    val errorMessage: String? = null,
    val allowRealDataIntegration: Boolean = true,
    val integrationLevel: ReaderIntegrationLevel = ReaderIntegrationLevel.READY_EXISTING_FLOW
) {
    val isEmpty: Boolean get() = sources.isEmpty() && !isLoading && errorMessage == null
}

object SourceManagementMapper {
    fun fromSources(
        sources: List<BookSource>,
        selectedSourceId: String? = null,
        importState: SourceImportUiState = SourceImportUiState(),
        testState: SourceTestUiState = SourceTestUiState(),
        notice: String? = null,
        fakeRealModeLabel: String = "existing repository flow"
    ): SourceManagementUiState =
        SourceManagementUiState(
            sources = sources.map { it.toUiModel(testState) },
            importState = importState,
            testState = testState,
            notice = notice,
            selectedSourceId = selectedSourceId,
            fakeRealModeLabel = fakeRealModeLabel
        )

    fun fakeFallback(): SourceManagementUiState =
        fromSources(
            sources = SourceManagementFixture.sources,
            fakeRealModeLabel = "fake repository fallback"
        )

    fun importPreview(json: String): SourceImportUiState {
        val trimmed = json.trim()
        return when {
            trimmed.isBlank() -> SourceImportUiState(
                status = SourceImportStatus.BlankJson,
                message = "导入内容为空，请粘贴 JSON 后重试",
                input = json
            )
            !trimmed.startsWith("[") && !trimmed.startsWith("{") -> SourceImportUiState(
                status = SourceImportStatus.Error,
                message = "导入内容不是 JSON",
                input = json
            )
            else -> SourceImportUiState(
                status = SourceImportStatus.Ready,
                message = "JSON 待导入",
                input = json
            )
        }
    }

    fun importSuccess(count: Int): SourceImportUiState =
        SourceImportUiState(
            status = SourceImportStatus.Imported,
            message = "已导入 $count 个书源",
            importedCount = count
        )

    fun validateUrlInput(
        input: String,
        mode: SourceImportMode
    ): SourceUrlInputUiState {
        val trimmed = input.trim()
        val valid = trimmed.startsWith("http://") || trimmed.startsWith("https://")
        return SourceUrlInputUiState(
            mode = mode,
            input = input,
            isValid = valid,
            message = when {
                trimmed.isBlank() -> "${mode.label} 不能为空"
                valid -> "${mode.label} 可用"
                else -> "${mode.label} 必须以 http:// 或 https:// 开头"
            }
        )
    }

    fun noOpNotice(action: String): String =
        "$action 暂无可执行内容"

    fun testLoading(sourceId: String): SourceTestUiState =
        SourceTestUiState(
            status = SourceTestStatus.Loading,
            sourceId = sourceId,
            message = "正在测试书源"
        )

    fun testSuccess(sourceId: String): SourceTestUiState =
        SourceTestUiState(
            status = SourceTestStatus.Success,
            sourceId = sourceId,
            message = "书源测试通过"
        )

    fun testFailure(sourceId: String, message: String): SourceTestUiState =
        SourceTestUiState(
            status = SourceTestStatus.Failure,
            sourceId = sourceId,
            message = message
        )

    fun detailDataOf(source: SourceUiModel): SourceDetailData =
        SourceDetailData(
            sourceName = source.name,
            sourceUrl = source.url,
            sourceGroup = source.group,
            enabled = source.enabled,
            searchRuleStatus = source.searchRuleStatus,
            tocRuleStatus = source.tocRuleStatus,
            contentRuleStatus = source.contentRuleStatus
        )

    private fun BookSource.toUiModel(testState: SourceTestUiState): SourceUiModel {
        val error = validationError()
        val id = sourceUrl.ifBlank { "source:${sourceName.ifBlank { "unnamed" }}" }
        return SourceUiModel(
            id = id,
            name = sourceName.ifBlank { "未命名书源" },
            url = sourceUrl,
            group = sourceGroup?.takeIf { it.isNotBlank() } ?: "未分组",
            enabled = enabled,
            status = when {
                error != null -> SourceUiStatus.Error
                enabled -> SourceUiStatus.Enabled
                else -> SourceUiStatus.Disabled
            },
            searchRuleStatus = ruleStatus(searchUrl),
            tocRuleStatus = ruleStatus(tocUrl),
            contentRuleStatus = ruleStatus(contentUrl),
            lastTestResult = if (testState.sourceId == id || testState.sourceId == sourceUrl) testState else SourceTestUiState(),
            errorMessage = error
        )
    }

    private fun BookSource.validationError(): String? =
        when {
            sourceUrl.isBlank() -> "sourceUrl is missing"
            sourceName.isBlank() -> "sourceName is missing"
            else -> null
        }

    private fun ruleStatus(rule: String?): String =
        if (rule.isNullOrBlank()) "未配置" else "已配置"
}

object SourceManagementFixture {
    val sources: List<BookSource> = listOf(
        BookSource(
            sourceUrl = "https://fixture.local/source-a",
            sourceName = "Fixture 书源 A",
            sourceGroup = "中文",
            enabled = true,
            searchUrl = "/search?q=key",
            tocUrl = "/toc",
            contentUrl = "/content"
        ),
        BookSource(
            sourceUrl = "https://fixture.local/source-b",
            sourceName = "Fixture 书源 B",
            sourceGroup = "备用",
            enabled = false,
            searchUrl = "/search?keyword=key"
        )
    )
}
