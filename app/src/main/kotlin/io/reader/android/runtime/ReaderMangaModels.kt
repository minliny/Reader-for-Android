package io.reader.android.runtime

/** Core-owned manga source identity; image bytes and rendering remain Host-owned. */
data class ReaderMangaChapterRef(val sourceId: String, val bookId: String)

data class ReaderMangaPage(
    val url: String,
    val requestRule: String? = null,
    val referrer: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val sourcePageId: String? = null,
)

data class ReaderMangaPagesExtract(
    val chapter: ReaderMangaChapterRef,
    val chapterTitle: String,
    val pages: List<ReaderMangaPage>,
)

data class ReaderMangaExtractRequest(
    val chapter: ReaderMangaChapterRef,
    val chapterTitle: String = "",
    val chapterResponse: String = "",
    val chapterUrl: String? = null,
    val variables: Map<String, String> = emptyMap(),
)

data class ReaderMangaChapterIdentity(val sourceId: String, val bookId: String, val chapterId: String)

enum class ReaderMangaReadPolicy(val wire: String) {
    CACHE_ONLY("cacheOnly"), CACHE_FIRST("cacheFirst"), REFRESH("refresh"),
}

data class ReaderMangaChapterRequest(
    val chapter: ReaderMangaChapterRef,
    val chapterIndex: Int,
    val policy: ReaderMangaReadPolicy = ReaderMangaReadPolicy.CACHE_FIRST,
    val expectedManifestVersion: String? = null,
    val preparationRevision: Long? = null,
)

data class ReaderMangaManifestPage(val pageId: String, val ordinal: Int, val resourceRef: String)

data class ReaderMangaManifest(
    val chapter: ReaderMangaChapterIdentity,
    val sourceRuleVersion: String,
    val manifestVersion: String,
    val decodeRevision: String,
    val pages: List<ReaderMangaManifestPage>,
)

data class ReaderMangaResource(val resourceRef: String, val request: ReaderMangaPage)

data class ReaderMangaChapterData(
    val manifest: ReaderMangaManifest,
    val chapterIndex: Int,
    val chapterTitle: String,
    val cached: Boolean,
    val resources: List<ReaderMangaResource>,
)

data class ReaderMangaPageWindow(
    val manifest: ReaderMangaManifest,
    val chapterIndex: Int,
    val chapterTitle: String,
    val cached: Boolean,
    val pageStart: Int,
    val totalPages: Int,
    val resources: List<ReaderMangaResource>,
    val resumeOnly: Boolean,
)

data class ReaderMangaPageWindowRequest(
    val chapter: ReaderMangaChapterIdentity,
    val manifestVersion: String,
    val start: Int,
    val limit: Int,
)
