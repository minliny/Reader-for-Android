package com.reader.android.ui.stitch

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reader.android.ui.booksource.BookSourceAdapterShell
import com.reader.android.ui.discover.DiscoverAdapterShell
import com.reader.android.ui.theme.ReaderTheme

// ── Stitch Bottom Nav ──

data class StitchTab(val label: String, val icon: ImageVector)
val stitchTabs = listOf(
    StitchTab("书架", Icons.Filled.Book),
    StitchTab("发现", Icons.Filled.Explore),
    StitchTab("书源", Icons.Filled.Hub),
    StitchTab("我的", Icons.Filled.Person)
)

@Composable
fun StitchBottomNav(selectedIndex: Int, onTabSelected: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().background(ReaderTheme.colors.bottomBarBg).padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        stitchTabs.forEachIndexed { index, tab ->
            val isSelected = index == selectedIndex
            Column(
                modifier = Modifier
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Tab, onClick = { onTabSelected(index) })
                    .semantics { contentDescription = tab.label }
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(tab.icon, null, tint = if (isSelected) ReaderTheme.colors.primary else ReaderTheme.colors.controlInk, modifier = Modifier.size(22.dp))
                Text(tab.label, color = if (isSelected) ReaderTheme.colors.primary else ReaderTheme.colors.controlInk, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal)
            }
        }
    }
}

// ── Stitch App Shell ──

@Composable
fun StitchAppShell(onSearchClick: () -> Unit = {}, onSettingsClick: () -> Unit = {}) {
    StitchBookshelfPage(onSearchClick = onSearchClick)
}

// ── Bookshelf Tab ──

@Composable
private fun StitchBookshelfPage(onSearchClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f, fill = true)) {
                Text("Reader", color = ReaderTheme.colors.controlInk, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("静默阅读空间", color = ReaderTheme.colors.bodyText, fontSize = 12.sp)
            }
            Box(
                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(4.dp)).background(ReaderTheme.colors.metaBg)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = onSearchClick)
                    .semantics { contentDescription = "搜索" },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.Search, null, tint = ReaderTheme.colors.controlInk, modifier = Modifier.size(20.dp)) }
        }

        // 最近阅读 section
        SectionHeader("最近阅读")
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            val cardWidth = (maxWidth - 8.dp) / 2
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RecentBookCard("深空信号", "第三章 回声 · 35%", "已缓存", Icons.Filled.Cached, width = cardWidth)
                RecentBookCard("林间航线", "第一章 启程 · 12%", "未缓存", Icons.Filled.Schedule, width = cardWidth)
            }
        }

        // 我的书架 section
        Spacer(modifier = Modifier.height(12.dp))
        SectionHeader("我的书架")
        listOf(
            Triple("深空信号", "林间 · 42 万字", "35%"),
            Triple("夜航档案", "南溪 · 18 万字", "100%"),
            Triple("云端旧书", "匿名 · 8 万字", "0%")
        ).forEach { (title, author, progress) -> BookshelfItemRow(title, author, progress) }

        // 今日状态 section
        Spacer(modifier = Modifier.height(12.dp))
        SectionHeader("今日状态")
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            val chipWidth = (maxWidth - 16.dp) / 3
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusChip("32 已读分钟", Icons.Filled.Timer, width = chipWidth)
                StatusChip("4 书签条数", Icons.Filled.BookmarkBorder, width = chipWidth)
                StatusChip("12 离线缓存", Icons.Filled.CloudDownload, width = chipWidth)
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

// ── Discover Tab ──

@Composable
private fun StitchDiscoverPage() {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("发现", color = ReaderTheme.colors.controlInk, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp).semantics { heading() })
        SectionHeader("今日推荐")
        DiscoverAdapterShell.articles().take(2).forEach { article ->
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clip(RoundedCornerShape(4.dp)).background(ReaderTheme.colors.floatingControlBg).padding(10.dp)) {
                Column {
                    Text(article.title, color = ReaderTheme.colors.controlInk, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${article.source} · ${article.publishedAt}", color = ReaderTheme.colors.bodyText, fontSize = 11.sp)
                    Text(article.snippet, color = ReaderTheme.colors.bodyText, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        SectionHeader("RSS 订阅")
        DiscoverAdapterShell.subscriptions().forEach { sub ->
            StitchListItemRow(title = sub.title, subtitle = "${sub.articleCount} 篇更新", modifier = Modifier.padding(horizontal = 12.dp))
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

// ── BookSource Tab ──

@Composable
private fun StitchBookSourcePage() {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("书源", color = ReaderTheme.colors.controlInk, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp).semantics { heading() })
        SectionHeader("已启用 (${BookSourceAdapterShell.sourceList().count { it.enabled }})")
        BookSourceAdapterShell.sourceList().forEach { src ->
            StitchListItemRow(title = src.sourceName, subtitle = if (src.enabled) "已启用" else "已禁用", modifier = Modifier.padding(horizontal = 12.dp))
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

// ── Mine Tab ──

@Composable
private fun StitchMinePage(onSettingsClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("我的", color = ReaderTheme.colors.controlInk, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp).semantics { heading() })
        SectionHeader("阅读空间")
        StitchListItemRow("WebDAV 配置", "未配置", modifier = Modifier.padding(horizontal = 12.dp))
        StitchListItemRow("备份导出", "上次：-", modifier = Modifier.padding(horizontal = 12.dp))
        StitchListItemRow("备份导入", "从本地恢复", modifier = Modifier.padding(horizontal = 12.dp))
        StitchListItemRow("阅读进度同步", "未同步", modifier = Modifier.padding(horizontal = 12.dp))
        StitchListItemRow("远程 WebDAV 书籍", "不可用", modifier = Modifier.padding(horizontal = 12.dp))
        SectionHeader("关于")
        StitchListItemRow("关于 Reader", "版本 1.0", modifier = Modifier.padding(horizontal = 12.dp))
        StitchListItemRow("隐私与权限", "", modifier = Modifier.padding(horizontal = 12.dp))
        Spacer(modifier = Modifier.height(24.dp))
    }
}

// ── Shared Components ──

@Composable
private fun SectionHeader(title: String) {
    Text(title, color = ReaderTheme.colors.controlInk, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).semantics { heading() })
}

@Composable
private fun RecentBookCard(title: String, subtitle: String, tag: String, icon: ImageVector, width: androidx.compose.ui.unit.Dp) {
    Box(modifier = Modifier.width(width).clip(RoundedCornerShape(4.dp)).background(ReaderTheme.colors.floatingControlBg).padding(10.dp)) {
        Column {
            Text(title, color = ReaderTheme.colors.controlInk, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = ReaderTheme.colors.bodyText, fontSize = 11.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = ReaderTheme.colors.primary, modifier = Modifier.size(12.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(tag, color = ReaderTheme.colors.primary, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun BookshelfItemRow(title: String, author: String, progress: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(4.dp)).background(ReaderTheme.colors.metaBg), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.AutoStories, null, tint = ReaderTheme.colors.controlInk, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f, fill = true)) {
            Text(title, color = ReaderTheme.colors.controlInk, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(author, color = ReaderTheme.colors.bodyText, fontSize = 12.sp)
        }
        Text(progress, color = ReaderTheme.colors.primary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun StatusChip(text: String, icon: ImageVector, width: androidx.compose.ui.unit.Dp) {
    Box(modifier = Modifier.width(width).clip(RoundedCornerShape(4.dp)).background(ReaderTheme.colors.floatingControlBg).padding(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = ReaderTheme.colors.primary, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(text, color = ReaderTheme.colors.controlInk, fontSize = 12.sp)
        }
    }
}

@Composable
private fun StitchListItemRow(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 10.dp).semantics { contentDescription = title },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f, fill = true)) {
            Text(title, color = ReaderTheme.colors.controlInk, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) Text(subtitle, color = ReaderTheme.colors.bodyText, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
