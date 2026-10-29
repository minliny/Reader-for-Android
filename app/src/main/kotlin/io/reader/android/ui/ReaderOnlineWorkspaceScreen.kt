package io.reader.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.reader.android.runtime.ReaderOnlineContentKind

/** Existing public message adapters; no raw JSON, rule parameters or pretend reading action. */
@Composable
internal fun ReaderOnlineWorkspaceScreen(state: OnlineWorkspaceState, owner: ReaderOnlineWorkspace, requestFile: () -> Unit) {
    val revision = state.revision; val enabled = !state.busy && !state.pending && state.sourceDeletion == null
    Column(Modifier.fillMaxSize().testTag("reader_online_workspace")) {
        state.feedback?.let { message -> key(state.feedbackId) { ReaderTransientFeedback(message) { owner.clearFeedback(state.feedbackId) } } }
        state.pendingSources.forEach { original -> ReaderRecoveryNotice(
            if (original.hostCleanupPending && original.confirmation == io.reader.android.runtime.SourceImportConfirmation.APPLIED)
                "原书源操作已确认，会话清理仍未完成。" else "原书源操作尚未确认，原记录会继续保留。", state.busy,
            { owner.reconcileSource(original.id, revision) }, if (original.hostCleanupPending) "继续清理原会话" else "核对原操作", "reader_source_reconcile") }
        state.pendingAdds.forEach { original -> ReaderRecoveryNotice("原入架尚未确认，请核对原操作。", state.busy,
            { owner.reconcileAdd(original.operationId, revision) }, "核对原入架", "reader_online_add_reconcile") }
        state.pendingReads.forEach { original -> ReaderRecoveryNotice("原搜索或详情尚未确认，暂不能开始新的请求。", state.busy,
            { owner.observeRead(original.id, revision) }, "读取原状态", "reader_online_read_observe") }
        if (state.unconfirmedId != null && state.pendingSources.isEmpty() && state.pendingAdds.isEmpty() && state.pendingReads.isEmpty())
            ReaderRecoveryNotice("原操作尚未确认，原意图会继续保留。", state.busy, null)
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth().testTag("reader_online_loading"))
        val retrySearch = state.sessionFailed && !state.pending && state.selection != null && state.query.isNotBlank()
        if (state.error != null || retrySearch) {
            ReaderPageState(
                if (retrySearch) "搜索未完成" else "本次操作未完成",
                state.error ?: "原操作已核对，可以重新执行相同查询。",
                if (retrySearch) "重试搜索" else "读取状态",
                if (retrySearch) ({ owner.retrySearch(revision) }) else ({ owner.refresh(revision) }),
            )
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(19.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (state.page) {
                OnlineWorkspacePage.SOURCES -> {
                    item {
                        Text("书源", style = MaterialTheme.typography.titleLarge)
                        Row {
                            TextButton(requestFile, enabled = enabled, modifier = Modifier.testTag("reader_source_import_file")) { Text("从文件导入书源") }
                            TextButton({ owner.refresh(revision) }, enabled = !state.busy, modifier = Modifier.testTag("reader_sources_refresh")) { Text("重新读取") }
                        }
                    }
                    if (!state.loaded && !state.busy && state.error == null) item { ReaderPageState("书源尚未读取", "请读取已有书源。", "读取", { owner.refresh(revision) }) }
                    if (state.sources.isEmpty() && state.loaded && !state.busy) item { ReaderPageState("还没有书源", "选择书源 JSON 文件，再明确导入其中一个来源后搜索书籍。") }
                    state.sourceDocument?.let { document ->
                        val epoch = state.sourceDocumentEpoch
                        item { Text("文件中的书源 · ${document.sourceCount}", style = MaterialTheme.typography.titleMedium)
                            Text("请选择一个书源导入。", style = MaterialTheme.typography.bodySmall) }
                        itemsIndexed(document.entries, key = { _, entry -> "document:${document.id}:$epoch:${entry.index}" }) { _, entry ->
                            Surface(shape = MaterialTheme.shapes.medium) { Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                Text(entry.name?.takeIf { it.isNotBlank() } ?: "未命名书源", style = MaterialTheme.typography.titleMedium)
                                Text(entry.sourceId, style = MaterialTheme.typography.bodySmall)
                                TextButton({ owner.importDocumentChoice(epoch, entry.index, revision) }, enabled = enabled,
                                    modifier = Modifier.testTag("reader_source_document_choose_${entry.index}")) { Text("导入此书源") }
                            } }
                        }
                    }
                    itemsIndexed(state.sources, key = { _, source -> source.sourceId }) { index, source ->
                        Surface(shape = MaterialTheme.shapes.medium) { Row(Modifier.fillMaxWidth().padding(12.dp)) {
                            Column(Modifier.weight(1f)) { Text(source.name); if (!source.enabled) Text("已停用", style = MaterialTheme.typography.bodySmall) }
                            TextButton({ owner.select(source, revision) }, enabled = enabled && source.enabled,
                                modifier = Modifier.testTag("reader_source_select_$index")) { Text("选择") }
                            TextButton({ owner.setSourceEnabled(source, !source.enabled, revision) }, enabled = enabled,
                                modifier = Modifier.testTag("reader_source_toggle_$index")) { Text(if (source.enabled) "停用" else "启用") }
                            TextButton({ owner.requestSourceDeletion(source, revision) }, enabled = enabled,
                                modifier = Modifier.testTag("reader_source_delete_$index")) { Text("删除") }
                        } }
                    }
                }
                OnlineWorkspacePage.RESULTS -> {
                    item {
                        Text(state.selected?.name.orEmpty(), style = MaterialTheme.typography.titleMedium)
                        OutlinedTextField(state.query, { owner.editQuery(it, revision) }, enabled = enabled,
                            singleLine = true, label = { Text("搜索书名或作者") }, modifier = Modifier.fillMaxWidth().testTag("reader_online_query"))
                        TextButton({ owner.search(revision) }, enabled = enabled && state.query.isNotBlank() && state.selection != null,
                            modifier = Modifier.testTag("reader_online_search")) { Text("搜索") }
                    }
                    if (state.loadedPage && state.books.isEmpty() && !state.busy && !state.pending && state.error == null) item { ReaderPageState("没有找到书籍", "试试其他关键词。") }
                    itemsIndexed(state.books) { index, book -> Surface(shape = MaterialTheme.shapes.medium) {
                        Column(Modifier.fillMaxWidth().padding(12.dp)) {
                            Text(book.title, style = MaterialTheme.typography.titleMedium)
                            book.author?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                            TextButton({ owner.detail(book, revision) }, enabled = enabled && !state.sessionFailed, modifier = Modifier.testTag("reader_online_detail_$index")) { Text("查看详情") }
                        }
                    } }
                    if (state.session != null && state.loadedPage && !state.sessionFailed && !state.ended && state.nextPage <= 20) item {
                        TextButton({ owner.next(revision) }, enabled = enabled, modifier = Modifier.testTag("reader_online_next")) { Text("下一页") }
                    }
                }
                OnlineWorkspacePage.DETAIL -> state.detail?.let { detail -> item {
                    Text(detail.title, style = MaterialTheme.typography.headlineSmall)
                    Text(detail.author, style = MaterialTheme.typography.bodyMedium)
                    detail.intro?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    if (detail.contentKind == ReaderOnlineContentKind.TEXT) {
                        TextButton({ owner.add(revision) }, enabled = enabled && state.added != detail.key,
                            modifier = Modifier.testTag("reader_online_add")) { Text(if (state.added == detail.key) "已加入书架" else "加入书架") }
                    } else ReaderPageState("暂不支持这类书籍入架", "当前入口仅开放在线文字书。")
                    // Reading/TOC buttons stay absent until composite-source reading/progress is implemented.
                } }
            }
        }
    }
    ReaderNativeConfirmation(state.sourceDeletion, owner::confirmSourceDeletion, owner::dismissSourceDeletion)
}
