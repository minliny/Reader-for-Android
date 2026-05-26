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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reader.android.ui.booksource.BookSourceAdapterShell
import com.reader.android.ui.discover.DiscoverAdapterShell
import com.reader.android.ui.theme.ReaderTheme

// ── Bottom Nav ──

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
        modifier = modifier.fillMaxWidth().background(ReaderTheme.colors.bottomBarBg).padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        stitchTabs.forEachIndexed { index, tab ->
            val isSelected = index == selectedIndex
            Column(
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null, role = Role.Tab, onClick = { onTabSelected(index) }
                ).semantics { contentDescription = tab.label }.padding(vertical = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(tab.icon, null, tint = if (isSelected) ReaderTheme.colors.primary else ReaderTheme.colors.controlInk, modifier = Modifier.size(20.dp))
                Text(tab.label, color = if (isSelected) ReaderTheme.colors.primary else ReaderTheme.colors.controlInk, fontSize = 10.sp, fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal)
            }
        }
    }
}

// ── App Shell ──

@Composable
fun StitchAppShell(onSearchClick: () -> Unit = {}, onSettingsClick: () -> Unit = {}) {
    StitchBookshelfPage(onSearchClick = onSearchClick)
}

// ── Bookshelf Tab ──

@Composable
private fun StitchBookshelfPage(onSearchClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().background(ReaderTheme.colors.paperBg).verticalScroll(rememberScrollState())) {
        // Header — Stitch "disappearing UI" style: minimal, no card
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f, fill = true)) {
                Text("Reader", color = ReaderTheme.colors.controlInk, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text("Quiet Digital Sanctuary", color = ReaderTheme.colors.bodyText, fontSize = 11.sp)
            }
            Box(
                modifier = Modifier.size(32.dp).clip(RoundedCornerShape(4.dp)).background(ReaderTheme.colors.metaBg)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = onSearchClick)
                    .semantics { contentDescription = "搜索" },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.Search, null, tint = ReaderTheme.colors.controlInk, modifier = Modifier.size(18.dp)) }
        }

        // 最近阅读 — compact cards with subtle background, no borders
        Spacer(modifier = Modifier.height(8.dp))
        SectionHeader("最近阅读")
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            val cardWidth = (maxWidth - 8.dp) / 2
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RecentBookCard("深空信号", "林间", "38%", "已缓存", width = cardWidth)
                RecentBookCard("林间航线", "南溪", "12%", "未缓存", width = cardWidth)
            }
        }

        // 我的书架 — flat list with 1px divider (Stitch: "no card containers")
        Spacer(modifier = Modifier.height(12.dp))
        SectionHeader("我的书架")
        listOf(
            Triple("深空信号", "林间 · 42万字", "38%"),
            Triple("夜航档案", "南溪 · 18万字", "100%"),
            Triple("云端旧书", "匿名 · 8万字", "0%")
        ).forEachIndexed { index, (title, author, progress) ->
            BookshelfItemRow(title, author, progress)
            if (index < 2) HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = ReaderTheme.colors.controlBorder)
        }
        // "导入" action — compact secondary button
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.End) {
            Box(modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(ReaderTheme.colors.floatingControlBg)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button) { }
                .semantics { contentDescription = "导入" }.padding(horizontal = 12.dp, vertical = 6.dp)
            ) { Text("导入", color = ReaderTheme.colors.controlInk, fontSize = 12.sp) }
        }

        // 今日状态 — dense metric chips
        Spacer(modifier = Modifier.height(4.dp))
        SectionHeader("今日状态")
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            val chipWidth = (maxWidth - 16.dp) / 3
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusChip("32 已读分钟", Icons.Filled.Timer, width = chipWidth)
                StatusChip("4 书签", Icons.Filled.BookmarkBorder, width = chipWidth)
                StatusChip("12 离线", Icons.Filled.CloudDownload, width = chipWidth)
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

// ── Shared Components (Stitch-aligned) ──

@Composable
private fun SectionHeader(title: String) {
    Text(title, color = ReaderTheme.colors.controlInk, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp).semantics { heading() })
}

@Composable
private fun RecentBookCard(title: String, author: String, progress: String, tag: String, width: Dp) {
    Box(modifier = Modifier.width(width).clip(RoundedCornerShape(4.dp)).background(ReaderTheme.colors.floatingControlBg).padding(8.dp)) {
        Column {
            Text(title, color = ReaderTheme.colors.controlInk, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(author, color = ReaderTheme.colors.bodyText, fontSize = 11.sp)
            Spacer(modifier = Modifier.height(4.dp))
            // Progress bar — thin Stitch style
            Box(modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(1.5.dp)).background(ReaderTheme.colors.mutedTrack)) {
                Box(modifier = Modifier.fillMaxWidth(progress.removeSuffix("%").toFloatOrNull()?.div(100f)?.coerceIn(0f, 1f) ?: 0f).height(3.dp).clip(RoundedCornerShape(1.5.dp)).background(ReaderTheme.colors.primary))
            }
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(progress, color = ReaderTheme.colors.bodyText, fontSize = 10.sp)
                Spacer(modifier = Modifier.width(4.dp))
                Text(tag, color = ReaderTheme.colors.primary, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun BookshelfItemRow(title: String, author: String, progress: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        // Thumbnail placeholder
        Box(modifier = Modifier.size(36.dp).clip(RoundedCornerShape(2.dp)).background(ReaderTheme.colors.metaBg), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.AutoStories, null, tint = ReaderTheme.colors.controlInk, modifier = Modifier.size(16.dp))
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f, fill = true)) {
            Text(title, color = ReaderTheme.colors.controlInk, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(author, color = ReaderTheme.colors.bodyText, fontSize = 12.sp)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(progress, color = ReaderTheme.colors.primary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun StatusChip(text: String, icon: ImageVector, width: Dp) {
    Box(modifier = Modifier.width(width).clip(RoundedCornerShape(4.dp)).background(ReaderTheme.colors.floatingControlBg).padding(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = ReaderTheme.colors.primary, modifier = Modifier.size(12.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(text, color = ReaderTheme.colors.controlInk, fontSize = 11.sp)
        }
    }
}
