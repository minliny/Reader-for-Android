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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reader.android.R
import com.reader.ui.shell.ReaderUiIntent
import com.reader.ui.shell.SourceEditState
import com.reader.ui.theme.ReaderShapes
import com.reader.ui.theme.ReaderTextStyles
import com.reader.ui.theme.readerExtraColors

/**
 * W3: 书源编辑页（source-edit）— 原生 Compose 实现，替代 Demo 占位。
 *
 * 从书源详情页"编辑"入口推入。渲染书源表单（名称/URL/分组/启用/注释），
 * 保存/取消操作通过 dispatch 接 reducer。
 */
@Composable
fun SourceEditScreen(
    onBack: () -> Unit,
    state: SourceEditState = SourceEditState.Idle,
    dispatch: (ReaderUiIntent) -> Unit = {}
) {
    val extra = readerExtraColors()
    val editing = state as? SourceEditState.Editing
    var name by remember(editing?.sourceId) { mutableStateOf(editing?.name ?: "") }
    var url by remember(editing?.sourceId) { mutableStateOf(editing?.url ?: "") }
    var group by remember(editing?.sourceId) { mutableStateOf(editing?.group ?: "") }
    var enabled by remember(editing?.sourceId) { mutableStateOf(editing?.enabled ?: true) }
    var comment by remember(editing?.sourceId) { mutableStateOf(editing?.comment ?: "") }

    val isSaving = state is SourceEditState.Saving
    val errorMessage = (state as? SourceEditState.Error)?.message

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
                    .clickable(onClick = {
                        if (!isSaving) dispatch(ReaderUiIntent.SourceEditCancel)
                    }),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.reader_ic_chevron_left),
                    contentDescription = "返回",
                    tint = extra.controlInk
                )
            }
            Text(
                text = if (editing?.sourceId.isNullOrEmpty()) "新建书源" else "编辑书源",
                style = ReaderTextStyles.appBarTitle,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            // Save button
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clickable(enabled = !isSaving) {
                        dispatch(ReaderUiIntent.SourceEditUpdateField(
                            name = name, url = url, group = group, enabled = enabled, comment = comment
                        ))
                        dispatch(ReaderUiIntent.SourceEditSave)
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isSaving) "保存中" else "保存",
                    style = ReaderTextStyles.tabLabel,
                    color = if (isSaving) extra.muted else extra.controlPrimary
                )
            }
        }

        // Form content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SourceEditSection("基本信息") {
                SourceEditFieldRow(
                    title = "书源名称",
                    value = name,
                    onValueChange = { name = it }
                )
                SourceEditDivider()
                SourceEditFieldRow(
                    title = "书源 URL",
                    value = url,
                    onValueChange = { url = it }
                )
                SourceEditDivider()
                SourceEditFieldRow(
                    title = "书源分组",
                    value = group,
                    onValueChange = { group = it }
                )
            }

            SourceEditSection("状态") {
                SourceEditToggleRow(
                    title = "启用书源",
                    checked = enabled,
                    onToggle = { enabled = it }
                )
            }

            SourceEditSection("注释") {
                SourceEditFieldRow(
                    title = "备注",
                    value = comment,
                    onValueChange = { comment = it }
                )
            }

            // Error message
            if (errorMessage != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(extra.danger.copy(alpha = 0.1f), ReaderShapes.md)
                        .border(1.dp, extra.danger, ReaderShapes.md)
                        .clip(ReaderShapes.md)
                        .padding(12.dp)
                ) {
                    Text(
                        text = errorMessage,
                        style = ReaderTextStyles.tabLabel,
                        color = extra.danger
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SourceEditSection(
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
private fun SourceEditFieldRow(
    title: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    val extra = readerExtraColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = title,
            style = ReaderTextStyles.continueAction,
            color = extra.controlInk,
            modifier = Modifier.width(80.dp)
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = TextStyle(
                fontSize = 15.sp,
                color = extra.controlInk
            ),
            cursorBrush = SolidColor(extra.controlPrimary),
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SourceEditToggleRow(
    title: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val extra = readerExtraColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clickable { onToggle(!checked) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
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
                .background(
                    color = if (checked) extra.controlPrimary else extra.controlDisabledBg,
                    shape = CircleShape
                )
                .clip(CircleShape),
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .padding(start = if (checked) 22.dp else 2.dp)
                    .background(
                        color = androidx.compose.ui.graphics.Color.White,
                        shape = CircleShape
                    )
            )
        }
    }
}

@Composable
private fun SourceEditDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(1.dp)
            .background(readerExtraColors().hairline)
    )
}

