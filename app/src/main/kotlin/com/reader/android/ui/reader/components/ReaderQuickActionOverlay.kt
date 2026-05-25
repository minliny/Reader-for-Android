package com.reader.android.ui.reader.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.reader.android.ui.components.ReaderPrimaryButton
import com.reader.android.ui.components.ReaderSecondaryButton
import com.reader.android.ui.theme.ReaderTheme

data class SearchMatch(
    val title: String,
    val snippet: String
)

data class ReplaceRule(
    val name: String,
    val pattern: String,
    val replacement: String,
    val scope: String,
    val enabled: Boolean
)

enum class AutoScrollSpeed { Slow, Medium, Fast }
enum class AutoScrollMode { Scroll, PageFlip, ContinuousScroll }

@Composable
fun ReaderSearchOverlay(
    query: String,
    resultCount: Int,
    results: List<SearchMatch>,
    currentIndex: Int,
    modifier: Modifier = Modifier,
    onQueryChange: (String) -> Unit = {},
    onClear: () -> Unit = {},
    onPrevResult: () -> Unit = {},
    onNextResult: () -> Unit = {},
    onDismiss: () -> Unit = {}
) {
    ReaderQuickActionPanel(
        modifier = modifier,
        title = "搜索",
        meta = "共 $resultCount 处结果",
        onDismiss = onDismiss
    ) {
        // Search field row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(ReaderTheme.shapes.chip)
                    .background(ReaderTheme.colors.metaBg)
                    .border(0.5.dp, ReaderTheme.colors.controlBorder, ReaderTheme.shapes.chip)
                    .semantics { contentDescription = "搜索本章内容" }
                    .padding(horizontal = ReaderTheme.spacing.sm, vertical = 10.dp)
            ) {
                Text(
                    text = query.ifBlank { "搜索本章内容" },
                    color = if (query.isBlank()) ReaderTheme.colors.bodyText else ReaderTheme.colors.controlInk,
                    style = ReaderTheme.typography.bookTitle
                )
            }
            IconButton(
                onClick = onClear,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Filled.Close, "清除", tint = ReaderTheme.colors.controlInk)
            }
        }

        // Results list
        if (results.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(top = ReaderTheme.spacing.sm),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                itemsIndexed(results) { _, match ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(ReaderTheme.shapes.small)
                            .padding(vertical = ReaderTheme.spacing.xs)
                            .semantics { contentDescription = "搜索结果，${match.title}" }
                    ) {
                        Text(
                            text = match.title,
                            color = ReaderTheme.colors.controlInk,
                            style = ReaderTheme.typography.bookTitle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = match.snippet,
                            color = ReaderTheme.colors.bodyText,
                            style = ReaderTheme.typography.bookMeta,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Navigation
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)
            ) {
                ReaderSecondaryButton(
                    text = "上一个结果",
                    onClick = onPrevResult,
                    modifier = Modifier.weight(1f)
                )
                ReaderPrimaryButton(
                    text = "下一个结果",
                    onClick = onNextResult,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun ReaderAutoScrollOverlay(
    isRunning: Boolean,
    speed: AutoScrollSpeed,
    mode: AutoScrollMode,
    modifier: Modifier = Modifier,
    onStart: () -> Unit = {},
    onPause: () -> Unit = {},
    onStop: () -> Unit = {},
    onSpeedChange: (AutoScrollSpeed) -> Unit = {},
    onModeChange: (AutoScrollMode) -> Unit = {},
    onDismiss: () -> Unit = {}
) {
    ReaderQuickActionPanel(
        modifier = modifier,
        title = "自动翻页",
        meta = "状态：${if (isRunning) "运行中" else "未开启"}",
        onDismiss = onDismiss
    ) {
        // Control buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)
        ) {
            ReaderPrimaryButton("开始", onStart, Modifier.weight(1f))
            ReaderSecondaryButton("暂停", onPause, Modifier.weight(1f))
            ReaderSecondaryButton("停止", onStop, Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(ReaderTheme.spacing.sm))

        // Speed selector
        val speedLabel = when (speed) {
            AutoScrollSpeed.Slow -> "慢速 · 适合精读"
            AutoScrollSpeed.Medium -> "中速 · 适合连续阅读"
            AutoScrollSpeed.Fast -> "快速 · 适合浏览"
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ReaderTheme.shapes.small)
                .border(0.5.dp, ReaderTheme.colors.controlBorder, ReaderTheme.shapes.small)
                .padding(ReaderTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("翻页速度", color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.bookTitle)
                Text(speedLabel, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.bookMeta)
            }
        }

        Spacer(modifier = Modifier.height(ReaderTheme.spacing.xs))

        // Speed slider
        val sliderValue = when (speed) {
            AutoScrollSpeed.Slow -> 0f
            AutoScrollSpeed.Medium -> 0.5f
            AutoScrollSpeed.Fast -> 1f
        }
        Slider(
            value = sliderValue,
            onValueChange = {
                onSpeedChange(
                    when {
                        it < 0.33f -> AutoScrollSpeed.Slow
                        it < 0.66f -> AutoScrollSpeed.Medium
                        else -> AutoScrollSpeed.Fast
                    }
                )
            },
            modifier = Modifier
                .height(32.dp)
                .semantics { contentDescription = "翻页速度" },
            colors = SliderDefaults.colors(
                thumbColor = ReaderTheme.colors.primary,
                activeTrackColor = ReaderTheme.colors.primary,
                inactiveTrackColor = ReaderTheme.colors.mutedTrack
            )
        )

        Spacer(modifier = Modifier.height(ReaderTheme.spacing.xs))

        // Mode segmented control
        Text("翻页方式", color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.bookMeta)
        Spacer(modifier = Modifier.height(ReaderTheme.spacing.xs))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.xs)
        ) {
            AutoScrollMode.entries.forEach { m ->
                val label = when (m) {
                    AutoScrollMode.Scroll -> "滚动"
                    AutoScrollMode.PageFlip -> "点击翻页"
                    AutoScrollMode.ContinuousScroll -> "连续滚动"
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(ReaderTheme.shapes.chip)
                        .background(if (m == mode) ReaderTheme.colors.primary else ReaderTheme.colors.floatingControlBg)
                        .border(0.5.dp, ReaderTheme.colors.controlBorder, ReaderTheme.shapes.chip)
                        .clickable(role = Role.Button) { onModeChange(m) }
                        .semantics { contentDescription = label }
                        .padding(vertical = ReaderTheme.spacing.xs),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = if (m == mode) ReaderTheme.colors.paperBg else ReaderTheme.colors.controlInk,
                        style = ReaderTheme.typography.readerControlLabel
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(ReaderTheme.spacing.sm))

        Text(
            "开启后将在本章内按当前速度推进，不影响下方页内控制条。",
            color = ReaderTheme.colors.bodyText,
            style = ReaderTheme.typography.bookMeta
        )
    }
}

@Composable
fun ReaderReplaceOverlay(
    bookName: String,
    rules: List<ReplaceRule>,
    modifier: Modifier = Modifier,
    onRuleToggle: (Int, Boolean) -> Unit = { _, _ -> },
    onApply: () -> Unit = {},
    onCancel: () -> Unit = {},
    onRestore: () -> Unit = {},
    onDismiss: () -> Unit = {}
) {
    ReaderQuickActionPanel(
        modifier = modifier,
        title = "内容替换",
        meta = "匹配规则 ${rules.size} 条",
        onDismiss = onDismiss
    ) {
        // Current book context — CRITICAL: only shows current book rules
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ReaderTheme.shapes.small)
                .border(0.5.dp, ReaderTheme.colors.controlBorder, ReaderTheme.shapes.small)
                .padding(ReaderTheme.spacing.sm)
        ) {
            Column {
                Text(
                    "当前书籍：$bookName",
                    color = ReaderTheme.colors.controlInk,
                    style = ReaderTheme.typography.bookTitle
                )
                Text(
                    "仅显示当前书籍匹配到的替换规则",
                    color = ReaderTheme.colors.bodyText,
                    style = ReaderTheme.typography.bookMeta
                )
            }
        }

        Spacer(modifier = Modifier.height(ReaderTheme.spacing.sm))

        // Rule list
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.xs)
        ) {
            rules.forEachIndexed { index, rule ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(ReaderTheme.shapes.small)
                        .border(0.5.dp, ReaderTheme.colors.controlBorder, ReaderTheme.shapes.small)
                        .padding(ReaderTheme.spacing.sm)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                rule.name,
                                color = ReaderTheme.colors.controlInk,
                                style = ReaderTheme.typography.bookTitle
                            )
                            Text(
                                "范围：${rule.scope}",
                                color = ReaderTheme.colors.bodyText,
                                style = ReaderTheme.typography.bookMeta
                            )
                        }
                        Spacer(modifier = Modifier.width(ReaderTheme.spacing.sm))
                        Switch(
                            checked = rule.enabled,
                            onCheckedChange = { onRuleToggle(index, it) },
                            modifier = Modifier.size(40.dp, 24.dp),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = ReaderTheme.colors.paperBg,
                                checkedTrackColor = ReaderTheme.colors.primary,
                                uncheckedThumbColor = ReaderTheme.colors.controlInk,
                                uncheckedTrackColor = ReaderTheme.colors.mutedTrack
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "匹配：${rule.pattern}",
                        color = ReaderTheme.colors.bodyText,
                        style = ReaderTheme.typography.bookMeta
                    )
                    Text(
                        "替换：${rule.replacement}",
                        color = ReaderTheme.colors.primary,
                        style = ReaderTheme.typography.bookMeta
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(ReaderTheme.spacing.sm))

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)
        ) {
            ReaderPrimaryButton("应用", onApply, Modifier.weight(1f))
            ReaderSecondaryButton("取消", onCancel, Modifier.weight(1f))
            ReaderSecondaryButton("恢复原文", onRestore, Modifier.weight(1f))
        }
    }
}

@Composable
private fun ReaderQuickActionPanel(
    title: String,
    meta: String,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(ReaderTheme.shapes.readerOverlay)
            .background(ReaderTheme.colors.floatingControlBg)
            .border(0.5.dp, ReaderTheme.colors.controlBorder, ReaderTheme.shapes.readerOverlay)
            .semantics { contentDescription = "${title}弹窗" }
            .padding(ReaderTheme.spacing.sm)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
                color = ReaderTheme.colors.controlInk,
                style = ReaderTheme.typography.sectionTitle
            )
            Text(
                text = meta,
                color = ReaderTheme.colors.bodyText,
                style = ReaderTheme.typography.bookMeta
            )
        }

        Spacer(modifier = Modifier.height(ReaderTheme.spacing.sm))

        // Content
        content()
    }
}
