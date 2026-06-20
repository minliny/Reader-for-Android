package com.reader.android.ui.settings

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.reader.android.ui.components.BookCover
import com.reader.android.ui.components.ReaderAppTopBar
import com.reader.android.ui.components.ReaderChip
import com.reader.android.ui.components.ReaderIconToken
import com.reader.android.ui.components.ReaderPrimaryButton
import com.reader.android.ui.components.ReaderSecondaryButton
import com.reader.android.ui.components.ReaderSwitch
import com.reader.android.ui.components.asImageVector
import com.reader.android.ui.theme.ReaderTheme

private val BookshelfSearchDangerColor = Color(0xFFD92D20)

@Composable
fun BookshelfSearchSettingsScreen(
    state: BookshelfSearchSettingsUiState = BookshelfSearchSettingsMapper.fromFixture(),
    onBack: () -> Unit = {},
    onLayoutChange: (String) -> Unit = {},
    onColumnCountChange: (Int) -> Unit = {},
    onSelectOpen: (BookshelfSearchSettingsRowUiModel) -> Unit = {},
    onSelectOption: (BookshelfSearchSettingsRowUiModel, String) -> Unit = { _, _ -> },
    onSwitchChange: (BookshelfSearchSettingsRowUiModel, Boolean) -> Unit = { _, _ -> },
    onClearHistoryOpen: () -> Unit = {},
    onClearHistoryConfirm: () -> Unit = {},
    onRetry: () -> Unit = {},
    onOpenSystemSettings: () -> Unit = {}
) {
    ReaderTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ReaderTheme.colors.paperBg)
                .semantics { contentDescription = "SettingsShell 书架与搜索" }
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                ReaderAppTopBar(
                    title = state.topBar.title,
                    navigationContentDescription = state.topBar.backLabel,
                    onNavigateBack = onBack
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(ReaderTheme.spacing.screenPadding)
                        .semantics { contentDescription = "settingsContent" },
                    verticalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)
                ) {
                    BookshelfSearchSettingsSection(
                        section = state.bookshelf,
                        onLayoutChange = onLayoutChange,
                        onColumnCountChange = onColumnCountChange,
                        onSelectOpen = onSelectOpen,
                        onSwitchChange = onSwitchChange
                    )
                    BookshelfModePreviewCard(preview = state.preview)
                    BookshelfSearchSettingsSection(
                        section = state.search,
                        onLayoutChange = onLayoutChange,
                        onColumnCountChange = onColumnCountChange,
                        onSelectOpen = onSelectOpen,
                        onSwitchChange = onSwitchChange
                    )
                    BookshelfSearchDangerActionRow(state.danger, onClearHistoryOpen)
                    if (state.displayState == BookshelfSearchSettingsDisplayState.Loading) {
                        BookshelfSearchFeedback(state.loading, onPrimary = onRetry, loading = true)
                    }
                    if (state.displayState == BookshelfSearchSettingsDisplayState.Error) {
                        BookshelfSearchFeedback(state.error, onPrimary = onRetry)
                    }
                    if (state.displayState == BookshelfSearchSettingsDisplayState.Permission) {
                        BookshelfSearchFeedback(state.permission, onPrimary = onOpenSystemSettings)
                    }
                }
            }

            BookshelfSearchToastHost(
                state = state,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 58.dp)
                    .semantics { contentDescription = "toastHost" }
            )
            if (state.displayState == BookshelfSearchSettingsDisplayState.OptionSheet) {
                BookshelfSearchOptionSheet(
                    row = state.searchRangeRow,
                    onSelectOption = onSelectOption,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .semantics { contentDescription = "sheetHost" }
                )
            }
            if (state.displayState == BookshelfSearchSettingsDisplayState.Confirm) {
                BookshelfSearchConfirmDialog(
                    danger = state.danger,
                    onCancel = onBack,
                    onConfirm = onClearHistoryConfirm,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(ReaderTheme.spacing.screenPadding)
                        .semantics { contentDescription = "dialogHost" }
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .semantics { contentDescription = "settingsStateHost" }
            )
        }
    }
}

@Composable
private fun BookshelfSearchSettingsSection(
    section: BookshelfSearchSettingsSectionUiModel,
    onLayoutChange: (String) -> Unit,
    onColumnCountChange: (Int) -> Unit,
    onSelectOpen: (BookshelfSearchSettingsRowUiModel) -> Unit,
    onSwitchChange: (BookshelfSearchSettingsRowUiModel, Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "设置分组，${section.title}" },
        verticalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.xs)
    ) {
        Text(section.title, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.sectionTitle)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ReaderTheme.shapes.small)
                .background(ReaderTheme.colors.metaBg)
        ) {
            section.rows.forEach { row ->
                BookshelfSearchSettingsRow(
                    row = row,
                    onLayoutChange = onLayoutChange,
                    onColumnCountChange = onColumnCountChange,
                    onSelectOpen = onSelectOpen,
                    onSwitchChange = onSwitchChange
                )
            }
        }
    }
}

@Composable
private fun BookshelfSearchSettingsRow(
    row: BookshelfSearchSettingsRowUiModel,
    onLayoutChange: (String) -> Unit,
    onColumnCountChange: (Int) -> Unit,
    onSelectOpen: (BookshelfSearchSettingsRowUiModel) -> Unit,
    onSwitchChange: (BookshelfSearchSettingsRowUiModel, Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .clickable(enabled = row.type == BookshelfSearchSettingsRowType.Select, role = Role.Button) { onSelectOpen(row) }
            .semantics { contentDescription = "${row.title}，${row.value.ifBlank { row.meta }}" }
            .padding(horizontal = ReaderTheme.spacing.sm, vertical = ReaderTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)
    ) {
        Icon(
            imageVector = row.icon.toBookshelfSearchIcon().asImageVector(),
            contentDescription = null,
            tint = ReaderTheme.colors.primary,
            modifier = Modifier.size(20.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(row.title, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.bookTitle)
            val secondary = row.meta.ifBlank { row.value }
            if (secondary.isNotBlank()) {
                Text(secondary, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.bookMeta)
            }
        }
        when (row.type) {
            BookshelfSearchSettingsRowType.Segment -> Row(horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.xs)) {
                row.options.forEach { option ->
                    ReaderChip(
                        text = option,
                        selected = option == row.value,
                        onClick = { onLayoutChange(option) }
                    )
                }
            }
            BookshelfSearchSettingsRowType.Stepper -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.xs)
            ) {
                ReaderSecondaryButton(text = row.minLabel, onClick = { onColumnCountChange(-1) }, modifier = Modifier.width(42.dp))
                ReaderChip(text = row.value, selected = true)
                ReaderSecondaryButton(text = row.maxLabel, onClick = { onColumnCountChange(1) }, modifier = Modifier.width(42.dp))
            }
            BookshelfSearchSettingsRowType.Select -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.xs)
            ) {
                ReaderChip(text = row.value, selected = true)
                Icon(
                    imageVector = ReaderIconToken.Chevron.asImageVector(),
                    contentDescription = null,
                    tint = ReaderTheme.colors.bodyText,
                    modifier = Modifier.size(18.dp)
                )
            }
            BookshelfSearchSettingsRowType.Switch -> ReaderSwitch(
                checked = row.enabled,
                onCheckedChange = { onSwitchChange(row, it) }
            )
        }
    }
}

@Composable
private fun BookshelfModePreviewCard(preview: BookshelfSearchPreviewUiModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ReaderTheme.shapes.small)
            .background(ReaderTheme.colors.metaBg)
            .border(1.dp, ReaderTheme.colors.controlBorder, ReaderTheme.shapes.small)
            .semantics { contentDescription = "${preview.coverTitle}，${preview.listTitle}" }
            .padding(ReaderTheme.spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)
    ) {
        BookshelfCoverPreview(preview, modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(210.dp)
                .background(ReaderTheme.colors.controlBorder)
        )
        BookshelfListPreview(preview, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun BookshelfCoverPreview(
    preview: BookshelfSearchPreviewUiModel,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.xs)) {
        Text(preview.coverTitle, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.bookMeta, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.xs)) {
            preview.books.forEach { book ->
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box {
                        BookCover(title = book.title, modifier = Modifier.fillMaxWidth())
                        BookshelfSearchBadge(book.badge, modifier = Modifier.align(Alignment.TopEnd))
                    }
                    Spacer(Modifier.height(ReaderTheme.spacing.xs))
                    Text(
                        book.title,
                        color = ReaderTheme.colors.controlInk,
                        style = ReaderTheme.typography.bookMeta,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun BookshelfListPreview(
    preview: BookshelfSearchPreviewUiModel,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.xs)) {
        Text(preview.listTitle, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.bookMeta, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        preview.books.forEach { book ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.xs)
            ) {
                Box {
                    BookCover(title = book.title, modifier = Modifier.size(width = 34.dp, height = 48.dp))
                    BookshelfSearchBadge(book.badge, modifier = Modifier.align(Alignment.TopEnd))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(book.title, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.bookTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(book.meta, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.bookMeta, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(book.update, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.bookMeta, maxLines = 1)
            }
        }
    }
}

@Composable
private fun BookshelfSearchBadge(
    label: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(18.dp)
            .clip(CircleShape)
            .background(BookshelfSearchDangerColor),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White, style = ReaderTheme.typography.readerControlLabel)
    }
}

@Composable
private fun BookshelfSearchDangerActionRow(
    danger: BookshelfSearchDangerUiModel,
    onClearHistoryOpen: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ReaderTheme.shapes.small)
            .background(ReaderTheme.colors.metaBg)
            .border(1.dp, BookshelfSearchDangerColor.copy(alpha = 0.35f), ReaderTheme.shapes.small)
            .clickable(role = Role.Button, onClick = onClearHistoryOpen)
            .semantics { contentDescription = danger.title }
            .padding(ReaderTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)
    ) {
        Icon(
            imageVector = ReaderIconToken.Trash.asImageVector(),
            contentDescription = null,
            tint = BookshelfSearchDangerColor,
            modifier = Modifier.size(20.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(danger.title, color = BookshelfSearchDangerColor, style = ReaderTheme.typography.bookTitle)
            Text(danger.copy, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.bookMeta)
        }
        Icon(
            imageVector = ReaderIconToken.Chevron.asImageVector(),
            contentDescription = null,
            tint = BookshelfSearchDangerColor,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun BookshelfSearchOptionSheet(
    row: BookshelfSearchSettingsRowUiModel,
    onSelectOption: (BookshelfSearchSettingsRowUiModel, String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
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
        Text(row.title, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.pageTitle)
        row.options.forEach { option ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(ReaderTheme.shapes.small)
                    .background(if (option == row.value) ReaderTheme.colors.floatingControlBgAlt else ReaderTheme.colors.metaBg)
                    .clickable(role = Role.Button) { onSelectOption(row, option) }
                    .semantics { contentDescription = "选项，$option" }
                    .padding(ReaderTheme.spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(option, modifier = Modifier.weight(1f), color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.bookTitle)
                if (option == row.value) {
                    ReaderChip(text = "当前", selected = true)
                }
            }
        }
        ReaderSecondaryButton(text = "取消", onClick = {}, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun BookshelfSearchToastHost(
    state: BookshelfSearchSettingsUiState,
    modifier: Modifier = Modifier
) {
    val label = when (state.displayState) {
        BookshelfSearchSettingsDisplayState.Default -> state.toast.success
        BookshelfSearchSettingsDisplayState.Error -> state.toast.error
        BookshelfSearchSettingsDisplayState.Permission -> state.toast.permission
        else -> null
    } ?: return
    Box(
        modifier = modifier
            .clip(ReaderTheme.shapes.chip)
            .background(ReaderTheme.colors.floatingControlBgAlt)
            .border(1.dp, ReaderTheme.colors.controlBorder, ReaderTheme.shapes.chip)
            .padding(horizontal = ReaderTheme.spacing.md, vertical = ReaderTheme.spacing.sm)
    ) {
        Text(label, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.readerControlLabel)
    }
}

@Composable
private fun BookshelfSearchConfirmDialog(
    danger: BookshelfSearchDangerUiModel,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(ReaderTheme.shapes.card)
            .background(ReaderTheme.colors.paperBg)
            .border(1.dp, BookshelfSearchDangerColor.copy(alpha = 0.45f), ReaderTheme.shapes.card)
            .padding(ReaderTheme.spacing.cardPadding),
        verticalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)
    ) {
        Text(danger.confirmTitle, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.pageTitle)
        Text(danger.copy, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.stateMessage)
        Row(horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)) {
            ReaderSecondaryButton(text = danger.cancelLabel, onClick = onCancel, modifier = Modifier.weight(1f))
            BookshelfSearchDangerButton(text = danger.confirmLabel, onClick = onConfirm, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun BookshelfSearchDangerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(ReaderTheme.shapes.chip)
            .background(BookshelfSearchDangerColor)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = text }
            .padding(horizontal = ReaderTheme.spacing.md, vertical = ReaderTheme.spacing.sm),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White, style = ReaderTheme.typography.readerControlLabel)
    }
}

@Composable
private fun BookshelfSearchFeedback(
    feedback: BookshelfSearchSettingsFeedbackUiModel,
    onPrimary: () -> Unit,
    loading: Boolean = false
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 220.dp)
            .clip(ReaderTheme.shapes.small)
            .background(ReaderTheme.colors.metaBg)
            .semantics { contentDescription = feedback.title }
            .padding(ReaderTheme.spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = if (loading) ReaderIconToken.Refresh.asImageVector() else ReaderIconToken.Permission.asImageVector(),
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

private fun String.toBookshelfSearchIcon(): ReaderIconToken = when (this) {
    "grid" -> ReaderIconToken.Grid
    "columns" -> ReaderIconToken.ViewList
    "folder" -> ReaderIconToken.Folder
    "badge" -> ReaderIconToken.Badge
    "search" -> ReaderIconToken.Search
    "sort" -> ReaderIconToken.Sort
    "people" -> ReaderIconToken.People
    "clock" -> ReaderIconToken.Clock
    "list" -> ReaderIconToken.List
    "trash" -> ReaderIconToken.Trash
    else -> ReaderIconToken.Settings
}
