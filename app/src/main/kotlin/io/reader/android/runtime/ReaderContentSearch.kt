package io.reader.android.runtime

import org.json.JSONObject
import java.util.UUID

data class ReaderContentPositionScope(
    val sourceId: String,
    val bookId: String,
    val chapterIndex: Int,
    val bodyVersion: String,
    val processingVersion: String,
)

data class ReaderContentSearchSession(
    val id: String,
    val generation: Long,
    val runtimeSession: String,
    val sourceId: String,
    val bookId: String,
    val query: String,
    val pageSize: Int,
)

data class ReaderContentSearchHit(
    val id: String,
    val sessionId: String,
    val sourceId: String,
    val bookId: String,
    val bookName: String,
    val chapterIndex: Int,
    val chapterTitle: String,
    val chapterOffset: Int,
    val matchLength: Int,
    val snippetStart: Int,
    val snippet: String,
    val positionScope: ReaderContentPositionScope,
)

data class ReaderContentSearchPage(
    val session: ReaderContentSearchSession,
    val results: List<ReaderContentSearchHit>,
    val hasMore: Boolean,
    val nextCursor: String?,
)

/** Retrying close(session) observes the original request; it never repeats the search. */
class ContentSearchPendingException(
    val session: ReaderContentSearchSession,
    val stage: String,
    cause: Throwable? = null,
) : Exception("CONTENT_SEARCH_PENDING: ${session.id}/$stage", cause)

/** Only validates the Core receipt and its scalar coordinates; it does not search source text. */
internal object ContentSearchReceiptPolicy {
    fun cursor(data: JSONObject): String? {
        if (!data.has("nextCursor") || data.isNull("nextCursor")) return null
        return string(data, "nextCursor").also {
            check(it.isNotEmpty() && it.toByteArray(Charsets.UTF_8).size <= 64) { "INVALID_CONTENT_SEARCH_CURSOR" }
        }
    }

    fun page(session: ReaderContentSearchSession, data: JSONObject): ReaderContentSearchPage {
        val hasMore = if (data.has("hasMore")) data.get("hasMore") as? Boolean
            ?: error("INVALID_CONTENT_SEARCH_HAS_MORE") else false
        val cursor = cursor(data)
        check(hasMore == (cursor != null)) { "CONTENT_SEARCH_CURSOR_PAGE_MISMATCH" }
        val rows = data.getJSONArray("results")
        check(rows.length() <= session.pageSize) { "CONTENT_SEARCH_PAGE_TOO_LARGE" }
        val hits = (0 until rows.length()).map { index ->
            val row = rows.getJSONObject(index)
            val scope = row.getJSONObject("positionScope")
            val chapter = scalar(row, "chapterIndex")
            ReaderContentSearchHit(
                id = "${session.id}:$chapter:${scalar(row, "chapterOffset")}",
                sessionId = session.id,
                sourceId = string(row, "sourceId"), bookId = string(row, "bookId"),
                bookName = string(row, "bookName"), chapterIndex = chapter,
                chapterTitle = string(row, "chapterTitle"), chapterOffset = scalar(row, "chapterOffset"),
                matchLength = scalar(row, "matchLength"), snippetStart = scalar(row, "snippetStart"),
                snippet = string(row, "snippet"),
                positionScope = ReaderContentPositionScope(string(scope, "sourceId"), string(scope, "bookId"),
                    scalar(scope, "chapterIndex"), string(scope, "bodyVersion"), string(scope, "processingVersion")),
            ).also { validateHit(session, it) }
        }
        check(hits.map { it.id }.toSet().size == hits.size) { "DUPLICATE_CONTENT_SEARCH_HIT" }
        return ReaderContentSearchPage(session, hits, hasMore, cursor)
    }

    fun validateHit(session: ReaderContentSearchSession, hit: ReaderContentSearchHit) {
        val scope = hit.positionScope
        check(hit.sessionId == session.id && hit.id == "${session.id}:${hit.chapterIndex}:${hit.chapterOffset}" &&
            hit.sourceId == session.sourceId && hit.bookId == session.bookId &&
            scope.sourceId == session.sourceId && scope.bookId == session.bookId &&
            hit.chapterIndex >= 0 && scope.chapterIndex == hit.chapterIndex &&
            scope.bodyVersion.isNotEmpty() && scope.processingVersion.isNotEmpty()) { "CONTENT_SEARCH_SCOPE_MISMATCH" }
        val snippetCount = hit.snippet.codePointCount(0, hit.snippet.length)
        check(hit.chapterOffset >= 0 && hit.snippetStart in 0..hit.chapterOffset && hit.matchLength > 0 &&
            hit.matchLength == session.query.codePointCount(0, session.query.length)) { "INVALID_CONTENT_SEARCH_SCALAR" }
        val relative = hit.chapterOffset - hit.snippetStart
        check(relative <= snippetCount && hit.matchLength <= snippetCount - relative) { "CONTENT_SEARCH_SNIPPET_RANGE" }
        check(scalarSlice(hit.snippet, relative, hit.matchLength) == session.query) { "CONTENT_SEARCH_SNIPPET_MATCH_CHANGED" }
    }

    fun jump(session: ReaderContentSearchSession, hit: ReaderContentSearchHit, chapter: ReaderChapter): Int {
        validateHit(session, hit)
        val scope = hit.positionScope
        check(chapter.bookId == scope.bookId && chapter.index == scope.chapterIndex &&
            chapter.bodyVersion == scope.bodyVersion && chapter.processingVersion == scope.processingVersion) {
            "CONTENT_SEARCH_TARGET_STALE"
        }
        check(hit.chapterOffset <= chapter.scalarCount && hit.matchLength <= chapter.scalarCount - hit.chapterOffset) {
            "CONTENT_SEARCH_CHAPTER_RANGE"
        }
        check(scalarSlice(chapter.text, hit.chapterOffset, hit.matchLength) == session.query) { "CONTENT_SEARCH_TARGET_STALE" }
        return hit.chapterOffset
    }

    private fun scalarSlice(text: String, start: Int, length: Int): String = text.substring(
        ReaderTextCoordinates.utf16Offset(text, start), ReaderTextCoordinates.utf16Offset(text, start + length))

    private fun string(row: JSONObject, field: String): String =
        row.get(field) as? String ?: error("INVALID_CONTENT_SEARCH_STRING: $field")

    private fun scalar(row: JSONObject, field: String): Int {
        val value = when (val raw = row.get(field)) {
            is Int -> raw.toLong()
            is Long -> raw
            else -> error("INVALID_CONTENT_SEARCH_INTEGER: $field")
        }
        check(value in 0..Int.MAX_VALUE.toLong()) { "CONTENT_SEARCH_SCALAR_OUT_OF_RANGE: $field" }
        return value.toInt()
    }
}

/** Uses ReaderLibrary's existing IO mutex and transport. Callbacks never nest a poll/request. */
internal class ReaderContentSearch(private val transport: CoreTransport) {
    private class Call(val closing: Boolean, val cursor: String?) {
        var requestId = 0L
        var terminal: JSONObject? = null
    }

    private class Search(val session: ReaderContentSearchSession) {
        var retired = false
        var nextCursor: String? = null
        var lastCursor: String? = null
        var snapshotClosed = false
        var pending: Call? = null
        var cleanupAttempted = false
        var cleanupFailure: Throwable? = null
    }

    private val runtimeSession = transport.session
    private val searches = linkedMapOf<String, Search>()
    private var current: Search? = null
    private var generation = 0L
    private var cursorCapability = false

    fun start(bookId: String, keyword: String, limit: Int = 50): ReaderContentSearchPage {
        checkRuntime()
        require(bookId.matches(Regex("local:[a-f0-9]{64}")) && keyword.isNotBlank() && limit in 1..200)
        current?.let { retire(it) }
        // Do not create another snapshot while the previous initial result is still unknown.
        searches.values.toList().filter { it.retired }.forEach { close(it.session) }
        if (!cursorCapability) {
            val capabilities = transport.request("core.info").getJSONArray("capabilities")
            check((0 until capabilities.length()).any { capabilities.get(it) == "search.content.cursor.v1" }) {
                "CONTENT_SEARCH_CURSOR_UNAVAILABLE"
            }
            cursorCapability = true
        }
        check(generation < Long.MAX_VALUE) { "CONTENT_SEARCH_GENERATION_EXHAUSTED" }
        val session = ReaderContentSearchSession(UUID.randomUUID().toString(), ++generation, runtimeSession,
            "local", bookId, keyword, limit)
        val search = Search(session)
        searches[session.id] = search
        current = search
        return requestPage(search, null)
    }

    fun next(session: ReaderContentSearchSession, cursor: String): ReaderContentSearchPage {
        val search = active(session)
        check(search.pending == null && search.nextCursor != null && cursor == search.nextCursor) {
            "CONTENT_SEARCH_CURSOR_RETIRED"
        }
        return requestPage(search, cursor)
    }

    fun validateJump(session: ReaderContentSearchSession, hit: ReaderContentSearchHit, chapter: ReaderChapter): Int {
        active(session)
        return ContentSearchReceiptPolicy.jump(session, hit, chapter)
    }

    fun close(session: ReaderContentSearchSession) {
        checkRuntime()
        check(session.runtimeSession == runtimeSession) { "CONTENT_SEARCH_RUNTIME_RETIRED" }
        val search = searches[session.id] ?: return // Already closed; never touches a newer session.
        check(search.session == session) { "CONTENT_SEARCH_SESSION_MISMATCH" }
        retire(search)
        val pending = search.pending
        if (pending != null && pending.terminal == null) {
            try { transport.drainOriginalTerminal(pending.requestId, 1_000) }
            catch (error: Exception) { throw ContentSearchPendingException(session, "original", error) }
        }
        consumeForCleanup(search)
        if (search.pending != null) throw ContentSearchPendingException(session, "original", search.cleanupFailure)
        try {
            if (!search.snapshotClosed) closeSnapshot(search)
        } catch (error: Exception) {
            throw ContentSearchPendingException(session, "close", error)
        }
        check(search.snapshotClosed) { "CONTENT_SEARCH_CLOSE_UNCONFIRMED" }
        searches.remove(session.id)
    }

    /** Called after the existing owner finishes polling, including unrelated library reads. */
    fun finishOwnerTurn() {
        searches.values.toList().forEach { search ->
            if (search.pending?.terminal != null) consumeForCleanup(search)
            if ((search.retired || search.nextCursor == null) && search.pending == null &&
                !search.snapshotClosed && !search.cleanupAttempted && search.lastCursor != null) {
                try { closeSnapshot(search) } catch (error: Exception) { search.cleanupFailure = error }
            }
            if (search.retired && search.snapshotClosed && search.pending == null) searches.remove(search.session.id)
        }
    }

    private fun requestPage(search: Search, cursor: String?): ReaderContentSearchPage {
        val call = Call(closing = false, cursor = cursor)
        val params = parameters(search).apply { if (cursor == null) put("useCursor", true) else put("cursor", cursor) }
        try {
            dispatch(search, call, params, 30_000)
            val page = consume(search) ?: error("CONTENT_SEARCH_ORIGINAL_RESULT_MISSING")
            // A continuation's final page omits nextCursor, but its snapshot still needs an explicit close.
            if (!page.hasMore && !search.snapshotClosed) closeSnapshot(search)
            return page
        } catch (error: Exception) {
            retire(search)
            finishOwnerTurn()
            if (!search.snapshotClosed || search.pending != null) {
                throw ContentSearchPendingException(search.session, if (search.cleanupAttempted) "close" else "search", error)
            }
            throw error
        }
    }

    private fun dispatch(search: Search, call: Call, params: JSONObject, timeoutMs: Long) {
        search.pending = call
        try {
            transport.request("search.content", params, timeoutMs,
                beforeSend = { call.requestId = it },
                terminal = { event -> call.terminal = JSONObject(event.toString()) })
        } catch (error: Exception) {
            if (call.requestId == 0L) {
                search.pending = null // The transport never admitted this command.
                if (!call.closing && call.cursor == null) search.snapshotClosed = true
            }
            throw error
        }
    }

    private fun consume(search: Search): ReaderContentSearchPage? {
        val call = search.pending ?: return null
        val event = call.terminal ?: return null
        search.pending = null
        check(call.requestId > 0 && event.getLong("requestId") == call.requestId) { "CONTENT_SEARCH_ORIGINAL_IDENTITY" }
        if (event.getString("type") == "error" && !call.closing && call.cursor == null &&
            event.getJSONObject("error").optJSONObject("details")?.optBoolean("originalPending") != true) {
            // Core's initial search guard closes its slot on error, before publishing the terminal.
            search.snapshotClosed = true
        }
        val data = NativeCoreTransport.result("search.content", event)
        if (call.closing) {
            check(data.getJSONArray("results").length() == 0 && ContentSearchReceiptPolicy.cursor(data) == null &&
                (!data.has("hasMore") || data.get("hasMore") == false)) { "INVALID_CONTENT_SEARCH_CLOSE_RECEIPT" }
            search.snapshotClosed = true
            search.nextCursor = null
            return null
        }
        // Save even a malformed page's known token before validating hit data, so it can be closed.
        search.lastCursor = ContentSearchReceiptPolicy.cursor(data) ?: call.cursor ?: search.lastCursor
        val page = ContentSearchReceiptPolicy.page(search.session, data)
        search.nextCursor = page.nextCursor
        if (!page.hasMore && call.cursor == null) search.snapshotClosed = true
        return page
    }

    private fun consumeForCleanup(search: Search) {
        try { consume(search) }
        catch (error: Exception) { search.cleanupFailure = error }
    }

    private fun closeSnapshot(search: Search) {
        if (search.snapshotClosed) return
        check(search.pending == null) { "CONTENT_SEARCH_ORIGINAL_PENDING" }
        val cursor = search.lastCursor ?: throw ContentSearchPendingException(search.session, "cursor")
        search.cleanupAttempted = true
        val call = Call(closing = true, cursor = cursor)
        dispatch(search, call, parameters(search).put("cursor", cursor).put("closeCursor", true), 3_000)
        consume(search)
        check(search.snapshotClosed) { "CONTENT_SEARCH_CLOSE_UNCONFIRMED" }
    }

    private fun parameters(search: Search): JSONObject = JSONObject().put("sourceId", search.session.sourceId)
        .put("bookId", search.session.bookId).put("keyword", search.session.query).put("maxResults", search.session.pageSize)

    private fun active(session: ReaderContentSearchSession): Search {
        checkRuntime()
        val search = searches[session.id]
        check(session.runtimeSession == runtimeSession && search != null && search.session == session &&
            current === search && !search.retired) { "CONTENT_SEARCH_SESSION_RETIRED" }
        return search
    }

    private fun retire(search: Search) {
        search.retired = true
        if (current === search) current = null
    }

    private fun checkRuntime() = check(transport.session == runtimeSession) { "CONTENT_SEARCH_RUNTIME_RETIRED" }
}
