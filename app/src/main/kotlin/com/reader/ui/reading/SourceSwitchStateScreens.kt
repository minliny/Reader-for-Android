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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.reader.android.R
import com.reader.ui.theme.ReaderShapes
import com.reader.ui.theme.ReaderTextStyles
import com.reader.ui.theme.readerExtraColors

/**
 * W3: 换源状态变体原生页面。
 *
 * 替换原 `Contract25RouteScreen` 的 `DemoFlowShell + DemoStateCard` 通用 scaffold。
 * 每个状态渲染 FlowShell 上下文（reader paper 背景 + 顶部栏 + 候选源窗口框架），
 * 并在窗口内呈现状态专属内容：
 * - empty: 无候选源
 * - error: 加载失败
 * - timeout: 请求超时
 * - loading: 切换中
 * - rollback: 切换失败回滚
 * - preview: 候选源预览（最新章节 + 正文片段）
 *
 * 不使用固定文本冒充产品页：每个状态的图标、文案、操作均反映该状态的语义。
 */
@Composable
fun SourceSwitchStateScreen(
    routeId: String,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit
) {
    val extra = readerExtraColors()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(extra.paper)
    ) {
        // 顶部栏：返回 + 书名 + 当前书源
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
                    text = "换源 · 优书网 · 第 32 章 雨夜",
                    style = ReaderTextStyles.infoLayer,
                    color = extra.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // 换源窗口（与 ReaderSourceSwitchWindow 视觉一致的状态容器）
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(top = 92.dp, start = 12.dp, end = 12.dp)
                .background(extra.paperBright, ReaderShapes.xl)
                .border(1.dp, extra.controlLine, ReaderShapes.xl)
                .clip(ReaderShapes.xl)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 窗口标题行
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.reader_ic_source_switch),
                    contentDescription = null,
                    tint = extra.readerInk,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "换源",
                    style = ReaderTextStyles.sectionTitle,
                    color = extra.readerInk,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(ReaderShapes.md)
                        .background(extra.floatingControlBackgroundAlt)
                        .clickable { onNavigate("reader") },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.reader_ic_close),
                        contentDescription = "关闭换源",
                        tint = extra.readerInk,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            SourceSwitchStateContent(routeId, onNavigate)
        }
    }
}

@Composable
private fun SourceSwitchStateContent(
    routeId: String,
    onNavigate: (String) -> Unit
) {
    val extra = readerExtraColors()
    when (routeId) {
        "source-switch-empty" -> SourceSwitchStatePanel(
            iconRes = R.drawable.reader_ic_source_stack,
            iconTint = extra.controlMuted,
            heading = "没有找到可用候选书源",
            body = "当前书籍没有其他可用候选书源。可重新加载候选列表，或返回阅读继续使用当前书源。",
            actions = listOf(
                SourceSwitchAction("重新加载", "source-switch", primary = true),
                SourceSwitchAction("返回阅读", "reader")
            ),
            onNavigate = onNavigate
        )
        "source-switch-error" -> SourceSwitchStatePanel(
            iconRes = R.drawable.reader_ic_warning,
            iconTint = extra.danger,
            heading = "候选书源加载失败",
            body = "候选书源列表加载失败，请检查网络或书源状态后重试。",
            actions = listOf(
                SourceSwitchAction("重试加载", "source-switch", primary = true),
                SourceSwitchAction("返回阅读", "reader")
            ),
            onNavigate = onNavigate
        )
        "source-switch-timeout" -> SourceSwitchStatePanel(
            iconRes = R.drawable.reader_ic_clock,
            iconTint = extra.danger,
            heading = "候选书源请求超时",
            body = "候选书源请求超时，可能是网络延迟或来源响应过慢。可重试加载或返回阅读。",
            actions = listOf(
                SourceSwitchAction("重试加载", "source-switch", primary = true),
                SourceSwitchAction("返回阅读", "reader")
            ),
            onNavigate = onNavigate
        )
        "source-switch-loading" -> SourceSwitchLoadingPanel(onNavigate = onNavigate)
        "source-switch-rollback" -> SourceSwitchStatePanel(
            iconRes = R.drawable.reader_ic_refresh,
            iconTint = extra.danger,
            heading = "目标书源切换失败",
            body = "已回滚到原书源并保留阅读进度。可重新选择书源，或返回阅读继续使用当前源。",
            actions = listOf(
                SourceSwitchAction("重新选择书源", "source-switch", primary = true),
                SourceSwitchAction("返回阅读", "reader")
            ),
            onNavigate = onNavigate
        )
        "source-switch-preview" -> SourceSwitchPreviewPanel(onNavigate = onNavigate)
        else -> SourceSwitchStatePanel(
            iconRes = R.drawable.reader_ic_source_switch,
            iconTint = extra.controlPrimary,
            heading = "换源",
            body = "当前换源状态。",
            actions = listOf(SourceSwitchAction("返回阅读", "reader")),
            onNavigate = onNavigate
        )
    }
}

@Composable
private fun SourceSwitchStatePanel(
    iconRes: Int,
    iconTint: Color,
    heading: String,
    body: String,
    actions: List<SourceSwitchAction>,
    onNavigate: (String) -> Unit
) {
    val extra = readerExtraColors()
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(iconTint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(28.dp)
            )
        }
        Text(
            text = heading,
            style = ReaderTextStyles.emptyHeading,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Text(
            text = body,
            style = ReaderTextStyles.emptyBody,
            color = extra.muted,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(2.dp))
        actions.forEach { action ->
            SourceSwitchActionButton(action, onNavigate)
        }
    }
}

@Composable
private fun SourceSwitchLoadingPanel(onNavigate: (String) -> Unit) {
    val extra = readerExtraColors()
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CircularProgressIndicator(
            color = extra.controlPrimary,
            strokeWidth = 3.dp,
            modifier = Modifier.size(48.dp)
        )
        Text(
            text = "正在切换书源",
            style = ReaderTextStyles.emptyHeading,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Text(
            text = "正在重新拉取目录与正文，当前阅读位置保持不变。切换完成后会自动回到阅读。",
            style = ReaderTextStyles.emptyBody,
            color = extra.muted,
            textAlign = TextAlign.Center
        )
        // 进度条占位（演示切换进度）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(ReaderShapes.pill)
                .background(extra.controlLine)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.55f)
                    .height(4.dp)
                    .clip(ReaderShapes.pill)
                    .background(extra.controlPrimary)
            )
        }
        Spacer(Modifier.height(2.dp))
        SourceSwitchActionButton(
            SourceSwitchAction("取消切换", "source-switch-rollback", primary = false),
            onNavigate
        )
    }
}

@Composable
private fun SourceSwitchPreviewPanel(onNavigate: (String) -> Unit) {
    val extra = readerExtraColors()
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 候选源信息
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
                painter = painterResource(id = R.drawable.reader_ic_source_stack),
                contentDescription = null,
                tint = extra.controlPrimary,
                modifier = Modifier.size(20.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "笔趣阁镜像",
                    style = ReaderTextStyles.continueAction,
                    color = extra.controlInk
                )
                Text(
                    text = "180 ms · 可切换 · 同步至第 32 章",
                    style = ReaderTextStyles.infoLayer,
                    color = extra.muted
                )
            }
        }

        Text(
            text = "最新章节",
            style = ReaderTextStyles.sectionTitle,
            color = extra.controlInk
        )
        Text(
            text = "第 32 章 雨夜",
            style = ReaderTextStyles.continueAction,
            color = extra.readerInk
        )

        Text(
            text = "正文片段",
            style = ReaderTextStyles.sectionTitle,
            color = extra.controlInk
        )
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
                text = "雨声在窗外连成一片，像无数细小的针，密密地刺在玻璃上。",
                style = ReaderTextStyles.readerBody,
                color = extra.readerInk
            )
            Text(
                text = "他站在窗前，手里握着那封被雨水润湿的信。纸页边角微微卷起，字迹却依旧清晰。",
                style = ReaderTextStyles.readerBody,
                color = extra.readerInk
            )
        }

        Text(
            text = "确认切换后将重新拉取目录与正文，当前阅读位置保持不变。",
            style = ReaderTextStyles.infoLayer,
            color = extra.muted
        )

        Spacer(Modifier.height(2.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SourceSwitchActionButton(
                SourceSwitchAction("返回列表", "source-switch", primary = false),
                onNavigate,
                modifier = Modifier.weight(1f)
            )
            SourceSwitchActionButton(
                SourceSwitchAction("确认换源", "source-switch-loading", primary = true),
                onNavigate,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

private data class SourceSwitchAction(
    val label: String,
    val targetRoute: String,
    val primary: Boolean = false
)

@Composable
private fun SourceSwitchActionButton(
    action: SourceSwitchAction,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val extra = readerExtraColors()
    val bg = if (action.primary) extra.controlPrimary else extra.controlSurface
    val fg = if (action.primary) Color.White else extra.controlInk
    val border = if (action.primary) Color.Transparent else extra.controlLine
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = 44.dp)
            .clip(ReaderShapes.pill)
            .background(bg, ReaderShapes.pill)
            .border(1.dp, border, ReaderShapes.pill)
            .clickable { onNavigate(action.targetRoute) }
            .padding(horizontal = 24.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = action.label,
            style = ReaderTextStyles.continueAction,
            color = fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
