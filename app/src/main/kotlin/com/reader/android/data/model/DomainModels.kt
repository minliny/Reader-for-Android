package com.reader.android.data.model

/**
 * Core domain DTOs matching Reader-Core public DTO contract.
 * Fields mirror Core DTOs from ReaderCoreModels / ReaderCoreProtocols / ReaderCoreParser.
 */

// ── Book ──

data class BookInfo(
    val name: String,
    val author: String,
    val intro: String? = null,
    val kind: String? = null,
    val coverUrl: String? = null,
    val tocUrl: String? = null,
    val wordCount: String? = null,
    val latestChapter: String? = null,
    val updateTime: String? = null,
    val origin: String? = null
)

// ── Source ──

data class BookSource(
    val sourceUrl: String,
    val sourceName: String,
    val sourceGroup: String? = null,
    val enabled: Boolean = true,
    val sourceComment: String? = null,
    // Search rule
    val searchUrl: String? = null,
    val searchCharset: String? = null,
    val searchMethod: String? = null,
    // Book info rule
    val bookInfoUrl: String? = null,
    // TOC rule
    val tocUrl: String? = null,
    val tocCharset: String? = null,
    // Content rule
    val contentUrl: String? = null,
    val contentCharset: String? = null,
    // Meta
    val header: String? = null,
    val loginUrl: String? = null
)

// ── Search ──

data class SearchQuery(
    val keyword: String,
    val page: Int = 1
)

data class SearchResultItem(
    val name: String,
    val author: String,
    val coverUrl: String? = null,
    val detailUrl: String? = null,
    val kind: String? = null,
    val wordCount: String? = null,
    val latestChapter: String? = null,
    val intro: String? = null,
    val sourceName: String? = null
)

// ── TOC / Chapter ──

data class TOCItem(
    val title: String,
    val url: String,
    val children: List<TOCItem> = emptyList()
)

// ── Content ──

data class ContentPage(
    val content: String,
    val title: String? = null,
    val nextPageUrl: String? = null
)
