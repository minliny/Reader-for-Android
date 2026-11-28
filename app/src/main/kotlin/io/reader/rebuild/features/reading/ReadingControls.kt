package io.reader.rebuild.features.reading

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.reader.core.theme.ReaderDesignMetrics as Metrics
import com.reader.core.theme.ReaderIconRegistry
import com.reader.core.theme.ReaderPaperThemes
import io.reader.rebuild.design.LocalReaderColors
import io.reader.rebuild.design.ReaderAction
import io.reader.rebuild.design.ReaderIcon
import io.reader.rebuild.design.readerTextStyle

private enum class ControlModule { PAGE, SPEECH, APPEARANCE, SETTINGS }

@Composable
internal fun ReadingControlsContent(
    title: String,
    chapter: String,
    appearance: ReadingAppearance,
    expanded: Boolean,
    onExpand: () -> Unit = {},
    onAppearance: (ReadingAppearance) -> Unit = {},
    onDirectory: () -> Unit = {},
    onBookmarks: () -> Unit = {},
    onPrevious: () -> Unit = {},
    onNext: () -> Unit = {},
    onBack: () -> Unit = {},
    onRefresh: (() -> Unit)? = null,
    onSwitch: (() -> Unit)? = null,
    onSpeech: (() -> Unit)? = null,
    chapterNumber: Int = 0,
    chapterCount: Int = 0,
) {
    val colors = LocalReaderColors.current
    var module by remember { mutableStateOf(ControlModule.PAGE) }
    var brightness by remember { mutableStateOf(.68f) }
    Box(Modifier.widthIn(max = 620.dp).fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(Modifier.weight(1f), color = colors.panel, contentColor = colors.primaryText,
                shadowElevation = 8.dp, shape = RoundedCornerShape(26.dp), border = BorderStroke(1.dp, colors.outline)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ControlHeader(title, chapter, expanded, onBack, onExpand, onSwitch)
                    if (expanded) {
                        ControlModuleNavigation(module) { module = it }
                        when (module) {
                            ControlModule.PAGE -> FullPageControls(appearance, onAppearance, onDirectory, onBookmarks,
                                onPrevious, onNext, onRefresh)
                            ControlModule.SPEECH -> SpeechModuleHint(onSpeech)
                            ControlModule.APPEARANCE -> AppearanceModule(appearance, onAppearance)
                            ControlModule.SETTINGS -> SettingsModule(appearance, onAppearance)
                        }
                    } else {
                        QuickHome(onDirectory, onSwitch, onBookmarks, chapter, onPrevious, onNext, chapterNumber, chapterCount)
                        ControlModuleNavigation(module) { module = it; onExpand() }
                    }
                }
            }
            BrightnessRail(brightness) { brightness = it }
        }
    }
}

@Composable
private fun ControlHeader(title: String, chapter: String, expanded: Boolean, onBack: () -> Unit,
    onExpand: () -> Unit, onSwitch: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        ReaderAction(ReaderIconRegistry.back, "退出阅读", onBack)
        Column(Modifier.weight(1f)) {
            Text(title, style = readerTextStyle(Metrics.readerControlBookTitle), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(chapter, style = readerTextStyle(Metrics.readerControlChapterTitle), color = LocalReaderColors.current.secondaryText,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        ReaderAction(ReaderIconRegistry.more, "阅读更多", {})
        ReaderAction(ReaderIconRegistry.sourceSwitch, "换源", onSwitch ?: {}, enabled = onSwitch != null)
        TextButton(onExpand) { Text(if (expanded) "收起" else "展开") }
    }
}

@Composable
private fun QuickHome(onDirectory: () -> Unit, onSwitch: (() -> Unit)?, onBookmarks: () -> Unit,
    chapter: String, onPrevious: () -> Unit, onNext: () -> Unit,
    chapterNumber: Int = 0, chapterCount: Int = 0) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        QuickAction(ReaderIconRegistry.directory, "目录", onDirectory)
        QuickAction(ReaderIconRegistry.search, "搜索", {})
        QuickAction(ReaderIconRegistry.sourceSwitch, "换源", onSwitch ?: {}, enabled = onSwitch != null)
    }
    Surface(Modifier.fillMaxWidth(), color = LocalReaderColors.current.surface, shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, LocalReaderColors.current.outline)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                ReaderAction(ReaderIconRegistry.previousChapter, "上一章", onPrevious)
                Text(chapter, Modifier.weight(1f), style = readerTextStyle(Metrics.rowLabel), maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
                ReaderAction(ReaderIconRegistry.nextChapter, "下一章", onNext)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                if (chapterNumber > 0 && chapterCount > 0) {
                    Text("第 $chapterNumber / 共 $chapterCount 章", Modifier.weight(1f), style = readerTextStyle(Metrics.rowDetail))
                } else {
                    Spacer(Modifier.weight(1f))
                }
                TextButton(onBookmarks) { Text("加书签") }
            }
            val chapterProgress = if (chapterNumber > 0 && chapterCount > 0) {
                (chapterNumber.toFloat() / chapterCount.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            }
            Slider(value = chapterProgress, onValueChange = {}, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun QuickAction(icon: com.reader.core.theme.ReaderIconMapping, label: String, onClick: () -> Unit,
    enabled: Boolean = true) {
    val colors = LocalReaderColors.current
    Surface(Modifier.fillMaxWidth(.333f).clickable(enabled, onClick = onClick), color = colors.surface,
        shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, colors.outline)) {
        Column(Modifier.padding(vertical = 8.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
            ReaderIcon(icon, label, tint = if (enabled) colors.accent else colors.secondaryText)
            Text(label, color = if (enabled) colors.primaryText else colors.secondaryText,
                style = readerTextStyle(Metrics.rowDetail))
        }
    }
}

@Composable
private fun ControlModuleNavigation(selected: ControlModule, onSelect: (ControlModule) -> Unit) {
    val items = listOf(ControlModule.PAGE to (ReaderIconRegistry.directory to "翻页"),
        ControlModule.SPEECH to (ReaderIconRegistry.ttsHeadphones to "朗读"),
        ControlModule.APPEARANCE to (ReaderIconRegistry.readingAppearance to "界面"),
        ControlModule.SETTINGS to (ReaderIconRegistry.settings to "设置"))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        items.forEach { (key, pair) ->
            val colors = LocalReaderColors.current
            Column(Modifier.clickable(onClick = { onSelect(key) }).padding(horizontal = 8.dp),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                Surface(Modifier.size(34.dp), shape = CircleShape,
                    color = if (selected == key) colors.accentFill else colors.field) {
                    ReaderIcon(pair.first, pair.second, tint = if (selected == key) colors.onAccent else colors.secondaryText)
                }
                Text(pair.second, style = readerTextStyle(Metrics.rowDetail), color = colors.secondaryText)
            }
        }
    }
}

@Composable
private fun FullPageControls(appearance: ReadingAppearance, onAppearance: (ReadingAppearance) -> Unit,
    onDirectory: () -> Unit, onBookmarks: () -> Unit, onPrevious: () -> Unit, onNext: () -> Unit,
    onRefresh: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        OutlinedButton(onPrevious) { Text("上一章") }
        OutlinedButton(onDirectory) { Text("目录") }
        OutlinedButton(onBookmarks) { Text("书签") }
        OutlinedButton(onNext) { Text("下一章") }
        if (onRefresh != null) OutlinedButton(onRefresh) { Text("刷新") }
    }
    ReadingControlStepper("字号", { onAppearance(appearance.copy(font = (appearance.font - 1).coerceAtLeast(12.0))) },
        { onAppearance(appearance.copy(font = (appearance.font + 1).coerceAtMost(36.0))) })
    ReadingControlStepper("行距", { onAppearance(appearance.copy(line = (appearance.line - .1).coerceAtLeast(1.25))) },
        { onAppearance(appearance.copy(line = (appearance.line + .1).coerceAtMost(2.6))) })
    ReadingControlStepper("字距", { onAppearance(appearance.copy(spacing = (appearance.spacing - .02).coerceAtLeast(-.1))) },
        { onAppearance(appearance.copy(spacing = (appearance.spacing + .02).coerceAtMost(.2))) })
}

@Composable
private fun AppearanceModule(appearance: ReadingAppearance, onAppearance: (ReadingAppearance) -> Unit) {
    val colors = LocalReaderColors.current
    Text("主题库", style = readerTextStyle(Metrics.sectionHeading))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ReaderPaperThemes.all.take(4).forEach { paper ->
            Surface(Modifier.weight(1f).height(42.dp).clickable { onAppearance(appearance.copy(paperId = paper.id)) },
                color = Color(paper.swatch.toInt()), shape = RoundedCornerShape(10.dp),
                border = BorderStroke(if (paper.id == appearance.paper.id) 2.dp else 1.dp, colors.outline)) {}
        }
    }
    Text("排版库", style = readerTextStyle(Metrics.sectionHeading))
    ReadingControlStepper("字号", { onAppearance(appearance.copy(font = (appearance.font - 1).coerceAtLeast(12.0))) },
        { onAppearance(appearance.copy(font = (appearance.font + 1).coerceAtMost(36.0))) })
    ReadingControlStepper("行距", { onAppearance(appearance.copy(line = (appearance.line - .1).coerceAtLeast(1.25))) },
        { onAppearance(appearance.copy(line = (appearance.line + .1).coerceAtMost(2.6))) })
}

@Composable
private fun SettingsModule(appearance: ReadingAppearance, onAppearance: (ReadingAppearance) -> Unit) {
    val colors = LocalReaderColors.current
    Text("阅读设置", style = readerTextStyle(Metrics.sectionHeading))
    TextButton({ onAppearance(appearance.copy(scrolling = !appearance.scrolling)) }) {
        Text(if (appearance.scrolling) "当前滚动，切换分页" else "当前分页，切换滚动", color = colors.primaryText)
    }
}

@Composable
private fun SpeechModuleHint(onSpeech: (() -> Unit)?) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("朗读", style = readerTextStyle(Metrics.sectionHeading))
        Button(onClick = onSpeech ?: {}, enabled = onSpeech != null) { Text("打开朗读面板") }
    }
}

@Composable
private fun BrightnessRail(value: Float, onValue: (Float) -> Unit) {
    val colors = LocalReaderColors.current
    Surface(Modifier.width(42.dp), color = colors.panel, shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, colors.outline)) {
        Column(Modifier.padding(vertical = 8.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
            Text("☀", color = colors.accent)
            Slider(value = value, onValueChange = onValue, modifier = Modifier.height(120.dp), valueRange = 0f..1f)
            IconButton(onClick = { onValue(.5f) }) { Text("A", color = colors.accent) }
        }
    }
}

@Composable
private fun ReadingControlStepper(label: String, decrease: () -> Unit, increase: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Metrics.spaceSmall.dp)) {
        Text(label, Modifier.weight(1f), style = readerTextStyle(Metrics.rowDetail))
        TextButton(decrease, Modifier.semantics { contentDescription = "减小$label" }) { Text("−") }
        TextButton(increase, Modifier.semantics { contentDescription = "增大$label" }) { Text("+") }
    }
}
