package com.reader.ui.source

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.reader.android.R
import com.reader.ui.shell.ReaderUiIntent
import com.reader.ui.theme.ReaderShapes
import com.reader.ui.theme.ReaderTextStyles
import com.reader.ui.theme.readerExtraColors

/**
 * W3: 书源详情页（source-detail）— 原生 Compose 实现，替代 Demo 占位。
 *
 * 从书源管理列表条目推入。展示书源元数据（名称/URL/分组/启用状态/规则数），
 * 提供编辑/调试/删除入口。
 */
@Composable
fun SourceDetailScreen(
    onBack: () -> Unit,
    onEdit: () -> Unit = {},
    onDebug: () -> Unit = {},
    onDelete: () -> Unit = {},
    dispatch: (ReaderUiIntent) -> Unit = {}
) {
    val extra = readerExtraColors()
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
                text = "书源详情",
                style = ReaderTextStyles.appBarTitle,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clickable(onClick = onEdit),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.reader_ic_edit),
                    contentDescription = "编辑",
                    tint = extra.controlInk
                )
            }
        }

        // Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SourceDetailSection("基本信息") {
                SourceDetailRow(title = "书源名称", value = "优书网")
                SourceDetailDivider()
                SourceDetailRow(title = "书源 URL", value = "https://www.youshuw.com")
                SourceDetailDivider()
                SourceDetailRow(title = "书源分组", value = "默认")
                SourceDetailDivider()
                SourceDetailRow(title = "启用状态", value = "已启用")
            }

            SourceDetailSection("书源信息") {
                SourceDetailRow(title = "搜索规则数", value = "3")
                SourceDetailDivider()
                SourceDetailRow(title = "发现规则数", value = "2")
                SourceDetailDivider()
                SourceDetailRow(title = "详情规则数", value = "5")
                SourceDetailDivider()
                SourceDetailRow(title = "目录规则数", value = "4")
                SourceDetailDivider()
                SourceDetailRow(title = "正文规则数", value = "8")
            }

            SourceDetailSection("操作") {
                SourceDetailActionRow(
                    iconRes = R.drawable.reader_ic_edit,
                    title = "编辑书源",
                    onClick = onEdit
                )
                SourceDetailDivider()
                SourceDetailActionRow(
                    iconRes = R.drawable.reader_ic_bug,
                    title = "调试书源",
                    onClick = onDebug
                )
                SourceDetailDivider()
                SourceDetailActionRow(
                    iconRes = R.drawable.reader_ic_trash,
                    title = "删除书源",
                    onClick = onDelete
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SourceDetailSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Text(
        text = title,
        style = ReaderTextStyles.sectionTitle,
        color = readerExtraColors().muted,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp)
    )
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
private fun SourceDetailRow(title: String, value: String) {
    val extra = readerExtraColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = ReaderTextStyles.continueAction,
            color = extra.controlInk,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = ReaderTextStyles.tabLabel,
            color = extra.muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SourceDetailActionRow(
    iconRes: Int,
    title: String,
    onClick: () -> Unit
) {
    val extra = readerExtraColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = extra.controlInk,
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = title,
            style = ReaderTextStyles.continueAction,
            color = extra.controlInk,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SourceDetailDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(1.dp)
            .background(readerExtraColors().hairline)
    )
}
