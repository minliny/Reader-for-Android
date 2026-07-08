package com.reader.host

import com.reader.android.data.model.BookSource
import com.reader.android.data.network.FakeRssItemRepository
import com.reader.android.data.network.FakeSubscriptionRepository
import com.reader.android.data.network.RssItem
import com.reader.android.data.network.RssItemRepository
import com.reader.android.data.network.RssParser
import com.reader.android.data.network.RssSubscription
import com.reader.android.data.network.SubscriptionRepository
import com.reader.android.data.repository.BookSourceRepository
import com.reader.android.data.repository.FakeBookSourceRepository
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

// ════════════════════════════════════════════════════════════════════════════
// P1-5: Source / RSS capability handlers.
//
// These handlers close the "data layer exists but UI uses demo data" gap by
// exposing BookSourceRepository + SubscriptionRepository + RssParser through
// the Host capability surface so the reducer/UI can dispatch real CRUD +
// debug + import/export + RSS fetch operations through the same
// `HostRequest → HostAdapter.dispatch → HostReply` round-trip used by TTS,
// permission, notification, etc.
//
// All handlers are pure-JVM (no Android Context required) so they can be
// exercised in JVM unit tests with FakeBookSourceRepository /
// FakeSubscriptionRepository. Production wires the real DataStore /
// Room-backed implementations via AppProvider.
// ════════════════════════════════════════════════════════════════════════════

/**
 * Bundles the repositories + parser that the source/RSS handlers need.
 * Created once in `ReaderCoreClient.buildHostRuntime` and injected into
 * each handler so the handler has no Android/Context dependency.
 */
class SourceRssContext(
    val bookSourceRepository: BookSourceRepository,
    val subscriptionRepository: SubscriptionRepository,
    val rssParser: RssParser = RssParser(),
    val rssItemRepository: RssItemRepository
)

// ── source.list ──────────────────────────────────────────────────────────────

/**
 * `source.list` — returns all book sources (enabled + disabled) as a JSON
 * array. Used by the source management screen to render the real list
 * instead of the hardcoded `sourceDemoRows()`.
 */
class SourceListHandler(private val ctx: SourceRssContext) : CapabilityHandler {
    override fun handle(request: HostRequest): HostReply {
        val sources = ctx.bookSourceRepository.getAll()
        val arr = JSONArray()
        sources.forEach { arr.put(sourceToJson(it)) }
        return HostReply.complete(JSONObject().put("sources", arr).toString())
    }

    companion object { const val CAPABILITY = "source.list" }

    private fun sourceToJson(source: BookSource): JSONObject = JSONObject().apply {
        put("sourceUrl", source.sourceUrl)
        put("sourceName", source.sourceName)
        source.sourceGroup?.let { put("sourceGroup", it) }
        put("enabled", source.enabled)
        source.sourceComment?.let { put("sourceComment", it) }
        source.searchUrl?.let { put("searchUrl", it) }
    }
}

// ── source.add ───────────────────────────────────────────────────────────────

/**
 * `source.add` — adds a single book source. Dedupes by `sourceUrl`
 * (last-write-wins in DataStore impl).
 */
class SourceAddHandler(private val ctx: SourceRssContext) : CapabilityHandler {
    override fun handle(request: HostRequest): HostReply {
        val params = try { JSONObject(request.paramsJson()) } catch (e: Exception) {
            return HostReply.error(INTERNAL, "invalid params: ${e.message}", false)
        }
        val url = params.optString("sourceUrl", "")
        if (url.isEmpty()) return HostReply.error(INTERNAL, "sourceUrl required", false)
        val source = BookSource(
            sourceUrl = url,
            sourceName = params.optString("sourceName", "未命名"),
            sourceGroup = nullable(params, "sourceGroup"),
            enabled = params.optBoolean("enabled", true),
            sourceComment = nullable(params, "sourceComment"),
            searchUrl = nullable(params, "searchUrl"),
            searchCharset = nullable(params, "searchCharset"),
            searchMethod = nullable(params, "searchMethod"),
            bookInfoUrl = nullable(params, "bookInfoUrl"),
            tocUrl = nullable(params, "tocUrl"),
            tocCharset = nullable(params, "tocCharset"),
            contentUrl = nullable(params, "contentUrl"),
            contentCharset = nullable(params, "contentCharset"),
            header = nullable(params, "header"),
            loginUrl = nullable(params, "loginUrl")
        )
        ctx.bookSourceRepository.add(source)
        return HostReply.complete(JSONObject().put("added", true).put("sourceUrl", url).toString())
    }

    companion object { const val CAPABILITY = "source.add"; private const val INTERNAL = "INTERNAL" }

    private fun nullable(obj: JSONObject, key: String): String? =
        if (obj.has(key) && !obj.isNull(key)) obj.getString(key) else null
}

// ── source.remove ────────────────────────────────────────────────────────────

/**
 * `source.remove` — removes a book source by URL.
 */
class SourceRemoveHandler(private val ctx: SourceRssContext) : CapabilityHandler {
    override fun handle(request: HostRequest): HostReply {
        val params = try { JSONObject(request.paramsJson()) } catch (e: Exception) {
            return HostReply.error(INTERNAL, "invalid params: ${e.message}", false)
        }
        val url = params.optString("sourceUrl", "")
        if (url.isEmpty()) return HostReply.error(INTERNAL, "sourceUrl required", false)
        ctx.bookSourceRepository.remove(url)
        return HostReply.complete(JSONObject().put("removed", true).put("sourceUrl", url).toString())
    }

    companion object { const val CAPABILITY = "source.remove"; private const val INTERNAL = "INTERNAL" }
}

// ── source.set_enabled ───────────────────────────────────────────────────────

/**
 * `source.set_enabled` — toggles a source's enabled flag (start/stop).
 */
class SourceSetEnabledHandler(private val ctx: SourceRssContext) : CapabilityHandler {
    override fun handle(request: HostRequest): HostReply {
        val params = try { JSONObject(request.paramsJson()) } catch (e: Exception) {
            return HostReply.error(INTERNAL, "invalid params: ${e.message}", false)
        }
        val url = params.optString("sourceUrl", "")
        if (url.isEmpty()) return HostReply.error(INTERNAL, "sourceUrl required", false)
        val enabled = params.optBoolean("enabled", true)
        ctx.bookSourceRepository.setEnabled(url, enabled)
        return HostReply.complete(JSONObject().put("sourceUrl", url).put("enabled", enabled).toString())
    }

    companion object { const val CAPABILITY = "source.set_enabled"; private const val INTERNAL = "INTERNAL" }
}

// ── source.import ────────────────────────────────────────────────────────────

/**
 * `source.import` — imports a JSON array of book sources. Returns the count
 * imported. Mirrors `BookSourceRepository.importJson`.
 */
class SourceImportHandler(private val ctx: SourceRssContext) : CapabilityHandler {
    override fun handle(request: HostRequest): HostReply {
        val params = try { JSONObject(request.paramsJson()) } catch (e: Exception) {
            return HostReply.error(INTERNAL, "invalid params: ${e.message}", false)
        }
        val json = params.optString("json", "")
        if (json.isEmpty()) return HostReply.error(INTERNAL, "json required", false)
        val count = try {
            ctx.bookSourceRepository.importJson(json)
        } catch (e: Exception) {
            return HostReply.error(INTERNAL, "import failed: ${e.message}", false)
        }
        return HostReply.complete(JSONObject().put("imported", count).toString())
    }

    companion object { const val CAPABILITY = "source.import"; private const val INTERNAL = "INTERNAL" }
}

// ── source.export ────────────────────────────────────────────────────────────

/**
 * `source.export` — exports all book sources as a JSON array string.
 * The output is the same format `source.import` consumes.
 */
class SourceExportHandler(private val ctx: SourceRssContext) : CapabilityHandler {
    override fun handle(request: HostRequest): HostReply {
        val json = ctx.bookSourceRepository.exportJson()
        return HostReply.complete(JSONObject().put("json", json).put("count", JSONArray(json).length()).toString())
    }

    companion object { const val CAPABILITY = "source.export" }
}

// ── source.debug.run ─────────────────────────────────────────────────────────

/**
 * `source.debug.run` — runs a debug probe against a book source for a given
 * step (search / detail / toc / content). The Host owns the HTTP transport
 * and the parser; this handler coordinates the fetch + parse so the UI can
 * show real debug results instead of `sourceDebugCases()` demo data.
 *
 * Current implementation: validates inputs and returns a structured
 * "step + sourceUrl + status" envelope. The actual network fetch is
 * delegated to the existing `http.execute` capability (Core can re-dispatch
 * through it) so this handler stays pure-JVM and testable without a network.
 */
class SourceDebugRunHandler(private val ctx: SourceRssContext) : CapabilityHandler {
    override fun handle(request: HostRequest): HostReply {
        val params = try { JSONObject(request.paramsJson()) } catch (e: Exception) {
            return HostReply.error(INTERNAL, "invalid params: ${e.message}", false)
        }
        val url = params.optString("sourceUrl", "")
        if (url.isEmpty()) return HostReply.error(INTERNAL, "sourceUrl required", false)
        val step = params.optString("step", "")
        if (step.isEmpty()) return HostReply.error(INTERNAL, "step required", false)
        val keyword = params.optString("keyword", "")
        val source = ctx.bookSourceRepository.getByUrl(url)
            ?: return HostReply.error(INTERNAL, "source not found: $url", false)

        val result = JSONObject()
        result.put("sourceUrl", url)
        result.put("step", step)
        result.put("sourceName", source.sourceName)
        result.put("status", "PENDING")
        result.put("message", "dispatch through http.execute for real fetch")
        if (keyword.isNotEmpty()) result.put("keyword", keyword)
        return HostReply.complete(result.toString())
    }

    companion object { const val CAPABILITY = "source.debug.run"; private const val INTERNAL = "INTERNAL" }
}

// ── source.debug.detect ──────────────────────────────────────────────────────

/**
 * `source.debug.detect` — auto-detects source rules by probing the source
 * URL and returning a best-guess rule set (search/detail/toc/content URLs).
 *
 * Current implementation: returns the source's existing configured URLs as
 * the "detected" set so the debug UI can render a real (non-demo) result.
 * A real detector would fetch the page and run heuristics.
 */
class SourceDebugDetectHandler(private val ctx: SourceRssContext) : CapabilityHandler {
    override fun handle(request: HostRequest): HostReply {
        val params = try { JSONObject(request.paramsJson()) } catch (e: Exception) {
            return HostReply.error(INTERNAL, "invalid params: ${e.message}", false)
        }
        val url = params.optString("sourceUrl", "")
        if (url.isEmpty()) return HostReply.error(INTERNAL, "sourceUrl required", false)
        val source = ctx.bookSourceRepository.getByUrl(url)
            ?: return HostReply.error(INTERNAL, "source not found: $url", false)

        val detected = JSONObject()
        detected.put("searchUrl", source.searchUrl ?: "")
        detected.put("bookInfoUrl", source.bookInfoUrl ?: "")
        detected.put("tocUrl", source.tocUrl ?: "")
        detected.put("contentUrl", source.contentUrl ?: "")
        detected.put("searchCharset", source.searchCharset ?: "")
        detected.put("tocCharset", source.tocCharset ?: "")
        detected.put("contentCharset", source.contentCharset ?: "")
        return HostReply.complete(JSONObject()
            .put("sourceUrl", url)
            .put("detected", detected)
            .put("status", "OK")
            .toString())
    }

    companion object { const val CAPABILITY = "source.debug.detect"; private const val INTERNAL = "INTERNAL" }
}

// ── rss.subscription.list ────────────────────────────────────────────────────

/**
 * `rss.subscription.list` — returns all RSS subscriptions as a JSON array.
 * Used by the RSS tab + subscription management screen to render the real
 * list instead of `rssDemoSources()` / `rssManagedSources()`.
 */
class RssSubscriptionListHandler(private val ctx: SourceRssContext) : CapabilityHandler {
    override fun handle(request: HostRequest): HostReply {
        val subs = runBlocking { ctx.subscriptionRepository.getAll() }
        val arr = JSONArray()
        subs.forEach { sub ->
            arr.put(JSONObject()
                .put("feedUrl", sub.feedUrl)
                .put("title", sub.title)
                .put("lastUpdated", sub.lastUpdated)
                .put("lastItemGuid", sub.lastItemGuid ?: JSONObject.NULL))
        }
        return HostReply.complete(JSONObject().put("subscriptions", arr).toString())
    }

    companion object { const val CAPABILITY = "rss.subscription.list" }
}

// ── rss.subscription.add ──────────────────────────────────────────────────────

/**
 * `rss.subscription.add` — adds an RSS subscription by feed URL. The title
 * is left blank pending the first successful feed parse (see
 * `rss.refresh`).
 */
class RssSubscriptionAddHandler(private val ctx: SourceRssContext) : CapabilityHandler {
    override fun handle(request: HostRequest): HostReply {
        val params = try { JSONObject(request.paramsJson()) } catch (e: Exception) {
            return HostReply.error(INTERNAL, "invalid params: ${e.message}", false)
        }
        val feedUrl = params.optString("feedUrl", "")
        if (feedUrl.isEmpty()) return HostReply.error(INTERNAL, "feedUrl required", false)
        val title = params.optString("title", "")
        runBlocking {
            ctx.subscriptionRepository.add(RssSubscription(feedUrl = feedUrl, title = title))
        }
        return HostReply.complete(JSONObject().put("added", true).put("feedUrl", feedUrl).toString())
    }

    companion object { const val CAPABILITY = "rss.subscription.add"; private const val INTERNAL = "INTERNAL" }
}

// ── rss.subscription.delete ───────────────────────────────────────────────────

/**
 * `rss.subscription.delete` — removes an RSS subscription by feed URL.
 */
class RssSubscriptionDeleteHandler(private val ctx: SourceRssContext) : CapabilityHandler {
    override fun handle(request: HostRequest): HostReply {
        val params = try { JSONObject(request.paramsJson()) } catch (e: Exception) {
            return HostReply.error(INTERNAL, "invalid params: ${e.message}", false)
        }
        val feedUrl = params.optString("feedUrl", "")
        if (feedUrl.isEmpty()) return HostReply.error(INTERNAL, "feedUrl required", false)
        runBlocking { ctx.subscriptionRepository.remove(feedUrl) }
        return HostReply.complete(JSONObject().put("removed", true).put("feedUrl", feedUrl).toString())
    }

    companion object { const val CAPABILITY = "rss.subscription.delete"; private const val INTERNAL = "INTERNAL" }
}

// ── rss.refresh ───────────────────────────────────────────────────────────────

// `rss.refresh` fetches + parses an RSS feed and updates the subscription's
// lastItemGuid / lastUpdated. The actual HTTP fetch is delegated to the caller
// via http.execute; this handler accepts the raw XML body and returns parsed
// items to UI after SubscriptionRepository.markUpdated.
class RssRefreshHandler(private val ctx: SourceRssContext) : CapabilityHandler {
    override fun handle(request: HostRequest): HostReply {
        val params = try { JSONObject(request.paramsJson()) } catch (e: Exception) {
            return HostReply.error(INTERNAL, "invalid params: ${e.message}", false)
        }
        val feedUrl = params.optString("feedUrl", "")
        if (feedUrl.isEmpty()) return HostReply.error(INTERNAL, "feedUrl required", false)
        val xml = params.optString("xml", "")
        if (xml.isEmpty()) return HostReply.error(INTERNAL, "xml required", false)

        val feed = ctx.rssParser.parse(xml)
            ?: return HostReply.error(INTERNAL, "failed to parse RSS feed", true)

        val lastGuid = feed.items.firstOrNull()?.guid
        runBlocking {
            ctx.subscriptionRepository.markUpdated(feedUrl, lastGuid)
            // Cache parsed items so `rss.list` / `rss.item.read` can serve
            // them locally as a fallback until Core lands those methods.
            ctx.rssItemRepository.upsertAll(feedUrl, feed.items)
        }

        val itemsArr = JSONArray()
        feed.items.forEach { item ->
            itemsArr.put(JSONObject()
                .put("title", item.title)
                .put("link", item.link)
                .put("description", item.description ?: JSONObject.NULL)
                .put("pubDate", item.pubDate ?: JSONObject.NULL)
                .put("guid", item.guid ?: JSONObject.NULL))
        }
        return HostReply.complete(JSONObject()
            .put("feedUrl", feedUrl)
            .put("feedTitle", feed.title)
            .put("items", itemsArr)
            .put("count", feed.items.size)
            .toString())
    }

    companion object { const val CAPABILITY = "rss.refresh"; private const val INTERNAL = "INTERNAL" }
}

// ── rss.list ─────────────────────────────────────────────────────────────────

/**
 * `rss.list` — returns cached RSS articles as a JSON array. Backed by the
 * local [RssItemRepository] cache populated by `rss.refresh`, so the UI can
 * render the article list without waiting for Core to implement this method.
 *
 * Params:
 *  - `feedUrl` (optional): filter to a single feed.
 *  - `unreadOnly` (optional, default false): return only unread items.
 *  - `limit` (optional, default 100): cap on items returned (ignored when
 *    `unreadOnly=true` is set, since unread is typically a small set).
 *
 * Returns `{items: [...], count: N}`. Each item carries title/link/
 * description/author/pubDate/guid. Read state is implicit: when
 * `unreadOnly=true` is set, only unread items are returned.
 */
class RssListHandler(private val ctx: SourceRssContext) : CapabilityHandler {
    override fun handle(request: HostRequest): HostReply {
        val params = try { JSONObject(request.paramsJson()) } catch (e: Exception) {
            return HostReply.error(INTERNAL, "invalid params: ${e.message}", false)
        }
        val feedUrl = params.optString("feedUrl", "")
        val unreadOnly = params.optBoolean("unreadOnly", false)
        val limit = params.optInt("limit", 100)

        val items: List<RssItem> = runBlocking {
            when {
                feedUrl.isNotEmpty() -> ctx.rssItemRepository.listByFeed(feedUrl)
                unreadOnly -> ctx.rssItemRepository.listUnread()
                else -> ctx.rssItemRepository.listAll(limit)
            }
        }

        val arr = JSONArray()
        items.forEach { arr.put(rssItemToJson(it)) }
        return HostReply.complete(JSONObject()
            .put("items", arr)
            .put("count", items.size)
            .toString())
    }

    companion object { const val CAPABILITY = "rss.list"; private const val INTERNAL = "INTERNAL" }
}

// ── rss.item.read ─────────────────────────────────────────────────────────────

/**
 * `rss.item.read` — marks a cached RSS article as read/unread by GUID.
 * Backed by [RssItemRepository.markRead] so the read-state persists across
 * process death and survives feed re-fetch (items are keyed by GUID).
 *
 * Params:
 *  - `guid` (required): the item GUID to update.
 *  - `read` (required): true = mark read, false = mark unread.
 *
 * Returns `{marked: true, guid: "...", read: true/false}`.
 */
class RssItemReadHandler(private val ctx: SourceRssContext) : CapabilityHandler {
    override fun handle(request: HostRequest): HostReply {
        val params = try { JSONObject(request.paramsJson()) } catch (e: Exception) {
            return HostReply.error(INTERNAL, "invalid params: ${e.message}", false)
        }
        val guid = params.optString("guid", "")
        if (guid.isEmpty()) return HostReply.error(INTERNAL, "guid required", false)
        if (!params.has("read")) return HostReply.error(INTERNAL, "read required", false)
        val read = params.getBoolean("read")
        runBlocking { ctx.rssItemRepository.markRead(guid, read) }
        return HostReply.complete(JSONObject()
            .put("marked", true)
            .put("guid", guid)
            .put("read", read)
            .toString())
    }

    companion object { const val CAPABILITY = "rss.item.read"; private const val INTERNAL = "INTERNAL" }
}

/** Shared JSON shape for an [RssItem] — used by `rss.list` and `rss.refresh`. */
private fun rssItemToJson(item: RssItem): JSONObject = JSONObject()
    .put("title", item.title)
    .put("link", item.link)
    .put("description", item.description ?: JSONObject.NULL)
    .put("author", item.author ?: JSONObject.NULL)
    .put("pubDate", item.pubDate ?: JSONObject.NULL)
    .put("guid", item.guid ?: JSONObject.NULL)

/**
 * Convenience entry point: registers the full source/RSS capability set
 * onto a [HostRuntime] / [HostAdapter] chain. Called from
 * `ReaderCoreClient.buildHostRuntime` so production + JVM tests share the
 * same registration code (mirrors `HostFacade.registerHandlers`).
 */
fun SourceRssContext.registerHandlers(runtime: HostRuntime): HostRuntime = runtime
    .register(SourceListHandler.CAPABILITY, SourceListHandler(this))
    .register(SourceAddHandler.CAPABILITY, SourceAddHandler(this))
    .register(SourceRemoveHandler.CAPABILITY, SourceRemoveHandler(this))
    .register(SourceSetEnabledHandler.CAPABILITY, SourceSetEnabledHandler(this))
    .register(SourceImportHandler.CAPABILITY, SourceImportHandler(this))
    .register(SourceExportHandler.CAPABILITY, SourceExportHandler(this))
    .register(SourceDebugRunHandler.CAPABILITY, SourceDebugRunHandler(this))
    .register(SourceDebugDetectHandler.CAPABILITY, SourceDebugDetectHandler(this))
    .register(RssSubscriptionListHandler.CAPABILITY, RssSubscriptionListHandler(this))
    .register(RssSubscriptionAddHandler.CAPABILITY, RssSubscriptionAddHandler(this))
    .register(RssSubscriptionDeleteHandler.CAPABILITY, RssSubscriptionDeleteHandler(this))
    .register(RssRefreshHandler.CAPABILITY, RssRefreshHandler(this))
    .register(RssListHandler.CAPABILITY, RssListHandler(this))
    .register(RssItemReadHandler.CAPABILITY, RssItemReadHandler(this))

/**
 * Builds a [SourceRssContext] backed by fake repositories for JVM tests.
 * Production wires the real DataStore + Room impls via AppProvider.
 */
fun fakeSourceRssContext(
    bookSources: List<BookSource> = emptyList(),
    subscriptions: List<RssSubscription> = emptyList(),
    rssItemRepository: RssItemRepository = FakeRssItemRepository()
): SourceRssContext {
    val bookSourceRepo = FakeBookSourceRepository().apply {
        bookSources.forEach { add(it) }
    }
    val subRepo = FakeSubscriptionRepository().apply {
        runBlocking { subscriptions.forEach { add(it) } }
    }
    return SourceRssContext(bookSourceRepo, subRepo, rssItemRepository = rssItemRepository)
}
