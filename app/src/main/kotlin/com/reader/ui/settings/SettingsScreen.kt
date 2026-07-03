package com.reader.ui.settings

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reader.android.R
import com.reader.ui.theme.ReaderShapes
import com.reader.ui.theme.ReaderTextStyles
import com.reader.ui.theme.readerExtraColors

@Composable
fun SettingsScreen(
    reducedMotion: Boolean,
    onReducedMotionChange: (Boolean) -> Unit,
    onGeneralSettings: () -> Unit,
    onBookshelfSettings: () -> Unit,
    onSourceManagement: () -> Unit,
    onSyncBackup: () -> Unit,
    onAboutFeedback: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        SettingsTopBar()
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 118.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                SettingsSection(title = "设置") {
                    SettingsEntryRow(
                        iconRes = R.drawable.reader_ic_gear,
                        title = "通用设置",
                        onClick = onGeneralSettings,
                        side = { SettingsChevron() }
                    )
                    SettingsEntryRow(
                        iconRes = R.drawable.reader_ic_bookshelf,
                        title = "书架与搜索设置",
                        onClick = onBookshelfSettings,
                        side = { SettingsChevron() }
                    )
                    SettingsEntryRow(
                        iconRes = R.drawable.reader_ic_source_stack,
                        title = "书源管理",
                        onClick = onSourceManagement,
                        side = { SettingsChevron() }
                    )
                    SettingsEntryRow(
                        iconRes = R.drawable.reader_ic_sync,
                        title = "同步与备份",
                        onClick = onSyncBackup,
                        side = { SettingsChevron() }
                    )
                    SettingsEntryRow(
                        iconRes = R.drawable.reader_ic_info,
                        title = "关于与反馈",
                        onClick = onAboutFeedback,
                        side = { SettingsChevron() }
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsTopBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 58.dp)
            .padding(top = 6.dp, start = 20.dp, end = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "设置",
            style = ReaderTextStyles.appBarTitle,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    val extra = readerExtraColors()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = settingsSectionStyle(),
            color = MaterialTheme.colorScheme.onBackground
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f), ReaderShapes.lg)
                .border(1.dp, extra.hairline, ReaderShapes.lg)
                .padding(horizontal = 10.dp),
            content = content
        )
    }
}

@Composable
private fun SettingsEntryRow(
    @DrawableRes iconRes: Int,
    title: String,
    meta: String? = null,
    onClick: (() -> Unit)? = null,
    side: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 58.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 9.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SettingsIconBox(iconRes)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = title,
                style = settingsRowTitleStyle(),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (meta != null) {
                Text(
                    text = meta,
                    style = settingsRowMetaStyle(),
                    color = readerExtraColors().muted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        side()
    }
}

@Composable
private fun SettingsIconBox(@DrawableRes iconRes: Int) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(28.dp)
            .background(colors.primary.copy(alpha = 0.11f), ReaderShapes.pill),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = colors.primary,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun SettingsValue(text: String) {
    Text(
        text = text,
        style = settingsSideStyle(),
        color = MaterialTheme.colorScheme.primary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.width(84.dp),
        textAlign = TextAlign.End
    )
}

@Composable
private fun SettingsActionLabel(text: String) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .defaultMinSize(minHeight = 30.dp)
            .background(colors.primary.copy(alpha = 0.10f), ReaderShapes.md)
            .border(1.dp, colors.primary.copy(alpha = 0.22f), ReaderShapes.md)
            .padding(horizontal = 11.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = settingsSideStyle(),
            color = colors.primary,
            maxLines = 1
        )
    }
}

@Composable
private fun SettingsChevron() {
    Icon(
        painter = painterResource(id = R.drawable.reader_ic_chevron),
        contentDescription = null,
        tint = readerExtraColors().muted,
        modifier = Modifier.size(18.dp)
    )
}

@Composable
private fun SettingsSwitch(enabled: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Box(
        modifier = Modifier
            .size(width = 38.dp, height = 22.dp)
            .clip(ReaderShapes.pill)
            .background(if (enabled) colors.primary else extra.metaBackground)
            .border(1.dp, if (enabled) colors.primary else extra.hairline, ReaderShapes.pill)
            .clickable(onClick = onClick)
            .padding(2.dp),
        contentAlignment = if (enabled) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .background(Color.White, ReaderShapes.pill)
        )
    }
}

@Composable
private fun SettingsSegment(
    options: List<String>,
    selected: String,
    onSelected: (String) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { option ->
            val active = selected == option
            val colors = MaterialTheme.colorScheme
            val extra = readerExtraColors()
            Box(
                modifier = Modifier
                    .defaultMinSize(minHeight = 30.dp)
                    .background(if (active) colors.primary else extra.metaBackground, ReaderShapes.pill)
                    .border(1.dp, if (active) colors.primary else extra.hairline, ReaderShapes.pill)
                    .clickable { onSelected(option) }
                    .padding(horizontal = 9.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = option,
                    style = settingsSideStyle().copy(fontSize = 10.sp),
                    color = if (active) colors.onPrimary else colors.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SettingsBadge(label: String, tone: SettingsTone) {
    val tint = when (tone) {
        SettingsTone.Good -> readerExtraColors().forest
        SettingsTone.Warn -> readerExtraColors().danger
        SettingsTone.Info -> MaterialTheme.colorScheme.primary
        SettingsTone.Muted -> readerExtraColors().muted
    }
    Box(
        modifier = Modifier
            .defaultMinSize(minHeight = 28.dp)
            .background(tint.copy(alpha = 0.10f), ReaderShapes.pill)
            .border(1.dp, tint.copy(alpha = 0.28f), ReaderShapes.pill)
            .padding(horizontal = 9.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = settingsSideStyle().copy(fontSize = 10.sp),
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SettingsMetricGrid() {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingsMetricCard(
            iconRes = R.drawable.reader_ic_bookshelf,
            value = "0",
            label = "书架书籍",
            modifier = Modifier.weight(1f)
        )
        SettingsMetricCard(
            iconRes = R.drawable.reader_ic_add,
            value = "未接入",
            label = "书源状态",
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SettingsMetricCard(
    @DrawableRes iconRes: Int,
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    val extra = readerExtraColors()
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = 56.dp)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f), ReaderShapes.lg)
            .border(1.dp, extra.hairline, ReaderShapes.lg)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SettingsIconBox(iconRes)
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = value,
                style = settingsMetricStyle(),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = label,
                style = settingsRowMetaStyle(),
                color = extra.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private enum class SettingsTone {
    Good,
    Warn,
    Info,
    Muted
}

private fun settingsSectionStyle() = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = 13.sp,
    lineHeight = 16.sp,
    fontWeight = FontWeight(900)
)

private fun settingsRowTitleStyle() = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = 13.sp,
    lineHeight = 16.sp,
    fontWeight = FontWeight(800)
)

private fun settingsRowMetaStyle() = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = 10.sp,
    lineHeight = 13.sp,
    fontWeight = FontWeight(500)
)

private fun settingsSideStyle() = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = 11.sp,
    lineHeight = 13.sp,
    fontWeight = FontWeight(900),
    textAlign = TextAlign.Center
)

private fun settingsMetricStyle() = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = 15.sp,
    lineHeight = 18.sp,
    fontWeight = FontWeight(900)
)
