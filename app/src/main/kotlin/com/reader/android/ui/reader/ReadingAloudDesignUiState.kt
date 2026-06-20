package com.reader.android.ui.reader

enum class ReadingAloudDisplayState {
    Default,
    Running,
    Paused,
    Error
}

data class ReadingAloudPanelUiModel(
    val title: String,
    val subtitle: String,
    val settingsLabel: String,
    val previousLabel: String,
    val nextLabel: String,
    val startLabel: String,
    val pauseLabel: String,
    val continueLabel: String,
    val stopLabel: String
)

data class ReadingAloudSentenceUiModel(
    val text: String,
    val label: String,
    val progressLabel: String
)

data class ReadingAloudControlUiModel(
    val label: String,
    val value: String
)

data class ReadingAloudControlsUiModel(
    val speed: ReadingAloudControlUiModel,
    val voice: ReadingAloudControlUiModel,
    val range: ReadingAloudControlUiModel,
    val timer: ReadingAloudControlUiModel
)

data class ReadingVoiceOptionUiModel(
    val label: String,
    val meta: String,
    val active: Boolean
)

data class ReadingSpeedOptionUiModel(
    val label: String,
    val value: Float,
    val active: Boolean
)

data class ReadingRunningCapsuleUiModel(
    val title: String,
    val pausedTitle: String,
    val sentence: String,
    val actionLabel: String,
    val continueLabel: String,
    val settingsLabel: String
)

data class ReadingAloudUiState(
    val status: ReadingTocBookmarkStatusUiModel,
    val topControl: ReadingTopControlUiModel,
    val reading: ReaderShellTextUiModel,
    val panel: ReadingAloudPanelUiModel,
    val currentSentence: ReadingAloudSentenceUiModel,
    val controls: ReadingAloudControlsUiModel,
    val voices: List<ReadingVoiceOptionUiModel>,
    val speedOptions: List<ReadingSpeedOptionUiModel>,
    val runningCapsule: ReadingRunningCapsuleUiModel,
    val error: ReaderShellFeedbackUiModel,
    val moduleNav: List<ReadingModuleNavItemUiModel>,
    val brightness: ReadingBrightnessUiModel,
    val bottomReadout: ReadingBottomReadoutUiModel,
    val displayState: ReadingAloudDisplayState = ReadingAloudDisplayState.Default
) {
    val isRunning: Boolean get() = displayState == ReadingAloudDisplayState.Running
    val isPaused: Boolean get() = displayState == ReadingAloudDisplayState.Paused
    val activeVoice: ReadingVoiceOptionUiModel get() =
        voices.firstOrNull { it.active } ?: voices.first()
}

object ReadingAloudMapper {
    fun fromFixture(): ReadingAloudUiState =
        ReadingAloudUiState(
            status = ReadingTocBookmarkStatusUiModel(
                left = "长夜余火 · 第 32 章",
                time = "10:30"
            ),
            topControl = ReadingTopControlUiModel(
                bookTitle = "长夜余火",
                sourceLine = "起点中文网 · 第 32 章",
                sourceActionLabel = "换源"
            ),
            reading = ReaderShellTextUiModel(
                title = "",
                paragraphs = listOf(
                    "雨，下了一整夜。",
                    "城市像是被泼了一盆凉水，街灯昏黄，水面反着光，连呼吸都带着潮气。",
                    "他站在巷口，手里攥着那封信。纸已经被雨水润湿，边角微微卷起，字迹却依旧清晰。",
                    "“等你回来。”",
                    "没有署名，只有短短四个字。",
                    "他笑了笑，笑得很轻，像是自言自语。然后转身，沿着雨声走进夜里。"
                )
            ),
            panel = ReadingAloudPanelUiModel(
                title = "朗读",
                subtitle = "第 32 章 雨夜",
                settingsLabel = "朗读设置 >",
                previousLabel = "上一句",
                nextLabel = "下一句",
                startLabel = "开始",
                pauseLabel = "暂停",
                continueLabel = "继续",
                stopLabel = "停止朗读"
            ),
            currentSentence = ReadingAloudSentenceUiModel(
                text = "雨，下了一整夜。",
                label = "当前句",
                progressLabel = "第 1 / 48 句"
            ),
            controls = ReadingAloudControlsUiModel(
                speed = ReadingAloudControlUiModel("语速", "1.0x"),
                voice = ReadingAloudControlUiModel("声音", "清晰女声"),
                range = ReadingAloudControlUiModel("范围", "当前章节"),
                timer = ReadingAloudControlUiModel("定时关闭", "15 分钟")
            ),
            voices = listOf(
                ReadingVoiceOptionUiModel("清晰女声", "系统 TTS · 当前", active = true),
                ReadingVoiceOptionUiModel("沉稳男声", "系统 TTS", active = false),
                ReadingVoiceOptionUiModel("自然童声", "系统 TTS", active = false)
            ),
            speedOptions = listOf(
                ReadingSpeedOptionUiModel("0.8x", 0.8f, active = false),
                ReadingSpeedOptionUiModel("1.0x", 1f, active = true),
                ReadingSpeedOptionUiModel("1.2x", 1.2f, active = false),
                ReadingSpeedOptionUiModel("1.5x", 1.5f, active = false)
            ),
            runningCapsule = ReadingRunningCapsuleUiModel(
                title = "正在朗读",
                pausedTitle = "朗读已暂停",
                sentence = "雨，下了一整夜。",
                actionLabel = "暂停",
                continueLabel = "继续",
                settingsLabel = "设置"
            ),
            error = ReaderShellFeedbackUiModel(
                title = "加载失败，请重试",
                message = "系统 TTS 暂不可用，已保留当前阅读位置。",
                primaryAction = "重试"
            ),
            moduleNav = listOf(
                ReadingModuleNavItemUiModel("目录", "directory", active = false),
                ReadingModuleNavItemUiModel("朗读", "tts", active = true),
                ReadingModuleNavItemUiModel("界面", "appearance", active = false),
                ReadingModuleNavItemUiModel("设置", "settings", active = false)
            ),
            brightness = ReadingBrightnessUiModel(
                title = "亮度",
                value = 56,
                autoLabel = "A",
                modeLabel = "系统"
            ),
            bottomReadout = ReadingBottomReadoutUiModel(
                progress = "38%",
                chapter = "第 32/128 章"
            )
        )

    fun running(): ReadingAloudUiState =
        fromFixture().copy(displayState = ReadingAloudDisplayState.Running)

    fun paused(): ReadingAloudUiState =
        fromFixture().copy(displayState = ReadingAloudDisplayState.Paused)

    fun error(): ReadingAloudUiState =
        fromFixture().copy(displayState = ReadingAloudDisplayState.Error)
}
