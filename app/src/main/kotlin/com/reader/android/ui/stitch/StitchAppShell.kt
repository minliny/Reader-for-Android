package com.reader.android.ui.stitch

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reader.android.ui.theme.ReaderTheme

data class StitchTab(val label: String, val icon: ImageVector)

@Composable
fun StitchBottomNav(selectedIndex: Int, onTabSelected: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth().background(ReaderTheme.colors.bottomBarBg).padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly) {
        listOf("书架" to Icons.AutoMirrored.Filled.MenuBook, "发现" to Icons.Filled.Search, "书源" to Icons.Filled.MoreVert, "我的" to Icons.Filled.Search)
            .forEachIndexed { i, t ->
                val sel = i == selectedIndex
                Column(Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onTabSelected(i) }
                    .semantics { contentDescription = t.first }.padding(vertical = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(t.second, null, tint = if (sel) ReaderTheme.colors.primary else ReaderTheme.colors.controlInk, modifier = Modifier.size(20.dp))
                    Text(t.first, color = if (sel) ReaderTheme.colors.primary else ReaderTheme.colors.controlInk, fontSize = 10.sp)
                }
            }
    }
}

@Composable
fun StitchAppShell(onSearchClick: () -> Unit = {}, onBookClick: (String, String) -> Unit = { _, _ -> }) {
    StitchBookshelfPage(onSearchClick, onBookClick)
}

// ── Bookshelf: dual List/Cover mode from Stitch HTML ──

@Composable
fun StitchBookshelfPage(onSearchClick: () -> Unit = {}, onBookClick: (String, String) -> Unit = { _, _ -> }) {
    var isListMode by remember { mutableStateOf(true) }
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("全部", "默认", "本地书", "哲学")

    Column(Modifier.fillMaxSize().background(ReaderTheme.colors.paperBg)) {
        // Header: from Stitch sticky top-0, flex justify-between, px-4 py-3
        Row(Modifier.fillMaxWidth().padding(16.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Filled.MenuBook, null, tint = ReaderTheme.colors.controlInk, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Text("阅读", color = ReaderTheme.colors.primary, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, lineHeight = 32.sp)
            Spacer(Modifier.weight(1f))
            Icon(if (isListMode) Icons.Filled.GridView else Icons.AutoMirrored.Filled.ViewList, "切换布局", tint = ReaderTheme.colors.controlInk, modifier = Modifier.size(24.dp)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { isListMode = !isListMode })
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Filled.Search, "搜索", tint = ReaderTheme.colors.controlInk, modifier = Modifier.size(24.dp)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSearchClick() })
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Filled.MoreVert, "更多", tint = ReaderTheme.colors.controlInk, modifier = Modifier.size(24.dp))
        }

        // Tabs: from Stitch overflow-x-auto, gap-6, border-b
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(16.dp, 12.dp)) {
            tabs.forEachIndexed { i, t ->
                val sel = i == selectedTab
                Text(t, color = if (sel) ReaderTheme.colors.primary else ReaderTheme.colors.controlInk,
                    fontSize = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.28.sp,
                    modifier = Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { selectedTab = i })
                if (i < tabs.lastIndex) Spacer(Modifier.width(24.dp))
            }
        }
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(ReaderTheme.colors.controlBorder))

        // Content
        if (isListMode) {
            // List mode from Stitch "Bookshelf List Mode" HTML: flex-col gap-6
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp, 24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)) {
                BookListItem("凡人修仙传", "忘语", 0.65f, "已读 65%", "上次阅读: 第一章 山边小村", onClick = { onBookClick("/34811", "凡人修仙传") })
                BookListItem("人类简史", "尤瓦尔·赫拉利", 0.45f, "已读 45%", "上次阅读: 第十章 农业革命", onClick = { onBookClick("人类简史", "尤瓦尔·赫拉利") })
                BookListItem("沉思录", "马可·奥勒留", 0.12f, "已读 12%", "上次阅读: 卷三", onClick = { onBookClick("沉思录", "马可·奥勒留") })
                BookListItem("哥德尔、艾舍尔、巴赫", "侯世达", 0.89f, "已读 89%", "上次阅读: 第六章", onClick = { onBookClick("哥德尔、艾舍尔、巴赫", "侯世达") })
                BookListItem("三体：地球往事", "刘慈欣", 0.65f, "已读 65%", "上次阅读: 第28章", onClick = { onBookClick("三体：地球往事", "刘慈欣") })
            }
        } else {
            // Cover mode from Stitch "Bookshelf Cover Mode" HTML: grid-cols-2 gap-x-4 gap-y-8
            LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp, 24.dp, 16.dp, 80.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(32.dp)) {
                item { BookCoverItem("三体：地球往事", 0.65f, "已读 65%") }
                item { BookCoverItem("沉思录", 0.12f, "已读 12%") }
                item { BookCoverItem("设计心理学", 1f, "已读完") }
                item { BookCoverItem("人类简史", 0.45f, "已读 45%") }
                item { BookCoverItem("哥德尔、艾舍尔、巴赫", 0.89f, "已读 89%") }
                item { BookCoverItem("1984", 0.3f, "已读 30%") }
            }
        }
    }
}

// ── List Item: from Stitch <article class="flex items-start gap-4"> ──

@Composable
private fun BookListItem(title: String, author: String, prog: Float, progText: String, lastRead: String, onClick: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().semantics { contentDescription = "$title, $author" }
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        // Cover: w-[72px] h-[104px], rounded-DEFAULT, shadow
        Box(Modifier.size(72.dp, 104.dp).clip(RoundedCornerShape(4.dp)).background(ReaderTheme.colors.floatingControlBg)
            .shadow(6.dp, RoundedCornerShape(4.dp), ambientColor = Color(0x18444444), spotColor = Color(0x18444444)),
            contentAlignment = Alignment.Center
        ) { Text(title.first().toString(), color = ReaderTheme.colors.primary, fontSize = 28.sp, fontWeight = FontWeight.Bold) }

        Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
            // Title: body-lg, 18px/30px, semibold
            Text(title, color = ReaderTheme.colors.controlInk, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, lineHeight = 30.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            // Author: label-md, 14px/20px, mt-1
            Text(author, color = ReaderTheme.colors.bodyText, fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp, letterSpacing = 0.28.sp, modifier = Modifier.padding(top = 4.dp))
            // Progress: flex items-center gap-3 mt-3, h-1
            Row(Modifier.padding(top = 12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(ReaderTheme.colors.mutedTrack)) {
                    Box(Modifier.fillMaxWidth(prog.coerceIn(0f, 1f)).height(4.dp).clip(RoundedCornerShape(2.dp)).background(ReaderTheme.colors.primary))
                }
                Text(progText, color = ReaderTheme.colors.primary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
            // Last read: label-md, text-xs, mt-1
            Text(lastRead, color = ReaderTheme.colors.bodyText, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

// ── Cover Item: from Stitch <article class="flex flex-col group"> grid-cols-2 ──

@Composable
private fun BookCoverItem(title: String, prog: Float, progText: String) {
    Column(Modifier.fillMaxWidth().semantics { contentDescription = title }
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }) {
        // aspect-[2/3], rounded-DEFAULT, shadow, mb-3
        Box(Modifier.fillMaxWidth().aspectRatio(2f/3f).clip(RoundedCornerShape(4.dp)).background(ReaderTheme.colors.floatingControlBg)
            .shadow(8.dp, RoundedCornerShape(4.dp), ambientColor = Color(0x14444444), spotColor = Color(0x14444444)),
            contentAlignment = Alignment.Center
        ) { Text(title.first().toString(), color = ReaderTheme.colors.primary, fontSize = 36.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(12.dp))
        // Title: body-md, 16px/26px, font-medium, line-clamp-2
        Text(title, color = ReaderTheme.colors.controlInk, fontSize = 16.sp, fontWeight = FontWeight.Medium, lineHeight = 26.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(8.dp))
        // Progress: h-1, rounded-full, gap-3
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(ReaderTheme.colors.mutedTrack)) {
                Box(Modifier.fillMaxWidth(prog.coerceIn(0f, 1f)).height(4.dp).clip(RoundedCornerShape(2.dp)).background(ReaderTheme.colors.primary))
            }
            Text(progText, color = ReaderTheme.colors.bodyText, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}
