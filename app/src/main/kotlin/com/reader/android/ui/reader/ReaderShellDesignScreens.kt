package com.reader.android.ui.reader

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.reader.android.ui.components.ReaderCard
import com.reader.android.ui.components.ReaderPrimaryButton
import com.reader.android.ui.components.ReaderSecondaryButton
import com.reader.android.ui.theme.ReaderTheme

@Composable
fun ReadingEntryScreen(
    state: ReadingEntryUiState = ReadingEntryMapper.fromFixture(),
    onBackToSource: () -> Unit = {},
    onContinueReading: () -> Unit = {},
    onStartReading: () -> Unit = {},
    onRetryOpen: () -> Unit = {},
    onSwitchSource: () -> Unit = {},
    onContinueCached: () -> Unit = {}
) {
    ReaderTheme {
        Box(modifier = Modifier.fillMaxSize().background(ReaderTheme.colors.paperBg)) {
            ReaderShellReadingSurface(reading = state.reading)
            ReadingEntryInfoLayer(status = state.status, modifier = Modifier.align(Alignment.TopCenter))
            when (state.displayState) {
                ReadingEntryDisplayState.Default -> ReadingEntryDock(
                    dock = state.dock,
                    onBackToSource = onBackToSource,
                    onContinueReading = onContinueReading,
                    onStartReading = onStartReading,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
                ReadingEntryDisplayState.Loading -> ReaderShellFeedbackCard(
                    feedback = state.loading,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
                ReadingEntryDisplayState.Error -> ReaderShellFeedbackCard(
                    feedback = state.error,
                    onPrimary = onSwitchSource,
                    onSecondary = onRetryOpen,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
                ReadingEntryDisplayState.Offline -> ReaderShellFeedbackCard(
                    feedback = state.offline,
                    onPrimary = onContinueCached,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }
}

@Composable
fun ImmersiveReadingScreen(
    state: ImmersiveReadingUiState = ImmersiveReadingMapper.fromFixture(),
    onTapPrevious: () -> Unit = {},
    onTapCenter: () -> Unit = {},
    onTapNext: () -> Unit = {},
    onRetry: () -> Unit = {},
    onContinueCached: () -> Unit = {}
) {
    ReaderTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ReaderTheme.colors.paperBg)
                .semantics { contentDescription = "ReaderShell 沉浸阅读" }
        ) {
            ReaderShellReadingSurface(reading = state.reading)
            ImmersiveInfoLayer(info = state.info)
            ImmersiveTapZones(
                zones = state.zones,
                onTapPrevious = onTapPrevious,
                onTapCenter = onTapCenter,
                onTapNext = onTapNext
            )
            when (state.displayState) {
                ImmersiveReadingDisplayState.Default -> Unit
                ImmersiveReadingDisplayState.Loading -> ReaderShellFeedbackCard(
                    feedback = state.loading,
                    modifier = Modifier.align(Alignment.Center)
                )
                ImmersiveReadingDisplayState.Error -> ReaderShellFeedbackCard(
                    feedback = state.error,
                    onPrimary = onRetry,
                    modifier = Modifier.align(Alignment.Center)
                )
                ImmersiveReadingDisplayState.Offline -> ReaderShellFeedbackCard(
                    feedback = state.offline,
                    onPrimary = onContinueCached,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}

@Composable
private fun ReaderShellReadingSurface(
    reading: ReaderShellTextUiModel,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 28.dp, end = 28.dp, top = 96.dp, bottom = 176.dp)
            .semantics { contentDescription = "readingSurface" }
    ) {
        Text(
            text = reading.title,
            color = ReaderTheme.colors.controlInk,
            style = ReaderTheme.typography.readerChapterTitle,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(ReaderTheme.spacing.md))
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
private fun ReadingEntryInfoLayer(
    status: ReadingEntryStatusUiModel,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(ReaderTheme.colors.softTopBg)
            .padding(horizontal = ReaderTheme.spacing.screenPadding, vertical = ReaderTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = status.left,
            modifier = Modifier.weight(1f),
            color = ReaderTheme.colors.controlInk,
            style = ReaderTheme.typography.readerSmallMeta,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(status.time, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.readerSmallMeta)
        Spacer(modifier = Modifier.width(ReaderTheme.spacing.sm))
        Text(status.progress, color = ReaderTheme.colors.primary, style = ReaderTheme.typography.readerSmallMeta)
        Spacer(modifier = Modifier.width(ReaderTheme.spacing.sm))
        Text(status.chapterLabel, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.readerSmallMeta)
    }
}

@Composable
private fun ReadingEntryDock(
    dock: ReadingEntryDockUiModel,
    onBackToSource: () -> Unit,
    onContinueReading: () -> Unit,
    onStartReading: () -> Unit,
    modifier: Modifier = Modifier
) {
    ReaderCard(
        modifier = modifier.fillMaxWidth().padding(ReaderTheme.spacing.screenPadding),
        contentDescription = "readerOverlayHost"
    ) {
        Text(dock.title, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.pageTitle)
        Spacer(modifier = Modifier.height(ReaderTheme.spacing.xs))
        Text(dock.source, color = ReaderTheme.colors.primary, style = ReaderTheme.typography.bookTitle)
        Text(dock.context, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.bookMeta)
        Spacer(modifier = Modifier.height(ReaderTheme.spacing.md))
        dock.actions.forEachIndexed { index, action ->
            ReadingEntryActionRow(
                action = action,
                onClick = if (action.primary) onContinueReading else onStartReading
            )
            if (index < dock.actions.lastIndex) Spacer(modifier = Modifier.height(ReaderTheme.spacing.sm))
        }
        Spacer(modifier = Modifier.height(ReaderTheme.spacing.md))
        ReaderSecondaryButton(text = dock.backLabel, onClick = onBackToSource, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun ReadingEntryActionRow(
    action: ReadingEntryActionUiModel,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(action.title, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.bookTitle)
            Text(action.meta, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.bookMeta)
        }
        if (action.primary) {
            ReaderPrimaryButton(text = action.label, onClick = onClick)
        } else {
            ReaderSecondaryButton(text = action.label, onClick = onClick)
        }
    }
}

@Composable
private fun ReaderShellFeedbackCard(
    feedback: ReaderShellFeedbackUiModel,
    modifier: Modifier = Modifier,
    onPrimary: (() -> Unit)? = null,
    onSecondary: (() -> Unit)? = null
) {
    ReaderCard(
        modifier = modifier.fillMaxWidth().padding(ReaderTheme.spacing.screenPadding),
        contentDescription = "readerStateHost"
    ) {
        Text(feedback.title, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.pageTitle)
        Spacer(modifier = Modifier.height(ReaderTheme.spacing.xs))
        Text(feedback.message, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.stateMessage)
        Spacer(modifier = Modifier.height(ReaderTheme.spacing.md))
        Row(horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)) {
            feedback.primaryAction?.let { label ->
                ReaderPrimaryButton(text = label, onClick = onPrimary ?: {}, modifier = Modifier.weight(1f))
            }
            feedback.secondaryAction?.let { label ->
                ReaderSecondaryButton(text = label, onClick = onSecondary ?: {}, modifier = Modifier.weight(1f))
            }
            feedback.disabledAction?.let { label ->
                ReaderSecondaryButton(text = label, onClick = {}, enabled = false, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ImmersiveInfoLayer(info: ImmersiveInfoUiModel) {
    Box(modifier = Modifier.fillMaxSize().padding(ReaderTheme.spacing.screenPadding)) {
        WeakInfoText(text = info.topLeft, modifier = Modifier.align(Alignment.TopStart))
        WeakInfoText(text = info.time, modifier = Modifier.align(Alignment.TopEnd))
        ProgressInfo(progress = info.progress, modifier = Modifier.align(Alignment.BottomStart))
        ProgressInfo(progress = info.chapterOnly, modifier = Modifier.align(Alignment.BottomEnd))
    }
}

@Composable
private fun WeakInfoText(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        modifier = modifier
            .clip(ReaderTheme.shapes.small)
            .background(ReaderTheme.colors.softTopBg)
            .padding(horizontal = ReaderTheme.spacing.xs, vertical = 4.dp),
        color = ReaderTheme.colors.controlInk,
        style = ReaderTheme.typography.readerSmallMeta
    )
}

@Composable
private fun ProgressInfo(
    progress: String,
    modifier: Modifier = Modifier
) {
    WeakInfoText(text = progress, modifier = modifier)
}

@Composable
private fun ImmersiveTapZones(
    zones: List<ImmersiveTapZoneUiModel>,
    onTapPrevious: () -> Unit,
    onTapCenter: () -> Unit,
    onTapNext: () -> Unit
) {
    Row(modifier = Modifier.fillMaxSize()) {
        zones.forEach { zone ->
            val onTap = when (zone.type) {
                "previous" -> onTapPrevious
                "next" -> onTapNext
                else -> onTapCenter
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(onClick = onTap)
                    .semantics { contentDescription = zone.label }
            )
        }
    }
}
