package com.reader.android.ui.stitch

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
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
    Row(
        modifier = modifier.fillMaxWidth().background(ReaderTheme.colors.bottomBarBg).padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        listOf(
            StitchTab("书架", Icons.AutoMirrored.Filled.MenuBook),
            StitchTab("发现", Icons.Filled.Search),
            StitchTab("书源", Icons.Filled.MoreVert),
            StitchTab("我的", Icons.Filled.Search)
        ).forEachIndexed { index, tab ->
            val sel = index == selectedIndex
            Column(Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onTabSelected(index) }
                .semantics { contentDescription = tab.label }.padding(vertical = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(tab.icon, null, tint = if (sel) ReaderTheme.colors.primary else ReaderTheme.colors.controlInk, modifier = Modifier.size(20.dp))
                Text(tab.label, color = if (sel) ReaderTheme.colors.primary else ReaderTheme.colors.controlInk, fontSize = 10.sp)
            }
        }
    }
}

@Composable
fun StitchAppShell(onSearchClick: () -> Unit = {}) { StitchBookshelfPage(onSearchClick) }

// ── Bookshelf Page — direct HTML Tailwind→Compose translation ──

@Composable
fun StitchBookshelfPage(onSearchClick: () -> Unit) {
    Column(Modifier.fillMaxSize().background(ReaderTheme.colors.paperBg).verticalScroll(rememberScrollState())) {
        // Header: sticky, flex, justify-between, px-4 py-3
        Row(Modifier.fillMaxWidth().padding(16.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Filled.MenuBook, null, tint = ReaderTheme.colors.controlInk, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Text("阅读", color = ReaderTheme.colors.primary, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Icon(Icons.Filled.Search, "搜索", tint = ReaderTheme.colors.controlInk, modifier = Modifier.size(24.dp)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSearchClick() })
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Filled.MoreVert, "更多", tint = ReaderTheme.colors.controlInk, modifier = Modifier.size(24.dp))
        }

        // Tabs: scrollable, gap-6, border-b
        var tab by remember { mutableIntStateOf(0) }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(16.dp, 12.dp)) {
            listOf("全部", "默认", "本地书").forEachIndexed { i, t ->
                val sel = i == tab
                Text(t, color = if (sel) ReaderTheme.colors.primary else ReaderTheme.colors.controlInk,
                    fontSize = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.28.sp,
                    modifier = Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { tab = i })
                if (i < 2) Spacer(Modifier.width(24.dp)) // gap-6
            }
        }
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(ReaderTheme.colors.controlBorder))

        // Book List: px-4 py-6, gap-6
        Column(Modifier.padding(16.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            BookItem("人类简史", "尤瓦尔·赫拉利", 0.45f, "45%", "上次阅读: 第十章 农业革命")
            BookItem("沉思录", "马可·奥勒留", 0.12f, "12%", "上次阅读: 卷三")
            BookItem("哥德尔、艾舍尔、巴赫", "侯世达", 0.89f, "89%", "上次阅读: 第六章")
        }
        Spacer(Modifier.height(80.dp))
    }
}

// Direct <article> translation: flex items-start gap-4
@Composable
private fun BookItem(title: String, author: String, prog: Float, progText: String, lastRead: String) {
    Row(Modifier.fillMaxWidth().semantics { contentDescription = "$title, $author" }, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        // Cover: w-[72px] h-[104px], rounded-DEFAULT, shadow
        Box(Modifier.size(72.dp, 104.dp).clip(RoundedCornerShape(4.dp)).background(ReaderTheme.colors.floatingControlBg)
            .shadow(6.dp, RoundedCornerShape(4.dp), ambientColor = Color(0x18444444), spotColor = Color(0x18444444)),
            contentAlignment = Alignment.Center
        ) { Text(title.first().toString(), color = ReaderTheme.colors.primary, fontSize = 28.sp, fontWeight = FontWeight.Bold) }

        Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
            Text(title, color = ReaderTheme.colors.controlInk, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, lineHeight = 30.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(author, color = ReaderTheme.colors.bodyText, fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp, letterSpacing = 0.28.sp, modifier = Modifier.padding(top = 4.dp))
            // Progress bar: h-1, rounded-full
            Row(Modifier.padding(top = 12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(ReaderTheme.colors.mutedTrack)) {
                    Box(Modifier.fillMaxWidth(prog.coerceIn(0f, 1f)).height(4.dp).clip(RoundedCornerShape(2.dp)).background(ReaderTheme.colors.primary))
                }
                Text(progText, color = ReaderTheme.colors.primary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
            Text(lastRead, color = ReaderTheme.colors.bodyText, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}
