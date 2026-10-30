package io.reader.android.runtime

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

/** Protocol packets only: no book source rules, content fixture, network, Native or database. NOT_RUN. */
class ReaderOnlineCatalogPolicyTest {
    private val lease = ReaderOperationOwner.ClientLease(UUID.randomUUID(), UUID.randomUUID(), 1, 1)
    private fun source() = ReaderOnlineSourceSelection("protocol-source-a", "version-a", "source", lease, "runtime-a")
    private fun page(source: ReaderOnlineSourceSelection, books: JSONArray) = JSONObject()
        .put("sourceId", source.sourceId).put("sourceVersion", source.sourceVersion).put("contentKind", "text").put("books", books)

    @Test fun exactSourceVersionAndOpaqueBookSurvivePreviewWithoutLocalAuthority() {
        val selected = source()
        val session = ReaderOnlineSearchSession("host-session", 1, "query", selected)
        val raw = JSONObject().put("bookId", "local:" + "a".repeat(64)).put("title", "protocol title")
            .put("author", "@protocol").put("opaqueVariable", JSONObject().put("key", "retained"))
        val result = ReaderOnlineCatalogPolicy.page(session, 1, page(selected, JSONArray().put(raw)))
        assertNull(result.hasMore) // Core has no page total/cursor; one nonempty page cannot prove another exists.
        assertEquals(ShelfBookKey(selected.sourceId, raw.getString("bookId")), result.books.single().key)
        assertEquals(raw.toString(), result.books.single().originalCoreBookJson)
        val detail = JSONObject().put("sourceId", selected.sourceId).put("sourceVersion", selected.sourceVersion)
            .put("contentKind", JSONObject.NULL).put("book", JSONObject().put("bookId", raw.getString("bookId"))
                .put("title", "detail").put("author", "author")).put("variables", JSONObject().put("token", "original"))
        val preview = ReaderOnlineCatalogPolicy.detail(selected, result.books.single(), detail)
        assertEquals(ReaderOnlineContentKind.UNKNOWN, preview.contentKind)
        val legacyPage = JSONObject(page(selected, JSONArray().put(raw)).toString()).also { it.remove("sourceVersion") }
        assertEquals(ReaderOnlineContentKind.TEXT, ReaderOnlineCatalogPolicy.page(session, 1, legacyPage).contentKind)
        val legacyDetail = JSONObject(detail.toString()).also { it.remove("sourceVersion"); it.remove("contentKind") }
        assertEquals(ReaderOnlineContentKind.TEXT, ReaderOnlineCatalogPolicy.detail(selected, result.books.single(), legacyDetail).contentKind)
        assertEquals(mapOf("token" to "original"), preview.variables)
        assertThrows(IllegalStateException::class.java) {
            ReaderOnlineCatalogPolicy.page(session, 1, JSONObject(page(selected, JSONArray()).toString()).put("contentKind", JSONObject.NULL))
        }
        assertThrows(IllegalStateException::class.java) {
            ReaderOnlineCatalogPolicy.page(session, 1, JSONObject(page(selected, JSONArray()).toString()).put("sourceVersion", JSONObject.NULL))
        }
        assertThrows(IllegalStateException::class.java) {
            ReaderOnlineCatalogPolicy.page(session, 1, page(selected, JSONArray()).put("sourceVersion", "changed"))
        }
        assertThrows(IllegalStateException::class.java) {
            ReaderOnlineCatalogPolicy.detail(selected, result.books.single(), JSONObject(detail.toString()).put("sourceId", "foreign-source"))
        }
    }

    @Test fun hostSessionCloseHasNoRpcAndUnknownPageNeverReplaysOrBecomesAnotherQuery() {
        val selectedSource = source()
        val methods = mutableListOf<String>(); val params = mutableListOf<String>(); var nextId = 0L
        var rejectSearch = false
        val port = object : CoreTransport {
            override val session = "runtime-a"
            override fun request(method: String, parameters: JSONObject, timeoutMs: Long, beforeSend: (Long) -> Unit,
                terminal: ((JSONObject) -> Unit)?): JSONObject {
                methods.add(method); params.add(parameters.toString()); val id = ++nextId; beforeSend(id)
                if (rejectSearch && method == "book.search") throw CoreProtocolPending(
                    CoreProtocolReference(session, "protocol-original", id, "protocol-token"), CoreProtocolStage.ORIGINAL_UNCERTAIN)
                val data = if (method == "runtime.setHostCapabilities") JSONObject().put("applied", true).put("capabilities", 2).put("lanes", 0)
                    else page(selectedSource, JSONArray())
                terminal?.invoke(JSONObject().put("protocolVersion", 1).put("type", "result").put("requestId", id).put("data", data))
                return data
            }
            override fun drainOriginalTerminal(requestId: Long, timeoutMs: Long) { error("NO_NEW_DRAIN") }
            override fun close() { error("NO_NATIVE_CLOSE") }
        }
        val service = ReaderOnlineCatalog(port, { id -> listOf(ReaderSourceEntry(id, "source", "", true, false, "version-a", null)) }, {})
        val selection = service.select(lease, selectedSource.sourceId)
        val first = service.start(lease, selection, "first")
        assertEquals(false, service.page(lease, first, 1).hasMore)
        val beforeClose = methods.toList(); service.close(first); assertEquals(beforeClose, methods)
        assertThrows(IllegalStateException::class.java) { service.page(lease, first, 2) }
        val second = service.start(lease, selection, "second"); rejectSearch = true
        assertThrows(CoreProtocolPending::class.java) { service.page(lease, second, 1) }
        val pending = service.pending().single()
        assertEquals("book.search", pending.method); assertEquals(ReaderOnlineOriginalState.NO_TERMINAL, pending.state)
        assertEquals("second", JSONObject(pending.originalParametersJson).getString("keyword"))
        val sent = methods.toList(); service.close(second)
        assertEquals(pending, service.observeOriginal(pending.id))
        assertThrows(ReaderOnlineOriginalPending::class.java) { service.start(lease, selection, "third") }
        assertEquals(sent, methods) // No Core close, retry, query-with-new-ID, shelf write or progress command.
    }

    @Test fun sharedWriterPendingBlocksCatalogBeforeAnyNewCoreDispatch() {
        val methods = mutableListOf<String>()
        var sharedWriterPending = false
        val port = object : CoreTransport {
            override val session = "runtime-a"
            override fun request(method: String, parameters: JSONObject, timeoutMs: Long, beforeSend: (Long) -> Unit,
                terminal: ((JSONObject) -> Unit)?): JSONObject {
                methods += method
                val id = methods.size.toLong(); beforeSend(id)
                val data = if (method == "runtime.setHostCapabilities") JSONObject().put("applied", true).put("capabilities", 2).put("lanes", 0)
                    else page(source(), JSONArray())
                terminal?.invoke(JSONObject().put("protocolVersion", 1).put("requestId", id).put("type", "result").put("data", data))
                return data
            }
            override fun drainOriginalTerminal(requestId: Long, timeoutMs: Long) = error("NO_NEW_DRAIN")
            override fun close() = error("NO_NATIVE_CLOSE")
        }
        val service = ReaderOnlineCatalog(port,
            { id -> listOf(ReaderSourceEntry(id, "source", "", true, false, "version-a", null)) },
            {}, { check(!sharedWriterPending) { "SHARED_WRITER_ORIGINAL_PENDING" } })
        val selection = service.select(lease, source().sourceId)
        val before = methods.toList()
        sharedWriterPending = true
        assertThrows(IllegalStateException::class.java) { service.start(lease, selection, "blocked") }
        assertEquals(before, methods)
    }

    @Test fun mangaTocCarriesDetailVariablesAndSelectsFirstReadableChapter() {
        val fixture = MangaFixture()
        val request = fixture.catalog.mangaToc(lease, fixture.preview)
        assertEquals(ReaderMangaChapterRef(source().sourceId, "manga-book"), request.chapter)
        assertEquals(7, request.chapterIndex) // Index 0 is a group; readable indexes are sparse.
        assertEquals(ReaderMangaReadPolicy.CACHE_FIRST, request.policy)
        val parameters = fixture.requests.single { it.first == "book.toc" }.second
        assertEquals(source().sourceId, parameters.getString("sourceId"))
        assertEquals("manga-book", parameters.getString("bookId"))
        assertEquals("https://example.invalid/manga/toc", parameters.getString("tocUrl"))
        assertEquals("original-token", parameters.getJSONObject("variables").getString("token"))
        assertEquals("{\"cursor\":2}", parameters.getJSONObject("variables").getString("continuation"))
        assertEquals(listOf("runtime.setHostCapabilities", "book.search", "book.detail", "book.toc"),
            fixture.requests.map { it.first })
    }

    @Test fun mangaTocRejectsForeignIdentityVersionDeferredAndUnreadableCatalogs() {
        val invalidTocs = listOf<(JSONObject) -> Unit>(
            { it.put("sourceId", "foreign-source") },
            { it.put("bookId", "foreign-book") },
            { it.put("sourceVersion", "changed-version") },
            { it.put("catalogDeferredReason", "classificationRequired") },
            { it.put("readableChapterIndexes", JSONArray()) },
            { it.put("readableChapterIndexes", JSONArray().put(99)) },
            { it.getJSONArray("toc").put(JSONObject().put("index", 7).put("title", "duplicate").put("url", "/duplicate")) },
        )
        invalidTocs.forEach { change ->
            val fixture = MangaFixture(change)
            assertThrows(IllegalStateException::class.java) { fixture.catalog.mangaToc(lease, fixture.preview) }
            assertEquals(1, fixture.requests.count { it.first == "book.toc" })
            assertFalse(fixture.requests.any { it.first.startsWith("manga.") })
        }
    }

    @Test fun mangaPreparationSurvivesSearchCloseWithoutReusingRetiredPreview() {
        val fixture = MangaFixture()
        val request = fixture.catalog.mangaToc(lease, fixture.preview)
        val sent = fixture.requests.map { it.first }
        fixture.catalog.close(fixture.search)
        assertEquals(sent, fixture.requests.map { it.first }) // Closing presentation has no Core RPC.
        assertEquals(7, request.chapterIndex)
        assertEquals(ReaderMangaReadPolicy.CACHE_FIRST, request.policy)
        assertThrows(IllegalStateException::class.java) { fixture.catalog.mangaToc(lease, fixture.preview) }
        assertEquals(sent, fixture.requests.map { it.first })
    }

    @Test fun catalogAdvertisesOnlyHttpAndMangaTransferAndRejectsWrongAcknowledgementCount() {
        val fixture = MangaFixture()
        val params = fixture.requests.single { it.first == "runtime.setHostCapabilities" }.second
        val names = params.getJSONArray("capabilities")
        assertEquals(listOf("http.execute", "manga.resource.transfer"), (0 until names.length()).map { names.getString(it) })
        assertEquals(0, params.getJSONArray("extensions").length())
        assertEquals(0, params.getJSONArray("lanes").length())
        assertEquals("android", params.getString("platform"))
        listOf(0, 1, 3).forEach { count ->
            val error = assertThrows(IllegalStateException::class.java) { MangaFixture(capabilityAckCount = count) }
            assertEquals("ONLINE_HOST_MANIFEST_UNCONFIRMED", error.message)
        }
    }

    /** Reuses the catalog protocol-port fixture; it never executes source rules or Native. */
    private inner class MangaFixture(changeToc: (JSONObject) -> Unit = {}, capabilityAckCount: Int = 2) {
        val requests = mutableListOf<Pair<String, JSONObject>>()
        private val book = JSONObject().put("bookId", "manga-book").put("title", "Comic").put("author", "Author")
        private val toc = JSONObject().put("sourceId", source().sourceId).put("bookId", "manga-book")
            .put("sourceVersion", source().sourceVersion).put("contentKind", "manga")
            .put("toc", JSONArray()
                .put(JSONObject().put("index", 0).put("title", "Volume").put("url", ""))
                .put(JSONObject().put("index", 7).put("title", "First readable").put("url", "/chapter/7"))
                .put(JSONObject().put("index", 12).put("title", "Next readable").put("url", "/chapter/12")))
            .put("readableChapterIndexes", JSONArray().put(7).put(12)).also(changeToc)
        private val port = object : CoreTransport {
            override val session = "runtime-a"
            override fun request(method: String, parameters: JSONObject, timeoutMs: Long, beforeSend: (Long) -> Unit,
                terminal: ((JSONObject) -> Unit)?): JSONObject {
                requests += method to JSONObject(parameters.toString())
                val id = requests.size.toLong(); beforeSend(id)
                val data = when (method) {
                    "runtime.setHostCapabilities" -> JSONObject().put("applied", true).put("capabilities", capabilityAckCount).put("lanes", 0)
                    "book.search" -> page(source(), JSONArray().put(book)).put("contentKind", "manga")
                    "book.detail" -> JSONObject().put("sourceId", source().sourceId).put("sourceVersion", source().sourceVersion)
                        .put("contentKind", "manga").put("book", book).put("tocUrl", "https://example.invalid/manga/toc")
                        .put("variables", JSONObject().put("token", "original-token").put("continuation", "{\"cursor\":2}"))
                    "book.toc" -> toc
                    else -> error("UNEXPECTED_MANGA_PREPARATION_METHOD:$method")
                }
                terminal?.invoke(JSONObject().put("protocolVersion", 1).put("requestId", id).put("type", "result").put("data", data))
                return data
            }
            override fun drainOriginalTerminal(requestId: Long, timeoutMs: Long) = error("NO_NEW_DRAIN")
            override fun close() = error("NO_NATIVE_CLOSE")
        }
        val catalog = ReaderOnlineCatalog(port,
            { id -> listOf(ReaderSourceEntry(id, "source", "", true, false, source().sourceVersion, null)) }, {})
        private val selection = catalog.select(lease, source().sourceId)
        val search = catalog.start(lease, selection, "Comic")
        val preview = catalog.preview(lease, search, catalog.page(lease, search, 1).books.single())
    }
}
