package com.reader.ui.shell

import com.reader.android.BuildConfig
import io.reader.ui.runtime.ReaderUIEffectKind
import io.reader.ui.runtime.ReaderUIRuntime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderRssPilotTest {
    private val fixtures = ReaderUiTypedContractFixtures

    @Test
    fun `rss remains default Shadow in production build`() {
        assertFalse(BuildConfig.READER_UI_RSS_PILOT_ENABLED)
        assertFalse(ReaderUiRuntimeCoordinator().rssPilotEnabled)
    }

    @Test
    fun `all seven RSS events dispatch canonical lossless Core effects`() {
        val cases = listOf(
            Triple("rss.refresh", "rss-refresh-valid", "rss.feed.refresh"),
            Triple("rss.subscription.add", "rss-add-valid", "rss.subscription.persist"),
            Triple("rss.subscription.delete", "rss-delete-valid", "rss.subscription.remove"),
            Triple("rss.subscription.edit", "rss-edit-valid", "rss.subscription.persist"),
            Triple("rss.entry.open", "rss-entry-open-valid", "rss.entry.read"),
            Triple("rss.favorite.add", "rss-favorite-add-valid", "rss.favorite.persist"),
            Triple("rss.favorite.remove", "rss-favorite-remove-valid", "rss.favorite.remove")
        )

        cases.forEachIndexed { index, (event, fixtureId, effectType) ->
            val coordinator = ReaderUiRuntimeCoordinator(rssPilotEnabled = true)
            val result = coordinator.dispatchRssPilot(
                event,
                fixtures.payload(fixtureId),
                "rss-$index"
            )

            assertTrue("$event must dispatch", result is ReaderRssPilotDispatch.Dispatched)
            val effect = (result as ReaderRssPilotDispatch.Dispatched).transition.effects.single()
            assertEquals(ReaderUIEffectKind.CORE, effect.kind)
            assertEquals(effectType, effect.type)
            assertEquals("rss-$index", effect.correlationId)
            assertEquals(event, coordinator.lastObservation?.event)
            if (event == "rss.subscription.add" || event == "rss.subscription.edit") {
                assertFalse("nested params must not claim a scalar projection", effect.legacyPayloadIsComplete)
            }
        }
    }

    @Test
    fun `rss boundary failure clears active correlation`() {
        val runtime = ReaderUIRuntime()
        val coordinator = ReaderUiRuntimeCoordinator(
            runtime = runtime,
            rssPilotEnabled = true,
            runtimeDispatchOverride = { event, payload, correlationId ->
                runtime.dispatchJSON(event, payload, correlationId).copy(effects = emptyList())
            }
        )

        val result = coordinator.dispatchRssPilot(
            "rss.refresh",
            fixtures.payload("rss-refresh-valid"),
            "rss-boundary"
        )

        assertEquals(ReaderRssPilotDispatch.FailedClosed, result)
        assertEquals("RSS_EFFECT_BOUNDARY", coordinator.lastObservation?.runtimeErrorCode)
        assertFalse(coordinator.acceptRssResult("rss.feed.refresh", "rss-boundary").accepted)
    }

    @Test
    fun `rss stale guard rejects old result and typed-validates current result`() {
        val coordinator = ReaderUiRuntimeCoordinator(rssPilotEnabled = true)
        coordinator.dispatchRssPilot(
            "rss.refresh",
            fixtures.payload("rss-refresh-valid"),
            "rss-old"
        )
        coordinator.dispatchRssPilot(
            "rss.entry.open",
            fixtures.payload("rss-entry-open-valid"),
            "rss-new"
        )

        assertFalse(coordinator.acceptRssResult("rss.feed.refresh", "rss-old").accepted)
        assertTrue(
            coordinator.acceptRssResult(
                "rss.entry.read",
                "rss-new",
                fixtures.result("rss-entry-open-rss-entry-read-valid")
            ).accepted
        )
    }

    @Test
    fun `rss disabled and non RSS dispatches stay outside Pilot`() {
        assertEquals(
            ReaderRssPilotDispatch.NotEnabled,
            ReaderUiRuntimeCoordinator(rssPilotEnabled = false).dispatchRssPilot(
                "rss.refresh",
                fixtures.payload("rss-refresh-valid"),
                "rss-disabled"
            )
        )
        assertEquals(
            ReaderRssPilotDispatch.NotEnabled,
            ReaderUiRuntimeCoordinator(rssPilotEnabled = true).dispatchRssPilot(
                "import.start",
                emptyMap(),
                "rss-wrong-event"
            )
        )
    }
}
