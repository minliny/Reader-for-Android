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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TextFormat
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reader.android.ui.theme.ReaderTheme

// ── Reader Page — from Stitch "Reader Interface - Control Mode" HTML ──

@Composable
fun StitchReaderPage(
    bookTitle: String = "深空信号",
    chapterTitle: String = "第一章：阿长与《山海经》",
    sourceName: String = "本地书籍",
    onBack: () -> Unit = {}
) {
    var brightness by remember { mutableFloatStateOf(0.6f) }

    Box(Modifier.fillMaxSize().background(ReaderTheme.colors.paperBg)) {
        // ── Content Area (behind all controls) ──
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp)
            .padding(top = 92.dp, bottom = 180.dp)) // clear top bar + bottom controls
        {
            Text(bookTitle, color = ReaderTheme.colors.controlInk, fontSize = 28.sp, fontWeight = FontWeight.SemiBold,
                lineHeight = 36.sp, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(32.dp))
            // Sample text paragraphs from Stitch HTML
            listOf(
                "通讯台的指示灯已经闪烁了三个标准时。在这片被称为“寂静航线”的星域里，通常除了背景辐射的白噪音，什么也听不到。艾伦揉了揉干涩的眼睛，盯着那个代表未知频段的跳动波形。",
                "“这不是随机的脉冲星信号，”领航员索菲亚走到他身后，指着屏幕边缘的傅里叶变换图谱，“看这里的周期性间断，它有明显的结构，像是一种原始的二进制。”",
                "空间站外，恒星的光芒被一层稀薄的星云物质过滤，呈现出一种黯淡的铁锈红。船舱内的维生系统发出平缓的嗡嗡声，与屏幕上跳动的波形形成了一种奇异的共振。",
                "那声音起初像是冰层断裂的脆响，随后逐渐演变成一种低沉的、仿佛是在水下敲击金属的节奏。每敲击三次，就会有一次长达十秒的绝对静默。",
                "“三短，一长静默……”索菲亚的脸色变了，“在旧地球时代的航海通讯里，这是……”",
                "“求救信号。”艾伦打断了她，手指在控制台的触控板上快速滑过。"
            ).forEach { paragraph ->
                Text(paragraph, color = ReaderTheme.colors.bodyText, fontSize = 18.sp, lineHeight = 30.sp,
                    modifier = Modifier.padding(bottom = 24.dp))
            }
        }

        // ── Global mask (subtle dimming when controls visible) ──
        // Not needed for this mode

        // ── Top Bar — from Stitch: flex justify-between, px-gutter py-base, border-b ──
        Column(Modifier.fillMaxWidth().background(ReaderTheme.colors.floatingControlBg).align(Alignment.TopCenter)) {
            // Row 1: back, title, refresh/swap/more
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = ReaderTheme.colors.controlInk, modifier = Modifier.size(24.dp)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onBack() })
                Text(bookTitle, color = ReaderTheme.colors.controlInk, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(Icons.Filled.Refresh, Icons.Filled.SwapHoriz, Icons.Filled.MoreVert).forEach { icon ->
                        Icon(icon, null, tint = ReaderTheme.colors.controlInk, modifier = Modifier.size(24.dp))
                    }
                }
            }
            // Row 2: chapter title + source chip
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(chapterTitle, color = ReaderTheme.colors.bodyText, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Box(Modifier.clip(RoundedCornerShape(999.dp)).border(0.5.dp, ReaderTheme.colors.controlBorder, RoundedCornerShape(999.dp)).padding(horizontal = 12.dp, vertical = 4.dp)) {
                    Text(sourceName, color = ReaderTheme.colors.controlInk, fontSize = 12.sp)
                }
            }
            Box(Modifier.fillMaxWidth().height(0.5.dp).background(ReaderTheme.colors.controlBorder))
        }

        // ── Brightness Control (Left Sidebar) — from Stitch: fixed left, vertical, rounded-full ──
        Column(
            Modifier.align(Alignment.CenterStart).padding(start = 4.dp)
                .clip(RoundedCornerShape(999.dp)).background(ReaderTheme.colors.floatingControlBg)
                .border(0.5.dp, ReaderTheme.colors.controlBorder, RoundedCornerShape(999.dp))
                .padding(vertical = 12.dp, horizontal = 8.dp).width(40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Filled.BrightnessAuto, "自动亮度", tint = ReaderTheme.colors.controlInk, modifier = Modifier.size(20.dp))
            // Vertical slider
            Box(Modifier.weight(1f).width(4.dp).clip(RoundedCornerShape(2.dp)).background(ReaderTheme.colors.mutedTrack)) {
                Box(Modifier.fillMaxWidth().fillMaxHeight(brightness).clip(RoundedCornerShape(2.dp)).background(ReaderTheme.colors.primary))
            }
            Icon(Icons.Filled.ChevronRight, null, tint = ReaderTheme.colors.controlInk, modifier = Modifier.size(20.dp))
        }

        // ── Bottom Control Panel — from Stitch: fixed bottom, rounded-t-xl ──
        Column(Modifier.fillMaxWidth().align(Alignment.BottomCenter)
            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)).background(ReaderTheme.colors.floatingControlBg)
        ) {
            // Row 1: 4 quick action buttons — from Stitch: grid-cols-4, items-center
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                QuickAction(Icons.Filled.Search, "搜索")
                QuickAction(Icons.Filled.AutoMode, "自动翻页")
                QuickAction(Icons.Filled.SwapHoriz, "内容替换")
                QuickAction(Icons.Filled.DarkMode, "夜间模式")
            }
            Box(Modifier.fillMaxWidth().height(0.5.dp).background(ReaderTheme.colors.controlBorder.copy(alpha = 0.5f)))

            // Row 2: Page control with progress
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Filled.ChevronLeft, "上一章", tint = ReaderTheme.colors.controlInk, modifier = Modifier.size(24.dp))
                Box(Modifier.weight(1f).height(8.dp)) {
                    Box(Modifier.fillMaxWidth().height(4.dp).align(Alignment.Center).clip(RoundedCornerShape(2.dp)).background(ReaderTheme.colors.mutedTrack))
                    Box(Modifier.fillMaxWidth(0.45f).height(4.dp).align(Alignment.CenterStart).clip(RoundedCornerShape(2.dp)).background(ReaderTheme.colors.primary))
                }
                Icon(Icons.Filled.ChevronRight, "下一章", tint = ReaderTheme.colors.controlInk, modifier = Modifier.size(24.dp))
            }
            Box(Modifier.fillMaxWidth().height(0.5.dp).background(ReaderTheme.colors.controlBorder.copy(alpha = 0.5f)))

            // Row 3: Bottom nav — 目录/朗读/界面/设置
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly) {
                listOf(
                    Icons.AutoMirrored.Filled.MenuBook to "目录",
                    Icons.Filled.VolumeUp to "朗读",
                    Icons.Filled.TextFormat to "界面",
                    Icons.Filled.Settings to "设置"
                ).forEach { (icon, label) ->
                    Column(Modifier.padding(4.dp).semantics { contentDescription = label },
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(icon, null, tint = ReaderTheme.colors.controlInk, modifier = Modifier.size(22.dp))
                        Text(label, color = ReaderTheme.colors.controlInk, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickAction(icon: ImageVector, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(4.dp).semantics { contentDescription = label }) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(8.dp))
            .background(ReaderTheme.colors.quickButtonBg).border(0.5.dp, ReaderTheme.colors.controlBorder, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = ReaderTheme.colors.controlInk, modifier = Modifier.size(22.dp))
        }
        Text(label, color = ReaderTheme.colors.controlInk, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp))
    }
}
