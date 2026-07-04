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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reader.android.R
import com.reader.ui.shell.SettingsShellFrame
import com.reader.ui.theme.ReaderShapes
import com.reader.ui.theme.ReaderTextStyles
import com.reader.ui.theme.readerExtraColors

@Composable
fun SettingsGeneralScreen(
    reducedMotion: Boolean,
    onReducedMotionChange: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    var theme by remember { mutableStateOf("跟随系统") }
    var autoUpdate by remember { mutableStateOf(true) }
    var backToTop by remember { mutableStateOf(true) }
    var crashLog by remember { mutableStateOf(true) }

    SettingsSubpageScaffold(title = "通用设置", onBack = onBack) {
        item {
            SettingsSubSection(title = "基础偏好") {
                SettingsSubRow(
                    iconRes = R.drawable.reader_ic_settings,
                    title = "App主题",
                    side = {
                        SettingsSubSegment(
                            options = listOf("跟随系统", "浅色", "深色"),
                            selected = theme,
                            onSelected = { theme = it }
                        )
                    }
                )
                SettingsSubDivider()
                SettingsSubRow(
                    iconRes = R.drawable.reader_ic_globe,
                    title = "语言",
                    side = { SettingsSubValue("简体中文", chevron = true) }
                )
                SettingsSubDivider()
                SettingsSubRow(
                    iconRes = R.drawable.reader_ic_bookshelf,
                    title = "启动时打开",
                    side = { SettingsSubValue("书架", chevron = true) }
                )
            }
        }
        item {
            SettingsSubSection(title = "行为与反馈") {
                SettingsSubRow(
                    iconRes = R.drawable.reader_ic_refresh,
                    title = "自动检查更新",
                    side = { SettingsSubSwitch(autoUpdate) { autoUpdate = !autoUpdate } }
                )
                SettingsSubDivider()
                SettingsSubRow(
                    iconRes = R.drawable.reader_ic_top,
                    title = "点击当前底栏回顶部",
                    side = { SettingsSubSwitch(backToTop) { backToTop = !backToTop } }
                )
                SettingsSubDivider()
                SettingsSubRow(
                    iconRes = R.drawable.reader_ic_more,
                    title = "减少动态效果",
                    side = { SettingsSubSwitch(reducedMotion) { onReducedMotionChange(!reducedMotion) } }
                )
                SettingsSubDivider()
                SettingsSubRow(
                    iconRes = R.drawable.reader_ic_bug,
                    title = "崩溃日志",
                    side = {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            SettingsSubBadge("已开启", SettingsSubTone.Good)
                            SettingsSubSwitch(crashLog) { crashLog = !crashLog }
                        }
                    }
                )
                SettingsSubDivider()
                SettingsSubRow(
                    iconRes = R.drawable.reader_ic_refresh,
                    title = "动画效果",
                    side = { SettingsSubValue("标准", chevron = true) }
                )
                SettingsSubDivider()
                SettingsSubRow(
                    iconRes = R.drawable.reader_ic_trash,
                    title = "缓存清理",
                    side = { SettingsSubActionLabel("清理缓存") }
                )
            }
        }
        item {
            SettingsSubSection(title = "系统权限") {
                SettingsSubRow(
                    iconRes = R.drawable.reader_ic_folder,
                    title = "文件访问",
                    side = {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            SettingsSubBadge("已授权", SettingsSubTone.Good)
                            SettingsSubActionLabel("去设置")
                        }
                    }
                )
                SettingsSubDivider()
                SettingsSubRow(
                    iconRes = R.drawable.reader_ic_bell,
                    title = "通知权限",
                    side = {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            SettingsSubBadge("未授权", SettingsSubTone.Warn)
                            SettingsSubActionLabel("去设置")
                        }
                    }
                )
                SettingsSubDivider()
                SettingsSubRow(
                    iconRes = R.drawable.reader_ic_gear,
                    title = "电池优化",
                    side = {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            SettingsSubBadge("受系统管理", SettingsSubTone.Info)
                            SettingsSubActionLabel("去设置")
                        }
                    }
                )
            }
        }
        item {
            SettingsSubDangerAction(
                iconRes = R.drawable.reader_ic_refresh,
                title = "恢复默认",
                meta = "恢复后将重置 App 主题、语言、启动页面和行为偏好。"
            )
        }
    }
}

@Composable
fun AboutFeedbackScreen(onBack: () -> Unit) {
    SettingsSubpageScaffold(title = "关于与反馈", onBack = onBack) {
        item {
            SettingsSubSection(title = "项目信息") {
                SettingsSubRow(
                    iconRes = R.drawable.reader_ic_refresh,
                    title = "检查更新",
                    side = { SettingsSubValue("已是最新") }
                )
                SettingsSubDivider()
                SettingsSubRow(
                    iconRes = R.drawable.reader_ic_code,
                    title = "源码仓库",
                    side = { SettingsSubChevron() }
                )
                SettingsSubDivider()
                SettingsSubRow(
                    iconRes = R.drawable.reader_ic_link,
                    title = "开源许可",
                    side = { SettingsSubChevron() }
                )
                SettingsSubDivider()
                SettingsSubRow(
                    iconRes = R.drawable.reader_ic_people,
                    title = "参与贡献",
                    side = { SettingsSubChevron() }
                )
            }
        }
    }
}

@Composable
fun SyncBackupScreen(
    onBack: () -> Unit,
    onWebDavConfig: () -> Unit
) {
    val backups = remember { settingsBackupItems() }
    SettingsSubpageScaffold(title = "同步与备份", onBack = onBack) {
        item {
            SettingsSubSection(title = "WebDAV 配置") {
                SettingsInputRow(R.drawable.reader_ic_link, "服务器地址", "https://dav.example.com/reader/backup")
                SettingsSubDivider()
                SettingsInputRow(R.drawable.reader_ic_people, "账号", "reader@example.com")
                SettingsSubDivider()
                SettingsInputRow(R.drawable.reader_ic_shield, "密码", "reader-demo-password")
                SettingsSubDivider()
                SettingsInputRow(R.drawable.reader_ic_folder, "同步目录", "/ReaderBackup/ReaderAndroid")
            }
        }
        item {
            SettingsSectionActions(
                actions = listOf(
                    SettingsSubAction(R.drawable.reader_ic_refresh, "测试网络连通性"),
                    SettingsSubAction(R.drawable.reader_ic_check, "保存配置", onWebDavConfig)
                )
            )
        }
        item {
            SettingsBackupList(backups = backups)
        }
    }
}

@Composable
fun WebDavConfigScreen(onBack: () -> Unit) {
    SettingsSubpageScaffold(title = "WebDAV 配置", onBack = onBack) {
        item {
            SettingsSubSection(title = "连接信息") {
                SettingsInputRow(R.drawable.reader_ic_link, "服务器地址", "https://dav.example.com/reader/backup")
                SettingsSubDivider()
                SettingsInputRow(R.drawable.reader_ic_people, "账号", "reader@example.com")
                SettingsSubDivider()
                SettingsInputRow(R.drawable.reader_ic_shield, "密码", "reader-demo-password")
                SettingsSubDivider()
                SettingsInputRow(R.drawable.reader_ic_folder, "同步目录", "/ReaderBackup/ReaderAndroid")
            }
        }
        item {
            SettingsSectionActions(
                actions = listOf(
                    SettingsSubAction(R.drawable.reader_ic_refresh, "测试网络连通性"),
                    SettingsSubAction(R.drawable.reader_ic_check, "保存配置")
                )
            )
        }
    }
}

@Composable
fun SourceManagementScreen(
    onBack: () -> Unit,
    onImportSource: () -> Unit
) {
    var enabled by remember { mutableStateOf(true) }
    val sources = remember { settingsSourceItems() }
    SettingsSubpageScaffold(title = "书源管理", onBack = onBack) {
        item {
            SettingsMetricGrid(
                metrics = listOf(
                    SettingsMetric(R.drawable.reader_ic_source_stack, "个书源", "12"),
                    SettingsMetric(R.drawable.reader_ic_check, "个启用", "8"),
                    SettingsMetric(R.drawable.reader_ic_warning, "个异常", "4"),
                    SettingsMetric(R.drawable.reader_ic_clock, "刚刚检测", "10:30")
                )
            )
        }
        item { SettingsSubSearchBox("搜索框：搜索书源名称或域名") }
        item {
            SettingsChipRow(listOf("全部", "已启用", "异常", "未检测", "自定义"), active = "全部")
        }
        item {
            SettingsChipRow(listOf("全部分组", "玄幻书源", "起点导入", "测试书源"), active = "全部分组")
        }
        item {
            SettingsSubSection(title = "批量操作") {
                SettingsSubRow(
                    iconRes = R.drawable.reader_ic_refresh,
                    title = "检测",
                    side = { SettingsSubActionLabel("开始检测") }
                )
                SettingsSubDivider()
                SettingsSubRow(
                    iconRes = R.drawable.reader_ic_link,
                    title = "详情",
                    side = { SettingsSubActionLabel("查看") }
                )
                SettingsSubDivider()
                SettingsSubRow(
                    iconRes = R.drawable.reader_ic_edit,
                    title = "编辑",
                    side = { SettingsSubActionLabel("编辑") }
                )
                SettingsSubDivider()
                SettingsSubRow(
                    iconRes = R.drawable.reader_ic_bug,
                    title = "错误日志",
                    side = { SettingsSubActionLabel("查看") }
                )
                SettingsSubDivider()
                SettingsSubRow(
                    iconRes = R.drawable.reader_ic_source_stack,
                    title = "启用开关",
                    side = { SettingsSubSwitch(enabled) { enabled = !enabled } }
                )
            }
        }
        item { SettingsSourceList(sources = sources) }
        item {
            SettingsSubFloatingAction(
                iconRes = R.drawable.reader_ic_add,
                label = "新增",
                onClick = onImportSource
            )
        }
    }
}

@Composable
private fun SettingsSubpageScaffold(
    title: String,
    onBack: () -> Unit,
    content: LazyListScope.() -> Unit
) {
    SettingsShellFrame(
        backTopBar = { SettingsSubTopBar(title = title, onBack = onBack) },
        settingsContent = {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                content = content
            )
        }
    )
}

@Composable
private fun SettingsSubTopBar(title: String, onBack: () -> Unit) {
    val colors = MaterialTheme.colorScheme
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
                tint = colors.onBackground,
                modifier = Modifier.size(24.dp)
            )
        }
        Text(
            text = title,
            style = ReaderTextStyles.backBarTitle,
            color = colors.onBackground,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.size(44.dp))
    }
}

@Composable
private fun SettingsSubSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = title, style = settingsSubSectionStyle(), color = colors.onBackground)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface.copy(alpha = 0.82f), ReaderShapes.md)
                .border(1.dp, extra.hairline.copy(alpha = 0.72f), ReaderShapes.md),
            content = content
        )
    }
}

@Composable
private fun SettingsSubRow(
    @DrawableRes iconRes: Int,
    title: String,
    meta: String? = null,
    side: @Composable () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 58.dp)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsSubIcon(iconRes = iconRes)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = title,
                style = settingsSubTitleStyle(),
                color = colors.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (meta != null) {
                Text(
                    text = meta,
                    style = settingsSubMetaStyle(),
                    color = extra.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        side()
    }
}

@Composable
private fun SettingsInputRow(@DrawableRes iconRes: Int, title: String, value: String) {
    SettingsSubRow(
        iconRes = iconRes,
        title = title,
        side = {
            Text(
                text = value,
                style = settingsSubControlStyle(),
                color = readerExtraColors().muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(0.48f)
            )
        }
    )
}

@Composable
private fun SettingsSubSegment(
    options: List<String>,
    selected: String,
    onSelected: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .background(readerExtraColors().metaBackground.copy(alpha = 0.72f), ReaderShapes.pill)
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        options.forEach { option ->
            val active = option == selected
            Box(
                modifier = Modifier
                    .defaultMinSize(minHeight = 26.dp)
                    .background(
                        if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface.copy(alpha = 0f),
                        ReaderShapes.pill
                    )
                    .clickable { onSelected(option) }
                    .padding(horizontal = 7.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = option,
                    style = settingsSubControlStyle(),
                    color = if (active) MaterialTheme.colorScheme.onPrimary else readerExtraColors().muted,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun SettingsSubValue(value: String, chevron: Boolean = false) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = value,
            style = settingsSubControlStyle(),
            color = readerExtraColors().primaryDark,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (chevron) SettingsSubChevron()
    }
}

@Composable
private fun SettingsSubChevron() {
    Icon(
        painter = painterResource(id = R.drawable.reader_ic_chevron),
        contentDescription = null,
        tint = readerExtraColors().muted,
        modifier = Modifier.size(14.dp)
    )
}

@Composable
private fun SettingsSubActionLabel(label: String) {
    Box(
        modifier = Modifier
            .defaultMinSize(minHeight = 28.dp)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), ReaderShapes.pill)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = settingsSubControlStyle(),
            color = readerExtraColors().primaryDark,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SettingsSubSwitch(checked: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Box(
        modifier = Modifier
            .size(width = 38.dp, height = 22.dp)
            .background(if (checked) colors.primary else extra.hairline.copy(alpha = 0.68f), ReaderShapes.pill)
            .clickable(onClick = onClick)
            .padding(2.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .background(colors.surface, ReaderShapes.pill)
        )
    }
}

@Composable
private fun SettingsSubBadge(label: String, tone: SettingsSubTone) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    val foreground = when (tone) {
        SettingsSubTone.Good -> extra.forest
        SettingsSubTone.Warn -> extra.danger
        SettingsSubTone.Info -> extra.primaryDark
        SettingsSubTone.Muted -> extra.muted
    }
    val background = when (tone) {
        SettingsSubTone.Good -> extra.forest.copy(alpha = 0.10f)
        SettingsSubTone.Warn -> extra.danger.copy(alpha = 0.10f)
        SettingsSubTone.Info -> colors.primary.copy(alpha = 0.10f)
        SettingsSubTone.Muted -> extra.metaBackground.copy(alpha = 0.72f)
    }
    Box(
        modifier = Modifier
            .defaultMinSize(minHeight = 22.dp)
            .background(background, ReaderShapes.pill)
            .padding(horizontal = 7.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = label, style = settingsSubBadgeStyle(), color = foreground, maxLines = 1)
    }
}

@Composable
private fun SettingsSubDangerAction(
    @DrawableRes iconRes: Int,
    title: String,
    meta: String
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 50.dp)
            .background(colors.error.copy(alpha = 0.08f), ReaderShapes.md)
            .border(1.dp, colors.error.copy(alpha = 0.20f), ReaderShapes.md)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(painter = painterResource(id = iconRes), contentDescription = null, tint = colors.error, modifier = Modifier.size(17.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = title, style = settingsSubTitleStyle(), color = colors.error, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(text = meta, style = settingsSubMetaStyle(), color = colors.error.copy(alpha = 0.74f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SettingsSectionActions(actions: List<SettingsSubAction>) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        actions.forEach { action ->
            Row(
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 42.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.86f), ReaderShapes.md)
                    .border(1.dp, readerExtraColors().hairline.copy(alpha = 0.62f), ReaderShapes.md)
                    .clickable(enabled = action.onClick != null, onClick = action.onClick ?: {})
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(painter = painterResource(id = action.iconRes), contentDescription = null, tint = readerExtraColors().primaryDark, modifier = Modifier.size(16.dp))
                Text(text = action.title, style = settingsSubControlStyle(), color = readerExtraColors().primaryDark, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun SettingsBackupList(backups: List<SettingsBackupItem>) {
    SettingsSubSection(title = "恢复数据") {
        backups.forEachIndexed { index, item ->
            if (index > 0) SettingsSubDivider()
            SettingsSubRow(
                iconRes = item.iconRes,
                title = item.title,
                meta = "${item.source} · ${item.time} · ${item.type}",
                side = {
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        SettingsSubBadge(item.badge, item.tone)
                        Text(text = item.size, style = settingsSubMetaStyle(), color = readerExtraColors().muted, maxLines = 1)
                    }
                }
            )
        }
    }
}

@Composable
private fun SettingsMetricGrid(metrics: List<SettingsMetric>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        metrics.chunked(2).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { metric ->
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 58.dp)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.86f), ReaderShapes.md)
                            .border(1.dp, readerExtraColors().hairline.copy(alpha = 0.62f), ReaderShapes.md)
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SettingsSubIcon(iconRes = metric.iconRes)
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(text = metric.value, style = settingsSubTitleStyle(), color = MaterialTheme.colorScheme.onBackground, maxLines = 1)
                            Text(text = metric.label, style = settingsSubMetaStyle(), color = readerExtraColors().muted, maxLines = 1)
                        }
                    }
                }
                repeat(2 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun SettingsSubSearchBox(placeholder: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 40.dp)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.82f), ReaderShapes.pill)
            .border(1.dp, readerExtraColors().hairline.copy(alpha = 0.62f), ReaderShapes.pill)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(painter = painterResource(id = R.drawable.reader_ic_search), contentDescription = null, tint = readerExtraColors().muted, modifier = Modifier.size(17.dp))
        Text(text = placeholder, style = settingsSubControlStyle(), color = readerExtraColors().muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SettingsChipRow(items: List<String>, active: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items.take(5).forEach { item ->
            val selected = item == active
            Box(
                modifier = Modifier
                    .defaultMinSize(minHeight = 30.dp)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary else readerExtraColors().metaBackground.copy(alpha = 0.84f),
                        ReaderShapes.pill
                    )
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = item,
                    style = settingsSubControlStyle(),
                    color = if (selected) MaterialTheme.colorScheme.onPrimary else readerExtraColors().navInactive,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SettingsSourceList(sources: List<SettingsSourceItem>) {
    SettingsSubSection(title = "书源列表") {
        sources.forEachIndexed { index, source ->
            if (index > 0) SettingsSubDivider()
            SettingsSubRow(
                iconRes = R.drawable.reader_ic_source_stack,
                title = source.title,
                meta = source.meta,
                side = {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        SettingsSubBadge(source.status, source.tone)
                        SettingsSubSwitch(source.enabled) {}
                    }
                }
            )
        }
    }
}

@Composable
private fun SettingsSubFloatingAction(
    @DrawableRes iconRes: Int,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 44.dp)
            .background(MaterialTheme.colorScheme.primary, ReaderShapes.pill)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(painter = painterResource(id = iconRes), contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.size(6.dp))
        Text(text = label, style = settingsSubTitleStyle(), color = MaterialTheme.colorScheme.onPrimary, maxLines = 1)
    }
}

@Composable
private fun SettingsSubIcon(@DrawableRes iconRes: Int) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), ReaderShapes.pill),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = readerExtraColors().primaryDark,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun SettingsSubDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(readerExtraColors().hairline.copy(alpha = 0.42f))
    )
}

private data class SettingsSubAction(
    @DrawableRes val iconRes: Int,
    val title: String,
    val onClick: (() -> Unit)? = null
)

private data class SettingsBackupItem(
    @DrawableRes val iconRes: Int,
    val source: String,
    val title: String,
    val time: String,
    val type: String,
    val size: String,
    val badge: String,
    val tone: SettingsSubTone
)

private data class SettingsMetric(
    @DrawableRes val iconRes: Int,
    val label: String,
    val value: String
)

private data class SettingsSourceItem(
    val title: String,
    val meta: String,
    val status: String,
    val tone: SettingsSubTone,
    val enabled: Boolean
)

private enum class SettingsSubTone { Good, Warn, Info, Muted }

private fun settingsBackupItems() = listOf(
    SettingsBackupItem(R.drawable.reader_ic_sync, "WebDAV", "自动备份", "2026-06-23 08:00", "完整备份", "12.8 MB", "最新", SettingsSubTone.Good),
    SettingsBackupItem(R.drawable.reader_ic_folder, "本地", "手动备份", "2026-06-23 10:30", "完整备份", "12.8 MB", "本机", SettingsSubTone.Info),
    SettingsBackupItem(R.drawable.reader_ic_sync, "WebDAV", "夜间备份", "2026-06-21 22:30", "书架与设置", "8.6 MB", "局部", SettingsSubTone.Warn),
    SettingsBackupItem(R.drawable.reader_ic_folder, "本地", "阅读进度快照", "2026-06-20 09:40", "阅读进度", "2.4 MB", "进度", SettingsSubTone.Muted)
)

private fun settingsSourceItems() = listOf(
    SettingsSourceItem("起点中文网", "qidian.com · 起点导入", "可用", SettingsSubTone.Good, enabled = true),
    SettingsSourceItem("笔趣阁", "biquge.example · 玄幻书源", "异常", SettingsSubTone.Warn, enabled = true),
    SettingsSourceItem("本地导入源", "本地文件导入 · 自定义", "未检测", SettingsSubTone.Muted, enabled = false),
    SettingsSourceItem("测试书源", "test.example · 测试书源", "可用", SettingsSubTone.Good, enabled = true)
)

private fun settingsSubSectionStyle() = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = 13.sp,
    lineHeight = 16.sp,
    fontWeight = FontWeight(900)
)

private fun settingsSubTitleStyle() = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = 13.sp,
    lineHeight = 16.sp,
    fontWeight = FontWeight(850)
)

private fun settingsSubMetaStyle() = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = 10.sp,
    lineHeight = 14.sp,
    fontWeight = FontWeight(500)
)

private fun settingsSubControlStyle() = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = 11.sp,
    lineHeight = 13.sp,
    fontWeight = FontWeight(850)
)

private fun settingsSubBadgeStyle() = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = 10.sp,
    lineHeight = 12.sp,
    fontWeight = FontWeight(850)
)
