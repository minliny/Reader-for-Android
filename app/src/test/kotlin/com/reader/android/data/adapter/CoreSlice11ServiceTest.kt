package com.reader.android.data.adapter

import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreSlice11ServiceTest {

    @Test
    fun `source list is Core only and strips signed query from display`() = runBlocking {
        var methodSeen = ""
        val service = CoreSlice11Service(Slice11CoreCommandClient { method, _ ->
            methodSeen = method
            JSONObject().put(
                "sources",
                JSONArray().put(
                    JSONObject()
                        .put("sourceId", "source-1")
                        .put("name", "Source")
                        .put("baseUrl", "https://source.example/books?token=secret")
                        .put("enabled", true)
                        .put("enabledExplore", false)
                )
            ).put("total", 1)
        })

        val row = (service.listSources() as Slice11Outcome.Success).value.single()

        assertEquals("source.list", methodSeen)
        assertEquals("https://source.example/books", row.displayOrigin)
        assertFalse(row.toString().contains("secret"))
    }

    @Test
    fun `source import sends raw Legado object to Core without local persistence`() = runBlocking {
        var captured: JSONObject? = null
        val service = CoreSlice11Service(Slice11CoreCommandClient { method, params ->
            assertEquals("source.import", method)
            captured = JSONObject(params.toString())
            JSONObject()
                .put("sourceId", params.getString("sourceId"))
                .put("name", params.getString("name"))
                .put("imported", true)
        })

        val outcome = service.importLegadoSource(
            """{"bookSourceUrl":"https://source.example","bookSourceName":"Source","enabled":true}"""
        )

        assertTrue(outcome is Slice11Outcome.Success)
        assertEquals("Source", captured!!.getJSONObject("bookSource").getString("bookSourceName"))
        assertEquals("https://source.example", captured!!.getString("sourceId"))
    }

    @Test
    fun `source export payload is redacted until explicit file edge`() = runBlocking {
        val raw = """[{"bookSourceUrl":"https://source.example","header":"Bearer secret"}]"""
        val service = CoreSlice11Service(Slice11CoreCommandClient { method, _ ->
            assertEquals("source.export", method)
            JSONObject().put("data", raw).put("count", 1).put("format", "json")
        })

        val payload = (service.exportSources() as Slice11Outcome.Success).value

        assertFalse(payload.toString().contains("secret"))
        assertTrue(payload.toUtf8Bytes().toString(Charsets.UTF_8).contains("Bearer secret"))
    }

    @Test
    fun `source check uses real Core continuation method and validates levels`() = runBlocking {
        var calls = 0
        val service = CoreSlice11Service(Slice11CoreCommandClient { method, params ->
            calls++
            assertEquals("source.check.run", method)
            assertEquals("L5", params.getJSONArray("levels").getString(0))
            JSONObject().put(
                "results",
                JSONArray().put(
                    JSONObject()
                        .put("sourceId", "source-1")
                        .put("available", false)
                        .put("levelsPassed", JSONArray().put("L1").put("L2"))
                        .put("failureReason", "L3 detail: parse failed")
                        .put("durationMs", 32)
                )
            )
        })

        val result = (service.runSourceCheck(listOf("source-1"), levels = listOf("L5")) as Slice11Outcome.Success).value.single()
        val invalid = service.runSourceCheck(listOf("source-1"), levels = listOf("L6")) as Slice11Outcome.Failed

        assertFalse(result.available)
        assertEquals(listOf("L1", "L2"), result.levelsPassed)
        assertEquals(Slice11FailureCode.INVALID_INPUT, invalid.failure.code)
        assertEquals(1, calls)
    }

    @Test
    fun `rule subscription summary never exposes signed query`() = runBlocking {
        val service = CoreSlice11Service(Slice11CoreCommandClient { method, _ ->
            assertEquals("rule-sub.list", method)
            JSONObject().put(
                "subs",
                JSONArray().put(ruleSub(url = "https://rules.example/feed.json?token=secret"))
            )
        })

        val row = (service.listRuleSubscriptions() as Slice11Outcome.Success).value.single()

        assertEquals("https://rules.example/feed.json", row.displayUrl)
        assertFalse(row.toString().contains("secret"))
    }

    @Test
    fun `rule subscription rejects credential-bearing URL before Core`() = runBlocking {
        var calls = 0
        val service = CoreSlice11Service(Slice11CoreCommandClient { _, _ -> calls++; JSONObject() })

        val result = service.putRuleSubscription(
            id = 1,
            name = "Rules",
            url = "https://user:password@rules.example/feed.json",
            type = 0,
            customOrder = 0,
            autoUpdate = false,
            updatedAt = 0
        ) as Slice11Outcome.Failed

        assertEquals(Slice11FailureCode.INVALID_INPUT, result.failure.code)
        assertEquals(0, calls)
    }

    @Test
    fun `rss subscription list comes from Core and strips query credentials`() = runBlocking {
        val service = CoreSlice11Service(Slice11CoreCommandClient { method, _ ->
            assertEquals("rss.subscription.list", method)
            JSONObject().put(
                "subscriptions",
                JSONArray().put(rssSubscription(feedUrl = "https://rss.example/feed?sig=secret"))
            )
        })

        val row = (service.listRssSubscriptions() as Slice11Outcome.Success).value.single()

        assertEquals("https://rss.example/feed", row.displayFeedUrl)
        assertEquals(4, row.unreadCount)
        assertFalse(row.toString().contains("secret"))
    }

    @Test
    fun `rss add and update use frozen Core subscription DTOs`() = runBlocking {
        val methods = mutableListOf<String>()
        val service = CoreSlice11Service(Slice11CoreCommandClient { method, params ->
            methods += method
            val row = rssSubscription(
                subscriptionId = params.getString("subscriptionId"),
                feedUrl = params.optString("feedUrl", "https://rss.example/feed"),
                title = params.optString("title", "RSS"),
                enabled = params.optBoolean("enabled", true)
            )
            JSONObject().put("subscription", row)
        })

        assertTrue(service.addRssSubscription("rss-1", "https://rss.example/feed", "RSS") is Slice11Outcome.Success)
        assertTrue(service.updateRssSubscription("rss-1", enabled = false) is Slice11Outcome.Success)
        assertEquals(listOf("rss.subscription.add", "rss.subscription.update"), methods)
    }

    @Test
    fun `rss refresh consumes Core maintained counts`() = runBlocking {
        val service = CoreSlice11Service(Slice11CoreCommandClient { method, params ->
            assertEquals("rss.subscription.refresh", method)
            assertEquals(99L, params.getLong("evaluatedAt"))
            JSONObject()
                .put("subscription", rssSubscription())
                .put("items", JSONArray())
                .put("count", 8)
                .put("unreadCount", 4)
                .put("newCount", 2)
                .put("fetched", true)
                .put("notModified", false)
                .put("evaluatedAt", 99L)
        })

        val row = (service.refreshRssSubscription("rss-1", 99) as Slice11Outcome.Success).value

        assertEquals(2, row.newCount)
        assertTrue(row.fetched)
    }

    @Test
    fun `rss item signed link stays private until browser edge`() = runBlocking {
        val service = CoreSlice11Service(Slice11CoreCommandClient { method, _ ->
            assertEquals("rss.subscription.items", method)
            JSONObject()
                .put("subscription", rssSubscription())
                .put(
                    "items",
                    JSONArray().put(
                        JSONObject()
                            .put("subscriptionId", "rss-1")
                            .put("title", "Entry")
                            .put("link", "https://rss.example/article?sig=secret")
                            .put("guid", "guid-1")
                            .put("read", false)
                            .put("firstSeenAt", 1L)
                    )
                )
                .put("count", 1)
                .put("unreadCount", 1)
        })

        val item = (service.listRssItems("rss-1") as Slice11Outcome.Success).value.single()

        assertFalse(item.toString().contains("secret"))
        assertEquals("https://rss.example/article?sig=secret", item.externalHttpUrl())
    }

    @Test
    fun `rss favorites use Core query and keep signed link private`() = runBlocking {
        val service = CoreSlice11Service(Slice11CoreCommandClient { method, params ->
            assertEquals("rss.favorite.list", method)
            assertEquals(25, params.getInt("limit"))
            JSONObject()
                .put(
                    "favorites",
                    JSONArray().put(
                        JSONObject()
                            .put("subscriptionId", "rss-1")
                            .put("guid", "guid-1")
                            .put("feedUrl", "https://rss.example/feed")
                            .put("subscriptionTitle", "RSS")
                            .put("title", "Favorite")
                            .put("link", "https://rss.example/favorite?sig=secret")
                            .put("summary", "Summary")
                            .put("publishedAt", "today")
                            .put("firstSeenAt", 1L)
                            .put("addedAt", 2L)
                    )
                )
                .put("count", 1)
        })

        val item = (service.listRssFavorites(limit = 25) as Slice11Outcome.Success).value.single()

        assertEquals("Favorite", item.title)
        assertFalse(item.toString().contains("secret"))
        assertEquals("https://rss.example/favorite?sig=secret", item.externalHttpUrl())
    }

    @Test
    fun `rss read mutation is subscription scoped`() = runBlocking {
        var captured: JSONObject? = null
        val service = CoreSlice11Service(Slice11CoreCommandClient { method, params ->
            assertEquals("rss.item.read", method)
            captured = JSONObject(params.toString())
            JSONObject()
                .put("marked", true)
                .put("subscriptionId", params.getString("subscriptionId"))
                .put("guid", params.getString("guid"))
                .put("read", params.getBoolean("read"))
                .put("unreadCount", 3)
        })

        val count = (service.markRssItemRead("rss-1", "shared-guid", true) as Slice11Outcome.Success).value

        assertEquals("rss-1", captured!!.getString("subscriptionId"))
        assertEquals(3, count)
    }

    @Test
    fun `capability matrix fails closed for missing profile continuation and protected download`() {
        assertEquals(
            AndroidSlice11CapabilityStatus.BLOCKED_MISSING_PROFILE_ISOLATION,
            AndroidSlice11CapabilityMatrix.status("webview.profile")
        )
        assertEquals(
            AndroidSlice11CapabilityStatus.BLOCKED_MISSING_CONTINUATION_PROTOCOL,
            AndroidSlice11CapabilityMatrix.status("challenge.continuation")
        )
        assertEquals(
            AndroidSlice11CapabilityStatus.BLOCKED_MISSING_PROTECTED_DOWNLOAD_PROTOCOL,
            AndroidSlice11CapabilityMatrix.status("rss.protected-download")
        )
        val blocked = CoreSlice11Service(Slice11CoreCommandClient { _, _ -> JSONObject() })
            .blocked("webview.profile")
        assertEquals(Slice11FailureCode.CAPABILITY_BLOCKED, blocked.failure.code)
    }

    @Test
    fun `Core unavailable is retryable and cannot fall back to Android RSS storage`() = runBlocking {
        val service = CoreSlice11Service(Slice11CoreCommandClient { _, _ ->
            throw IllegalStateException("not initialized")
        })

        val result = service.listRssSubscriptions() as Slice11Outcome.Failed

        assertEquals(Slice11FailureCode.CORE_UNAVAILABLE, result.failure.code)
        assertTrue(result.failure.retryable)
    }

    @Test
    fun `malformed Core result fails protocol validation`() = runBlocking {
        val service = CoreSlice11Service(Slice11CoreCommandClient { _, _ ->
            JSONObject().put("subscriptions", JSONArray().put(JSONObject().put("subscriptionId", "rss-1")))
        })

        val result = service.listRssSubscriptions() as Slice11Outcome.Failed

        assertEquals(Slice11FailureCode.CORE_PROTOCOL_MISMATCH, result.failure.code)
        assertNotNull(result.failure.message)
    }

    private fun ruleSub(url: String): JSONObject = JSONObject()
        .put("id", 1L)
        .put("name", "Rules")
        .put("url", url)
        .put("type", 0)
        .put("customOrder", 2)
        .put("autoUpdate", true)
        .put("update", 3L)

    private fun rssSubscription(
        subscriptionId: String = "rss-1",
        feedUrl: String = "https://rss.example/feed",
        title: String = "RSS",
        enabled: Boolean = true
    ): JSONObject = JSONObject()
        .put("subscriptionId", subscriptionId)
        .put("feedUrl", feedUrl)
        .put("title", title)
        .put("enabled", enabled)
        .put("lastFetchAt", 10L)
        .put("lastEntryId", "entry-1")
        .put("unreadCount", 4)
}
