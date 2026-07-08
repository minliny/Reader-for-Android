package com.reader.host

import com.reader.android.data.model.BookSource
import com.reader.android.data.network.RssSubscription
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM proof for the P1-5 Source / RSS capability handlers.
 *
 * Verifies the full CRUD + import/export + debug + RSS subscription +
 * RSS refresh flow against a [fakeSourceRssContext] (FakeBookSourceRepository
 * + FakeSubscriptionRepository). Production wires the same handlers over
 * DataStore + Room-backed repositories, so a regression in the handler logic
 * fails this test.
 *
 * Capability coverage:
 *  - source.list
 *  - source.add / remove / set_enabled
 *  - source.import / export
 *  - source.debug.run / source.debug.detect
 *  - rss.subscription.list / add / delete
 *  - rss.refresh (with real RssParser)
 */
class SourceRssCapabilityHandlersJvmTest {

    private fun makeCtx(
        sources: List<BookSource> = emptyList(),
        subs: List<RssSubscription> = emptyList()
    ) = fakeSourceRssContext(sources, subs)

    private fun req(capability: String, params: JSONObject = JSONObject()) =
        HostRequest(1L, 1L, capability, params.toString())

    private fun resultJson(reply: HostReply): JSONObject =
        JSONObject((reply as HostReply.Complete).resultJson())

    // ── source.list ──────────────────────────────────────────────────────

    @Test
    fun `source_list returns all sources`() {
        val ctx = makeCtx(sources = listOf(
            BookSource(sourceUrl = "https://a.com", sourceName = "A", enabled = true),
            BookSource(sourceUrl = "https://b.com", sourceName = "B", enabled = false)
        ))
        val reply = SourceListHandler(ctx).handle(req(SourceListHandler.CAPABILITY))
        assertTrue(reply.isComplete())
        val sources = resultJson(reply).getJSONArray("sources")
        assertEquals(2, sources.length())
        assertEquals("A", sources.getJSONObject(0).getString("sourceName"))
    }

    @Test
    fun `source_list returns empty array when no sources`() {
        val ctx = makeCtx()
        val reply = SourceListHandler(ctx).handle(req(SourceListHandler.CAPABILITY))
        assertTrue(reply.isComplete())
        assertEquals(0, resultJson(reply).getJSONArray("sources").length())
    }

    // ── source.add ────────────────────────────────────────────────────────

    @Test
    fun `source_add adds a source and source_list reflects it`() {
        val ctx = makeCtx()
        val params = JSONObject()
            .put("sourceUrl", "https://example.com")
            .put("sourceName", "Example")
            .put("enabled", true)
            .put("searchUrl", "https://example.com/search?q={{key}}")
        val reply = SourceAddHandler(ctx).handle(req(SourceAddHandler.CAPABILITY, params))
        assertTrue(reply.isComplete())
        assertTrue(resultJson(reply).getBoolean("added"))

        val listReply = SourceListHandler(ctx).handle(req(SourceListHandler.CAPABILITY))
        val sources = resultJson(listReply).getJSONArray("sources")
        assertEquals(1, sources.length())
        assertEquals("Example", sources.getJSONObject(0).getString("sourceName"))
        assertEquals("https://example.com/search?q={{key}}",
            sources.getJSONObject(0).getString("searchUrl"))
    }

    @Test
    fun `source_add rejects blank sourceUrl`() {
        val ctx = makeCtx()
        val reply = SourceAddHandler(ctx).handle(req(SourceAddHandler.CAPABILITY,
            JSONObject().put("sourceName", "No URL")))
        assertTrue(reply.isError())
        assertEquals("INTERNAL", (reply as HostReply.Error).code())
    }

    @Test
    fun `source_add dedupes by sourceUrl`() {
        val ctx = makeCtx()
        val params = JSONObject().put("sourceUrl", "https://a.com").put("sourceName", "Old")
        SourceAddHandler(ctx).handle(req(SourceAddHandler.CAPABILITY, params))
        val params2 = JSONObject().put("sourceUrl", "https://a.com").put("sourceName", "New")
        SourceAddHandler(ctx).handle(req(SourceAddHandler.CAPABILITY, params2))

        val list = SourceListHandler(ctx).handle(req(SourceListHandler.CAPABILITY))
        val sources = resultJson(list).getJSONArray("sources")
        assertEquals(1, sources.length())
        assertEquals("New", sources.getJSONObject(0).getString("sourceName"))
    }

    // ── source.remove ────────────────────────────────────────────────────

    @Test
    fun `source_remove removes a source`() {
        val ctx = makeCtx(sources = listOf(
            BookSource(sourceUrl = "https://a.com", sourceName = "A")
        ))
        val params = JSONObject().put("sourceUrl", "https://a.com")
        val reply = SourceRemoveHandler(ctx).handle(req(SourceRemoveHandler.CAPABILITY, params))
        assertTrue(reply.isComplete())
        assertTrue(resultJson(reply).getBoolean("removed"))
        assertEquals(0, ctx.bookSourceRepository.getAll().size)
    }

    @Test
    fun `source_remove rejects blank sourceUrl`() {
        val ctx = makeCtx()
        val reply = SourceRemoveHandler(ctx).handle(req(SourceRemoveHandler.CAPABILITY))
        assertTrue(reply.isError())
    }

    // ── source.set_enabled ────────────────────────────────────────────────

    @Test
    fun `source_set_enabled toggles enabled flag`() {
        val ctx = makeCtx(sources = listOf(
            BookSource(sourceUrl = "https://a.com", sourceName = "A", enabled = true)
        ))
        val params = JSONObject().put("sourceUrl", "https://a.com").put("enabled", false)
        val reply = SourceSetEnabledHandler(ctx).handle(req(SourceSetEnabledHandler.CAPABILITY, params))
        assertTrue(reply.isComplete())
        assertFalse(resultJson(reply).getBoolean("enabled"))
        assertFalse(ctx.bookSourceRepository.getByUrl("https://a.com")!!.enabled)
    }

    // ── source.import / export ───────────────────────────────────────────

    @Test
    fun `source_import then source_export round-trips`() {
        val ctx = makeCtx()
        val importJson = JSONArray()
            .put(JSONObject().put("sourceUrl", "https://a.com").put("sourceName", "A"))
            .put(JSONObject().put("sourceUrl", "https://b.com").put("sourceName", "B"))
            .toString()
        val importReply = SourceImportHandler(ctx).handle(
            req(SourceImportHandler.CAPABILITY, JSONObject().put("json", importJson))
        )
        assertTrue(importReply.isComplete())
        assertEquals(2, resultJson(importReply).getInt("imported"))

        val exportReply = SourceExportHandler(ctx).handle(req(SourceExportHandler.CAPABILITY))
        assertTrue(exportReply.isComplete())
        assertEquals(2, resultJson(exportReply).getInt("count"))
        val exportedArr = JSONArray(resultJson(exportReply).getString("json"))
        assertEquals(2, exportedArr.length())
    }

    @Test
    fun `source_export returns empty array when no sources`() {
        val ctx = makeCtx()
        val reply = SourceExportHandler(ctx).handle(req(SourceExportHandler.CAPABILITY))
        assertTrue(reply.isComplete())
        assertEquals(0, resultJson(reply).getInt("count"))
    }

    // ── source.debug.run ─────────────────────────────────────────────────

    @Test
    fun `source_debug_run returns pending envelope for known source`() {
        val ctx = makeCtx(sources = listOf(
            BookSource(sourceUrl = "https://a.com", sourceName = "A")
        ))
        val params = JSONObject()
            .put("sourceUrl", "https://a.com")
            .put("step", "search")
            .put("keyword", "test")
        val reply = SourceDebugRunHandler(ctx).handle(req(SourceDebugRunHandler.CAPABILITY, params))
        assertTrue(reply.isComplete())
        val result = resultJson(reply)
        assertEquals("search", result.getString("step"))
        assertEquals("PENDING", result.getString("status"))
        assertEquals("test", result.getString("keyword"))
    }

    @Test
    fun `source_debug_run rejects unknown source`() {
        val ctx = makeCtx()
        val reply = SourceDebugRunHandler(ctx).handle(req(SourceDebugRunHandler.CAPABILITY,
            JSONObject().put("sourceUrl", "https://unknown.com").put("step", "search")))
        assertTrue(reply.isError())
    }

    @Test
    fun `source_debug_run rejects missing step`() {
        val ctx = makeCtx(sources = listOf(
            BookSource(sourceUrl = "https://a.com", sourceName = "A")
        ))
        val reply = SourceDebugRunHandler(ctx).handle(req(SourceDebugRunHandler.CAPABILITY,
            JSONObject().put("sourceUrl", "https://a.com")))
        assertTrue(reply.isError())
    }

    // ── source.debug.detect ──────────────────────────────────────────────

    @Test
    fun `source_debug_detect returns configured urls`() {
        val ctx = makeCtx(sources = listOf(
            BookSource(
                sourceUrl = "https://a.com",
                sourceName = "A",
                searchUrl = "https://a.com/search",
                tocUrl = "https://a.com/toc",
                contentUrl = "https://a.com/content"
            )
        ))
        val reply = SourceDebugDetectHandler(ctx).handle(
            req(SourceDebugDetectHandler.CAPABILITY, JSONObject().put("sourceUrl", "https://a.com"))
        )
        assertTrue(reply.isComplete())
        val detected = resultJson(reply).getJSONObject("detected")
        assertEquals("https://a.com/search", detected.getString("searchUrl"))
        assertEquals("https://a.com/toc", detected.getString("tocUrl"))
        assertEquals("https://a.com/content", detected.getString("contentUrl"))
        assertEquals("OK", resultJson(reply).getString("status"))
    }

    @Test
    fun `source_debug_detect rejects unknown source`() {
        val ctx = makeCtx()
        val reply = SourceDebugDetectHandler(ctx).handle(
            req(SourceDebugDetectHandler.CAPABILITY, JSONObject().put("sourceUrl", "https://unknown.com"))
        )
        assertTrue(reply.isError())
    }

    // ── rss.subscription.list ────────────────────────────────────────────

    @Test
    fun `rss_subscription_list returns all subscriptions`() {
        val ctx = makeCtx(subs = listOf(
            RssSubscription(feedUrl = "https://feed1.com", title = "Feed 1"),
            RssSubscription(feedUrl = "https://feed2.com", title = "Feed 2")
        ))
        val reply = RssSubscriptionListHandler(ctx).handle(req(RssSubscriptionListHandler.CAPABILITY))
        assertTrue(reply.isComplete())
        val subs = resultJson(reply).getJSONArray("subscriptions")
        assertEquals(2, subs.length())
        assertEquals("Feed 1", subs.getJSONObject(0).getString("title"))
    }

    // ── rss.subscription.add ──────────────────────────────────────────────

    @Test
    fun `rss_subscription_add adds and list reflects it`() {
        val ctx = makeCtx()
        val params = JSONObject().put("feedUrl", "https://feed.com").put("title", "My Feed")
        val reply = RssSubscriptionAddHandler(ctx).handle(
            req(RssSubscriptionAddHandler.CAPABILITY, params)
        )
        assertTrue(reply.isComplete())
        assertTrue(resultJson(reply).getBoolean("added"))

        val listReply = RssSubscriptionListHandler(ctx).handle(req(RssSubscriptionListHandler.CAPABILITY))
        val subs = resultJson(listReply).getJSONArray("subscriptions")
        assertEquals(1, subs.length())
        assertEquals("My Feed", subs.getJSONObject(0).getString("title"))
    }

    @Test
    fun `rss_subscription_add rejects blank feedUrl`() {
        val ctx = makeCtx()
        val reply = RssSubscriptionAddHandler(ctx).handle(
            req(RssSubscriptionAddHandler.CAPABILITY, JSONObject().put("title", "No URL"))
        )
        assertTrue(reply.isError())
    }

    // ── rss.subscription.delete ──────────────────────────────────────────

    @Test
    fun `rss_subscription_delete removes subscription`() {
        val ctx = makeCtx(subs = listOf(
            RssSubscription(feedUrl = "https://feed.com", title = "Feed")
        ))
        val params = JSONObject().put("feedUrl", "https://feed.com")
        val reply = RssSubscriptionDeleteHandler(ctx).handle(
            req(RssSubscriptionDeleteHandler.CAPABILITY, params)
        )
        assertTrue(reply.isComplete())
        assertTrue(resultJson(reply).getBoolean("removed"))
        assertEquals(0, ctx.subscriptionRepository.let { runBlocking { it.getAll() } }.size)
    }

    // ── rss.refresh ──────────────────────────────────────────────────────

    @Test
    fun `rss_refresh parses xml and updates subscription`() {
        val ctx = makeCtx(subs = listOf(
            RssSubscription(feedUrl = "https://feed.com", title = "")
        ))
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0">
              <channel>
                <title>My Feed</title>
                <link>https://feed.com</link>
                <description>Test feed</description>
                <item>
                  <title>Article 1</title>
                  <link>https://feed.com/1</link>
                  <guid>guid-1</guid>
                </item>
                <item>
                  <title>Article 2</title>
                  <link>https://feed.com/2</link>
                  <guid>guid-2</guid>
                </item>
              </channel>
            </rss>
        """.trimIndent()
        val params = JSONObject().put("feedUrl", "https://feed.com").put("xml", xml)
        val reply = RssRefreshHandler(ctx).handle(req(RssRefreshHandler.CAPABILITY, params))
        assertTrue(reply.isComplete())
        val result = resultJson(reply)
        assertEquals("My Feed", result.getString("feedTitle"))
        assertEquals(2, result.getInt("count"))
        val items = result.getJSONArray("items")
        assertEquals("Article 1", items.getJSONObject(0).getString("title"))

        // Subscription should be marked updated with lastItemGuid
        val sub = runBlocking { ctx.subscriptionRepository.get("https://feed.com") }
        assertNotNull(sub)
        assertEquals("guid-1", sub!!.lastItemGuid)
    }

    @Test
    fun `rss_refresh rejects invalid xml`() {
        val ctx = makeCtx(subs = listOf(
            RssSubscription(feedUrl = "https://feed.com", title = "")
        ))
        val params = JSONObject().put("feedUrl", "https://feed.com").put("xml", "not xml")
        val reply = RssRefreshHandler(ctx).handle(req(RssRefreshHandler.CAPABILITY, params))
        assertTrue(reply.isError())
        assertTrue((reply as HostReply.Error).retryable())
    }

    @Test
    fun `rss_refresh rejects missing feedUrl`() {
        val ctx = makeCtx()
        val reply = RssRefreshHandler(ctx).handle(
            req(RssRefreshHandler.CAPABILITY, JSONObject().put("xml", "<rss/>"))
        )
        assertTrue(reply.isError())
    }

    // ── full registration smoke test ──────────────────────────────────────

    @Test
    fun `all source and rss capabilities are registered via registerHandlers`() {
        val ctx = makeCtx()
        val adapter = HostRuntime.over(object : HostTransport {
            override fun pollEventJson(timeoutMillis: Long): String? = null
            override fun sendCommand(commandJson: String) {}
        }).let { runtime ->
            ctx.registerHandlers(runtime)
            runtime
        }.adapter()

        val expected = listOf(
            "source.list", "source.add", "source.remove", "source.set_enabled",
            "source.import", "source.export",
            "source.debug.run", "source.debug.detect",
            "rss.subscription.list", "rss.subscription.add", "rss.subscription.delete",
            "rss.refresh"
        )
        expected.forEach { cap ->
            assertTrue("$cap must be registered", adapter.isRegistered(cap))
        }
    }
}
