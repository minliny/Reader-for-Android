package io.reader.android.runtime

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Core manga descriptor boundary only; no image request, decode, cache or device path. */
class ReaderMangaServiceTest {
    private val chapter = ReaderMangaChapterRef("source-a", "book-a")

    @Test
    fun pagesExtractDispatchesExactCoreParamsAndPreservesPageRequestSemantics() {
        val calls = mutableListOf<Pair<String, JSONObject>>()
        val service = ReaderMangaService(fakeTransport { method, params ->
            calls += method to params
            JSONObject().put("sourceId", chapter.sourceId).put("bookId", chapter.bookId)
                .put("chapterTitle", "第一话").put("pages", JSONArray()
                    .put(JSONObject().put("url", "https://img.test/1.jpg").put("referrer", "https://img.test/chapter")
                        .put("headers", JSONObject().put("User-Agent", "Reader")))
                    .put(JSONObject().put("url", "https://img.test/2.jpg").put("requestRule", "{\"method\":\"POST\"}"))
                    .put(JSONObject().put("url", "https://img.test/2.jpg").put("requestRule", "{\"method\":\"POST\"}")))
        })
        val result = service.pagesExtract(ReaderMangaExtractRequest(
            chapter = chapter,
            chapterTitle = "第一话",
            chapterResponse = "<html>images</html>",
            chapterUrl = "https://img.test/chapter",
            variables = mapOf("token" to "v1"),
        ))
        assertEquals("manga.pages.extract", calls.single().first)
        val params = calls.single().second
        assertEquals("<html>images</html>", params.getString("chapterResponse"))
        assertEquals("https://img.test/chapter", params.getString("chapterUrl"))
        assertEquals("v1", params.getJSONObject("variables").getString("token"))
        assertEquals("第一话", result.chapterTitle)
        assertEquals(3, result.pages.size)
        assertEquals("{\"method\":\"POST\"}", result.pages[1].requestRule)
        assertEquals(result.pages[1], result.pages[2])
        assertEquals("Reader", result.pages[0].headers["User-Agent"])
    }

    @Test
    fun pagesExtractRejectsForeignIdentityBlankUrlAndNonStringHeader() {
        val foreign = JSONObject().put("sourceId", "other").put("bookId", chapter.bookId)
            .put("pages", JSONArray())
        assertThrows(IllegalStateException::class.java) { ReaderMangaPolicy.pagesExtract(foreign, chapter) }

        val blank = JSONObject().put("sourceId", chapter.sourceId).put("bookId", chapter.bookId)
            .put("pages", JSONArray().put(JSONObject().put("url", " ")))
        assertThrows(IllegalStateException::class.java) { ReaderMangaPolicy.pagesExtract(blank, chapter) }

        val nonStringHeader = JSONObject().put("sourceId", chapter.sourceId).put("bookId", chapter.bookId)
            .put("pages", JSONArray().put(JSONObject().put("url", "https://img.test/1.jpg")
                .put("headers", JSONObject().put("X-Count", 1))))
        assertThrows(IllegalStateException::class.java) { ReaderMangaPolicy.pagesExtract(nonStringHeader, chapter) }

        val nonObjectHeaders = JSONObject().put("sourceId", chapter.sourceId).put("bookId", chapter.bookId)
            .put("pages", JSONArray().put(JSONObject().put("url", "https://img.test/1.jpg").put("headers", 1)))
        assertThrows(IllegalStateException::class.java) { ReaderMangaPolicy.pagesExtract(nonObjectHeaders, chapter) }
    }

    @Test
    fun explicitNullOptionalPageFieldsAndReaderLibraryObserverLaneRemainAdmitted() {
        val empty = JSONObject().put("sourceId", chapter.sourceId).put("bookId", chapter.bookId)
            .put("pages", JSONArray().put(JSONObject().put("url", "https://img.test/1.jpg")
                .put("requestRule", JSONObject.NULL).put("referrer", JSONObject.NULL)))
        val result = ReaderMangaPolicy.pagesExtract(empty, chapter)
        assertEquals(null, result.pages.single().requestRule)
        assertEquals(null, result.pages.single().referrer)
        val source = sequenceOf(
            File("src/main/kotlin/io/reader/android/runtime/ReaderLibrary.kt"),
            File("app/src/main/kotlin/io/reader/android/runtime/ReaderLibrary.kt"),
        ).first { it.isFile }.readText()
        assertTrue("ReaderLibrary must expose the A6 Core adapter" , "mangaPagesExtract" in source)
        assertTrue("A6 extraction must use observer-read lane", "onIO(WholeTurnMode.OBSERVER_READ) { mangaService.pagesExtract" in source)
        assertTrue("manga chapter acquisition may issue Host HTTP and must retain its original",
            "mangaDirectory.requireTarget(client.lease, request); mangaService.chapter(request)" in source)
    }

    @Test
    fun chapterGetBindsManifestAndPagesWindowBindsAbsoluteOrdinals() {
        val chapterIdentity = ReaderMangaChapterIdentity("source-a", "book-a", "chapter-7")
        val calls = mutableListOf<Pair<String, JSONObject>>()
        val service = ReaderMangaService(fakeTransport { method, params ->
            calls += method to params
            val manifest = JSONObject().put("chapter", JSONObject().put("sourceId", "source-a")
                .put("bookId", "book-a").put("chapterId", "chapter-7"))
                .put("sourceRuleVersion", "rules-1").put("manifestVersion", "manifest-1")
                .put("decodeRevision", "decode-1").put("pages", JSONArray()
                    .put(JSONObject().put("pageId", "page-0").put("ordinal", 0).put("resourceRef", "manga:page-0"))
                    .put(JSONObject().put("pageId", "page-1").put("ordinal", 1).put("resourceRef", "manga:page-1")))
            when (method) {
                "manga.chapter.get" -> JSONObject().put("manifest", manifest).put("chapterIndex", 7)
                    .put("chapterTitle", "第七话").put("cached", false).put("resources", JSONArray()
                        .put(JSONObject().put("resourceRef", "manga:page-0")
                            .put("request", JSONObject().put("url", "https://img.test/0.jpg").put("sourcePageId", "p0"))))
                else -> JSONObject().put("manifest", JSONObject().put("chapter", JSONObject().put("sourceId", "source-a")
                    .put("bookId", "book-a").put("chapterId", "chapter-7"))
                    .put("sourceRuleVersion", "rules-1").put("manifestVersion", "manifest-1")
                    .put("decodeRevision", "decode-1").put("pages", JSONArray()
                        .put(JSONObject().put("pageId", "page-1").put("ordinal", 1).put("resourceRef", "manga:page-1"))))
                    .put("chapterIndex", 7).put("chapterTitle", "第七话").put("cached", true)
                    .put("pageStart", 1).put("totalPages", 2).put("resources", JSONArray()
                        .put(JSONObject().put("resourceRef", "manga:page-1")
                            .put("request", JSONObject().put("url", "https://img.test/1.jpg"))))
            }
        })
        val chapter = service.chapter(ReaderMangaChapterRequest(ReaderMangaChapterRef("source-a", "book-a"), 7,
            expectedManifestVersion = "manifest-1"))
        assertEquals(chapterIdentity, chapter.manifest.chapter)
        assertEquals("p0", chapter.resources.single().request.sourcePageId)
        assertEquals("manga.chapter.get", calls[0].first)
        assertEquals("cacheFirst", calls[0].second.getString("policy"))
        val window = service.pagesWindow(ReaderMangaPageWindowRequest(chapterIdentity, "manifest-1", 1, 1))
        assertEquals(1, window.pageStart)
        assertEquals(1, window.manifest.pages.single().ordinal)
        assertEquals("manga.pages.window", calls[1].first)
        assertEquals(1, calls[1].second.getInt("limit"))
    }

    @Test
    fun chapterAndWindowRejectManifestOrWindowScopeDrift() {
        val identity = ReaderMangaChapterIdentity("source-a", "book-a", "chapter-7")
        val manifest = JSONObject().put("chapter", JSONObject().put("sourceId", "source-a")
            .put("bookId", "other").put("chapterId", "chapter-7"))
            .put("sourceRuleVersion", "rules-1").put("manifestVersion", "manifest-1")
            .put("decodeRevision", "decode-1").put("pages", JSONArray()
                .put(JSONObject().put("pageId", "page-0").put("ordinal", 0).put("resourceRef", "manga:page-0")))
        val chapterValue = JSONObject().put("manifest", manifest).put("chapterIndex", 7)
            .put("chapterTitle", "第七话").put("cached", false).put("resources", JSONArray())
        assertThrows(IllegalStateException::class.java) {
            ReaderMangaPolicy.chapterData(chapterValue,
                ReaderMangaChapterRequest(ReaderMangaChapterRef("source-a", "book-a"), 7))
        }
        val windowValue = JSONObject().put("manifest", manifest.put("chapter", JSONObject()
            .put("sourceId", "source-a").put("bookId", "book-a").put("chapterId", "chapter-7")))
            .put("chapterIndex", 7).put("chapterTitle", "第七话").put("cached", false)
            .put("pageStart", 3).put("totalPages", 4).put("resources", JSONArray())
        assertThrows(IllegalStateException::class.java) {
            ReaderMangaPolicy.pageWindow(windowValue, ReaderMangaPageWindowRequest(identity, "manifest-1", 3, 1))
        }
    }

    @Test
    fun resourcePrepareInspectAndMaterializationContractsBindTransferAndManifestIdentity() {
        val calls = mutableListOf<String>()
        val identity = ReaderMangaChapterIdentity("source-a", "book-a", "chapter-7")
        var wireTransferId = ""
        val service = ReaderMangaService(fakeTransport { method, params ->
            calls += method
            when (method) {
                "manga.resource.prepare" -> JSONObject().put("prepared", true).put("resourceRef", "manga:page-0")
                    .put("transferId", params.getString("transferId")).put("bytes", 1234).also { wireTransferId = params.getString("transferId") }
                "manga.resource.inspect" -> JSONObject().put("transferId", wireTransferId)
                    .put("sha256", "a".repeat(64)).put("bytes", 1234).put("status", "present")
                    .put("orientation", 1).put("decodeProfile", JSONObject().put("format", "jpeg")
                        .put("encodedWidth", 1080).put("encodedHeight", 1920).put("allocationClass", "scanline"))
                "manga.cache.materialization.prepare" -> JSONObject().put("token", "mat-token")
                    .put("chapter", JSONObject().put("sourceId", identity.sourceId).put("bookId", identity.bookId).put("chapterId", identity.chapterId))
                    .put("manifestVersion", "manifest-1").put("intent", "cache")
                "manga.cache.materialization.complete" -> JSONObject().put("completed", true).put("manifestVersion", "manifest-1")
                else -> JSONObject().put("aborted", true)
            }
        })
        val prepared = service.resourcePrepare(ReaderMangaResourcePrepareRequest(identity, "manifest-1", "manga:page-0", "rules-1", "transfer-1"))
        assertEquals(1234, prepared.bytes)
        val inspected = service.resourceInspect(ReaderMangaResourceInspectRequest(wireTransferId, "a".repeat(64), 1234))
        assertEquals(ReaderMangaResourceStatus.PRESENT, inspected.status)
        assertEquals(ReaderMangaImageFormat.JPEG, inspected.decodeProfile.format)
        val materialized = service.materializationPrepare(ReaderMangaMaterializationPrepareRequest(identity, "manifest-1", ReaderMangaMaterializationIntent.CACHE))
        assertEquals("mat-token", materialized.token)
        assertEquals(true, service.materializationComplete(ReaderMangaMaterializationCompleteRequest(identity, "manifest-1", "mat-token", listOf("manga:page-0"))).completed)
        assertEquals(true, service.materializationAbort("mat-token").aborted)
        assertEquals(listOf("manga.resource.prepare", "manga.resource.inspect", "manga.cache.materialization.prepare",
            "manga.cache.materialization.complete", "manga.cache.materialization.abort"), calls)
    }

    private fun fakeTransport(response: (String, JSONObject) -> JSONObject) = object : CoreTransport {
        override val session = "manga-test"
        override fun request(method: String, params: JSONObject, timeoutMs: Long, beforeSend: (Long) -> Unit,
            terminal: ((JSONObject) -> Unit)?): JSONObject = response(method, params)
        override fun drainOriginalTerminal(requestId: Long, timeoutMs: Long) = error("NO_DRAIN")
        override fun close() = error("NO_CLOSE")
    }
}
