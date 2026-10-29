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
            val headers = row.optJSONObject("headers")?.let { headerObject ->
                val parsed = linkedMapOf<String, String>()
                val keys = headerObject.keys()
                while (keys.hasNext()) {
                    val key = keys.next().also { check(it.isNotBlank()) { "MANGA_HEADER_NAME:$index" } }
                    val header = headerObject.get(key)
                    check(header is String && header.isNotBlank()) { "MANGA_HEADER_VALUE:$index" }
                    parsed[key] = header
                }
                parsed.toMap()
            } ?: emptyMap()
            pages += ReaderMangaPage(url, requestRule, referrer, headers)
        }
        return ReaderMangaPagesExtract(
            ReaderMangaChapterRef(sourceId, bookId),
            value.optString("chapterTitle"),
            pages.toList(),
        )
    }

    private fun JSONObject.optionalString(name: String, error: String): String? = when {
        !has(name) || isNull(name) -> null
        else -> getString(name).also { check(it.isNotBlank()) { error } }
    }
}
