package com.reader.android.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.reader.android.ui.components.ReaderChip
import com.reader.android.ui.components.ReaderIconButton
import com.reader.android.ui.components.ReaderIconToken
import com.reader.android.ui.components.ReaderPrimaryButton
import com.reader.android.ui.components.ReaderSecondaryButton
import com.reader.android.ui.components.asImageVector
import com.reader.android.ui.theme.ReaderTheme

@Composable
fun ContentSearchScreen(
    state: ContentSearchUiState = ContentSearchMapper.fromFixture(),
    onClose: () -> Unit = {},
    onQueryChange: (String) -> Unit = {},
    onClear: () -> Unit = {},
    onFilterChange: (ContentSearchFilterUiModel) -> Unit = {},
    onPreviousResult: () -> Unit = {},
    onNextResult: () -> Unit = {},
    onOpenResult: (ContentSearchResultUiModel) -> Unit = {},
    onRetry: () -> Unit = {}
) {
    ReaderTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ReaderTheme.colors.paperBg)
                .semantics { contentDescription = "ReaderShell 内容搜索" }
        ) {
            ContentSearchReadingSurface(state.reading, state.search.query)
            ContentSearchOverlay(
                state = state,
                onClose = onClose,
                onQueryChange = onQueryChange,
                onClear = onClear,
                modifier = Modifier.semantics { contentDescription = "readerOverlayHost" }
            )
            ContentSearchBottomSheet(
                state = state,
                onFilterChange = onFilterChange,
                onPreviousResult = onPreviousResult,
                onNextResult = onNextResult,
                onOpenResult = onOpenResult,
                onClear = onClear,
                onRetry = onRetry,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .semantics { contentDescription = "bottomSheetHost" }
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .semantics { contentDescription = "readerModuleNav" }
            )
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .semantics { contentDescription = "readerStateHost" }
            )
        }
    }
}

@Composable
private fun ContentSearchReadingSurface(
    reading: ReaderShellTextUiModel,
    query: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ReaderTheme.colors.paperBg)
            .padding(start = 28.dp, end = 28.dp, top = 112.dp, bottom = 430.dp)
            .semantics { contentDescription = "readingSurface" }
    ) {
        reading.paragraphs.forEach { paragraph ->
            HighlightedText(
                text = paragraph,
                query = query,
                color = ReaderTheme.colors.bodyText,
                style = ReaderTheme.typography.readerBody
            )
            Spacer(modifier = Modifier.height(ReaderTheme.spacing.md))
        }
    }
}

@Composable
private fun ContentSearchOverlay(
    state: ContentSearchUiState,
    onClose: () -> Unit,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ReaderTheme.colors.softTopBg)
                .padding(horizontal = ReaderTheme.spacing.screenPadding, vertical = ReaderTheme.spacing.xs),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(state.status.left, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.readerSmallMeta)
            Text(state.status.time, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.readerSmallMeta)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ReaderTheme.colors.floatingControlBg)
                .padding(horizontal = ReaderTheme.spacing.sm, vertical = ReaderTheme.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.xs)
        ) {
            ReaderIconButton(
                icon = ReaderIconToken.Close.asImageVector(),
                contentDescription = "关闭搜索",
                onClick = onClose
            )
            ContentSearchInput(
                search = state.search,
                onQueryChange = onQueryChange,
                onClear = onClear,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ContentSearchInput(
    search: ContentSearchInputUiModel,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .heightIn(min = 52.dp)
            .clip(ReaderTheme.shapes.chip)
            .background(ReaderTheme.colors.metaBg)
            .border(1.dp, ReaderTheme.colors.primary, ReaderTheme.shapes.chip)
            .clickable(role = Role.Button) { onQueryChange(search.query) }
            .semantics { contentDescription = "${search.label}，${search.query.ifBlank { search.placeholder }}" }
            .padding(horizontal = ReaderTheme.spacing.sm, vertical = ReaderTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.xs)
    ) {
        Icon(
            imageVector = ReaderIconToken.Search.asImageVector(),
            contentDescription = null,
            tint = ReaderTheme.colors.primary,
            modifier = Modifier.size(20.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = search.query.ifBlank { search.placeholder },
                color = ReaderTheme.colors.controlInk,
                style = ReaderTheme.typography.bookTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(search.label, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.readerSmallMeta)
        }
        ReaderSecondaryButton(
            text = search.clearLabel,
            onClick = onClear,
            contentDescription = search.clearLabel
        )
    }
}

@Composable
private fun ContentSearchBottomSheet(
    state: ContentSearchUiState,
    onFilterChange: (ContentSearchFilterUiModel) -> Unit,
    onPreviousResult: () -> Unit,
    onNextResult: () -> Unit,
    onOpenResult: (ContentSearchResultUiModel) -> Unit,
    onClear: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 360.dp, max = 520.dp)
            .clip(ReaderTheme.shapes.bottomSheet)
            .background(ReaderTheme.colors.paperBg)
            .border(1.dp, ReaderTheme.colors.controlBorder, ReaderTheme.shapes.bottomSheet)
            .padding(ReaderTheme.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(width = 38.dp, height = 4.dp)
                .clip(ReaderTheme.shapes.chip)
                .background(ReaderTheme.colors.mutedTrack)
        )
        ContentSearchPanelHeader(state, onPreviousResult, onNextResult)
        ContentSearchFilters(state.filters, onFilterChange)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            when (state.displayState) {
                ContentSearchDisplayState.Loading -> ContentSearchLoadingPanel(state.loading)
                ContentSearchDisplayState.Empty -> ContentSearchFeedbackPanel(state.empty, onClear)
                ContentSearchDisplayState.Error -> ContentSearchFeedbackPanel(state.error, onRetry)
                ContentSearchDisplayState.Offline -> Column(verticalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)) {
                    ContentSearchOfflineBanner(state.offline)
                    ContentSearchResultList(state.results, state.search.query, onOpenResult)
                }
                ContentSearchDisplayState.Default -> ContentSearchResultList(state.results, state.search.query, onOpenResult)
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ReaderTheme.shapes.small)
                .background(ReaderTheme.colors.floatingControlBgAlt)
                .padding(ReaderTheme.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.xs)
        ) {
            Icon(
                imageVector = ReaderIconToken.CurrentLocation.asImageVector(),
                contentDescription = null,
                tint = ReaderTheme.colors.primary,
                modifier = Modifier.size(18.dp)
            )
            Text(state.panel.tip, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.bookMeta)
        }
    }
}

@Composable
private fun ContentSearchPanelHeader(
    state: ContentSearchUiState,
    onPreviousResult: () -> Unit,
    onNextResult: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(state.panel.title, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.pageTitle)
            Text(
                "${state.panel.bookTitle} · ${state.search.label}",
                color = ReaderTheme.colors.bodyText,
                style = ReaderTheme.typography.bookMeta
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(state.panel.resultCount, color = ReaderTheme.colors.primary, style = ReaderTheme.typography.bookTitle)
            Row(horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.xs)) {
                ReaderSecondaryButton(text = "上一条", onClick = onPreviousResult)
                ReaderSecondaryButton(text = "下一条", onClick = onNextResult)
            }
        }
    }
}

@Composable
private fun ContentSearchFilters(
    filters: List<ContentSearchFilterUiModel>,
    onFilterChange: (ContentSearchFilterUiModel) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.xs)) {
        filters.forEach { filter ->
            ReaderChip(
                text = filter.label,
                selected = filter.active,
                onClick = { onFilterChange(filter) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ContentSearchResultList(
    results: List<ContentSearchResultUiModel>,
    query: String,
    onOpenResult: (ContentSearchResultUiModel) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.xs)) {
        results.forEach { result ->
            ContentSearchResultRow(result, query, onOpenResult)
        }
    }
}

@Composable
private fun ContentSearchResultRow(
    result: ContentSearchResultUiModel,
    query: String,
    onOpenResult: (ContentSearchResultUiModel) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ReaderTheme.shapes.small)
            .background(ReaderTheme.colors.metaBg)
            .clickable(role = Role.Button) { onOpenResult(result) }
            .semantics { contentDescription = "搜索结果，${result.title}，跳转并高亮" }
            .padding(ReaderTheme.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.xs)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(result.title, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.bookTitle)
                Text(result.meta, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.bookMeta)
            }
            ReaderChip(text = result.progressLabel, selected = result.progressLabel == "当前章节")
        }
        HighlightedText(
            text = result.excerpt,
            query = query,
            color = ReaderTheme.colors.bodyText,
            style = ReaderTheme.typography.bookMeta
        )
    }
}

@Composable
private fun ContentSearchLoadingPanel(feedback: ReaderShellFeedbackUiModel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 210.dp)
            .clip(ReaderTheme.shapes.small)
            .background(ReaderTheme.colors.metaBg)
            .semantics { contentDescription = feedback.title }
            .padding(ReaderTheme.spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(feedback.title, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.pageTitle)
        Spacer(modifier = Modifier.height(ReaderTheme.spacing.xs))
        Text(feedback.message, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.stateMessage)
        Spacer(modifier = Modifier.height(ReaderTheme.spacing.md))
        repeat(4) { index ->
            Box(
                modifier = Modifier
                    .fillMaxWidth(if (index % 2 == 0) 0.86f else 0.64f)
                    .height(10.dp)
                    .clip(ReaderTheme.shapes.chip)
                    .background(ReaderTheme.colors.mutedTrack)
            )
            Spacer(modifier = Modifier.height(ReaderTheme.spacing.xs))
        }
    }
}

@Composable
private fun ContentSearchFeedbackPanel(
    feedback: ReaderShellFeedbackUiModel,
    onPrimary: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 210.dp)
            .clip(ReaderTheme.shapes.small)
            .background(ReaderTheme.colors.metaBg)
            .semantics { contentDescription = feedback.title }
            .padding(ReaderTheme.spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = if (feedback.title == "无匹配结果") {
                ReaderIconToken.Search.asImageVector()
            } else {
                ReaderIconToken.Refresh.asImageVector()
            },
            contentDescription = null,
            tint = ReaderTheme.colors.primary,
            modifier = Modifier.size(32.dp)
        )
        Spacer(modifier = Modifier.height(ReaderTheme.spacing.sm))
        Text(feedback.title, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.pageTitle)
        Spacer(modifier = Modifier.height(ReaderTheme.spacing.xs))
        Text(feedback.message, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.stateMessage)
        feedback.primaryAction?.let { label ->
            Spacer(modifier = Modifier.height(ReaderTheme.spacing.md))
            ReaderPrimaryButton(text = label, onClick = onPrimary)
        }
    }
}

@Composable
private fun ContentSearchOfflineBanner(feedback: ReaderShellFeedbackUiModel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ReaderTheme.shapes.small)
            .background(ReaderTheme.colors.floatingControlBgAlt)
            .padding(ReaderTheme.spacing.sm)
    ) {
        Text(feedback.title, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.bookTitle)
        Text(feedback.message, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.bookMeta)
    }
}

@Composable
private fun HighlightedText(
    text: String,
    query: String,
    color: androidx.compose.ui.graphics.Color,
    style: androidx.compose.ui.text.TextStyle
) {
    val annotated = buildAnnotatedString {
        if (query.isBlank() || !text.contains(query)) {
            append(text)
            return@buildAnnotatedString
        }
        val parts = text.split(query)
        parts.forEachIndexed { index, part ->
            append(part)
            if (index != parts.lastIndex) {
                withStyle(
                    SpanStyle(
                        color = ReaderTheme.colors.primary,
                        fontWeight = FontWeight.Bold,
                        background = ReaderTheme.colors.floatingControlBgAlt
                    )
                ) {
                    append(query)
                }
            }
        }
    }
    Text(text = annotated, color = color, style = style)
}
