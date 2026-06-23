package com.reader.android.ui.reader

enum class ReadingSettingsDisplayState {
    Default,
    Subpage,
    Loading,
    Error
}

enum class ReadingSettingsControlType {
    Segment,
    Switch,
    Stepper,
    Preset
}

data class ReadingSettingsTopBarUiModel(
    val title: String,
    val presetLabel: String
)

data class ReadingSettingsPresetUiModel(
    val title: String,
    val value: String,
    val icon: String
)

data class ReadingSettingsGroupUiModel(
    val title: String,
    val meta: String,
    val icon: String,
    val target: String
)

data class ReadingSettingsSwitchUiModel(
    val title: String,
    val meta: String,
    val enabled: Boolean
)

data class ReadingSettingsRestoreUiModel(
    val title: String,
    val confirmTitle: String,
    val copy: String,
    val cancelLabel: String,
    val confirmLabel: String
)

data class ReadingSettingsSubRowUiModel(
    val title: String,
    val meta: String,
    val controlType: ReadingSettingsControlType,
    val options: List<String> = emptyList(),
    val activeOption: String = "",
    val enabled: Boolean = false,
    val value: Int = 0,
    val active: Boolean = false
)

data class ReadingSettingsSectionUiModel(
    val title: String,
    val rows: List<ReadingSettingsSubRowUiModel>
)

data class ReadingSettingsSubpageUiModel(
    val title: String,
    val subtitle: String,
    val sections: List<ReadingSettingsSectionUiModel>
)

data class ReadingSettingsUiState(
    val topBar: ReadingSettingsTopBarUiModel,
    val quickPresets: List<ReadingSettingsPresetUiModel>,
    val groups: List<ReadingSettingsGroupUiModel>,
    val advancedTitle: String,
    val advanced: List<ReadingSettingsSwitchUiModel>,
    val restore: ReadingSettingsRestoreUiModel,
    val subpage: ReadingSettingsSubpageUiModel,
    val loading: ReaderShellFeedbackUiModel,
    val error: ReaderShellFeedbackUiModel,
    val moduleNav: List<ReadingModuleNavItemUiModel>,
    val displayState: ReadingSettingsDisplayState = ReadingSettingsDisplayState.Default
) {
    val currentTitle: String get() =
        if (displayState == ReadingSettingsDisplayState.Subpage) subpage.title else topBar.title
}

object ReadingSettingsMapper {
    fun fromFixture(): ReadingSettingsUiState =
        ReadingSettingsUiState(
            topBar = ReadingSettingsTopBarUiModel(
                title = "阅读设置",
                presetLabel = "预设"
            ),
            quickPresets = listOf(
                ReadingSettingsPresetUiModel("跟随系统方向", "当前方向", "phone"),
                ReadingSettingsPresetUiModel("覆盖翻页", "翻页方式", "book"),
                ReadingSettingsPresetUiModel("屏幕常亮关闭", "显示设置", "sun")
            ),
            groups = listOf(
                ReadingSettingsGroupUiModel("屏幕与显示", "方向 · 亮度 · 常亮 · 间距", "monitor", "display"),
                ReadingSettingsGroupUiModel("翻页与手势", "翻页方式 · 触屏手势 · 区域自定义", "gesture", "gesture"),
                ReadingSettingsGroupUiModel("阅读辅助", "自动翻页 · 朗读设置 · 文字优化", "assist", "assist"),
                ReadingSettingsGroupUiModel("进度与信息", "章节显示 · 进度显示 · 统计", "progress", "progress")
            ),
            advancedTitle = "高级设置",
            advanced = listOf(
                ReadingSettingsSwitchUiModel("自动加载后续章节", "章节末尾自动加载后续内容", enabled = true),
                ReadingSettingsSwitchUiModel("章节失败自动重试", "加载失败时自动重试", enabled = true),
                ReadingSettingsSwitchUiModel("缓存优先", "优先使用本地缓存内容", enabled = true),
                ReadingSettingsSwitchUiModel("误触保护", "阅读时屏蔽边缘区域的误触", enabled = false)
            ),
            restore = ReadingSettingsRestoreUiModel(
                title = "恢复默认阅读设置",
                confirmTitle = "恢复默认",
                copy = "将恢复显示、行为、辅助和进度信息设置，不会删除书籍或阅读进度。",
                cancelLabel = "取消",
                confirmLabel = "恢复默认"
            ),
            subpage = ReadingSettingsSubpageUiModel(
                title = "屏幕与显示",
                subtitle = "方向 · 亮度 · 常亮 · 间距",
                sections = listOf(
                    ReadingSettingsSectionUiModel(
                        title = "显示",
                        rows = listOf(
                            ReadingSettingsSubRowUiModel(
                                title = "屏幕方向",
                                meta = "跟随系统方向",
                                controlType = ReadingSettingsControlType.Segment,
                                options = listOf("跟随系统", "竖屏", "横屏"),
                                activeOption = "跟随系统"
                            ),
                            ReadingSettingsSubRowUiModel(
                                title = "屏幕常亮",
                                meta = "阅读时保持屏幕常亮",
                                controlType = ReadingSettingsControlType.Switch,
                                enabled = false
                            ),
                            ReadingSettingsSubRowUiModel(
                                title = "边距",
                                meta = "左右留白",
                                controlType = ReadingSettingsControlType.Stepper,
                                value = 18
                            )
                        )
                    ),
                    ReadingSettingsSectionUiModel(
                        title = "预设管理",
                        rows = listOf(
                            ReadingSettingsSubRowUiModel(
                                title = "日间阅读",
                                meta = "纸张背景 · 标准行距",
                                controlType = ReadingSettingsControlType.Preset,
                                active = true
                            ),
                            ReadingSettingsSubRowUiModel(
                                title = "夜间阅读",
                                meta = "深色背景 · 柔和亮度",
                                controlType = ReadingSettingsControlType.Preset,
                                active = false
                            )
                        )
                    )
                )
            ),
            loading = ReaderShellFeedbackUiModel(
                title = "正在加载",
                message = "正在读取阅读设置，请稍候。"
            ),
            error = ReaderShellFeedbackUiModel(
                title = "操作失败，请重试",
                message = "保存失败，已保留当前阅读设置。",
                primaryAction = "重试"
            ),
            moduleNav = listOf(
                ReadingModuleNavItemUiModel("目录", "directory", active = false),
                ReadingModuleNavItemUiModel("朗读", "tts", active = false),
                ReadingModuleNavItemUiModel("界面", "appearance", active = false),
                ReadingModuleNavItemUiModel("设置", "settings", active = true)
            )
        )

    fun subpage(): ReadingSettingsUiState =
        fromFixture().copy(displayState = ReadingSettingsDisplayState.Subpage)

    fun loading(): ReadingSettingsUiState =
        fromFixture().copy(displayState = ReadingSettingsDisplayState.Loading)

    fun error(): ReadingSettingsUiState =
        fromFixture().copy(displayState = ReadingSettingsDisplayState.Error)
}
