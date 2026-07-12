package com.reader.ui.shell

import com.reader.android.BuildConfig
import io.reader.ui.runtime.ReaderUIEffectKind
import io.reader.ui.runtime.ReaderUIRuntime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderSyncPilotTest {
    private val fixtures = ReaderUiTypedContractFixtures

    @Test
    fun `sync remains default Shadow in production build`() {
        assertFalse(BuildConfig.READER_UI_SYNC_PILOT_ENABLED)
        val coordinator = ReaderUiRuntimeCoordinator()
        assertFalse(coordinator.syncPilotEnabled)
        assertEquals(
            ReaderSyncPilotDispatch.NotEnabled,
            coordinator.dispatchSyncPilot(
                "sync.run",
                fixtures.payload("sync.run-valid"),
                "sync-shadow"
            )
        )
    }

    @Test
    fun `all seven sync events dispatch canonical lossless effect sequences`() {
        val cases = listOf(
            Triple("sync.run", "sync.run-valid", listOf("sync.snapshot", "sync.push")),
            Triple("webdav.config.test", "webdav.config.test-valid", listOf("sync.push")),
            Triple("sync.start", "sync.start-valid", listOf("sync.snapshot")),
            Triple("sync.progress", "sync.progress-valid", listOf("sync.push")),
            Triple("sync.complete", "sync.complete-valid", listOf("sync.push")),
            Triple("sync.conflict", "sync-conflict-valid", listOf("sync.conflict.detect")),
            Triple("sync.resolve", "sync.resolve-valid", listOf("sync.conflict.resolve"))
        )

        cases.forEachIndexed { index, (event, fixtureId, expectedCore) ->
            val coordinator = ReaderUiRuntimeCoordinator(syncPilotEnabled = true)
            val result = coordinator.dispatchSyncPilot(event, fixtures.payload(fixtureId), "sync-$index")

            assertTrue("$event must dispatch", result is ReaderSyncPilotDispatch.Dispatched)
            val effects = (result as ReaderSyncPilotDispatch.Dispatched).transition.effects
            assertEquals(expectedCore, effects.filter { it.kind == ReaderUIEffectKind.CORE }.map { it.type })
            assertTrue(effects.all { it.correlationId == "sync-$index" })
            assertTrue(effects.filter { it.kind == ReaderUIEffectKind.CORE }.all { !it.legacyPayloadIsComplete })
            if (event == "webdav.config.test") {
                assertEquals("http.execute", effects.single { it.kind == ReaderUIEffectKind.HOST }.type)
            }
        }
    }

    @Test
    fun `sync run remains active until both typed Core results validate`() {
        val coordinator = ReaderUiRuntimeCoordinator(syncPilotEnabled = true)
        coordinator.dispatchSyncPilot(
            "sync.run",
            fixtures.payload("sync.run-valid"),
            "sync-run-results"
        )

        val snapshot = coordinator.acceptSyncResult(
            "sync.snapshot",
            "sync-run-results",
            fixtures.result("sync-run-sync-snapshot-valid")
        )
        assertTrue(snapshot.accepted)
        assertFalse(snapshot.completed)

        val push = coordinator.acceptSyncResult(
            "sync.push",
            "sync-run-results",
            fixtures.result("sync-run-sync-push-valid")
        )
        assertTrue(push.accepted)
        assertTrue(push.completed)
    }

    @Test
    fun `sync stale guards reject superseded start and conflict results`() {
        val cases = listOf(
            Triple("sync.start", "sync.start-valid", "sync.snapshot"),
            Triple("sync.conflict", "sync-conflict-valid", "sync.conflict.detect")
        )
        cases.forEachIndexed { index, (event, fixtureId, effectType) ->
            val coordinator = ReaderUiRuntimeCoordinator(syncPilotEnabled = true)
            coordinator.dispatchSyncPilot(event, fixtures.payload(fixtureId), "old-$index")
            coordinator.dispatchSyncPilot(event, fixtures.payload(fixtureId), "new-$index")
            assertFalse(coordinator.acceptSyncResult(effectType, "old-$index").accepted)
        }
    }

    @Test
    fun `sync resolve cancel clears active correlation`() {
        val coordinator = ReaderUiRuntimeCoordinator(syncPilotEnabled = true)
        coordinator.dispatchSyncPilot(
            "sync.resolve",
            fixtures.payload("sync.resolve-valid"),
            "sync-resolve"
        )

        assertTrue(coordinator.cancelSync("sync-resolve"))
        assertFalse(coordinator.acceptSyncResult("sync.conflict.resolve", "sync-resolve").accepted)
    }

    @Test
    fun `sync boundary failure clears transaction without orphan ledger`() {
        val runtime = ReaderUIRuntime()
        val coordinator = ReaderUiRuntimeCoordinator(
            runtime = runtime,
            syncPilotEnabled = true,
            runtimeDispatchOverride = { event, payload, correlationId ->
                runtime.dispatchJSON(event, payload, correlationId).copy(effects = emptyList())
            }
        )

        val result = coordinator.dispatchSyncPilot(
            "sync.run",
            fixtures.payload("sync.run-valid"),
            "sync-boundary"
        )

        assertEquals(ReaderSyncPilotDispatch.FailedClosed, result)
        assertEquals("SYNC_EFFECT_BOUNDARY", coordinator.lastObservation?.runtimeErrorCode)
        assertFalse(coordinator.acceptSyncResult("sync.snapshot", "sync-boundary").accepted)
    }

    @Test
    fun `sync stale guard works across events and current typed result is accepted`() {
        val coordinator = ReaderUiRuntimeCoordinator(syncPilotEnabled = true)
        coordinator.dispatchSyncPilot(
            "sync.start",
            fixtures.payload("sync.start-valid"),
            "sync-old"
        )
        coordinator.dispatchSyncPilot(
            "sync.progress",
            fixtures.payload("sync.progress-valid"),
            "sync-new"
        )

        assertFalse(coordinator.acceptSyncResult("sync.snapshot", "sync-old").accepted)
        val current = coordinator.acceptSyncResult(
            "sync.push",
            "sync-new",
            fixtures.result("sync-progress-sync-push-valid")
        )
        assertTrue(current.accepted)
        assertTrue(current.completed)
    }

    @Test
    fun `non sync event stays outside typed Sync seam`() {
        assertEquals(
            ReaderSyncPilotDispatch.NotEnabled,
            ReaderUiRuntimeCoordinator(syncPilotEnabled = true).dispatchSyncPilot(
                "rss.refresh",
                emptyMap(),
                "sync-wrong-event"
            )
        )
    }
}
