package com.reader.android.ui.bookshelf

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.reader.android.ui.components.ReaderAppTopBar
import com.reader.android.ui.components.ReaderChip
import com.reader.android.ui.components.ReaderIconButton
import com.reader.android.ui.components.ReaderIconToken
import com.reader.android.ui.components.ReaderLoadingState
import com.reader.android.ui.components.ReaderMainTab
import com.reader.android.ui.components.ReaderMainTabBar
import com.reader.android.ui.components.ReaderPrimaryButton
import com.reader.android.ui.components.ReaderSecondaryButton
import com.reader.android.ui.components.asImageVector
import com.reader.android.ui.theme.ReaderTheme

@Composable
fun BookshelfEmptyDesignScreen(
    state: BookshelfEmptyDesignUiState = BookshelfEmptyDesignMapper.currentGroupEmpty(),
    onSearch: () -> Unit = {},
    onMore: () -> Unit = {},
    onGroupChange: (BookshelfEmptyGroupUiModel) -> Unit = {},
    onPrimaryAction: (BookshelfEmptyDisplayState) -> Unit = {},
    onSecondaryAction: (BookshelfEmptyDisplayState) -> Unit = {},
    onNavChange: (Int) -> Unit = {}
) {
    ReaderTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(ReaderTheme.colors.paperBg)
                .semantics { contentDescription = "LibraryShell，书架空状态" }
        ) {
            ReaderAppTopBar(
                title = state.title,
                actions = {
                    ReaderIconButton(
                        icon = ReaderIconToken.Search.asImageVector(),
                        contentDescription = "搜索书籍",
                        onClick = onSearch
                    )
                    ReaderIconButton(
                        icon = ReaderIconToken.More.asImageVector(),
                        contentDescription = "更多书架操作",
                        onClick = onMore
                    )
                }
            )
            BookshelfEmptyGroupRow(
                groups = state.groups,
                onGroupChange = onGroupChange
            )
            BookshelfEmptyStateHost(
                state = state,
                onPrimaryAction = onPrimaryAction,
                onSecondaryAction = onSecondaryAction,
                modifier = Modifier.weight(1f)
            )
            ReaderMainTabBar(
                tabs = bookshelfEmptyMainTabs(),
                selectedIndex = 0,
                onTabSelected = onNavChange
            )
        }
    }
}

@Composable
private fun BookshelfEmptyGroupRow(
    groups: List<BookshelfEmptyGroupUiModel>,
    onGroupChange: (BookshelfEmptyGroupUiModel) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = ReaderTheme.spacing.screenPadding, vertical = ReaderTheme.spacing.xs)
            .semantics { contentDescription = "ContentRegion，书架分组" },
        horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.xs)
    ) {
        groups.forEach { group ->
            ReaderChip(
                text = group.label,
                selected = group.active,
                contentDescription = "书架分组，${group.label}",
                onClick = { onGroupChange(group) }
            )
        }
    }
}

@Composable
private fun BookshelfEmptyStateHost(
    state: BookshelfEmptyDesignUiState,
    onPrimaryAction: (BookshelfEmptyDisplayState) -> Unit,
    onSecondaryAction: (BookshelfEmptyDisplayState) -> Unit,
    modifier: Modifier = Modifier
) {
    if (state.displayState == BookshelfEmptyDisplayState.Loading) {
        ReaderLoadingState(
            modifier = modifier.semantics { contentDescription = "StateHost，${state.state.title}" },
            message = state.state.message
        )
    } else {
        BookshelfEmptyStateCard(
            state = state,
            onPrimaryAction = onPrimaryAction,
            onSecondaryAction = onSecondaryAction,
            modifier = modifier
        )
    }
}

@Composable
private fun BookshelfEmptyStateCard(
    state: BookshelfEmptyDesignUiState,
    onPrimaryAction: (BookshelfEmptyDisplayState) -> Unit,
    onSecondaryAction: (BookshelfEmptyDisplayState) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(ReaderTheme.spacing.lg)
            .semantics { contentDescription = "StateHost，${state.state.title}" },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .height(72.dp)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = state.displayState.iconToken().asImageVector(),
                contentDescription = null,
                tint = ReaderTheme.colors.primary
            )
        }
        Spacer(modifier = Modifier.height(ReaderTheme.spacing.md))
        Text(
            text = state.state.title,
            modifier = Modifier.semantics { heading() },
            color = ReaderTheme.colors.controlInk,
            style = ReaderTheme.typography.stateTitle,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(ReaderTheme.spacing.xs))
        Text(
            text = state.state.message,
            color = ReaderTheme.colors.bodyText,
            style = ReaderTheme.typography.stateMessage,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(ReaderTheme.spacing.xs))
        Text(
            text = state.state.hint,
            color = ReaderTheme.colors.controlInk,
            style = ReaderTheme.typography.bookMeta,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(ReaderTheme.spacing.md))
        Row(horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)) {
            if (state.state.secondaryAction.isNotBlank()) {
                ReaderSecondaryButton(
                    text = state.state.secondaryAction,
                    onClick = { onSecondaryAction(state.displayState) }
                )
            }
            if (state.state.primaryAction.isNotBlank()) {
                ReaderPrimaryButton(
                    text = state.state.primaryAction,
                    onClick = { onPrimaryAction(state.displayState) }
                )
            }
        }
    }
}

private fun BookshelfEmptyDisplayState.iconToken(): ReaderIconToken =
    when (this) {
        BookshelfEmptyDisplayState.Default,
        BookshelfEmptyDisplayState.AllEmpty -> ReaderIconToken.FolderOff
        BookshelfEmptyDisplayState.Loading -> ReaderIconToken.Refresh
        BookshelfEmptyDisplayState.Error -> ReaderIconToken.Warning
        BookshelfEmptyDisplayState.Offline -> ReaderIconToken.Offline
        BookshelfEmptyDisplayState.Permission -> ReaderIconToken.Permission
    }

private fun bookshelfEmptyMainTabs(): List<ReaderMainTab> =
    listOf(
        ReaderMainTab("书架", "主导航，书架", ReaderIconToken.Bookshelf.asImageVector()),
        ReaderMainTab("发现", "主导航，发现", ReaderIconToken.Discover.asImageVector()),
        ReaderMainTab("RSS", "主导航，RSS", ReaderIconToken.Rss.asImageVector()),
        ReaderMainTab("设置", "主导航，设置", ReaderIconToken.Settings.asImageVector())
    )
