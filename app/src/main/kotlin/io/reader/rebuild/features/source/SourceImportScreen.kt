package io.reader.rebuild.features.source

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.reader.core.theme.ReaderDesignMetrics as Metrics
import com.reader.core.theme.ReaderIconRegistry as Icons
import io.reader.rebuild.design.LocalReaderColors
import io.reader.rebuild.design.ReaderConfirmation
import io.reader.rebuild.design.ReaderConfirmationState
import io.reader.rebuild.design.ReaderIcon
import io.reader.rebuild.design.ReaderTaskProgress
import io.reader.rebuild.design.ReaderTaskProgressState
import io.reader.rebuild.design.readerTextStyle
import io.reader.rebuild.host.storage.AuthorizedDocument
import java.io.ByteArrayOutputStream
import java.net.URI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class SourceInput(val label: String) {
    TEXT("文本"), FILE("文件"), URL("网址"),
}

@Composable
internal fun SourceImportScreen(
    coordinator: SourceManagementCoordinator,
    onOpenDocument: ((AuthorizedDocument) -> Unit) -> Unit,
    onBack: () -> Unit,
    onConfirmationVisibility: (Boolean) -> Unit,
) {
    val colors = LocalReaderColors.current
    val scope = rememberCoroutineScope()
    val batch by coordinator.importBatch.collectAsState()
    val unresolved by coordinator.unresolvedOperation.collectAsState()
    var mode by remember { mutableStateOf(SourceInput.TEXT) }
    var document by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var file by remember { mutableStateOf<AuthorizedDocument?>(null) }
    var selection by remember { mutableStateOf<Set<String>>(emptySet()) }
    var preview by remember { mutableStateOf<List<SourceImportPreview>?>(null) }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmationGeneration by remember { mutableLongStateOf(0L) }
    var confirm by remember { mutableStateOf<ReaderConfirmationState?>(null) }
    var submitted by remember { mutableStateOf(false) }
    val showingResults = submitted || batch.running || batch.pendingOperationId != null
    val current = preview
    SourceConfirmationVisibility(confirm != null, onConfirmationVisibility)

    fun prepare() {
        if (submitting) return
        submitting = true
        error = null
        scope.launch {
            try {
                val candidates = when (mode) {
                    SourceInput.TEXT -> coordinator.previewImport(document)
                    SourceInput.FILE -> coordinator.previewImport(requireNotNull(file).readRuleDocument())
                    SourceInput.URL -> coordinator.previewImportUrl(url.trim())
                }
                selection = candidates.filter { it.existing == null }.map { requireNotNull(it.input.sourceId) }.toSet()
                preview = candidates
            } catch (failure: CancellationException) {
                throw failure
            } catch (_: Exception) {
                error = "无法预览书源。请检查规则 JSON 或来源地址。"
            } finally {
                submitting = false
            }
        }
    }

    SourcePage("导入书源", onBack) {
        LazyColumn(
            Modifier.sourcePageWidth().padding(Metrics.screenInset.dp).testTag("source.import"),
            verticalArrangement = Arrangement.spacedBy(Metrics.spaceSmall.dp),
        ) {
            item { SourceImportSteps(if (showingResults) 3 else if (current == null) 1 else 2) }
            error?.let { message -> item {
                Text(message, style = readerTextStyle(Metrics.rowDetail), color = colors.danger,
                    modifier = Modifier.testTag("source.import.error"))
            } }
            if (showingResults) item {
                val pendingItem = batch.running || batch.pendingOperationId != null
                val active = if (pendingItem && batch.completed + batch.failed < batch.total) 1 else 0
                val pending = batch.total - batch.completed - batch.failed - active
                ReaderTaskProgress(ReaderTaskProgressState(
                    if (batch.running) "正在导入书源" else if (batch.pendingOperationId != null) "等待核对导入" else "导入结果",
                    batch.completed, batch.failed, active, pending,
                ))
                SourceTextAction("再次导入", { submitted = false }, !batch.running && batch.pendingOperationId == null)
            }
            if (showingResults) return@LazyColumn
            if (current == null) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(Metrics.spaceExtraSmall.dp)) {
                        SourceInput.entries.forEach { choice ->
                            FilterChip(selected = mode == choice, onClick = { mode = choice; error = null },
                                enabled = !submitting && !batch.running,
                                label = { Text(choice.label, style = readerTextStyle(Metrics.sourceFilterFieldText)) })
                        }
                    }
                }
                item {
                    when (mode) {
                        SourceInput.TEXT -> OutlinedTextField(
                            document, { document = it }, Modifier.fillMaxWidth().testTag("source.import.text")
                                .heightIn(min = Metrics.sourceImportPasteHeight.dp),
                            label = { Text("书源 JSON", style = readerTextStyle(Metrics.sourceFilterFieldText)) },
                            textStyle = readerTextStyle(Metrics.sourceFilterFieldText), minLines = 5, maxLines = 14,
                            enabled = !submitting && !batch.running,
                            shape = RoundedCornerShape(Metrics.inputRadius.dp),
                        )
                        SourceInput.FILE -> Button(
                            onClick = { onOpenDocument { file = it } }, enabled = !submitting && !batch.running,
                            modifier = Modifier.testTag("source.import.file"),
                        ) {
                            ReaderIcon(Icons.openDocument, null, size = Metrics.sourceLoginActionIconSize.dp, tint = colors.onAccent)
                            Text(file?.name ?: "选取文件", Modifier.padding(start = Metrics.spaceExtraSmall.dp),
                                style = readerTextStyle(Metrics.rowLabel), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        SourceInput.URL -> OutlinedTextField(
                            url, { url = it }, Modifier.fillMaxWidth().testTag("source.import.url"),
                            label = { Text("规则文件网址", style = readerTextStyle(Metrics.sourceFilterFieldText)) },
                            textStyle = readerTextStyle(Metrics.sourceFilterFieldText),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            singleLine = true, enabled = !submitting && !batch.running,
                            shape = RoundedCornerShape(Metrics.inputRadius.dp),
                        )
                    }
                }
                item {
                    val ready = when (mode) {
                        SourceInput.TEXT -> document.isNotBlank()
                        SourceInput.FILE -> file != null
                        SourceInput.URL -> validSourceDocumentUrl(url)
                    }
                    Button(onClick = ::prepare, enabled = ready && !submitting && !batch.running && unresolved == null,
                        modifier = Modifier.testTag("source.import.preview")) {
                        Text(if (submitting) "正在读取" else "预览书源", style = readerTextStyle(Metrics.rowLabel))
                    }
                }
            } else {
                item {
                    Text("可导入 ${current.count { it.existing == null }} 条 · 已存在 ${current.count { it.existing != null }} 条",
                        style = readerTextStyle(Metrics.rowDetail), color = colors.secondaryText)
                }
                items(current, key = { requireNotNull(it.input.sourceId) }) { entry ->
                    val id = requireNotNull(entry.input.sourceId)
                    val input = entry.input.bookSource
                    SourceCard {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Metrics.rowBlockSpacing.dp)) {
                                Text(input?.sourceText("bookSourceName") ?: id, style = readerTextStyle(Metrics.sourceRowTitle),
                                    color = colors.primaryText, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(input?.sourceText("bookSourceGroup")?.takeIf { it.isNotBlank() } ?: id,
                                    style = readerTextStyle(Metrics.sourceStatusText), color = colors.secondaryText,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (entry.existing != null) SourceStatusBadge("已存在", colors.secondaryText)
                            }
                            Checkbox(id in selection, { checked ->
                                selection = if (checked) selection + id else selection - id
                            }, enabled = entry.existing == null, modifier = Modifier.testTag("source.import.select.$id"))
                        }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(Metrics.spaceExtraSmall.dp)) {
                        SourceTextAction("返回输入", { preview = null })
                        Button(
                            onClick = {
                                confirm = ReaderConfirmationState("确认导入书源？",
                                    "将导入 ${selection.size} 条书源，并保留文档中的分组。", "确认导入", ++confirmationGeneration)
                            },
                            enabled = selection.isNotEmpty() && !batch.running && unresolved == null,
                            modifier = Modifier.testTag("source.import.confirm"),
                        ) { Text("确认导入", style = readerTextStyle(Metrics.rowLabel)) }
                    }
                }
            }
        }
    }
    ReaderConfirmation(confirm, ownerForeground = true, onDismiss = { confirm = null }, onConfirm = {
        val selected = current.orEmpty().filter { it.input.sourceId in selection && it.existing == null }
        if (selected.isNotEmpty() && coordinator.beginImport(selected) != null) {
            submitted = true
            preview = null
            selection = emptySet()
            document = ""
            file = null
            url = ""
        }
        confirm = null
    })
}

@Composable
private fun SourceImportSteps(step: Int) {
    val colors = LocalReaderColors.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Metrics.spaceExtraSmall.dp)) {
        listOf("输入", "预览", "导入").forEachIndexed { index, title ->
            val current = step == index + 1
            val foreground = if (current) colors.accent else colors.secondaryText
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(Metrics.rowBlockSpacing.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(Metrics.sourceImportStepDiameter.dp)
                    .background(if (current) colors.accentTint else colors.mutedFill, CircleShape),
                    contentAlignment = Alignment.Center) {
                    Text("${index + 1}", style = readerTextStyle(Metrics.sourceTransferBadgeText), color = foreground)
                }
                Text(title, style = readerTextStyle(Metrics.sourceFilterFieldText), color = foreground, maxLines = 1)
            }
        }
    }
}

internal fun validSourceDocumentUrl(text: String): Boolean = runCatching {
    val uri = URI(text.trim())
    uri.scheme?.lowercase() in setOf("http", "https") && !uri.host.isNullOrBlank() && uri.userInfo == null
}.getOrDefault(false)

private suspend fun AuthorizedDocument.readRuleDocument(): String = withContext(Dispatchers.IO) {
    val cap = 8 * 1024 * 1024
    val bytes = open().use { input ->
        ByteArrayOutputStream().use { output ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(count <= cap - output.size()) { "Source document exceeds the size limit" }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
    }
    bytes.decodeToString(throwOnInvalidSequence = true).removePrefix("\uFEFF")
}
