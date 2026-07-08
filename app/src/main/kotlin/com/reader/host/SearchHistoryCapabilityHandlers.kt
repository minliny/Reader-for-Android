package com.reader.host

import com.reader.android.data.repository.FakeSearchHistoryRepository
import com.reader.android.data.repository.SearchHistoryRepository
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

// ════════════════════════════════════════════════════════════════════════════
// P2: Search history capability handlers.
//
// Local Room-backed fallback for `search.history.list` / `add` / `clear`
// until Core lands these protocol methods (PAGE_REFERENCE.md marks
// `searchHistory` as [C] Core-owned). Exposing them through the Host
// capability surface means the UI dispatches through the same
// `HostRequest → HostAdapter.dispatch → HostReply` round-trip used by
// source / RSS / WebDAV handlers — when Core lands the real methods, only
// the repository backing needs to swap (the UI dispatch code stays).
//
// All handlers are pure-JVM (no Android Context required) so they can be
// exercised in JVM unit tests with [FakeSearchHistoryRepository]. Production
// wires the Room-backed [com.reader.android.data.repository.RoomSearchHistoryRepository]
// via [com.reader.android.AppProvider.searchHistoryRepository].
// ════════════════════════════════════════════════════════════════════════════

/**
 * Bundles the [SearchHistoryRepository] that the `search.history.*` handlers
 * need. Created once in `ReaderCoreClient.buildHostRuntime` and injected into
 * each handler so the handler has no Android/Context dependency.
 */
class SearchHistoryContext(val repository: SearchHistoryRepository)

// ── search.history.list ──────────────────────────────────────────────────────

/**
 * `search.history.list` — returns recent search keywords ordered by recency.
 * Optional `limit` param (default 20).
 *
 * Returns `{keywords: [...], count: N}`.
 */
class SearchHistoryListHandler(private val ctx: SearchHistoryContext) : CapabilityHandler {
    override fun handle(request: HostRequest): HostReply {
        val params = try { JSONObject(request.paramsJson()) } catch (e: Exception) {
            return HostReply.error(INTERNAL, "invalid params: ${e.message}", false)
        }
        val limit = params.optInt("limit", 20)
        val keywords = runBlocking { ctx.repository.list(limit) }
        val arr = JSONArray().apply { keywords.forEach { put(it) } }
        val result = JSONObject().put("keywords", arr).put("count", keywords.size)
        return HostReply.complete(result.toString())
    }

    companion object { const val CAPABILITY = "search.history.list"; private const val INTERNAL = "INTERNAL" }
}

// ── search.history.add ───────────────────────────────────────────────────────

/**
 * `search.history.add` — adds (or re-touches) a keyword, moving it to the
 * top of the recency-ordered list. Dedupes by keyword.
 *
 * Returns `{added: true, keyword: "..."}`.
 */
class SearchHistoryAddHandler(private val ctx: SearchHistoryContext) : CapabilityHandler {
    override fun handle(request: HostRequest): HostReply {
        val params = try { JSONObject(request.paramsJson()) } catch (e: Exception) {
            return HostReply.error(INTERNAL, "invalid params: ${e.message}", false)
        }
        val keyword = params.optString("keyword", "").trim()
        if (keyword.isEmpty()) return HostReply.error(INTERNAL, "keyword must not be empty", true)
        runBlocking { ctx.repository.add(keyword) }
        return HostReply.complete(JSONObject().put("added", true).put("keyword", keyword).toString())
    }

    companion object { const val CAPABILITY = "search.history.add"; private const val INTERNAL = "INTERNAL" }
}

// ── search.history.clear ─────────────────────────────────────────────────────

/**
 * `search.history.clear` — clears all search history.
 * Returns `{cleared: true}`.
 */
class SearchHistoryClearHandler(private val ctx: SearchHistoryContext) : CapabilityHandler {
    override fun handle(request: HostRequest): HostReply {
        runBlocking { ctx.repository.clear() }
        return HostReply.complete(JSONObject().put("cleared", true).toString())
    }

    companion object { const val CAPABILITY = "search.history.clear" }
}

/**
 * Convenience entry point: registers the full `search.history.*` capability
 * set onto a [HostRuntime] chain. Called from `ReaderCoreClient.buildHostRuntime`
 * so production + JVM tests share the same registration code (mirrors
 * [SourceRssContext.registerHandlers] + [WebDavContext.registerHandlers]).
 */
fun SearchHistoryContext.registerHandlers(runtime: HostRuntime): HostRuntime = runtime
    .register(SearchHistoryListHandler.CAPABILITY, SearchHistoryListHandler(this))
    .register(SearchHistoryAddHandler.CAPABILITY, SearchHistoryAddHandler(this))
    .register(SearchHistoryClearHandler.CAPABILITY, SearchHistoryClearHandler(this))

/**
 * Builds a [SearchHistoryContext] backed by [FakeSearchHistoryRepository] for
 * JVM tests. Production wires the Room-backed implementation via AppProvider.
 */
fun fakeSearchHistoryContext(): SearchHistoryContext =
    SearchHistoryContext(FakeSearchHistoryRepository())
