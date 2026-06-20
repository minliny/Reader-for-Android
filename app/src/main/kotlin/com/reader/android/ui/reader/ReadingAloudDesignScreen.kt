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
fun ReadingAloudScreen(
    state: ReadingAloudUiState = ReadingAloudMapper.fromFixture(),
    onBack: () -> Unit = {},
    onSourceChange: () -> Unit = {},
    onStartReadAloud: () -> Unit = {},
    onPauseReadAloud: () -> Unit = {},
    onContinueReadAloud: () -> Unit = {},
    onPreviousSentence: () -> Unit = {},
    onNextSentence: () -> Unit = {},
    onSpeedChange: (ReadingSpeedOptionUiModel) -> Unit = {},
    onVoiceChange: (ReadingVoiceOptionUiModel) -> Unit = {},
    onOpenReadAloudSettings: () -> Unit = {},
    onRetry: () -> Unit = {}
) {
    ReaderTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ReaderTheme.colors.paperBg)
                .semantics { contentDescription = "ReaderShell 朗读" }
        ) {
            AloudReadingSurface(state.reading)
            AloudOverlay(
                state = state,
                onBack = onBack,
                onSourceChange = onSourceChange,
                modifier = Modifier.semantics { contentDescription = "readerOverlayHost" }
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            ) {
                AloudBottomSheet(
                    state = state,
                    onStartReadAloud = onStartReadAloud,
                    onPauseReadAloud = onPauseReadAloud,
                    onContinueReadAloud = onContinueReadAloud,
                    onPreviousSentence = onPreviousSentence,
                    onNextSentence = onNextSentence,
                    onSpeedChange = onSpeedChange,
                    onVoiceChange = onVoiceChange,
                    onOpenReadAloudSettings = onOpenReadAloudSettings,
                    onRetry = onRetry,
                    modifier = Modifier.semantics { contentDescription = "bottomSheetHost" }
                )
                AloudModuleNav(
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
private fun AloudReadingSurface(reading: ReaderShellTextUiModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 28.dp, end = 28.dp, top = 108.dp, bottom = 410.dp)
            .semantics { contentDescription = "readingSurface" }
    ) {
        reading.paragraphs.forEach { paragraph ->
            Text(paragraph, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.readerBody)
            Spacer(modifier = Modifier.height(ReaderTheme.spacing.md))
        }
    }
}

@Composable
private fun AloudOverlay(
    state: ReadingAloudUiState,
    onBack: () -> Unit,
    onSourceChange: () -> Unit,
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
            ReaderIconButton(ReaderIconToken.Back.asImageVector(), "返回", onBack)
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
            ReaderIconButton(ReaderIconToken.More.asImageVector(), "更多", {})
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
private fun AloudBottomSheet(
    state: ReadingAloudUiState,
    onStartReadAloud: () -> Unit,
    onPauseReadAloud: () -> Unit,
    onContinueReadAloud: () -> Unit,
    onPreviousSentence: () -> Unit,
    onNextSentence: () -> Unit,
    onSpeedChange: (ReadingSpeedOptionUiModel) -> Unit,
    onVoiceChange: (ReadingVoiceOptionUiModel) -> Unit,
    onOpenReadAloudSettings: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 310.dp, max = 455.dp)
            .clip(ReaderTheme.shapes.bottomSheet)
            .background(ReaderTheme.colors.paperBg)
            .border(1.dp, ReaderTheme.colors.controlBorder, ReaderTheme.shapes.bottomSheet)
            .padding(ReaderTheme.spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            AloudPanelHeader(state, onOpenReadAloudSettings)
            Spacer(modifier = Modifier.height(ReaderTheme.spacing.xs))
            if (state.displayState == ReadingAloudDisplayState.Error) {
                AloudFeedbackBlock(state.error, onRetry)
            } else {
                AloudSentenceCard(state.currentSentence)
                if (state.isRunning || state.isPaused) {
                    Spacer(modifier = Modifier.height(ReaderTheme.spacing.xs))
                    AloudRunningCapsule(state, onPauseReadAloud, onContinueReadAloud, onOpenReadAloudSettings)
                }
                Spacer(modifier = Modifier.height(ReaderTheme.spacing.xs))
                AloudTransport(
                    state = state,
                    onStartReadAloud = onStartReadAloud,
                    onPauseReadAloud = onPauseReadAloud,
                    onContinueReadAloud = onContinueReadAloud,
                    onPreviousSentence = onPreviousSentence,
                    onNextSentence = onNextSentence
                )
                Spacer(modifier = Modifier.height(ReaderTheme.spacing.xs))
                AloudSpeedControl(state.speedOptions, onSpeedChange)
                Spacer(modifier = Modifier.height(ReaderTheme.spacing.xs))
                AloudVoiceOptions(state.voices, onVoiceChange)
                Spacer(modifier = Modifier.height(ReaderTheme.spacing.xs))
                AloudSettingGrid(state.controls)
            }
        }
        AloudBrightnessPanel(state.brightness)
    }
}

@Composable
private fun AloudPanelHeader(
    state: ReadingAloudUiState,
    onOpenReadAloudSettings: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(state.panel.title, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.pageTitle)
            Text(state.panel.subtitle, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.bookMeta)
        }
        ReaderSecondaryButton(text = state.panel.settingsLabel, onClick = onOpenReadAloudSettings)
    }
}

@Composable
private fun AloudSentenceCard(sentence: ReadingAloudSentenceUiModel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ReaderTheme.shapes.small)
            .background(ReaderTheme.colors.metaBg)
            .semantics { contentDescription = "当前朗读句，${sentence.progressLabel}" }
            .padding(ReaderTheme.spacing.sm)
    ) {
        Text(sentence.text, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.bookTitle)
        Text("${sentence.label} · ${sentence.progressLabel}", color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.bookMeta)
    }
}

@Composable
private fun AloudRunningCapsule(
    state: ReadingAloudUiState,
    onPauseReadAloud: () -> Unit,
    onContinueReadAloud: () -> Unit,
    onOpenReadAloudSettings: () -> Unit
) {
    val title = if (state.isPaused) state.runningCapsule.pausedTitle else state.runningCapsule.title
    val action = if (state.isPaused) state.runningCapsule.continueLabel else state.runningCapsule.actionLabel
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ReaderTheme.shapes.chip)
            .background(ReaderTheme.colors.floatingControlBgAlt)
            .semantics { contentDescription = "朗读运行状态，$title" }
            .padding(ReaderTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)
    ) {
        Icon(
            imageVector = if (state.isPaused) ReaderIconToken.Tts.asImageVector() else ReaderIconToken.AutoScroll.asImageVector(),
            contentDescription = null,
            tint = ReaderTheme.colors.primary,
            modifier = Modifier.size(20.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.bookTitle)
            Text(state.runningCapsule.sentence, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.bookMeta)
        }
        ReaderPrimaryButton(text = action, onClick = if (state.isPaused) onContinueReadAloud else onPauseReadAloud)
        ReaderSecondaryButton(text = state.runningCapsule.settingsLabel, onClick = onOpenReadAloudSettings)
    }
}

@Composable
private fun AloudTransport(
    state: ReadingAloudUiState,
    onStartReadAloud: () -> Unit,
    onPauseReadAloud: () -> Unit,
    onContinueReadAloud: () -> Unit,
    onPreviousSentence: () -> Unit,
    onNextSentence: () -> Unit
) {
    val label = when (state.displayState) {
        ReadingAloudDisplayState.Running -> state.panel.pauseLabel
        ReadingAloudDisplayState.Paused -> state.panel.continueLabel
        else -> state.panel.startLabel
    }
    val onPrimary = when (state.displayState) {
        ReadingAloudDisplayState.Running -> onPauseReadAloud
        ReadingAloudDisplayState.Paused -> onContinueReadAloud
        else -> onStartReadAloud
    }
    Row(horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)) {
        ReaderSecondaryButton(text = state.panel.previousLabel, onClick = onPreviousSentence, modifier = Modifier.weight(1f))
        ReaderPrimaryButton(text = label, onClick = onPrimary, modifier = Modifier.weight(1f))
        ReaderSecondaryButton(text = state.panel.nextLabel, onClick = onNextSentence, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun AloudSpeedControl(
    options: List<ReadingSpeedOptionUiModel>,
    onSpeedChange: (ReadingSpeedOptionUiModel) -> Unit
) {
    Column {
        Text("语速", color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.sectionTitle)
        Spacer(modifier = Modifier.height(ReaderTheme.spacing.xs))
        Row(horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.xs)) {
            options.forEach { option ->
                ReaderChip(
                    text = option.label,
                    selected = option.active,
                    onClick = { onSpeedChange(option) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun AloudVoiceOptions(
    voices: List<ReadingVoiceOptionUiModel>,
    onVoiceChange: (ReadingVoiceOptionUiModel) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.xs)) {
        voices.forEach { voice ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(ReaderTheme.shapes.small)
                    .background(if (voice.active) ReaderTheme.colors.floatingControlBgAlt else ReaderTheme.colors.metaBg)
                    .clickable(role = Role.Button) { onVoiceChange(voice) }
                    .semantics { contentDescription = "声音，${voice.label}" }
                    .padding(ReaderTheme.spacing.xs),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(voice.label, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.bookMeta)
                Text(voice.meta, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.readerSmallMeta)
            }
        }
    }
}

@Composable
private fun AloudSettingGrid(controls: ReadingAloudControlsUiModel) {
    val items = listOf(controls.speed, controls.voice, controls.range, controls.timer)
    Column(verticalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.xs)) {
        items.chunked(2).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.xs)) {
                rowItems.forEach { item ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(ReaderTheme.shapes.small)
                            .background(ReaderTheme.colors.metaBg)
                            .padding(ReaderTheme.spacing.sm)
                    ) {
                        Text(item.label, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.bookMeta)
                        Text(item.value, color = ReaderTheme.colors.primary, style = ReaderTheme.typography.readerControlLabel)
                    }
                }
            }
        }
    }
}

@Composable
private fun AloudFeedbackBlock(
    feedback: ReaderShellFeedbackUiModel,
    onPrimary: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 220.dp, max = 300.dp)
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
private fun AloudBrightnessPanel(brightness: ReadingBrightnessUiModel) {
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
private fun AloudModuleNav(
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
