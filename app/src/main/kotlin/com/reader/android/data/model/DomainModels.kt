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

/**
 * One selector expression for a [BookSourceRule]. Legado-style shape:
 * `name@css||attr` (CSS selector, optional `||attr` to read an attribute),
 * `name@xpath` (bounded XPath mapped to a CSS selector by the adapter),
 * or `name@text` / `name@html` for explicit extraction modes.
 *
 * This is a clean-room rule grammar — no Legado source is referenced. The
 * field list is the minimal set the four parsers consume; it is additive on
 * [BookSource] and defaults to null so existing fixtures/sources keep working.
 */
data class RuleField(
    val name: String,
    val selector: String,
    val extraction: RuleExtraction = RuleExtraction.TEXT,
    val attribute: String? = null
)

enum class RuleExtraction { TEXT, HTML, ATTRIBUTE }

/**
 * A parsed selector rule for one fetch stage (search / bookInfo / toc / content).
 * [listSelector] selects the repeated rows for list stages (search results, TOC
 * chapters); [fields] are evaluated relative to each row (or the whole document
 * for bookInfo / content). Null fields fall back to the regex path in the parser.
 */
data class BookSourceRule(
    val listSelector: String? = null,
    val fields: List<RuleField> = emptyList()
) {
    fun field(name: String): RuleField? = fields.firstOrNull { it.name == name }
}

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
    // Selector rules (clean-room Legado-style grammar; null = use regex fallback)
    val ruleSearch: BookSourceRule? = null,
    val ruleBookInfo: BookSourceRule? = null,
    val ruleToc: BookSourceRule? = null,
    val ruleContent: BookSourceRule? = null,
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
