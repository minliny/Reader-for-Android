package io.reader.android.runtime

import org.json.JSONObject

/** Checks the supplied Core manga DTO/original receipt; it performs no IO or recovery inference. */
internal object ReaderMangaProgressPolicy {
    const val METHOD = "reading.progress.update"
    const val KIND = "mangaProgress"
    const val MAX_INTEGER = 9_007_199_254_740_991L
    val id = Regex("android-manga-progress-[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")
    private val pageId = Regex("mp1:[a-f0-9]{64}")
    private val definiteRejections = setOf("INVALID_PARAMS", "INVALID_MESSAGE", "UNKNOWN_METHOD",
        "INVALID_PROTOCOL_VERSION", "BUSY", "RESOURCE_EXHAUSTED")

    fun reference(value: ReaderMangaChapterRef) {
        identity(value.sourceId); identity(value.bookId)
        check(value.sourceId != "local") { "MANGA_PROGRESS_REMOTE_SOURCE_REQUIRED" }
    }
    private fun identity(value: String) {
        check(value.isNotBlank() && value.length <= 4096 && value.none(Char::isISOControl)) { "MANGA_PROGRESS_IDENTITY" }
    }
    private fun token(value: ReaderMangaProgressToken) {
        check(value.epoch in 1..MAX_INTEGER && value.revision in 0..MAX_INTEGER) { "MANGA_PROGRESS_TOKEN" }
    }
    fun locationJson(value: ReaderMangaLocation): JSONObject {
        reference(ReaderMangaChapterRef(value.chapter.sourceId, value.chapter.bookId)); identity(value.chapter.chapterId)
        identity(value.manifestVersion)
        check(pageId.matches(value.pageId) && value.pageOrdinalFallback in 0 until 10_000 &&
            value.x.isFinite() && value.y.isFinite() && value.x in 0.0..1.0 && value.y in 0.0..1.0 &&
            value.progressRevision in 1..MAX_INTEGER) { "MANGA_PROGRESS_LOCATION" }
        return JSONObject().put("kind", "manga").put("chapter", JSONObject()
            .put("sourceId", value.chapter.sourceId).put("bookId", value.chapter.bookId).put("chapterId", value.chapter.chapterId))
            .put("manifestVersion", value.manifestVersion).put("pageId", value.pageId)
            .put("pageOrdinalFallback", value.pageOrdinalFallback).put("x", value.x).put("y", value.y)
            .put("progressRevision", value.progressRevision)
    }
    fun location(value: JSONObject): ReaderMangaLocation {
        check(value.get("kind") == "manga") { "MANGA_PROGRESS_KIND" }
        val chapter = value.getJSONObject("chapter")
        val ordinal = integer(value, "pageOrdinalFallback").also { check(it in 0 until 10_000L) }
        return ReaderMangaLocation(ReaderMangaChapterIdentity(string(chapter, "sourceId"), string(chapter, "bookId"),
            string(chapter, "chapterId")), string(value, "manifestVersion"), string(value, "pageId"), ordinal.toInt(),
            number(value, "x"), number(value, "y"), integer(value, "progressRevision")).also { locationJson(it) }
    }
    fun state(value: JSONObject, owner: ReaderMangaChapterRef): ReaderMangaProgressState {
        reference(owner)
        val rawToken = value.getJSONObject("token")
        val parsedToken = ReaderMangaProgressToken(integer(rawToken, "epoch"), integer(rawToken, "revision")).also(::token)
        check(value.has("location")) { "MANGA_PROGRESS_LOCATION_MISSING" }
        val parsed = if (value.isNull("location")) null else location(value.getJSONObject("location"))
        check(parsed == null && parsedToken.revision == 0L || parsed != null &&
            parsed.chapter.sourceId == owner.sourceId && parsed.chapter.bookId == owner.bookId &&
            parsed.progressRevision == parsedToken.revision) { "MANGA_PROGRESS_STATE_SCOPE" }
        return ReaderMangaProgressState(parsedToken, parsed)
    }
    fun parameters(request: ReaderMangaProgressSaveRequest): JSONObject {
        val location = request.location; val manifest = request.manifest
        check(location.chapter == manifest.chapter && location.manifestVersion == manifest.manifestVersion &&
            manifest.sourceRuleVersion.isNotBlank() && manifest.decodeRevision.isNotBlank()) { "MANGA_PROGRESS_MANIFEST_SCOPE" }
        check(manifest.pages.count { it.pageId == location.pageId && it.ordinal == location.pageOrdinalFallback } == 1) {
            "MANGA_PROGRESS_VISIBLE_PAGE_SCOPE"
        }
        return parameters(location, request.expected)
    }
    private fun parameters(location: ReaderMangaLocation, expected: ReaderMangaProgressToken): JSONObject {
        token(expected)
        check(expected.revision < MAX_INTEGER && location.progressRevision == expected.revision + 1) { "MANGA_PROGRESS_REVISION" }
        return JSONObject().put("kind", "manga").put("location", locationJson(location))
            .put("expectedEpoch", expected.epoch).put("expectedRevision", expected.revision)
    }
    fun owner(record: JSONObject): ReaderMangaChapterRef = ReaderMangaChapterRef(string(record, "sourceId"), string(record, "bookId"))
    fun record(value: JSONObject) {
        check(integer(value, "journalSchema") == 1L && value.get("kind") == KIND &&
            id.matches(string(value, "id")) && value.get("method") == METHOD &&
            value.get("attempted") is Boolean && value.get("completed") is Boolean &&
            value.get("transportConfirmed") is Boolean) { "MANGA_PROGRESS_ORIGINAL_RECORD" }
        val params = JSONObject(string(value, "parametersJson"))
        check(params.get("kind") == "manga") { "MANGA_PROGRESS_KIND" }
        val location = location(params.getJSONObject("location"))
        parameters(location, ReaderMangaProgressToken(integer(params, "expectedEpoch"), integer(params, "expectedRevision")))
        check(owner(value) == ReaderMangaChapterRef(location.chapter.sourceId, location.chapter.bookId)) { "MANGA_PROGRESS_ORIGINAL_SCOPE" }
        val hasIdentity = value.has("requestId") || value.has("runtimeSession") || value.has("clientIdentity")
        if (hasIdentity) check(value.getBoolean("attempted") && integer(value, "requestId") > 0 &&
            string(value, "runtimeSession").isNotBlank() && string(value, "clientIdentity").isNotBlank()) { "MANGA_PROGRESS_ORIGINAL_DISPATCH" }
        if (value.has("wireTerminal")) terminal(value, value.getJSONObject("wireTerminal"))
        if (value.getBoolean("transportConfirmed")) check(value.has("wireTerminal")) { "MANGA_PROGRESS_CONFIRMATION_WITHOUT_TERMINAL" }
        if (value.getBoolean("completed")) check(value.has("wireTerminal")) { "MANGA_PROGRESS_COMPLETION_WITHOUT_TERMINAL" }
    }
    fun terminal(record: JSONObject, event: JSONObject) {
        check(integer(event, "protocolVersion") == 1L && integer(event, "requestId") == integer(record, "requestId") &&
            event.get("type") in setOf("result", "error")) { "MANGA_PROGRESS_ORIGINAL_TERMINAL_IDENTITY" }
    }
    fun acknowledgement(record: JSONObject, event: JSONObject): ReaderMangaProgressState {
        terminal(record, event)
        check(event.get("type") == "result") { "MANGA_PROGRESS_RESULT_REQUIRED" }
        val params = JSONObject(string(record, "parametersJson"))
        val expected = location(params.getJSONObject("location"))
        val state = state(event.getJSONObject("data"), owner(record))
        check(state.token.epoch == integer(params, "expectedEpoch") && state.token.revision == expected.progressRevision &&
            state.location == expected) { "MANGA_PROGRESS_ORIGINAL_ACK_MISMATCH" }
        return state
    }
    fun definiteRejection(record: JSONObject, event: JSONObject): Boolean {
        terminal(record, event)
        if (event.get("type") != "error") return false
        val error = event.getJSONObject("error")
        return error.optJSONObject("details")?.optBoolean("originalPending") != true &&
            string(error, "code") in definiteRejections
    }
    fun decodeRecord(bytes: ByteArray): JSONObject = ProgressJournalCandidatePolicy.parse(bytes).also(::record)
    fun encodeRecord(record: JSONObject): String {
        this.record(record)
        return record.toString().also { check(it.toByteArray(Charsets.UTF_8).size <= ProgressJournalCandidatePolicy.MAX_BYTES) }
    }
    fun integer(value: JSONObject, key: String): Long = when (val raw = value.get(key)) {
        is Int -> raw.toLong(); is Long -> raw; else -> error("MANGA_PROGRESS_INTEGER:$key")
    }
    private fun string(value: JSONObject, key: String): String = value.get(key) as? String ?: error("MANGA_PROGRESS_STRING:$key")
    private fun number(value: JSONObject, key: String): Double = (value.get(key) as? Number)?.toDouble()?.also {
        check(it.isFinite()) { "MANGA_PROGRESS_FINITE:$key" }
    } ?: error("MANGA_PROGRESS_NUMBER:$key")
}
