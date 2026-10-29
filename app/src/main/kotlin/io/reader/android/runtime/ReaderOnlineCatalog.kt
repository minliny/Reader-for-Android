package io.reader.android.runtime

import org.json.JSONArray
import org.json.JSONObject
import java.util.Collections
import java.util.IdentityHashMap
import java.util.UUID

/** App singleton, all methods in its caller's existing held business lane. No extra runtime/Host loop. */
internal class ReaderOnlineCatalog(
    private val transport: CoreTransport,
    private val sources: (String) -> List<ReaderSourceEntry>,
    private val assertHeld: () -> Unit,
) {
    private class Search(val session: ReaderOnlineSearchSession) {
        var closed = false
        var lastPage = 0
        var ended = false
        var failed = false
        var retainedCandidateBytes = 0L
        val issued = Collections.newSetFromMap(IdentityHashMap<ReaderOnlineBook, Boolean>())
    }
    private val originals = ReaderOnlineCatalogOriginals(transport, assertHeld)
    private val selections = Collections.newSetFromMap(IdentityHashMap<ReaderOnlineSourceSelection, Boolean>())
    private val searches = linkedMapOf<String, Search>()
    private var addPreview: Pair<ReaderOnlineDetailPreview, ReaderOnlineSearchSession>? = null
    private var generation = 0L
    private var runtime: String? = null
    private var capabilitiesConfigured = false

    fun select(lease: ReaderOperationOwner.ClientLease, sourceId: String): ReaderOnlineSourceSelection {
        checkLane()
        require(sourceId.isNotBlank() && sourceId != "local") { "ONLINE_STORED_SOURCE_REQUIRED" }
        originals.pending().firstOrNull()?.let { throw ReaderOnlineOriginalPending(it) }
        val source = sources(sourceId).single { it.sourceId == sourceId && it.enabled }
        check(source.sourceVersion.isNotBlank()) { "ONLINE_SOURCE_VERSION_REQUIRED" }
        // Selections have no Native resources. Retiring them never mutates an original call or allocates a Native resource.
        addPreview = null
        selections.clear()
        searches.values.forEach { retire(it) }; searches.clear()
        val selected = ReaderOnlineSourceSelection(source.sourceId, source.sourceVersion, source.name, lease, transport.session)
        check(selections.size < 8) { "ONLINE_SELECTION_CAPACITY" }
        selections.add(selected)
        return selected
    }
    fun start(lease: ReaderOperationOwner.ClientLease, source: ReaderOnlineSourceSelection, keyword: String): ReaderOnlineSearchSession {
        selection(lease, source)
        require(keyword.isNotBlank() && keyword.toByteArray(Charsets.UTF_8).size <= 4_096) { "ONLINE_KEYWORD_BUDGET" }
        originals.pending().firstOrNull()?.let { throw ReaderOnlineOriginalPending(it) }
        addPreview = null
        configureCapabilities()
        searches.values.filter { it.session.source.lease == lease }.forEach { retire(it) }
        searches.entries.removeAll { it.value.closed }
        check(searches.size < 8 && generation < Long.MAX_VALUE) { "ONLINE_SESSION_CAPACITY" }
        val session = ReaderOnlineSearchSession(UUID.randomUUID().toString(), ++generation, keyword, source)
        val search = Search(session); searches[session.id] = search
        return session // Caller owns the exact close identity before the first network-backed page.
    }
    fun page(lease: ReaderOperationOwner.ClientLease, session: ReaderOnlineSearchSession, page: Int): ReaderOnlineSearchPage {
        val search = active(lease, session)
        require(page == search.lastPage + 1 && page in 1..20 && !search.ended) { "ONLINE_PAGE_NOT_NEXT" }
        return fetch(search, page)
    }
    fun preview(lease: ReaderOperationOwner.ClientLease, session: ReaderOnlineSearchSession, selected: ReaderOnlineBook): ReaderOnlineDetailPreview {
        val search = active(lease, session)
        check(selected in search.issued && selected.key.sourceId == session.source.sourceId) { "ONLINE_CANDIDATE_NOT_ISSUED" }
        try {
            val value = originals.request("book.detail", selected.key.sourceId, JSONObject().put("sourceId", selected.key.sourceId)
                .put("book", JSONObject(selected.originalCoreBookJson)).put("bookUrl", selected.key.bookId))
            return ReaderOnlineCatalogPolicy.detail(session.source, selected, value).also { addPreview = it to session }
        } catch (error: Exception) { search.failed = true; throw error }
    }
    fun close(session: ReaderOnlineSearchSession) {
        checkLane()
        val search = searches[session.id] ?: return
        check(search.session === session) { "FOREIGN_ONLINE_SEARCH_SESSION" }
        if (addPreview?.second === session) addPreview = null
        retire(search)
        searches.remove(session.id)
        // There is no Core search cursor/close RPC. Pending original routes/facts are untouched.
    }
    fun authorizeAdd(lease: ReaderOperationOwner.ClientLease, preview: ReaderOnlineDetailPreview): ReaderOnlineAddAuthority {
        val issued = checkNotNull(addPreview) { "ONLINE_ADD_PREVIEW_NOT_ISSUED" }
        active(lease, issued.second)
        check(issued.first === preview && preview.contentKind == ReaderOnlineContentKind.TEXT && originals.pending().isEmpty()) { "ONLINE_ADD_PREVIEW_RETIRED" }
        return ReaderOnlineAddAuthority(preview, lease, transport.session)
    }
    fun pending(): List<ReaderOnlineOriginalObservation> { assertHeld(); return originals.pending() }
    fun observeOriginal(id: String): ReaderOnlineOriginalObservation { assertHeld(); return originals.observe(id) }

    private fun fetch(search: Search, page: Int): ReaderOnlineSearchPage {
        val session = search.session
        check(search.issued.size <= 5_000 - ReaderOnlineCatalogPolicy.MAX_PAGE_BOOKS) { "ONLINE_CANDIDATE_CAPACITY" }
        try {
            val data = originals.request("book.search", session.source.sourceId, JSONObject().put("sourceId", session.source.sourceId)
            .put("keyword", session.keyword).put("page", page))
            val result = ReaderOnlineCatalogPolicy.page(session, page, data)
            val bytes = result.books.sumOf { it.originalCoreBookJson.toByteArray(Charsets.UTF_8).size.toLong() }
            check(bytes <= 4 * 1024 * 1024 - search.retainedCandidateBytes) { "ONLINE_CANDIDATE_BYTE_BUDGET" }
            search.retainedCandidateBytes += bytes
            search.issued.addAll(result.books); search.lastPage = page; search.ended = result.hasMore == false
            return result
        } catch (error: Exception) { search.failed = true; throw error }
    }
    private fun configureCapabilities() {
        if (capabilitiesConfigured) return
        val data = originals.request("runtime.setHostCapabilities", "", JSONObject().put("capabilities", JSONArray().put("http.execute"))
            .put("extensions", JSONArray()).put("lanes", JSONArray()).put("platform", "android"))
        check(data.get("applied") == true && data.getInt("capabilities") == 1 && data.getInt("lanes") == 0) { "ONLINE_HTTP_MANIFEST_UNCONFIRMED" }
        capabilitiesConfigured = true // No cookie/login/asset/WebView capability claimed.
    }
    private fun selection(lease: ReaderOperationOwner.ClientLease, selected: ReaderOnlineSourceSelection) {
        checkLane()
        check(selected in selections && selected.lease == lease && selected.runtimeSession == transport.session) { "ONLINE_SELECTION_RETIRED" }
    }
    private fun active(lease: ReaderOperationOwner.ClientLease, session: ReaderOnlineSearchSession): Search {
        selection(lease, session.source)
        return checkNotNull(searches[session.id]) { "ONLINE_SESSION_RETIRED" }.also {
            check(it.session === session && !it.closed && !it.failed) { "ONLINE_SESSION_RETIRED" }
        }
    }
    private fun retire(search: Search) { search.closed = true; search.issued.clear(); search.retainedCandidateBytes = 0 }
    private fun checkLane() {
        assertHeld()
        val actual = transport.session
        if (runtime == null) runtime = actual else check(runtime == actual) { "ONLINE_RUNTIME_RETIRED" }
    }
}
