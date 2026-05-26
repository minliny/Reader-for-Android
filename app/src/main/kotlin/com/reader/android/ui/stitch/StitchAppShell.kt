package com.reader.android.ui.stitch

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        modifier = modifier
            .fillMaxWidth()
            .background(ReaderTheme.colors.bottomBarBg)
            .padding(vertical = 6.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly
    ) {
        stitchTabs.forEachIndexed { index, tab ->
            val isSelected = index == selectedIndex
            Column(
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null, role = Role.Tab,
                        onClick = { onTabSelected(index) }
                    )
                    .semantics { contentDescription = tab.label }
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    tab.icon, contentDescription = null,
                    tint = if (isSelected) ReaderTheme.colors.primary else ReaderTheme.colors.controlInk,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    tab.label,
                    color = if (isSelected) ReaderTheme.colors.primary else ReaderTheme.colors.controlInk,
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
                )
            }
        }
    }
}

// ── Stitch App Shell ──

@Composable
fun StitchAppShell() {
    var selectedTab by remember { mutableIntStateOf(0) }
    Column(modifier = Modifier.fillMaxSize().background(ReaderTheme.colors.paperBg)) {
        Column(modifier = Modifier.weight(1f)) {
            when (selectedTab) {
                0 -> StitchBookshelfPlaceholder()
                1 -> StitchDiscoverPlaceholder()
                2 -> StitchBookSourcePlaceholder()
                3 -> StitchMinePlaceholder()
            }
        }
        StitchBottomNav(selectedIndex = selectedTab, onTabSelected = { selectedTab = it })
    }
}

@Composable
private fun StitchBookshelfPlaceholder() = StitchTabPlaceholder("书架", subtitle = "最近阅读 · 全部书籍", tabs = stitchTabs)

@Composable
private fun StitchDiscoverPlaceholder() = StitchTabPlaceholder("发现", subtitle = "今日推荐 · RSS 订阅", tabs = stitchTabs)

@Composable
private fun StitchBookSourcePlaceholder() = StitchTabPlaceholder("书源", subtitle = "书源管理 · 最近测试结果", tabs = stitchTabs)

@Composable
private fun StitchMinePlaceholder() = StitchTabPlaceholder("我的", subtitle = "阅读空间 · WebDAV · 备份", tabs = stitchTabs)

@Composable
private fun StitchTabPlaceholder(title: String, subtitle: String, tabs: List<StitchTab>) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp)
    ) {
        Text(title, color = ReaderTheme.colors.controlInk, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Text(subtitle, color = ReaderTheme.colors.bodyText, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(12.dp))
        // Placeholder content
        Text("Stitch 风格原生界面", color = ReaderTheme.colors.bodyText.copy(alpha = 0.5f), fontSize = 14.sp)
        Text("基于 Stitch MCP 设计系统重建", color = ReaderTheme.colors.bodyText.copy(alpha = 0.4f), fontSize = 12.sp)
    }
}
