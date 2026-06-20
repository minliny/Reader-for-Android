package com.reader.android.ui.reader

enum class AutoPageDisplayState {
    Default,
    Running,
    Paused,
    Error
}

data class AutoPageTopControlUiModel(
    val title: String,
    val sourceLine: String
)

data class AutoPageSpeedUiModel(
    val title: String,
    val valueLabel: String,
    val value: Float,
    val slowLabel: String,
    val fastLabel: String
)

data class AutoPageModeUiModel(
    val label: String,
    val active: Boolean
)

data class AutoPageOptionUiModel(
    val title: String,
    val meta: String,
    val icon: String,
    val enabled: Boolean
)

data class AutoPageActionsUiModel(
    val cancelLabel: String,
    val startLabel: String,
    val stopLabel: String
)

data class AutoPageRunningCapsuleUiModel(
    val title: String,
    val pausedTitle: String,
    val sentence: String,
    val actionLabel: String,
    val continueLabel: String,
    val stopLabel: String,
    val settingsLabel: String
)

data class AutoPageUiState(
    val status: ReadingTocBookmarkStatusUiModel,
    val topControl: AutoPageTopControlUiModel,
    val reading: ReaderShellTextUiModel,
    val speed: AutoPageSpeedUiModel,
    val modes: List<AutoPageModeUiModel>,
    val optionsTitle: String,
    val options: List<AutoPageOptionUiModel>,
    val actions: AutoPageActionsUiModel,
    val runningCapsule: AutoPageRunningCapsuleUiModel,
    val error: ReaderShellFeedbackUiModel,
    val displayState: AutoPageDisplayState = AutoPageDisplayState.Default
) {
    val isRunning: Boolean get() = displayState == AutoPageDisplayState.Running
    val isPaused: Boolean get() = displayState == AutoPageDisplayState.Paused
    val activeMode: AutoPageModeUiModel get() =
        modes.firstOrNull { it.active } ?: modes.first()
}

object AutoPageMapper {
    fun fromFixture(): AutoPageUiState =
        AutoPageUiState(
            status = ReadingTocBookmarkStatusUiModel(
                left = "长夜余火 · 第 32 章",
                time = "10:30"
            ),
            topControl = AutoPageTopControlUiModel(
                title = "自动翻页",
                sourceLine = "长夜余火 · 第 32 章"
            ),
            reading = ReaderShellTextUiModel(
                title = "",
                paragraphs = listOf(
                    "雨，下了一整夜。",
                    "城市像是被泼了一盆凉水，街灯昏黄，水面反着光，连呼吸都带着潮气。",
                    "他站在巷口，手里攥着那封信。纸已经被雨水润湿，边角微微卷起，字迹却依旧清晰。"
                )
            ),
            speed = AutoPageSpeedUiModel(
                title = "翻页速度",
                valueLabel = "每 8 秒一页",
                value = 0.42f,
                slowLabel = "慢",
                fastLabel = "快"
            ),
            modes = listOf(
                AutoPageModeUiModel("滚动", active = false),
                AutoPageModeUiModel("翻页", active = true),
                AutoPageModeUiModel("仿真翻页", active = false)
            ),
            optionsTitle = "更多选项",
            options = listOf(
                AutoPageOptionUiModel("屏幕常亮", "自动翻页时保持屏幕点亮", "sun", enabled = true),
                AutoPageOptionUiModel("到章末停止", "当前章节结束后自动停止", "bookmark", enabled = false),
                AutoPageOptionUiModel("音量键调速", "运行中用音量键微调速度", "volume", enabled = false)
            ),
            actions = AutoPageActionsUiModel(
                cancelLabel = "取消",
                startLabel = "开始自动翻页",
                stopLabel = "停止"
            ),
            runningCapsule = AutoPageRunningCapsuleUiModel(
                title = "正在自动翻页",
                pausedTitle = "自动翻页已暂停",
                sentence = "每 8 秒一页 · 翻页模式",
                actionLabel = "暂停",
                continueLabel = "继续",
                stopLabel = "停止",
                settingsLabel = "设置"
            ),
            error = ReaderShellFeedbackUiModel(
                title = "操作失败，请重试",
                message = "当前章节暂时无法继续翻页，已停止自动翻页并保留阅读位置。",
                primaryAction = "重试"
            )
        )

    fun running(): AutoPageUiState =
        fromFixture().copy(displayState = AutoPageDisplayState.Running)

    fun paused(): AutoPageUiState =
        fromFixture().copy(displayState = AutoPageDisplayState.Paused)

    fun error(): AutoPageUiState =
        fromFixture().copy(displayState = AutoPageDisplayState.Error)
}
