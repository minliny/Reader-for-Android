package com.reader.android.ui.stitch

import androidx.compose.foundation.background
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

// Direct translation of Stitch MCP HTML:
// "阅读页控制层参照模板 - 基于修正版含亮度条 (P1 Fixed) - 箭头修复版"
//
// Tailwind → Compose mapping:
//   px-gutter=24dp, py-base=4dp, py-sm=12dp, py-md=24dp
//   rounded-full=9999px, rounded-DEFAULT=4dp, rounded-t-xl
//   headline-lg-mobile=28px/36px, body-lg=18px/30px, label-md=14px/20px
//   gap-2=8dp, gap-4=16dp, h-40=160dp, h-1=4dp, h-1.5=6dp
//   shadow-lg, border-dashed removed (debug styling), bg-[#fdf6e3]→paperBg

@Composable
fun StitchReaderPage(
    bookTitle: String = "深空信号",
    chapterTitle: String = "第一章：阿长与《山海经》",
    sourceName: String = "本地书籍",
    onBack: () -> Unit = {}
) {
    var brightness by remember { mutableFloatStateOf(0.6f) }

    Box(Modifier.fillMaxSize().background(ReaderTheme.colors.paperBg)) {
        // ── Content area ──
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp).padding(top = 128.dp, bottom = 192.dp)
        ) {
            Text(bookTitle, color = ReaderTheme.colors.controlInk, fontSize = 28.sp, fontWeight = FontWeight.SemiBold,
                lineHeight = 36.sp, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(32.dp))
            listOf(
                "通讯台的指示灯已经闪烁了三个标准时。在这片被称为「寂静航线」的星域里，通常除了背景辐射的白噪音，什么也听不到。艾伦揉了揉干涩的眼睛，盯着那个代表未知频段的跳动波形。",
                "「这不是随机的脉冲星信号。」领航员索菲亚走到他身后，指着屏幕边缘的傅里叶变换图谱：「看这里的周期性间断，它有明显的结构，像是一种原始的二进制。」",
                "空间站外，恒星的光芒被一层稀薄的星云物质过滤，呈现出一种黯淡的铁锈红。船舱内的维生系统发出平缓的嗡嗡声，与屏幕上跳动的波形形成了一种奇异的共振。",
                "那声音起初像是冰层断裂的脆响，随后逐渐演变成一种低沉的、仿佛是在水下敲击金属的节奏。每敲击三次，就会有一次长达十秒的绝对静默。",
                "「三短，一长静默……」索菲亚的脸色变了，「在旧地球时代的航海通讯里，这是……」",
                "「求救信号。」艾伦打断了她，手指在控制台的触控板上快速滑过。"
            ).forEach { p ->
                Text(p, color = ReaderTheme.colors.bodyText, fontSize = 18.sp, lineHeight = 30.sp,
                    modifier = Modifier.padding(bottom = 24.dp))
            }
        }

        // ── Top Bar (fixed, bg-surface-container) ──
        Column(Modifier.fillMaxWidth().background(ReaderTheme.colors.floatingControlBg).align(Alignment.TopCenter)) {
            // Row 1: back (arrow_back) | title | refresh + swap_horiz + more_vert
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = ReaderTheme.colors.controlInk,
                    modifier = Modifier.size(24.dp).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onBack() })
                Text(bookTitle, color = ReaderTheme.colors.controlInk, fontSize = 24.sp, fontWeight = FontWeight.Bold,
                    lineHeight = 32.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(Icons.Filled.Refresh, Icons.Filled.SwapHoriz, Icons.Filled.MoreVert).forEach { icon ->
                        Icon(icon, null, tint = ReaderTheme.colors.controlInk, modifier = Modifier.size(24.dp)
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { })
                    }
                }
            }
            // Row 2: chapter + source chip
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(chapterTitle, color = ReaderTheme.colors.bodyText, fontSize = 14.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Box(Modifier.clip(RoundedCornerShape(999.dp)).background(ReaderTheme.colors.floatingControlBgAlt)
                    .padding(horizontal = 12.dp, vertical = 4.dp)) {
                    Text(sourceName, color = ReaderTheme.colors.controlInk, fontSize = 12.sp)
                }
            }
        }

        // ── Brightness Dock (left sidebar, rounded-full) ──
        Column(
            Modifier.align(Alignment.CenterStart).padding(start = 8.dp).width(48.dp)
                .clip(RoundedCornerShape(999.dp)).background(ReaderTheme.colors.floatingControlBg)
                .padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(Icons.Filled.BrightnessAuto, "自动亮度", tint = ReaderTheme.colors.controlInk, modifier = Modifier.size(24.dp))
            // Vertical track (h-40 = 160dp, w-1.5 = 6dp)
            Box(Modifier.weight(1f).width(6.dp).clip(CircleShape).background(ReaderTheme.colors.mutedTrack.copy(alpha = 0.3f))) {
                Box(Modifier.fillMaxWidth().fillMaxHeight(brightness).clip(CircleShape).background(ReaderTheme.colors.primary))
            }
            Icon(Icons.Filled.ChevronRight, "右移", tint = ReaderTheme.colors.controlInk, modifier = Modifier.size(24.dp))
        }

        // ── Bottom Panel (fixed, rounded-t-xl, shadow-lg) ──
        Column(Modifier.fillMaxWidth().align(Alignment.BottomCenter)
            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
            .background(ReaderTheme.colors.floatingControlBg)
        ) {
            // Quick Actions: grid-cols-4 (icon + label)
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp),
                horizontalArrangement = Arrangement.SpaceEvenly) {
                QuickAction("搜索", Icons.Filled.Search)
                QuickAction("自动翻页", Icons.Filled.AutoMode)
                QuickAction("内容替换", Icons.Filled.SwapHoriz)
                QuickAction("夜间模式", Icons.Filled.DarkMode)
            }

            // Page Control: prev | progress bar | next
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Icon(Icons.Filled.ChevronLeft, "本章内上一页", tint = ReaderTheme.colors.controlInk, modifier = Modifier.size(24.dp))
                Box(Modifier.weight(1f).height(8.dp)) {
                    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(2.dp)).background(ReaderTheme.colors.mutedTrack.copy(alpha = 0.3f)))
                    Box(Modifier.fillMaxWidth(0.45f).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(ReaderTheme.colors.primary))
                }
                Icon(Icons.Filled.ChevronRight, "本章内下一页", tint = ReaderTheme.colors.controlInk, modifier = Modifier.size(24.dp))
            }

            // Bottom Nav: 4 items (h-16, pb-safe)
            Row(Modifier.fillMaxWidth().height(64.dp).padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                listOf(
                    Icons.AutoMirrored.Filled.MenuBook to "目录",
                    Icons.Filled.VolumeUp to "朗读",
                    Icons.Filled.TextFormat to "界面",
                    Icons.Filled.Settings to "设置"
                ).forEach { (icon, label) ->
                    Column(Modifier.width(64.dp).semantics { contentDescription = label },
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(icon, null, tint = ReaderTheme.colors.controlInk, modifier = Modifier.size(24.dp))
                        Text(label, color = ReaderTheme.colors.controlInk, fontSize = 14.sp, lineHeight = 20.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickAction(label: String, icon: ImageVector) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(4.dp).semantics { contentDescription = label }
    ) {
        Icon(icon, null, tint = ReaderTheme.colors.controlInk, modifier = Modifier.size(24.dp))
        Text(label, color = ReaderTheme.colors.controlInk, fontSize = 14.sp, lineHeight = 20.sp)
    }
}
