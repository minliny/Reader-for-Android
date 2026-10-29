package io.reader.android.runtime

/** Core-owned manga source identity; image bytes and rendering remain Host-owned. */
data class ReaderMangaChapterRef(val sourceId: String, val bookId: String)

data class ReaderMangaPage(
    val url: String,
    val requestRule: String? = null,
    val referrer: String? = null,
    val headers: Map<String, String> = emptyMap(),
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
