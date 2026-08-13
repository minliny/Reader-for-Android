package com.reader.android.ui.toc

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.reader.android.AppProvider
import com.reader.android.data.bridge.CoreBridge
import com.reader.android.data.bridge.FakeCoreBridge
import com.reader.android.data.model.BookSource
import com.reader.android.data.model.TOCItem
import com.reader.android.data.network.HttpClient
import com.reader.android.data.network.TOCParser
import com.reader.android.ui.components.BookProgressIndicator
import com.reader.android.ui.components.ReaderAppTopBar
import com.reader.android.ui.components.ReaderEmptyState
import com.reader.android.ui.components.ReaderErrorState
import com.reader.android.ui.components.ReaderLoadingState
import com.reader.android.ui.components.ReaderPrimaryButton
import com.reader.android.ui.components.ReaderSecondaryButton
import com.reader.android.ui.theme.ReaderTheme

class TOCViewModel(private val useRealHttp: Boolean = false) {
    private val bridge: CoreBridge = AppProvider.coreBridge
    private val httpClient = HttpClient()
    private val parser = TOCParser()
    private val source = BookSource(sourceUrl = "", sourceName = "笔趣阁")

    var chapters by mutableStateOf<List<TOCItem>>(emptyList())
        private set
    var isLoading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    suspend fun load(tocUrl: String) {
        isLoading = true
        error = null
        try {
            if (useRealHttp) {
                val response = httpClient.get(tocUrl)
                chapters = parser.parseTOCResponse(response.body)
            } else {
                chapters = bridge.getTOC(tocUrl, source)
            }
        } catch (e: Exception) {
            error = e.message ?: "加载失败"
        }
        isLoading = false
    }
}

@Composable
fun TOCScreen(
    tocUrl: String,
    onBack: () -> Unit,
    onChapterClick: (String, String) -> Unit,
    directoryState: BookDirectoryUiState? = null,
    onRetry: () -> Unit = {},
    onBackToDetail: () -> Unit = onBack
) {
    val viewModel = remember { TOCViewModel() }

    if (directoryState == null) {
        LaunchedEffect(tocUrl) {
            viewModel.load(tocUrl)
        }
    }

    ReaderTheme {
        Column(modifier = Modifier.fillMaxSize()) {
            ReaderAppTopBar(
                title = directoryState?.topBarTitle ?: "目录",
                onNavigateBack = onBack
            )

            if (directoryState != null) {
                BookDirectoryStateContent(
                    state = directoryState,
                    onChapterClick = onChapterClick,
                    onRetry = onRetry,
                    onBackToDetail = onBackToDetail,
                    modifier = Modifier.weight(1f)
                )
            } else when {
                viewModel.error != null -> {
                    ReaderErrorState(
                        title = "加载失败",
                        message = viewModel.error,
                        modifier = Modifier.weight(1f),
                        onRetryClick = { /* retry deferred */ }
                    )
                }
                viewModel.isLoading -> {
                    ReaderLoadingState(
                        modifier = Modifier.weight(1f),
                        message = "加载中"
                    )
                }
                viewModel.chapters.isEmpty() -> {
                    ReaderEmptyState(
                        title = "暂无章节",
                        message = "目录内容为空",
                        modifier = Modifier.weight(1f)
                    )
                }
                else -> {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(viewModel.chapters) { item ->
                            ChapterItem(
                                item = item,
                                depth = 0,
                                onChapterClick = onChapterClick
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BookDirectoryStateContent(
    state: BookDirectoryUiState,
    onChapterClick: (String, String) -> Unit,
    onRetry: () -> Unit,
    onBackToDetail: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        BookDirectorySummaryBar(summary = state.summary)
        when (state.displayState) {
            BookDirectoryDisplayState.Default -> {
                BookDirectoryCurrentChapterRow(
                    currentChapter = state.currentChapter,
                    onClick = {
                        state.chapters.firstOrNull { it.title == state.currentChapter.title }
                            ?.let { onChapterClick(it.target, it.title) }
                    }
                )
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(state.chapters, key = { it.id }) { chapter ->
                        BookDirectoryChapterRow(
                            chapter = chapter,
                            onClick = { onChapterClick(chapter.target, chapter.title) }
                        )
                    }
                }
            }
            BookDirectoryDisplayState.Loading -> ReaderLoadingState(
                modifier = Modifier.weight(1f),
                message = state.feedback?.title ?: "正在加载目录"
            )
            BookDirectoryDisplayState.Empty -> BookDirectoryFeedback(
                feedback = state.feedback ?: BookDirectoryFixture.emptyFeedback,
                onRetry = onRetry,
                onBackToDetail = onBackToDetail,
                modifier = Modifier.weight(1f)
            )
            BookDirectoryDisplayState.Error -> BookDirectoryFeedback(
                feedback = state.feedback ?: BookDirectoryFixture.errorFeedback,
                onRetry = onRetry,
                onBackToDetail = onBackToDetail,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun BookDirectorySummaryBar(summary: BookDirectorySummaryUiModel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ReaderTheme.spacing.screenPadding, vertical = ReaderTheme.spacing.xs)
            .clip(ReaderTheme.shapes.small)
            .background(ReaderTheme.colors.metaBg)
            .border(1.dp, ReaderTheme.colors.controlBorder, ReaderTheme.shapes.small)
            .semantics { contentDescription = "目录摘要，${summary.title}" }
            .padding(ReaderTheme.spacing.sm)
    ) {
        Text(
            text = summary.title,
            color = ReaderTheme.colors.controlInk,
            style = ReaderTheme.typography.sectionTitle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(ReaderTheme.spacing.xs))
        Row(
            horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(summary.sourceLabel, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.bookMeta)
            Text(summary.chapterCount, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.bookMeta)
        }
    }
}

@Composable
private fun BookDirectoryCurrentChapterRow(
    currentChapter: BookDirectoryCurrentChapterUiModel,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ReaderTheme.spacing.screenPadding, vertical = 4.dp)
            .clip(ReaderTheme.shapes.small)
            .background(ReaderTheme.colors.primary.copy(alpha = 0.10f))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "当前阅读章节，${currentChapter.title}" }
            .padding(ReaderTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                currentChapter.title,
                color = ReaderTheme.colors.primary,
                style = ReaderTheme.typography.bookTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(currentChapter.status, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.bookMeta)
        }
        BookProgressIndicator(
            progress = currentChapter.progress / 100f,
            modifier = Modifier.width(72.dp),
            contentDescription = "当前章节阅读进度，${currentChapter.progress}%"
        )
    }
}

@Composable
private fun BookDirectoryChapterRow(
    chapter: BookDirectoryChapterUiModel,
    onClick: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { contentDescription = "章节，${chapter.title}，${chapter.status.label}" }
                .padding(
                    horizontal = ReaderTheme.spacing.screenPadding,
                    vertical = ReaderTheme.spacing.sm
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)
        ) {
            Text(
                chapter.title,
                modifier = Modifier.weight(1f),
                color = ReaderTheme.colors.controlInk,
                style = ReaderTheme.typography.bookTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (chapter.isNew) {
                Text(
                    "新",
                    modifier = Modifier
                        .clip(ReaderTheme.shapes.chip)
                        .background(ReaderTheme.colors.primary)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    color = ReaderTheme.colors.paperBg,
                    style = ReaderTheme.typography.readerControlLabel
                )
            }
            Text(chapter.status.label, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.bookMeta)
        }
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = ReaderTheme.spacing.screenPadding),
            color = ReaderTheme.colors.controlBorder
        )
    }
}

@Composable
private fun BookDirectoryFeedback(
    feedback: BookDirectoryFeedbackUiModel,
    onRetry: () -> Unit,
    onBackToDetail: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(ReaderTheme.spacing.lg)
            .semantics { contentDescription = feedback.title },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(feedback.title, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.stateTitle)
        Spacer(modifier = Modifier.height(ReaderTheme.spacing.xs))
        Text(feedback.message, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.stateMessage)
        Spacer(modifier = Modifier.height(ReaderTheme.spacing.md))
        Row(horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)) {
            ReaderPrimaryButton(feedback.primaryAction, onRetry)
            ReaderSecondaryButton(feedback.secondaryAction, onBackToDetail)
        }
    }
}

@Composable
private fun ChapterItem(
    item: TOCItem,
    depth: Int,
    onChapterClick: (String, String) -> Unit
) {
    if (item.url.isNotBlank()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onChapterClick(item.url, item.title) }
                .padding(
                    start = ReaderTheme.spacing.screenPadding + (depth * 20).dp,
                    end = ReaderTheme.spacing.screenPadding,
                    top = ReaderTheme.spacing.sm,
                    bottom = ReaderTheme.spacing.sm
                )
        ) {
            Text(
                text = item.title,
                style = if (depth == 0) ReaderTheme.typography.bookTitle
                        else ReaderTheme.typography.bookMeta,
                color = ReaderTheme.colors.controlInk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    } else {
        // Volume header (no url)
        Text(
            text = item.title,
            style = ReaderTheme.typography.sectionTitle,
            color = ReaderTheme.colors.primary,
            modifier = Modifier.padding(
                start = ReaderTheme.spacing.screenPadding + (depth * 20).dp,
                top = ReaderTheme.spacing.md,
                bottom = ReaderTheme.spacing.xs
            )
        )
    }

    item.children.forEach { child ->
        ChapterItem(
            item = child,
            depth = depth + 1,
            onChapterClick = onChapterClick
        )
    }

    if (depth == 0) {
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = ReaderTheme.spacing.screenPadding),
            color = ReaderTheme.colors.controlBorder
        )
    }
}
