package com.reader.android.ui.stitch

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reader.android.ui.theme.ReaderTheme

// ── 1. 导入本地书籍 (zip1/_4) ──

@Composable
fun StitchImportBookPage(onBack: () -> Unit = {}) {
    Column(Modifier.fillMaxSize().background(ReaderTheme.colors.paperBg)) {
        Row(Modifier.fillMaxWidth().height(48.dp).background(ReaderTheme.colors.paperBg)
            .border(0.5.dp, ReaderTheme.colors.controlBorder).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = ReaderTheme.colors.controlInk,
                modifier = Modifier.size(24.dp).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onBack() })
            Spacer(Modifier.weight(1f))
            Text("导入本地书籍", color = ReaderTheme.colors.controlInk, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f)); Spacer(Modifier.size(24.dp))
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ImportOption("从文件管理器选择", "支持 TXT, EPUB 格式", Icons.Filled.FileOpen)
            ImportOption("从文件夹导入", "批量导入整个文件夹的书籍", Icons.Filled.Folder)
            ImportOption("从其他应用分享", "接收其他 App 分享的书籍文件", Icons.Filled.Add)
        }
    }
}

@Composable
private fun ImportOption(title: String, subtitle: String, icon: ImageVector) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(ReaderTheme.colors.floatingControlBg).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(4.dp)).background(ReaderTheme.colors.metaBg), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = ReaderTheme.colors.primary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = ReaderTheme.colors.controlInk, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = ReaderTheme.colors.bodyText, fontSize = 12.sp)
        }
    }
}

// ── 2. 更多菜单 (zip1/_6) ──

@Composable
fun StitchBookMoreMenu(onDismiss: () -> Unit = {}, onAction: (String) -> Unit = {}) {
    Column(Modifier.fillMaxSize().background(ReaderTheme.colors.paperBg.copy(alpha = 0.3f))
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDismiss() },
        verticalArrangement = Arrangement.Bottom) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
            .background(ReaderTheme.colors.paperBg)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }.padding(vertical = 8.dp)) {
            listOf("继续阅读" to Icons.Filled.Book, "查看详情" to Icons.Filled.Search, "移出书架" to Icons.Filled.Delete, "缓存本书" to Icons.Filled.FileOpen)
                .forEach { (label, icon) ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onAction(label); onDismiss() }
                        .semantics { contentDescription = label }, verticalAlignment = Alignment.CenterVertically) {
                        Icon(icon, null, tint = ReaderTheme.colors.controlInk, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(16.dp))
                        Text(label, color = ReaderTheme.colors.controlInk, fontSize = 16.sp)
                    }
                }
            Spacer(Modifier.height(8.dp))
        }
    }
}

// ── 3. 分组管理 (zip1/_7) ──

@Composable
fun StitchGroupManagementPage(onBack: () -> Unit = {}) {
    Column(Modifier.fillMaxSize().background(ReaderTheme.colors.paperBg)) {
        Row(Modifier.fillMaxWidth().height(48.dp).background(ReaderTheme.colors.paperBg)
            .border(0.5.dp, ReaderTheme.colors.controlBorder).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = ReaderTheme.colors.controlInk, modifier = Modifier.size(24.dp)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onBack() })
            Spacer(Modifier.weight(1f))
            Text("书架分组管理", color = ReaderTheme.colors.controlInk, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Icon(Icons.Filled.Add, "新增", tint = ReaderTheme.colors.primary, modifier = Modifier.size(24.dp))
        }
        LazyColumn(Modifier.weight(1f)) {
            items(listOf("全部", "默认", "本地书", "哲学", "科幻")) { group ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Folder, null, tint = ReaderTheme.colors.controlInk, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(group, color = ReaderTheme.colors.controlInk, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Icon(Icons.Filled.Edit, null, tint = ReaderTheme.colors.controlInk, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(12.dp))
                    Icon(Icons.Filled.Delete, null, tint = ReaderTheme.colors.controlInk.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

// ── 4. 朗读控制 (zip2/_2) ──

@Composable
fun StitchTtsOverlay(onDismiss: () -> Unit = {}) {
    var isPlaying by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(ReaderTheme.colors.paperBg.copy(alpha = 0.3f))
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDismiss() },
        verticalArrangement = Arrangement.Bottom) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
            .background(ReaderTheme.colors.floatingControlBg)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
            .shadow(2.dp, RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)).padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("朗读控制", color = ReaderTheme.colors.controlInk, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).semantics { heading() })
                Icon(Icons.Filled.Close, "关闭", tint = ReaderTheme.colors.controlInk, modifier = Modifier.size(20.dp)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDismiss() })
            }
            Text("第一章 深空信号", color = ReaderTheme.colors.bodyText, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Icon(if (isPlaying) Icons.Filled.Stop else Icons.Filled.PlayArrow, "播放", tint = ReaderTheme.colors.primary,
                    modifier = Modifier.size(40.dp).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { isPlaying = !isPlaying })
            }
            Spacer(Modifier.height(16.dp))
            StitchSliderRow("语速", 1f, 0.5f..3f)
            StitchSliderRow("音量", 0.7f, 0f..1f)
            StitchSettingRow("定时关闭", "不开启")
            StitchSettingRow("朗读音色", "温和女声")
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun StitchSliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = ReaderTheme.colors.controlInk, fontSize = 14.sp)
            Text("${(value * 100).toInt()}%", color = ReaderTheme.colors.bodyText, fontSize = 12.sp)
        }
        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(ReaderTheme.colors.mutedTrack)) {
            Box(Modifier.fillMaxWidth(value.coerceIn(range)).height(4.dp).clip(RoundedCornerShape(2.dp)).background(ReaderTheme.colors.primary))
        }
    }
}

@Composable
private fun StitchSettingRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = ReaderTheme.colors.controlInk, fontSize = 14.sp)
        Text(value, color = ReaderTheme.colors.bodyText, fontSize = 12.sp)
    }
}

// ── 5. 阅读设置 (zip2/_8) ──

@Composable
fun StitchReaderSettingsOverlay(onDismiss: () -> Unit = {}) {
    Column(Modifier.fillMaxSize().background(ReaderTheme.colors.paperBg.copy(alpha = 0.3f))
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDismiss() },
        verticalArrangement = Arrangement.Bottom) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
            .background(ReaderTheme.colors.floatingControlBg)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
            .shadow(2.dp, RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)).padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("阅读设置", color = ReaderTheme.colors.controlInk, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).semantics { heading() })
                Icon(Icons.Filled.Close, "关闭", tint = ReaderTheme.colors.controlInk, modifier = Modifier.size(20.dp)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDismiss() })
            }
            Spacer(Modifier.height(12.dp))
            Text("文字", color = ReaderTheme.colors.controlInk, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
            StitchSettingRow("字体", "默认"); StitchSettingRow("字号", "18"); StitchSettingRow("字距", "标准")
            Text("段落", color = ReaderTheme.colors.controlInk, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
            StitchSettingRow("缩进", "2 字符"); StitchSettingRow("行距", "标准")
            Text("界面", color = ReaderTheme.colors.controlInk, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
            StitchSettingRow("翻页动画", "覆盖"); StitchSettingRow("主题", "米色纸张")
            Spacer(Modifier.height(8.dp))
        }
    }
}
