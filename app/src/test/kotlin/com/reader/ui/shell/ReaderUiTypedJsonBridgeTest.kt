package com.reader.ui.shell

import io.reader.ui.runtime.ReaderUIRuntimeException
import kotlinx.serialization.json.JsonObject
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Test

class ReaderUiTypedJsonBridgeTest {
    private val fixtures = ReaderUiTypedContractFixtures

    @Test
    fun `Core result is recursively projected before typed validation`() {
        val raw = JSONObject(JsonObject(fixtures.result("rss-refresh-rss-feed-refresh-valid")).toString())
            .put("coreDebug", "private")
        raw.getJSONObject("subscription").put("databaseRowId", 42)

        val projected = projectAndValidateReaderUiResult(
            "rss.refresh",
            "rss.feed.refresh",
            raw
        )
        val json = JSONObject(JsonObject(projected).toString())

        assertFalse(json.has("coreDebug"))
        assertFalse(json.getJSONObject("subscription").has("databaseRowId"))
        assertEquals("feed-1", json.getJSONObject("subscription").getString("subscriptionId"))
    }

    @Test
    fun `projection cannot manufacture a missing required Core result field`() {
        try {
            projectAndValidateReaderUiResult(
                "rss.entry.open",
                "rss.entry.read",
                JSONObject().put("guid", "g1").put("read", true)
            )
            fail("missing marked, subscriptionId and unreadCount must fail closed")
        } catch (error: ReaderUIRuntimeException) {
            assertEquals("INVALID_TYPED_RESULT", error.code)
        }
    }

    @Test
    fun `nested typed request survives JSONObject conversion losslessly`() {
        val payload = fixtures.payload("source-switch-confirm-valid")
        val json = payload.toCanonicalJSONObject()

        assertEquals("source-new", json.getJSONObject("target").getString("sourceId"))
        assertEquals(0, json.getJSONArray("newToc").getJSONObject(0).getInt("order"))
        assertEquals(300L, json.getLong("updatedAt"))
    }
}
