package com.reader.ui.bookshelf

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import com.reader.ui.demo.DemoRoutePage
import com.reader.ui.shell.DemoLibraryShell
import com.reader.ui.theme.ReaderShapes
import com.reader.ui.theme.ReaderTextStyles
import com.reader.ui.theme.readerExtraColors
import com.reader.ui.tokens.ReaderTypeToken

/**
 * W1 导入工作流专用 renderer：为 8 个 schema-only 导入路由提供阶段面包屑、
 * 状态卡片和路由专属内容（文件信息、解析进度、重复列表、冲突字段、结果摘要）。
 *
 * Canonical source: `frontend-demo-optimized/renderers/w1-import-renderers.js`
 *
 * 动作流（importPhase enum）：入口 → 选择 → 输入 → 解析 → 预览 → 冲突 → 应用 → 结果
 */
@Composable
fun W1ImportRouteScreen(
    page: DemoRoutePage,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit
) {
    val phase = w1ImportPhaseForRoute(page.id)
    DemoLibraryShell(
        title = w1ImportTitleForRoute(page.id),
        onBack = onBack,
        bottomActions = page.actions,
        onNavigate = onNavigate
    ) {
        item { W1ImportPhaseBreadcrumb(currentPhase = phase) }
        item { W1ImportStateCard(routeId = page.id, phase = phase) }
        when (page.id) {
            "import-permission-denied" -> item { W1ImportPermissionDetail() }
            "import-format-unsupported" -> item { W1ImportFormatDetail() }
            "import-empty-file" -> item { W1ImportEmptyFileDetail() }
            "import-parsing" -> item { W1ImportParsingDetail() }
            "import-duplicate" -> item { W1ImportDuplicateList() }
            "import-conflict-resolve" -> item { W1ImportConflictList() }
            "import-partial-success" -> item { W1ImportPartialSummary() }
            "import-result-detail" -> item { W1ImportResultDetailSummary() }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// 阶段面包屑
// ═══════════════════════════════════════════════════════════════════════════════

private enum class W1ImportPhase(val label: String) {
    SELECTING("选择"),
    INPUT("输入"),
    PARSING("解析"),
    PREVIEW("预览"),
    CONFLICT("冲突"),
    APPLYING("应用"),
    RESULT("结果")
}

private fun w1ImportPhaseForRoute(routeId: String): W1ImportPhase = when (routeId) {
    "import-permission-denied" -> W1ImportPhase.SELECTING
    "import-format-unsupported" -> W1ImportPhase.INPUT
    "import-empty-file" -> W1ImportPhase.PARSING
    "import-parsing" -> W1ImportPhase.PARSING
    "import-duplicate" -> W1ImportPhase.PREVIEW
    "import-conflict-resolve" -> W1ImportPhase.CONFLICT
    "import-partial-success" -> W1ImportPhase.RESULT
    "import-result-detail" -> W1ImportPhase.RESULT
    else -> W1ImportPhase.SELECTING
}

private fun w1ImportTitleForRoute(routeId: String): String = when (routeId) {
    "import-permission-denied" -> "导入权限被拒绝"
    "import-format-unsupported" -> "格式不支持"
    "import-empty-file" -> "空文件"
    "import-parsing" -> "解析中"
    "import-duplicate" -> "重复检测"
    "import-conflict-resolve" -> "解决冲突"
    "import-partial-success" -> "部分导入成功"
    "import-result-detail" -> "导入结果详情"
    else -> "导入"
}

@Composable
private fun W1ImportPhaseBreadcrumb(currentPhase: W1ImportPhase) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    val phases = W1ImportPhase.entries
    val currentIndex = phases.indexOf(currentPhase)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        phases.forEachIndexed { index, phase ->
            val isActive = index == currentIndex
            val isPast = index < currentIndex
            val dotColor = when {
                isActive -> colors.primary
                isPast -> colors.primary.copy(alpha = 0.5f)
                else -> extra.hairline
            }
            val labelColor = when {
                isActive -> colors.primary
                isPast -> colors.primary.copy(alpha = 0.7f)
                else -> extra.muted
            }
            val labelWeight = if (isActive) FontWeight(800) else FontWeight(550)
            Box(
                modifier = Modifier
                    .size(if (isActive) 8.dp else 6.dp)
                    .background(dotColor, CircleShape)
            )
            Text(
                text = phase.label,
                style = TextStyle(
                    fontFamily = FontFamily.Default,
                    fontSize = ReaderTypeToken.ACTION_LABEL.value,
                    fontWeight = labelWeight
                ),
                color = labelColor,
                maxLines = 1
            )
            if (index != phases.lastIndex) {
                Box(
                    modifier = Modifier
                        .width(8.dp)
                        .height(1.dp)
                        .background(extra.hairline)
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// 状态卡片（公共结构）
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun W1ImportStateCard(routeId: String, phase: W1ImportPhase) {
    val (iconRes, title, summary) = w1ImportStateCardContent(routeId)
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface.copy(alpha = 0.88f), ReaderShapes.lg)
            .border(1.dp, extra.hairline, ReaderShapes.lg)
            .padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(colors.primary.copy(alpha = 0.10f), ReaderShapes.md),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(26.dp)
            )
        }
        Text(
            text = title,
            style = ReaderTextStyles.emptyHeading,
            color = colors.onBackground,
            textAlign = TextAlign.Center
        )
        Text(
            text = summary,
            style = ReaderTextStyles.emptyBody,
            color = extra.muted,
            textAlign = TextAlign.Center
        )
    }
}

private fun w1ImportStateCardContent(routeId: String): Triple<Int, String, String> = when (routeId) {
    "import-permission-denied" -> Triple(
        R.drawable.reader_ic_shield,
        "导入权限被拒绝",
        "系统未授予存储访问权限，无法读取本地书籍文件。"
    )
    "import-format-unsupported" -> Triple(
        R.drawable.reader_ic_file,
        "文件格式不支持",
        "当前文件格式暂不支持导入，请转换为支持的格式。"
    )
    "import-empty-file" -> Triple(
        R.drawable.reader_ic_file,
        "文件为空",
        "所选文件没有可导入的内容，请确认文件未损坏后重新选择。"
    )
    "import-parsing" -> Triple(
        R.drawable.reader_ic_refresh,
        "正在解析书籍",
        "正在解析文件内容、识别章节结构和元数据，请稍候。"
    )
    "import-duplicate" -> Triple(
        R.drawable.reader_ic_copy,
        "检测到重复书籍",
        "以下书籍在本地书架已存在，请选择处理方式。"
    )
    "import-conflict-resolve" -> Triple(
        R.drawable.reader_ic_warning,
        "解决导入冲突",
        "导入数据与本地记录存在冲突，请逐项选择处理方式。"
    )
    "import-partial-success" -> Triple(
        R.drawable.reader_ic_check,
        "部分导入成功",
        "本次导入共 3 项，其中 1 项成功、2 项失败，可重试失败项或查看详情。"
    )
    "import-result-detail" -> Triple(
        R.drawable.reader_ic_info,
        "导入结果详情",
        "以下是本次导入的完整结果，可导出报告或继续导入其他书籍。"
    )
    else -> Triple(R.drawable.reader_ic_info, "导入", "")
}

// ═══════════════════════════════════════════════════════════════════════════════
// 路由专属内容
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun W1ImportPermissionDetail() {
    W1ImportDetailCard {
        W1ImportDetailRow("所需权限", "存储访问")
        W1ImportDetailDivider()
        W1ImportDetailRow("触发场景", "本地书导入 · 选择文件阶段")
        W1ImportDetailDivider()
        W1ImportDetailRow("影响范围", "无法读取或写入本地书籍文件")
    }
}

@Composable
private fun W1ImportFormatDetail() {
    W1ImportDetailCard {
        W1ImportDetailRow("文件名", "未知文件.epub")
        W1ImportDetailDivider()
        W1ImportDetailRow("检测格式", "未知格式")
        W1ImportDetailDivider()
        W1ImportDetailRow("支持格式", "EPUB · TXT · MOBI · AZW3 · PDF")
    }
    W1ImportHint("可尝试使用格式转换工具转换为支持的格式后重新导入。")
}

@Composable
private fun W1ImportEmptyFileDetail() {
    W1ImportDetailCard {
        W1ImportDetailRow("文件名", "未知文件")
        W1ImportDetailDivider()
        W1ImportDetailRow("文件大小", "0 KB")
        W1ImportDetailDivider()
        W1ImportDetailRow("检测结果", "文件内容为空或无法读取")
    }
}

@Composable
private fun W1ImportParsingDetail() {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    W1ImportDetailCard {
        W1ImportDetailRow("当前文件", "雨夜.epub")
        W1ImportDetailDivider()
        W1ImportDetailRow("当前步骤", "正在识别章节结构")
    }
    Spacer(Modifier.height(10.dp))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface.copy(alpha = 0.88f), ReaderShapes.lg)
            .border(1.dp, extra.hairline, ReaderShapes.lg)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("解析进度", style = w1ImportLabelStyle(), color = extra.muted)
            Text("72%", style = w1ImportLabelStyle().copy(fontWeight = FontWeight(800)), color = colors.primary)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(extra.hairline.copy(alpha = 0.5f), ReaderShapes.pill)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.72f)
                    .height(6.dp)
                    .background(colors.primary, ReaderShapes.pill)
            )
        }
    }
}

@Composable
private fun W1ImportDuplicateList() {
    val duplicates = remember { w1ImportDuplicateItems() }
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "检测到 ${duplicates.size} 个重复项",
            style = w1ImportLabelStyle().copy(fontWeight = FontWeight(700)),
            color = colors.onBackground
        )
        duplicates.forEach { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface.copy(alpha = 0.88f), ReaderShapes.md)
                    .border(1.dp, extra.hairline, ReaderShapes.md)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(R.drawable.reader_ic_copy),
                    contentDescription = null,
                    tint = extra.muted,
                    modifier = Modifier.size(18.dp)
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        item.title,
                        style = w1ImportTitleStyle(),
                        color = colors.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        item.meta,
                        style = w1ImportMetaStyle(),
                        color = extra.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    item.size,
                    style = w1ImportMetaStyle(),
                    color = extra.muted
                )
            }
        }
    }
}

@Composable
private fun W1ImportConflictList() {
    val conflicts = remember { w1ImportConflictItems() }
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "字段冲突",
            style = w1ImportLabelStyle().copy(fontWeight = FontWeight(700)),
            color = colors.onBackground
        )
        conflicts.forEach { item ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface.copy(alpha = 0.88f), ReaderShapes.md)
                    .border(1.dp, extra.hairline, ReaderShapes.md)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(item.field, style = w1ImportMetaStyle(), color = extra.muted)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    W1ImportConflictValue(label = "本地", value = item.local, modifier = Modifier.weight(1f))
                    W1ImportConflictValue(label = "导入", value = item.remote, modifier = Modifier.weight(1f))
                }
            }
        }
    }
    W1ImportHint("选择解决方案后将进入应用阶段，可通过回滚撤销本次导入。")
}

@Composable
private fun W1ImportPartialSummary() {
    val results = remember { w1ImportPartialResults() }
    val successCount = results.count { it.tone == W1ImportTone.GOOD }
    val failCount = results.size - successCount
    W1ImportSummaryRow(
        items = listOf(
            Triple("成功", "$successCount 项", W1ImportTone.GOOD),
            Triple("失败", "$failCount 项", W1ImportTone.DANGER),
            Triple("总计", "${results.size} 项", W1ImportTone.NEUTRAL)
        )
    )
    Spacer(Modifier.height(8.dp))
    W1ImportResultList(results = results)
}

@Composable
private fun W1ImportResultDetailSummary() {
    W1ImportSummaryRow(
        items = listOf(
            Triple("导入时间", "刚刚", W1ImportTone.NEUTRAL),
            Triple("来源", "本地文件", W1ImportTone.NEUTRAL),
            Triple("分组", "默认分组", W1ImportTone.NEUTRAL)
        )
    )
    Spacer(Modifier.height(8.dp))
    val results = remember { w1ImportFullResults() }
    W1ImportResultList(results = results)
}

// ═══════════════════════════════════════════════════════════════════════════════
// 公共组件
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun W1ImportDetailCard(content: @Composable ColumnScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface.copy(alpha = 0.88f), ReaderShapes.lg)
            .border(1.dp, extra.hairline, ReaderShapes.lg)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content
    )
}

@Composable
private fun W1ImportDetailRow(label: String, value: String) {
    val extra = readerExtraColors()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = w1ImportMetaStyle(), color = extra.muted)
        Text(
            value,
            style = w1ImportTitleStyle(),
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.End,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun W1ImportDetailDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(readerExtraColors().hairline.copy(alpha = 0.5f))
    )
}

@Composable
private fun W1ImportHint(text: String) {
    val extra = readerExtraColors()
    Text(
        text = text,
        style = w1ImportMetaStyle(),
        color = extra.muted,
        modifier = Modifier.padding(horizontal = 2.dp)
    )
}

@Composable
private fun W1ImportConflictValue(label: String, value: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Column(
        modifier = modifier
            .background(extra.metaBackground.copy(alpha = 0.6f), ReaderShapes.sm)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(label, style = w1ImportMetaStyle().copy(fontWeight = FontWeight(700)), color = extra.muted)
        Text(value, style = w1ImportTitleStyle(), color = colors.onBackground, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun W1ImportSummaryRow(items: List<Triple<String, String, W1ImportTone>>) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface.copy(alpha = 0.88f), ReaderShapes.lg)
            .border(1.dp, extra.hairline, ReaderShapes.lg)
            .padding(14.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        items.forEach { (label, value, tone) ->
            val valueColor = when (tone) {
                W1ImportTone.GOOD -> colors.primary
                W1ImportTone.DANGER -> colors.error
                W1ImportTone.NEUTRAL -> colors.onBackground
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(label, style = w1ImportMetaStyle(), color = extra.muted)
                Text(value, style = w1ImportTitleStyle().copy(fontWeight = FontWeight(800)), color = valueColor)
            }
        }
    }
}

@Composable
private fun W1ImportResultList(results: List<W1ImportResultItem>) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        results.forEach { item ->
            val valueColor = when (item.tone) {
                W1ImportTone.GOOD -> colors.primary
                W1ImportTone.DANGER -> colors.error
                W1ImportTone.NEUTRAL -> colors.onBackground
            }
            val iconRes = if (item.tone == W1ImportTone.DANGER) R.drawable.reader_ic_warning else R.drawable.reader_ic_check
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface.copy(alpha = 0.88f), ReaderShapes.md)
                    .border(1.dp, extra.hairline, ReaderShapes.md)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = valueColor,
                    modifier = Modifier.size(18.dp)
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(item.title, style = w1ImportTitleStyle(), color = colors.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (item.meta.isNotEmpty()) {
                        Text(item.meta, style = w1ImportMetaStyle(), color = extra.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                Text(item.status, style = w1ImportMetaStyle().copy(fontWeight = FontWeight(700)), color = valueColor, maxLines = 1)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// 数据模型
// ═══════════════════════════════════════════════════════════════════════════════

private enum class W1ImportTone { GOOD, DANGER, NEUTRAL }

private data class W1ImportDuplicateItem(val title: String, val meta: String, val size: String)
private data class W1ImportConflictItem(val field: String, val local: String, val remote: String)
private data class W1ImportResultItem(val title: String, val status: String, val meta: String, val tone: W1ImportTone)

private fun w1ImportDuplicateItems() = listOf(
    W1ImportDuplicateItem("雨夜.epub", "本地已存在 · 同名同作者", "1.2 MB"),
    W1ImportDuplicateItem("旧书扫描.txt", "本地已存在 · 同名不同作者", "0.8 MB")
)

private fun w1ImportConflictItems() = listOf(
    W1ImportConflictItem("书名", "雨夜", "雨夜（修订版）"),
    W1ImportConflictItem("作者", "佚名", "张三"),
    W1ImportConflictItem("分组", "默认分组", "小说")
)

private fun w1ImportPartialResults() = listOf(
    W1ImportResultItem("雨夜.epub", "成功", "作者已识别 · 加入默认分组", W1ImportTone.GOOD),
    W1ImportResultItem("旧书扫描.txt", "失败", "编码异常", W1ImportTone.DANGER),
    W1ImportResultItem("缺失章节.mobi", "失败", "格式不支持", W1ImportTone.DANGER)
)

private fun w1ImportFullResults() = listOf(
    W1ImportResultItem("雨夜.epub", "成功", "作者已识别 · 加入默认分组", W1ImportTone.GOOD),
    W1ImportResultItem("旧书扫描.txt", "成功", "编码 UTF-8 · 章节识别完成", W1ImportTone.GOOD),
    W1ImportResultItem("缺失章节.mobi", "失败", "格式不支持 · 已跳过", W1ImportTone.DANGER)
)

// ═══════════════════════════════════════════════════════════════════════════════
// 文本样式
// ═══════════════════════════════════════════════════════════════════════════════

private fun w1ImportLabelStyle() = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = ReaderTypeToken.ACTION_LABEL.value,
    lineHeight = 14.sp,
    fontWeight = FontWeight(550)
)

private fun w1ImportTitleStyle() = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = ReaderTypeToken.SECTION_TITLE.value,
    lineHeight = 19.sp,
    fontWeight = FontWeight(700)
)

private fun w1ImportMetaStyle() = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = ReaderTypeToken.BOOK_META.value,
    lineHeight = 16.sp,
    fontWeight = FontWeight(500)
)
