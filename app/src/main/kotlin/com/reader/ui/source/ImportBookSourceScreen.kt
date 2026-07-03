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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.reader.android.R
import com.reader.ui.theme.ReaderShapes
import com.reader.ui.theme.ReaderTextStyles
import com.reader.ui.theme.readerExtraColors
import java.net.URI
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun ImportBookSourceScreen(
    onDone: () -> Unit,
    vm: ImportBookSourceViewModel = viewModel()
) {
    var conflictMode by remember { mutableStateOf("跳过重复") }
    val previewRows = remember {
        listOf(
            SourcePreviewRow("起点中文网", "qidian.com · 起点导入", "新增", SourceTone.Good),
            SourcePreviewRow("晋江文学城", "jjwx.example · 起点导入", "重复", SourceTone.Muted),
            SourcePreviewRow("轻小说文库", "lightnovel.example · 测试书源", "新增", SourceTone.Good),
            SourcePreviewRow("旧规则源", "old.example · 自定义", "重复", SourceTone.Muted),
            SourcePreviewRow("失效示例源", "dead.example · 测试书源", "异常", SourceTone.Warn),
            SourcePreviewRow("豆瓣阅读", "read.douban.com · 自定义", "新增", SourceTone.Good),
            SourcePreviewRow("开源书源示例", "opensource.example · 测试书源", "新增", SourceTone.Good)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        SourceImportTopBar(onBack = onDone)
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
                onAction = onDone
            )
            SourceStatLine("共 24 个书源 · 18 个新增 · 4 个重复 · 2 个异常")
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
            SourcePreviewList(previewRows)
        }
        SourceBottomActionBar(
            importEnabled = true,
            importLabel = "确认导入",
            onCancel = onDone,
            onImport = onDone,
            onDone = onDone,
            showDone = false
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
            fontSize = 13.sp,
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
                fontSize = 10.sp,
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
    fontSize = 13.sp,
    lineHeight = 16.sp,
    fontWeight = FontWeight(800)
)

private fun sourceMetaStyle() = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = 10.sp,
    lineHeight = 13.sp,
    fontWeight = FontWeight(500)
)

private fun sourceActionStyle() = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = 11.sp,
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
