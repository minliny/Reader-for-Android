package com.reader.android.ui.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.reader.android.data.bridge.FakeCoreBridge
import com.reader.android.data.model.BookSource
import com.reader.android.data.model.ContentPage
import com.reader.android.data.network.ContentParser
import com.reader.android.data.network.HttpClient
import com.reader.android.ui.components.ReaderLoadingState
import com.reader.android.ui.reader.components.BrightnessDock
import com.reader.android.ui.reader.components.ReaderControlBase
import com.reader.android.ui.reader.components.ReaderNightState
import com.reader.android.ui.reader.components.ReaderSearchOverlay
import com.reader.android.ui.reader.components.ReaderAutoScrollOverlay
import com.reader.android.ui.reader.components.ReaderReplaceOverlay
import com.reader.android.ui.reader.components.ReaderDirectoryOverlay
import com.reader.android.ui.reader.components.ReaderTtsOverlay
import com.reader.android.ui.reader.components.ReaderAppearanceOverlay
import com.reader.android.ui.reader.components.ReaderSettingsOverlay
import com.reader.android.ui.reader.components.AutoScrollSpeed
import com.reader.android.ui.reader.components.AutoScrollMode
import com.reader.android.ui.reader.components.ReplaceRule
import com.reader.android.ui.reader.components.TocEntry
import com.reader.android.ui.reader.components.AppSettingItem
import com.reader.android.ui.reader.components.AppSwitchItem
import com.reader.android.ui.theme.ReaderTheme

class ReaderViewModel(private val useRealHttp: Boolean = false) {
    private val bridge = FakeCoreBridge()
    private val httpClient = HttpClient()
    private val parser = ContentParser()
    private val source = BookSource(sourceUrl = "", sourceName = "笔趣阁")

    var content by mutableStateOf<ContentPage?>(null)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var chapterTitle by mutableStateOf("")
        private set
    var error by mutableStateOf<String?>(null)
        private set

    suspend fun load(contentUrl: String, title: String = "") {
        isLoading = true
        chapterTitle = title
        error = null
        try {
            if (useRealHttp) {
                val response = httpClient.get(contentUrl)
                content = parser.parseContentResponse(response.body)
            } else {
                content = bridge.getContent(contentUrl, source)
            }
        } catch (e: Exception) {
            error = e.message ?: "加载失败"
        }
        isLoading = false
    }
}

@Composable
fun ReaderScreen(
    contentUrl: String? = null,
    chapterTitle: String = "",
    onBack: (() -> Unit)? = null,
    onNextChapter: ((String, String) -> Unit)? = null,
    runtimeState: ReaderRuntimeUiState? = null
) {
    val viewModel = remember { ReaderViewModel() }
    var isNight by remember { mutableStateOf(false) }

    LaunchedEffect(contentUrl) {
        if (contentUrl != null) {
            viewModel.load(contentUrl, chapterTitle)
        }
    }

    // State-driven path: when runtimeState is provided, render from it
    if (runtimeState != null) {
        StateDrivenReaderScreen(
            state = runtimeState,
            onBack = onBack,
            onNightModeToggle = { isNight = !isNight },
            onOverlayDismiss = { /* handled by state changes */ }
        )
        return
    }

    // Legacy path: inline ViewModel-driven rendering
    ReaderNightState(isNight = isNight) {
        when {
            viewModel.isLoading -> {
                ReaderLoadingState(modifier = Modifier.fillMaxSize(), message = "加载中")
            }
            viewModel.content != null -> {
                val text = viewModel.content!!.content
                ReaderControlBase(
                    chapterTitle = viewModel.chapterTitle.ifBlank { chapterTitle.ifBlank { "阅读" } },
                    bookTitle = viewModel.chapterTitle.ifBlank { chapterTitle },
                    sourceName = "本地书籍",
                    chapterProgress = 0.25f,
                    brightnessDock = BrightnessDock.Left,
                    onBackClick = { onBack?.invoke() },
                    onNightModeClick = { isNight = !isNight }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp, vertical = 128.dp)
                    ) {
                        Text(
                            text = text,
                            color = ReaderTheme.colors.bodyText,
                            style = ReaderTheme.typography.readerBody,
                            lineHeight = ReaderTheme.typography.readerBody.lineHeight
                        )
                    }
                }
            }
            contentUrl == null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(128.dp)
                ) {
                    Text(
                        "选择一个章节开始阅读",
                        color = ReaderTheme.colors.bodyText,
                        style = ReaderTheme.typography.stateMessage
                    )
                }
            }
        }
    }
}

@Composable
private fun StateDrivenReaderScreen(
    state: ReaderRuntimeUiState,
    onBack: (() -> Unit)?,
    onNightModeToggle: () -> Unit,
    onOverlayDismiss: () -> Unit
) {
    if (state.isLoading) {
        ReaderLoadingState(modifier = Modifier.fillMaxSize(), message = "加载中")
        return
    }

    val baseParams = ReaderRuntimeMapper.toControlBaseParams(state)

    ReaderNightState(isNight = state.isNightMode) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Reading content
            if (state.content != null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 128.dp)
                ) {
                    Text(
                        text = state.content.text,
                        color = ReaderTheme.colors.bodyText,
                        style = ReaderTheme.typography.readerBody,
                        lineHeight = ReaderTheme.typography.readerBody.lineHeight
                    )
                }
            }

            // Control base overlay
            ReaderControlBase(
                chapterTitle = baseParams.chapterTitle,
                bookTitle = baseParams.bookTitle,
                sourceName = baseParams.sourceName,
                chapterProgress = baseParams.chapterProgress,
                brightnessDock = baseParams.brightnessDock,
                onBackClick = { onBack?.invoke() },
                onNightModeClick = onNightModeToggle,
                onSearchClick = { /* state change handled externally */ },
                onAutoScrollClick = { },
                onReplaceClick = { },
                onDirectoryClick = { },
                onTtsClick = { },
                onAppearanceClick = { },
                onSettingsClick = { }
            ) { /* content rendered above */ }

            // Overlays based on control layer state
            when (val overlayState = state.controlLayerState) {
                is ReaderControlLayerState.QuickActionOverlay -> {
                    when (overlayState.type) {
                        ReaderOverlayType.SEARCH -> ReaderSearchOverlay(
                            query = state.searchState.query,
                            resultCount = state.searchState.resultCount,
                            results = state.searchState.results.map { com.reader.android.ui.reader.components.SearchMatch(it.title, it.snippet) },
                            currentIndex = state.searchState.currentIndex,
                            modifier = Modifier.fillMaxSize(),
                            onDismiss = onOverlayDismiss
                        )
                        ReaderOverlayType.AUTO_SCROLL -> ReaderAutoScrollOverlay(
                            isRunning = state.autoScrollState == ReaderAutoScrollState.RUNNING,
                            speed = AutoScrollSpeed.Medium,
                            mode = AutoScrollMode.Scroll,
                            modifier = Modifier.fillMaxSize(),
                            onDismiss = onOverlayDismiss
                        )
                        ReaderOverlayType.REPLACE -> ReaderReplaceOverlay(
                            bookName = state.book.bookTitle,
                            rules = state.replaceRules.map { ReplaceRule(it.name, it.description, it.enabled) },
                            modifier = Modifier.fillMaxSize(),
                            onDismiss = onOverlayDismiss
                        )
                        else -> {}
                    }
                }
                is ReaderControlLayerState.BottomFunctionOverlay -> {
                    when (overlayState.type) {
                        ReaderOverlayType.DIRECTORY -> ReaderDirectoryOverlay(
                            tocEntries = state.tocBookmarkState.entries.map {
                                TocEntry(it.title, it.level, it.isCurrent, it.hasBookmark)
                            },
                            volumeInfo = state.tocBookmarkState.volumeInfo,
                            currentChapter = state.chapter.chapterTitle,
                            modifier = Modifier.fillMaxSize(),
                            onDismiss = onOverlayDismiss
                        )
                        ReaderOverlayType.TTS -> ReaderTtsOverlay(
                            isPlaying = state.ttsState == ReaderTtsState.PLAYING,
                            currentTime = "00:00",
                            totalTime = "08:12",
                            progress = 0f,
                            speed = 1f,
                            volume = 0.7f,
                            currentChapterTitle = state.chapter.chapterTitle,
                            modifier = Modifier.fillMaxSize(),
                            onDismiss = onOverlayDismiss
                        )
                        ReaderOverlayType.APPEARANCE -> ReaderAppearanceOverlay(
                            fontName = "默认",
                            fontSize = "18",
                            letterSpacing = "标准",
                            scriptMode = "简体",
                            indent = "2 字符",
                            lineSpacing = "标准",
                            paragraphSpacing = "标准",
                            infoDisplay = "四角信息",
                            pageFlipAnimation = "覆盖",
                            themeName = "米色纸张",
                            modifier = Modifier.fillMaxSize(),
                            onDismiss = onOverlayDismiss
                        )
                        ReaderOverlayType.SETTINGS -> ReaderSettingsOverlay(
                            items = listOf(
                                AppSettingItem("屏幕方向", "跟随系统"),
                                AppSettingItem("屏幕超时", "5 分钟"),
                                AppSettingItem("单双页", "自动"),
                                AppSettingItem("进度条行为", "控制层显示")
                            ),
                            switches = listOf(
                                AppSwitchItem("隐藏状态栏", true),
                                AppSwitchItem("文字两端对齐", true),
                                AppSwitchItem("文字底部对齐", false),
                                AppSwitchItem("音量键翻页", true),
                                AppSwitchItem("单手翻页", false)
                            ),
                            modifier = Modifier.fillMaxSize(),
                            onDismiss = onOverlayDismiss
                        )
                        else -> {}
                    }
                }
                is ReaderControlLayerState.BaseControlVisible -> { /* base controls already shown */ }
            }
        }
    }
}
