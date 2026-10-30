package io.reader.android.runtime

import org.json.JSONObject

/** Strict admission for Core manga page descriptors; no download or decode occurs here. */
internal object ReaderMangaPolicy {
    fun directoryTargetProof(request: ReaderMangaChapterRequest): JSONObject? {
        val target = request.directoryTarget ?: return null
        val directory = target.directory
        check(request.chapter == directory.chapter && request.chapterIndex == target.chapterIndex &&
            target.sourceRuleVersion == directory.sourceVersion && target.url.isNotBlank()) { "MANGA_DIRECTORY_REQUEST_SCOPE" }
        return JSONObject(target.proofJson).also { proof ->
            check(proof.getString("sourceId") == request.chapter.sourceId && proof.getString("bookId") == request.chapter.bookId &&
                proof.getString("catalogRevision") == directory.catalogRevision && proof.getString("structureRevision") == directory.structureRevision &&
                proof.getString("nodeId") == target.nodeId && ReaderMangaProgressPolicy.integer(proof, "chapterIndex") == request.chapterIndex.toLong() &&
                proof.getString("url") == target.url) { "MANGA_DIRECTORY_NATIVE_PROOF_SCOPE" }
        }
    }
    fun pagesExtract(value: JSONObject, expected: ReaderMangaChapterRef): ReaderMangaPagesExtract {
        val sourceId = value.getString("sourceId").also { check(it == expected.sourceId && it.isNotBlank()) { "MANGA_SOURCE_SCOPE" } }
        val bookId = value.getString("bookId").also { check(it == expected.bookId && it.isNotBlank()) { "MANGA_BOOK_SCOPE" } }
        val rows = value.optJSONArray("pages") ?: error("MANGA_PAGES_MISSING")
        val pages = ArrayList<ReaderMangaPage>(rows.length())
        for (index in 0 until rows.length()) {
            val row = rows.getJSONObject(index)
            val url = row.getString("url").also { check(it.isNotBlank()) { "MANGA_PAGE_URL:$index" } }
            val requestRule = row.optionalString("requestRule", "MANGA_REQUEST_RULE:$index")
            val referrer = row.optionalString("referrer", "MANGA_REFERRER:$index")
            val headers = when {
                !row.has("headers") || row.isNull("headers") -> emptyMap()
                else -> {
                    val headerObject = row.get("headers") as? JSONObject
                        ?: error("MANGA_HEADERS_TYPE:$index")
                    val parsed = linkedMapOf<String, String>()
                    val keys = headerObject.keys()
                    while (keys.hasNext()) {
                        val key = keys.next().also { check(it.isNotBlank()) { "MANGA_HEADER_NAME:$index" } }
                        val header = headerObject.get(key)
                        check(header is String && header.isNotBlank()) { "MANGA_HEADER_VALUE:$index" }
                        parsed[key] = header
                    }
                    parsed.toMap()
                }
            }
            pages += ReaderMangaPage(url, requestRule, referrer, headers)
        }
        return ReaderMangaPagesExtract(
            ReaderMangaChapterRef(sourceId, bookId),
            value.optString("chapterTitle"),
            pages.toList(),
        )
    }

    fun chapterData(value: JSONObject, expected: ReaderMangaChapterRequest): ReaderMangaChapterData {
        val chapterIndex = value.getInt("chapterIndex").also { check(it == expected.chapterIndex && it >= 0) { "MANGA_CHAPTER_INDEX" } }
        val manifest = manifest(value.getJSONObject("manifest"), expected.chapter.sourceId, expected.chapter.bookId)
        expected.directoryTarget?.let { target ->
            directoryTargetProof(expected)
            check(manifest.chapter.chapterId == target.url && manifest.sourceRuleVersion == target.sourceRuleVersion) {
                "MANGA_DIRECTORY_RETURNED_CHAPTER_SCOPE"
            }
        }
        check(manifest.manifestVersion == expected.expectedManifestVersion || expected.expectedManifestVersion == null) {
            "MANGA_MANIFEST_VERSION"
        }
        val resources = resources(value.optJSONArray("resources"), manifest)
        return ReaderMangaChapterData(manifest, chapterIndex, value.getString("chapterTitle"), value.getBoolean("cached"), resources)
    }

    fun pageWindow(value: JSONObject, expected: ReaderMangaPageWindowRequest): ReaderMangaPageWindow {
        check(expected.start >= 0 && expected.limit in 1..32) { "MANGA_WINDOW_RANGE" }
        val pageStart = value.getInt("pageStart").also { check(it == expected.start && it >= 0) { "MANGA_WINDOW_START" } }
        val totalPages = value.getInt("totalPages").also { check(it in 1..10_000 && pageStart < it) { "MANGA_WINDOW_TOTAL" } }
        val manifest = manifest(value.getJSONObject("manifest"), expected.chapter.sourceId, expected.chapter.bookId,
            expected.chapter.chapterId, expected.manifestVersion, pageStart)
        check(manifest.pages.size <= expected.limit) { "MANGA_WINDOW_PAGE_COUNT" }
        val resources = resources(value.optJSONArray("resources"), manifest)
        check(resources.size <= expected.limit && pageStart + resources.size <= totalPages) { "MANGA_WINDOW_RESOURCE_COUNT" }
        return ReaderMangaPageWindow(manifest, value.getInt("chapterIndex").also { check(it >= 0) { "MANGA_CHAPTER_INDEX" } },
            value.getString("chapterTitle"), value.getBoolean("cached"), pageStart, totalPages, resources,
            value.optBoolean("resumeOnly", false))
    }

    fun resourcePrepare(value: JSONObject, expected: ReaderMangaResourcePrepareRequest): ReaderMangaResourcePrepareResult {
        check(value.optBoolean("prepared", false)) { "MANGA_RESOURCE_NOT_PREPARED" }
        val ref = value.getString("resourceRef").also { check(it == expected.resourceRef && it.isNotBlank()) { "MANGA_RESOURCE_REF" } }
        val transferId = value.getString("transferId").also { check(it == expected.transferId && it.isNotBlank()) { "MANGA_TRANSFER_ID" } }
        val bytes = value.getInt("bytes").also { check(it in 1..16 * 1024 * 1024) { "MANGA_RESOURCE_BYTES" } }
        return ReaderMangaResourcePrepareResult(true, ref, transferId, bytes)
    }

    fun resourceInspect(value: JSONObject, expected: ReaderMangaResourceInspectRequest): ReaderMangaResourceInspectResult {
        val transferId = value.getString("transferId").also { check(it == expected.transferId && it.isNotBlank()) { "MANGA_TRANSFER_ID" } }
        val sha256 = value.getString("sha256").also { check(it == expected.sha256 && it.matches(Regex("[0-9a-f]{64}"))) { "MANGA_RESOURCE_SHA" } }
        val bytes = value.getInt("bytes").also { check(it == expected.bytes && it in 1..16 * 1024 * 1024) { "MANGA_RESOURCE_BYTES" } }
        val status = when (value.getString("status")) {
            "absent" -> ReaderMangaResourceStatus.ABSENT
            "present" -> ReaderMangaResourceStatus.PRESENT
            else -> error("MANGA_RESOURCE_STATUS")
        }
        val orientation = if (!value.has("orientation") || value.isNull("orientation")) null else value.getInt("orientation")
            .also { check(it in 1..8) { "MANGA_RESOURCE_ORIENTATION" } }
        check(status == ReaderMangaResourceStatus.ABSENT && orientation == null || status == ReaderMangaResourceStatus.PRESENT && orientation != null) {
            "MANGA_RESOURCE_ORIENTATION_SCOPE"
        }
        val profile = decodeProfile(value.getJSONObject("decodeProfile"))
        return ReaderMangaResourceInspectResult(transferId, sha256, bytes, status, orientation, profile)
    }

    fun materializationPrepare(value: JSONObject, expected: ReaderMangaMaterializationPrepareRequest): ReaderMangaMaterializationPrepareResult {
        val token = value.getString("token").also { check(it.isNotBlank()) { "MANGA_MATERIALIZATION_TOKEN" } }
        val chapter = identity(value.getJSONObject("chapter"))
        check(chapter == expected.chapter && value.getString("manifestVersion") == expected.manifestVersion) { "MANGA_MATERIALIZATION_SCOPE" }
        val intent = materializationIntent(value.getString("intent"))
        check(intent == expected.intent) { "MANGA_MATERIALIZATION_INTENT" }
        return ReaderMangaMaterializationPrepareResult(token, chapter, expected.manifestVersion, intent)
    }

    fun materializationComplete(value: JSONObject, expected: ReaderMangaMaterializationCompleteRequest): ReaderMangaMaterializationCompleteResult {
        check(value.has("completed")) { "MANGA_MATERIALIZATION_COMPLETED" }
        val version = value.getString("manifestVersion").also { check(it == expected.manifestVersion && it.isNotBlank()) { "MANGA_MANIFEST_VERSION" } }
        check(expected.token.isNotBlank() && expected.resourceRefs.isNotEmpty() && expected.resourceRefs.distinct().size == expected.resourceRefs.size) {
            "MANGA_MATERIALIZATION_REQUEST"
        }
        return ReaderMangaMaterializationCompleteResult(value.getBoolean("completed"), version)
    }

    fun materializationAbort(value: JSONObject): ReaderMangaMaterializationAbortResult =
        ReaderMangaMaterializationAbortResult(value.getBoolean("aborted"))

    private fun manifest(value: JSONObject, sourceId: String, bookId: String, chapterId: String? = null,
        manifestVersion: String? = null, ordinalStart: Int? = null): ReaderMangaManifest {
        val chapter = value.getJSONObject("chapter")
        val identity = ReaderMangaChapterIdentity(chapter.getString("sourceId"), chapter.getString("bookId"), chapter.getString("chapterId"))
        check(identity.sourceId == sourceId && identity.bookId == bookId && identity.chapterId.isNotBlank()) { "MANGA_MANIFEST_SCOPE" }
        chapterId?.let { check(identity.chapterId == it) { "MANGA_CHAPTER_ID" } }
        val version = value.getString("manifestVersion").also {
            check(it.isNotBlank() && (manifestVersion == null || it == manifestVersion)) { "MANGA_MANIFEST_VERSION" }
        }
        val rows = value.getJSONArray("pages")
        check(rows.length() in 1..10_000) { "MANGA_MANIFEST_PAGES" }
        val pages = ArrayList<ReaderMangaManifestPage>(rows.length())
        for (position in 0 until rows.length()) {
            val row = rows.getJSONObject(position)
            val ordinal = row.getInt("ordinal").also { check(it >= 0) { "MANGA_PAGE_ORDINAL" } }
            ordinalStart?.let { check(ordinal == it + position) { "MANGA_PAGE_ORDINAL_CONTINUITY" } }
            pages += ReaderMangaManifestPage(row.getString("pageId"), ordinal, row.getString("resourceRef")).also {
                check(it.pageId.isNotBlank() && it.resourceRef.isNotBlank()) { "MANGA_PAGE_IDENTITY" }
            }
        }
        val sourceRuleVersion = value.getString("sourceRuleVersion").also { check(it.isNotBlank()) { "MANGA_SOURCE_RULE_VERSION" } }
        val decodeRevision = value.getString("decodeRevision").also { check(it.isNotBlank()) { "MANGA_DECODE_REVISION" } }
        return ReaderMangaManifest(identity, sourceRuleVersion, version, decodeRevision, pages)
    }

    private fun identity(value: JSONObject): ReaderMangaChapterIdentity = ReaderMangaChapterIdentity(
        value.getString("sourceId"), value.getString("bookId"), value.getString("chapterId"),
    ).also { check(it.sourceId.isNotBlank() && it.bookId.isNotBlank() && it.chapterId.isNotBlank()) { "MANGA_CHAPTER_IDENTITY" } }

    private fun materializationIntent(value: String): ReaderMangaMaterializationIntent = when (value) {
        "cache" -> ReaderMangaMaterializationIntent.CACHE
        "download" -> ReaderMangaMaterializationIntent.DOWNLOAD
        else -> error("MANGA_MATERIALIZATION_INTENT")
    }

    private fun decodeProfile(value: JSONObject): ReaderMangaImageDecodeProfile {
        val format = when (value.getString("format")) {
            "jpeg" -> ReaderMangaImageFormat.JPEG
            "png" -> ReaderMangaImageFormat.PNG
            "webp" -> ReaderMangaImageFormat.WEBP
            else -> error("MANGA_IMAGE_FORMAT")
        }
        val allocation = when (value.getString("allocationClass")) {
            "scanline" -> ReaderMangaAllocationClass.SCANLINE
            "pngInterlaced" -> ReaderMangaAllocationClass.PNG_INTERLACED
            "fullFrame" -> ReaderMangaAllocationClass.FULL_FRAME
            else -> error("MANGA_ALLOCATION_CLASS")
        }
        val width = value.optionalPositiveLong("encodedWidth")
        val height = value.optionalPositiveLong("encodedHeight")
        check((format == ReaderMangaImageFormat.WEBP || width != null && height != null) &&
            (format != ReaderMangaImageFormat.WEBP || allocation == ReaderMangaAllocationClass.FULL_FRAME) &&
            (allocation != ReaderMangaAllocationClass.PNG_INTERLACED || format == ReaderMangaImageFormat.PNG)) {
            "MANGA_DECODE_PROFILE"
        }
        return ReaderMangaImageDecodeProfile(format, width, height, allocation)
    }

    private fun JSONObject.optionalPositiveLong(name: String): Long? = when {
        !has(name) || isNull(name) -> null
        else -> getLong(name).also { check(it > 0) { "MANGA_DECODE_DIMENSION" } }
    }

    private fun resources(rows: org.json.JSONArray?, manifest: ReaderMangaManifest): List<ReaderMangaResource> {
        if (rows == null) return emptyList()
        val known = manifest.pages.map { it.resourceRef }.toSet()
        val output = ArrayList<ReaderMangaResource>(rows.length())
        for (index in 0 until rows.length()) {
            val row = rows.getJSONObject(index)
            val ref = row.getString("resourceRef").also { check(it in known) { "MANGA_RESOURCE_SCOPE:$index" } }
            val request = page(row.getJSONObject("request"))
            output += ReaderMangaResource(ref, request)
        }
        return output
    }

    private fun page(row: JSONObject): ReaderMangaPage {
        val url = row.getString("url").also { check(it.isNotBlank()) { "MANGA_PAGE_URL" } }
        val requestRule = row.optionalString("requestRule", "MANGA_REQUEST_RULE")
        val referrer = row.optionalString("referrer", "MANGA_REFERRER")
        val sourcePageId = row.optionalString("sourcePageId", "MANGA_SOURCE_PAGE_ID")
        val headers = when {
            !row.has("headers") || row.isNull("headers") -> emptyMap()
            else -> {
                val objectValue = row.getJSONObject("headers")
                val values = linkedMapOf<String, String>()
                val keys = objectValue.keys()
                while (keys.hasNext()) {
                    val key = keys.next().also { check(it.isNotBlank()) { "MANGA_HEADER_NAME" } }
                    val value = objectValue.get(key)
                    check(value is String && value.isNotBlank()) { "MANGA_HEADER_VALUE" }
                    values[key] = value
                }
                values.toMap()
            }
        }
        return ReaderMangaPage(url, requestRule, referrer, headers, sourcePageId)
    }

    private fun JSONObject.optionalString(name: String, error: String): String? = when {
        !has(name) || isNull(name) -> null
        else -> getString(name).also { check(it.isNotBlank()) { error } }
    }
}
