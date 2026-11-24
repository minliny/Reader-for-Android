package io.reader.android.runtime

import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

/** The App-owned reading and catalog services with a protocol port. */
class ReaderOnlineRefreshTest {
    private val key = ShelfBookKey("source-a", "https://book.invalid/detail")
    private val lease = ReaderOperationOwner.ClientLease(UUID.randomUUID(), UUID.randomUUID(), 1, 1)
    private val version = "version-a"
    private val tocUrl = "https://book.invalid/issued-toc"
    private val chapterUrl = "https://book.invalid/chapter/2"
    private val processing = "a".repeat(64)

    @Test fun directoryRefreshRetainsFailedViewAndPublishesOnlyTheInstalledCatalog() {
        val fixture = Fixture()
        val before = fixture.reading.directory(lease, key)
        fixture.failNext = "book.toc"
        assertThrows(CoreCommandException::class.java) { fixture.reading.refreshDirectory(lease, before.directory) }
        assertEquals(2, fixture.reading.resolve(lease, before.nodes.single()).chapterIndex)

        val refreshed = fixture.reading.refreshDirectory(lease, before.directory)
        val request = fixture.calls.last { it.first == "book.toc" }.second
        assertEquals(tocUrl, request.getString("tocUrl"))
        assertEquals("cursor-a", request.getJSONObject("variables").getString("cursor"))
        assertEquals(listOf("First", "New chapter"), refreshed.nodes.map { it.title })
        assertEquals(1L, refreshed.unreadNewChapters)
        assertThrows(IllegalStateException::class.java) { fixture.reading.resolve(lease, before.nodes.single()) }
    }

    @Test fun explicitChapterRetryKeepsFailedEntryAndUsesOnlyTheCoreMappedVisibleScalar() {
        val fixture = Fixture()
        val before = fixture.reading.open(lease, key)
        assertEquals("甲😀乙", before.chapter.text)
        fixture.failNext = "chapter.content"
        assertThrows(CoreCommandException::class.java) { fixture.reading.refreshChapter(lease, before, 2) }
        assertEquals("甲😀乙", before.chapter.text)

        val refreshed = fixture.reading.refreshChapter(lease, before, 2)
        val request = fixture.calls.last { it.first == "chapter.content" }.second
        assertEquals(true, request.getBoolean("forceRefresh"))
        assertEquals(chapterUrl, request.getString("chapterUrl"))
        assertEquals("chapter-cursor", request.getJSONObject("variables").getString("cursor"))
        val context = request.getJSONObject("positionContext")
        assertEquals(before.chapter.bodyVersion, context.getString("bodyVersion"))
        assertEquals(before.chapter.processingVersion, context.getString("processingVersion"))
        assertEquals(2, context.getJSONArray("anchors").getJSONObject(0).getInt("offset"))
        assertEquals("新甲😀乙", refreshed.chapter.text)
        assertEquals(3, refreshed.restoreScalarOffset)
        assertThrows(IllegalStateException::class.java) { fixture.reading.refreshChapter(lease, before, 2) }
    }

    @Test fun cacheOnlyContinuationKeepsWorkingWithoutSourceAndMissingBodyDoesNotFetch() {
        val fixture = Fixture().apply { sourceEnabled = false }
        assertEquals(2, fixture.reading.open(lease, key).restoreScalarOffset)
        fixture.bodyMissing = true
        val missing = assertThrows(ReaderOnlineEntryUnavailable::class.java) { fixture.reading.open(lease, key) }
        assertEquals("contentMissing", missing.reason)
        assertFalse(fixture.calls.any { it.first in setOf("book.toc", "chapter.content", "reading.entry.prepare") })
    }

    private inner class Fixture {
        val calls = mutableListOf<Pair<String, JSONObject>>()
        var sourceEnabled = true
        var bodyMissing = false
        var failNext: String? = null
        private var updatedToc = false
        private var updatedBody = false
        private val oldBody = "b".repeat(64)
        private val newBody = "c".repeat(64)
        private fun scoped() = JSONObject().put("sourceId", key.sourceId).put("bookId", key.bookId)
        private fun scope() = scoped().put("chapterIndex", 2).put("bodyVersion", if (updatedBody) newBody else oldBody)
            .put("processingVersion", processing)
        private fun progress() = scope().put("chapterOffset", if (updatedBody) 3 else 2).put("updatedAt", 1)
        private fun directory() = scoped().put("status", "ready").put("viewId", "view-$updatedToc")
            .put("catalogRevision", "catalog-$updatedToc").put("structureRevision", "structure-$updatedToc")
        private fun node(id: String, title: String, index: Int) = JSONObject().put("nodeId", id).put("title", title)
            .put("kind", "target").put("chapterIndex", index)
        private fun cache() = scoped().put("sourceVersion", version).put("tocAvailable", true)
            .put("continuationVariables", JSONObject().put("cursor", "cursor-a"))
            .put("chapters", JSONArray().put(JSONObject().put("chapterIndex", 2).put("title", "First")
                .put("url", chapterUrl).put("navigable", true).put("cachedBytes", 18)
                .put("variables", JSONObject().put("cursor", "chapter-cursor"))))
        private val transport = object : CoreTransport {
            override val session = "runtime-a"
            override fun request(method: String, params: JSONObject, timeoutMs: Long, beforeSend: (Long) -> Unit,
                terminal: ((JSONObject) -> Unit)?): JSONObject {
                calls += method to JSONObject(params.toString())
                val id = calls.size.toLong(); beforeSend(id)
                val event = JSONObject().put("protocolVersion", 1).put("requestId", id)
                if (method == failNext) {
                    failNext = null
                    val error = JSONObject().put("code", "SOURCE_NETWORK_FAILURE")
                    terminal?.invoke(event.put("type", "error").put("error", error))
                    throw CoreCommandException(method, error)
                }
                val data = when (method) {
                    "runtime.setHostCapabilities" -> JSONObject().put("applied", true).put("capabilities", 2).put("lanes", 0)
                    "bookshelf.get" -> JSONObject().put("book", scoped().put("unreadCount", if (updatedToc) 1 else 0))
                    "cache.book.status" -> cache()
                    "search-book.get" -> JSONObject().put("book", JSONObject().put("origin", key.sourceId)
                        .put("bookUrl", key.bookId).put("tocUrl", tocUrl))
                    "book.toc" -> scoped().put("sourceVersion", version).put("catalogInstalled", true).also { updatedToc = true }
                    "reading.directory.view.open.v2" -> directory().put("nodes", JSONArray().put(node("n2", "First", 2)).apply {
                        if (updatedToc) put(node("n5", "New chapter", 5))
                    })
                    "reading.directory.target.resolve.v2" -> directory().put("kind", "chapterStart").put("nodeId", "n2")
                        .put("chapterIndex", 2).put("directoryTargetProof", scoped().put("catalogRevision", "catalog-$updatedToc")
                            .put("structureRevision", "structure-$updatedToc").put("nodeId", "n2").put("chapterIndex", 2).put("url", chapterUrl))
                    "reading.entry.snapshot" -> if (bodyMissing) scoped().put("kind", "missing").put("reason", "contentMissing") else
                        scoped().put("kind", "ready").put("chapterIndex", 2).put("chapterTitle", "First")
                            .put("positionScope", scope()).put("progress", progress()).put("content", if (updatedBody) "新甲😀乙" else "甲😀乙")
                            .put("blocks", JSONArray().put(JSONObject().put("kind", "text"))).put("baseUrl", JSONObject.NULL)
                    "reading.progress.get" -> JSONObject().put("found", true).put("progress", progress())
                        .put("progressRevision", "reader-progress-v1:" + (if (updatedBody) "f" else "e").repeat(64))
                    "chapter.content" -> {
                        updatedBody = true
                        scoped().put("bodyVersion", newBody).put("processingVersion", processing)
                            .put("positionMigration", JSONObject().put("status", "committed")
                                .put("previousBodyVersion", oldBody).put("previousProcessingVersion", processing)
                                .put("bodyVersion", newBody).put("processingVersion", processing)
                                .put("anchors", JSONArray().put(JSONObject().put("id", "visible").put("previousOffset", 2).put("offset", 3))))
                    }
                    else -> error("Unexpected $method")
                }
                terminal?.invoke(event.put("type", "result").put("data", data))
                return data
            }
            override fun drainOriginalTerminal(requestId: Long, timeoutMs: Long) = error("Unexpected drain")
            override fun close() = error("Unexpected close")
        }
        private val sources: (String) -> List<ReaderSourceEntry> = { id ->
            listOf(ReaderSourceEntry(id, "source", "", sourceEnabled, false, version, null))
        }
        val reading = ReaderOnlineReading(transport, ReaderOnlineCatalog(transport, sources, {}), sources, {}, {})
    }
}
