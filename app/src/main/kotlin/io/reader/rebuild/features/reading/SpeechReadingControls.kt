package io.reader.rebuild.features.reading

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reader.core.protocol.M3TtsChapterRef
import com.reader.core.theme.ReaderIconRegistry
import io.reader.rebuild.core.readerResult
import io.reader.rebuild.design.LocalReaderColors
import io.reader.rebuild.design.ReaderIcon
import kotlinx.coroutines.launch

@Composable
internal fun SpeechReadingControls(
    chapter: ReaderChapter,
    coordinator: SpeechSessionCoordinator,
) {
    val colors = LocalReaderColors.current
    val snapshot by coordinator.snapshot.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var expanded by remember(chapter.ownerId) { mutableStateOf(false) }
    var engine by remember(chapter.ownerId) { mutableStateOf("系统音色") }
    var httpEndpoint by remember(chapter.ownerId) { mutableStateOf("") }
    var editingHttp by remember(chapter.ownerId) { mutableStateOf(false) }
    var httpEnabled by remember(chapter.ownerId) { mutableStateOf(true) }
    val bodyVersion = chapter.positionScope.bodyVersion
    val processingVersion = chapter.positionScope.processingVersion
    val identity = M3TtsChapterRef(chapter.book.sourceId, chapter.book.bookId, chapter.index, chapter.title)

    val active = snapshot
    Surface(Modifier.fillMaxWidth().padding(12.dp), color = colors.panel) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReaderIcon(ReaderIconRegistry.ttsHeadphones, "朗读", size = 22.dp)
                Text("朗读", color = colors.primaryText, modifier = Modifier.weight(1f))
                Text(
                    when (active?.state?.wireValue) {
                        "playing" -> "朗读中"
                        "paused" -> "已暂停"
                        "preparing", "loading" -> "准备中"
                        "stopped" -> "已停止"
                        "failed" -> "失败"
                        else -> "未开始"
                    },
                    color = colors.secondaryText,
                )
                Button(onClick = { scope.launch {
                    if (active == null) readerResult {
                        coordinator.start(SpeechChapter(identity, chapter.text, bodyVersion, processingVersion), chapter.title,
                            chapter.initialScalar, ReaderSpeechEngine.System())
                    } else coordinator.send(if (active.state.wireValue == "playing")
                        io.reader.rebuild.host.media.SpeechAction.Pause else io.reader.rebuild.host.media.SpeechAction.Play)
                } }) {
                    Text(if (active == null) "开始" else if (active.state.wireValue == "playing") "暂停" else "播放")
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { scope.launch { coordinator.send(io.reader.rebuild.host.media.SpeechAction.Previous) } }) { Text("上一段") }
                OutlinedButton(onClick = { scope.launch { coordinator.send(io.reader.rebuild.host.media.SpeechAction.Next) } }) { Text("下一段") }
                OutlinedButton(onClick = { scope.launch { coordinator.send(io.reader.rebuild.host.media.SpeechAction.Stop) } }) { Text("停止") }
                OutlinedButton(onClick = { expanded = !expanded }) { Text(if (expanded) "收起" else "设置") }
            }
            if (expanded) {
                Text("音色与引擎", color = colors.secondaryText)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { engine = "系统音色" }) { Text("系统音色") }
                    OutlinedButton(onClick = { engine = "在线朗读" }) { Text("在线朗读") }
                }
                Text("当前引擎 · $engine", color = colors.accent)
                Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("在线引擎", Modifier.weight(1f))
                    Switch(httpEnabled, { httpEnabled = it })
                    TextButton({ editingHttp = !editingHttp }) { Text(if (editingHttp) "收起" else "编辑") }
                }
                if (editingHttp) {
                    OutlinedTextField(
                        httpEndpoint,
                        { httpEndpoint = it },
                        Modifier.fillMaxWidth(),
                        label = { Text("在线朗读地址") },
                        placeholder = { Text("https://…") },
                        singleLine = true,
                    )
                    Button(onClick = { editingHttp = false }) { Text("保存引擎") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { scope.launch { coordinator.send(io.reader.rebuild.host.media.SpeechAction.Rate(80)) } }) { Text("0.8×") }
                    Button(onClick = { scope.launch { coordinator.send(io.reader.rebuild.host.media.SpeechAction.Rate(100)) } }) { Text("1.0×") }
                    Button(onClick = { scope.launch { coordinator.send(io.reader.rebuild.host.media.SpeechAction.Rate(125)) } }) { Text("1.25×") }
                }
            }
        }
    }
}
