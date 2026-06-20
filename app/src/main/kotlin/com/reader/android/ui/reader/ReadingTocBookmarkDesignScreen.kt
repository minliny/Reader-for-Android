package com.reader.android.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.reader.android.ui.components.ReaderChip
import com.reader.android.ui.components.ReaderIconButton
import com.reader.android.ui.components.ReaderIconToken
import com.reader.android.ui.components.ReaderPrimaryButton
import com.reader.android.ui.components.ReaderSecondaryButton
import com.reader.android.ui.components.asImageVector
import com.reader.android.ui.theme.ReaderTheme

@Composable
fun ReadingTocBookmarkScreen(
    state: ReadingTocBookmarkUiState = ReadingTocBookmarkMapper.fromFixture(),
    onBack: () -> Unit = {},
    onSourceChange: () -> Unit = {},
    onMoreMenuOpen: () -> Unit = {},
    onSegmentChange: (String) -> Unit = {},
    onChapterJump: (ReadingChapterRowUiModel) -> Unit = {},
    onBookmarkJump: (ReadingBookmarkRowUiModel) -> Unit = {},
    onRetry: () -> Unit = {},
    onCacheCurrentVolume: () -> Unit = {},
    onFilterUnread: () -> Unit = {}
) {
    ReaderTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ReaderTheme.colors.paperBg)
                .semantics { contentDescription = "ReaderShell 目录与书签" }
        ) {
            TocBookmarkReadingSurface(state.reading)
            TocBookmarkOverlay(
                state = state,
                onBack = onBack,
                onSourceChange = onSourceChange,
                onMoreMenuOpen = onMoreMenuOpen,
                modifier = Modifier.semantics { contentDescription = "readerOverlayHost" }
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            ) {
                TocBookmarkBottomSheet(
                    state = state,
                    onSegmentChange = onSegmentChange,
                    onChapterJump = onChapterJump,
                    onBookmarkJump = onBookmarkJump,
                    onRetry = onRetry,
                    onCacheCurrentVolume = onCacheCurrentVolume,
                    onFilterUnread = onFilterUnread,
                    modifier = Modifier.semantics { contentDescription = "bottomSheetHost" }
                )
                TocBookmarkModuleNav(
                    items = state.moduleNav,
                    modifier = Modifier.semantics { contentDescription = "readerModuleNav" }
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .semantics { contentDescription = "readerStateHost" }
            )
        }
    }
}

@Composable
private fun TocBookmarkReadingSurface(reading: ReaderShellTextUiModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 28.dp, end = 28.dp, top = 108.dp, bottom = 420.dp)
            .semantics { contentDescription = "readingSurface" }
    ) {
        reading.paragraphs.forEach { paragraph ->
            Text(
                text = paragraph,
                color = ReaderTheme.colors.bodyText,
                style = ReaderTheme.typography.readerBody
            )
            Spacer(modifier = Modifier.height(ReaderTheme.spacing.md))
        }
    }
}

@Composable
private fun TocBookmarkOverlay(
    state: ReadingTocBookmarkUiState,
    onBack: () -> Unit,
    onSourceChange: () -> Unit,
    onMoreMenuOpen: () -> Unit,
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
                icon = ReaderIconToken.Back.asImageVector(),
                contentDescription = "返回",
                onClick = onBack
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    state.topControl.bookTitle,
                    color = ReaderTheme.colors.controlInk,
                    style = ReaderTheme.typography.bookTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    state.topControl.sourceLine,
                    color = ReaderTheme.colors.bodyText,
                    style = ReaderTheme.typography.bookMeta,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            ReaderSecondaryButton(text = state.topControl.sourceActionLabel, onClick = onSourceChange)
            ReaderIconButton(
                icon = ReaderIconToken.More.asImageVector(),
                contentDescription = "更多",
                onClick = onMoreMenuOpen
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ReaderTheme.spacing.screenPadding, vertical = ReaderTheme.spacing.xs),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(state.bottomReadout.progress, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.bookMeta)
            Text(state.bottomReadout.chapter, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.bookMeta)
        }
    }
}

@Composable
private fun TocBookmarkBottomSheet(
    state: ReadingTocBookmarkUiState,
    onSegmentChange: (String) -> Unit,
    onChapterJump: (ReadingChapterRowUiModel) -> Unit,
    onBookmarkJump: (ReadingBookmarkRowUiModel) -> Unit,
    onRetry: () -> Unit,
    onCacheCurrentVolume: () -> Unit,
    onFilterUnread: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 320.dp, max = 470.dp)
            .clip(ReaderTheme.shapes.bottomSheet)
            .background(ReaderTheme.colors.paperBg)
            .border(1.dp, ReaderTheme.colors.controlBorder, ReaderTheme.shapes.bottomSheet)
            .padding(ReaderTheme.spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            TocBookmarkPanelHeader(state.panel)
            Spacer(modifier = Modifier.height(ReaderTheme.spacing.xs))
            if (state.displayState == ReadingTocBookmarkDisplayState.Search) {
                TocBookmarkSearchField(state.search.query, state.search.resultsLabel)
                Spacer(modifier = Modifier.height(ReaderTheme.spacing.xs))
            }
            TocBookmarkSegmentTabs(
                tabs = state.panel.tabs,
                activeType = state.activeTabType,
                onSegmentChange = onSegmentChange
            )
            Spacer(modifier = Modifier.height(ReaderTheme.spacing.xs))
            when (state.displayState) {
                ReadingTocBookmarkDisplayState.Loading -> TocBookmarkFeedbackBlock(state.loading, onPrimary = onRetry)
                ReadingTocBookmarkDisplayState.Error -> TocBookmarkFeedbackBlock(state.error, onPrimary = onRetry)
                ReadingTocBookmarkDisplayState.Empty -> TocBookmarkFeedbackBlock(
                    feedback = state.empty,
                    onPrimary = { onSegmentChange("directory") }
                )
                ReadingTocBookmarkDisplayState.Bookmark -> TocBookmarkList(
                    state = state,
                    onChapterJump = onChapterJump,
                    onBookmarkJump = onBookmarkJump
                )
                else -> TocBookmarkList(
                    state = state,
                    onChapterJump = onChapterJump,
                    onBookmarkJump = onBookmarkJump
                )
            }
            if (state.displayState == ReadingTocBookmarkDisplayState.MoreMenu) {
                Spacer(modifier = Modifier.height(ReaderTheme.spacing.xs))
                TocBookmarkMoreMenu(
                    menu = state.moreMenu,
                    onCacheCurrentVolume = onCacheCurrentVolume,
                    onFilterUnread = onFilterUnread
                )
            }
        }
        TocBookmarkBrightnessPanel(state.brightness)
    }
}

@Composable
private fun TocBookmarkPanelHeader(panel: ReadingTocPanelUiModel) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(panel.title, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.pageTitle)
            Text(panel.meta, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.bookMeta)
        }
        ReaderSecondaryButton(text = panel.fullDirectoryLabel, onClick = {})
    }
}

@Composable
private fun TocBookmarkSearchField(query: String, resultsLabel: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ReaderTheme.shapes.chip)
            .background(ReaderTheme.colors.metaBg)
            .padding(horizontal = ReaderTheme.spacing.sm, vertical = ReaderTheme.spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = ReaderIconToken.Search.asImageVector(),
            contentDescription = null,
            tint = ReaderTheme.colors.bodyText,
            modifier = Modifier.size(16.dp)
        )
        Text(query, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.bookMeta)
        Spacer(modifier = Modifier.weight(1f))
        Text(resultsLabel, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.bookMeta)
    }
}

@Composable
private fun TocBookmarkSegmentTabs(
    tabs: List<ReadingTocPanelTabUiModel>,
    activeType: String,
    onSegmentChange: (String) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.xs)) {
        tabs.forEach { tab ->
            ReaderChip(
                text = tab.label,
                selected = tab.type == activeType,
                contentDescription = "目录书签切换，${tab.label}",
                onClick = { onSegmentChange(tab.type) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun TocBookmarkList(
    state: ReadingTocBookmarkUiState,
    onChapterJump: (ReadingChapterRowUiModel) -> Unit,
    onBookmarkJump: (ReadingBookmarkRowUiModel) -> Unit
) {
    if (state.activeTabType == "bookmark") {
        LazyColumn(
            modifier = Modifier.heightIn(min = 160.dp, max = 260.dp),
            verticalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.xs)
        ) {
            items(state.visibleBookmarks, key = { it.chapter + it.location }) { bookmark ->
                BookmarkRow(bookmark = bookmark, onBookmarkJump = onBookmarkJump)
            }
        }
    } else {
        Column {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(state.panel.currentVolume, modifier = Modifier.weight(1f), color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.bookMeta)
                ReaderSecondaryButton(text = state.panel.returnProgressLabel, onClick = {})
            }
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(state.visibleChapters, key = { it.title }) { chapter ->
                    ChapterRow(chapter = chapter, onChapterJump = onChapterJump)
                }
            }
            Text(state.panel.filterLabel, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.bookMeta)
        }
    }
}

@Composable
private fun ChapterRow(
    chapter: ReadingChapterRowUiModel,
    onChapterJump: (ReadingChapterRowUiModel) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ReaderTheme.shapes.small)
            .background(if (chapter.current) ReaderTheme.colors.primary.copy(alpha = 0.10f) else ReaderTheme.colors.paperBg)
            .clickable(role = Role.Button) { onChapterJump(chapter) }
            .semantics { contentDescription = "章节，${chapter.title}，${chapter.status}" }
            .padding(horizontal = ReaderTheme.spacing.sm, vertical = ReaderTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            chapter.title,
            modifier = Modifier.weight(1f),
            color = if (chapter.current) ReaderTheme.colors.primary else ReaderTheme.colors.controlInk,
            style = ReaderTheme.typography.bookTitle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(chapter.status, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.bookMeta)
    }
}

@Composable
private fun BookmarkRow(
    bookmark: ReadingBookmarkRowUiModel,
    onBookmarkJump: (ReadingBookmarkRowUiModel) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ReaderTheme.shapes.small)
            .background(ReaderTheme.colors.metaBg)
            .clickable(role = Role.Button) { onBookmarkJump(bookmark) }
            .semantics { contentDescription = "书签，${bookmark.chapter}，${bookmark.location}" }
            .padding(ReaderTheme.spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)
    ) {
        Icon(
            imageVector = ReaderIconToken.Bookmark.asImageVector(),
            contentDescription = null,
            tint = ReaderTheme.colors.primary,
            modifier = Modifier.size(18.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text("${bookmark.chapter} · ${bookmark.location}", color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.bookTitle)
            Text(bookmark.excerpt, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.bookMeta)
            Text(bookmark.time, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.readerSmallMeta)
        }
    }
}

@Composable
private fun TocBookmarkFeedbackBlock(
    feedback: ReaderShellFeedbackUiModel,
    onPrimary: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 180.dp, max = 260.dp)
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
        feedback.primaryAction?.let { label ->
            Spacer(modifier = Modifier.height(ReaderTheme.spacing.md))
            ReaderPrimaryButton(text = label, onClick = onPrimary)
        }
    }
}

@Composable
private fun TocBookmarkMoreMenu(
    menu: ReadingTocMoreMenuUiModel,
    onCacheCurrentVolume: () -> Unit,
    onFilterUnread: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ReaderTheme.shapes.small)
            .background(ReaderTheme.colors.floatingControlBg)
            .semantics { contentDescription = menu.title }
            .padding(ReaderTheme.spacing.sm)
    ) {
        Text(menu.title, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.sectionTitle)
        menu.items.forEachIndexed { index, item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) {
                        if (index == 0) onCacheCurrentVolume() else onFilterUnread()
                    }
                    .padding(vertical = ReaderTheme.spacing.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.label, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.bookTitle)
                    Text(item.description, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.bookMeta)
                }
            }
        }
    }
}

@Composable
private fun TocBookmarkBrightnessPanel(brightness: ReadingBrightnessUiModel) {
    Column(
        modifier = Modifier
            .width(52.dp)
            .fillMaxHeight()
            .clip(ReaderTheme.shapes.small)
            .background(ReaderTheme.colors.metaBg)
            .semantics { contentDescription = "亮度控制，${brightness.value}%" }
            .padding(vertical = ReaderTheme.spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(brightness.title, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.readerSmallMeta)
        Icon(
            imageVector = ReaderIconToken.AutoBrightness.asImageVector(),
            contentDescription = null,
            tint = ReaderTheme.colors.primary,
            modifier = Modifier.size(18.dp)
        )
        Text("${brightness.value}%", color = ReaderTheme.colors.primary, style = ReaderTheme.typography.readerControlLabel)
        Text(brightness.autoLabel, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.bookMeta)
        Text(brightness.modeLabel, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.readerSmallMeta)
    }
}

@Composable
private fun TocBookmarkModuleNav(
    items: List<ReadingModuleNavItemUiModel>,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(ReaderTheme.colors.bottomBarBg)
            .padding(vertical = ReaderTheme.spacing.xs),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        items.forEach { item ->
            val selected = item.active
            val icon = when (item.type) {
                "tts" -> ReaderIconToken.Tts
                "appearance" -> ReaderIconToken.Appearance
                "settings" -> ReaderIconToken.ReadingSettings
                else -> ReaderIconToken.Directory
            }
            Column(
                modifier = Modifier
                    .width(72.dp)
                    .height(56.dp)
                    .clip(ReaderTheme.shapes.small)
                    .background(if (selected) ReaderTheme.colors.primary.copy(alpha = 0.18f) else ReaderTheme.colors.bottomBarBg)
                    .clickable(role = Role.Button) {}
                    .semantics { contentDescription = "阅读模块，${item.label}" },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = icon.asImageVector(),
                    contentDescription = null,
                    tint = if (selected) ReaderTheme.colors.paperBg else ReaderTheme.colors.controlInk,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    item.label,
                    color = if (selected) ReaderTheme.colors.primary else ReaderTheme.colors.controlInk,
                    style = ReaderTheme.typography.readerControlLabel
                )
            }
        }
    }
}
