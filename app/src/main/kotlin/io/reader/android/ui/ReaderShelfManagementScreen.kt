package io.reader.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.reader.android.runtime.ManagementConfirmation
import io.reader.android.runtime.ShelfBookKey

/** Uses the existing message/confirmation adapters. It never owns Core facts or starts a mutation itself. */
@Composable
internal fun ReaderShelfManagementRecovery(state: ShelfManagementUiState, owner: ReaderShelfManagement) {
    val id = state.pending.firstOrNull()?.id ?: state.pendingOriginal ?: return
    val revision = state.recoveryRevision
    ReaderRecoveryNotice("原书架管理操作尚未确认，原意图会继续保留。", state.busy,
        { owner.reconcile(id, revision) }, "核对原操作", "reader_reconcile_shelf_management")
    val original = state.recoveryResult
    if (original != null && original.operationId == id && original.confirmation == ManagementConfirmation.UNKNOWN &&
        original.canContinueOriginal) {
        ReaderRecoveryNotice("已核对，可按原身份和原参数继续同一次操作。", state.busy,
            { owner.reconcile(id, revision, continueOriginal = true) }, "继续原操作", "reader_continue_shelf_management")
    }
}

@Composable
internal fun ReaderShelfManagementScreen(state: ShelfManagementUiState, owner: ReaderShelfManagement) {
    val read = state.read
    val enabled = !state.busy && !state.hasPending
    state.feedback?.let { message -> key(state.feedbackIdentity) {
        ReaderTransientFeedback(message) { owner.clearFeedback(state.feedbackIdentity) }
    } }
    if (read == null) {
        if (!state.busy) ReaderPageState("分组和书架尚未读取", state.error ?: "请读取已有分组和本地文字书架。", "读取", { owner.refresh(state.recoveryRevision) })
        return
    }
    LazyColumn(Modifier.fillMaxSize().testTag("reader_shelf_management_screen"), contentPadding = PaddingValues(19.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("分组 ${read.groups.size} 个 · 本地文字书架 ${read.books.size} 本", style = MaterialTheme.typography.titleMedium)
            Text("修改或删除分组只改变分组定义，书籍已有的归属文字不会自动变化。", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { owner.refresh(read.revision) }, enabled = !state.busy, modifier = Modifier.testTag("reader_management_refresh")) { Text("重新读取") }
            state.error?.let { ReaderPageState("书架管理状态", it, "重新读取", { owner.refresh(read.revision) }) }
        }
        item {
            Text(if (state.editing == null) "新建分组" else "修改分组名称", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(state.name, { owner.editName(it, read.revision) }, enabled = enabled, singleLine = true,
                label = { Text("分组名称") }, modifier = Modifier.fillMaxWidth().testTag("reader_group_name"))
            Row {
                TextButton(onClick = { owner.saveName(read.revision) }, enabled = enabled && state.name.isNotBlank(),
                    modifier = Modifier.testTag("reader_group_save")) { Text(if (state.editing == null) "创建分组" else "保存名称") }
                if (state.editing != null) TextButton(onClick = { owner.editGroup(null, read.revision) }, enabled = enabled) { Text("改为新建") }
            }
        }
        if (read.groups.isEmpty()) item { Text("暂无分组。") }
        items(read.groups, key = { it.groupId }) { group ->
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Text(group.groupName, style = MaterialTheme.typography.titleMedium)
                    Text("顺序 ${group.order} · ${if (group.show) "显示" else "隐藏"}", style = MaterialTheme.typography.bodySmall)
                    Row {
                        TextButton(onClick = { owner.editGroup(group, read.revision) }, enabled = enabled,
                            modifier = Modifier.testTag("reader_group_edit_${group.groupId}")) { Text("改名") }
                        TextButton(onClick = { owner.requestDelete(group, read.revision) }, enabled = enabled,
                            modifier = Modifier.testTag("reader_group_delete_${group.groupId}")) { Text("删除分组") }
                        TextButton(onClick = { owner.assign(group, read.revision) }, enabled = enabled && state.selected.size == 1,
                            modifier = Modifier.testTag("reader_group_assign_${group.groupId}")) { Text("设为所选书归属") }
                    }
                }
            }
        }
        item {
            HorizontalDivider()
            Text("书架管理 · 已选 ${state.selected.size} 本", style = MaterialTheme.typography.titleMedium)
            Text("单选可设置归属；单选或多选可移出书架。移出后会清除所选书籍的阅读位置、正文修改和阅读缓存；原文件会保留。", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { owner.assign(null, read.revision) }, enabled = enabled && state.selected.size == 1,
                modifier = Modifier.testTag("reader_group_clear_assignment")) { Text("清除所选书归属") }
            TextButton(onClick = { owner.requestRemove(read.revision) }, enabled = enabled && state.selected.isNotEmpty(),
                modifier = Modifier.testTag("reader_remove_shelf_selection")) { Text("移出所选书籍") }
        }
        items(read.books, key = { "${it.sourceId}:${it.id}" }) { book ->
            val target = ShelfBookKey(book.sourceId, book.id)
            Row(Modifier.fillMaxWidth().clickable(enabled = enabled) { owner.toggleBook(target, read.revision) }
                .padding(vertical = 8.dp).testTag("reader_management_book_${book.sourceId}_${book.id}"), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(target in state.selected, { owner.toggleBook(target, read.revision) }, enabled = enabled)
                Column(Modifier.weight(1f)) {
                    Text(book.title, style = MaterialTheme.typography.titleMedium)
                    Text(book.group?.let { "归属：$it" } ?: "未设置归属", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    val captured = state.confirmation
    val confirmation = captured?.let { ticket -> when (val intent = ticket.intent) {
        is ShelfManagementIntent.DeleteGroup -> ReaderConfirmationPresentation(ticket.id, "删除分组“${intent.group.groupName}”？",
            "仅删除分组定义。书籍的已有归属不变，不会移出书架。", "删除分组")
        is ShelfManagementIntent.Remove -> ReaderConfirmationPresentation(ticket.id, "将 ${intent.targets.size} 本书移出书架？",
            "移出后会清除所选书籍的阅读位置、正文修改和阅读缓存；原文件会保留。", "移出书架")
        else -> null
    } }
    ReaderNativeConfirmation(confirmation, owner::confirm, owner::dismiss)
}
