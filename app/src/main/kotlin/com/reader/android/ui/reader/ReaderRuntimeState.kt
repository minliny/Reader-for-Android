package com.reader.android.ui.reader

import com.reader.android.ui.reader.components.BrightnessDock

// ── Book / Chapter / Content models ──

data class ReaderBookUiModel(
    val bookTitle: String,
    val sourceName: String,
    val tocUrl: String? = null
)

data class ReaderChapterUiModel(
    val chapterTitle: String,
    val chapterIndex: Int = 0,
    val totalChapters: Int = 1
)

data class ReaderContentUiModel(
    val text: String,
    val prevPageAvailable: Boolean = false,
    val nextPageAvailable: Boolean = false
)

data class ReaderPageProgressUiModel(
    val progress: Float,
    val currentPage: Int = 0,
    val totalPages: Int = 1
)

// ── Control layer states ──

enum class ReaderOverlayType {
    NONE,
    SEARCH,
    AUTO_SCROLL,
    REPLACE,
    DIRECTORY,
    TTS,
    APPEARANCE,
    SETTINGS
}

sealed interface ReaderControlLayerState {
    data object BaseControlVisible : ReaderControlLayerState
    data class QuickActionOverlay(val type: ReaderOverlayType) : ReaderControlLayerState
    data class BottomFunctionOverlay(val type: ReaderOverlayType) : ReaderControlLayerState
}

data class ReaderBrightnessDockState(
    val dock: BrightnessDock = BrightnessDock.Left,
    val autoBrightness: Boolean = true,
    val brightnessValue: Float = 0.5f
)

// ── TTS / AutoScroll states ──

enum class ReaderTtsState {
    IDLE, PLAYING, PAUSED
}

enum class ReaderAutoScrollState {
    IDLE, RUNNING, PAUSED
}

// ── Replace rules ──

data class ReaderReplaceRuleUiModel(
    val name: String,
    val description: String,
    val enabled: Boolean
)

// ── TOC / Bookmark state ──

data class ReaderTocEntryUiModel(
    val title: String,
    val level: Int = 1,
    val isCurrent: Boolean = false,
    val hasBookmark: Boolean = false,
    val progress: Float? = null
)

data class ReaderTocBookmarkState(
    val entries: List<ReaderTocEntryUiModel> = emptyList(),
    val volumeInfo: String = "",
    val activeTab: String = "目录"
)

// ── Search state ──

data class ReaderSearchUiModel(
    val query: String = "",
    val resultCount: Int = 0,
    val results: List<ReaderSearchResultUiModel> = emptyList(),
    val currentIndex: Int = 0
)

data class ReaderSearchResultUiModel(
    val title: String,
    val snippet: String
)

// ── Comprehensive runtime state ──

data class ReaderRuntimeUiState(
    val book: ReaderBookUiModel = ReaderBookUiModel("", ""),
    val chapter: ReaderChapterUiModel = ReaderChapterUiModel(""),
    val content: ReaderContentUiModel? = null,
    val pageProgress: ReaderPageProgressUiModel = ReaderPageProgressUiModel(0f),
    val controlLayerState: ReaderControlLayerState = ReaderControlLayerState.BaseControlVisible,
    val isNightMode: Boolean = false,
    val brightnessDockState: ReaderBrightnessDockState = ReaderBrightnessDockState(),
    val ttsState: ReaderTtsState = ReaderTtsState.IDLE,
    val autoScrollState: ReaderAutoScrollState = ReaderAutoScrollState.IDLE,
    val replaceRules: List<ReaderReplaceRuleUiModel> = emptyList(),
    val tocBookmarkState: ReaderTocBookmarkState = ReaderTocBookmarkState(),
    val searchState: ReaderSearchUiModel = ReaderSearchUiModel(),
    val isLoading: Boolean = false,
    val error: String? = null
)
