package com.reader.ui.demo

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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.reader.android.R
import com.reader.ui.theme.ReaderShapes
import com.reader.ui.theme.ReaderTextStyles
import com.reader.ui.theme.readerExtraColors

/**
 * W5: 替换规则状态变体原生页面。
 *
 * 替换原 `Contract25RouteScreen` 的 `ReaderContractStateScreen` 通用 scaffold
 * （DemoReaderShell + DemoStateCard）。每个状态渲染阅读 paper 背景 + 顶部栏
 * （返回 + 书名 + 章节上下文），并呈现状态专属内容：
 * - delete-confirm: 删除确认（规则信息 + 取消/确认）
 * - apply-result: 应用结果（成功状态 + 应用规则数 + 原文/替换后片段）
 * - import-export: 导入导出（JSON 预览 + 导入/导出/复制）
 * - preview: 预览（原文/替换后并排对比 + 应用规则列表）
 * - page: 替换页面（规则列表 + 启用/排序/作用范围 + 管理）
 *
 * 不使用固定文本冒充产品页：每个状态的图标、文案、操作均反映该状态的语义。
 */
@Composable
fun ReaderReplaceStateScreen(
    routeId: String,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit
) {
    when (routeId) {
        "reader-replace-delete-confirm" -> ReaderReplaceDeleteConfirmScreen(onBack, onNavigate)
        "reader-replace-apply-result" -> ReaderReplaceApplyResultScreen(onBack, onNavigate)
        "reader-replace-import-export" -> ReaderReplaceImportExportScreen(onBack, onNavigate)
        "reader-replace-preview" -> ReaderReplacePreviewScreen(onBack, onNavigate)
        "reader-replace-page" -> ReaderReplacePageScreen(onBack, onNavigate)
        else -> ReaderReplacePageScreen(onBack, onNavigate)
    }
}

@Composable
private fun ReaderReplaceScaffold(
    subtitle: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit
) {
    val extra = readerExtraColors()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(extra.paper)
    ) {
        // 顶部栏：返回 + 书名 + 章节上下文
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .defaultMinSize(minHeight = 54.dp)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "长夜余火",
                    style = ReaderTextStyles.appBarTitle,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = ReaderTextStyles.infoLayer,
                    color = extra.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // 内容区
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 92.dp, start = 12.dp, end = 12.dp, bottom = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            content()
        }
    }
}

private data class ReplaceRuleRow(
    val name: String,
    val scope: String,
    val enabled: Boolean,
    val order: Int
)

private fun replaceRules(): List<ReplaceRuleRow> = listOf(
    ReplaceRuleRow("雨容称呼", "本书 · 第 32 章", enabled = true, order = 1),
    ReplaceRuleRow("旧称统一", "本书 · 全章", enabled = true, order = 2),
    ReplaceRuleRow("标点清理", "本书 · 全章", enabled = false, order = 3),
    ReplaceRuleRow("广告过滤", "全局 · 所有书源", enabled = true, order = 4)
)

@Composable
private fun ReplaceActionButton(
    label: String,
    primary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val extra = readerExtraColors()
    val bg = if (primary) extra.controlPrimary else extra.controlSurface
    val fg = if (primary) Color.White else extra.controlInk
    val border = if (primary) Color.Transparent else extra.controlLine
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = 44.dp)
            .clip(ReaderShapes.pill)
            .background(bg, ReaderShapes.pill)
            .border(1.dp, border, ReaderShapes.pill)
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = ReaderTextStyles.continueAction,
            color = fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// 1. 删除确认 ----------------------------------------------------------------

/**
 * 删除替换规则确认。展示待删除规则信息，提供取消/确认删除操作。
 */
@Composable
fun ReaderReplaceDeleteConfirmScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit
) {
    val extra = readerExtraColors()
    ReaderReplaceScaffold(subtitle = "替换 · 删除规则确认", onBack = onBack) {
        // 待删除规则信息卡
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ReaderShapes.md)
                .background(extra.controlActiveSoft, ReaderShapes.md)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.reader_ic_replace),
                contentDescription = null,
                tint = extra.controlPrimary,
                modifier = Modifier.size(20.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "雨容称呼",
                    style = ReaderTextStyles.continueAction,
                    color = extra.controlInk
                )
                Text(
                    text = "本书 · 第 32 章 · 规则顺序 1",
                    style = ReaderTextStyles.infoLayer,
                    color = extra.muted
                )
            }
        }

        // 删除确认提示
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ReaderShapes.lg)
                .background(extra.paperBright, ReaderShapes.lg)
                .border(1.dp, extra.controlLine, ReaderShapes.lg)
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(extra.danger.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.reader_ic_trash),
                    contentDescription = null,
                    tint = extra.danger,
                    modifier = Modifier.size(28.dp)
                )
            }
            Text(
                text = "删除替换规则？",
                style = ReaderTextStyles.emptyHeading,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Text(
                text = "删除后不可恢复；其他替换规则和原始正文不会被修改。",
                style = ReaderTextStyles.emptyBody,
                color = extra.muted,
                textAlign = TextAlign.Center
            )
        }

        // 操作按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ReplaceActionButton(
                label = "取消",
                primary = false,
                onClick = { onNavigate("content-replacement") },
                modifier = Modifier.weight(1f)
            )
            ReplaceActionButton(
                label = "确认删除",
                primary = true,
                onClick = { onNavigate("content-replacement") },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// 2. 应用结果 ----------------------------------------------------------------

/**
 * 替换规则应用结果。展示应用成功的规则数与原文/替换后片段对比。
 */
@Composable
fun ReaderReplaceApplyResultScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit
) {
    val extra = readerExtraColors()
    ReaderReplaceScaffold(subtitle = "替换 · 应用结果", onBack = onBack) {
        // 结果概览卡
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ReaderShapes.lg)
                .background(extra.paperBright, ReaderShapes.lg)
                .border(1.dp, extra.controlLine, ReaderShapes.lg)
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(extra.forest.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.reader_ic_check),
                    contentDescription = null,
                    tint = extra.forest,
                    modifier = Modifier.size(28.dp)
                )
            }
            Text(
                text = "已应用 4 条替换规则",
                style = ReaderTextStyles.emptyHeading,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Text(
                text = "已按规则顺序应用到当前正文，可返回管理或继续阅读。",
                style = ReaderTextStyles.emptyBody,
                color = extra.muted,
                textAlign = TextAlign.Center
            )
        }

        // 原文 / 替换后片段对比
        ReplaceSnippetCard(
            label = "原文片段",
            text = "他称呼她为「雨容」，这是旧世界的称呼方式。",
            tint = extra.muted
        )
        ReplaceSnippetCard(
            label = "替换后片段",
            text = "他称呼她为「雨容」，这是新世界的称谓。",
            tint = extra.controlPrimary
        )

        // 操作按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ReplaceActionButton(
                label = "返回规则管理",
                primary = false,
                onClick = { onNavigate("content-replacement") },
                modifier = Modifier.weight(1f)
            )
            ReplaceActionButton(
                label = "继续阅读",
                primary = true,
                onClick = { onNavigate("immersive-reading") },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ReplaceSnippetCard(label: String, text: String, tint: Color) {
    val extra = readerExtraColors()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ReaderShapes.md)
            .background(extra.surfaceSoft, ReaderShapes.md)
            .border(1.dp, extra.hairline, ReaderShapes.md)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = label,
            style = ReaderTextStyles.sectionTitle,
            color = tint
        )
        Text(
            text = text,
            style = ReaderTextStyles.readerBody,
            color = extra.readerInk
        )
    }
}

// 3. 导入导出 ----------------------------------------------------------------

/**
 * 替换规则导入导出。展示 JSON 预览，提供导入/导出/复制操作。
 */
@Composable
fun ReaderReplaceImportExportScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit
) {
    val extra = readerExtraColors()
    ReaderReplaceScaffold(subtitle = "替换 · 导入导出", onBack = onBack) {
        // 说明卡
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ReaderShapes.md)
                .background(extra.controlActiveSoft, ReaderShapes.md)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.reader_ic_info),
                contentDescription = null,
                tint = extra.controlPrimary,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = "以 JSON 预览、导入或导出替换规则，并在提交前校验规则格式。",
                style = ReaderTextStyles.infoLayer,
                color = extra.controlInk
            )
        }

        // JSON 预览
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ReaderShapes.md)
                .background(extra.metaBackground, ReaderShapes.md)
                .border(1.dp, extra.hairline, ReaderShapes.md)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "replace_rules.json",
                style = ReaderTextStyles.infoLayer,
                color = extra.muted
            )
            Text(
                text = buildJsonPreview(),
                style = ReaderTextStyles.readerBody.copy(fontFamily = FontFamily.Monospace),
                color = extra.readerInk
            )
        }

        // 操作按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ReplaceActionButton(
                label = "复制 JSON",
                primary = false,
                onClick = { onNavigate("reader-replace-import-export") },
                modifier = Modifier.weight(1f)
            )
            ReplaceActionButton(
                label = "导入规则",
                primary = false,
                onClick = { onNavigate("reader-replace-page") },
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ReplaceActionButton(
                label = "导出规则",
                primary = true,
                onClick = { onNavigate("reader-replace-import-export") },
                modifier = Modifier.weight(1f)
            )
            ReplaceActionButton(
                label = "返回规则管理",
                primary = false,
                onClick = { onNavigate("content-replacement") },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

private fun buildJsonPreview(): String = """[
  {
    "name": "雨容称呼",
    "scope": "book",
    "pattern": "雨容",
    "replacement": "雨容",
    "enabled": true,
    "order": 1
  },
  {
    "name": "旧称统一",
    "scope": "book",
    "pattern": "旧世界",
    "replacement": "新世界",
    "enabled": true,
    "order": 2
  },
  {
    "name": "标点清理",
    "scope": "book",
    "pattern": "[，。]{2,}",
    "replacement": "",
    "enabled": false,
    "order": 3
  }
]"""

// 4. 预览 --------------------------------------------------------------------

/**
 * 替换规则预览。并排对比原文与替换后正文，展示本次应用的规则列表。
 */
@Composable
fun ReaderReplacePreviewScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit
) {
    val extra = readerExtraColors()
    ReaderReplaceScaffold(subtitle = "替换 · 规则预览", onBack = onBack) {
        // 原文 / 替换后片段对比
        ReplaceSnippetCard(
            label = "原文片段",
            text = "他称呼她为「雨容」，这是旧世界的称呼方式。雨声在窗外连成一片，像无数细小的针。",
            tint = extra.muted
        )
        ReplaceSnippetCard(
            label = "替换后片段",
            text = "他称呼她为「雨容」，这是新世界的称谓。雨声在窗外连成一片，像无数细小的针。",
            tint = extra.controlPrimary
        )

        // 本次应用的规则列表
        Text(
            text = "本次应用的规则",
            style = ReaderTextStyles.sectionTitle,
            color = extra.controlInk
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            replaceRules().forEach { rule ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(ReaderShapes.md)
                        .background(extra.paperBright, ReaderShapes.md)
                        .border(1.dp, extra.hairline, ReaderShapes.md)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.reader_ic_replace),
                        contentDescription = null,
                        tint = if (rule.enabled) extra.controlPrimary else extra.controlMuted,
                        modifier = Modifier.size(18.dp)
                    )
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "${rule.order}. ${rule.name}",
                            style = ReaderTextStyles.continueAction,
                            color = extra.controlInk,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = rule.scope,
                            style = ReaderTextStyles.infoLayer,
                            color = extra.muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = if (rule.enabled) "已应用" else "未启用",
                        style = ReaderTextStyles.infoLayer,
                        color = if (rule.enabled) extra.forest else extra.controlMuted
                    )
                }
            }
        }

        // 操作按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ReplaceActionButton(
                label = "返回规则管理",
                primary = false,
                onClick = { onNavigate("content-replacement") },
                modifier = Modifier.weight(1f)
            )
            ReplaceActionButton(
                label = "继续阅读",
                primary = true,
                onClick = { onNavigate("immersive-reading") },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// 5. 替换页面 ----------------------------------------------------------------

/**
 * 内容替换规则管理页。管理规则顺序、启用状态、作用范围，并提供新建/导入导出/预览入口。
 */
@Composable
fun ReaderReplacePageScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit
) {
    val extra = readerExtraColors()
    ReaderReplaceScaffold(subtitle = "替换 · 规则管理", onBack = onBack) {
        // 规则列表
        Text(
            text = "替换规则",
            style = ReaderTextStyles.sectionTitle,
            color = extra.controlInk
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            replaceRules().forEach { rule ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(ReaderShapes.md)
                        .background(extra.paperBright, ReaderShapes.md)
                        .border(1.dp, extra.hairline, ReaderShapes.md)
                        .clickable { onNavigate("reader-replace-delete-confirm") }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.reader_ic_replace),
                        contentDescription = null,
                        tint = if (rule.enabled) extra.controlPrimary else extra.controlMuted,
                        modifier = Modifier.size(20.dp)
                    )
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "${rule.order}. ${rule.name}",
                            style = ReaderTextStyles.continueAction,
                            color = extra.controlInk,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = rule.scope,
                            style = ReaderTextStyles.infoLayer,
                            color = extra.muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    // 启用状态徽标
                    Text(
                        text = if (rule.enabled) "已启用" else "未启用",
                        style = ReaderTextStyles.infoLayer,
                        color = if (rule.enabled) extra.forest else extra.controlMuted,
                        modifier = Modifier
                            .clip(ReaderShapes.pill)
                            .background(
                                if (rule.enabled) extra.forest.copy(alpha = 0.10f) else extra.controlLine.copy(alpha = 0.20f),
                                ReaderShapes.pill
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // 管理操作
        Text(
            text = "管理",
            style = ReaderTextStyles.sectionTitle,
            color = extra.controlInk
        )
        // 两列操作网格
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ReplaceActionButton(
                label = "新建规则",
                primary = true,
                onClick = { onNavigate("reader-replace-page") },
                modifier = Modifier.weight(1f)
            )
            ReplaceActionButton(
                label = "导入导出",
                primary = false,
                onClick = { onNavigate("reader-replace-import-export") },
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ReplaceActionButton(
                label = "详细预览",
                primary = false,
                onClick = { onNavigate("reader-replace-preview") },
                modifier = Modifier.weight(1f)
            )
            ReplaceActionButton(
                label = "应用结果",
                primary = false,
                onClick = { onNavigate("reader-replace-apply-result") },
                modifier = Modifier.weight(1f)
            )
        }
        ReplaceActionButton(
            label = "返回阅读",
            primary = false,
            onClick = { onNavigate("immersive-reading") },
            modifier = Modifier.fillMaxWidth()
        )
    }
}
