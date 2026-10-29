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
                val data = if (method == "runtime.setHostCapabilities") JSONObject().put("applied", true).put("capabilities", 1).put("lanes", 0)
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
                val data = if (method == "runtime.setHostCapabilities") JSONObject().put("applied", true).put("capabilities", 1).put("lanes", 0)
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
}
