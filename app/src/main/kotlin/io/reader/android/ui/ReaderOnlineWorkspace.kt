package io.reader.android.ui

import android.net.Uri
import io.reader.android.runtime.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

internal enum class OnlineWorkspacePage { SOURCES, RESULTS, DETAIL }
internal data class OnlineUiTicket(val owner: String, val host: Long, val query: Long, val revision: String)
internal object OnlineWorkspaceUiPolicy {
    fun current(ticket: OnlineUiTicket, owner: String, host: Long, query: Long, revision: String) =
        ticket.owner == owner && ticket.host == host && ticket.query == query && ticket.revision == revision

    fun searchRetry(state: OnlineWorkspaceState, revision: String): Pair<ReaderOnlineSourceSelection, String>? {
        if (state.page != OnlineWorkspacePage.RESULTS || state.revision != revision || state.busy || state.pending ||
            state.sourceDeletion != null || (!state.sessionFailed && !state.searchStopped) || state.query.isBlank()) return null
        val selected = state.selected ?: return null
        val selection = state.selection ?: return null
        if (!selected.enabled || selection.sourceId != selected.sourceId || selection.sourceVersion != selected.sourceVersion) return null
        return selection to state.query
    }

    fun stopSearch(state: OnlineWorkspaceState, revision: String): OnlineWorkspaceState? {
        if (state.page != OnlineWorkspacePage.RESULTS || state.revision != revision || !state.busy || !state.searchInFlight || state.searchStopped) return null
        return state.copy(searchStopped = true, books = emptyList(), loadedPage = false)
    }

    fun publishesSearchPage(state: OnlineWorkspaceState, session: ReaderOnlineSearchSession) =
        !state.searchStopped && state.session === session
}
internal data class OnlineWorkspaceState(
    val revision: String = UUID.randomUUID().toString(), val page: OnlineWorkspacePage = OnlineWorkspacePage.SOURCES,
    val sources: List<ReaderSourceEntry> = emptyList(), val loaded: Boolean = false, val selected: ReaderSourceEntry? = null,
    val selection: ReaderOnlineSourceSelection? = null, val session: ReaderOnlineSearchSession? = null,
    val query: String = "", val books: List<ReaderOnlineBook> = emptyList(), val nextPage: Int = 1, val ended: Boolean = false, val loadedPage: Boolean = false, val sessionFailed: Boolean = false,
    val searchInFlight: Boolean = false, val searchStopped: Boolean = false,
    val detail: ReaderOnlineDetailPreview? = null, val added: ShelfBookKey? = null, val busy: Boolean = false,
    val pendingSources: List<PendingSourceImport> = emptyList(), val pendingAdds: List<ReaderOnlineAddPending> = emptyList(),
    val pendingReads: List<ReaderOnlineOriginalObservation> = emptyList(), val unconfirmedId: String? = null,
    val sourceDocument: ReaderSourceDocumentPreview? = null, val sourceDocumentEpoch: Long = 0,
    val sourceDocumentSelection: ReaderSourceJsonInput? = null,
    val sourceDeletion: ReaderConfirmationPresentation? = null,
    val error: String? = null, val feedback: String? = null, val feedbackId: String = UUID.randomUUID().toString(),
) { val pending get() = pendingSources.isNotEmpty() || pendingAdds.isNotEmpty() || pendingReads.isNotEmpty() || unconfirmedId != null }

/** UI projection only. Every business command uses the existing Library/client and VM lane. */
internal class ReaderOnlineWorkspace(
    private val scope: CoroutineScope, private val library: () -> ReaderLibrary,
    private val visible: () -> Boolean, private val onVmLane: suspend (suspend () -> Unit) -> Unit,
    private val cleanupLane: suspend (suspend () -> Unit) -> Unit,
    private val mainBusy: (Boolean) -> Unit, private val retainProtocol: (Throwable) -> Boolean,
    private val reloadShelf: suspend () -> Unit,
) {
    private val owner = UUID.randomUUID().toString(); private var host = 0L; private var queryGeneration = 0L
    private data class SourceDeletion(val id: String, val source: ReaderSourceEntry, val ticket: OnlineUiTicket)
    private var sourceDeletion: SourceDeletion? = null
    private var active = true; private var picker: Pair<String, OnlineUiTicket>? = null
    private data class DocumentStage(val document: ReaderSourceDocumentPreview, val epoch: Long, val ticket: OnlineUiTicket)
    private var documentEpoch = 0L; private var documentStage: DocumentStage? = null
    private val mutable = MutableStateFlow(OnlineWorkspaceState()); val state = mutable.asStateFlow()
    private fun ticket() = OnlineUiTicket(owner, host, queryGeneration, state.value.revision)
    private fun qualifies(t: OnlineUiTicket) = active && visible() && OnlineWorkspaceUiPolicy.current(t, owner, host, queryGeneration, state.value.revision)
    private fun offered(revision: String) = active && visible() && !state.value.busy && state.value.sourceDeletion == null && revision == state.value.revision
    private fun replace(next: OnlineWorkspaceState) { mutable.value = next.copy(revision = UUID.randomUUID().toString()) }
    fun open() { if (!active || !visible() || state.value.busy) return; host++; sourceDeletion = null; clearDocument(); replace(OnlineWorkspaceState()); refresh(state.value.revision) }
    fun refresh(revision: String) { if (offered(revision)) { clearDocument(); run(ticket()) { t ->
        loadPending(t)
        val rows = library().sources()
        if (qualifies(t)) replace(state.value.copy(sources = rows, loaded = true, error = null))
    } } }
    fun pickFile(revision: String): String? {
        if (!offered(revision) || state.value.pending || picker != null) return null
        clearDocument() // A new file request retires only the previous preview/choice, never a business original.
        return UUID.randomUUID().toString().also { picker = it to ticket() }
    }
    fun fileResult(token: String, uri: Uri?) {
        val original = picker?.takeIf { it.first == token } ?: return
        picker = null
        if (uri == null || !qualifies(original.second) || state.value.pending) return
        run(original.second) { t ->
            val document = library().previewSourceDocument(uri) // No source mutation on file result.
            if (qualifies(t)) {
                check(documentEpoch < Long.MAX_VALUE) { "SOURCE_DOCUMENT_EPOCH_EXHAUSTED" }
                val epoch = ++documentEpoch
                replace(state.value.copy(sourceDocument = document, sourceDocumentEpoch = epoch, sourceDocumentSelection = null,
                    page = OnlineWorkspacePage.SOURCES, error = null))
                documentStage = DocumentStage(document, epoch, ticket())
            }
        }
    }
    fun importDocumentChoice(epoch: Long, index: Int, revision: String) {
        if (!offered(revision) || state.value.pending) return
        val original = documentStage ?: return
        if (original.epoch != epoch || state.value.sourceDocumentEpoch != epoch || state.value.sourceDocument !== original.document ||
            !qualifies(original.ticket) || index !in original.document.entries.indices) return
        val selected = original.document.select(index)
        run(original.ticket) { t ->
            if (documentStage !== original || !qualifies(t) || state.value.sourceDocument !== original.document || state.value.sourceDocumentEpoch != epoch) return@run
            documentStage = null // This explicit choice can be admitted only once. It does not alter a durable original.
            mutable.update { it.copy(sourceDocument = null, sourceDocumentSelection = selected) }
            val result = library().importSourceDocumentSelection(original.document, index)
            if (qualifies(t)) {
                if (!result.durable || result.confirmation == SourceImportConfirmation.UNKNOWN) mutable.update { it.copy(unconfirmedId = result.operationId) }
                else if (result.confirmation == SourceImportConfirmation.APPLIED) feedback("书源已导入。")
                else feedback("原导入已确认未应用。")
            }
            loadPending(t)
            val rows = library().sources(); if (qualifies(t)) replace(state.value.copy(sources = rows, loaded = true))
        }
    }
    private fun clearDocument() {
        documentStage = null
        mutable.update { it.copy(sourceDocument = null, sourceDocumentEpoch = 0, sourceDocumentSelection = null) }
    }
    fun setSourceEnabled(source: ReaderSourceEntry, enabled: Boolean, revision: String) {
        if (!offered(revision) || state.value.pending || state.value.page != OnlineWorkspacePage.SOURCES ||
            state.value.sources.none { it === source } || source.enabled == enabled) return
        run(ticket()) { t ->
            val result = library().setSourceEnabled(source.sourceId, source.sourceVersion, enabled)
            finishSourceManagement(t, result, if (enabled) "启用" else "停用")
        }
    }
    fun requestSourceDeletion(source: ReaderSourceEntry, revision: String) {
        if (!offered(revision) || state.value.pending || state.value.page != OnlineWorkspacePage.SOURCES || state.value.sources.none { it === source }) return
        val id = UUID.randomUUID().toString(); sourceDeletion = SourceDeletion(id, source, ticket())
        mutable.update { it.copy(sourceDeletion = ReaderConfirmationPresentation(id, "删除书源“${source.name}”？",
            "将删除该书源定义及其在线搜索元数据，并清理该书源的 HTTP 会话。已有书架条目、阅读位置和本地原件保留。", "删除书源")) }
    }
    fun dismissSourceDeletion(id: String) {
        if (sourceDeletion?.id != id) return
        sourceDeletion = null; mutable.update { if (it.sourceDeletion?.identity == id) it.copy(sourceDeletion = null) else it }
    }
    fun confirmSourceDeletion(id: String) {
        val original = sourceDeletion?.takeIf { it.id == id } ?: return
        if (!qualifies(original.ticket) || state.value.busy || state.value.pending || state.value.sources.none { it === original.source }) return
        dismissSourceDeletion(id) // Retire this exact presentation before queueing its one original mutation.
        run(original.ticket) { t ->
            if (state.value.sources.none { it === original.source }) return@run
            val result = library().deleteSource(original.source.sourceId, original.source.sourceVersion)
            finishSourceManagement(t, result, "删除")
        }
    }
    private suspend fun finishSourceManagement(t: OnlineUiTicket, result: SourceImportResult, action: String) {
        if (qualifies(t)) {
            if (!result.durable || result.confirmation == SourceImportConfirmation.UNKNOWN) mutable.update { it.copy(unconfirmedId = result.operationId) }
            else if (result.confirmation == SourceImportConfirmation.APPLIED) feedback("原${action}已确认。")
            else feedback("原${action}已确认未应用。")
        }
        loadPending(t)
        val actual = library().sources(); if (qualifies(t)) replace(state.value.copy(sources = actual, loaded = true))
    }
    fun select(source: ReaderSourceEntry, revision: String) {
        if (!offered(revision) || state.value.pending || source !in state.value.sources || !source.enabled) return
        clearDocument(); retireSession(); queryGeneration++
        replace(state.value.copy(selected = source, selection = null, session = null, query = "", books = emptyList(), detail = null, added = null,
            loadedPage = false, sessionFailed = false, searchStopped = false))
        run(ticket()) { t ->
            val actual = library().selectOnlineSource(source.sourceId)
            if (qualifies(t)) replace(state.value.copy(selection = actual, page = OnlineWorkspacePage.RESULTS))
        }
    }
    fun editQuery(value: String, revision: String) {
        if (!offered(revision) || state.value.pending) return
        clearDocument(); retireSession(); queryGeneration++
        replace(state.value.copy(query = value, session = null, books = emptyList(), detail = null, added = null, ended = false, nextPage = 1,
            loadedPage = false, sessionFailed = false, searchStopped = false))
    }
    fun search(revision: String) {
        if (!offered(revision) || state.value.pending || state.value.query.isBlank()) return
        val selected = state.value.selection ?: return
        startSearch(selected, state.value.query)
    }

    /** Explicit retry only after the caller has resolved every earlier original. */
    fun retrySearch(revision: String) {
        if (!active || !visible()) return
        val (selected, query) = OnlineWorkspaceUiPolicy.searchRetry(state.value, revision) ?: return
        startSearch(selected, query)
    }
    private fun startSearch(selected: ReaderOnlineSourceSelection, query: String) {
        clearDocument(); retireSession(); queryGeneration++
        replace(state.value.copy(session = null, books = emptyList(), detail = null, ended = false, nextPage = 1,
            loadedPage = false, sessionFailed = false, searchStopped = false))
        run(ticket(), search = true) { t -> performSearch(t, selected, query) }
    }
    fun stopSearch(revision: String) {
        if (!active || !visible()) return
        // Retire this presentation. The retained Core call still owns its exact terminal and ACK.
        OnlineWorkspaceUiPolicy.stopSearch(state.value, revision)?.let { mutable.value = it }
    }

    private suspend fun performSearch(t: OnlineUiTicket, selected: ReaderOnlineSourceSelection, query: String) {
        if (state.value.searchStopped) return
        val session = library().beginOnlineSearch(selected, query)
        if (!qualifies(t) || state.value.searchStopped) { library().closeOnlineSearch(session); return }
        mutable.update { it.copy(session = session) } // Exact close identity exists before the first page.
        val page = library().onlineSearchPage(session, 1)
        if (qualifies(t) && OnlineWorkspaceUiPolicy.publishesSearchPage(state.value, session))
            replace(state.value.copy(books = page.books, nextPage = 2, ended = page.hasMore == false, loadedPage = true))
    }
    fun next(revision: String) {
        if (!offered(revision) || state.value.pending || state.value.sessionFailed || !state.value.loadedPage || state.value.ended || state.value.nextPage > 20) return
        val session = state.value.session ?: return; val page = state.value.nextPage
        run(ticket(), search = true) { t ->
            if (!OnlineWorkspaceUiPolicy.publishesSearchPage(state.value, session)) return@run
            val result = library().onlineSearchPage(session, page)
            if (qualifies(t) && OnlineWorkspaceUiPolicy.publishesSearchPage(state.value, session))
                replace(state.value.copy(books = state.value.books + result.books, nextPage = page + 1, ended = result.hasMore == false))
        }
    }
    fun detail(book: ReaderOnlineBook, revision: String) {
        if (!offered(revision) || state.value.pending || state.value.sessionFailed || state.value.books.none { it === book }) return
        val session = state.value.session ?: return
        run(ticket()) { t ->
            val result = library().previewOnlineBook(session, book)
            if (qualifies(t) && state.value.session === session) replace(state.value.copy(detail = result, page = OnlineWorkspacePage.DETAIL, added = null))
        }
    }
    fun add(revision: String) {
        if (!offered(revision) || state.value.pending) return
        val detail = state.value.detail?.takeIf { it.contentKind == ReaderOnlineContentKind.TEXT && it.key != state.value.added } ?: return
        run(ticket()) { t ->
            val result = library().addOnlinePreview(detail)
            check(result.key == detail.key) { "ONLINE_ADD_UI_SCOPE" }
            if (result.durable && result.confirmation in setOf(ReaderOnlineAddConfirmation.ADDED, ReaderOnlineAddConfirmation.ALREADY_PRESENT_BEFORE_NEW_INTENT)) {
                if (qualifies(t)) { mutable.update { it.copy(added = detail.key) }; feedback("已加入书架。") }
                reloadShelf() // Read-only projection. Its failure cannot cause another add.
            } else if (qualifies(t)) {
                if (result.confirmation == ReaderOnlineAddConfirmation.UNKNOWN) mutable.update { it.copy(unconfirmedId = result.operationId) }
                else feedback("原入架已确认未应用。")
            }
            loadPending(t)
        }
    }
    fun reconcileSource(id: String, revision: String) {
        if (!offered(revision) || state.value.pendingSources.none { it.id == id }) return
        run(ticket()) { t ->
            val result = library().reconcileSourceImport(id)
            if (qualifies(t) && result.durable && result.confirmation != SourceImportConfirmation.UNKNOWN) mutable.update { it.copy(unconfirmedId = if (it.unconfirmedId == id) null else it.unconfirmedId) }
            loadPending(t)
            val rows = library().sources(); if (qualifies(t)) replace(state.value.copy(sources = rows, loaded = true))
        }
    }
    fun reconcileAdd(id: String, revision: String) {
        if (!offered(revision) || state.value.pendingAdds.none { it.operationId == id }) return
        run(ticket()) { t ->
            val result = library().reconcileOnlineAdd(id) // Same child originals only; no unknown replay.
            if (qualifies(t) && result.durable && result.confirmation != ReaderOnlineAddConfirmation.UNKNOWN) mutable.update { it.copy(unconfirmedId = if (it.unconfirmedId == id) null else it.unconfirmedId) }
            if (result.durable && result.confirmation == ReaderOnlineAddConfirmation.ADDED) { if (qualifies(t)) feedback("原入架已确认。"); reloadShelf() }
            loadPending(t)
        }
    }
    fun observeRead(id: String, revision: String) {
        if (!offered(revision) || state.value.pendingReads.none { it.id == id }) return
        run(ticket()) { t ->
            library().observeOnlineOriginal(id) // Warm facts only, never a resend or a fabricated resolved outcome.
            loadPending(t)
        }
    }
    private suspend fun loadPending(t: OnlineUiTicket) {
        val reads = library().pendingOnlineAcquisitions()
        if (qualifies(t)) mutable.update { it.copy(pendingReads = reads) }
        val sources = library().pendingSourceImports(); val adds = library().pendingOnlineAdds()
        if (qualifies(t)) mutable.update { it.copy(pendingSources = sources, pendingAdds = adds) }
    }
    private fun feedback(message: String) { mutable.update { it.copy(feedback = message, feedbackId = UUID.randomUUID().toString()) } }
    fun clearFeedback(id: String) { mutable.update { if (it.feedbackId == id) it.copy(feedback = null) else it } }
    private fun run(t: OnlineUiTicket, search: Boolean = false, action: suspend (OnlineUiTicket) -> Unit) {
        if (!qualifies(t) || state.value.busy) return
        mutable.update { it.copy(busy = true, searchInFlight = search, error = null, feedback = null) }; mainBusy(true)
        scope.launch {
            try { onVmLane { if (qualifies(t)) action(t) } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                val protocol = retainProtocol(error)
                if (qualifies(t)) {
                    val id = (error as? SourceRulesPendingException)?.operationId ?: (error as? ReaderOnlineAddUnconfirmed)?.operationId
                    val original = (error as? ReaderOnlineOriginalPending)?.original
                    mutable.update { it.copy(sessionFailed = it.sessionFailed || it.session != null, unconfirmedId = id ?: it.unconfirmedId,
                        pendingReads = if (original != null) it.pendingReads.filterNot { old -> old.id == original.id } + original else it.pendingReads,
                        error = if (protocol || id != null || original != null || it.searchStopped) null else when (error) {
                        is ReaderSourceDocumentException -> "无法识别这份书源文件，请选择包含书源对象或数组的 JSON 文件。"
                        is SecurityException -> "无法访问所选文件，请重新选择。"
                        else -> if (it.added != null) "入架已确认，但书架读取未完成。可重新读取。" else "本次操作未完成，请读取状态或核对原操作。"
                    }) }
                    try { onVmLane { loadPending(t) } } catch (cancelled: CancellationException) { throw cancelled } catch (pendingRead: Exception) { retainProtocol(pendingRead) }
                }
            } finally {
                if (search && state.value.searchStopped && qualifies(t)) {
                    retireSession()
                    mutable.update { it.copy(session = null) }
                }
                mutable.update { it.copy(busy = false, searchInFlight = false) }; mainBusy(false)
            }
        }
    }
    private fun retireSession() {
        val original = state.value.session ?: return
        scope.launch { try { cleanupLane { library().closeOnlineSearch(original) } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { retainProtocol(error) } }
    }
    fun back(): Boolean {
        if (!active || state.value.busy) return true
        state.value.sourceDeletion?.let { dismissSourceDeletion(it.identity); return true }
        return when (state.value.page) {
            OnlineWorkspacePage.DETAIL -> { replace(state.value.copy(detail = null, page = OnlineWorkspacePage.RESULTS)); true }
            OnlineWorkspacePage.RESULTS -> { retireSession(); queryGeneration++; replace(state.value.copy(session = null, books = emptyList(), detail = null,
                page = OnlineWorkspacePage.SOURCES, searchStopped = false)); true }
            OnlineWorkspacePage.SOURCES -> false
        }
    }
    fun leave() { sourceDeletion = null; mutable.update { it.copy(sourceDeletion = null) }; host++; picker = null; clearDocument(); retireSession();
        replace(state.value.copy(session = null, detail = null, books = emptyList(), searchStopped = false)) }
    fun close() { sourceDeletion = null; mutable.update { it.copy(sourceDeletion = null) }; active = false; picker = null; host++; clearDocument() } // Library retires its real client; no detached new UI task/runtime.
}
