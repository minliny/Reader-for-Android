package com.reader.host

import com.reader.android.data.network.FakeRssItemRepository
import com.reader.android.data.network.RssSubscription
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM proof for the P4 `rss.list` / `rss.item.read` Host capability handlers
 * and the `rss.refresh` caching behavior.
 *
 * Closes the `TODO(core-blocker)` gap in
 * [com.reader.android.data.network.RoomSubscriptionRepository] by giving the
 * Host a local [FakeRssItemRepository] cache so `rss.list` /
 * `rss.item.read` work as a fallback until Core lands these protocol methods.
 * Production wires the same handlers over a Room-backed repository via
 * AppProvider, so a regression in the handler logic fails this test.
 *
 * Coverage:
 *  - rss.list (empty / after refresh / feedUrl filter / unreadOnly filter)
 *  - rss.item.read (mark read / toggle back to unread)
 *  - rss.refresh caches items into the repository
 *  - registration smoke test (both new capabilities registered)
 */
class RssItemCapabilityHandlersJvmTest {

    private fun makeCtx(rssItemRepo: FakeRssItemRepository = FakeRssItemRepository()) =
        fakeSourceRssContext(
            subscriptions = listOf(RssSubscription(feedUrl = FEED_A, title = "")),
            rssItemRepository = rssItemRepo
        )

    private fun req(capability: String, params: JSONObject = JSONObject()) =
        HostRequest(1L, 1L, capability, params.toString())

    private fun resultJson(reply: HostReply): JSONObject =
        JSONObject((reply as HostReply.Complete).resultJson())

    private fun feedXml(feedTitle: String, vararg items: Pair<String, String>): String {
        val sb = StringBuilder()
        sb.append("""
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0">
              <channel>
                <title>$feedTitle</title>
                <link>https://feed.com</link>
                <description>Test feed</description>
        """.trimIndent())
        items.forEach { (title, guid) ->
            sb.append(
                """
                <item>
                  <title>$title</title>
                  <link>https://feed.com/$guid</link>
                  <guid>$guid</guid>
                </item>
                """.trimIndent()
            )
        }
        sb.append("</channel></rss>")
        return sb.toString()
    }

    private fun refresh(ctx: SourceRssContext, feedUrl: String, xml: String): HostReply =
        RssRefreshHandler(ctx).handle(
            req(RssRefreshHandler.CAPABILITY, JSONObject().put("feedUrl", feedUrl).put("xml", xml))
        )

    // ── rss.list ────────────────────────────────────────────────────────

    @Test
    fun `rss_list returns empty when no items cached`() {
        val ctx = makeCtx()
        val reply = RssListHandler(ctx).handle(req(RssListHandler.CAPABILITY))
        assertTrue(reply.isComplete())
        val result = resultJson(reply)
        assertEquals(0, result.getInt("count"))
        assertEquals(0, result.getJSONArray("items").length())
    }

    @Test
    fun `rss_list returns items after refresh caches them`() {
        val ctx = makeCtx()
        refresh(ctx, FEED_A, feedXml("My Feed", "Article 1" to "guid-1", "Article 2" to "guid-2"))

        val reply = RssListHandler(ctx).handle(req(RssListHandler.CAPABILITY))
        assertTrue(reply.isComplete())
        val result = resultJson(reply)
        assertEquals(2, result.getInt("count"))
        val items = result.getJSONArray("items")
        assertEquals("Article 1", items.getJSONObject(0).getString("title"))
        assertEquals("guid-1", items.getJSONObject(0).getString("guid"))
    }

    @Test
    fun `rss_list filters by feedUrl`() {
        val ctx = makeCtx()
        refresh(ctx, FEED_A, feedXml("Feed A", "A1" to "a-1", "A2" to "a-2"))
        refresh(ctx, FEED_B, feedXml("Feed B", "B1" to "b-1"))

        val reply = RssListHandler(ctx).handle(
            req(RssListHandler.CAPABILITY, JSONObject().put("feedUrl", FEED_A))
        )
        assertTrue(reply.isComplete())
        val items = resultJson(reply).getJSONArray("items")
        assertEquals(2, items.length())
        // every returned item belongs to FEED_A
        val guids = (0 until items.length()).map { items.getJSONObject(it).getString("guid") }
        assertTrue(guids.contains("a-1"))
        assertTrue(guids.contains("a-2"))
        assertFalse(guids.contains("b-1"))
    }

    @Test
    fun `rss_list filters unread only`() {
        val ctx = makeCtx()
        refresh(ctx, FEED_A, feedXml("Feed", "One" to "g-1", "Two" to "g-2"))

        // mark g-1 read
        RssItemReadHandler(ctx).handle(
            req(RssItemReadHandler.CAPABILITY, JSONObject().put("guid", "g-1").put("read", true))
        )

        val reply = RssListHandler(ctx).handle(
            req(RssListHandler.CAPABILITY, JSONObject().put("unreadOnly", true))
        )
        assertTrue(reply.isComplete())
        val items = resultJson(reply).getJSONArray("items")
        assertEquals(1, items.length())
        assertEquals("g-2", items.getJSONObject(0).getString("guid"))
    }

    // ── rss.item.read ───────────────────────────────────────────────────

    @Test
    fun `rss_item_read marks item as read`() {
        val ctx = makeCtx()
        refresh(ctx, FEED_A, feedXml("Feed", "One" to "g-1", "Two" to "g-2"))

        val reply = RssItemReadHandler(ctx).handle(
            req(RssItemReadHandler.CAPABILITY, JSONObject().put("guid", "g-1").put("read", true))
        )
        assertTrue(reply.isComplete())
        val result = resultJson(reply)
        assertTrue(result.getBoolean("marked"))
        assertEquals("g-1", result.getString("guid"))
        assertTrue(result.getBoolean("read"))

        // g-1 should no longer appear in unreadOnly listing
        val listReply = RssListHandler(ctx).handle(
            req(RssListHandler.CAPABILITY, JSONObject().put("unreadOnly", true))
        )
        val guids = (0 until resultJson(listReply).getJSONArray("items").length()).map {
            resultJson(listReply).getJSONArray("items").getJSONObject(it).getString("guid")
        }
        assertFalse(guids.contains("g-1"))
        assertTrue(guids.contains("g-2"))
    }

    @Test
    fun `rss_item_read toggles back to unread`() {
        val ctx = makeCtx()
        refresh(ctx, FEED_A, feedXml("Feed", "One" to "g-1"))

        // mark read
        RssItemReadHandler(ctx).handle(
            req(RssItemReadHandler.CAPABILITY, JSONObject().put("guid", "g-1").put("read", true))
        )
        // mark unread again
        RssItemReadHandler(ctx).handle(
            req(RssItemReadHandler.CAPABILITY, JSONObject().put("guid", "g-1").put("read", false))
        )

        val listReply = RssListHandler(ctx).handle(
            req(RssListHandler.CAPABILITY, JSONObject().put("unreadOnly", true))
        )
        val items = resultJson(listReply).getJSONArray("items")
        assertEquals(1, items.length())
        assertEquals("g-1", items.getJSONObject(0).getString("guid"))
    }

    @Test
    fun `rss_item_read rejects missing guid`() {
        val ctx = makeCtx()
        val reply = RssItemReadHandler(ctx).handle(
            req(RssItemReadHandler.CAPABILITY, JSONObject().put("read", true))
        )
        assertTrue(reply.isError())
    }

    @Test
    fun `rss_item_read rejects missing read`() {
        val ctx = makeCtx()
        val reply = RssItemReadHandler(ctx).handle(
            req(RssItemReadHandler.CAPABILITY, JSONObject().put("guid", "g-1"))
        )
        assertTrue(reply.isError())
    }

    // ── rss.refresh caching ─────────────────────────────────────────────

    @Test
    fun `rss_refresh_caches_items_in_repository`() {
        val repo = FakeRssItemRepository()
        val ctx = makeCtx(repo)
        refresh(ctx, FEED_A, feedXml("My Feed", "Article 1" to "guid-1", "Article 2" to "guid-2"))

        val cached = runBlocking { repo.listAll() }
        assertEquals(2, cached.size)
        val titles = cached.map { it.title }
        assertTrue(titles.contains("Article 1"))
        assertTrue(titles.contains("Article 2"))
        assertEquals("guid-1", cached.first { it.title == "Article 1" }.guid)
    }

    @Test
    fun `rss_refresh replaces items with same guid`() {
        val repo = FakeRssItemRepository()
        val ctx = makeCtx(repo)
        // first refresh with old title
        refresh(ctx, FEED_A, feedXml("Feed", "Old Title" to "g-1"))
        // second refresh updates the same guid with a new title
        refresh(ctx, FEED_A, feedXml("Feed", "New Title" to "g-1"))

        val cached = runBlocking { repo.listAll() }
        assertEquals(1, cached.size)
        assertEquals("New Title", cached[0].title)
    }

    // ── registration smoke test ─────────────────────────────────────────

    @Test
    fun `rss_list and rss_item_read are registered via registerHandlers`() {
        val ctx = makeCtx()
        val adapter = HostRuntime.over(object : HostTransport {
            override fun pollEventJson(timeoutMillis: Long): String? = null
            override fun sendCommand(commandJson: String) {}
        }).let { runtime ->
            ctx.registerHandlers(runtime)
            runtime
        }.adapter()

        assertTrue("rss.list must be registered", adapter.isRegistered(RssListHandler.CAPABILITY))
        assertTrue(
            "rss.item.read must be registered",
            adapter.isRegistered(RssItemReadHandler.CAPABILITY)
        )
    }

    @Test
    fun `registerHandlers registers all fourteen source and rss capabilities`() {
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
            "rss.refresh", "rss.list", "rss.item.read"
        )
        expected.forEach { cap ->
            assertTrue("$cap must be registered", adapter.isRegistered(cap))
        }
        assertEquals(14, expected.size)
    }

    private companion object {
        private const val FEED_A = "https://feed-a.com"
        private const val FEED_B = "https://feed-b.com"
    }
}
