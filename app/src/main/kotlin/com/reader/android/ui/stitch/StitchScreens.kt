package com.reader.android.ui.stitch

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reader.android.ui.detail.BookDetailUiModel
import com.reader.android.ui.reader.components.ReplaceRule
import com.reader.android.ui.search.SearchResultUiModel
import com.reader.android.ui.theme.ReaderTheme

// ── Stitch Search Screen ──

@Composable
fun StitchSearchScreen(onBack: () -> Unit = {}) {
    var query by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxSize().background(ReaderTheme.colors.paperBg)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StitchIconButton(Icons.AutoMirrored.Filled.ArrowBack, "返回", onBack, size = 36.dp)
            Spacer(modifier = Modifier.width(8.dp))
            StitchSearchField(query, { query = it }, Modifier.weight(1f))
        }
        // History / recommendations
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Text("搜索历史", color = ReaderTheme.colors.bodyText, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
            Spacer(modifier = Modifier.height(8.dp))
            listOf("深空信号", "一剑独尊", "科幻世界").forEach { term ->
                StitchChip(text = term, modifier = Modifier.padding(end = 8.dp, bottom = 4.dp))
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text("推荐搜索", color = ReaderTheme.colors.bodyText, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(8.dp))
            listOf("仙侠", "都市", "历史").forEach { term ->
                StitchChip(text = term, modifier = Modifier.padding(end = 8.dp, bottom = 4.dp))
            }
        }
    }
}

// ── Stitch Search Results ──

@Composable
fun StitchSearchResultsScreen(
    query: String,
    results: List<SearchResultUiModel>,
    onBack: () -> Unit = {},
    onResultClick: (SearchResultUiModel) -> Unit = {}
) {
    Column(modifier = Modifier.fillMaxSize().background(ReaderTheme.colors.paperBg)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StitchIconButton(Icons.AutoMirrored.Filled.ArrowBack, "返回", onBack, size = 36.dp)
            Spacer(modifier = Modifier.width(8.dp))
            StitchSearchField(query, {}, Modifier.weight(1f))
        }
        Text("共 ${results.size} 条结果", color = ReaderTheme.colors.bodyText, fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(results) { item ->
                StitchPanel(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(item.title, color = ReaderTheme.colors.controlInk, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row { Text("${item.author} · ${item.sourceName}", color = ReaderTheme.colors.bodyText, fontSize = 12.sp) }
                    Text("最新：${item.latestChapter}", color = ReaderTheme.colors.bodyText, fontSize = 12.sp)
                    item.intro.takeIf { it.isNotBlank() }?.let {
                        Text(it, color = ReaderTheme.colors.bodyText, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    StitchActionButton("查看详情", { onResultClick(item) }, primary = true)
                }
            }
        }
    }
}

// ── Stitch BookDetail Screen ──

@Composable
fun StitchBookDetailScreen(
    detail: BookDetailUiModel,
    onBack: () -> Unit = {},
    onStartReading: () -> Unit = {},
    onTOC: () -> Unit = {}
) {
    Column(modifier = Modifier.fillMaxSize().background(ReaderTheme.colors.paperBg)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StitchIconButton(Icons.AutoMirrored.Filled.ArrowBack, "返回", onBack, size = 36.dp)
            Spacer(modifier = Modifier.width(8.dp))
            Text(detail.title, color = ReaderTheme.colors.controlInk, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() })
        }
        Column(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
            Spacer(modifier = Modifier.height(8.dp))
            Text("${detail.author} · ${detail.sourceName}", color = ReaderTheme.colors.bodyText, fontSize = 13.sp)
            StitchChip(text = detail.category, modifier = Modifier.padding(vertical = 4.dp))
            StitchChip(detail.cacheStatus, modifier = Modifier.padding(vertical = 4.dp, horizontal = 4.dp))
            if (detail.readingProgress > 0f) {
                StitchChip("进度 ${(detail.readingProgress * 100).toInt()}%", modifier = Modifier.padding(vertical = 4.dp, horizontal = 4.dp))
            }
            Text(detail.currentChapter.ifBlank { "" }, color = ReaderTheme.colors.bodyText, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(12.dp))
            Text(detail.intro.ifBlank { "" }, color = ReaderTheme.colors.bodyText, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(12.dp))
            // TOC Preview
            val toc = detail.tocPreview
            Text("目录 (${toc.chapterCount}章)", color = ReaderTheme.colors.controlInk, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text("${toc.firstChapterTitle} — ${toc.latestChapterTitle}", color = ReaderTheme.colors.bodyText, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Row {
                val actionLabel = if (detail.readingProgress > 0f) "继续阅读" else "开始阅读"
                StitchActionButton(actionLabel, onStartReading, primary = true, modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.width(8.dp))
                StitchActionButton("查看目录", onTOC, modifier = Modifier.weight(1f))
            }
        }
    }
}

// ── Stitch Replace Overlay Content ──

@Composable
fun StitchReplaceOverlayContent(rules: List<ReplaceRule>, onRuleToggle: (Int, Boolean) -> Unit = { _, _ -> }) {
    Column {
        Text("内容替换", color = ReaderTheme.colors.controlInk, fontSize = 15.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.semantics { heading() })
        Text("仅显示当前书籍匹配规则", color = ReaderTheme.colors.bodyText, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(8.dp))
        rules.forEachIndexed { index, rule ->
            StitchPanel(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(rule.name, color = ReaderTheme.colors.controlInk, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text("范围：${rule.scope} · 匹配：${rule.pattern}", color = ReaderTheme.colors.bodyText, fontSize = 12.sp)
                        Text("替换：${rule.replacement}", color = ReaderTheme.colors.primary, fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier.size(36.dp, 22.dp).clip(RoundedCornerShape(11.dp))
                            .background(if (rule.enabled) ReaderTheme.colors.primary else ReaderTheme.colors.mutedTrack)
                    ) {
                        Box(
                            modifier = Modifier.size(18.dp).clip(RoundedCornerShape(9.dp))
                                .background(ReaderTheme.colors.paperBg)
                                .align(if (rule.enabled) Alignment.CenterEnd else Alignment.CenterStart)
                                .padding(1.dp)
                        )
                    }
                }
            }
        }
    }
}

// ── Helpers ──

@Composable
private fun StitchIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, onClick: () -> Unit, size: androidx.compose.ui.unit.Dp = 40.dp) {
    Box(
        modifier = Modifier.size(size)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = desc },
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = ReaderTheme.colors.controlInk, modifier = Modifier.size(20.dp)) }
}

@Composable
private fun StitchChip(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(2.dp))
            .background(ReaderTheme.colors.floatingControlBg)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(text, color = ReaderTheme.colors.controlInk, fontSize = 12.sp)
    }
}
