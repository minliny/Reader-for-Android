package com.reader.android.ui.settings

enum class GeneralSettingsDisplayState {
    Default,
    OptionSheet,
    Loading,
    Error,
    Permission
}

enum class GeneralSettingsRowType {
    Segment,
    Select,
    Switch
}

data class GeneralSettingsTopBarUiModel(
    val title: String,
    val backLabel: String
)

data class GeneralSettingsRowUiModel(
    val type: GeneralSettingsRowType,
    val icon: String,
    val title: String,
    val value: String = "",
    val options: List<String> = emptyList(),
    val meta: String = "",
    val enabled: Boolean = false,
    val permission: String? = null
)

data class GeneralSettingsGroupUiModel(
    val title: String,
    val rows: List<GeneralSettingsRowUiModel>
)

data class GeneralSettingsRestoreUiModel(
    val title: String,
    val confirmTitle: String,
    val copy: String,
    val cancelLabel: String,
    val confirmLabel: String,
    val intentLabel: String = "恢复默认"
)

data class GeneralSettingsToastUiModel(
    val success: String,
    val error: String,
    val permission: String
)

data class GeneralSettingsFeedbackUiModel(
    val title: String,
    val message: String,
    val primaryAction: String? = null
)

data class GeneralSettingsUiState(
    val topBar: GeneralSettingsTopBarUiModel,
    val groups: List<GeneralSettingsGroupUiModel>,
    val restore: GeneralSettingsRestoreUiModel,
    val toast: GeneralSettingsToastUiModel,
    val loading: GeneralSettingsFeedbackUiModel,
    val error: GeneralSettingsFeedbackUiModel,
    val permission: GeneralSettingsFeedbackUiModel,
    val displayState: GeneralSettingsDisplayState = GeneralSettingsDisplayState.Default
) {
    val languageRow: GeneralSettingsRowUiModel get() =
        groups.flatMap { it.rows }.first { it.title == "语言" }
}

object GeneralSettingsMapper {
    fun fromFixture(): GeneralSettingsUiState =
        GeneralSettingsUiState(
            topBar = GeneralSettingsTopBarUiModel(
                title = "通用设置",
                backLabel = "返回"
            ),
            groups = listOf(
                GeneralSettingsGroupUiModel(
                    title = "基础偏好",
                    rows = listOf(
                        GeneralSettingsRowUiModel(
                            type = GeneralSettingsRowType.Segment,
                            icon = "palette",
                            title = "App主题",
                            value = "跟随系统",
                            meta = "主题跟随系统",
                            options = listOf("跟随系统", "浅色", "深色")
                        ),
                        GeneralSettingsRowUiModel(
                            type = GeneralSettingsRowType.Select,
                            icon = "globe",
                            title = "语言",
                            value = "简体中文",
                            options = listOf("简体中文", "繁體中文", "English")
                        ),
                        GeneralSettingsRowUiModel(
                            type = GeneralSettingsRowType.Select,
                            icon = "home",
                            title = "启动时打开",
                            value = "书架",
                            options = listOf("书架", "发现", "RSS", "设置")
                        )
                    )
                ),
                GeneralSettingsGroupUiModel(
                    title = "行为与反馈",
                    rows = listOf(
                        GeneralSettingsRowUiModel(
                            type = GeneralSettingsRowType.Switch,
                            icon = "refresh",
                            title = "自动检查更新",
                            meta = "有新版本时自动检查并提示",
                            enabled = true
                        ),
                        GeneralSettingsRowUiModel(
                            type = GeneralSettingsRowType.Switch,
                            icon = "top",
                            title = "点击当前底栏回顶部",
                            meta = "再次点击当前底栏按钮时回到页面顶部",
                            enabled = true
                        ),
                        GeneralSettingsRowUiModel(
                            type = GeneralSettingsRowType.Switch,
                            icon = "motion",
                            title = "减少动态效果",
                            meta = "降低动画效果以提升流畅度",
                            enabled = true
                        ),
                        GeneralSettingsRowUiModel(
                            type = GeneralSettingsRowType.Switch,
                            icon = "bug",
                            title = "崩溃日志",
                            meta = "仅保存本地诊断日志，便于排查问题",
                            enabled = true,
                            permission = "已开启"
                        ),
                        GeneralSettingsRowUiModel(
                            type = GeneralSettingsRowType.Select,
                            icon = "play",
                            title = "动画效果",
                            meta = "设置界面内的动画播放效果强度",
                            value = "标准",
                            options = listOf("减少", "标准", "增强")
                        )
                    )
                )
            ),
            restore = GeneralSettingsRestoreUiModel(
                title = "恢复通用设置",
                confirmTitle = "恢复通用设置？",
                copy = "恢复后将重置 App 主题、语言、启动页面和行为偏好。",
                cancelLabel = "取消",
                confirmLabel = "确认恢复"
            ),
            toast = GeneralSettingsToastUiModel(
                success = "保存成功",
                error = "操作失败，请重试",
                permission = "需要系统权限"
            ),
            loading = GeneralSettingsFeedbackUiModel(
                title = "正在加载",
                message = "正在读取通用设置，请稍候。"
            ),
            error = GeneralSettingsFeedbackUiModel(
                title = "加载失败，请重试",
                message = "设置读取失败，已保留本地已知配置。",
                primaryAction = "重试"
            ),
            permission = GeneralSettingsFeedbackUiModel(
                title = "需要系统权限",
                message = "崩溃日志需要系统日志权限，授权后才能继续保存。",
                primaryAction = "去设置"
            )
        )

    fun optionSheet(): GeneralSettingsUiState =
        fromFixture().copy(displayState = GeneralSettingsDisplayState.OptionSheet)

    fun loading(): GeneralSettingsUiState =
        fromFixture().copy(displayState = GeneralSettingsDisplayState.Loading)

    fun error(): GeneralSettingsUiState =
        fromFixture().copy(displayState = GeneralSettingsDisplayState.Error)

    fun permission(): GeneralSettingsUiState =
        fromFixture().copy(displayState = GeneralSettingsDisplayState.Permission)
}
