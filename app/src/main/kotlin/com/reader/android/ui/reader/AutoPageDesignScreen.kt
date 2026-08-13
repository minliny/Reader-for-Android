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
import com.reader.android.ui.components.ReaderSlider
import com.reader.android.ui.components.ReaderSwitch
import com.reader.android.ui.components.asImageVector
import com.reader.android.ui.theme.ReaderTheme

@Composable
fun AutoPageScreen(
    state: AutoPageUiState = AutoPageMapper.fromFixture(),
    onClose: () -> Unit = {},
    onMore: () -> Unit = {},
    onSpeedChange: (Float) -> Unit = {},
    onModeChange: (AutoPageModeUiModel) -> Unit = {},
    onToggleOption: (AutoPageOptionUiModel, Boolean) -> Unit = { _, _ -> },
    onStartAutoPage: () -> Unit = {},
    onPauseAutoPage: () -> Unit = {},
    onContinueAutoPage: () -> Unit = {},
    onStopAutoPage: () -> Unit = {},
    onRetry: () -> Unit = {}
) {
    ReaderTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ReaderTheme.colors.paperBg)
                .semantics { contentDescription = "ReaderShell 自动翻页" }
        ) {
            AutoPageReadingSurface(state.reading)
            AutoPageOverlay(
                state = state,
                onClose = onClose,
                onMore = onMore,
                modifier = Modifier.semantics { contentDescription = "readerOverlayHost" }
            )
            AutoPageBottomSheet(
                state = state,
                onClose = onClose,
                onSpeedChange = onSpeedChange,
                onModeChange = onModeChange,
                onToggleOption = onToggleOption,
                onStartAutoPage = onStartAutoPage,
                onPauseAutoPage = onPauseAutoPage,
                onContinueAutoPage = onContinueAutoPage,
                onStopAutoPage = onStopAutoPage,
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
private fun AutoPageReadingSurface(reading: ReaderShellTextUiModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ReaderTheme.colors.paperBg)
            .padding(start = 28.dp, end = 28.dp, top = 112.dp, bottom = 360.dp)
            .semantics { contentDescription = "readingSurface" }
    ) {
        reading.paragraphs.forEach { paragraph ->
            Text(paragraph, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.readerBody)
            Spacer(modifier = Modifier.height(ReaderTheme.spacing.md))
        }
    }
}

@Composable
private fun AutoPageOverlay(
    state: AutoPageUiState,
    onClose: () -> Unit,
    onMore: () -> Unit,
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
                contentDescription = "关闭",
                onClick = onClose
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    state.topControl.title,
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
            ReaderIconButton(
                icon = ReaderIconToken.More.asImageVector(),
                contentDescription = "更多",
                onClick = onMore
            )
        }
    }
}

@Composable
private fun AutoPageBottomSheet(
    state: AutoPageUiState,
    onClose: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    onModeChange: (AutoPageModeUiModel) -> Unit,
    onToggleOption: (AutoPageOptionUiModel, Boolean) -> Unit,
    onStartAutoPage: () -> Unit,
    onPauseAutoPage: () -> Unit,
    onContinueAutoPage: () -> Unit,
    onStopAutoPage: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 264.dp, max = 436.dp)
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
        when (state.displayState) {
            AutoPageDisplayState.Default -> AutoPageSettingsPanel(
                state = state,
                onSpeedChange = onSpeedChange,
                onModeChange = onModeChange,
                onToggleOption = onToggleOption,
                onStartAutoPage = onStartAutoPage,
                onClose = onClose
            )
            AutoPageDisplayState.Running,
            AutoPageDisplayState.Paused -> AutoPageRunningPanel(
                state = state,
                onSpeedChange = onSpeedChange,
                onPauseAutoPage = onPauseAutoPage,
                onContinueAutoPage = onContinueAutoPage,
                onStopAutoPage = onStopAutoPage
            )
            AutoPageDisplayState.Error -> AutoPageFeedbackPanel(state.error, onRetry)
        }
    }
}

@Composable
private fun AutoPageSettingsPanel(
    state: AutoPageUiState,
    onSpeedChange: (Float) -> Unit,
    onModeChange: (AutoPageModeUiModel) -> Unit,
    onToggleOption: (AutoPageOptionUiModel, Boolean) -> Unit,
    onStartAutoPage: () -> Unit,
    onClose: () -> Unit
) {
    AutoPageSpeedCard(state.speed, onSpeedChange)
    AutoPageModeCard(state.modes, onModeChange)
    AutoPageOptionsCard(state.optionsTitle, state.options, onToggleOption)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)
    ) {
        ReaderSecondaryButton(
            text = state.actions.cancelLabel,
            onClick = onClose,
            modifier = Modifier.weight(1f)
        )
        ReaderPrimaryButton(
            text = state.actions.startLabel,
            onClick = onStartAutoPage,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun AutoPageRunningPanel(
    state: AutoPageUiState,
    onSpeedChange: (Float) -> Unit,
    onPauseAutoPage: () -> Unit,
    onContinueAutoPage: () -> Unit,
    onStopAutoPage: () -> Unit
) {
    AutoPageRunningCapsule(
        state = state,
        onPauseAutoPage = onPauseAutoPage,
        onContinueAutoPage = onContinueAutoPage,
        onStopAutoPage = onStopAutoPage
    )
    AutoPageSpeedCard(state.speed, onSpeedChange)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)
    ) {
        ReaderSecondaryButton(
            text = state.actions.cancelLabel,
            onClick = onStopAutoPage,
            modifier = Modifier.weight(1f)
        )
        ReaderSecondaryButton(
            text = state.actions.stopLabel,
            onClick = onStopAutoPage,
            modifier = Modifier.weight(1f),
            contentDescription = "停止自动翻页"
        )
    }
}

@Composable
private fun AutoPageSpeedCard(
    speed: AutoPageSpeedUiModel,
    onSpeedChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ReaderTheme.shapes.small)
            .background(ReaderTheme.colors.metaBg)
            .semantics { contentDescription = "${speed.title}，${speed.valueLabel}" }
            .padding(ReaderTheme.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.xs)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(speed.title, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.sectionTitle)
            Text(speed.valueLabel, color = ReaderTheme.colors.primary, style = ReaderTheme.typography.readerControlLabel)
        }
        ReaderSlider(value = speed.value, onValueChange = onSpeedChange)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(speed.slowLabel, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.readerSmallMeta)
            Text(speed.fastLabel, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.readerSmallMeta)
        }
    }
}

@Composable
private fun AutoPageModeCard(
    modes: List<AutoPageModeUiModel>,
    onModeChange: (AutoPageModeUiModel) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ReaderTheme.shapes.small)
            .background(ReaderTheme.colors.metaBg)
            .padding(ReaderTheme.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.xs)
    ) {
        Text("翻页模式", color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.sectionTitle)
        Row(horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.xs)) {
            modes.forEach { mode ->
                ReaderChip(
                    text = mode.label,
                    selected = mode.active,
                    onClick = { onModeChange(mode) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun AutoPageOptionsCard(
    title: String,
    options: List<AutoPageOptionUiModel>,
    onToggleOption: (AutoPageOptionUiModel, Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ReaderTheme.shapes.small)
            .background(ReaderTheme.colors.metaBg)
            .padding(ReaderTheme.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.xs)
    ) {
        Text(title, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.sectionTitle)
        options.forEach { option ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 46.dp)
                    .semantics { contentDescription = "${option.title}，${if (option.enabled) "已开启" else "已关闭"}" },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)
            ) {
                Icon(
                    imageVector = option.icon.toAutoPageIcon().asImageVector(),
                    contentDescription = null,
                    tint = ReaderTheme.colors.primary,
                    modifier = Modifier.size(20.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(option.title, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.bookTitle)
                    Text(option.meta, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.bookMeta)
                }
                ReaderSwitch(
                    checked = option.enabled,
                    onCheckedChange = { onToggleOption(option, it) }
                )
            }
        }
    }
}

@Composable
private fun AutoPageRunningCapsule(
    state: AutoPageUiState,
    onPauseAutoPage: () -> Unit,
    onContinueAutoPage: () -> Unit,
    onStopAutoPage: () -> Unit
) {
    val paused = state.isPaused
    val title = if (paused) state.runningCapsule.pausedTitle else state.runningCapsule.title
    val primaryLabel = if (paused) state.runningCapsule.continueLabel else state.runningCapsule.actionLabel
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ReaderTheme.shapes.chip)
            .background(ReaderTheme.colors.floatingControlBgAlt)
            .semantics { contentDescription = "自动翻页运行状态，$title" }
            .padding(ReaderTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)
    ) {
        Icon(
            imageVector = if (paused) ReaderIconToken.AutoScroll.asImageVector() else ReaderIconToken.AutoScroll.asImageVector(),
            contentDescription = null,
            tint = ReaderTheme.colors.primary,
            modifier = Modifier.size(20.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.bookTitle)
            Text(state.runningCapsule.sentence, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.bookMeta)
        }
        ReaderPrimaryButton(
            text = primaryLabel,
            onClick = if (paused) onContinueAutoPage else onPauseAutoPage
        )
        ReaderSecondaryButton(
            text = state.runningCapsule.stopLabel,
            onClick = onStopAutoPage,
            contentDescription = "停止自动翻页"
        )
    }
}

@Composable
private fun AutoPageFeedbackPanel(
    feedback: ReaderShellFeedbackUiModel,
    onRetry: () -> Unit
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
            imageVector = ReaderIconToken.Refresh.asImageVector(),
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
            ReaderPrimaryButton(text = label, onClick = onRetry)
        }
    }
}

private fun String.toAutoPageIcon(): ReaderIconToken = when (this) {
    "sun" -> ReaderIconToken.AutoBrightness
    "bookmark" -> ReaderIconToken.Bookmark
    "volume" -> ReaderIconToken.Tts
    else -> ReaderIconToken.AutoScroll
}
