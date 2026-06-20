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
import com.reader.android.ui.components.ReaderIconButton
import com.reader.android.ui.components.ReaderIconToken
import com.reader.android.ui.components.ReaderPrimaryButton
import com.reader.android.ui.components.ReaderSecondaryButton
import com.reader.android.ui.components.ReaderSwitch
import com.reader.android.ui.components.asImageVector
import com.reader.android.ui.theme.ReaderTheme

@Composable
fun ContentReplacementScreen(
    state: ContentReplacementUiState = ContentReplacementMapper.fromFixture(),
    onClose: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onToggleReplacement: (Boolean) -> Unit = {},
    onToggleRule: (ReplacementRuleUiModel, Boolean) -> Unit = { _, _ -> },
    onOpenRule: (ReplacementRuleUiModel) -> Unit = {},
    onAddRule: () -> Unit = {},
    onPatternChange: (String) -> Unit = {},
    onReplacementChange: (String) -> Unit = {},
    onTestReplacement: () -> Unit = {},
    onSaveRule: () -> Unit = {},
    onTemporaryClose: () -> Unit = {},
    onRetry: () -> Unit = {}
) {
    ReaderTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ReaderTheme.colors.paperBg)
                .semantics { contentDescription = "ReaderShell 内容替换" }
        ) {
            ContentReplacementReadingSurface(state.reading)
            ContentReplacementOverlay(
                state = state,
                onClose = onClose,
                onOpenSettings = onOpenSettings,
                modifier = Modifier.semantics { contentDescription = "readerOverlayHost" }
            )
            ContentReplacementBottomSheet(
                state = state,
                onToggleReplacement = onToggleReplacement,
                onToggleRule = onToggleRule,
                onOpenRule = onOpenRule,
                onAddRule = onAddRule,
                onPatternChange = onPatternChange,
                onReplacementChange = onReplacementChange,
                onTestReplacement = onTestReplacement,
                onSaveRule = onSaveRule,
                onTemporaryClose = onTemporaryClose,
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
private fun ContentReplacementReadingSurface(reading: ReaderShellTextUiModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ReaderTheme.colors.paperBg)
            .padding(start = 28.dp, end = 28.dp, top = 112.dp, bottom = 430.dp)
            .semantics { contentDescription = "readingSurface" }
    ) {
        reading.paragraphs.forEach { paragraph ->
            Text(paragraph, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.readerBody)
            Spacer(modifier = Modifier.height(ReaderTheme.spacing.md))
        }
    }
}

@Composable
private fun ContentReplacementOverlay(
    state: ContentReplacementUiState,
    onClose: () -> Unit,
    onOpenSettings: () -> Unit,
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
                    state.topControl.bookTitle,
                    color = ReaderTheme.colors.bodyText,
                    style = ReaderTheme.typography.bookMeta,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            ReaderIconButton(
                icon = ReaderIconToken.ReadingSettings.asImageVector(),
                contentDescription = state.topControl.settingsLabel,
                onClick = onOpenSettings
            )
        }
    }
}

@Composable
private fun ContentReplacementBottomSheet(
    state: ContentReplacementUiState,
    onToggleReplacement: (Boolean) -> Unit,
    onToggleRule: (ReplacementRuleUiModel, Boolean) -> Unit,
    onOpenRule: (ReplacementRuleUiModel) -> Unit,
    onAddRule: () -> Unit,
    onPatternChange: (String) -> Unit,
    onReplacementChange: (String) -> Unit,
    onTestReplacement: () -> Unit,
    onSaveRule: () -> Unit,
    onTemporaryClose: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 370.dp, max = 526.dp)
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
        ContentReplacementPanelHeader(state.panel)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            when (state.displayState) {
                ContentReplacementDisplayState.Default -> ContentReplacementDefaultPanel(
                    state = state,
                    onToggleReplacement = onToggleReplacement,
                    onToggleRule = onToggleRule,
                    onOpenRule = onOpenRule,
                    onAddRule = onAddRule,
                    onTemporaryClose = onTemporaryClose
                )
                ContentReplacementDisplayState.Edit -> ContentReplacementEditPanel(
                    state = state,
                    errored = false,
                    onPatternChange = onPatternChange,
                    onReplacementChange = onReplacementChange,
                    onTestReplacement = onTestReplacement,
                    onSaveRule = onSaveRule
                )
                ContentReplacementDisplayState.Error -> ContentReplacementEditPanel(
                    state = state,
                    errored = true,
                    onPatternChange = onPatternChange,
                    onReplacementChange = onReplacementChange,
                    onTestReplacement = onTestReplacement,
                    onSaveRule = onSaveRule
                )
                ContentReplacementDisplayState.Empty -> ContentReplacementFeedbackPanel(state.empty, onAddRule)
                ContentReplacementDisplayState.Loading -> ContentReplacementLoadingPanel(state.loading)
            }
        }
    }
}

@Composable
private fun ContentReplacementPanelHeader(panel: ContentReplacementPanelUiModel) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            panel.title,
            modifier = Modifier.weight(1f),
            color = ReaderTheme.colors.controlInk,
            style = ReaderTheme.typography.pageTitle
        )
        ReaderSecondaryButton(text = panel.allRulesLabel, onClick = {})
    }
}

@Composable
private fun ContentReplacementDefaultPanel(
    state: ContentReplacementUiState,
    onToggleReplacement: (Boolean) -> Unit,
    onToggleRule: (ReplacementRuleUiModel, Boolean) -> Unit,
    onOpenRule: (ReplacementRuleUiModel) -> Unit,
    onAddRule: () -> Unit,
    onTemporaryClose: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)) {
        ContentReplacementEnableCard(state.panel, state.enabled, onToggleReplacement)
        ContentReplacementRuleList(state.rules, onToggleRule, onOpenRule)
        ContentReplacementPreviewCard(state.preview)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)) {
            ReaderSecondaryButton(
                text = state.panel.tempCloseLabel,
                onClick = onTemporaryClose,
                modifier = Modifier.weight(1f)
            )
            ReaderPrimaryButton(
                text = state.panel.addRuleLabel,
                onClick = onAddRule,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ContentReplacementEnableCard(
    panel: ContentReplacementPanelUiModel,
    enabled: Boolean,
    onToggleReplacement: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ReaderTheme.shapes.small)
            .background(ReaderTheme.colors.metaBg)
            .semantics { contentDescription = "${panel.enableTitle}，${if (enabled) "已开启" else "已关闭"}" }
            .padding(ReaderTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(panel.enableTitle, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.bookTitle)
            Text(panel.enableCopy, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.bookMeta)
        }
        ReaderSwitch(checked = enabled, onCheckedChange = onToggleReplacement)
    }
}

@Composable
private fun ContentReplacementRuleList(
    rules: List<ReplacementRuleUiModel>,
    onToggleRule: (ReplacementRuleUiModel, Boolean) -> Unit,
    onOpenRule: (ReplacementRuleUiModel) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ReaderTheme.shapes.small)
            .background(ReaderTheme.colors.metaBg)
            .semantics { contentDescription = "替换规则列表" }
    ) {
        rules.forEach { rule ->
            ContentReplacementRuleRow(rule, onToggleRule, onOpenRule)
        }
    }
}

@Composable
private fun ContentReplacementRuleRow(
    rule: ReplacementRuleUiModel,
    onToggleRule: (ReplacementRuleUiModel, Boolean) -> Unit,
    onOpenRule: (ReplacementRuleUiModel) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .clickable(role = Role.Button) { onOpenRule(rule) }
            .semantics { contentDescription = "替换规则，${rule.title}，${rule.meta}" }
            .padding(horizontal = ReaderTheme.spacing.sm, vertical = ReaderTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)
    ) {
        Icon(
            imageVector = rule.icon.toReplacementIcon().asImageVector(),
            contentDescription = null,
            tint = ReaderTheme.colors.primary,
            modifier = Modifier.size(20.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(rule.title, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.bookTitle)
            Text(rule.meta, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.bookMeta)
        }
        ReaderSwitch(checked = rule.enabled, onCheckedChange = { onToggleRule(rule, it) })
        Icon(
            imageVector = ReaderIconToken.Chevron.asImageVector(),
            contentDescription = null,
            tint = ReaderTheme.colors.bodyText,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun ContentReplacementEditPanel(
    state: ContentReplacementUiState,
    errored: Boolean,
    onPatternChange: (String) -> Unit,
    onReplacementChange: (String) -> Unit,
    onTestReplacement: () -> Unit,
    onSaveRule: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ReaderTheme.shapes.small)
                .background(ReaderTheme.colors.metaBg)
                .semantics { contentDescription = "规则编辑表单" }
                .padding(ReaderTheme.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)
        ) {
            Text(
                text = if (errored) state.error.title else state.panel.addRuleLabel,
                color = ReaderTheme.colors.controlInk,
                style = ReaderTheme.typography.pageTitle
            )
            ContentReplacementTextField(
                label = state.form.beforeLabel,
                value = state.form.beforeValue,
                onClick = { onPatternChange(state.form.beforeValue) }
            )
            ContentReplacementTextField(
                label = state.form.afterLabel,
                value = state.form.afterValue,
                onClick = { onReplacementChange(state.form.afterValue) }
            )
            if (errored) {
                Text(state.error.message, color = ReaderTheme.colors.primary, style = ReaderTheme.typography.stateMessage)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)) {
                ReaderSecondaryButton(
                    text = state.form.testLabel,
                    onClick = onTestReplacement,
                    modifier = Modifier.weight(1f)
                )
                ReaderPrimaryButton(
                    text = state.form.saveLabel,
                    onClick = onSaveRule,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        ContentReplacementPreviewCard(state.preview)
    }
}

@Composable
private fun ContentReplacementTextField(
    label: String,
    value: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ReaderTheme.shapes.small)
            .background(ReaderTheme.colors.paperBg)
            .border(1.dp, ReaderTheme.colors.controlBorder, ReaderTheme.shapes.small)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "$label，$value" }
            .padding(ReaderTheme.spacing.sm)
    ) {
        Text(label, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.readerSmallMeta)
        Text(value, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.bookTitle)
    }
}

@Composable
private fun ContentReplacementPreviewCard(preview: ReplacementPreviewUiModel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ReaderTheme.shapes.small)
            .background(ReaderTheme.colors.metaBg)
            .semantics { contentDescription = preview.title }
            .padding(ReaderTheme.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.xs)
    ) {
        Text(preview.title, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.sectionTitle)
        ReplacementPreviewLine(preview.beforeLabel, preview.beforeText, "反着光")
        ReplacementPreviewLine(preview.afterLabel, preview.afterText, "映着光")
    }
}

@Composable
private fun ReplacementPreviewLine(
    label: String,
    text: String,
    highlight: String
) {
    Row(horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.xs)) {
        Text("$label：", color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.bookMeta)
        ReplacementHighlightedText(text = text, query = highlight)
    }
}

@Composable
private fun ContentReplacementLoadingPanel(feedback: ReaderShellFeedbackUiModel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 240.dp)
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
        repeat(5) { index ->
            Box(
                modifier = Modifier
                    .fillMaxWidth(if (index % 2 == 0) 0.86f else 0.68f)
                    .height(10.dp)
                    .clip(ReaderTheme.shapes.chip)
                    .background(ReaderTheme.colors.mutedTrack)
            )
            Spacer(modifier = Modifier.height(ReaderTheme.spacing.xs))
        }
    }
}

@Composable
private fun ContentReplacementFeedbackPanel(
    feedback: ReaderShellFeedbackUiModel,
    onPrimary: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 240.dp)
            .clip(ReaderTheme.shapes.small)
            .background(ReaderTheme.colors.metaBg)
            .semantics { contentDescription = feedback.title }
            .padding(ReaderTheme.spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = ReaderIconToken.ContentReplace.asImageVector(),
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
private fun ReplacementHighlightedText(
    text: String,
    query: String
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
    Text(text = annotated, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.bookMeta)
}

private fun String.toReplacementIcon(): ReaderIconToken = when (this) {
    "shield" -> ReaderIconToken.Permission
    "text" -> ReaderIconToken.Appearance
    "typo" -> ReaderIconToken.ContentReplace
    else -> ReaderIconToken.ContentReplace
}
