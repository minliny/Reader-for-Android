package com.reader.host

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM proof for the P2 search history capability handlers.
 *
 * Verifies the `search.history.list` / `add` / `clear` round-trip against
 * a [fakeSearchHistoryContext] (FakeSearchHistoryRepository). Production
 * wires the same handlers over the Room-backed RoomSearchHistoryRepository,
 * so a regression in the handler logic fails this test.
 *
 * Capability coverage:
 *  - search.history.list (empty + populated)
 *  - search.history.add (insert + dedupe + move-to-top + empty-keyword rejection)
 *  - search.history.clear
 *  - registration smoke test (all 3 capabilities registered)
 */
class SearchHistoryCapabilityHandlersJvmTest {

    private fun makeCtx() = fakeSearchHistoryContext()

    private fun req(capability: String, params: JSONObject = JSONObject()) =
        HostRequest(1L, 1L, capability, params.toString())

    private fun resultJson(reply: HostReply): JSONObject =
        JSONObject((reply as HostReply.Complete).resultJson())

    // ── search.history.list ──────────────────────────────────────────────

    @Test
    fun `search_history_list returns empty when no history`() {
        val ctx = makeCtx()
        val reply = SearchHistoryListHandler(ctx).handle(req(SearchHistoryListHandler.CAPABILITY))
        assertTrue(reply.isComplete())
        val result = resultJson(reply)
        assertEquals(0, result.getInt("count"))
        assertEquals(0, result.getJSONArray("keywords").length())
    }

    // ── search.history.add ───────────────────────────────────────────────

    @Test
    fun `search_history_add then list contains keyword`() {
        val ctx = makeCtx()
        val params = JSONObject().put("keyword", "斗破苍穹")
        val reply = SearchHistoryAddHandler(ctx).handle(req(SearchHistoryAddHandler.CAPABILITY, params))
        assertTrue(reply.isComplete())
        assertTrue(resultJson(reply).getBoolean("added"))

        val listReply = SearchHistoryListHandler(ctx).handle(req(SearchHistoryListHandler.CAPABILITY))
        assertTrue(listReply.isComplete())
        val keywords = resultJson(listReply).getJSONArray("keywords")
        assertEquals(1, keywords.length())
        assertEquals("斗破苍穹", keywords.getString(0))
    }

    @Test
    fun `search_history_add dedupes and moves to top`() {
        val ctx = makeCtx()
        // Add "first", then "second", then "first" again — "first" should move to top.
        SearchHistoryAddHandler(ctx).handle(req(SearchHistoryAddHandler.CAPABILITY,
            JSONObject().put("keyword", "first")))
        // Sleep to ensure System.currentTimeMillis() differs between adds so the
        // recency ordering in FakeSearchHistoryRepository is deterministic.
        Thread.sleep(5)
        SearchHistoryAddHandler(ctx).handle(req(SearchHistoryAddHandler.CAPABILITY,
            JSONObject().put("keyword", "second")))
        Thread.sleep(5)
        SearchHistoryAddHandler(ctx).handle(req(SearchHistoryAddHandler.CAPABILITY,
            JSONObject().put("keyword", "first")))

        val listReply = SearchHistoryListHandler(ctx).handle(req(SearchHistoryListHandler.CAPABILITY))
        assertTrue(listReply.isComplete())
        val keywords = resultJson(listReply).getJSONArray("keywords")
        assertEquals(2, keywords.length())
        assertEquals("first", keywords.getString(0)) // moved to top (most recent)
        assertEquals("second", keywords.getString(1))
    }

    @Test
    fun `search_history_add rejects empty keyword`() {
        val ctx = makeCtx()
        val reply = SearchHistoryAddHandler(ctx).handle(req(SearchHistoryAddHandler.CAPABILITY,
            JSONObject().put("keyword", "   ")))
        assertTrue(reply.isError())
        assertEquals("INTERNAL", (reply as HostReply.Error).code())
    }

    // ── search.history.clear ─────────────────────────────────────────────

    @Test
    fun `search_history_clear empties the list`() {
        val ctx = makeCtx()
        SearchHistoryAddHandler(ctx).handle(req(SearchHistoryAddHandler.CAPABILITY,
            JSONObject().put("keyword", "foo")))
        SearchHistoryAddHandler(ctx).handle(req(SearchHistoryAddHandler.CAPABILITY,
            JSONObject().put("keyword", "bar")))

        val clearReply = SearchHistoryClearHandler(ctx).handle(req(SearchHistoryClearHandler.CAPABILITY))
        assertTrue(clearReply.isComplete())
        assertTrue(resultJson(clearReply).getBoolean("cleared"))

        val listReply = SearchHistoryListHandler(ctx).handle(req(SearchHistoryListHandler.CAPABILITY))
        assertTrue(listReply.isComplete())
        assertEquals(0, resultJson(listReply).getInt("count"))
    }

    // ── registration smoke test ──────────────────────────────────────────

    @Test
    fun `all search history capabilities are registered via registerHandlers`() {
        val ctx = makeCtx()
        val adapter = HostRuntime.over(object : HostTransport {
            override fun pollEventJson(timeoutMillis: Long): String? = null
            override fun sendCommand(commandJson: String) {}
        }).let { runtime ->
            ctx.registerHandlers(runtime)
            runtime
        }.adapter()

        val expected = listOf(
            "search.history.list", "search.history.add", "search.history.clear"
        )
        expected.forEach { cap ->
            assertTrue("$cap must be registered", adapter.isRegistered(cap))
        }
    }
}
