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
fun ReadingAppearanceScreen(
    state: ReadingAppearanceUiState = ReadingAppearanceMapper.fromFixture(),
    onBack: () -> Unit = {},
    onSourceChange: () -> Unit = {},
    onFontSizeDecrease: () -> Unit = {},
    onFontSizeIncrease: () -> Unit = {},
    onLineSpacingChange: (ReadingSegmentOptionUiModel) -> Unit = {},
    onThemeChange: (ReadingThemeSwatchUiModel) -> Unit = {},
    onFontChange: (ReadingFontOptionUiModel) -> Unit = {},
    onAnimationChange: (ReadingSegmentOptionUiModel) -> Unit = {},
    onEditThemeOpen: () -> Unit = {},
    onResetAppearance: () -> Unit = {},
    onSaveTheme: () -> Unit = {},
    onRetry: () -> Unit = {}
) {
    ReaderTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ReaderTheme.colors.paperBg)
                .semantics { contentDescription = "ReaderShell 阅读外观" }
        ) {
            AppearanceReadingSurface(state.reading)
            AppearanceOverlay(
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
                AppearanceBottomSheet(
                    state = state,
                    onFontSizeDecrease = onFontSizeDecrease,
                    onFontSizeIncrease = onFontSizeIncrease,
                    onLineSpacingChange = onLineSpacingChange,
                    onThemeChange = onThemeChange,
                    onFontChange = onFontChange,
                    onAnimationChange = onAnimationChange,
                    onEditThemeOpen = onEditThemeOpen,
                    onResetAppearance = onResetAppearance,
                    onSaveTheme = onSaveTheme,
                    onRetry = onRetry,
                    modifier = Modifier.semantics { contentDescription = "bottomSheetHost" }
                )
                AppearanceModuleNav(
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
private fun AppearanceReadingSurface(reading: ReaderShellTextUiModel) {
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
private fun AppearanceOverlay(
    state: ReadingAppearanceUiState,
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
            ReaderIconButton(icon = ReaderIconToken.More.asImageVector(), contentDescription = "更多", onClick = {})
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
private fun AppearanceBottomSheet(
    state: ReadingAppearanceUiState,
    onFontSizeDecrease: () -> Unit,
    onFontSizeIncrease: () -> Unit,
    onLineSpacingChange: (ReadingSegmentOptionUiModel) -> Unit,
    onThemeChange: (ReadingThemeSwatchUiModel) -> Unit,
    onFontChange: (ReadingFontOptionUiModel) -> Unit,
    onAnimationChange: (ReadingSegmentOptionUiModel) -> Unit,
    onEditThemeOpen: () -> Unit,
    onResetAppearance: () -> Unit,
    onSaveTheme: () -> Unit,
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
            AppearancePanelHeader(state)
            Spacer(modifier = Modifier.height(ReaderTheme.spacing.xs))
            when (state.displayState) {
                ReadingAppearanceDisplayState.Loading -> AppearanceFeedbackBlock(state.loading, onPrimary = onRetry)
                ReadingAppearanceDisplayState.Error -> AppearanceFeedbackBlock(state.error, onPrimary = onRetry)
                ReadingAppearanceDisplayState.Font -> AppearanceFontPanel(state = state, onFontChange = onFontChange)
                ReadingAppearanceDisplayState.Theme -> AppearanceThemePanel(
                    state = state,
                    onThemeChange = onThemeChange,
                    onAnimationChange = onAnimationChange
                )
                ReadingAppearanceDisplayState.Edit -> AppearanceEditThemePanel(
                    state = state,
                    onResetAppearance = onResetAppearance,
                    onSaveTheme = onSaveTheme
                )
                ReadingAppearanceDisplayState.Default -> AppearanceDefaultPanel(
                    state = state,
                    onFontSizeDecrease = onFontSizeDecrease,
                    onFontSizeIncrease = onFontSizeIncrease,
                    onLineSpacingChange = onLineSpacingChange,
                    onThemeChange = onThemeChange,
                    onEditThemeOpen = onEditThemeOpen,
                    onResetAppearance = onResetAppearance
                )
            }
        }
        AppearanceBrightnessPanel(state.brightness)
    }
}

@Composable
private fun AppearancePanelHeader(state: ReadingAppearanceUiState) {
    val title = when (state.displayState) {
        ReadingAppearanceDisplayState.Font -> state.panel.fontTitle
        ReadingAppearanceDisplayState.Theme -> "主题"
        ReadingAppearanceDisplayState.Edit -> state.editTheme.title
        else -> state.panel.title
    }
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.pageTitle)
            Text(state.panel.subtitle, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.bookMeta)
        }
        ReaderSecondaryButton(text = state.panel.moreLabel, onClick = {})
    }
}

@Composable
private fun AppearanceDefaultPanel(
    state: ReadingAppearanceUiState,
    onFontSizeDecrease: () -> Unit,
    onFontSizeIncrease: () -> Unit,
    onLineSpacingChange: (ReadingSegmentOptionUiModel) -> Unit,
    onThemeChange: (ReadingThemeSwatchUiModel) -> Unit,
    onEditThemeOpen: () -> Unit,
    onResetAppearance: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)) {
        AppearanceFontStepper(state.fontSize, onFontSizeDecrease, onFontSizeIncrease)
        AppearanceSegmentRow(state.lineSpacing.title, state.lineSpacing.options, onLineSpacingChange)
        AppearanceThemeSwatches(state.themes, onThemeChange)
        AppearancePreviewCards(state)
        Row(horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)) {
            ReaderSecondaryButton(text = state.panel.resetLabel, onClick = onResetAppearance, modifier = Modifier.weight(1f))
            ReaderPrimaryButton(text = state.editTheme.title, onClick = onEditThemeOpen, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun AppearanceFontStepper(
    fontSize: ReadingFontSizeUiModel,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)
    ) {
        Text(fontSize.title, modifier = Modifier.weight(1f), color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.sectionTitle)
        ReaderSecondaryButton(text = fontSize.minLabel, onClick = onDecrease)
        Text("${fontSize.value}", color = ReaderTheme.colors.primary, style = ReaderTheme.typography.pageTitle)
        ReaderSecondaryButton(text = fontSize.plusLabel, onClick = onIncrease)
    }
}

@Composable
private fun <T> AppearanceSegmentRow(
    title: String,
    options: List<T>,
    onSelected: (T) -> Unit
) where T : Any {
    Column {
        Text(title, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.sectionTitle)
        Spacer(modifier = Modifier.height(ReaderTheme.spacing.xs))
        Row(horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.xs)) {
            options.forEach { option ->
                val label = when (option) {
                    is ReadingSegmentOptionUiModel -> option.label
                    else -> option.toString()
                }
                val active = option is ReadingSegmentOptionUiModel && option.active
                ReaderChip(
                    text = label,
                    selected = active,
                    onClick = { onSelected(option) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun AppearanceThemeSwatches(
    themes: List<ReadingThemeSwatchUiModel>,
    onThemeChange: (ReadingThemeSwatchUiModel) -> Unit
) {
    Column {
        Text("主题 / 背景", color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.sectionTitle)
        Spacer(modifier = Modifier.height(ReaderTheme.spacing.xs))
        Row(horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.xs)) {
            themes.forEach { theme ->
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(ReaderTheme.shapes.small)
                        .background(if (theme.active) ReaderTheme.colors.floatingControlBgAlt else ReaderTheme.colors.metaBg)
                        .border(1.dp, ReaderTheme.colors.controlBorder, ReaderTheme.shapes.small)
                        .clickable(role = Role.Button) { onThemeChange(theme) }
                        .semantics { contentDescription = "主题，${theme.label}" }
                        .padding(ReaderTheme.spacing.xs),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(ReaderTheme.shapes.quickCircle)
                            .background(if (theme.active) ReaderTheme.colors.primary else ReaderTheme.colors.floatingControlBg)
                    )
                    Text(theme.label, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.readerSmallMeta)
                }
            }
        }
    }
}

@Composable
private fun AppearancePreviewCards(state: ReadingAppearanceUiState) {
    Row(horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)) {
        AppearancePreviewCard(
            title = state.panel.fontTitle,
            value = state.activeFont.label,
            meta = state.panel.fontPreviewLabel,
            modifier = Modifier.weight(1f)
        )
        AppearancePreviewCard(
            title = state.panel.animationTitle,
            value = state.activeAnimation.label,
            meta = state.panel.animationPreview,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun AppearancePreviewCard(
    title: String,
    value: String,
    meta: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(ReaderTheme.shapes.small)
            .background(ReaderTheme.colors.metaBg)
            .padding(ReaderTheme.spacing.sm)
    ) {
        Text(title, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.bookTitle)
        Text(value, color = ReaderTheme.colors.primary, style = ReaderTheme.typography.bookMeta)
        Text(meta, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.readerSmallMeta)
    }
}

@Composable
private fun AppearanceFontPanel(
    state: ReadingAppearanceUiState,
    onFontChange: (ReadingFontOptionUiModel) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.xs)) {
        state.fonts.forEach { font ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(ReaderTheme.shapes.small)
                    .background(if (font.active) ReaderTheme.colors.floatingControlBgAlt else ReaderTheme.colors.metaBg)
                    .clickable(role = Role.Button) { onFontChange(font) }
                    .semantics { contentDescription = "字体，${font.label}" }
                    .padding(ReaderTheme.spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(font.label, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.bookTitle)
                    Text(font.meta, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.bookMeta)
                }
                Text(font.preview, color = ReaderTheme.colors.primary, style = ReaderTheme.typography.readerSmallMeta)
            }
        }
        AppearancePreviewCards(state)
    }
}

@Composable
private fun AppearanceThemePanel(
    state: ReadingAppearanceUiState,
    onThemeChange: (ReadingThemeSwatchUiModel) -> Unit,
    onAnimationChange: (ReadingSegmentOptionUiModel) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)) {
        AppearanceThemeSwatches(state.themes, onThemeChange)
        AppearanceSegmentRow(state.panel.animationTitle, state.animations, onAnimationChange)
        AppearancePreviewCards(state)
    }
}

@Composable
private fun AppearanceEditThemePanel(
    state: ReadingAppearanceUiState,
    onResetAppearance: () -> Unit,
    onSaveTheme: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)) {
        Text(state.editTheme.backgroundLabel, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.sectionTitle)
        Row(horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.xs)) {
            state.editTheme.colors.forEach { color ->
                ReaderChip(text = color.label, selected = color.active, modifier = Modifier.weight(1f))
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ReaderTheme.shapes.small)
                .background(ReaderTheme.colors.metaBg)
                .semantics { contentDescription = state.editTheme.previewTitle }
                .padding(ReaderTheme.spacing.sm)
        ) {
            Text(state.editTheme.previewTitle, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.bookTitle)
            Text(state.editTheme.previewCopy, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.stateMessage)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)) {
            ReaderSecondaryButton(text = state.panel.resetLabel, onClick = onResetAppearance, modifier = Modifier.weight(1f))
            ReaderPrimaryButton(text = state.panel.saveLabel, onClick = onSaveTheme, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun AppearanceFeedbackBlock(
    feedback: ReaderShellFeedbackUiModel,
    onPrimary: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 200.dp, max = 280.dp)
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
private fun AppearanceBrightnessPanel(brightness: ReadingBrightnessUiModel) {
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
private fun AppearanceModuleNav(
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
