package com.reader.android.ui.settings

enum class SourceManagementDesignDisplayState {
    Default,
    Edit,
    Log,
    Confirm,
    Loading,
    Empty,
    Error,
    Offline,
    Permission
}

enum class SourceManagementActionType {
    Link,
    Switch
}

data class SourceManagementTopBarUiModel(
    val title: String,
    val backLabel: String
)

data class SourceManagementMetricUiModel(
    val icon: String,
    val label: String,
    val value: String
)

data class SourceManagementSearchUiModel(
    val placeholder: String
)

data class SourceManagementChipUiModel(
    val label: String,
    val active: Boolean
)

data class SourceManagementActionRowUiModel(
    val type: SourceManagementActionType,
    val icon: String,
    val title: String,
    val meta: String,
    val actionLabel: String = "",
    val enabled: Boolean = false
)

data class SourceManagementSectionUiModel(
    val title: String,
    val rows: List<SourceManagementActionRowUiModel>
)

data class SourceRowUiModel(
    val title: String,
    val meta: String,
    val status: String,
    val tone: String,
    val enabled: Boolean
)

data class SourceManagementFabUiModel(
    val icon: String,
    val label: String
)

data class SourceEditFieldUiModel(
    val label: String,
    val value: String
)

data class SourceEditFormUiModel(
    val title: String,
    val fields: List<SourceEditFieldUiModel>,
    val saveLabel: String
)

data class SourceLogItemUiModel(
    val level: String,
    val copy: String
)

data class SourceLogPanelUiModel(
    val title: String,
    val items: List<SourceLogItemUiModel>
)

data class SourceManagementConfirmUiModel(
    val title: String,
    val copy: String,
    val cancelLabel: String,
    val confirmLabel: String
)

data class SourceManagementEmptyUiModel(
    val title: String,
    val copy: String,
    val primaryAction: String
)

data class SourceManagementToastUiModel(
    val success: String,
    val error: String,
    val offline: String,
    val permission: String
)

data class SourceManagementFeedbackUiModel(
    val title: String,
    val message: String,
    val primaryAction: String? = null
)

data class SourceManagementDesignUiState(
    val topBar: SourceManagementTopBarUiModel,
    val metrics: List<SourceManagementMetricUiModel>,
    val searchBox: SourceManagementSearchUiModel,
    val filters: List<SourceManagementChipUiModel>,
    val groups: List<SourceManagementChipUiModel>,
    val sections: List<SourceManagementSectionUiModel>,
    val sourceListTitle: String,
    val sources: List<SourceRowUiModel>,
    val fab: SourceManagementFabUiModel,
    val form: SourceEditFormUiModel,
    val log: SourceLogPanelUiModel,
    val confirm: SourceManagementConfirmUiModel,
    val empty: SourceManagementEmptyUiModel,
    val toast: SourceManagementToastUiModel,
    val loading: SourceManagementFeedbackUiModel,
    val error: SourceManagementFeedbackUiModel,
    val offline: SourceManagementFeedbackUiModel,
    val permission: SourceManagementFeedbackUiModel,
    val displayState: SourceManagementDesignDisplayState = SourceManagementDesignDisplayState.Default
)

object SourceManagementDesignMapper {
    fun fromFixture(): SourceManagementDesignUiState =
        SourceManagementDesignUiState(
            topBar = SourceManagementTopBarUiModel("书源管理", "返回"),
            metrics = listOf(
                SourceManagementMetricUiModel("source", "个书源", "12"),
                SourceManagementMetricUiModel("check", "个启用", "8"),
                SourceManagementMetricUiModel("warning", "个异常", "4"),
                SourceManagementMetricUiModel("clock", "刚刚检测", "10:30")
            ),
            searchBox = SourceManagementSearchUiModel("搜索框：搜索书源名称或域名"),
            filters = listOf("全部", "已启用", "异常", "未检测", "自定义").mapIndexed { index, label ->
                SourceManagementChipUiModel(label = label, active = index == 0)
            },
            groups = listOf("全部分组", "玄幻书源", "起点导入", "测试书源").mapIndexed { index, label ->
                SourceManagementChipUiModel(label = label, active = index == 0)
            },
            sections = listOf(
                SourceManagementSectionUiModel(
                    title = "批量操作",
                    rows = listOf(
                        SourceManagementActionRowUiModel(
                            type = SourceManagementActionType.Link,
                            icon = "refresh",
                            title = "检测",
                            meta = "检测全部启用书源的可用状态",
                            actionLabel = "开始检测"
                        ),
                        SourceManagementActionRowUiModel(
                            type = SourceManagementActionType.Link,
                            icon = "info",
                            title = "详情",
                            meta = "查看书源状态、分组和检测入口"
                        ),
                        SourceManagementActionRowUiModel(
                            type = SourceManagementActionType.Link,
                            icon = "edit",
                            title = "编辑",
                            meta = "进入书源详情后可编辑规则配置"
                        ),
                        SourceManagementActionRowUiModel(
                            type = SourceManagementActionType.Link,
                            icon = "log",
                            title = "错误日志",
                            meta = "查看最近一次检测失败原因"
                        ),
                        SourceManagementActionRowUiModel(
                            type = SourceManagementActionType.Switch,
                            icon = "source",
                            title = "启用开关",
                            meta = "控制选中书源是否参与搜索与换源",
                            enabled = true
                        )
                    )
                )
            ),
            sourceListTitle = "书源列表",
            sources = listOf(
                SourceRowUiModel("起点中文网", "qidian.com · 起点导入", "可用", "good", true),
                SourceRowUiModel("笔趣阁", "biquge.example · 玄幻书源", "异常", "warn", true),
                SourceRowUiModel("本地导入源", "本地文件导入 · 自定义", "未检测", "muted", false),
                SourceRowUiModel("测试书源", "test.example · 测试书源", "可用", "good", true)
            ),
            fab = SourceManagementFabUiModel("add", "新增"),
            form = SourceEditFormUiModel(
                title = "SourceEditForm · 新增书源",
                fields = listOf(
                    SourceEditFieldUiModel("书源名称", "测试书源"),
                    SourceEditFieldUiModel("域名", "test.example"),
                    SourceEditFieldUiModel("分组", "测试书源")
                ),
                saveLabel = "保存"
            ),
            log = SourceLogPanelUiModel(
                title = "LogPanel · 错误日志",
                items = listOf(
                    SourceLogItemUiModel("ERROR", "笔趣阁目录解析失败，返回字段缺失。"),
                    SourceLogItemUiModel("WARN", "本地导入源尚未检测，可手动点击检测。")
                )
            ),
            confirm = SourceManagementConfirmUiModel(
                title = "禁用书源？",
                copy = "禁用后该书源不会参与搜索、发现和阅读中换源。",
                cancelLabel = "取消",
                confirmLabel = "确认禁用"
            ),
            empty = SourceManagementEmptyUiModel(
                title = "暂无书源",
                copy = "当前筛选条件下没有书源，可以新增或切换筛选。",
                primaryAction = "新增书源"
            ),
            toast = SourceManagementToastUiModel(
                success = "保存成功",
                error = "加载失败，请重试",
                offline = "网络不可用，请稍后重试",
                permission = "需要网络权限"
            ),
            loading = SourceManagementFeedbackUiModel(
                title = "书源加载态",
                message = "读取书源列表时保留骨架和筛选结构。"
            ),
            error = SourceManagementFeedbackUiModel(
                title = "加载失败态",
                message = "加载失败保留上下文并提供重试。",
                primaryAction = "重试"
            ),
            offline = SourceManagementFeedbackUiModel(
                title = "离线检测态",
                message = "离线时阻断检测，不影响查看已缓存书源。",
                primaryAction = "查看缓存"
            ),
            permission = SourceManagementFeedbackUiModel(
                title = "网络权限态",
                message = "检测书源需要网络权限。",
                primaryAction = "去授权"
            )
        )

    fun edit(): SourceManagementDesignUiState =
        fromFixture().copy(displayState = SourceManagementDesignDisplayState.Edit)

    fun log(): SourceManagementDesignUiState =
        fromFixture().copy(displayState = SourceManagementDesignDisplayState.Log)

    fun confirm(): SourceManagementDesignUiState =
        fromFixture().copy(displayState = SourceManagementDesignDisplayState.Confirm)

    fun loading(): SourceManagementDesignUiState =
        fromFixture().copy(displayState = SourceManagementDesignDisplayState.Loading)

    fun empty(): SourceManagementDesignUiState =
        fromFixture().copy(
            displayState = SourceManagementDesignDisplayState.Empty,
            sources = emptyList()
        )

    fun error(): SourceManagementDesignUiState =
        fromFixture().copy(displayState = SourceManagementDesignDisplayState.Error)

    fun offline(): SourceManagementDesignUiState =
        fromFixture().copy(displayState = SourceManagementDesignDisplayState.Offline)

    fun permission(): SourceManagementDesignUiState =
        fromFixture().copy(displayState = SourceManagementDesignDisplayState.Permission)
}
