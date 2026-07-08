package com.reader

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.reader.android.data.model.BookSource
import com.reader.android.data.network.RssSubscription
import com.reader.host.HostReply
import com.reader.host.HostRequest
import com.reader.host.HostRuntime
import com.reader.host.HostTransport
import com.reader.host.fakeSourceRssContext
import com.reader.host.registerHandlers
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * P1-5 device-level dispatch proof for the Source / RSS capability handlers.
 *
 * **What this proves**: all 12 source/RSS capabilities are registered on a
 * real Android runtime (not just JVM) and round-trip real CRUD + import/export
 * + RSS parse operations through the `HostRequest → HostAdapter.dispatch →
 * HostReply` boundary. The handlers are pure-JVM but this test exercises them
 * on the Dalvik/ART class loader to catch device-only reflection / JSON / enum
 * issues invisible to JVM unit tests.
 *
 * **Pattern**: direct [HostAdapter.dispatch] (mirrors
 * [HostRouterDispatchProofTest]) — no Core, no JNI, no poll thread. A no-op
 * [HostTransport] stub satisfies [HostRuntime.over]; we dispatch on the
 * runtime's adapter directly without starting the poll thread.
 */
@RunWith(AndroidJUnit4::class)
class SourceRssCapabilityDispatchProofTest {

    private val allCapabilities = listOf(
        "source.list",
        "source.add",
        "source.remove",
        "source.set_enabled",
        "source.import",
        "source.export",
        "source.debug.run",
        "source.debug.detect",
        "rss.subscription.list",
        "rss.subscription.add",
        "rss.subscription.delete",
        "rss.refresh"
    )

    /**
     * All 12 source/RSS handlers must be registered after
     * `SourceRssContext.registerHandlers(runtime)`. Each capability, when
     * dispatched, must NOT return the "unsupported capability" error —
     * proving the router dispatched to the handler (not the adapter's
     * catch-all INTERNAL path).
     */
    @Test
    fun sourceRss_registerHandlersWiresAll12Capabilities() {
        val ctx = fakeSourceRssContext(
            bookSources = listOf(
                BookSource(sourceUrl = "https://proof.test/src", sourceName = "Proof Source")
            ),
            subscriptions = listOf(
                RssSubscription(feedUrl = "https://proof.test/feed", title = "Proof Feed")
            )
        )
        val runtime = ctx.registerHandlers(HostRuntime.over(NoopTransport()))
        val adapter = runtime.adapter()

        for (capability in allCapabilities) {
            val params = paramsFor(capability)
            val request = HostRequest(1L, 1L, capability, params)
            val reply = adapter.dispatch(request)
            assertFalse(
                "$capability must dispatch to a handler, got 'unsupported capability'",
                reply.isError() && (reply as HostReply.Error).message().contains("unsupported capability")
            )
        }
    }

    /**
     * `source.add` → `source.list` contains it → `source.remove` →
     * `source.list` doesn't. Proves the CRUD round-trip works on device.
     */
    @Test
    fun source_addThenListThenRemoveRoundTrip() {
        val ctx = fakeSourceRssContext()
        val adapter = ctx.registerHandlers(HostRuntime.over(NoopTransport())).adapter()
        val url = "https://roundtrip.test/source"

        // add
        val addReply = adapter.dispatch(HostRequest(1L, 1L, "source.add", JSONObject()
            .put("sourceUrl", url)
            .put("sourceName", "Round Trip Source")
            .toString()))
        assertTrue("source.add must complete", addReply.isComplete())

        // list → contains it
        val listReply = adapter.dispatch(HostRequest(1L, 2L, "source.list", "{}"))
        assertTrue("source.list must complete", listReply.isComplete())
        val sources = JSONObject((listReply as HostReply.Complete).resultJson()).getJSONArray("sources")
        val found = (0 until sources.length()).any { i ->
            sources.getJSONObject(i).getString("sourceUrl") == url
        }
        assertTrue("source.list must contain the added source", found)

        // remove
        val removeReply = adapter.dispatch(HostRequest(1L, 3L, "source.remove", JSONObject()
            .put("sourceUrl", url).toString()))
        assertTrue("source.remove must complete", removeReply.isComplete())

        // list → doesn't contain it
        val listAfter = adapter.dispatch(HostRequest(1L, 4L, "source.list", "{}"))
        val sourcesAfter = JSONObject((listAfter as HostReply.Complete).resultJson()).getJSONArray("sources")
        val stillFound = (0 until sourcesAfter.length()).any { i ->
            sourcesAfter.getJSONObject(i).getString("sourceUrl") == url
        }
        assertFalse("source.list must not contain the removed source", stillFound)
    }

    /**
     * `source.import` with a JSON array → `source.export` returns matching
     * count. Proves the import/export format is self-consistent on device.
     */
    @Test
    fun source_importExportRoundTrip() {
        val ctx = fakeSourceRssContext()
        val adapter = ctx.registerHandlers(HostRuntime.over(NoopTransport())).adapter()

        val importArray = JSONArray().apply {
            put(JSONObject().put("sourceUrl", "https://imp.test/1").put("sourceName", "Src 1"))
            put(JSONObject().put("sourceUrl", "https://imp.test/2").put("sourceName", "Src 2"))
            put(JSONObject().put("sourceUrl", "https://imp.test/3").put("sourceName", "Src 3"))
        }

        val importReply = adapter.dispatch(HostRequest(1L, 1L, "source.import", JSONObject()
            .put("json", importArray.toString()).toString()))
        assertTrue("source.import must complete", importReply.isComplete())
        val imported = JSONObject((importReply as HostReply.Complete).resultJson()).getInt("imported")
        assertEquals("source.import must report 3 imported", 3, imported)

        val exportReply = adapter.dispatch(HostRequest(1L, 2L, "source.export", "{}"))
        assertTrue("source.export must complete", exportReply.isComplete())
        val exportResult = JSONObject((exportReply as HostReply.Complete).resultJson())
        assertEquals("source.export count must match import count", 3, exportResult.getInt("count"))
    }

    /**
     * `source.add` → `source.set_enabled(false)` → `source.list` shows
     * `enabled=false`. Proves the toggle flag propagates on device.
     */
    @Test
    fun source_setEnabledTogglesFlag() {
        val ctx = fakeSourceRssContext()
        val adapter = ctx.registerHandlers(HostRuntime.over(NoopTransport())).adapter()
        val url = "https://toggle.test/source"

        adapter.dispatch(HostRequest(1L, 1L, "source.add", JSONObject()
            .put("sourceUrl", url)
            .put("sourceName", "Toggle Source")
            .put("enabled", true)
            .toString()))

        adapter.dispatch(HostRequest(1L, 2L, "source.set_enabled", JSONObject()
            .put("sourceUrl", url)
            .put("enabled", false)
            .toString()))

        val listReply = adapter.dispatch(HostRequest(1L, 3L, "source.list", "{}"))
        val sources = JSONObject((listReply as HostReply.Complete).resultJson()).getJSONArray("sources")
        val source = (0 until sources.length()).map { sources.getJSONObject(it) }
            .first { it.getString("sourceUrl") == url }
        assertEquals("source.set_enabled(false) must toggle the flag", false, source.getBoolean("enabled"))
    }

    /**
     * `rss.subscription.add` → `rss.subscription.list` contains →
     * `rss.subscription.delete` → list doesn't. Proves the RSS subscription
     * CRUD round-trip works on device.
     */
    @Test
    fun rss_subscription_addListDeleteRoundTrip() {
        val ctx = fakeSourceRssContext()
        val adapter = ctx.registerHandlers(HostRuntime.over(NoopTransport())).adapter()
        val feedUrl = "https://rss.test/feed"

        val addReply = adapter.dispatch(HostRequest(1L, 1L, "rss.subscription.add", JSONObject()
            .put("feedUrl", feedUrl)
            .put("title", "Test Feed")
            .toString()))
        assertTrue("rss.subscription.add must complete", addReply.isComplete())

        val listReply = adapter.dispatch(HostRequest(1L, 2L, "rss.subscription.list", "{}"))
        assertTrue("rss.subscription.list must complete", listReply.isComplete())
        val subs = JSONObject((listReply as HostReply.Complete).resultJson()).getJSONArray("subscriptions")
        val found = (0 until subs.length()).any { i ->
            subs.getJSONObject(i).getString("feedUrl") == feedUrl
        }
        assertTrue("rss.subscription.list must contain the added feed", found)

        val deleteReply = adapter.dispatch(HostRequest(1L, 3L, "rss.subscription.delete", JSONObject()
            .put("feedUrl", feedUrl).toString()))
        assertTrue("rss.subscription.delete must complete", deleteReply.isComplete())

        val listAfter = adapter.dispatch(HostRequest(1L, 4L, "rss.subscription.list", "{}"))
        val subsAfter = JSONObject((listAfter as HostReply.Complete).resultJson()).getJSONArray("subscriptions")
        val stillFound = (0 until subsAfter.length()).any { i ->
            subsAfter.getJSONObject(i).getString("feedUrl") == feedUrl
        }
        assertFalse("rss.subscription.list must not contain the deleted feed", stillFound)
    }

    /**
     * `rss.refresh` with a sample RSS 2.0 XML returns parsed items with
     * count > 0. Proves the [com.reader.android.data.network.RssParser]
     * runs on the device class loader and returns structured items.
     */
    @Test
    fun rss_refreshWithSampleXmlReturnsParsedItems() {
        val ctx = fakeSourceRssContext()
        val adapter = ctx.registerHandlers(HostRuntime.over(NoopTransport())).adapter()

        val xml = """
            <?xml version="1.0"?>
            <rss version="2.0">
            <channel>
                <title>Device Proof Feed</title>
                <link>https://rss.test</link>
                <description>Proof feed for device test</description>
                <item>
                    <title>Item One</title>
                    <link>https://rss.test/1</link>
                    <guid>guid-1</guid>
                </item>
                <item>
                    <title>Item Two</title>
                    <link>https://rss.test/2</link>
                    <guid>guid-2</guid>
                </item>
            </channel>
            </rss>
        """.trimIndent()

        val reply = adapter.dispatch(HostRequest(1L, 1L, "rss.refresh", JSONObject()
            .put("feedUrl", "https://rss.test/feed")
            .put("xml", xml)
            .toString()))

        assertTrue("rss.refresh must complete with valid XML, got: ${reply.kind()}", reply.isComplete())
        val result = JSONObject((reply as HostReply.Complete).resultJson())
        val items = result.getJSONArray("items")
        assertTrue("rss.refresh must return > 0 items", items.length() > 0)
        assertEquals("rss.refresh must return 2 items", 2, result.getInt("count"))
        assertEquals("Device Proof Feed", result.getString("feedTitle"))
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Build minimal valid params for each capability so the dispatch proof
     * can exercise every handler without hitting param-validation errors.
     */
    private fun paramsFor(capability: String): String = when (capability) {
        "source.list", "source.export", "rss.subscription.list" -> "{}"
        "source.add" -> JSONObject()
            .put("sourceUrl", "https://register.test/src")
            .put("sourceName", "Register Proof")
            .toString()
        "source.remove" -> JSONObject()
            .put("sourceUrl", "https://register.test/remove")
            .toString()
        "source.set_enabled" -> JSONObject()
            .put("sourceUrl", "https://proof.test/src")
            .put("enabled", false)
            .toString()
        "source.import" -> JSONObject()
            .put("json", JSONArray().put(JSONObject().put("sourceUrl", "https://imp.test/1").put("sourceName", "S1")).toString())
            .toString()
        "source.debug.run" -> JSONObject()
            .put("sourceUrl", "https://proof.test/src")
            .put("step", "search")
            .toString()
        "source.debug.detect" -> JSONObject()
            .put("sourceUrl", "https://proof.test/src")
            .toString()
        "rss.subscription.add" -> JSONObject()
            .put("feedUrl", "https://register.test/feed")
            .put("title", "Register Feed")
            .toString()
        "rss.subscription.delete" -> JSONObject()
            .put("feedUrl", "https://register.test/delete")
            .toString()
        "rss.refresh" -> JSONObject()
            .put("feedUrl", "https://register.test/refresh")
            .put("xml", "<rss><channel><title>F</title><link>l</link><item><title>I</title><link>l</link></item></channel></rss>")
            .toString()
        else -> "{}"
    }

    /**
     * No-op transport for [HostRuntime.over] — the dispatch proof never
     * starts the poll thread, so the transport is never called.
     */
    private class NoopTransport : HostTransport {
        override fun pollEventJson(timeoutMillis: Long): String? = null
        override fun sendCommand(commandJson: String) {}
    }
}
