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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import com.reader.android.ui.components.ReaderSwitch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

// ── Data types ──

data class TocEntry(
    val title: String,
    val level: Int,
    val isCurrent: Boolean = false,
    val hasBookmark: Boolean = false,
    val progress: Float? = null
)

data class AppSettingItem(
    val name: String,
    val value: String
)

data class AppSwitchItem(
    val name: String,
    val checked: Boolean
)

// ── Directory overlay ──

@Composable
fun ReaderDirectoryOverlay(
    tocEntries: List<TocEntry>,
    volumeInfo: String,
    currentChapter: String,
    modifier: Modifier = Modifier,
    onTocEntryClick: (Int) -> Unit = {},
    onDismiss: () -> Unit = {}
) {
    ReaderBottomPanel(
        modifier = modifier,
        contentDescription = "目录书签弹窗"
    ) {
        // Tabs: 目录 | 书签
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.xs)
        ) {
            listOf("目录" to true, "书签" to false).forEach { (label, active) ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(ReaderTheme.shapes.chip)
                        .background(if (active) ReaderTheme.colors.primary else ReaderTheme.colors.floatingControlBg)
                        .border(1.dp, ReaderTheme.colors.controlBorder, ReaderTheme.shapes.chip)
                        .semantics { contentDescription = label }
                        .padding(vertical = ReaderTheme.spacing.xs),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = if (active) ReaderTheme.colors.paperBg else ReaderTheme.colors.controlInk,
                        style = ReaderTheme.typography.readerControlLabel
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(ReaderTheme.spacing.xs))
        // Volume / level small text
        Text(volumeInfo, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.bookMeta)

        Spacer(modifier = Modifier.height(ReaderTheme.spacing.sm))

        // TOC list — clean, no card-style borders
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            itemsIndexed(tocEntries) { index, entry ->
                val indentStart = (entry.level - 1) * 10
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (entry.isCurrent) Modifier.background(ReaderTheme.colors.primary.copy(alpha = 0.10f))
                            else Modifier
                        )
                        .clickable(role = Role.Button) { onTocEntryClick(index) }
                        .semantics { contentDescription = "目录条目，${entry.title}" + if (entry.isCurrent) "，当前" else "" }
                        .padding(start = (10 + indentStart).dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Chapter title — left-aligned, main area
                    Text(
                        text = entry.title,
                        color = if (entry.isCurrent) ReaderTheme.colors.primary else ReaderTheme.colors.controlInk,
                        style = ReaderTheme.typography.bookTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    // Right-side indicator area — fixed-width slots, positions never shift
                    Spacer(modifier = Modifier.width(6.dp))
                    // Bookmark slot
                    Box(
                        modifier = Modifier.size(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (entry.hasBookmark) {
                            Icon(
                                Icons.Filled.Bookmark,
                                contentDescription = "书签",
                                tint = ReaderTheme.colors.primary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                    // Current-chapter slot
                    Box(
                        modifier = Modifier.size(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (entry.isCurrent) {
                            Icon(
                                Icons.Filled.MyLocation,
                                contentDescription = "当前阅读位置",
                                tint = ReaderTheme.colors.primary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                    // Progress bar slot
                    Box(
                        modifier = Modifier.size(40.dp, 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (entry.progress != null) {
                            LinearProgressIndicator(
                                progress = { entry.progress.coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .width(40.dp)
                                    .height(4.dp)
                                    .clip(CircleShape)
                                    .semantics { contentDescription = "章节进度，${(entry.progress * 100).toInt()}%" },
                                color = ReaderTheme.colors.primary,
                                trackColor = ReaderTheme.colors.mutedTrack,
                                drawStopIndicator = {}
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(ReaderTheme.spacing.sm))
        Text(
            "当前阅读章节：$currentChapter",
            color = ReaderTheme.colors.bodyText,
            style = ReaderTheme.typography.bookMeta
        )
    }
}

// ── TTS overlay ──

@Composable
fun ReaderTtsOverlay(
    isPlaying: Boolean,
    currentTime: String,
    totalTime: String,
    progress: Float,
    speed: Float,
    volume: Float,
    currentChapterTitle: String,
    modifier: Modifier = Modifier,
    onPause: () -> Unit = {},
    onStop: () -> Unit = {},
    onSpeedChange: (Float) -> Unit = {},
    onVolumeChange: (Float) -> Unit = {},
    onDismiss: () -> Unit = {}
) {
    ReaderBottomPanel(
        modifier = modifier,
        contentDescription = "朗读弹窗"
    ) {
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            // Status header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(ReaderTheme.shapes.small)
                    .border(1.dp, ReaderTheme.colors.controlBorder, ReaderTheme.shapes.small)
                    .background(ReaderTheme.colors.paperBg.copy(alpha = 0.28f))
                    .padding(ReaderTheme.spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "朗读",
                    modifier = Modifier.weight(1f).semantics { heading() },
                    color = ReaderTheme.colors.controlInk,
                    style = ReaderTheme.typography.sectionTitle
                )
                Text(
                    if (isPlaying) "正在朗读" else "已暂停",
                    color = ReaderTheme.colors.bodyText,
                    style = ReaderTheme.typography.bookMeta
                )
            }
            Spacer(modifier = Modifier.height(ReaderTheme.spacing.xs))
            Text("当前章节：$currentChapterTitle", color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.bookMeta)

            Spacer(modifier = Modifier.height(ReaderTheme.spacing.md))

            // Playback controls
            TtsGroup(title = "主播放控制") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)
                ) {
                    ReaderPrimaryButton(if (isPlaying) "暂停" else "播放", onPause, Modifier.weight(1f))
                    ReaderSecondaryButton("停止", onStop, Modifier.weight(1f))
                }
            }

            // Progress
            TtsGroup {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(currentTime, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.bookMeta)
                    Text(totalTime, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.bookMeta)
                }
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(CircleShape)
                        .semantics { contentDescription = "朗读进度，${(progress * 100).toInt()}%" },
                    color = ReaderTheme.colors.primary,
                    trackColor = ReaderTheme.colors.mutedTrack,
                    drawStopIndicator = {}
                )
            }

            // Parameters
            TtsGroup(title = "朗读参数") {
                TtsSliderRow("语速", speed, 0.5f..3f, onSpeedChange)
                TtsSliderRow("音量", volume, 0f..1f, onVolumeChange)
                TtsSettingRow("定时关闭", "不开启")
                TtsSettingRow("朗读音色", "温和女声")
            }
        }
    }
}

@Composable
private fun TtsGroup(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(ReaderTheme.shapes.small)
            .border(1.dp, ReaderTheme.colors.controlBorder, ReaderTheme.shapes.small)
            .padding(ReaderTheme.spacing.sm)
    ) {
        if (title != null) {
            Text(
                title,
                color = ReaderTheme.colors.controlInk,
                style = ReaderTheme.typography.readerControlLabel.copy(
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
            )
            Spacer(modifier = Modifier.height(ReaderTheme.spacing.xs))
        }
        content()
    }
}

@Composable
private fun TtsSliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onValueChange: (Float) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.bookTitle)
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = ReaderTheme.colors.primary,
                activeTrackColor = ReaderTheme.colors.primary,
                inactiveTrackColor = ReaderTheme.colors.mutedTrack
            ),
            modifier = Modifier.semantics { contentDescription = label }
        )
    }
}

@Composable
private fun TtsSettingRow(label: String, currentValue: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = SettingRowMinHeight)
            .clickable(role = Role.Button) { }
            .semantics { contentDescription = "$label，当前$currentValue" },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.bookTitle)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(currentValue, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.bookMeta)
            Spacer(modifier = Modifier.width(4.dp))
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = ReaderTheme.colors.controlInk, modifier = Modifier.size(SettingRowIconSize))
        }
    }
}

// ── Appearance overlay ──

@Composable
fun ReaderAppearanceOverlay(
    fontName: String,
    fontSize: String,
    letterSpacing: String,
    scriptMode: String,
    indent: String,
    lineSpacing: String,
    paragraphSpacing: String,
    infoDisplay: String,
    pageFlipAnimation: String,
    themeName: String,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit = {}
) {
    ReaderBottomPanel(
        modifier = modifier,
        contentDescription = "界面弹窗"
    ) {
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            AppGroup("文字") {
                AppSettingRow("字体", fontName)
                AppSettingRow("字号", fontSize)
                AppSettingRow("字距", letterSpacing)
                AppSettingRow("繁简", scriptMode)
            }
            AppGroup("段落") {
                AppSettingRow("缩进", indent)
                AppSettingRow("行距", lineSpacing)
                AppSettingRow("段距", paragraphSpacing)
            }
            AppGroup("界面") {
                AppSettingRow("信息", infoDisplay)
                AppSettingRow("翻页动画", pageFlipAnimation)
                AppSettingRow("主题", themeName)
            }
        }
    }
}

@Composable
private fun AppGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(ReaderTheme.shapes.readerOverlay)
            .border(1.dp, ReaderTheme.colors.controlBorder, ReaderTheme.shapes.readerOverlay)
            .padding(ReaderTheme.spacing.sm)
    ) {
        Text(
            title,
            color = ReaderTheme.colors.controlInk,
            style = ReaderTheme.typography.readerControlLabel.copy(
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )
        )
        Spacer(modifier = Modifier.height(ReaderTheme.spacing.xs))
        content()
    }
}

@Composable
private fun AppSettingRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.bookTitle)
        Text(value, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.bookMeta)
    }
}

// Unified setting row dimensional tokens
private val SettingRowIconSize = 20.dp
private val SettingRowMinHeight = 44.dp

// ── Settings overlay (reading behavior only!) ──

@Composable
fun ReaderSettingsOverlay(
    items: List<AppSettingItem>,
    switches: List<AppSwitchItem>,
    modifier: Modifier = Modifier,
    onSwitchChange: (Int, Boolean) -> Unit = { _, _ -> },
    onDismiss: () -> Unit = {}
) {
    ReaderBottomPanel(
        modifier = modifier,
        contentDescription = "设置弹窗"
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "设置",
                modifier = Modifier.semantics { heading() },
                color = ReaderTheme.colors.controlInk,
                style = ReaderTheme.typography.sectionTitle
            )
            Spacer(modifier = Modifier.width(ReaderTheme.spacing.sm))
            Text("阅读行为", color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.bookMeta)
        }

        Spacer(modifier = Modifier.height(ReaderTheme.spacing.sm))

        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = SettingRowMinHeight)
                        .semantics { contentDescription = "${item.name}，${item.value}" },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(item.name, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.bookTitle)
                    Text(item.value, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.bookMeta)
                }
            }
            switches.forEachIndexed { index, sw ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = SettingRowMinHeight)
                        .semantics { contentDescription = "${sw.name}，${if (sw.checked) "已开启" else "已关闭"}" },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(sw.name, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.bookTitle, modifier = Modifier.weight(1f))
                    ReaderSwitch(
                        checked = sw.checked,
                        onCheckedChange = { onSwitchChange(index, it) },
                        modifier = Modifier.size(40.dp, 24.dp)
                    )
                }
            }
        }
    }
}

// ── Shared panel container ──

@Composable
private fun ReaderBottomPanel(
    contentDescription: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(ReaderTheme.shapes.readerOverlay)
            .background(ReaderTheme.colors.floatingControlBg)
            .border(1.dp, ReaderTheme.colors.controlBorder, ReaderTheme.shapes.readerOverlay)
            .semantics { this.contentDescription = contentDescription }
            .padding(ReaderTheme.spacing.sm)
    ) {
        content()
    }
}
