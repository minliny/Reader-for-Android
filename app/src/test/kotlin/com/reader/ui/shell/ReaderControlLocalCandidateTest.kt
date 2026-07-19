package com.reader.ui.shell

import com.reader.ui.motion.MotionIdConstants
import io.reader.ui.runtime.ReaderUIRuntime
import io.reader.ui.runtime.ReaderUIState
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderControlLocalCandidateTest {

    @Test
    fun `schema 3 actions stay disabled and outside the schema 2 production allowlist`() {
        val runtime = ReaderUIRuntime(ReaderUIState(routeId = RouteIds.IMMERSIVE_READING))
        val coordinator = ReaderUiRuntimeCoordinator(runtime = runtime)
        val productionBefore = immersiveProductionState()
        val runtimeBefore = runtime.state

        val result = coordinator.dispatchReaderControlLocalCandidate(
            event = "reader.control.toggle",
            payload = mapOf("overlay" to JsonPrimitive("reader-control")),
            productionBefore = productionBefore
        )

        assertEquals(ReaderControlCandidateDispatch.NotEnabled, result)
        assertFalse(coordinator.readerControlLocalCandidateEnabled)
        assertTrue(
            READER_UI_RUNTIME_READER_CONTROL_LOCAL_CANDIDATE_EVENTS
                .intersect(coordinator.coveredEvents)
                .isEmpty()
        )
        assertTrue(
            READER_UI_RUNTIME_READER_CONTROL_LOCAL_CANDIDATE_EVENTS
                .intersect(coordinator.pilotEvents)
                .isEmpty()
        )
        assertEquals(runtimeBefore, runtime.state)
        assertNull(coordinator.lastObservation)
    }

    @Test
    fun `toggle show hydrates stale Runtime coordinates then projects the existing intent`() {
        val runtime = ReaderUIRuntime(
            ReaderUIState(
                routeId = "settings",
                routeStack = listOf("settings-general"),
                tab = "settings",
                overlay = "dialog"
            )
        )
        val coordinator = localCandidateCoordinator(runtime)
        val productionBefore = immersiveProductionState()
        val longLivedRuntimeBefore = runtime.state

        val applied = coordinator.dispatchReaderControlLocalCandidate(
            event = "reader.control.toggle",
            payload = mapOf("overlay" to JsonPrimitive("reader-control")),
            productionBefore = productionBefore
        ).requireApplied()

        assertEquals(ReaderControlCandidateDelta.SHOW, applied.delta)
        assertEquals(MotionIdConstants.READER_CONTROL_SHOW, applied.motionId)
        assertTrue(applied.changed)
        assertTrue(applied.productionState.readerControl.visible)
        assertEquals(MotionPhase.ENTERING, applied.productionState.readerControl.phase)
        assertRouteUnchanged(productionBefore, applied.productionState)
        assertEquals(longLivedRuntimeBefore, runtime.state)
        assertEquals(RouteIds.IMMERSIVE_READING, coordinator.lastObservation?.runtimeState?.routeId)
        assertEquals(listOf("bookshelf"), coordinator.lastObservation?.runtimeState?.routeStack)
        assertEquals("bookshelf", coordinator.lastObservation?.runtimeState?.tab)
        assertEquals("reader-control", coordinator.lastObservation?.runtimeState?.overlay)
        assertEquals(ReaderUiRuntimeDispatchMode.LOCAL_CANDIDATE, coordinator.lastObservation?.mode)
    }

    @Test
    fun `toggle hide maps every native Reader module and derives hide motion`() {
        val overlays = listOf("directory", "tts", "appearance", "settings")

        overlays.forEach { overlay ->
            val runtime = ReaderUIRuntime(
                ReaderUIState(routeId = "settings", overlay = "dialog")
            )
            val coordinator = localCandidateCoordinator(runtime)
            val productionBefore = immersiveProductionState(
                readerControl = ReaderControlState(
                    visible = true,
                    phase = MotionPhase.SETTLED,
                    activeModule = overlay.takeIf { it != "reader-control" } ?: "directory"
                )
            )
            val longLivedRuntimeBefore = runtime.state

            val applied = coordinator.dispatchReaderControlLocalCandidate(
                event = "reader.control.toggle",
                payload = mapOf("overlay" to JsonPrimitive("reader-control")),
                productionBefore = productionBefore
            ).requireApplied()

            assertEquals("overlay=$overlay", ReaderControlCandidateDelta.HIDE, applied.delta)
            assertEquals("overlay=$overlay", MotionIdConstants.READER_CONTROL_HIDE, applied.motionId)
            assertTrue("overlay=$overlay", applied.changed)
            assertTrue("overlay=$overlay", applied.productionState.readerControl.visible)
            assertEquals("overlay=$overlay", MotionPhase.LEAVING, applied.productionState.readerControl.phase)
            assertRouteUnchanged(productionBefore, applied.productionState)
            assertEquals("overlay=$overlay", longLivedRuntimeBefore, runtime.state)
            assertNull("overlay=$overlay", coordinator.lastObservation?.runtimeState?.overlay)
        }
    }

    @Test
    fun `module switch reuses native module state and derives switch motion`() {
        val runtime = ReaderUIRuntime(
            ReaderUIState(routeId = RouteIds.IMMERSIVE_READING, overlay = "appearance")
        )
        val coordinator = localCandidateCoordinator(runtime)
        val productionBefore = immersiveProductionState(
            readerControl = ReaderControlState(
                visible = true,
                phase = MotionPhase.SETTLED,
                activeModule = "appearance"
            )
        )
        val longLivedRuntimeBefore = runtime.state

        val applied = coordinator.dispatchReaderControlLocalCandidate(
            event = "reader.module.switch",
            payload = mapOf("module" to JsonPrimitive("tts")),
            productionBefore = productionBefore
        ).requireApplied()

        assertEquals(ReaderControlCandidateDelta.SWITCH, applied.delta)
        assertEquals(MotionIdConstants.READER_MODULE_SWITCH, applied.motionId)
        assertTrue(applied.changed)
        assertEquals("tts", applied.productionState.readerControl.activeModule)
        assertRouteUnchanged(productionBefore, applied.productionState)
        assertEquals(longLivedRuntimeBefore, runtime.state)
        assertEquals("tts", coordinator.lastObservation?.runtimeState?.overlay)
    }

    @Test
    fun `repeated module is an exact no-op with no motion`() {
        val runtime = ReaderUIRuntime(ReaderUIState(routeId = "settings", overlay = "dialog"))
        val coordinator = localCandidateCoordinator(runtime)
        val productionBefore = immersiveProductionState(
            readerControl = ReaderControlState(
                visible = true,
                phase = MotionPhase.SETTLED,
                activeModule = "tts"
            )
        )

        val applied = coordinator.dispatchReaderControlLocalCandidate(
            event = "reader.module.switch",
            payload = mapOf("module" to JsonPrimitive("tts")),
            productionBefore = productionBefore
        ).requireApplied()

        assertEquals(ReaderControlCandidateDelta.NO_OP, applied.delta)
        assertNull(applied.motionId)
        assertFalse(applied.changed)
        assertEquals(productionBefore, applied.productionState)
        assertEquals("dialog", runtime.state.overlay)
        assertEquals("tts", coordinator.lastObservation?.runtimeState?.overlay)
    }

    @Test
    fun `wrong Android route fails closed before Runtime mutation`() {
        val runtime = ReaderUIRuntime(ReaderUIState(routeId = RouteIds.IMMERSIVE_READING))
        val coordinator = localCandidateCoordinator(runtime)
        val productionBefore = ReaderUiState()
        val runtimeBefore = runtime.state

        val result = coordinator.dispatchReaderControlLocalCandidate(
            event = "reader.control.toggle",
            payload = mapOf("overlay" to JsonPrimitive("reader-control")),
            productionBefore = productionBefore
        )

        assertEquals(ReaderControlCandidateDispatch.FailedClosed, result)
        assertEquals(runtimeBefore, runtime.state)
        assertEquals("READER_ROUTE_GUARD", coordinator.lastObservation?.runtimeErrorCode)
        assertEquals(ReaderUiRuntimeDispatchMode.LOCAL_CANDIDATE, coordinator.lastObservation?.mode)
    }

    @Test
    fun `native non Reader overlay fails closed for toggle and module switch`() {
        listOf(
            "reader.control.toggle" to mapOf("overlay" to JsonPrimitive("reader-control")),
            "reader.module.switch" to mapOf("module" to JsonPrimitive("tts"))
        ).forEach { (event, payload) ->
            val runtime = ReaderUIRuntime(
                ReaderUIState(routeId = RouteIds.IMMERSIVE_READING, overlay = "dialog")
            )
            val coordinator = localCandidateCoordinator(runtime)
            val productionBefore = immersiveProductionState(
                readerControl = ReaderControlState(visible = true),
                overlayState = OverlayState.Dialog(
                    DialogContent.Confirm(title = "Confirm", message = "blocked", onConfirm = {})
                )
            )
            val runtimeBefore = runtime.state

            val result = coordinator.dispatchReaderControlLocalCandidate(
                event = event,
                payload = payload,
                productionBefore = productionBefore
            )

            assertEquals("event=$event", ReaderControlCandidateDispatch.FailedClosed, result)
            assertEquals("event=$event", runtimeBefore, runtime.state)
            assertEquals(
                "event=$event",
                "READER_CONTROL_OVERLAY_GUARD",
                coordinator.lastObservation?.runtimeErrorCode
            )
        }
    }

    @Test
    fun `unknown native module fails admission before candidate dispatch`() {
        val runtime = ReaderUIRuntime(ReaderUIState(routeId = "settings", overlay = "dialog"))
        val coordinator = localCandidateCoordinator(runtime)
        val productionBefore = immersiveProductionState(
            readerControl = ReaderControlState(
                visible = true,
                phase = MotionPhase.SETTLED,
                activeModule = "search"
            )
        )
        val runtimeBefore = runtime.state

        val result = coordinator.dispatchReaderControlLocalCandidate(
            event = "reader.module.switch",
            payload = mapOf("module" to JsonPrimitive("tts")),
            productionBefore = productionBefore
        )

        assertEquals(ReaderControlCandidateDispatch.FailedClosed, result)
        assertEquals(runtimeBefore, runtime.state)
        assertEquals("READER_CONTROL_NATIVE_STATE_GUARD", coordinator.lastObservation?.runtimeErrorCode)
    }

    private fun localCandidateCoordinator(runtime: ReaderUIRuntime): ReaderUiRuntimeCoordinator =
        ReaderUiRuntimeCoordinator(
            runtime = runtime,
            readerControlLocalCandidateEnabled = true
        )

    private fun immersiveProductionState(
        readerControl: ReaderControlState = ReaderControlState(),
        overlayState: OverlayState = OverlayState.None
    ): ReaderUiState {
        val context = ReaderContext(
            sourceId = "source-1",
            bookUrl = "book-1",
            bookName = "Book One",
            entry = ReaderEntry.COVER_TO_IMMERSIVE,
            entryRequestId = "entry-1"
        )
        return ReaderUiState(
            currentRoute = ReaderRoute.ImmersiveReading(context),
            backStack = listOf(ReaderRoute.TabShell(MainTab.BOOKSHELF)),
            readerContext = context,
            readerControl = readerControl,
            overlayState = overlayState
        )
    }

    private fun ReaderControlCandidateDispatch.requireApplied(): ReaderControlCandidateDispatch.Applied {
        assertTrue(this is ReaderControlCandidateDispatch.Applied)
        return this as ReaderControlCandidateDispatch.Applied
    }

    private fun assertRouteUnchanged(before: ReaderUiState, after: ReaderUiState) {
        assertEquals(before.currentRoute, after.currentRoute)
        assertEquals(before.backStack, after.backStack)
        assertEquals(before.activeTab, after.activeTab)
        assertEquals(before.readerContext, after.readerContext)
    }
}
