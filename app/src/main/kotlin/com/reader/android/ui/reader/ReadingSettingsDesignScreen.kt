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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.reader.android.ui.components.ReaderChip
import com.reader.android.ui.components.ReaderIconButton
import com.reader.android.ui.components.ReaderIconToken
import com.reader.android.ui.components.ReaderPrimaryButton
import com.reader.android.ui.components.ReaderSecondaryButton
import com.reader.android.ui.components.ReaderSwitch
import com.reader.android.ui.components.asImageVector
import com.reader.android.ui.theme.ReaderTheme

@Composable
fun ReadingSettingsScreen(
    state: ReadingSettingsUiState = ReadingSettingsMapper.fromFixture(),
    onBack: () -> Unit = {},
    onOpenPreset: () -> Unit = {},
    onOpenGroup: (ReadingSettingsGroupUiModel) -> Unit = {},
    onToggleSetting: (ReadingSettingsSwitchUiModel, Boolean) -> Unit = { _, _ -> },
    onSegmentChange: (ReadingSettingsSubRowUiModel, String) -> Unit = { _, _ -> },
    onStepperChange: (ReadingSettingsSubRowUiModel, Int) -> Unit = { _, _ -> },
    onPresetApply: (ReadingSettingsPresetUiModel) -> Unit = {},
    onSubPresetApply: (ReadingSettingsSubRowUiModel) -> Unit = {},
    onRestoreDefault: () -> Unit = {},
    onRetry: () -> Unit = {}
) {
    ReaderTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ReaderTheme.colors.paperBg)
                .semantics { contentDescription = "ReaderShell 阅读设置" }
        ) {
            ReadingSettingsSurface()
            ReadingSettingsOverlay(
                state = state,
                onBack = onBack,
                onOpenPreset = onOpenPreset,
                onOpenGroup = onOpenGroup,
                onToggleSetting = onToggleSetting,
                onSegmentChange = onSegmentChange,
                onStepperChange = onStepperChange,
                onPresetApply = onPresetApply,
                onSubPresetApply = onSubPresetApply,
                onRestoreDefault = onRestoreDefault,
                onRetry = onRetry,
                modifier = Modifier.semantics { contentDescription = "readerOverlayHost" }
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
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
private fun ReadingSettingsSurface() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ReaderTheme.colors.paperBg)
            .semantics { contentDescription = "readingSurface" }
    )
}

@Composable
private fun ReadingSettingsOverlay(
    state: ReadingSettingsUiState,
    onBack: () -> Unit,
    onOpenPreset: () -> Unit,
    onOpenGroup: (ReadingSettingsGroupUiModel) -> Unit,
    onToggleSetting: (ReadingSettingsSwitchUiModel, Boolean) -> Unit,
    onSegmentChange: (ReadingSettingsSubRowUiModel, String) -> Unit,
    onStepperChange: (ReadingSettingsSubRowUiModel, Int) -> Unit,
    onPresetApply: (ReadingSettingsPresetUiModel) -> Unit,
    onSubPresetApply: (ReadingSettingsSubRowUiModel) -> Unit,
    onRestoreDefault: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ReaderTheme.colors.paperBg)
    ) {
        ReadingSettingsTopBar(state, onBack, onOpenPreset)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(ReaderTheme.spacing.screenPadding),
            verticalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)
        ) {
            when (state.displayState) {
                ReadingSettingsDisplayState.Loading -> ReadingSettingsFeedback(state.loading, loading = true, onRetry = onRetry)
                ReadingSettingsDisplayState.Error -> ReadingSettingsFeedback(state.error, loading = false, onRetry = onRetry)
                ReadingSettingsDisplayState.Subpage -> ReadingSettingsSubpage(
                    subpage = state.subpage,
                    onSegmentChange = onSegmentChange,
                    onStepperChange = onStepperChange,
                    onToggleSetting = onToggleSetting,
                    onSubPresetApply = onSubPresetApply
                )
                ReadingSettingsDisplayState.Default -> ReadingSettingsHome(
                    state = state,
                    onPresetApply = onPresetApply,
                    onOpenGroup = onOpenGroup,
                    onToggleSetting = onToggleSetting,
                    onRestoreDefault = onRestoreDefault
                )
            }
        }
    }
}

@Composable
private fun ReadingSettingsTopBar(
    state: ReadingSettingsUiState,
    onBack: () -> Unit,
    onOpenPreset: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ReaderTheme.colors.softTopBg)
            .padding(horizontal = ReaderTheme.spacing.sm, vertical = ReaderTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.xs)
    ) {
        ReaderIconButton(
            icon = ReaderIconToken.Back.asImageVector(),
            contentDescription = "返回",
            onClick = onBack
        )
        Text(
            text = state.currentTitle,
            modifier = Modifier.weight(1f),
            color = ReaderTheme.colors.controlInk,
            style = ReaderTheme.typography.pageTitle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        ReaderSecondaryButton(
            text = state.topBar.presetLabel,
            onClick = onOpenPreset,
            contentDescription = "打开${state.topBar.presetLabel}"
        )
    }
}

@Composable
private fun ReadingSettingsHome(
    state: ReadingSettingsUiState,
    onPresetApply: (ReadingSettingsPresetUiModel) -> Unit,
    onOpenGroup: (ReadingSettingsGroupUiModel) -> Unit,
    onToggleSetting: (ReadingSettingsSwitchUiModel, Boolean) -> Unit,
    onRestoreDefault: () -> Unit
) {
    ReadingSettingsQuickPresets(state.quickPresets, onPresetApply)
    ReadingSettingsGroupList(state.groups, onOpenGroup)
    ReadingSettingsAdvancedList(state.advancedTitle, state.advanced, onToggleSetting)
    ReadingSettingsRestoreRow(state.restore, onRestoreDefault)
}

@Composable
private fun ReadingSettingsQuickPresets(
    presets: List<ReadingSettingsPresetUiModel>,
    onPresetApply: (ReadingSettingsPresetUiModel) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.xs)) {
        Text("预设管理", color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.sectionTitle)
        Row(horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.xs)) {
            presets.forEach { preset ->
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 104.dp)
                        .clip(ReaderTheme.shapes.small)
                        .background(ReaderTheme.colors.metaBg)
                        .clickable(role = Role.Button) { onPresetApply(preset) }
                        .semantics { contentDescription = "预设，${preset.title}，${preset.value}" }
                        .padding(ReaderTheme.spacing.sm),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Icon(
                        imageVector = preset.icon.toReadingSettingsIcon().asImageVector(),
                        contentDescription = null,
                        tint = ReaderTheme.colors.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(preset.title, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.bookTitle)
                    Text(preset.value, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.bookMeta)
                }
            }
        }
    }
}

@Composable
private fun ReadingSettingsGroupList(
    groups: List<ReadingSettingsGroupUiModel>,
    onOpenGroup: (ReadingSettingsGroupUiModel) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ReaderTheme.shapes.small)
            .background(ReaderTheme.colors.metaBg)
            .semantics { contentDescription = "设置分组" }
    ) {
        groups.forEach { group ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .clickable(role = Role.Button) { onOpenGroup(group) }
                    .padding(horizontal = ReaderTheme.spacing.sm, vertical = ReaderTheme.spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(ReaderTheme.shapes.quickCircle)
                        .background(ReaderTheme.colors.floatingControlBgAlt),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = group.icon.toReadingSettingsIcon().asImageVector(),
                        contentDescription = null,
                        tint = ReaderTheme.colors.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(group.title, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.bookTitle)
                    Text(group.meta, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.bookMeta)
                }
                Icon(
                    imageVector = ReaderIconToken.Chevron.asImageVector(),
                    contentDescription = null,
                    tint = ReaderTheme.colors.bodyText,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun ReadingSettingsAdvancedList(
    title: String,
    items: List<ReadingSettingsSwitchUiModel>,
    onToggleSetting: (ReadingSettingsSwitchUiModel, Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.xs)) {
        Text(title, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.sectionTitle)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ReaderTheme.shapes.small)
                .background(ReaderTheme.colors.metaBg)
        ) {
            items.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 58.dp)
                        .semantics { contentDescription = "${item.title}，${if (item.enabled) "已开启" else "已关闭"}" }
                        .padding(horizontal = ReaderTheme.spacing.sm, vertical = ReaderTheme.spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.title, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.bookTitle)
                        Text(item.meta, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.bookMeta)
                    }
                    ReaderSwitch(
                        checked = item.enabled,
                        onCheckedChange = { onToggleSetting(item, it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ReadingSettingsRestoreRow(
    restore: ReadingSettingsRestoreUiModel,
    onRestoreDefault: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ReaderTheme.shapes.small)
            .border(1.dp, ReaderTheme.colors.controlBorder, ReaderTheme.shapes.small)
            .clickable(role = Role.Button, onClick = onRestoreDefault)
            .semantics { contentDescription = restore.title }
            .padding(ReaderTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(restore.title, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.bookTitle)
            Text(restore.copy, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.bookMeta)
        }
        Icon(
            imageVector = ReaderIconToken.Chevron.asImageVector(),
            contentDescription = null,
            tint = ReaderTheme.colors.bodyText,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun ReadingSettingsSubpage(
    subpage: ReadingSettingsSubpageUiModel,
    onSegmentChange: (ReadingSettingsSubRowUiModel, String) -> Unit,
    onStepperChange: (ReadingSettingsSubRowUiModel, Int) -> Unit,
    onToggleSetting: (ReadingSettingsSwitchUiModel, Boolean) -> Unit,
    onSubPresetApply: (ReadingSettingsSubRowUiModel) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ReaderTheme.shapes.small)
            .background(ReaderTheme.colors.metaBg)
            .semantics { contentDescription = "阅读设置二级页，${subpage.title}" }
            .padding(ReaderTheme.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)
    ) {
        Column {
            Text(subpage.title, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.pageTitle)
            Text(subpage.subtitle, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.bookMeta)
        }
        subpage.sections.forEach { section ->
            Column(verticalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.xs)) {
                Text(section.title, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.sectionTitle)
                section.rows.forEach { row ->
                    ReadingSettingsSubRow(
                        row = row,
                        onSegmentChange = onSegmentChange,
                        onStepperChange = onStepperChange,
                        onToggleSetting = onToggleSetting,
                        onSubPresetApply = onSubPresetApply
                    )
                }
            }
        }
    }
}

@Composable
private fun ReadingSettingsSubRow(
    row: ReadingSettingsSubRowUiModel,
    onSegmentChange: (ReadingSettingsSubRowUiModel, String) -> Unit,
    onStepperChange: (ReadingSettingsSubRowUiModel, Int) -> Unit,
    onToggleSetting: (ReadingSettingsSwitchUiModel, Boolean) -> Unit,
    onSubPresetApply: (ReadingSettingsSubRowUiModel) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ReaderTheme.shapes.small)
            .background(ReaderTheme.colors.paperBg)
            .clickable(enabled = row.controlType == ReadingSettingsControlType.Preset, role = Role.Button) {
                onSubPresetApply(row)
            }
            .padding(ReaderTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(row.title, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.bookTitle)
            Text(row.meta, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.bookMeta)
        }
        when (row.controlType) {
            ReadingSettingsControlType.Segment -> ReadingSettingsSegment(row, onSegmentChange)
            ReadingSettingsControlType.Switch -> ReaderSwitch(
                checked = row.enabled,
                onCheckedChange = {
                    onToggleSetting(ReadingSettingsSwitchUiModel(row.title, row.meta, row.enabled), it)
                }
            )
            ReadingSettingsControlType.Stepper -> ReadingSettingsStepper(row, onStepperChange)
            ReadingSettingsControlType.Preset -> ReadingSettingsPresetState(row.active)
        }
    }
}

@Composable
private fun ReadingSettingsSegment(
    row: ReadingSettingsSubRowUiModel,
    onSegmentChange: (ReadingSettingsSubRowUiModel, String) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.xs)) {
        row.options.forEach { option ->
            ReaderChip(
                text = option,
                selected = option == row.activeOption,
                onClick = { onSegmentChange(row, option) }
            )
        }
    }
}

@Composable
private fun ReadingSettingsStepper(
    row: ReadingSettingsSubRowUiModel,
    onStepperChange: (ReadingSettingsSubRowUiModel, Int) -> Unit
) {
    Row(
        modifier = Modifier
            .clip(ReaderTheme.shapes.chip)
            .background(ReaderTheme.colors.floatingControlBg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ReaderStepperButton("-", "减少${row.title}") { onStepperChange(row, row.value - 1) }
        Text(
            "${row.value}",
            modifier = Modifier.width(34.dp),
            color = ReaderTheme.colors.controlInk,
            style = ReaderTheme.typography.readerControlLabel
        )
        ReaderStepperButton("+", "增加${row.title}") { onStepperChange(row, row.value + 1) }
    }
}

@Composable
private fun ReaderStepperButton(
    label: String,
    contentDescription: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = ReaderTheme.colors.primary, style = ReaderTheme.typography.readerControlLabel)
    }
}

@Composable
private fun ReadingSettingsPresetState(active: Boolean) {
    Text(
        text = if (active) "当前" else "应用",
        modifier = Modifier
            .clip(ReaderTheme.shapes.chip)
            .background(if (active) ReaderTheme.colors.primary.copy(alpha = 0.16f) else ReaderTheme.colors.floatingControlBg)
            .padding(horizontal = ReaderTheme.spacing.sm, vertical = ReaderTheme.spacing.xs),
        color = if (active) ReaderTheme.colors.primary else ReaderTheme.colors.controlInk,
        style = ReaderTheme.typography.readerControlLabel
    )
}

@Composable
private fun ReadingSettingsFeedback(
    feedback: ReaderShellFeedbackUiModel,
    loading: Boolean,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 420.dp)
            .clip(ReaderTheme.shapes.small)
            .background(ReaderTheme.colors.metaBg)
            .semantics { contentDescription = feedback.title }
            .padding(ReaderTheme.spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = if (loading) ReaderIconToken.ReadingSettings.asImageVector() else ReaderIconToken.Refresh.asImageVector(),
            contentDescription = null,
            tint = ReaderTheme.colors.primary,
            modifier = Modifier.size(34.dp)
        )
        Spacer(modifier = Modifier.height(ReaderTheme.spacing.sm))
        Text(feedback.title, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.pageTitle)
        Spacer(modifier = Modifier.height(ReaderTheme.spacing.xs))
        Text(feedback.message, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.stateMessage)
        if (loading) {
            Spacer(modifier = Modifier.height(ReaderTheme.spacing.md))
            repeat(6) { index ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth(if (index % 2 == 0) 0.82f else 0.64f)
                        .height(10.dp)
                        .clip(ReaderTheme.shapes.chip)
                        .background(ReaderTheme.colors.mutedTrack)
                )
                Spacer(modifier = Modifier.height(ReaderTheme.spacing.xs))
            }
        } else {
            feedback.primaryAction?.let { label ->
                Spacer(modifier = Modifier.height(ReaderTheme.spacing.md))
                ReaderPrimaryButton(text = label, onClick = onRetry)
            }
        }
    }
}

private fun String.toReadingSettingsIcon(): ReaderIconToken = when (this) {
    "phone", "monitor" -> ReaderIconToken.Appearance
    "book" -> ReaderIconToken.Directory
    "sun" -> ReaderIconToken.AutoBrightness
    "gesture" -> ReaderIconToken.AutoScroll
    "assist" -> ReaderIconToken.Tts
    "progress" -> ReaderIconToken.CurrentLocation
    else -> ReaderIconToken.ReadingSettings
}
