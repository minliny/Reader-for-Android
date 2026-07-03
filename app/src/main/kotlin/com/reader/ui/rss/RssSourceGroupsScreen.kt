package com.reader.ui.rss

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reader.android.R
import com.reader.ui.theme.ReaderShapes
import com.reader.ui.theme.ReaderTextStyles
import com.reader.ui.theme.readerExtraColors

@Composable
fun RssSourceGroupsScreen(
    onBack: () -> Unit,
    onEditGroup: () -> Unit,
    onSave: () -> Unit
) {
    val groups = remember { rssSourceGroups() }
    val enabledState = remember {
        mutableStateMapOf<String, Boolean>().apply {
            groups.forEach { group -> put(group.name, group.enabled) }
        }
    }
    RssSourceGroupScaffold(
        title = "RSS 分组",
        onBack = onBack,
        bottom = {
            RssSourceGroupBottomActions(
                secondary = "取消",
                primary = "保存",
                onSecondary = onBack,
                onPrimary = onSave
            )
        }
    ) {
        item {
            RssSourceGroupList(
                groups = groups,
                enabledState = { group -> enabledState[group.name] ?: group.enabled },
                onToggle = { group -> enabledState[group.name] = !(enabledState[group.name] ?: group.enabled) }
            )
        }
        item {
            RssSourceGroupActionRow(onEditGroup = onEditGroup)
        }
    }
}

@Composable
fun RssSourceGroupEditScreen(
    onBack: () -> Unit,
    onSave: () -> Unit
) {
    val fields = remember { rssSourceGroupEditFields() }
    RssSourceGroupScaffold(
        title = "编辑 RSS 分组",
        onBack = onBack,
        bottom = {
            RssSourceGroupBottomActions(
                secondary = "取消",
                primary = "保存",
                onSecondary = onBack,
                onPrimary = onSave
            )
        }
    ) {
        item {
            RssSourceGroupEditList(fields = fields)
        }
    }
}

@Composable
private fun RssSourceGroupScaffold(
    title: String,
    onBack: () -> Unit,
    bottom: @Composable () -> Unit,
    content: LazyListScope.() -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        Column(Modifier.fillMaxSize()) {
            RssSourceGroupTopBar(title = title, onBack = onBack)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 108.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = content
            )
        }
        Box(modifier = Modifier.align(Alignment.BottomCenter)) {
            bottom()
        }
    }
}

@Composable
private fun RssSourceGroupTopBar(title: String, onBack: () -> Unit) {
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
private fun RssSourceGroupList(
    groups: List<RssSourceGroup>,
    enabledState: (RssSourceGroup) -> Boolean,
    onToggle: (RssSourceGroup) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface.copy(alpha = 0.92f), ReaderShapes.md)
            .border(1.dp, extra.hairline.copy(alpha = 0.72f), ReaderShapes.md)
    ) {
        groups.forEachIndexed { index, group ->
            RssSourceGroupRow(
                group = group,
                enabled = enabledState(group),
                onToggle = { onToggle(group) }
            )
            if (index != groups.lastIndex) RssSourceGroupDivider()
        }
    }
}

@Composable
private fun RssSourceGroupRow(
    group: RssSourceGroup,
    enabled: Boolean,
    onToggle: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .padding(horizontal = 11.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RssSourceGroupIconCircle(iconRes = R.drawable.reader_ic_folder)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = group.name,
                style = rssSourceGroupTitleStyle(),
                color = colors.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = group.meta,
                style = rssSourceGroupMetaStyle(),
                color = extra.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        RssSourceGroupSwitch(checked = enabled, onChecked = onToggle)
    }
}

@Composable
private fun RssSourceGroupActionRow(onEditGroup: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        RssSourceGroupActionButton(
            label = "新增分组",
            iconRes = R.drawable.reader_ic_add,
            onClick = onEditGroup,
            modifier = Modifier.weight(1f)
        )
        RssSourceGroupActionButton(
            label = "重命名",
            iconRes = R.drawable.reader_ic_edit,
            onClick = onEditGroup,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun RssSourceGroupEditList(fields: List<RssSourceGroupEditField>) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface.copy(alpha = 0.92f), ReaderShapes.md)
            .border(1.dp, extra.hairline.copy(alpha = 0.72f), ReaderShapes.md)
    ) {
        fields.forEachIndexed { index, field ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 56.dp)
                    .padding(horizontal = 11.dp, vertical = 9.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(text = "分组配置", style = rssSourceGroupMetaStyle(), color = extra.muted, maxLines = 1)
                Text(
                    text = field.label,
                    style = rssSourceGroupTitleStyle(),
                    color = colors.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = field.value,
                    style = rssSourceGroupMetaStyle(),
                    color = extra.infoLayer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (index != fields.lastIndex) RssSourceGroupDivider()
        }
    }
}

@Composable
private fun RssSourceGroupBottomActions(
    secondary: String,
    primary: String,
    onSecondary: () -> Unit,
    onPrimary: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.background.copy(alpha = 0.94f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(extra.hairline.copy(alpha = 0.38f))
        )
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RssSourceGroupBottomButton(
                label = secondary,
                primary = false,
                onClick = onSecondary,
                modifier = Modifier.weight(1f)
            )
            RssSourceGroupBottomButton(
                label = primary,
                primary = true,
                onClick = onPrimary,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun RssSourceGroupBottomButton(
    label: String,
    primary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 44.dp)
            .background(if (primary) colors.primary else colors.surface.copy(alpha = 0.92f), ReaderShapes.pill)
            .border(1.dp, if (primary) colors.primary else extra.hairline, ReaderShapes.pill)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = TextStyle(
                fontFamily = FontFamily.Default,
                fontSize = 13.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight(850)
            ),
            color = if (primary) colors.onPrimary else extra.primaryDark,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun RssSourceGroupActionButton(
    label: String,
    @DrawableRes iconRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = 34.dp)
            .background(colors.primary.copy(alpha = 0.10f), ReaderShapes.pill)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = extra.primaryDark,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = label,
            style = rssSourceGroupButtonStyle(),
            color = extra.primaryDark,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun RssSourceGroupSwitch(checked: Boolean, onChecked: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Box(
        modifier = Modifier
            .size(width = 38.dp, height = 22.dp)
            .background(if (checked) colors.primary else extra.hairline.copy(alpha = 0.68f), ReaderShapes.pill)
            .clickable(onClick = onChecked)
            .padding(2.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = if (checked) 16.dp else 0.dp)
                .size(18.dp)
                .background(colors.surface, ReaderShapes.pill)
        )
    }
}

@Composable
private fun RssSourceGroupIconCircle(@DrawableRes iconRes: Int) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Box(
        modifier = Modifier
            .size(28.dp)
            .background(colors.primary.copy(alpha = 0.10f), ReaderShapes.pill),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = extra.primaryDark,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun RssSourceGroupDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(readerExtraColors().hairline.copy(alpha = 0.48f))
    )
}

private data class RssSourceGroup(
    val name: String,
    val meta: String,
    val enabled: Boolean
)

private data class RssSourceGroupEditField(
    val label: String,
    val value: String
)

private fun rssSourceGroups() = listOf(
    RssSourceGroup("开源项目", "2 个订阅源 · 默认展开", enabled = true),
    RssSourceGroup("社区", "1 个订阅源 · 有 12 条未读", enabled = true),
    RssSourceGroup("维护", "1 个订阅源 · 需要登录", enabled = true),
    RssSourceGroup("系统", "1 个订阅源 · 已暂停", enabled = false)
)

private fun rssSourceGroupEditFields() = listOf(
    RssSourceGroupEditField("分组名称", "开源项目"),
    RssSourceGroupEditField("默认展开", "开启"),
    RssSourceGroupEditField("排序规则", "未读优先，其次最近更新"),
    RssSourceGroupEditField("适用订阅源", "GitHub Releases、社区 RSS 源合集")
)

private fun rssSourceGroupTitleStyle() = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = 13.sp,
    lineHeight = 16.sp,
    fontWeight = FontWeight(850)
)

private fun rssSourceGroupMetaStyle() = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = 10.sp,
    lineHeight = 14.sp,
    fontWeight = FontWeight(500)
)

private fun rssSourceGroupButtonStyle() = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = 11.sp,
    lineHeight = 13.sp,
    fontWeight = FontWeight(850)
)
