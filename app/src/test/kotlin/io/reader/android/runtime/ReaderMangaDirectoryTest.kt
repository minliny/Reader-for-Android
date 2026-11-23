package io.reader.android.runtime

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

/** Native DTO/original-call owner tests only. No source execution, shelf, Native, network or device evidence. */
class ReaderMangaDirectoryTest {
    @Test fun previewTocThenNativeViewKeepsGroupsSparseIndexesAndSurvivesSearchClose() {
        val f = Fixture(); val first = f.open()
        assertEquals(listOf("group", "target"), first.nodes.map { it.kind })
        assertEquals(listOf(null, 9), first.nodes.map { it.chapterIndex })
        assertEquals(2, first.nextOffset)
        f.catalog.close(f.search)
        val second = f.owner.next(f.lease, first.directory, 2)
        assertSame(first.directory, second.directory)
        assertEquals(listOf("disabled", "target"), second.nodes.map { it.kind })
        assertEquals(listOf(null, 42), second.nodes.map { it.chapterIndex }); assertNull(second.nextOffset)
        val request = f.owner.resolve(f.lease, second.nodes.last())
        assertEquals(42, request.chapterIndex)
        assertEquals("https://example.invalid/chapter/42", request.directoryTarget!!.url)
        assertEquals("rules-1", request.directoryTarget!!.sourceRuleVersion)
        assertEquals(1, f.calls.count { it.first == "book.toc" })
        assertFalse(f.calls.any { it.first in setOf("bookshelf.get", "bookshelf.add", "reading.entry.snapshot", "chapter.content") })
        assertEquals(listOf("book.toc", "reading.directory.view.open.v2", "reading.directory.view.page.v2", "reading.directory.target.resolve.v2"),
            f.calls.map { it.first }.takeLast(4))
    }

    @Test fun groupDisabledForgedNodeAndWrongOffsetNeverDispatchAnotherNativeCall() {
        val f = Fixture(); val first = f.open(); val second = f.owner.next(f.lease, first.directory, 2)
        val target = first.nodes.last(); val forged = ReaderMangaDirectoryNode(first.directory, target.nodeId, target.title, target.kind, target.chapterIndex)
        val count = f.calls.size
        listOf(first.nodes.first(), second.nodes.first(), forged).forEach { node ->
            assertThrows(IllegalStateException::class.java) { f.owner.resolve(f.lease, node) }
        }
        assertThrows(IllegalStateException::class.java) { f.owner.next(f.lease, first.directory, 2) }
        assertEquals(count, f.calls.size)
        assertNotNull(f.owner.resolve(f.lease, target).directoryTarget)
    }

    @Test fun resolvedOriginalProofIsTransmittedAndReturnedChapterMustMatchUrlIndexAndRules() {
        val f = Fixture(); val first = f.open(); val request = f.owner.resolve(f.lease, first.nodes.last())
        f.owner.requireTarget(f.lease, request)
        val result = ReaderMangaService(f.transport).chapter(request)
        assertEquals(request.directoryTarget!!.url, result.manifest.chapter.chapterId)
        val sent = f.calls.last().second.getJSONObject("directoryTargetProof")
        assertEquals(JSONObject(request.directoryTarget!!.proofJson).toString(), sent.toString())
        for (drift in listOf<(JSONObject) -> Unit>(
            { it.put("chapterIndex", 42) },
            { it.getJSONObject("manifest").getJSONObject("chapter").put("chapterId", "https://example.invalid/foreign") },
            { it.getJSONObject("manifest").put("sourceRuleVersion", "rules-foreign") },
            { it.getJSONObject("manifest").getJSONObject("chapter").put("sourceId", "foreign") },
        )) {
            f.chapterChange = drift
            assertThrows(IllegalStateException::class.java) { ReaderMangaService(f.transport).chapter(request) }
        }
    }

    @Test fun forgedTargetOrCopiedForeignRequestCannotUseIssuedDirectoryAuthority() {
        val f = Fixture(); val first = f.open(); val request = f.owner.resolve(f.lease, first.nodes.last())
        val original = checkNotNull(request.directoryTarget)
        val forged = ReaderMangaDirectoryTarget(original.directory, original.nodeId, original.chapterIndex, original.url, original.sourceRuleVersion, original.proofJson)
        assertThrows(IllegalStateException::class.java) { f.owner.requireTarget(f.lease, request.copy(directoryTarget = forged)) }
        assertThrows(IllegalStateException::class.java) { f.owner.requireTarget(f.lease, request.copy(chapterIndex = 42)) }
        val foreign = request.copy(chapter = ReaderMangaChapterRef("other", "book"))
        val count = f.calls.size
        assertThrows(IllegalStateException::class.java) { ReaderMangaService(f.transport).chapter(foreign) }
        assertEquals(count, f.calls.size)
    }

    @Test fun newClientRuntimeSourceVersionAndReaderRetirementRevokeOldObjects() {
        val f = Fixture(); val first = f.open(); val request = f.owner.resolve(f.lease, first.nodes.last())
        val count = f.calls.size
        val newer = f.lease.copy(ownerGeneration = f.lease.ownerGeneration + 1)
        assertThrows(IllegalStateException::class.java) { f.owner.requireTarget(newer, request) }
        f.runtime = "runtime-2"
        assertThrows(IllegalStateException::class.java) { f.owner.requireTarget(f.lease, request) }
        f.runtime = "runtime-1"; f.version = "rules-2"
        assertThrows(IllegalStateException::class.java) { f.owner.requireTarget(f.lease, request) }
        f.version = "rules-1"; f.admitted = false
        assertThrows(IllegalStateException::class.java) { f.owner.requireTarget(f.lease, request) }
        f.admitted = true; f.owner.retireAll()
        assertThrows(IllegalStateException::class.java) { f.owner.requireTarget(f.lease, request) }
        assertThrows(IllegalStateException::class.java) { f.owner.reopen(f.lease, first.directory, 9) }
        assertEquals(count, f.calls.size)
    }

    @Test fun changedNativeRevisionInvalidatesTargetsButAllowsOnlyExplicitSameScopeReopen() {
        val f = Fixture(); val first = f.open(); val request = f.owner.resolve(f.lease, first.nodes.last())
        f.catalogRevision = "catalog-2"
        val unavailable = assertThrows(ReaderMangaDirectoryUnavailable::class.java) { f.owner.next(f.lease, first.directory, 2) }
        assertSame(first.directory, unavailable.directory); assertTrue(unavailable.cause is IllegalStateException)
        assertThrows(IllegalStateException::class.java) { f.owner.requireTarget(f.lease, request) }
        val before = f.calls.size
        val reopened = f.owner.reopen(f.lease, first.directory, 9)
        assertEquals("catalog-2", reopened.directory.catalogRevision); assertNotSame(first.directory, reopened.directory)
        assertEquals(listOf("reading.directory.view.open.v2"), f.calls.drop(before).map { it.first })
        assertEquals(1, f.calls.count { it.first == "book.toc" })
        assertThrows(IllegalStateException::class.java) { f.owner.reopen(f.lease, first.directory, 9) }
        f.owner.close(reopened.directory)
        assertThrows(IllegalStateException::class.java) { f.owner.reopen(f.lease, reopened.directory, 9) }
    }

    @Test fun forgedReopenAndChangedProofDoNotAcquireChapterOrResurrectClosedTargets() {
        val f = Fixture(); val first = f.open(); val real = first.directory
        val forged = ReaderMangaDirectory(real.id, real.chapter, real.sourceVersion, real.viewId, real.catalogRevision,
            real.structureRevision, real.owner, real.lease, real.runtime)
        assertThrows(IllegalStateException::class.java) { f.owner.reopen(f.lease, forged, 9) }
        f.proofChange = { it.put("url", "") }
        val unavailable = assertThrows(ReaderMangaDirectoryUnavailable::class.java) { f.owner.resolve(f.lease, first.nodes.last()) }
        assertSame(real, unavailable.directory); assertTrue(unavailable.cause is IllegalStateException)
        assertThrows(IllegalStateException::class.java) { f.owner.resolve(f.lease, first.nodes.last()) }
        assertFalse(f.calls.any { it.first == "manga.chapter.get" })
        f.version = "rules-new"
        val count = f.calls.size
        assertThrows(IllegalStateException::class.java) { f.owner.reopen(f.lease, real, 9) }
        assertEquals(count, f.calls.size)
        val pendingFixture = Fixture(); val pendingPage = pendingFixture.open()
        val originalPending = CoreProtocolPending(CoreProtocolReference("runtime-1", "directory-original", 17L, "request-token"),
            CoreProtocolStage.ORIGINAL_UNCERTAIN)
        pendingFixture.afterResponse = { method -> if (method == "reading.directory.target.resolve.v2") throw originalPending }
        val originalUnavailable = assertThrows(ReaderMangaDirectoryUnavailable::class.java) {
            pendingFixture.owner.resolve(pendingFixture.lease, pendingPage.nodes.last())
        }
        assertSame(pendingPage.directory, originalUnavailable.directory)
        assertSame(originalPending, originalUnavailable.cause)
        assertSame(originalPending, originalUnavailable.coreProtocolPending())
        assertEquals(1, pendingFixture.catalog.pending().size)
    }

    @Test fun lateReaderRetirementRejectsPublicationAfterTheActualOriginalCompletes() {
        val f = Fixture()
        f.afterResponse = { method -> if (method == "reading.directory.view.open.v2") f.owner.retireAll() }
        assertThrows(IllegalStateException::class.java) { f.open() }
        assertEquals(1, f.calls.count { it.first == "reading.directory.view.open.v2" })
        assertTrue(f.catalog.pending().isEmpty()) // The original result was consumed; no fake retry/close was dispatched.
    }

    private class Fixture {
        val lease = ReaderOperationOwner.ClientLease(UUID.randomUUID(), UUID.randomUUID(), 1, 1)
        val calls = mutableListOf<Pair<String, JSONObject>>()
        var runtime = "runtime-1"; var version = "rules-1"; var admitted = true
        var catalogRevision = "catalog-1"
        var chapterChange: (JSONObject) -> Unit = {}
        var proofChange: (JSONObject) -> Unit = {}
        var afterResponse: (String) -> Unit = {}
        private val ref = ReaderMangaChapterRef("source-a", "book-a")
        val transport = object : CoreTransport {
            override val session get() = runtime
            override fun request(method: String, params: JSONObject, timeoutMs: Long, beforeSend: (Long) -> Unit,
                terminal: ((JSONObject) -> Unit)?): JSONObject {
                calls += method to JSONObject(params.toString()); val id = calls.size.toLong(); beforeSend(id)
                val value = response(method, params); afterResponse(method)
                terminal?.invoke(JSONObject().put("protocolVersion", 1).put("requestId", id).put("type", "result").put("data", value))
                return value
            }
            override fun drainOriginalTerminal(requestId: Long, timeoutMs: Long) = error("NO_DRAIN")
            override fun close() = error("NO_NATIVE_CLOSE")
        }
        private fun sources(id: String) = listOf(ReaderSourceEntry(id, "source", "https://example.invalid", true, false, version, null))
        val catalog = ReaderOnlineCatalog(transport, ::sources, {})
        val owner = ReaderMangaDirectoryOwner(transport, catalog, ::sources, {}, { check(admitted) { "RETIRED_CLIENT" } }, pageLimit = 2)
        private val selection = catalog.select(lease, ref.sourceId)
        val search = catalog.start(lease, selection, "Comic")
        private val preview = catalog.preview(lease, search, catalog.page(lease, search, 1).books.single())
        fun open(): ReaderMangaDirectoryPage {
            val request = catalog.mangaToc(lease, preview)
            return owner.open(lease, request, preview.sourceVersion)
        }
        private fun node(id: String, kind: String, index: Int? = null) = JSONObject().put("nodeId", id).put("title", id)
            .put("kind", kind).put("chapterIndex", index ?: JSONObject.NULL).put("depth", if (kind == "group") 0 else 1)
            .put("hasChildren", kind == "group").put("expanded", kind == "group")
        private fun identity() = JSONObject().put("sourceId", ref.sourceId).put("bookId", ref.bookId)
            .put("viewId", "native-view").put("catalogRevision", catalogRevision).put("structureRevision", "structure-1")
        private fun response(method: String, params: JSONObject): JSONObject {
            val book = JSONObject().put("bookId", ref.bookId).put("title", "Comic").put("author", "Author")
            return when (method) {
                "runtime.setHostCapabilities" -> JSONObject().put("applied", true).put("capabilities", 2).put("lanes", 0)
                "book.search" -> JSONObject().put("sourceId", ref.sourceId).put("sourceVersion", version).put("contentKind", "manga").put("books", JSONArray().put(book))
                "book.detail" -> JSONObject().put("sourceId", ref.sourceId).put("sourceVersion", version).put("contentKind", "manga").put("book", book)
                    .put("tocUrl", "https://example.invalid/toc").put("variables", JSONObject())
                "book.toc" -> JSONObject().put("sourceId", ref.sourceId).put("bookId", ref.bookId).put("sourceVersion", version)
                    .put("readableChapterIndexes", JSONArray().put(9).put(42)).put("toc", JSONArray()
                        .put(JSONObject().put("index", 0).put("title", "Volume").put("url", "https://example.invalid/volume"))
                        .put(JSONObject().put("index", 9).put("title", "Nine").put("url", "https://example.invalid/chapter/9"))
                        .put(JSONObject().put("index", 42).put("title", "Forty-two").put("url", "https://example.invalid/chapter/42")))
                "reading.directory.view.open.v2" -> identity().put("status", "ready").put("visibleTotal", 4)
                    .put("nodes", JSONArray().put(node("volume", "group")).put(node("nine", "target", 9))).put("nextOffset", 2)
                "reading.directory.view.page.v2" -> identity().put("visibleTotal", 4)
                    .put("nodes", JSONArray().put(node("disabled", "disabled")).put(node("forty-two", "target", 42))).put("nextOffset", JSONObject.NULL)
                "reading.directory.target.resolve.v2" -> {
                    val index = if (params.getString("nodeId") == "nine") 9 else 42
                    val proof = JSONObject().put("sourceId", ref.sourceId).put("bookId", ref.bookId).put("catalogRevision", catalogRevision)
                        .put("structureRevision", "structure-1").put("nodeId", params.getString("nodeId")).put("chapterIndex", index)
                        .put("url", "https://example.invalid/chapter/$index").also(proofChange)
                    identity().put("nodeId", params.getString("nodeId")).put("kind", "chapterStart").put("chapterIndex", index).put("directoryTargetProof", proof)
                }
                "manga.chapter.get" -> JSONObject().put("chapterIndex", params.getInt("chapterIndex")).put("chapterTitle", "Chapter").put("cached", true)
                    .put("manifest", JSONObject().put("chapter", JSONObject().put("sourceId", ref.sourceId).put("bookId", ref.bookId)
                        .put("chapterId", "https://example.invalid/chapter/${params.getInt("chapterIndex")}"))
                        .put("sourceRuleVersion", "rules-1").put("manifestVersion", "manifest-1").put("decodeRevision", "identity-v1")
                        .put("pages", JSONArray().put(JSONObject().put("pageId", "page-0").put("ordinal", 0).put("resourceRef", "manga:page-0"))))
                    .also(chapterChange)
                else -> error("UNEXPECTED_DIRECTORY_CALL:$method")
            }
        }
    }
}
