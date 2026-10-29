package io.reader.android.runtime

import org.json.JSONObject

/** Strict admission for Core manga page descriptors; no download or decode occurs here. */
internal object ReaderMangaPolicy {
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
        return ReaderMangaManifest(identity, value.getString("sourceRuleVersion"), version,
            value.getString("decodeRevision"), pages)
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
