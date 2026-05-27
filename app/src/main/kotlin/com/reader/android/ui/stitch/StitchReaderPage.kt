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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TextFormat
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.VolumeUp
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reader.android.ui.theme.ReaderTheme

// Stitch MCP Export: "Reader Interface - Control Mode" (zip2/_3/code.html)
// Tailwind → Compose mapping:
//   px-gutter=24dp, py-base=4dp, py-sm=12dp, py-md=24dp
//   headline-md=24px/32px/600, body-lg=18px/30px/400, label-md=14px/20px/500
//   rounded-full=9999px, rounded-xl=12dp, rounded-lg=8dp
//   bg-[#fdf6e3]→pageTint, bg-surface-container→floatingControlBg
//   text-[#2c2c2c]→bodyText, primary→#366179

private val PageTint = Color(0xFFFDF6E3)
private val BodyTextInk = Color(0xFF2C2C2C)

@Composable
fun StitchReaderPage(
    bookTitle: String = "深空信号",
    chapterTitle: String = "第一章：阿长与《山海经》",
    sourceName: String = "本地书籍",
    showSearch: Boolean = false,
    onBack: () -> Unit = {},
    onDismissSearch: () -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }

    Box(Modifier.fillMaxSize().background(PageTint)) {
        // ── Content: pt-32 pb-48, px-gutter=24dp ──
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp).padding(top = 96.dp, bottom = 220.dp)
        ) {
            // title: headline-lg-mobile 28px/36px semibold
            Text(bookTitle, color = ReaderTheme.colors.controlInk,
                fontSize = 28.sp, fontWeight = FontWeight.SemiBold, lineHeight = 36.sp)
            Spacer(Modifier.height(32.dp)) // mb-8
            // Body: body-lg 18px/30px, text-justify, text-[#2c2c2c]
            listOf(
                "通讯台的指示灯已经闪烁了三个标准时。在这片被称为「寂静航线」的星域里，通常除了背景辐射的白噪音，什么也听不到。艾伦揉了揉干涩的眼睛，盯着那个代表未知频段的跳动波形。",
                "「这不是随机的脉冲星信号，」领航员索菲亚走到他身后，指着屏幕边缘的傅里叶变换图谱，「看这里的周期性间断，它有明显的结构，像是一种原始的二进制。」",
                "空间站外，恒星的光芒被一层稀薄的星云物质过滤，呈现出一种黯淡的铁锈红。船舱内的维生系统发出平缓的嗡嗡声，与屏幕上跳动的波形形成了一种奇异的共振。艾伦将接收增益调到最大，一段夹杂着大量杂音的尖锐音频突然在主控室里回荡开来。",
                "那声音起初像是冰层断裂的脆响，随后逐渐演变成一种低沉的、仿佛是在水下敲击金属的节奏。每敲击三次，就会有一次长达十秒的绝对静默。",
                "「三短，一长静默……」索菲亚的脸色变了，「在旧地球时代的航海通讯里，这是……」",
                "「求救信号。」艾伦打断了她，手指在控制台的触控板上快速滑过，试图锁定信号源的坐标。「但问题是，这个波段是我们在五十年前就已经淘汰的军用加密频道。而且，坐标指向的是——」",
                "他咽了一口唾沫，看着屏幕上解析出的星图坐标。",
                "「指向的是我们母星系的方向。可那里，在四十年前的大坍缩中，就已经什么都不剩了。」"
            ).forEach { p ->
                Text(p, color = BodyTextInk, fontSize = 18.sp, lineHeight = 30.sp,
                    textAlign = TextAlign.Justify, modifier = Modifier.padding(bottom = 24.dp))
            }
        }

        // ── Dark overlay mask: bg-black/20, top:64px, bottom:234px ──
        Box(Modifier.fillMaxSize().padding(top = 64.dp, bottom = 234.dp)
            .background(Color.Black.copy(alpha = 0.1f)))

        // ── Header: fixed top-0, bg-surface-container, shadow-sm ──
        Column(Modifier.fillMaxWidth().shadow(2.dp).background(ReaderTheme.colors.floatingControlBgAlt)) {
            // Single row: arrow_back | Reader + source subtext | more_vert
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = ReaderTheme.colors.primary,
                    modifier = Modifier.size(24.dp).clip(RoundedCornerShape(999.dp)).clickable(
                        interactionSource = remember { MutableInteractionSource() }, indication = null) { onBack() })
                // Center: title + subtext (flex-col items-center justify-center flex-1)
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Reader", color = ReaderTheme.colors.controlInk, fontSize = 24.sp,
                        fontWeight = FontWeight.Bold, lineHeight = 32.sp)
                    Text("来源：$sourceName", color = ReaderTheme.colors.bodyText, fontSize = 14.sp,
                        lineHeight = 20.sp, letterSpacing = 0.28.sp)
                }
                Icon(Icons.Filled.MoreVert, null, tint = ReaderTheme.colors.primary,
                    modifier = Modifier.size(24.dp).clip(RoundedCornerShape(999.dp)).clickable(
                        interactionSource = remember { MutableInteractionSource() }, indication = null) { })
            }
        }

        // ── Search overlay: fixed left-4 right-4, between header and bottom ──
        if (showSearch) {
            Column(
                Modifier.padding(horizontal = 16.dp).padding(top = 80.dp, bottom = 262.dp).fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp)).background(ReaderTheme.colors.floatingControlBg)
                    .border(0.5.dp, ReaderTheme.colors.controlBorder, RoundedCornerShape(12.dp))
                    .shadow(6.dp, RoundedCornerShape(12.dp))
            ) {
                // Title bar: flex items-center justify-between px-4 py-3
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("搜索", color = ReaderTheme.colors.controlInk, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Icon(Icons.Filled.Close, "关闭", tint = ReaderTheme.colors.controlInk,
                        modifier = Modifier.size(20.dp).clickable(
                            interactionSource = remember { MutableInteractionSource() }, indication = null) { onDismissSearch() })
                }
                // Search input: bg-surface-container-low, rounded-lg, px-3 py-2
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(8.dp)).background(ReaderTheme.colors.metaBg).padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Search, null, tint = ReaderTheme.colors.controlInk, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("搜索当前章节内容", color = ReaderTheme.colors.bodyText.copy(alpha = 0.6f), fontSize = 14.sp)
                }
                // Results header: flex items-center justify-between px-4 py-2
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text("共 3 处结果", color = ReaderTheme.colors.bodyText, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    listOf("上一处", "下一处").forEach { label ->
                        Text(label, color = ReaderTheme.colors.controlInk, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 4.dp)
                            .clip(RoundedCornerShape(4.dp)).border(0.5.dp, ReaderTheme.colors.controlBorder, RoundedCornerShape(4.dp))
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }
                // Result items
                listOf("寂静航线" to "第一章", "夹杂着" to "第三章").forEach { (match, ch) ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                        Text("1", color = ReaderTheme.colors.bodyText, fontSize = 12.sp)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("…$match…", color = ReaderTheme.colors.controlInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(ch, color = ReaderTheme.colors.bodyText, fontSize = 10.sp)
                        }
                    }
                }
            }
        }

        // ── Bottom Panel: fixed bottom-0, bg-surface-container, rounded-t-xl, shadow-lg ──
        Column(Modifier.fillMaxWidth().align(Alignment.BottomCenter)
            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
            .shadow(8.dp, RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
            .background(ReaderTheme.colors.floatingControlBg)
        ) {
            // Layer 1: Shortcuts (grid-cols-4, icon+label, gap-2)
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween) {
                QuickAction("搜索", Icons.Filled.Search, selected = showSearch)
                QuickAction("自动翻页", Icons.Filled.AutoMode)
                QuickAction("内容替换", Icons.Filled.SwapHoriz)
                QuickAction("夜间模式", Icons.Filled.DarkMode)
            }

            // Layer 2: Progress bar + skip buttons
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "本章内上一页", tint = ReaderTheme.colors.controlInk, modifier = Modifier.size(24.dp)
                    .clip(RoundedCornerShape(999.dp)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { })
                // Progress track
                Box(Modifier.weight(1f).height(8.dp)) {
                    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(2.dp)).background(ReaderTheme.colors.mutedTrack.copy(alpha = 0.3f)))
                    Box(Modifier.fillMaxWidth(0.45f).height(8.dp).clip(RoundedCornerShape(2.dp)).background(ReaderTheme.colors.primary))
                    Box(Modifier.size(16.dp).align(Alignment.CenterStart).offset(x = 4.dp, y = 0.dp)
                        .clip(RoundedCornerShape(999.dp)).background(Color.White)
                        .border(0.5.dp, ReaderTheme.colors.controlBorder.copy(alpha = 0.5f), RoundedCornerShape(999.dp)))
                }
                Icon(Icons.Filled.ChevronRight, "本章内下一页", tint = ReaderTheme.colors.controlInk, modifier = Modifier.size(24.dp)
                    .clip(RoundedCornerShape(999.dp)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { })
            }

            // Layer 3: Bottom Nav (h-16, border-t)
            Row(Modifier.fillMaxWidth().height(64.dp).background(ReaderTheme.colors.floatingControlBg)
                .border(0.5.dp, ReaderTheme.colors.controlBorder.copy(alpha = 0.3f)),
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
private fun QuickAction(label: String, icon: ImageVector, selected: Boolean = false) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(4.dp).semantics { contentDescription = label }
    ) {
        Icon(icon, null, tint = if (selected) ReaderTheme.colors.primary else ReaderTheme.colors.controlInk,
            modifier = Modifier.size(24.dp))
        Text(label, lineHeight = 20.sp,
            color = if (selected) ReaderTheme.colors.primary else ReaderTheme.colors.controlInk,
            fontSize = 14.sp)
    }
}
