package com.reader.ui.reading

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.reader.android.R
import com.reader.ui.shell.ReaderContext
import com.reader.ui.theme.ReaderShapes
import com.reader.ui.theme.ReaderTextStyles
import com.reader.ui.theme.readerExtraColors

/**
 * P0-Fix7: 阅读设置原生路由 — 不走 Demo 占位。
 *
 * 从阅读器更多菜单 dropdown "更多设置" 入口推入。渲染阅读相关的设置项：
 * 显示（亮度/字号/行距）、翻页（翻页方式/点击翻页）、高级（屏幕方向/沉浸式状态栏）。
 *
 * 设置值暂存本地 state；亮度/字号等通过 dispatch 回写 ReaderContext（后续 Slice 接线）。
 */
@Composable
fun ReaderSettingsScreen(
    onBack: () -> Unit,
    context: ReaderContext? = null,
    onNavigate: (String) -> Unit = {}
) {
    val extra = readerExtraColors()
    var pageMode by remember { mutableStateOf("覆盖") }
    var clickFlip by remember { mutableStateOf(true) }
    var immersiveBar by remember { mutableStateOf(true) }
    var screenOrientation by remember { mutableStateOf("跟随系统") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(extra.paper)
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .defaultMinSize(minHeight = 54.dp)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(width = 34.dp, height = 42.dp)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.reader_ic_chevron_left),
                    contentDescription = "返回",
                    tint = extra.controlInk
                )
            }
            Text(
                text = "阅读设置",
                style = ReaderTextStyles.appBarTitle,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Settings list
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── 显示设置 ──
            SettingsSectionTitle("显示")
            SettingsCard {
                SettingsRow(
                    title = "亮度",
                    value = if (context?.brightnessAuto == true) "自动" else "${((context?.brightness ?: 0f) * 100).toInt()}%"
                )
                SettingsDivider()
                SettingsRow(title = "字号", value = "16pt")
                SettingsDivider()
                SettingsRow(title = "行距", value = "1.5")
            }

            // ── 翻页设置 ──
            SettingsSectionTitle("翻页")
            SettingsCard {
                SettingsRow(
                    title = "翻页方式",
                    value = pageMode,
                    onClick = {
                        pageMode = when (pageMode) {
                            "覆盖" -> "平移"
                            "平移" -> "滑动"
                            else -> "覆盖"
                        }
                    }
                )
                SettingsDivider()
                SettingsToggleRow(
                    title = "点击翻页",
                    checked = clickFlip,
                    onToggle = { clickFlip = it }
                )
            }

            // ── 高级 ──
            SettingsSectionTitle("高级")
            SettingsCard {
                SettingsRow(
                    title = "屏幕方向",
                    value = screenOrientation,
                    onClick = {
                        screenOrientation = when (screenOrientation) {
                            "跟随系统" -> "竖屏"
                            "竖屏" -> "横屏"
                            else -> "跟随系统"
                        }
                    }
                )
                SettingsDivider()
                SettingsToggleRow(
                    title = "沉浸式状态栏",
                    checked = immersiveBar,
                    onToggle = { immersiveBar = it }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        style = ReaderTextStyles.sectionTitle,
        color = readerExtraColors().muted,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp)
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    val extra = readerExtraColors()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(color = MaterialTheme.colorScheme.surface, shape = ReaderShapes.md)
            .border(width = 1.dp, color = extra.hairline, shape = ReaderShapes.md)
            .clip(ReaderShapes.md),
        content = content
    )
}

@Composable
private fun SettingsRow(
    title: String,
    value: String = "",
    onClick: (() -> Unit)? = null
) {
    val extra = readerExtraColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 52.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = ReaderTextStyles.continueAction,
            color = extra.controlInk,
            modifier = Modifier.weight(1f)
        )
        if (value.isNotEmpty()) {
            Text(
                text = value,
                style = ReaderTextStyles.tabLabel,
                color = extra.muted
            )
        }
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val extra = readerExtraColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 52.dp)
            .clickable { onToggle(!checked) }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = ReaderTextStyles.continueAction,
            color = extra.controlInk,
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .size(width = 44.dp, height = 24.dp)
                .clip(CircleShape)
                .background(
                    color = if (checked) MaterialTheme.colorScheme.primary else extra.controlLine
                )
                .clickable { onToggle(!checked) },
            contentAlignment = Alignment.CenterEnd
        ) {
            Box(
                modifier = Modifier
                    .padding(horizontal = 2.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
            )
        }
    }
}

@Composable
private fun SettingsDivider() {
    val extra = readerExtraColors()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .padding(horizontal = 16.dp)
            .background(extra.hairline)
    )
}
