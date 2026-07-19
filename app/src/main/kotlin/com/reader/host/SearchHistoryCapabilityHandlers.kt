package com.reader.host

import com.reader.api.ReaderCoreClient
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

/**
 * Slice 10 Core-only search-history boundary.
 *
 * Search history is a business entity. Android must never keep a Room/DataStore
 * fallback when Core is unavailable, because a fallback can acknowledge a write
 * that Core never persisted and creates two competing sources of truth.
 */
fun interface SearchHistoryCoreGateway {
    suspend fun send(method: String, params: JSONObject): JSONObject
}

class ReaderCoreSearchHistoryGateway(
    private val coreProvider: () -> ReaderCoreClient = ReaderCoreClient::get
) : SearchHistoryCoreGateway {
    override suspend fun send(method: String, params: JSONObject): JSONObject =
        coreProvider().sendAndAwait(method, params, CORE_TIMEOUT_MILLIS)

    private companion object {
        const val CORE_TIMEOUT_MILLIS = 10_000L
    }
}

class SearchHistoryContext(val core: SearchHistoryCoreGateway)

class SearchHistoryListHandler(private val ctx: SearchHistoryContext) : CapabilityHandler {
    override fun handle(request: HostRequest): HostReply {
        val params = parseParams(request) ?: return invalidParams()
        return callCore(ctx, CAPABILITY, params)
    }

    companion object { const val CAPABILITY = "search.history.list" }
}

class SearchHistoryAddHandler(private val ctx: SearchHistoryContext) : CapabilityHandler {
    override fun handle(request: HostRequest): HostReply {
        val params = parseParams(request) ?: return invalidParams()
        if (params.optString("keyword", "").trim().isEmpty()) {
            return HostReply.error("INVALID_PARAMS", "keyword must not be empty", false)
        }
        return callCore(ctx, CAPABILITY, params)
    }

    companion object { const val CAPABILITY = "search.history.add" }
}

class SearchHistoryClearHandler(private val ctx: SearchHistoryContext) : CapabilityHandler {
    override fun handle(request: HostRequest): HostReply {
        val params = parseParams(request) ?: return invalidParams()
        if (params.length() != 0) {
            return HostReply.error("INVALID_PARAMS", "search.history.clear accepts no params", false)
        }
        return callCore(ctx, CAPABILITY, params)
    }

    companion object { const val CAPABILITY = "search.history.clear" }
}

fun SearchHistoryContext.registerHandlers(runtime: HostRuntime): HostRuntime = runtime
    .register(SearchHistoryListHandler.CAPABILITY, SearchHistoryListHandler(this))
    .register(SearchHistoryAddHandler.CAPABILITY, SearchHistoryAddHandler(this))
    .register(SearchHistoryClearHandler.CAPABILITY, SearchHistoryClearHandler(this))

private fun parseParams(request: HostRequest): JSONObject? =
    runCatching { JSONObject(request.paramsJson()) }.getOrNull()

private fun invalidParams(): HostReply =
    HostReply.error("INVALID_PARAMS", "payload must be a JSON object", false)

private fun callCore(
    context: SearchHistoryContext,
    method: String,
    params: JSONObject
): HostReply = try {
    val result = runBlocking { context.core.send(method, params) }
    HostReply.complete(result.toString())
} catch (error: Exception) {
    HostReply.error(
        "CORE_UNAVAILABLE",
        "$method requires the canonical Reader Core store",
        true
    )
}

/**
 * Deterministic Core-shaped test gateway used only by context-null JVM host
 * registration tests. Production always uses [ReaderCoreSearchHistoryGateway].
 */
fun fakeSearchHistoryContext(): SearchHistoryContext {
    val keywords = mutableListOf<String>()
    return SearchHistoryContext(
        SearchHistoryCoreGateway { method, params ->
            when (method) {
                SearchHistoryListHandler.CAPABILITY -> {
                    val limit = params.optInt("limit", 20).coerceAtLeast(0)
                    val values = keywords.take(limit)
                    JSONObject()
                        .put("keywords", JSONArray(values))
                        .put("count", values.size)
                }
                SearchHistoryAddHandler.CAPABILITY -> {
                    val keyword = params.getString("keyword").trim()
                    keywords.remove(keyword)
                    keywords.add(0, keyword)
                    JSONObject().put("added", true).put("keyword", keyword)
                }
                SearchHistoryClearHandler.CAPABILITY -> {
                    keywords.clear()
                    JSONObject().put("cleared", true)
                }
                else -> error("unsupported fake Core method: $method")
            }
        }
    )
}
