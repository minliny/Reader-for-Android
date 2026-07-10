package com.reader.ui.source

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reader.ui.tokens.ReaderTypeToken
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.reader.android.R
import com.reader.ui.shell.ReaderUiIntent
import com.reader.ui.shell.SourceImportState
import com.reader.ui.theme.ReaderShapes
import com.reader.ui.theme.ReaderTextStyles
import com.reader.ui.theme.readerExtraColors
import java.net.URI
import org.json.JSONArray
import org.json.JSONObject

/**
 * P3: Book-source import screen wired to the reducer's [SourceImportState]
 * state machine (Idle → Parsing → Preview → Importing → Done | Error).
 *
 * State ownership: [state] is the slice of [com.reader.ui.shell.ReaderUiState]
 * owned by the reducer. The screen never holds a parallel local state
 * machine — it only owns the JSON input field (EphemeralState per PLAN §2)
 * via [ImportBookSourceViewModel].
 *
 * Side effects: when the reducer transitions to `Parsing`, a
 * [LaunchedEffect] calls [ImportBookSourceViewModel.importBookSource] (which
 * bridges to Core `source.import`) and dispatches `CompleteSourceImport` or
 * `FailSourceImport` with the result.
 *
 * Navigation: [onBack] pops the route; [onDismiss] dispatches
 * `DismissSourceImportResult` to reset the state machine to Idle (used when
 * the user dismisses the Done/Error result and stays on the screen for
 * another import).
 */
@Composable
fun ImportBookSourceScreen(
    state: SourceImportState,
    dispatch: (ReaderUiIntent) -> Unit,
    onBack: () -> Unit,
    vm: ImportBookSourceViewModel = viewModel()
) {
    var conflictMode by remember { mutableStateOf("跳过重复") }
    val json by vm.json.collectAsStateWithLifecycle()

    // P3: Side effect — when the reducer enters Parsing, call Core
    // `source.import` and dispatch the matching terminal intent. The
    // LaunchedEffect keys on `state` so it re-fires only when the state
    // actually transitions to Parsing (not on every recomposition).
    LaunchedEffect(state) {
        if (state is SourceImportState.Parsing) {
            val result = vm.importBookSource(json)
            if (result.success) {
                dispatch(
                    ReaderUiIntent.CompleteSourceImport(
                        imported = 1,
                        skipped = 0,
                        failed = 0
                    )
                )
            } else {
                dispatch(ReaderUiIntent.FailSourceImport(result.data))
            }
        }
    }

    // P3: State-dependent label for the import button and bottom action bar.
    val importLabel = when (state) {
        is SourceImportState.Parsing, SourceImportState.Importing -> "导入中..."
        is SourceImportState.Done -> "导入成功"
        is SourceImportState.Error -> "重试"
        else -> "确认导入"
    }
    val importEnabled = state !is SourceImportState.Parsing &&
                        state !is SourceImportState.Importing
    val showDone = state is SourceImportState.Done

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        SourceImportTopBar(onBack = onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SourceImportOriginCard(
                title = "网络导入",
                meta = "https://example.com/booksource.json",
                actionLabel = "更换",
                onAction = onBack
            )
            // P3: JSON input field — drives the reducer via ParseSourceImport(json).
            SourceSectionTitle("书源 JSON")
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 240.dp)
                    .background(
                        color = readerExtraColors().paper,
                        shape = ReaderShapes.sm
                    )
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = ReaderShapes.sm
                    )
                    .padding(12.dp)
            ) {
                if (json.isEmpty()) {
                    Text(
                        text = "粘贴书源 JSON 数组...",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = TextStyle(
                            fontFamily = FontFamily.Default,
                            fontSize = ReaderTypeToken.BOOK_TITLE.value,
                            lineHeight = 20.sp
                        )
                    )
                }
                BasicTextField(
                    value = json,
                    onValueChange = vm::updateJson,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Default,
                        fontSize = ReaderTypeToken.BOOK_TITLE.value,
                        lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
                )
            }
            // P3: State feedback for e2e closure (空/解析/成功/错误).
            when (state) {
                is SourceImportState.Parsing -> SourceStatLine("正在解析并导入...")
                is SourceImportState.Importing -> SourceStatLine("正在写入书源...")
                is SourceImportState.Done -> SourceStatLine(
                    "导入成功（${state.imported} 成功 / ${state.skipped} 跳过 / ${state.failed} 失败）— 点击完成返回"
                )
                is SourceImportState.Error -> SourceStatLine("错误：${state.message}")
                is SourceImportState.Preview -> SourceStatLine("已解析 ${state.entries.size} 条 — 点击确认导入")
                else -> {
                    if (json.isNotBlank()) {
                        SourceStatLine("已粘贴 JSON — 点击确认导入")
                    } else {
                        SourceStatLine("粘贴书源 JSON 后点击确认导入")
                    }
                }
            }
            SourceSectionTitle("冲突处理")
            SourceSegmentedControl(
                options = listOf("跳过重复", "覆盖旧源", "保留两份"),
                selected = conflictMode,
                onSelected = { conflictMode = it }
            )
            SourceFormRow(
                title = "导入到分组",
                meta = "可在导入后批量调整分组",
                value = "保持原分组"
            )
        }
        SourceBottomActionBar(
            importEnabled = importEnabled,
            importLabel = importLabel,
            onCancel = onBack,
            onImport = {
                // P3: Drive the reducer state machine via ParseSourceImport.
                // The LaunchedEffect above observes Parsing and calls SourceApi.
                if (state is SourceImportState.Done) {
                    onBack()
                } else if (state is SourceImportState.Error) {
                    dispatch(ReaderUiIntent.DismissSourceImportResult)
                    dispatch(ReaderUiIntent.ParseSourceImport(json))
                } else {
                    dispatch(ReaderUiIntent.ParseSourceImport(json))
                }
            },
            onDone = onBack,
            showDone = showDone
        )
    }
}

@Composable
private fun SourceImportTopBar(onBack: () -> Unit) {
    val ink = MaterialTheme.colorScheme.onBackground
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 58.dp)
            .padding(top = 6.dp, start = 20.dp, end = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.reader_ic_chevron_left),
                contentDescription = "返回",
                tint = ink,
                modifier = Modifier.size(24.dp)
            )
        }
        Text(
            text = "导入书源",
            style = ReaderTextStyles.backBarTitle,
            color = ink,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.size(44.dp))
    }
}

@Composable
private fun SourceImportOriginCard(
    title: String,
    meta: String,
    actionLabel: String,
    onAction: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .background(colors.surface.copy(alpha = 0.78f), ReaderShapes.lg)
            .border(1.dp, extra.hairline, ReaderShapes.lg)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        IconCircle(R.drawable.reader_ic_add)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = title,
                style = sourceTitleStyle(),
                color = colors.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = meta,
                style = sourceMetaStyle(),
                color = extra.muted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        SourceSmallButton(
            label = actionLabel,
            enabled = true,
            onClick = onAction
        )
    }
}

@Composable
private fun SourceStatLine(text: String) {
    Text(
        text = text,
        style = sourceMetaStyle().copy(lineHeight = 15.sp),
        color = readerExtraColors().muted
    )
}

@Composable
private fun SourceSectionTitle(title: String) {
    Text(
        text = title,
        style = TextStyle(
            fontFamily = FontFamily.Default,
            fontSize = ReaderTypeToken.CHAPTER_TITLE.value,
            lineHeight = 16.sp,
            fontWeight = FontWeight(900)
        ),
        color = MaterialTheme.colorScheme.onBackground
    )
}

@Composable
private fun SourceSegmentedControl(
    options: List<String>,
    selected: String,
    onSelected: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { option ->
            SourceSegmentButton(
                label = option,
                selected = selected == option,
                onClick = { onSelected(option) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SourceSegmentButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 34.dp)
            .background(if (selected) colors.primary else colors.surface.copy(alpha = 0.82f), ReaderShapes.pill)
            .border(1.dp, if (selected) colors.primary else extra.hairline, ReaderShapes.pill)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = sourceActionStyle(),
            color = if (selected) colors.onPrimary else colors.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SourceFormRow(title: String, meta: String, value: String) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .background(colors.surface.copy(alpha = 0.78f), ReaderShapes.lg)
            .border(1.dp, extra.hairline, ReaderShapes.lg)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = title,
                style = sourceTitleStyle(),
                color = colors.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = meta,
                style = sourceMetaStyle(),
                color = extra.muted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = value,
            style = sourceActionStyle(),
            color = colors.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Icon(
            painter = painterResource(id = R.drawable.reader_ic_chevron),
            contentDescription = null,
            tint = extra.muted,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun SourcePreviewList(rows: List<SourcePreviewRow>) {
    val extra = readerExtraColors()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, extra.hairline, ReaderShapes.lg)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.58f), ReaderShapes.lg)
            .padding(horizontal = 12.dp)
    ) {
        rows.forEachIndexed { index, row ->
            SourcePreviewRow(row)
            if (index != rows.lastIndex) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(extra.hairline)
                )
            }
        }
    }
}

@Composable
private fun SourcePreviewRow(row: SourcePreviewRow) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 52.dp)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = row.title,
                style = sourceTitleStyle(),
                color = colors.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = row.meta,
                style = sourceMetaStyle(),
                color = extra.muted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        SourceStatusPill(label = row.status, tone = row.tone)
    }
}

@Composable
private fun SourceBottomActionBar(
    importEnabled: Boolean,
    importLabel: String,
    onCancel: () -> Unit,
    onImport: () -> Unit,
    onDone: () -> Unit,
    showDone: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.96f))
            .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SourceBottomButton(
            label = "取消",
            primary = false,
            enabled = true,
            onClick = onCancel,
            modifier = Modifier.weight(1f)
        )
        SourceBottomButton(
            label = if (showDone) "完成" else importLabel,
            primary = true,
            enabled = showDone || importEnabled,
            onClick = if (showDone) onDone else onImport,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SourceBottomButton(
    label: String,
    primary: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    val background = when {
        !enabled -> extra.metaBackground.copy(alpha = 0.58f)
        primary -> colors.primary
        else -> colors.surface.copy(alpha = 0.86f)
    }
    val border = when {
        !enabled -> extra.hairline
        primary -> colors.primary
        else -> colors.primary.copy(alpha = 0.24f)
    }
    val textColor = when {
        !enabled -> extra.muted
        primary -> colors.onPrimary
        else -> colors.primary
    }
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 42.dp)
            .background(background, ReaderShapes.md)
            .border(1.dp, border, ReaderShapes.md)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = sourceActionStyle(),
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SourceSmallButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Box(
        modifier = Modifier
            .defaultMinSize(minHeight = 34.dp)
            .background(colors.surface.copy(alpha = 0.86f), ReaderShapes.md)
            .border(1.dp, colors.primary.copy(alpha = if (enabled) 0.24f else 0.12f), ReaderShapes.md)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = sourceActionStyle(),
            color = if (enabled) colors.primary else extra.muted,
            maxLines = 1
        )
    }
}

@Composable
private fun SourceStatusPill(label: String, tone: SourceTone) {
    val tint = toneColor(tone)
    Box(
        modifier = Modifier
            .width(64.dp)
            .defaultMinSize(minHeight = 28.dp)
            .background(tint.copy(alpha = 0.11f), ReaderShapes.pill)
            .border(1.dp, tint.copy(alpha = 0.28f), ReaderShapes.pill)
            .padding(horizontal = 6.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = TextStyle(
                fontFamily = FontFamily.Default,
                fontSize = ReaderTypeToken.TOP_BAR_SUBTITLE.value,
                lineHeight = 12.sp,
                fontWeight = FontWeight(800),
                textAlign = TextAlign.Center
            ),
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun IconCircle(iconRes: Int, tint: Color = MaterialTheme.colorScheme.primary) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .background(tint.copy(alpha = 0.12f), ReaderShapes.pill),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun toneColor(tone: SourceTone): Color {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    return when (tone) {
        SourceTone.Good -> extra.forest
        SourceTone.Warn -> extra.danger
        SourceTone.Muted -> extra.muted
        SourceTone.Info -> colors.primary
    }
}

private fun sourceTitleStyle() = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = ReaderTypeToken.CHAPTER_TITLE.value,
    lineHeight = 16.sp,
    fontWeight = FontWeight(800)
)

private fun sourceMetaStyle() = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = ReaderTypeToken.TOP_BAR_SUBTITLE.value,
    lineHeight = 13.sp,
    fontWeight = FontWeight(500)
)

private fun sourceActionStyle() = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = ReaderTypeToken.ACTION_LABEL.value,
    lineHeight = 13.sp,
    fontWeight = FontWeight(900),
    textAlign = TextAlign.Center
)

private enum class SourceTone {
    Good,
    Warn,
    Muted,
    Info
}

private data class SourcePreviewRow(
    val title: String,
    val meta: String,
    val status: String,
    val tone: SourceTone
)
