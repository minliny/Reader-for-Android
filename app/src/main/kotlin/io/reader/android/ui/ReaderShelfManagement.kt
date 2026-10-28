package io.reader.android.ui

import io.reader.android.runtime.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.Collections

internal data class ShelfManagementRead(val revision: String, val groups: List<ShelfGroup>, val books: List<LibraryBook>)
internal sealed interface ShelfManagementIntent {
    data class Create(val name: String) : ShelfManagementIntent
    data class Rename(val group: ShelfGroup, val name: String) : ShelfManagementIntent
    data class DeleteGroup(val group: ShelfGroup) : ShelfManagementIntent
    data class Assign(val book: ShelfBookKey, val group: ShelfGroup?) : ShelfManagementIntent
    data class Remove(val targets: List<ShelfBookKey>) : ShelfManagementIntent
}
internal data class ShelfManagementTicket(
    val id: String, val owner: String, val revision: String, val intent: ShelfManagementIntent,
)
internal object ShelfManagementUiPolicy {
    fun removal(targets: List<ShelfBookKey>) = ShelfManagementIntent.Remove(Collections.unmodifiableList(targets.map { it.copy() }))
    fun qualifies(ticket: ShelfManagementTicket, owner: String, current: ShelfManagementRead?): Boolean {
        val read = current ?: return false
        if (ticket.owner != owner || ticket.revision != read.revision) return false
        fun hasBook(key: ShelfBookKey) = read.books.any { it.sourceId == key.sourceId && it.id == key.bookId }
        return when (val intent = ticket.intent) {
            is ShelfManagementIntent.Create -> intent.name.isNotBlank()
            is ShelfManagementIntent.Rename -> intent.group in read.groups && intent.name.isNotBlank()
            is ShelfManagementIntent.DeleteGroup -> intent.group in read.groups
            is ShelfManagementIntent.Assign -> hasBook(intent.book) && (intent.group == null || intent.group in read.groups)
            is ShelfManagementIntent.Remove -> intent.targets.isNotEmpty() && intent.targets.all(::hasBook)
        }
    }
}
internal data class ShelfManagementUiState(
    val read: ShelfManagementRead? = null,
    val busy: Boolean = false,
    val name: String = "",
    val editing: ShelfGroup? = null,
    val selected: List<ShelfBookKey> = emptyList(),
    val confirmation: ShelfManagementTicket? = null,
    val pending: List<PendingManagement> = emptyList(),
    val pendingOriginal: String? = null,
    val recoveryRevision: String = UUID.randomUUID().toString(),
    val recoveryResult: ManagementResult? = null,
    val error: String? = null,
    val feedback: String? = null,
    val feedbackIdentity: String = UUID.randomUUID().toString(),
) {
    val hasPending get() = pending.isNotEmpty() || pendingOriginal != null
}

/** UI state only. Native calls and original facts remain in the existing Library/App-owner service. */
internal class ReaderShelfManagement(
    private val scope: CoroutineScope,
    private val library: () -> ReaderLibrary,
    private val visible: () -> Boolean,
    private val onVmLane: suspend (suspend () -> Unit) -> Unit,
    private val mainBusy: (Boolean) -> Unit,
    private val retainProtocol: (Throwable) -> Boolean,
) {
    private val owner = UUID.randomUUID().toString()
    private var active = true
    private val mutable = MutableStateFlow(ShelfManagementUiState())
    val state = mutable.asStateFlow()
    private fun usable(revision: String): Boolean = active && visible() && !state.value.busy && state.value.read?.revision == revision
    private fun writable(revision: String) = usable(revision) && !state.value.hasPending
    fun editName(value: String, revision: String) { if (writable(revision)) mutable.update { it.copy(name = value) } }
    fun editGroup(group: ShelfGroup?, revision: String) {
        if (writable(revision) && (group == null || group in state.value.read!!.groups)) mutable.update {
            it.copy(editing = group, name = group?.groupName.orEmpty(), confirmation = null)
        }
    }
    fun toggleBook(key: ShelfBookKey, revision: String) {
        if (!writable(revision) || state.value.read!!.books.none { it.sourceId == key.sourceId && it.id == key.bookId }) return
        mutable.update { it.copy(selected = if (key in it.selected) it.selected.filterNot { old -> old == key } else it.selected + key,
            confirmation = null) }
    }
    fun open() { if (active && visible() && !state.value.busy) run(action = { load() }) }
    fun refresh(expectedPresentation: String) {
        val current = state.value
        if ((current.read?.revision ?: current.recoveryRevision) == expectedPresentation) open()
    }
    fun saveName(revision: String) {
        if (!writable(revision)) return
        val s = state.value
        if (s.name.isBlank()) return
        val intent = s.editing?.let { ShelfManagementIntent.Rename(it, s.name) } ?: ShelfManagementIntent.Create(s.name)
        submit(ticket(revision, intent))
    }
    fun assign(group: ShelfGroup?, revision: String) {
        if (!writable(revision) || state.value.selected.size != 1) return
        submit(ticket(revision, ShelfManagementIntent.Assign(state.value.selected.single(), group)))
    }
    fun requestDelete(group: ShelfGroup, revision: String) {
        if (!writable(revision)) return
        offer(ticket(revision, ShelfManagementIntent.DeleteGroup(group)))
    }
    fun requestRemove(revision: String) {
        if (!writable(revision)) return
        // Exact ordered composite keys: no pre-deduplication, loops over individual removals, or current-row lookup later.
        offer(ticket(revision, ShelfManagementUiPolicy.removal(state.value.selected)))
    }
    fun dismiss(id: String) { if (active && state.value.confirmation?.id == id) mutable.update { it.copy(confirmation = null) } }
    fun confirm(id: String) {
        val captured = state.value.confirmation?.takeIf { it.id == id } ?: return
        if (!writable(captured.revision) || !ShelfManagementUiPolicy.qualifies(captured, owner, state.value.read)) return
        mutable.update { it.copy(confirmation = null) }
        submit(captured)
    }
    fun reconcile(id: String, revision: String, continueOriginal: Boolean = false) {
        val s = state.value
        if (!active || !visible() || s.busy || revision != s.recoveryRevision || (s.pending.none { it.id == id } && s.pendingOriginal != id)) return
        if (continueOriginal && (s.recoveryResult?.operationId != id || s.recoveryResult?.canContinueOriginal != true)) return
        run(recoveryRevision = revision) {
            val result = library().reconcileShelfManagement(id, continueOriginal)
            mutable.update { it.copy(recoveryResult = result, pendingOriginal = if (result.confirmation == ManagementConfirmation.UNKNOWN) id else null) }
            if (result.confirmation == ManagementConfirmation.UNKNOWN) loadPending()
            else {
                mutable.update { it.copy(feedback = if (result.confirmation == ManagementConfirmation.APPLIED)
                    "原书架管理操作已确认。" else "原书架管理操作未应用。", feedbackIdentity = UUID.randomUUID().toString()) }
                load()
            }
        }
    }
    private fun ticket(revision: String, intent: ShelfManagementIntent) = ShelfManagementTicket(UUID.randomUUID().toString(), owner, revision, intent)
    private fun offer(ticket: ShelfManagementTicket) {
        if (ShelfManagementUiPolicy.qualifies(ticket, owner, state.value.read)) mutable.update { it.copy(confirmation = ticket) }
    }
    private fun submit(ticket: ShelfManagementTicket) {
        if (!writable(ticket.revision) || !ShelfManagementUiPolicy.qualifies(ticket, owner, state.value.read)) return
        run(ticket) {
            val message = when (val intent = ticket.intent) {
                is ShelfManagementIntent.Create -> { library().createShelfGroup(intent.name); "分组已创建。" }
                is ShelfManagementIntent.Rename -> { library().renameShelfGroup(intent.group.groupId, intent.name); "分组名称已更新，书籍已有归属未改变。" }
                is ShelfManagementIntent.DeleteGroup -> {
                    val result = library().deleteShelfGroup(intent.group.groupId)
                    if (result.deleted) "分组定义已删除，书籍已有归属未改变。" else "分组已不存在。"
                }
                is ShelfManagementIntent.Assign -> {
                    library().assignShelfGroup(intent.book, intent.group?.groupName.orEmpty())
                    if (intent.group == null) "所选书籍的分组归属已清除。" else "所选书籍的分组归属已更新。"
                }
                is ShelfManagementIntent.Remove -> {
                    val result = library().removeShelfBooks(intent.targets) // N >= 1 always uses one original Core batch.
                    if (result.confirmation == ManagementConfirmation.UNKNOWN) throw PendingOperationException(result.operationId, result.method)
                    if (result.confirmation == ManagementConfirmation.REJECTED) throw CoreCommandException(result.method,
                        org.json.JSONObject(result.rejection!!.originalErrorJson))
                    val batch = requireNotNull(result.batchRemoval)
                    if (batch.removedTargets.isEmpty()) "所选目标原已不在书架。" else
                        "${batch.removedTargets.size} 本书已移出书架。所选书籍的阅读位置、正文修改和阅读缓存已清除；原文件已保留。"
                }
            }
            // Return from a service mutation means its original apply/query receipt was durably qualified.
            mutable.update { it.copy(feedback = message, feedbackIdentity = UUID.randomUUID().toString(), editing = null, name = "", selected = emptyList()) }
            load() // Read failure cannot redispatch the already confirmed mutation.
        }
    }
    private suspend fun loadPending() {
        val pending = library().pendingShelfManagement()
        mutable.update { it.copy(pending = pending, recoveryRevision = UUID.randomUUID().toString()) }
    }
    private suspend fun load() {
        // Retire the old presentation before any read; a failed refresh cannot reactivate an old row callback.
        mutable.update { it.copy(read = null, confirmation = null, selected = emptyList(), editing = null, name = "") }
        loadPending()
        val groups = library().shelfGroups()
        val books = library().books() // Existing admitted local text scope, not old Android fixtures.
        check(books.map { ShelfBookKey(it.sourceId, it.id) }.distinct().size == books.size) { "DUPLICATE_CORE_SHELF_KEY" }
        mutable.update { it.copy(read = ShelfManagementRead(UUID.randomUUID().toString(), groups.toList(), books.toList()),
            confirmation = null, selected = emptyList(), editing = null, name = "", error = null) }
    }
    private fun run(ticket: ShelfManagementTicket? = null, recoveryRevision: String? = null, action: suspend () -> Unit) {
        if (!active || state.value.busy) return
        mutable.update { it.copy(busy = true, error = null, feedback = null) }; mainBusy(true)
        scope.launch {
            try {
                onVmLane {
                    if (!active || (ticket != null && (!visible() || !ShelfManagementUiPolicy.qualifies(ticket, owner, state.value.read)))) return@onVmLane
                    if (recoveryRevision != null && (!visible() || state.value.recoveryRevision != recoveryRevision)) return@onVmLane
                    action()
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                val protocol = retainProtocol(error)
                val pendingId = (error as? PendingOperationException)?.operationId
                if (pendingId != null) mutable.update { it.copy(pendingOriginal = pendingId) }
                try { onVmLane { loadPending() } }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { /* Original ID and last known list remain; never manufacture a receipt. */ }
                val knownRejection = error is CoreCommandException && error.method in setOf("book-group.create", "book-group.update",
                    "book-group.delete", "bookshelf.group.assign", "bookshelf.remove", "bookshelf.removeBatch")
                mutable.update { it.copy(error = when {
                    protocol || pendingId != null -> null
                    knownRejection -> "原操作已确认未应用。可调整后再次提交。"
                    it.feedback != null -> "原操作已确认，但列表刷新失败。请重试读取列表。"
                    else -> "书架管理未完成，请重试读取或核对原操作。"
                }) }
            } finally {
                mutable.update { it.copy(busy = false) }; mainBusy(false)
            }
        }
    }
    fun clearFeedback(identity: String) = mutable.update { if (it.feedbackIdentity == identity) it.copy(feedback = null) else it }
    fun retirePresentation() { mutable.update { it.copy(read = null, confirmation = null, selected = emptyList(),
        recoveryRevision = UUID.randomUUID().toString()) } }
    fun close() { active = false; retirePresentation() }
}
