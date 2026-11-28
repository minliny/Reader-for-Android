package io.reader.rebuild.goldens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.reader.core.theme.ReaderIconRegistry
import io.reader.rebuild.design.LocalReaderColors
import io.reader.rebuild.design.ReaderIcon

internal fun m3GoldenScenes() = listOf(
    AndroidGoldenScene("m3-manga-reading", "漫画阅读页，竖向条漫分片绘制", "第 2 章 · 1/3") {
        MangaGoldenPage(retry = false, horizontal = false)
    },
    AndroidGoldenScene("m3-manga-retry", "漫画单图失败原位重试", "保持占位几何和页序") {
        MangaGoldenPage(retry = true, horizontal = false)
    },
    AndroidGoldenScene("m3-tts-capsule", "听书胶囊悬浮态", "正文高亮与暂停入口") {
        SpeechGoldenPage(expanded = false, playing = true)
    },
    AndroidGoldenScene("m3-tts-playback", "朗读播控面板", "上一段、暂停、下一段、速率") {
        SpeechGoldenPage(expanded = true, playing = true)
    },
    AndroidGoldenScene("m3-tts-engines", "HttpTTS 配置页", "系统音色与 HttpTTS 引擎") {
        SpeechEngineGoldenPage()
    },
)

@Composable
private fun MangaGoldenPage(retry: Boolean, horizontal: Boolean) {
    val colors = LocalReaderColors.current
    Column(Modifier.fillMaxSize().background(colors.background)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            ReaderIcon(ReaderIconRegistry.back, "退出漫画")
            Text("山间异闻录", Modifier.weight(1f).padding(start = 10.dp), color = colors.primaryText)
            Text("2 / 3", color = colors.secondaryText)
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (retry) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ReaderIcon(ReaderIconRegistry.mangaImageUnavailable, "图片不可用", size = 42.dp)
                    Text("本图加载失败，已保留原位", color = colors.secondaryText)
                    Button(onClick = {}) { Text("原位重试") }
                }
            } else {
                Canvas(Modifier.fillMaxSize().padding(18.dp)) {
                    val w = size.width * if (horizontal) .78f else .92f
                    val left = (size.width - w) / 2f
                    drawRect(Color(0xFF2F6371), Offset(left, 20f), Size(w, size.height * .32f))
                    drawRect(Color(0xFF6C8E82), Offset(left, size.height * .32f + 28f), Size(w, size.height * .28f))
                    drawRect(Color(0xFFDFC081), Offset(left, size.height * .62f + 36f), Size(w, size.height * .25f))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            Button(onClick = {}) { Text("目录") }
            Button(onClick = {}) { Text(if (horizontal) "竖向" else "横向") }
            Button(onClick = {}) { Text("换源") }
            Button(onClick = {}) { Text("下载") }
        }
    }
}

@Composable
private fun SpeechGoldenPage(expanded: Boolean, playing: Boolean) {
    val colors = LocalReaderColors.current
    Column(Modifier.fillMaxSize().background(colors.background).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("山间晚读", color = colors.primaryText)
        Text("正文中句高亮保持 Core 标量位置", color = colors.secondaryText)
        Surface(Modifier.fillMaxWidth(), color = colors.surface) {
            Text("风绕过门廊，松针大把落在石上，一阵又一阵。", Modifier.padding(18.dp), color = colors.primaryText)
        }
        Surface(Modifier.fillMaxWidth(), color = colors.accentFill) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                ReaderIcon(if (playing) ReaderIconRegistry.ttsCapsulePause else ReaderIconRegistry.ttsCapsulePlay, "朗读")
                Text("正在朗读 · 第 1 段", Modifier.weight(1f).padding(start = 8.dp), color = colors.onAccent)
                Text("1.25×", color = colors.onAccent)
            }
        }
        if (expanded) Surface(Modifier.fillMaxWidth(), color = colors.panel) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("朗读播控", color = colors.primaryText)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Button(onClick = {}) { Text("上一段") }
                    Button(onClick = {}) { Text("暂停") }
                    Button(onClick = {}) { Text("下一段") }
                }
                Text("语速 1.25×   定时 未设置   引擎 系统音色", color = colors.secondaryText)
            }
        }
    }
}

@Composable
private fun SpeechEngineGoldenPage() {
    val colors = LocalReaderColors.current
    Column(Modifier.fillMaxSize().background(colors.background).padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("HttpTTS 配置", color = colors.primaryText)
        Surface(Modifier.fillMaxWidth(), color = colors.surface) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ReaderIcon(ReaderIconRegistry.ttsSystemEngine, "系统音色")
                    Text("系统音色", Modifier.weight(1f).padding(start = 10.dp), color = colors.primaryText)
                    Text("启用", color = colors.success)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ReaderIcon(ReaderIconRegistry.ttsOnlineEngine, "HttpTTS")
                    Text("Loopback Voice", Modifier.weight(1f).padding(start = 10.dp), color = colors.primaryText)
                    Text("启用", color = colors.success)
                }
            }
        }
        Button(onClick = {}) { Text("新增 HttpTTS 引擎") }
    }
}
