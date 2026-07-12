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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.reader.android.R
import com.reader.ui.theme.ReaderShapes
import com.reader.ui.theme.ReaderTextStyles
import com.reader.ui.theme.readerExtraColors

/**
 * W2: 阅读 content / TOC 状态原生页面。
 *
 * 替换原 `Contract25RouteScreen` 的通用 scaffold（DemoReaderShell + DemoStateCard）。
 * 每个状态渲染：
 * - reader paper 背景 + 顶部栏（返回 + 书名 + 章节上下文）
 * - 居中状态卡片（图标 + 标题 + 说明 + 状态专属操作）
 *
 * 不使用固定文本冒充产品页：每个状态的图标、文案、操作均反映该状态的语义。
 */
@Composable
fun ReaderContentStateScreen(
    routeId: String,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit
) {
    val spec = contentStateSpec(routeId)
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
                    text = spec.topTitle,
                    style = ReaderTextStyles.appBarTitle,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = spec.topSubtitle,
                    style = ReaderTextStyles.infoLayer,
                    color = extra.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // 居中状态卡片
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 28.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 状态图标或加载指示
            if (spec.loading) {
                CircularProgressIndicator(
                    color = extra.controlPrimary,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(48.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(spec.iconTint(extra).copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = spec.iconRes),
                        contentDescription = null,
                        tint = spec.iconTint(extra),
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Text(
                text = spec.heading,
                style = ReaderTextStyles.emptyHeading,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Text(
                text = spec.body,
                style = ReaderTextStyles.emptyBody,
                color = extra.muted,
                textAlign = TextAlign.Center
            )

            // 状态专属操作按钮
            if (spec.actions.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                spec.actions.forEach { action ->
                    StateActionButton(
                        label = action.label,
                        primary = action.primary,
                        onClick = { onNavigate(action.targetRoute) }
                    )
                }
            }
        }
    }
}

private data class ContentStateAction(
    val label: String,
    val targetRoute: String,
    val primary: Boolean = false
)

private data class ContentStateSpec(
    val topTitle: String,
    val topSubtitle: String,
    val heading: String,
    val body: String,
    val iconRes: Int,
    val loading: Boolean,
    val actions: List<ContentStateAction>
)

private fun contentStateSpec(routeId: String): ContentStateSpec = when (routeId) {
    "reader-content-loading" -> ContentStateSpec(
        topTitle = "长夜余火",
        topSubtitle = "第 32 章 雨夜 · 正文加载中",
        heading = "正在加载正文",
        body = "从当前书源拉取本章正文，加载完成后会保持当前章节上下文与阅读位置。",
        iconRes = R.drawable.reader_ic_book_open,
        loading = true,
        actions = listOf(ContentStateAction("返回控制层", "reader"))
    )
    "reader-content-offline" -> ContentStateSpec(
        topTitle = "长夜余火",
        topSubtitle = "第 32 章 雨夜 · 正文离线",
        heading = "当前网络不可用",
        body = "无法拉取本章正文。可重试加载，或返回控制层查看缓存章节与本书信息。",
        iconRes = R.drawable.reader_ic_offline,
        loading = false,
        actions = listOf(
            ContentStateAction("重试", "immersive-reading", primary = true),
            ContentStateAction("返回控制层", "reader")
        )
    )
    "reader-content-error" -> ContentStateSpec(
        topTitle = "长夜余火",
        topSubtitle = "第 32 章 雨夜 · 正文解析错误",
        heading = "本章正文解析失败",
        body = "可能是书源编码或来源异常。可重试加载，或返回控制层更换书源后再次尝试。",
        iconRes = R.drawable.reader_ic_warning,
        loading = false,
        actions = listOf(
            ContentStateAction("重试", "immersive-reading", primary = true),
            ContentStateAction("返回控制层", "reader")
        )
    )
    "reader-toc-loading" -> ContentStateSpec(
        topTitle = "长夜余火",
        topSubtitle = "目录加载中",
        heading = "正在加载章节列表",
        body = "从书源拉取本书目录，加载完成后可在目录中跳转章节。",
        iconRes = R.drawable.reader_ic_directory,
        loading = true,
        actions = listOf(ContentStateAction("返回控制层", "reader"))
    )
    "reader-toc-offline" -> ContentStateSpec(
        topTitle = "长夜余火",
        topSubtitle = "目录离线",
        heading = "当前网络不可用",
        body = "无法更新目录。可重试加载，或返回控制层查看已缓存的章节列表。",
        iconRes = R.drawable.reader_ic_offline,
        loading = false,
        actions = listOf(
            ContentStateAction("重试", "toc-bookmarks", primary = true),
            ContentStateAction("返回控制层", "reader")
        )
    )
    "reader-toc-error" -> ContentStateSpec(
        topTitle = "长夜余火",
        topSubtitle = "目录解析错误",
        heading = "章节列表解析失败",
        body = "书源返回的目录无法解析。可重试加载，或返回控制层更换书源后再次尝试。",
        iconRes = R.drawable.reader_ic_warning,
        loading = false,
        actions = listOf(
            ContentStateAction("重试", "toc-bookmarks", primary = true),
            ContentStateAction("返回控制层", "reader")
        )
    )
    // reader-page-boundary-first / last / progress-restore / background-restore
    // also share ReaderContentState renderer — fall back to a generic content state spec.
    "reader-page-boundary-first" -> ContentStateSpec(
        topTitle = "长夜余火",
        topSubtitle = "已是第一章",
        heading = "已是第一章",
        body = "没有更早的章节。可返回控制层查看目录，或继续阅读当前章节。",
        iconRes = R.drawable.reader_ic_nav_list,
        loading = false,
        actions = listOf(
            ContentStateAction("继续阅读", "immersive-reading", primary = true),
            ContentStateAction("返回控制层", "reader")
        )
    )
    "reader-page-boundary-last" -> ContentStateSpec(
        topTitle = "长夜余火",
        topSubtitle = "已是最后一章",
        heading = "已是最后一章",
        body = "没有更多正文。可返回控制层查看目录，或回到本章首页。",
        iconRes = R.drawable.reader_ic_nav_list,
        loading = false,
        actions = listOf(
            ContentStateAction("回到首页", "immersive-reading", primary = true),
            ContentStateAction("返回控制层", "reader")
        )
    )
    "reader-progress-restore" -> ContentStateSpec(
        topTitle = "长夜余火",
        topSubtitle = "阅读进度已恢复",
        heading = "已恢复阅读进度",
        body = "已恢复到上次阅读的章节、字符锚点和分页签名。可继续阅读，或从控制层开始。",
        iconRes = R.drawable.reader_ic_progress,
        loading = false,
        actions = listOf(
            ContentStateAction("继续阅读", "immersive-reading", primary = true),
            ContentStateAction("从控制层开始", "reader")
        )
    )
    "reader-background-restore" -> ContentStateSpec(
        topTitle = "长夜余火",
        topSubtitle = "应用从后台恢复",
        heading = "应用已从后台恢复",
        body = "可立即重载本章正文并保留当前位置，或返回控制层查看本书信息。",
        iconRes = R.drawable.reader_ic_refresh,
        loading = false,
        actions = listOf(
            ContentStateAction("立即重载", "immersive-reading", primary = true),
            ContentStateAction("返回控制层", "reader")
        )
    )
    else -> ContentStateSpec(
        topTitle = "长夜余火",
        topSubtitle = routeId,
        heading = "阅读状态",
        body = "当前阅读状态。",
        iconRes = R.drawable.reader_ic_book_open,
        loading = false,
        actions = listOf(ContentStateAction("返回控制层", "reader"))
    )
}

private fun ContentStateSpec.iconTint(extra: com.reader.ui.theme.ReaderExtraColors): Color =
    when {
        loading -> extra.controlPrimary
        iconRes == R.drawable.reader_ic_warning -> extra.danger
        iconRes == R.drawable.reader_ic_offline -> extra.controlMuted
        else -> extra.controlPrimary
    }

@Composable
private fun StateActionButton(
    label: String,
    primary: Boolean,
    onClick: () -> Unit
) {
    val extra = readerExtraColors()
    val bg = if (primary) extra.controlPrimary else extra.controlSurface
    val fg = if (primary) Color.White else extra.controlInk
    val border = if (primary) Color.Transparent else extra.controlLine
    Row(
        modifier = Modifier
            .defaultMinSize(minHeight = 44.dp)
            .clip(ReaderShapes.pill)
            .background(bg, ReaderShapes.pill)
            .border(1.dp, border, ReaderShapes.pill)
            .clickable(onClick = onClick)
            .padding(horizontal = 28.dp, vertical = 10.dp),
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
