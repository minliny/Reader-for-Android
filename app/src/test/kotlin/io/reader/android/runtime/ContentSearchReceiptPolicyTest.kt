package io.reader.android.runtime

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentSearchReceiptPolicyTest {
    private val book = "local:" + "a".repeat(64)
    private val session = ReaderContentSearchSession("search-original", 7, "runtime-original", "local", book, "😀书", 20)
    private val chapter = ReaderChapter(book, 2, "Chapter", "首甲😀书乙尾", "body-original", "processing-original")

    private fun row() = JSONObject().put("sourceId", "local").put("bookId", book).put("bookName", "Book")
        .put("chapterIndex", 2).put("chapterTitle", "Chapter").put("chapterOffset", 2)
        .put("matchLength", 2).put("snippetStart", 1).put("snippet", "甲😀书乙")
        .put("positionScope", JSONObject().put("sourceId", "local").put("bookId", book).put("chapterIndex", 2)
            .put("bodyVersion", "body-original").put("processingVersion", "processing-original"))

    private fun page(row: JSONObject = row(), cursor: String? = null) = JSONObject()
        .put("results", JSONArray().put(row)).apply { cursor?.let { put("hasMore", true).put("nextCursor", it) } }

    @Test fun scalarReceiptAndJumpKeepUnicodeScope() {
        val result = ContentSearchReceiptPolicy.page(session, page())
        val hit = result.results.single()
        assertEquals(2, ContentSearchReceiptPolicy.jump(session, hit, chapter))
        assertEquals(2, hit.matchLength)
        assertEquals(1, hit.snippetStart)
        assertFalse(result.hasMore)
        assertEquals(null, result.nextCursor)
        for (changed in listOf(chapter.copy(bookId = "other"), chapter.copy(index = 3),
            chapter.copy(bodyVersion = "new"), chapter.copy(processingVersion = "new"), chapter.copy(text = "短"))) {
            assertThrows(IllegalStateException::class.java) { ContentSearchReceiptPolicy.jump(session, hit, changed) }
        }
    }

    @Test fun foreignScopeAndCoercedOrOverflowingScalarsAreRejected() {
        for (field in listOf("sourceId", "bookId", "chapterIndex", "bodyVersion", "processingVersion")) {
            val changed = row().also { it.getJSONObject("positionScope").put(field, if (field == "chapterIndex") 3 else "") }
            assertThrows(Exception::class.java) { ContentSearchReceiptPolicy.page(session, page(changed)) }
        }
        for (field in listOf("chapterIndex", "chapterOffset", "matchLength", "snippetStart")) {
            for (invalid in listOf<Any>(-1, Int.MAX_VALUE.toLong() + 1, "2", 2.0)) {
                assertThrows(IllegalStateException::class.java) {
                    ContentSearchReceiptPolicy.page(session, page(row().put(field, invalid)))
                }
            }
        }
        assertThrows(Exception::class.java) {
            ContentSearchReceiptPolicy.page(session, page(row().also { it.remove("positionScope") }))
        }
    }

    @Test fun snippetMustContainTheExactCoreScalarMatch() {
        for (changed in listOf(row().put("snippetStart", 3), row().put("matchLength", 1),
            row().put("snippet", "甲😀"), row().put("snippet", "甲😀字乙"))) {
            assertThrows(IllegalStateException::class.java) { ContentSearchReceiptPolicy.page(session, page(changed)) }
        }
        val hit = ContentSearchReceiptPolicy.page(session, page()).results.single()
        assertThrows(IllegalStateException::class.java) {
            ContentSearchReceiptPolicy.jump(session.copy(id = "new-query"), hit, chapter)
        }
    }

    @Test fun cursorAndPageReceiptMustAgree() {
        assertEquals("opaque:1", ContentSearchReceiptPolicy.page(session, page(cursor = "opaque:1")).nextCursor)
        for (invalid in listOf(page().put("hasMore", true), page().put("nextCursor", "opaque:1"),
            page().put("hasMore", "true"), page(cursor = ""), page(cursor = "x".repeat(65)))) {
            assertThrows(IllegalStateException::class.java) { ContentSearchReceiptPolicy.page(session, invalid) }
        }
        assertThrows(IllegalStateException::class.java) {
            ContentSearchReceiptPolicy.page(session.copy(pageSize = 1), page().put("results", JSONArray().put(row()).put(row())))
        }
    }

    @Test fun continuationUsesExactCursorAndFinalPageExplicitlyCloses() {
        val transport = FakeTransport()
        transport.replies += page(cursor = "opaque:1")
        transport.replies += page(cursor = "opaque:2")
        transport.replies += page()
        transport.replies += JSONObject().put("results", JSONArray())
        val service = ReaderContentSearch(transport)
        val first = service.start(book, session.query, 20)
        val second = service.next(first.session, "opaque:1")
        val beforeStale = transport.calls.size
        assertThrows(IllegalStateException::class.java) { service.next(first.session, "opaque:1") }
        assertThrows(IllegalStateException::class.java) {
            service.next(first.session.copy(runtimeSession = "other-runtime"), "opaque:2")
        }
        assertEquals(beforeStale, transport.calls.size)
        val final = service.next(second.session, "opaque:2")
        assertFalse(final.hasMore)
        assertEquals(2, service.validateJump(final.session, final.results.single(), chapter))
        val commands = transport.calls.filter { it.method == "search.content" }.map { it.params }
        assertTrue(commands.first().getBoolean("useCursor"))
        assertEquals("opaque:1", commands[1].getString("cursor"))
        assertEquals("opaque:2", commands[2].getString("cursor"))
        assertEquals("opaque:2", commands[3].getString("cursor"))
        assertTrue(commands[3].getBoolean("closeCursor"))
        commands.forEach { assertFalse(it.has("offset")); assertEquals(20, it.getInt("maxResults")) }
    }

    @Test fun unknownInitialOutcomeOnlyDrainsAndClosesOriginal() {
        val transport = FakeTransport()
        transport.replies += null // Admitted, but its original terminal has not arrived.
        val service = ReaderContentSearch(transport)
        val pending = assertThrows(ContentSearchPendingException::class.java) { service.start(book, session.query, 20) }
        val original = transport.calls.last()
        assertThrows(ContentSearchPendingException::class.java) { service.close(pending.session) }
        assertEquals(2, transport.calls.size) // core.info and exactly one initial search.
        transport.late(original.id, page(cursor = "original:1"))
        transport.replies += JSONObject().put("results", JSONArray())
        service.finishOwnerTurn()
        service.close(pending.session)
        assertEquals(3, transport.calls.size)
        assertEquals("original:1", transport.calls.last().params.getString("cursor"))
        assertTrue(transport.calls.last().params.getBoolean("closeCursor"))
        assertFalse(transport.calls.last().params.has("useCursor"))
        assertTrue(transport.calls.all { !it.params.has("offset") })
    }

    @Test fun unknownContinuationIsRetiredWithoutCursorReplay() {
        val transport = FakeTransport()
        transport.replies += page(cursor = "original:1")
        transport.replies += null
        val service = ReaderContentSearch(transport)
        val first = service.start(book, session.query, 20)
        val pending = assertThrows(ContentSearchPendingException::class.java) { service.next(first.session, "original:1") }
        val originalNext = transport.calls.last()
        assertThrows(IllegalStateException::class.java) { service.next(first.session, "original:1") }
        assertThrows(IllegalStateException::class.java) { service.validateJump(first.session, first.results.single(), chapter) }
        assertThrows(ContentSearchPendingException::class.java) { service.start(book, "new query", 20) }
        assertEquals(3, transport.calls.size)
        transport.late(originalNext.id, page(cursor = "original:2"))
        transport.replies += JSONObject().put("results", JSONArray())
        service.close(pending.session)
        assertEquals("original:2", transport.calls.last().params.getString("cursor"))
        assertEquals(4, transport.calls.size)
        service.close(first.session) // An old close cannot dispatch against another session.
        assertEquals(4, transport.calls.size)
    }

    private class FakeTransport : CoreTransport {
        data class Dispatch(val id: Long, val method: String, val params: JSONObject)
        override val session = "runtime-test"
        val calls = mutableListOf<Dispatch>()
        val replies = mutableListOf<JSONObject?>()
        private val originals = mutableMapOf<Long, (JSONObject) -> Unit>()
        private var nextId = 100L

        override fun request(method: String, params: JSONObject, timeoutMs: Long, beforeSend: (Long) -> Unit,
            terminal: ((JSONObject) -> Unit)?): JSONObject {
            val id = nextId++
            beforeSend(id)
            calls += Dispatch(id, method, JSONObject(params.toString()))
            if (method == "core.info") return JSONObject().put("capabilities", JSONArray().put("search.content.cursor.v1"))
            val data = replies.removeAt(0)
            if (data == null) {
                originals[id] = checkNotNull(terminal)
                throw PendingOperationException("$session:$id", method)
            }
            terminal?.invoke(event(id, data))
            return data
        }

        fun late(id: Long, data: JSONObject) { checkNotNull(originals.remove(id)).invoke(event(id, data)) }
        override fun drainOriginalTerminal(requestId: Long, timeoutMs: Long) = Unit
        override fun close() = Unit
        private fun event(id: Long, data: JSONObject) = JSONObject().put("protocolVersion", 1)
            .put("requestId", id).put("type", "result").put("data", data)
    }
}
