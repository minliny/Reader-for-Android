package com.reader.android.data.bridge

import com.reader.android.AppProvider
import com.reader.android.data.model.BookInfo
import com.reader.android.data.model.BookSource
import com.reader.android.data.model.ContentPage
import com.reader.android.data.model.SearchQuery
import com.reader.android.data.model.SearchResultItem
import com.reader.android.data.model.TOCItem
import com.reader.android.data.network.BookInfoParser
import com.reader.android.data.network.ContentParser
import com.reader.android.data.network.HttpTransport
import com.reader.android.data.network.PostRequestBody
import com.reader.android.data.network.RequestHeaders
import com.reader.android.data.network.SearchParser
import com.reader.android.data.network.TOCParser
import java.net.URLEncoder

/**
 * Production implementation of [CoreBridge].
 * Wires [HttpTransport] → four parsers → domain models.
 *
 * Supports injectable transport:
 *   - [OkHttpTransport] for real network
 *   - [FixtureHttpTransport] for deterministic tests
 *
 * URL construction:
 *   - If the BookSource URL field starts with http, use it as-is
 *   - Otherwise resolve against sourceUrl base
 */
class RealCoreBridge(
    private val transport: HttpTransport,
    private val searchParser: SearchParser = SearchParser(),
    private val bookInfoParser: BookInfoParser = BookInfoParser(),
    private val tocParser: TOCParser = TOCParser(),
    private val contentParser: ContentParser = ContentParser(),
    private val cookieStore: com.reader.android.data.adapter.CookieStore? = null
) : CoreBridge {

    init {
        check(AppProvider.isNetworkAllowed) {
            "RealCoreBridge cannot be used when network is disabled. " +
                "Set READER_ALLOW_REAL_NETWORK=true or call AppProvider.enableNetworkForTestingOnly() in tests."
        }
    }

    /**
     * Search for books by keyword.
     * Constructs URL from [BookSource.searchUrl], replacing "key" placeholder with query.
     */
    override suspend fun search(query: SearchQuery, source: BookSource): List<SearchResultItem> {
        val searchUrl = resolveUrl(source.searchUrl, source.sourceUrl)
            ?: throw ReaderException(
                error = ReaderError(
                    code = ReaderErrorCode.PARSE,
                    stage = ReaderFailureStage.SEARCH,
                    message = "书源未配置搜索 URL",
                    sourceName = source.sourceName
                )
            )

        val url = searchUrl.replace("key", URLEncoder.encode(query.keyword, "UTF-8"))
        val headers = withCookies(buildHeaders(source), url)

        return runCatching {
            val response = if (source.searchMethod?.equals("POST", ignoreCase = true) == true) {
                transport.post(url, headers, searchBody(query.keyword, source))
            } else {
                transport.get(url, headers)
            }
            if (response.code != 200) {
                throw ReaderException(
                    error = ReaderError(
                        code = ReaderErrorCode.NETWORK,
                        stage = ReaderFailureStage.SEARCH,
                        message = "HTTP ${response.code}",
                        sourceName = source.sourceName
                    )
                )
            }
            val results = searchParser.parseSearchResponse(response.body, source.sourceName, source.ruleSearch)
            // Normalize all relative URLs in parser output to absolute
            results.map { it.copy(detailUrl = normalizeUrl(it.detailUrl, source.sourceUrl)) }
        }.getOrElse { e ->
            if (e is ReaderException) throw e
            throw ReaderException(
                error = ReaderError(
                    code = ReaderErrorCode.NETWORK,
                    stage = ReaderFailureStage.SEARCH,
                    message = e.message,
                    sourceName = source.sourceName
                )
            )
        }
    }

    /**
     * Fetch book detail page and parse metadata.
     * Uses the detailUrl from a search result directly.
     */
    override suspend fun getBookInfo(detailUrl: String, source: BookSource): BookInfo {
        val url = resolveUrl(detailUrl, source.sourceUrl)
            ?: throw ReaderException(
                error = ReaderError(
                    code = ReaderErrorCode.PARSE,
                    stage = ReaderFailureStage.BOOK_INFO,
                    message = "detailUrl 为空",
                    sourceName = source.sourceName
                )
            )
        val headers = buildHeaders(source)

        return runCatching {
            val response = transport.get(url, headers)
            if (response.code != 200) {
                throw ReaderException(
                    error = ReaderError(
                        code = ReaderErrorCode.NETWORK,
                        stage = ReaderFailureStage.BOOK_INFO,
                        message = "HTTP ${response.code}",
                        sourceName = source.sourceName
                    )
                )
            }
            val raw = bookInfoParser.parseBookInfoResponse(response.body, source.sourceName, source.ruleBookInfo)
                ?: throw ReaderException(
                    error = ReaderError(
                        code = ReaderErrorCode.PARSE,
                        stage = ReaderFailureStage.BOOK_INFO,
                        message = "无法解析书籍信息",
                        sourceName = source.sourceName
                    )
                )
            raw.copy(tocUrl = normalizeUrl(raw.tocUrl, source.sourceUrl))
        }.getOrElse { e ->
            if (e is ReaderException) throw e
            throw ReaderException(
                error = ReaderError(
                    code = ReaderErrorCode.NETWORK,
                    stage = ReaderFailureStage.BOOK_INFO,
                    message = e.message,
                    sourceName = source.sourceName
                )
            )
        }
    }

    /**
     * Fetch TOC (table of contents) page and parse chapter list.
     */
    override suspend fun getTOC(tocUrl: String, source: BookSource): List<TOCItem> {
        val url = resolveUrl(tocUrl, source.sourceUrl)
            ?: throw ReaderException(
                error = ReaderError(
                    code = ReaderErrorCode.PARSE,
                    stage = ReaderFailureStage.TOC,
                    message = "tocUrl 为空",
                    sourceName = source.sourceName
                )
            )
        val headers = buildHeaders(source)

        return runCatching {
            val response = transport.get(url, headers)
            if (response.code != 200) {
                throw ReaderException(
                    error = ReaderError(
                        code = ReaderErrorCode.NETWORK,
                        stage = ReaderFailureStage.TOC,
                        message = "HTTP ${response.code}",
                        sourceName = source.sourceName
                    )
                )
            }
            val toc = tocParser.parseTOCResponse(response.body, source.ruleToc)
            toc.map { normalizeTocUrls(it, source.sourceUrl) }
        }.getOrElse { e ->
            if (e is ReaderException) throw e
            throw ReaderException(
                error = ReaderError(
                    code = ReaderErrorCode.NETWORK,
                    stage = ReaderFailureStage.TOC,
                    message = e.message,
                    sourceName = source.sourceName
                )
            )
        }
    }

    /**
     * Fetch chapter content page, parse and clean into [ContentPage].
     */
    override suspend fun getContent(contentUrl: String, source: BookSource): ContentPage {
        val url = resolveUrl(contentUrl, source.sourceUrl)
            ?: throw ReaderException(
                error = ReaderError(
                    code = ReaderErrorCode.PARSE,
                    stage = ReaderFailureStage.CONTENT,
                    message = "contentUrl 为空",
                    sourceName = source.sourceName
                )
            )
        val headers = buildHeaders(source)

        return runCatching {
            val response = transport.get(url, headers)
            if (response.code != 200) {
                throw ReaderException(
                    error = ReaderError(
                        code = ReaderErrorCode.NETWORK,
                        stage = ReaderFailureStage.CONTENT,
                        message = "HTTP ${response.code}",
                        sourceName = source.sourceName
                    )
                )
            }
            val raw = contentParser.parseContentResponse(response.body, source.ruleContent)
                ?: throw ReaderException(
                    error = ReaderError(
                        code = ReaderErrorCode.PARSE,
                        stage = ReaderFailureStage.CONTENT,
                        message = "无法解析章节正文",
                        sourceName = source.sourceName
                    )
                )
            raw.copy(nextPageUrl = normalizeUrl(raw.nextPageUrl, source.sourceUrl))
        }.getOrElse { e ->
            if (e is ReaderException) throw e
            throw ReaderException(
                error = ReaderError(
                    code = ReaderErrorCode.NETWORK,
                    stage = ReaderFailureStage.CONTENT,
                    message = e.message,
                    sourceName = source.sourceName
                )
            )
        }
    }

    // ── Helpers ──

    private fun buildHeaders(source: BookSource): Map<String, String> {
        val headers = RequestHeaders().toMap().toMutableMap()
        source.header?.let { custom ->
            // Simple key:value header parsing, one per line
            custom.lines().forEach { line ->
                val parts = line.split(":", limit = 2)
                if (parts.size == 2) {
                    headers[parts[0].trim()] = parts[1].trim()
                }
            }
        }
        return headers
    }

    /**
     * Attach cookies for [url] from the wired [cookieStore] (when present) as a
     * `Cookie` header. This keeps OkHttp transport in sync with the WebView
     * CookieManager mirror during a source run.
     */
    private suspend fun withCookies(headers: Map<String, String>, url: String): Map<String, String> {
        val store = cookieStore ?: return headers
        val scope = store.get(url)
        if (scope.cookies.isEmpty()) return headers
        val existing = headers["Cookie"]
        val cookieHeader = scope.getCookieString()
        val merged = if (existing.isNullOrBlank()) cookieHeader else "$existing; $cookieHeader"
        return headers + ("Cookie" to merged)
    }

    /** Build the POST body for a search request from the keyword + source charset. */
    private fun searchBody(keyword: String, source: BookSource): PostRequestBody {
        val charset = source.searchCharset?.ifBlank { null } ?: "UTF-8"
        return PostRequestBody.formUrlEncoded(mapOf("key" to keyword, "charset" to charset))
    }

    /**
     * Resolve a URL against the book source base URL.
     * If [target] starts with http:// or https://, return it as-is.
     * Otherwise join it with the base URL from [baseUrl].
     */
    private fun resolveUrl(target: String?, baseUrl: String): String? {
        if (target.isNullOrBlank()) return null
        if (target.startsWith("http://") || target.startsWith("https://")) {
            return target
        }
        val base = baseUrl.trimEnd('/')
        val path = target.trimStart('/')
        return "$base/$path"
    }

    /**
     * Normalize a URL from parser output to absolute form.
     * Keeps null/blank as-is, converts relative URLs to absolute.
     */
    private fun normalizeUrl(url: String?, baseUrl: String): String? {
        if (url.isNullOrBlank()) return url
        if (url.startsWith("http://") || url.startsWith("https://")) return url
        val base = baseUrl.trimEnd('/')
        val path = url.trimStart('/')
        return "$base/$path"
    }

    /** Recursively normalize all URLs in TOC tree. */
    private fun normalizeTocUrls(item: TOCItem, baseUrl: String): TOCItem {
        return item.copy(
            url = normalizeUrl(item.url, baseUrl) ?: item.url,
            children = item.children.map { normalizeTocUrls(it, baseUrl) }
        )
    }
}

/**
 * Internal exception carrying structured [ReaderError].
 * Caught by bridge callers to map into [BridgeResult.Failure].
 */
class ReaderException(val error: ReaderError) : Exception(error.message)
