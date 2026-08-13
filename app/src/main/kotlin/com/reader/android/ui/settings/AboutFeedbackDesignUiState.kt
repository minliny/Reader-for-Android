package com.reader.android.ui.settings

enum class AboutFeedbackDisplayState {
    Default,
    Loading,
    Error,
    Confirm,
    Offline
}

data class AboutFeedbackTopBarUiModel(
    val title: String,
    val backLabel: String
)

data class AboutMetricUiModel(
    val icon: String,
    val label: String,
    val value: String
)

data class AboutFeedbackRowUiModel(
    val icon: String,
    val title: String,
    val meta: String,
    val value: String = ""
)

data class AboutFeedbackSectionUiModel(
    val title: String,
    val rows: List<AboutFeedbackRowUiModel>
)

data class AboutFeedbackConfirmUiModel(
    val title: String,
    val copy: String,
    val cancelLabel: String,
    val confirmLabel: String
)

data class AboutFeedbackToastUiModel(
    val success: String,
    val error: String,
    val offline: String
)

data class AboutFeedbackMessageUiModel(
    val title: String,
    val message: String,
    val primaryAction: String? = null
)

data class AboutFeedbackUiState(
    val topBar: AboutFeedbackTopBarUiModel,
    val metrics: List<AboutMetricUiModel>,
    val sections: List<AboutFeedbackSectionUiModel>,
    val confirm: AboutFeedbackConfirmUiModel,
    val toast: AboutFeedbackToastUiModel,
    val loading: AboutFeedbackMessageUiModel,
    val error: AboutFeedbackMessageUiModel,
    val offline: AboutFeedbackMessageUiModel,
    val footer: String,
    val displayState: AboutFeedbackDisplayState = AboutFeedbackDisplayState.Default
) {
    val versionMetric: AboutMetricUiModel get() = metrics.first { it.label == "当前版本" }
    val updateMetric: AboutMetricUiModel get() = metrics.first { it.label == "更新状态" }
}

object AboutFeedbackMapper {
    fun fromFixture(): AboutFeedbackUiState =
        AboutFeedbackUiState(
            topBar = AboutFeedbackTopBarUiModel(
                title = "关于与反馈",
                backLabel = "返回"
            ),
            metrics = listOf(
                AboutMetricUiModel(
                    icon = "book",
                    label = "当前版本",
                    value = "Reader 1.0.0"
                ),
                AboutMetricUiModel(
                    icon = "check",
                    label = "更新状态",
                    value = "已是最新版本"
                )
            ),
            sections = listOf(
                AboutFeedbackSectionUiModel(
                    title = "应用信息",
                    rows = listOf(
                        AboutFeedbackRowUiModel(
                            icon = "refresh",
                            title = "检查更新",
                            meta = "检查应用是否有新版本",
                            value = "已是最新版本"
                        ),
                        AboutFeedbackRowUiModel(
                            icon = "file",
                            title = "更新日志",
                            meta = "查看版本更新记录"
                        ),
                        AboutFeedbackRowUiModel(
                            icon = "link",
                            title = "开源许可",
                            meta = "查看开源软件许可信息"
                        )
                    )
                ),
                AboutFeedbackSectionUiModel(
                    title = "帮助与反馈",
                    rows = listOf(
                        AboutFeedbackRowUiModel(
                            icon = "info",
                            title = "使用帮助",
                            meta = "查看常见问题与使用指南"
                        ),
                        AboutFeedbackRowUiModel(
                            icon = "message",
                            title = "问题反馈",
                            meta = "提交问题或建议给开发团队"
                        ),
                        AboutFeedbackRowUiModel(
                            icon = "log",
                            title = "导出诊断日志",
                            meta = "导出应用运行日志以便排查问题"
                        )
                    )
                ),
                AboutFeedbackSectionUiModel(
                    title = "法律与隐私",
                    rows = listOf(
                        AboutFeedbackRowUiModel(
                            icon = "shield",
                            title = "隐私协议",
                            meta = "了解我们如何收集与使用数据"
                        ),
                        AboutFeedbackRowUiModel(
                            icon = "file",
                            title = "用户协议",
                            meta = "阅读完整的用户协议"
                        )
                    )
                )
            ),
            confirm = AboutFeedbackConfirmUiModel(
                title = "导出诊断日志？",
                copy = "导出的日志只包含运行信息，不包含书籍正文。",
                cancelLabel = "取消",
                confirmLabel = "确认导出"
            ),
            toast = AboutFeedbackToastUiModel(
                success = "已是最新版本",
                error = "加载失败，请重试",
                offline = "网络不可用，请稍后重试"
            ),
            loading = AboutFeedbackMessageUiModel(
                title = "检查更新中",
                message = "检查更新时保留页面结构。"
            ),
            error = AboutFeedbackMessageUiModel(
                title = "检查更新失败态",
                message = "失败时保留版本信息并提示重试。",
                primaryAction = "重试"
            ),
            offline = AboutFeedbackMessageUiModel(
                title = "离线反馈态",
                message = "网络不可用时阻断检查更新和反馈提交。",
                primaryAction = "重试"
            ),
            footer = "本应用不提供账户与登录功能"
        )

    fun loading(): AboutFeedbackUiState =
        fromFixture().copy(displayState = AboutFeedbackDisplayState.Loading)

    fun error(): AboutFeedbackUiState =
        fromFixture().copy(displayState = AboutFeedbackDisplayState.Error)

    fun confirm(): AboutFeedbackUiState =
        fromFixture().copy(displayState = AboutFeedbackDisplayState.Confirm)

    fun offline(): AboutFeedbackUiState =
        fromFixture().copy(displayState = AboutFeedbackDisplayState.Offline)
}
