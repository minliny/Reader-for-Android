package io.reader.android.runtime

import org.json.JSONArray
import org.json.JSONObject

/** Source-only Core manga adapter. Host image transfer, decode, cache and zoom are separate. */
internal class ReaderMangaService(
    private val transport: CoreTransport,
    private val materializationStore: ReaderMangaMaterializationStore? = null,
) {
    fun pagesExtract(request: ReaderMangaExtractRequest): ReaderMangaPagesExtract {
        check(request.chapter.sourceId.isNotBlank()) { "MANGA_SOURCE_ID" }
        check(request.chapter.bookId.isNotBlank()) { "MANGA_BOOK_ID" }
        val params = JSONObject()
            .put("sourceId", request.chapter.sourceId)
            .put("bookId", request.chapter.bookId)
            .put("chapterTitle", request.chapterTitle)
            .put("chapterResponse", request.chapterResponse)
            .put("chapterRequest", JSONObject.NULL)
            .put("source", JSONObject.NULL)
            .put("chapterUrl", request.chapterUrl ?: JSONObject.NULL)
            .put("variables", JSONObject().also { values -> request.variables.forEach { (key, value) -> values.put(key, value) } })
        return ReaderMangaPolicy.pagesExtract(transport.request("manga.pages.extract", params), request.chapter)
    }

    fun chapter(request: ReaderMangaChapterRequest): ReaderMangaChapterData {
        check(request.chapter.sourceId.isNotBlank()) { "MANGA_SOURCE_ID" }
        check(request.chapter.bookId.isNotBlank()) { "MANGA_BOOK_ID" }
        check(request.chapterIndex >= 0) { "MANGA_CHAPTER_INDEX" }
        val params = JSONObject()
            .put("sourceId", request.chapter.sourceId)
            .put("bookId", request.chapter.bookId)
            .put("chapterIndex", request.chapterIndex)
            .put("policy", request.policy.wire)
        request.expectedManifestVersion?.let { params.put("expectedManifestVersion", it) }
        request.preparationRevision?.let { params.put("preparationRevision", it) }
        return ReaderMangaPolicy.chapterData(transport.request("manga.chapter.get", params), request)
    }

    fun pagesWindow(request: ReaderMangaPageWindowRequest): ReaderMangaPageWindow {
        check(request.chapter.sourceId.isNotBlank() && request.chapter.bookId.isNotBlank() && request.chapter.chapterId.isNotBlank()) {
            "MANGA_CHAPTER_IDENTITY"
        }
        check(request.manifestVersion.isNotBlank()) { "MANGA_MANIFEST_VERSION" }
        check(request.start >= 0 && request.limit in 1..32) { "MANGA_WINDOW_RANGE" }
        val params = JSONObject()
            .put("sourceId", request.chapter.sourceId)
            .put("bookId", request.chapter.bookId)
            .put("chapterId", request.chapter.chapterId)
            .put("manifestVersion", request.manifestVersion)
            .put("start", request.start)
            .put("limit", request.limit)
        return ReaderMangaPolicy.pageWindow(transport.request("manga.pages.window", params), request)
    }

    /** These methods only cross the Core contract; Host transfer/decode is still not implemented. */
    fun resourcePrepare(request: ReaderMangaResourcePrepareRequest): ReaderMangaResourcePrepareResult {
        check(request.manifestVersion.isNotBlank() && request.resourceRef.isNotBlank() && request.sourceRuleVersion.isNotBlank()) {
            "MANGA_RESOURCE_PREPARE_SCOPE"
        }
        val identity = ReaderMangaTransferIdentity.forPrepare(request)
        val wireRequest = request.copy(transferId = identity.wireId)
        val params = JSONObject().put("chapter", JSONObject().put("sourceId", request.chapter.sourceId)
            .put("bookId", request.chapter.bookId).put("chapterId", request.chapter.chapterId))
            .put("manifestVersion", request.manifestVersion).put("resourceRef", request.resourceRef)
            .put("sourceRuleVersion", request.sourceRuleVersion).put("transferId", identity.wireId)
        return ReaderMangaPolicy.resourcePrepare(transport.request("manga.resource.prepare", params), wireRequest)
    }

    fun resourceInspect(request: ReaderMangaResourceInspectRequest): ReaderMangaResourceInspectResult {
        check(request.transferId.isNotBlank() && request.bytes in 1..16 * 1024 * 1024 && request.sha256.matches(Regex("[0-9a-f]{64}"))) {
            "MANGA_RESOURCE_INSPECT_SCOPE"
        }
        val params = JSONObject().put("transferId", request.transferId).put("sha256", request.sha256).put("bytes", request.bytes)
        return ReaderMangaPolicy.resourceInspect(transport.request("manga.resource.inspect", params), request)
    }

    fun materializationPrepare(request: ReaderMangaMaterializationPrepareRequest): ReaderMangaMaterializationPrepareResult {
        val params = JSONObject().put("chapter", JSONObject().put("sourceId", request.chapter.sourceId)
            .put("bookId", request.chapter.bookId).put("chapterId", request.chapter.chapterId))
            .put("manifestVersion", request.manifestVersion).put("intent", request.intent.wire)
        return ReaderMangaPolicy.materializationPrepare(transport.request("manga.cache.materialization.prepare", params), request).also {
            materializationStore?.prepare(it)
        }
    }

    fun materializationComplete(request: ReaderMangaMaterializationCompleteRequest): ReaderMangaMaterializationCompleteResult {
        val refs = JSONArray().also { request.resourceRefs.forEach(it::put) }
        val params = JSONObject().put("chapter", JSONObject().put("sourceId", request.chapter.sourceId)
            .put("bookId", request.chapter.bookId).put("chapterId", request.chapter.chapterId))
            .put("manifestVersion", request.manifestVersion).put("token", request.token).put("resourceRefs", refs)
        materializationStore?.requireResources(request.token, request.chapter, request.manifestVersion, request.resourceRefs)
        return ReaderMangaPolicy.materializationComplete(transport.request("manga.cache.materialization.complete", params), request).also {
            materializationStore?.complete(it, request.token)
        }
    }

    fun materializationAbort(token: String): ReaderMangaMaterializationAbortResult {
        check(token.isNotBlank()) { "MANGA_MATERIALIZATION_TOKEN" }
        return ReaderMangaPolicy.materializationAbort(transport.request("manga.cache.materialization.abort", JSONObject().put("token", token))).also {
            materializationStore?.abort(token)
        }
    }
}
