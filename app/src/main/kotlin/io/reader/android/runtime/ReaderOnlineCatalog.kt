package io.reader.android.runtime

import io.reader.android.runtime.http.ReaderMangaResourceHost
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
    private val beforeNewStage: () -> Unit = {},
) {
    private class Search(val session: ReaderOnlineSearchSession) {
        var closed = false
        var lastPage = 0
        var ended = false
        var failed = false
        var retainedCandidateBytes = 0L
        val issued = Collections.newSetFromMap(IdentityHashMap<ReaderOnlineBook, Boolean>())
    }
    private val originals = ReaderOnlineCatalogOriginals(transport, assertHeld, beforeNewStage)
    private val selections = Collections.newSetFromMap(IdentityHashMap<ReaderOnlineSourceSelection, Boolean>())
    private val searches = linkedMapOf<String, Search>()
    private var addPreview: Pair<ReaderOnlineDetailPreview, ReaderOnlineSearchSession>? = null
    private var generation = 0L
    private var runtime: String? = null
    private var capabilitiesConfigured = false

    fun select(lease: ReaderOperationOwner.ClientLease, sourceId: String): ReaderOnlineSourceSelection {
        checkLane()
        beforeNewStage()
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

    /** Prepare a manga chapter's source catalog while the issued search/detail session is alive. */
    fun mangaToc(lease: ReaderOperationOwner.ClientLease, preview: ReaderOnlineDetailPreview,
        resumeChapterId: String? = null): ReaderMangaChapterRequest {
        checkLane()
        val issued = checkNotNull(addPreview) { "ONLINE_MANGA_PREVIEW_NOT_ISSUED" }
        active(lease, issued.second)
        check(issued.first === preview && preview.contentKind == ReaderOnlineContentKind.MANGA) {
            "ONLINE_MANGA_PREVIEW_RETIRED"
        }
        val params = JSONObject().put("sourceId", preview.key.sourceId)
            .put("bookId", preview.key.bookId).put("variables", JSONObject(preview.variables))
        preview.tocUrl?.let { params.put("tocUrl", it) }
        val toc = originals.request("book.toc", preview.key.sourceId, params)
        ReaderOnlineAddPolicy.scope(toc, preview.key)
        ReaderOnlineAddPolicy.sourceVersion(toc, preview.sourceVersion)
        check(toc.optString("catalogDeferredReason").isEmpty()) { "ONLINE_MANGA_CATALOG_NOT_ADMITTED" }
        val readable = ReaderOnlineAddPolicy.readableChapterIndexes(toc)
        val chapters = toc.getJSONArray("toc")
        val selectedIndex = if (resumeChapterId == null) readable.first() else {
            // Core's manga chapter identity is the source TOC URL, not an assumed contiguous index.
            val matching = (0 until chapters.length()).map { chapters.getJSONObject(it) }
                .filter { it.optString("url") == resumeChapterId }
            check(resumeChapterId.isNotBlank() && matching.size == 1 && matching.single().getInt("index") in readable) {
                "ONLINE_MANGA_SAVED_CHAPTER_NOT_READABLE"
            }
            matching.single().getInt("index")
        }
        check((0 until chapters.length()).count { chapters.getJSONObject(it).getInt("index") == selectedIndex } == 1) {
            "ONLINE_MANGA_CHAPTER_IDENTITY"
        }
        return ReaderMangaChapterRequest(ReaderMangaChapterRef(preview.key.sourceId, preview.key.bookId),
            selectedIndex, ReaderMangaReadPolicy.CACHE_FIRST)
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
    /** Stored-source reading only; shares all current-runtime unknown/acquisition barriers. */
    fun readStored(method: String, key: ShelfBookKey, params: JSONObject): JSONObject {
        checkLane(); ReaderOnlineReadingPolicy.remote(key)
        check(method in ReaderOnlineReadingPolicy.METHODS) { "ONLINE_READING_METHOD_NOT_ADMITTED" }
        ReaderOnlineReadingPolicy.scope(params, key)
        configureCapabilities()
        return originals.request(method, key.sourceId, params)
    }
    /** A refresh must use a real stored-source TOC URL, since an omitted URL only reads cache. */
    fun refreshToc(key: ShelfBookKey, version: String, variables: JSONObject) {
        checkLane(); ReaderOnlineReadingPolicy.remote(key); configureCapabilities()
        val stored = originals.request("search-book.get", key.sourceId,
            JSONObject().put("origin", key.sourceId).put("bookUrl", key.bookId)).optJSONObject("book")
        stored?.let {
            check(it.getString("origin") == key.sourceId && it.getString("bookUrl") == key.bookId) { "ONLINE_REFRESH_BOOK_IDENTITY" }
        }
        val params = JSONObject().put("sourceId", key.sourceId).put("bookId", key.bookId)
        val retainedUrl = stored?.let { ReaderOnlineCatalogPolicy.optional(it, "tocUrl") }?.takeIf { it.isNotBlank() }
        if (retainedUrl != null) {
            params.put("tocUrl", retainedUrl).put("variables", variables)
        } else {
            val shelf = readStored("bookshelf.get", key, params).getJSONObject("book")
            ReaderOnlineReadingPolicy.scope(shelf, key)
            val book = JSONObject().put("bookId", key.bookId).put("title", shelf.getString("title")).put("author", shelf.getString("author"))
            val detail = originals.request("book.detail", key.sourceId,
                JSONObject().put("sourceId", key.sourceId).put("book", book).put("bookUrl", key.bookId))
            check(detail.getString("sourceId") == key.sourceId && detail.getString("sourceVersion") == version &&
                detail.getJSONObject("book").getString("bookId") == key.bookId &&
                ReaderOnlineCatalogPolicy.kind(detail) == ReaderOnlineContentKind.TEXT) { "ONLINE_REFRESH_DETAIL_SCOPE" }
            val tocUrl = detail.getString("tocUrl").also { check(it.isNotBlank()) { "ONLINE_REFRESH_TOC_URL_UNAVAILABLE" } }
            params.put("tocUrl", tocUrl).put("variables", detail.optJSONObject("variables") ?: JSONObject())
        }
        val refreshed = originals.request("book.toc", key.sourceId, params)
        ReaderOnlineReadingPolicy.scope(refreshed, key)
        check(refreshed.getString("sourceVersion") == version && refreshed.getBoolean("catalogInstalled") &&
            refreshed.isNull("catalogDeferredReason")) { "ONLINE_REFRESH_CATALOG_NOT_INSTALLED" }
    }
    /** Native manga directory projection over the same original-call owner, without a text/shelf entry. */
    fun readMangaDirectory(method: String, chapter: ReaderMangaChapterRef, params: JSONObject): JSONObject {
        checkLane()
        require(chapter.sourceId.isNotBlank() && chapter.sourceId != "local" && chapter.bookId.isNotBlank())
        check(method in setOf("reading.directory.view.open.v2", "reading.directory.view.page.v2", "reading.directory.target.resolve.v2")) {
            "MANGA_DIRECTORY_METHOD_NOT_ADMITTED"
        }
        check(params.getString("sourceId") == chapter.sourceId && params.getString("bookId") == chapter.bookId) { "MANGA_DIRECTORY_SCOPE" }
        configureCapabilities()
        return originals.request(method, chapter.sourceId, params)
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
        val data = originals.request("runtime.setHostCapabilities", "", JSONObject().put("capabilities",
            JSONArray().put("http.execute").put(ReaderMangaResourceHost.CAPABILITY))
            .put("extensions", JSONArray()).put("lanes", JSONArray()).put("platform", "android"))
        check(data.get("applied") == true && data.getInt("capabilities") == 2 && data.getInt("lanes") == 0) { "ONLINE_HOST_MANIFEST_UNCONFIRMED" }
        capabilitiesConfigured = true // Matches AppCoreOwner's HTTP and manga transfer handlers; no other capabilities claimed.
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
