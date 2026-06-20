package com.reader.android.ui.bookshelf

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.reader.android.ui.components.BookProgressIndicator
import com.reader.android.ui.components.ReaderAppTopBar
import com.reader.android.ui.components.ReaderCard
import com.reader.android.ui.components.ReaderChip
import com.reader.android.ui.components.ReaderIconToken
import com.reader.android.ui.components.ReaderPrimaryButton
import com.reader.android.ui.components.ReaderSecondaryButton
import com.reader.android.ui.components.asImageVector
import com.reader.android.ui.theme.ReaderTheme

@Composable
fun BookshelfLocalImportScreen(
    state: BookshelfLocalImportUiState = BookshelfLocalImportMapper.fromFixture(),
    onBack: () -> Unit = {},
    onOpenSystemFilePicker: () -> Unit = {},
    onChooseAgain: () -> Unit = {},
    onDone: () -> Unit = {},
    onBackToBookshelf: () -> Unit = {}
) {
    ReaderTheme {
        Column(modifier = Modifier.fillMaxSize().background(ReaderTheme.colors.paperBg)) {
            ReaderAppTopBar(
                title = state.title,
                navigationContentDescription = state.backLabel,
                onNavigateBack = onBack
            )
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = ReaderTheme.spacing.screenPadding),
                verticalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)
            ) {
                when (state.displayState) {
                    BookshelfLocalImportDisplayState.Default -> {
                        item { ImportIntroCard(state.intro, onOpenSystemFilePicker) }
                        item { ImportPermissionCard(state.permission) }
                        item { SupportedFormatsCard(state.supportedFormats) }
                        item { ImportFlowCard(state.flow) }
                        item { ReminderCard(state.reminderTitle, state.reminderMessage) }
                    }
                    BookshelfLocalImportDisplayState.Importing -> {
                        item { ImportingCard(state.importing) }
                        item { ImportPermissionCard(state.permission) }
                    }
                    BookshelfLocalImportDisplayState.Success,
                    BookshelfLocalImportDisplayState.PartialFailed,
                    BookshelfLocalImportDisplayState.Failed -> {
                        item { ImportSummaryCard(state.summary) }
                        if (state.displayState == BookshelfLocalImportDisplayState.Failed) {
                            item { ImportErrorGuidanceCard() }
                        }
                        items(state.results, key = { it.fileName }) { result ->
                            ImportResultRow(result)
                        }
                    }
                    BookshelfLocalImportDisplayState.PickerCancelled -> {
                        item { PickerCancelledCard(state.cancelState, onBackToBookshelf) }
                    }
                }
            }
            if (state.isResultState) {
                ImportBottomActions(
                    state = state,
                    onChooseAgain = onChooseAgain,
                    onDone = onDone,
                    onBackToBookshelf = onBackToBookshelf
                )
            }
        }
    }
}

@Composable
private fun ImportIntroCard(
    intro: BookshelfLocalImportIntroUiModel,
    onOpenSystemFilePicker: () -> Unit
) {
    ReaderCard(contentDescription = intro.title) {
        Text(intro.title, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.pageTitle)
        Spacer(modifier = Modifier.height(ReaderTheme.spacing.xs))
        Text(intro.message, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.stateMessage)
        Spacer(modifier = Modifier.height(ReaderTheme.spacing.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.xs)) {
            intro.formats.forEach { format ->
                ReaderChip(text = format, selected = format != "可多选")
            }
        }
        Spacer(modifier = Modifier.height(ReaderTheme.spacing.md))
        ReaderPrimaryButton(
            text = intro.primaryAction,
            onClick = onOpenSystemFilePicker,
            modifier = Modifier.fillMaxWidth(),
            contentDescription = "打开系统文件选择器"
        )
    }
}

@Composable
private fun ImportPermissionCard(permission: BookshelfLocalImportPermissionUiModel) {
    ReaderCard(contentDescription = permission.title) {
        Text(permission.title, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.sectionTitle)
        Spacer(modifier = Modifier.height(ReaderTheme.spacing.xs))
        Text(permission.message, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.stateMessage)
        Spacer(modifier = Modifier.height(ReaderTheme.spacing.xs))
        Text(permission.footnote, color = ReaderTheme.colors.primary, style = ReaderTheme.typography.bookMeta)
    }
}

@Composable
private fun SupportedFormatsCard(formats: List<BookshelfLocalImportFormatUiModel>) {
    ReaderCard(contentDescription = "支持格式") {
        Text("支持格式", color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.sectionTitle)
        Spacer(modifier = Modifier.height(ReaderTheme.spacing.xs))
        formats.forEach { format ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)
            ) {
                ReaderChip(text = format.label, selected = format.tone == "blue")
                Text(format.message, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.bookMeta)
            }
        }
    }
}

@Composable
private fun ImportFlowCard(flow: List<BookshelfLocalImportFlowStepUiModel>) {
    ReaderCard(contentDescription = "导入流程") {
        Text("导入流程", color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.sectionTitle)
        Spacer(modifier = Modifier.height(ReaderTheme.spacing.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.xs)) {
            flow.forEach { step ->
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .clip(ReaderTheme.shapes.medium)
                            .background(ReaderTheme.colors.primary)
                            .padding(horizontal = ReaderTheme.spacing.sm, vertical = ReaderTheme.spacing.xs),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(step.step, color = ReaderTheme.colors.paperBg, style = ReaderTheme.typography.readerControlLabel)
                    }
                    Spacer(modifier = Modifier.height(ReaderTheme.spacing.xs))
                    Text(
                        step.label,
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
private fun ReminderCard(title: String, message: String) {
    ReaderCard(contentDescription = title) {
        Text(title, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.sectionTitle)
        Spacer(modifier = Modifier.height(ReaderTheme.spacing.xs))
        Text(message, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.stateMessage)
    }
}

@Composable
private fun ImportingCard(importing: BookshelfLocalImportProgressUiModel) {
    ReaderCard(contentDescription = importing.title) {
        Text(importing.title, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.pageTitle)
        Spacer(modifier = Modifier.height(ReaderTheme.spacing.xs))
        Text(importing.message, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.stateMessage)
        Spacer(modifier = Modifier.height(ReaderTheme.spacing.sm))
        Text(importing.currentFile, color = ReaderTheme.colors.primary, style = ReaderTheme.typography.bookTitle)
        Spacer(modifier = Modifier.height(ReaderTheme.spacing.sm))
        BookProgressIndicator(
            progress = importing.progress / 100f,
            modifier = Modifier.fillMaxWidth(),
            contentDescription = "导入进度，${importing.progress}%"
        )
        Spacer(modifier = Modifier.height(ReaderTheme.spacing.xs))
        Text("${importing.progress}% · 解析中禁止重复选择文件", color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.bookMeta)
    }
}

@Composable
private fun ImportSummaryCard(summary: BookshelfLocalImportSummaryUiModel) {
    ReaderCard(contentDescription = summary.title) {
        Text(summary.title, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.pageTitle)
        Spacer(modifier = Modifier.height(ReaderTheme.spacing.xs))
        Text(summary.message, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.stateMessage)
        Spacer(modifier = Modifier.height(ReaderTheme.spacing.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.xs)) {
            summary.counts.forEach { count ->
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(ReaderTheme.shapes.small)
                        .background(ReaderTheme.colors.metaBg)
                        .padding(ReaderTheme.spacing.sm)
                        .semantics { contentDescription = "${count.label}${count.value}" },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("${count.value}", color = ReaderTheme.colors.primary, style = ReaderTheme.typography.pageTitle)
                    Text(count.label, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.bookMeta)
                }
            }
        }
    }
}

@Composable
private fun ImportErrorGuidanceCard() {
    ReaderCard(contentDescription = "失败原因和下一步") {
        Text("失败原因和下一步", color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.sectionTitle)
        Spacer(modifier = Modifier.height(ReaderTheme.spacing.xs))
        Text(
            "不支持或解析失败的文件不会被静默丢弃。请查看每一行原因后，重新选择 TXT 或 EPUB 文件。",
            color = ReaderTheme.colors.bodyText,
            style = ReaderTheme.typography.stateMessage
        )
    }
}

@Composable
private fun ImportResultRow(row: BookshelfLocalImportResultRowUiModel) {
    ReaderCard(contentDescription = row.fileName) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)
        ) {
            ReaderChip(
                text = row.status,
                selected = row.tone == "success",
                contentDescription = "${row.fileName}，${row.status}"
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(row.fileName, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.bookTitle)
                if (row.reason.isNotBlank()) {
                    Text(row.reason, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.bookMeta)
                }
            }
        }
    }
}

@Composable
private fun PickerCancelledCard(
    cancel: BookshelfLocalImportCancelUiModel,
    onBackToBookshelf: () -> Unit
) {
    ReaderCard(contentDescription = cancel.title) {
        Text(cancel.title, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.pageTitle)
        Spacer(modifier = Modifier.height(ReaderTheme.spacing.xs))
        Text(cancel.message, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.stateMessage)
        Spacer(modifier = Modifier.height(ReaderTheme.spacing.md))
        ReaderPrimaryButton(text = "返回书架", onClick = onBackToBookshelf, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun ImportBottomActions(
    state: BookshelfLocalImportUiState,
    onChooseAgain: () -> Unit,
    onDone: () -> Unit,
    onBackToBookshelf: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ReaderTheme.colors.bottomBarBg)
            .padding(ReaderTheme.spacing.screenPadding),
        horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)
    ) {
        when (state.displayState) {
            BookshelfLocalImportDisplayState.Success -> {
                ReaderPrimaryButton(text = state.actions.done, onClick = onDone, modifier = Modifier.fillMaxWidth())
            }
            BookshelfLocalImportDisplayState.PartialFailed -> {
                ReaderSecondaryButton(text = state.actions.done, onClick = onDone, modifier = Modifier.weight(1f))
                ReaderPrimaryButton(text = state.actions.chooseAgain, onClick = onChooseAgain, modifier = Modifier.weight(1f))
            }
            BookshelfLocalImportDisplayState.Failed -> {
                ReaderSecondaryButton(text = state.actions.backToBookshelf, onClick = onBackToBookshelf, modifier = Modifier.weight(1f))
                ReaderPrimaryButton(text = state.actions.chooseAgain, onClick = onChooseAgain, modifier = Modifier.weight(1f))
            }
            else -> Unit
        }
    }
}
