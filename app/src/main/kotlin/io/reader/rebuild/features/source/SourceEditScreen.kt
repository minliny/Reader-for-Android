package io.reader.rebuild.features.source

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.reader.core.protocol.M2RulesMutationReceiptRejected
import com.reader.core.protocol.M2RulesMutationReceiptSourceImport
import com.reader.core.protocol.M2SourceImportParams
import com.reader.core.protocol.M2SourceListEntry
import com.reader.core.protocol.ReaderProtocolJson
import com.reader.core.theme.ReaderDesignMetrics as Metrics
import io.reader.rebuild.design.LocalReaderColors
import io.reader.rebuild.design.readerTextStyle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private val formattedSourceRules = Json(from = ReaderProtocolJson) { prettyPrint = true }

@Composable
internal fun SourceEditScreen(
    source: M2SourceListEntry,
    coordinator: SourceManagementCoordinator,
    onBack: () -> Unit,
) {
    val colors = LocalReaderColors.current
    val scope = rememberCoroutineScope()
    val unresolved by coordinator.unresolvedOperation.collectAsState()
    var name by remember(source.sourceId) { mutableStateOf(source.name) }
    var draft by remember(source.sourceId) {
        mutableStateOf(source.bookSource?.let { formattedSourceRules.encodeToString(JsonObject.serializer(), it) }.orEmpty())
    }
    var saving by remember { mutableStateOf(false) }
    var issue by remember { mutableStateOf<String?>(null) }

    fun save() {
        val version = source.sourceVersion ?: return
        if (saving || unresolved != null) return
        val title = name.trim()
        val raw = draft
        saving = true
        issue = null
        scope.launch {
            try {
                val rules = withContext(Dispatchers.Default) {
                    if (raw.length <= 8 * 1024 * 1024) {
                        runCatching { ReaderProtocolJson.parseToJsonElement(raw) as? JsonObject }.getOrNull()
                    } else null
                }
                if (title.isEmpty() || rules == null || rules["bookSourceUrl"] != source.bookSource?.get("bookSourceUrl")) {
                    issue = "请检查书源名称、JSON 格式和原书源地址。"
                    return@launch
                }
                val updated = M2SourceImportParams(sourceId = source.sourceId, baseUrl = source.baseUrl,
                    bookSource = JsonObject(rules + ("bookSourceName" to JsonPrimitive(title))))
                when (val result = coordinator.edit(updated, version)) {
                    is M2RulesMutationReceiptSourceImport -> onBack()
                    is M2RulesMutationReceiptRejected -> {
                        issue = if (result.data.error.details?.reason == "sourceVersionConflict") {
                            "书源已被其他操作更新，当前草稿已保留，可复制后重新读取。"
                        } else "未能保存此次修改，已保留草稿。"
                    }
                    else -> issue = "无法核对此次保存结果，已保留草稿。"
                }
            } catch (failure: CancellationException) {
                throw failure
            } catch (_: Exception) {
                issue = "无法核对此次保存结果，已保留草稿。"
            } finally {
                saving = false
            }
        }
    }

    SourcePage("编辑书源", onBack) {
        Column(Modifier.sourcePageWidth().verticalScroll(rememberScrollState()).padding(Metrics.screenInset.dp),
            verticalArrangement = Arrangement.spacedBy(Metrics.spaceSmall.dp)) {
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth().testTag("source.edit.name"),
                singleLine = true, textStyle = readerTextStyle(Metrics.sourceRowTitle),
                label = { Text("书源名称", style = readerTextStyle(Metrics.rowDetail)) })
            Text(source.baseUrl, color = colors.secondaryText, style = readerTextStyle(Metrics.sourceStatusText))
            OutlinedTextField(draft, { draft = it },
                Modifier.fillMaxWidth().heightIn(min = Metrics.sourceImportPasteHeight.dp).testTag("source.edit.rules"),
                minLines = 6, maxLines = 14, textStyle = readerTextStyle(Metrics.sourceFilterFieldText),
                label = { Text("书源规则 JSON", style = readerTextStyle(Metrics.rowDetail)) })
            issue?.let {
                Text(it, Modifier.testTag("source.edit.conflict"), color = colors.danger,
                    style = readerTextStyle(Metrics.rowDetail))
            }
            if (source.sourceVersion == null) {
                Text("该书源没有可核对的编辑版本。", color = colors.secondaryText, style = readerTextStyle(Metrics.rowDetail))
            }
            Button(onClick = ::save, modifier = Modifier.testTag("source.edit.save"),
                enabled = source.sourceVersion != null && !saving && unresolved == null && name.isNotBlank()) {
                Text(if (saving) "保存中" else "保存", style = readerTextStyle(Metrics.rowLabel))
            }
        }
    }
}
