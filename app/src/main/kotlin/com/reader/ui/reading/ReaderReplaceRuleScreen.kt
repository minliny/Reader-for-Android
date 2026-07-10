package com.reader.ui.reading

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.reader.ui.shell.ReplaceRule
import com.reader.ui.theme.ReaderShapes
import com.reader.ui.theme.ReaderTextStyles
import com.reader.ui.theme.readerExtraColors

/**
 * W5: 内容替换规则管理页（reader-full-content-replacement）— 原生 Compose 实现。
 *
 * 从阅读器设置面板"内容替换"入口推入。展示规则列表，支持新增/编辑/删除/启用切换。
 * CRUD 操作通过 dispatch 接 reducer，规则列表来自 ReaderUiState.replaceRules。
 */
@Composable
fun ReaderReplaceRuleScreen(
    onBack: () -> Unit,
    rules: List<ReplaceRule> = emptyList(),
    dispatch: (ReaderUiIntent) -> Unit = {}
) {
    val extra = readerExtraColors()
    var showAddDialog by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<ReplaceRule?>(null) }

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
                text = "内容替换",
                style = ReaderTextStyles.appBarTitle,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clickable { showAddDialog = true; editingRule = null },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.reader_ic_add),
                    contentDescription = "新增规则",
                    tint = extra.controlPrimary
                )
            }
        }

        // Rules list
        if (rules.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "暂无替换规则\n点击右上角添加",
                    style = ReaderTextStyles.emptyBody,
                    color = extra.muted,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(rules) { rule ->
                    ReplaceRuleRow(
                        rule = rule,
                        onToggle = { dispatch(ReaderUiIntent.ReplaceRuleToggle(id = rule.id)) },
                        onEdit = { editingRule = rule; showAddDialog = true },
                        onDelete = { dispatch(ReaderUiIntent.ReplaceRuleDelete(id = rule.id)) }
                    )
                }
            }
        }
    }

    // Add/Edit dialog
    if (showAddDialog) {
        ReplaceRuleEditDialog(
            rule = editingRule,
            onDismiss = { showAddDialog = false; editingRule = null },
            onSave = { name, pattern, replacement, scope ->
                if (editingRule != null) {
                    dispatch(ReaderUiIntent.ReplaceRuleUpdate(
                        id = editingRule!!.id,
                        name = name,
                        pattern = pattern,
                        replacement = replacement,
                        scope = scope
                    ))
                } else {
                    dispatch(ReaderUiIntent.ReplaceRuleAdd(
                        name = name,
                        pattern = pattern,
                        replacement = replacement,
                        scope = scope
                    ))
                }
                showAddDialog = false
                editingRule = null
            }
        )
    }
}

@Composable
private fun ReplaceRuleRow(
    rule: ReplaceRule,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val extra = readerExtraColors()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(color = MaterialTheme.colorScheme.surface, shape = ReaderShapes.md)
            .border(width = 1.dp, color = extra.hairline, shape = ReaderShapes.md)
            .clip(ReaderShapes.md)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = rule.name,
                style = ReaderTextStyles.continueAction,
                color = if (rule.enabled) extra.controlInk else extra.muted,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            // Toggle switch
            Box(
                modifier = Modifier
                    .size(width = 44.dp, height = 24.dp)
                    .clickable(onClick = onToggle)
                    .background(
                        color = if (rule.enabled) extra.controlPrimary else extra.controlDisabledBg,
                        shape = CircleShape
                    )
                    .clip(CircleShape),
                contentAlignment = Alignment.CenterStart
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .padding(start = if (rule.enabled) 22.dp else 2.dp)
                        .background(
                            color = androidx.compose.ui.graphics.Color.White,
                            shape = CircleShape
                        )
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "模式: ${rule.pattern}",
                style = ReaderTextStyles.tabLabel,
                color = extra.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(ReaderShapes.sm)
                    .background(extra.controlActiveSoft, ReaderShapes.sm)
                    .clickable(onClick = onEdit)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "编辑",
                    style = ReaderTextStyles.tabLabel,
                    color = extra.controlPrimary
                )
            }
            Box(
                modifier = Modifier
                    .clip(ReaderShapes.sm)
                    .background(extra.danger.copy(alpha = 0.1f), ReaderShapes.sm)
                    .clickable(onClick = onDelete)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "删除",
                    style = ReaderTextStyles.tabLabel,
                    color = extra.danger
                )
            }
        }
    }
}

@Composable
private fun ReplaceRuleEditDialog(
    rule: ReplaceRule?,
    onDismiss: () -> Unit,
    onSave: (name: String, pattern: String, replacement: String, scope: String) -> Unit
) {
    var name by remember(rule?.id) { mutableStateOf(rule?.name ?: "") }
    var pattern by remember(rule?.id) { mutableStateOf(rule?.pattern ?: "") }
    var replacement by remember(rule?.id) { mutableStateOf(rule?.replacement ?: "") }
    var scope by remember(rule?.id) { mutableStateOf(rule?.scope ?: "all") }

    val extra = readerExtraColors()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f))
            .clickable(onClick = onDismiss)
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 32.dp)
                .fillMaxWidth()
                .background(extra.paper, ReaderShapes.lg)
                .border(1.dp, extra.hairline, ReaderShapes.lg)
                .clip(ReaderShapes.lg)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (rule == null) "新增替换规则" else "编辑替换规则",
                style = ReaderTextStyles.appBarTitle,
                color = extra.controlInk
            )
            RuleEditField(label = "名称", value = name, onValueChange = { name = it })
            RuleEditField(label = "正则模式", value = pattern, onValueChange = { pattern = it })
            RuleEditField(label = "替换为", value = replacement, onValueChange = { replacement = it })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("all" to "全部", "title" to "标题", "content" to "正文").forEach { (id, label) ->
                    Box(
                        modifier = Modifier
                            .clip(ReaderShapes.sm)
                            .background(
                                if (scope == id) extra.controlPrimary else extra.controlActiveSoft,
                                ReaderShapes.sm
                            )
                            .clickable { scope = id }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = label,
                            style = ReaderTextStyles.tabLabel,
                            color = if (scope == id) androidx.compose.ui.graphics.Color.White else extra.controlInk
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(ReaderShapes.sm)
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text("取消", style = ReaderTextStyles.continueAction, color = extra.muted)
                }
                Box(
                    modifier = Modifier
                        .clip(ReaderShapes.sm)
                        .background(extra.controlPrimary, ReaderShapes.sm)
                        .clickable {
                            if (name.isNotBlank() && pattern.isNotBlank()) {
                                onSave(name, pattern, replacement, scope)
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text("保存", style = ReaderTextStyles.continueAction, color = androidx.compose.ui.graphics.Color.White)
                }
            }
        }
    }
}

@Composable
private fun RuleEditField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    val extra = readerExtraColors()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = ReaderTextStyles.tabLabel,
            color = extra.muted
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(extra.controlField, ReaderShapes.sm)
                .border(1.dp, extra.controlLineStrong, ReaderShapes.sm)
                .clip(ReaderShapes.sm)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = TextStyle(
                    fontSize = 15.sp,
                    color = extra.controlInk
                ),
                cursorBrush = SolidColor(extra.controlPrimary),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

