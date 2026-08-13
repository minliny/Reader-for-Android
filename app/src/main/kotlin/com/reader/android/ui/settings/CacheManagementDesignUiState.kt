package com.reader.android.ui.settings

enum class CacheManagementDisplayState {
    Default,
    Loading,
    Empty,
    Confirm,
    Error
}

enum class CacheManagementRowType {
    Link,
    Switch,
    Select
}

data class CacheManagementTopBarUiModel(
    val title: String,
    val backLabel: String
)

data class CacheStorageUiModel(
    val title: String,
    val value: String,
    val percentLabel: String,
    val percent: Float,
    val copy: String
)

data class CacheManagementRowUiModel(
    val type: CacheManagementRowType,
    val icon: String,
    val title: String,
    val meta: String,
    val value: String = "",
    val enabled: Boolean = false
)

data class CacheManagementSectionUiModel(
    val title: String,
    val rows: List<CacheManagementRowUiModel>
)

data class CacheManagementDangerUiModel(
    val title: String,
    val meta: String,
    val confirmTitle: String,
    val copy: String,
    val cancelLabel: String,
    val confirmLabel: String
)

data class CacheManagementEmptyUiModel(
    val title: String,
    val message: String,
    val primaryAction: String
)

data class CacheManagementToastUiModel(
    val success: String,
    val error: String
)

data class CacheManagementFeedbackUiModel(
    val title: String,
    val message: String,
    val primaryAction: String? = null
)

data class CacheManagementUiState(
    val topBar: CacheManagementTopBarUiModel,
    val storage: CacheStorageUiModel,
    val sections: List<CacheManagementSectionUiModel>,
    val danger: CacheManagementDangerUiModel,
    val empty: CacheManagementEmptyUiModel,
    val toast: CacheManagementToastUiModel,
    val loading: CacheManagementFeedbackUiModel,
    val error: CacheManagementFeedbackUiModel,
    val cleanupResult: CacheManagementFeedbackUiModel,
    val displayState: CacheManagementDisplayState = CacheManagementDisplayState.Default
)

object CacheManagementMapper {
    fun fromFixture(): CacheManagementUiState =
        CacheManagementUiState(
            topBar = CacheManagementTopBarUiModel(
                title = "缓存管理",
                backLabel = "返回"
            ),
            storage = CacheStorageUiModel(
                title = "缓存占用",
                value = "1.28 GB",
                percentLabel = "62%",
                percent = 0.62f,
                copy = "正在计算各类缓存占用，分类结果会在下方同步更新。"
            ),
            sections = listOf(
                CacheManagementSectionUiModel(
                    title = "缓存分类",
                    rows = listOf(
                        CacheManagementRowUiModel(
                            type = CacheManagementRowType.Link,
                            icon = "clock",
                            title = "总缓存",
                            meta = "所有缓存数据占用的存储空间",
                            value = "1.28 GB"
                        ),
                        CacheManagementRowUiModel(
                            type = CacheManagementRowType.Link,
                            icon = "book",
                            title = "书籍缓存",
                            meta = "章节内容、目录等阅读数据",
                            value = "892 MB"
                        ),
                        CacheManagementRowUiModel(
                            type = CacheManagementRowType.Link,
                            icon = "image",
                            title = "封面缓存",
                            meta = "书籍封面图片缓存",
                            value = "126 MB"
                        ),
                        CacheManagementRowUiModel(
                            type = CacheManagementRowType.Link,
                            icon = "search",
                            title = "搜索缓存",
                            meta = "搜索记录、索引和结果缓存",
                            value = "84 MB"
                        ),
                        CacheManagementRowUiModel(
                            type = CacheManagementRowType.Link,
                            icon = "source",
                            title = "RSS 缓存",
                            meta = "订阅内容、图片和附件缓存",
                            value = "178 MB"
                        )
                    )
                ),
                CacheManagementSectionUiModel(
                    title = "缓存策略",
                    rows = listOf(
                        CacheManagementRowUiModel(
                            type = CacheManagementRowType.Switch,
                            icon = "storage",
                            title = "优先读取缓存",
                            meta = "优先使用本地缓存提升阅读速度",
                            enabled = true
                        ),
                        CacheManagementRowUiModel(
                            type = CacheManagementRowType.Switch,
                            icon = "download",
                            title = "自动缓存后续章节",
                            meta = "预读并缓存后续章节以便离线阅读",
                            enabled = true
                        ),
                        CacheManagementRowUiModel(
                            type = CacheManagementRowType.Select,
                            icon = "list",
                            title = "缓存范围",
                            meta = "设置预读并缓存的章节数量",
                            value = "5 章"
                        ),
                        CacheManagementRowUiModel(
                            type = CacheManagementRowType.Select,
                            icon = "folder",
                            title = "下载与缓存位置",
                            meta = "选择下载和缓存文件的存储位置",
                            value = "内部存储"
                        )
                    )
                )
            ),
            danger = CacheManagementDangerUiModel(
                title = "清理缓存",
                meta = "删除所有缓存数据，释放存储空间",
                confirmTitle = "确认清理缓存？",
                copy = "清理后会删除章节、封面、搜索和 RSS 缓存，不会删除书架记录。",
                cancelLabel = "取消",
                confirmLabel = "确认清理"
            ),
            empty = CacheManagementEmptyUiModel(
                title = "暂无缓存",
                message = "当前没有可清理的缓存数据。",
                primaryAction = "重新计算"
            ),
            toast = CacheManagementToastUiModel(
                success = "已清理",
                error = "操作失败，请重试"
            ),
            loading = CacheManagementFeedbackUiModel(
                title = "正在计算",
                message = "正在计算各类缓存占用，分类结果会在下方同步更新。"
            ),
            error = CacheManagementFeedbackUiModel(
                title = "清理失败态",
                message = "失败保留上下文和可恢复反馈。",
                primaryAction = "重试"
            ),
            cleanupResult = CacheManagementFeedbackUiModel(
                title = "已清理",
                message = "已释放缓存空间，书架记录和阅读进度已保留。"
            )
        )

    fun loading(): CacheManagementUiState =
        fromFixture().copy(displayState = CacheManagementDisplayState.Loading)

    fun empty(): CacheManagementUiState =
        fromFixture().copy(displayState = CacheManagementDisplayState.Empty)

    fun confirm(): CacheManagementUiState =
        fromFixture().copy(displayState = CacheManagementDisplayState.Confirm)

    fun error(): CacheManagementUiState =
        fromFixture().copy(displayState = CacheManagementDisplayState.Error)
}
