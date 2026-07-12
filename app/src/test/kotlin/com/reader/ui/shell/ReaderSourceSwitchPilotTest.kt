package com.reader.ui.shell

import com.reader.android.BuildConfig
import io.reader.ui.runtime.ReaderUIEffectKind
import io.reader.ui.runtime.ReaderUIRuntime
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderSourceSwitchPilotTest {
    private val fixtures = ReaderUiTypedContractFixtures

    @Test
    fun `source switch remains default Shadow in production build`() {
        assertFalse(BuildConfig.READER_UI_SOURCE_SWITCH_PILOT_ENABLED)
        assertFalse(ReaderUiRuntimeCoordinator().sourceSwitchPilotEnabled)
    }

    @Test
    fun `source switch overlay open pilot projects route and loading state`() {
        val coordinator = ReaderUiRuntimeCoordinator(sourceSwitchPilotEnabled = true)
        val result = coordinator.dispatchSourceSwitchPilot(
            ReaderUiIntent.SourceSwitchOpen("book-1", "Book One", "source-1", "ss-open-1"),
            ReaderUiState()
        )

        assertTrue(result is ReaderSourceSwitchPilotDispatch.OverlayApplied)
        val state = (result as ReaderSourceSwitchPilotDispatch.OverlayApplied).productionState
        assertTrue(state.currentRoute is ReaderRoute.SourceSwitchFlow)
        assertEquals(SourceSwitchState.Loading, state.sourceSwitch)
        assertEquals("source.switch.open", coordinator.lastObservation?.event)
    }

    @Test
    fun `source switch overlay cancel pilot projects route pop and idle state`() {
        val coordinator = ReaderUiRuntimeCoordinator(sourceSwitchPilotEnabled = true)
        val opened = coordinator.dispatchSourceSwitchPilot(
            ReaderUiIntent.SourceSwitchOpen("book-1", "Book One", "source-1", "ss-open-2"),
            ReaderUiState()
        ) as ReaderSourceSwitchPilotDispatch.OverlayApplied

        val result = coordinator.dispatchSourceSwitchPilot(
            ReaderUiIntent.SourceSwitchClose,
            opened.productionState
        )

        assertTrue(result is ReaderSourceSwitchPilotDispatch.OverlayApplied)
        val state = (result as ReaderSourceSwitchPilotDispatch.OverlayApplied).productionState
        assertEquals(SourceSwitchState.Idle, state.sourceSwitch)
        assertFalse(state.currentRoute is ReaderRoute.SourceSwitchFlow)
        assertEquals("source.switch.cancel", coordinator.lastObservation?.event)
    }

    @Test
    fun `legacy effect intents fail closed because R14 transaction DTO is unavailable`() {
        val coordinator = ReaderUiRuntimeCoordinator(sourceSwitchPilotEnabled = true)
        val intents = listOf<ReaderUiIntent>(
            ReaderUiIntent.SourceSwitchConfirm("source-new", "legacy-confirm"),
            ReaderUiIntent.SourceSwitchCancel
        )

        intents.forEach { intent ->
            assertEquals(
                ReaderSourceSwitchPilotDispatch.FailedClosed,
                coordinator.dispatchSourceSwitchPilot(intent, ReaderUiState())
            )
            assertEquals("SOURCE_SWITCH_TYPED_DATA_UNAVAILABLE", coordinator.lastObservation?.runtimeErrorCode)
        }
    }

    @Test
    fun `typed source switch confirm dispatches lossless commit effect`() {
        val coordinator = ReaderUiRuntimeCoordinator(sourceSwitchPilotEnabled = true)
        val result = coordinator.dispatchSourceSwitchPilotJSON(
            "source.switch.confirm",
            fixtures.payload("source-switch-confirm-valid"),
            "ss-confirm-1"
        )

        assertTrue(result is ReaderSourceSwitchPilotDispatch.EffectApplied)
        val effect = (result as ReaderSourceSwitchPilotDispatch.EffectApplied).transition.effects.single()
        assertEquals(ReaderUIEffectKind.CORE, effect.kind)
        assertEquals("source.switch.commit", effect.type)
        assertFalse(effect.legacyPayloadIsComplete)
        assertEquals("source-new", effect.jsonPayload["target"]?.toString()?.let { org.json.JSONObject(it).getString("sourceId") })
    }

    @Test
    fun `typed source switch rollback dispatches rollback effect`() {
        val coordinator = ReaderUiRuntimeCoordinator(sourceSwitchPilotEnabled = true)
        val result = coordinator.dispatchSourceSwitchPilotJSON(
            "source.switch.rollback",
            fixtures.payload("source-switch-rollback-valid"),
            "ss-rollback-1"
        )

        assertTrue(result is ReaderSourceSwitchPilotDispatch.EffectApplied)
        assertEquals(
            "source.switch.rollback",
            (result as ReaderSourceSwitchPilotDispatch.EffectApplied).transition.effects.single().type
        )
    }

    @Test
    fun `typed source switch boundary failure clears active correlation`() {
        val runtime = ReaderUIRuntime()
        val coordinator = ReaderUiRuntimeCoordinator(
            runtime = runtime,
            sourceSwitchPilotEnabled = true,
            runtimeDispatchOverride = { event, payload, correlationId ->
                runtime.dispatchJSON(event, payload, correlationId).copy(effects = emptyList())
            }
        )

        val result = coordinator.dispatchSourceSwitchPilotJSON(
            "source.switch.confirm",
            fixtures.payload("source-switch-confirm-valid"),
            "ss-boundary-1"
        )

        assertEquals(ReaderSourceSwitchPilotDispatch.FailedClosed, result)
        assertEquals("SOURCE_SWITCH_EFFECT_BOUNDARY", coordinator.lastObservation?.runtimeErrorCode)
        assertFalse(coordinator.acceptSourceSwitchResult("source.switch.commit", "ss-boundary-1").accepted)
    }

    @Test
    fun `typed source switch stale result guard rejects superseded correlation`() {
        val coordinator = ReaderUiRuntimeCoordinator(sourceSwitchPilotEnabled = true)
        coordinator.dispatchSourceSwitchPilotJSON(
            "source.switch.confirm",
            fixtures.payload("source-switch-confirm-valid"),
            "ss-old"
        )
        coordinator.dispatchSourceSwitchPilotJSON(
            "source.switch.rollback",
            fixtures.payload("source-switch-rollback-valid"),
            "ss-new"
        )

        assertFalse(coordinator.acceptSourceSwitchResult("source.switch.commit", "ss-old").accepted)
        assertTrue(
            coordinator.acceptSourceSwitchResult(
                "source.switch.rollback",
                "ss-new",
                fixtures.result("source-switch-rollback-source-switch-rollback-valid")
            ).accepted
        )
    }

    @Test
    fun `default Shadow keeps native source switch authoritative`() {
        val nativeReducerCalls = AtomicInteger()
        val coordinator = ReaderUiRuntimeCoordinator(sourceSwitchPilotEnabled = false)
        val vm = AppShellViewModel(
            readerUiRuntimeCoordinator = coordinator,
            nativeReducer = { state, intent ->
                nativeReducerCalls.incrementAndGet()
                ReaderUiReducer.reduce(state, intent)
            }
        )

        vm.dispatch(ReaderUiIntent.SourceSwitchOpen("book-1", "Book One", "source-1", "shadow-1"))

        assertEquals(1, nativeReducerCalls.get())
        assertTrue(vm.state.value.currentRoute is ReaderRoute.SourceSwitchFlow)
    }
}
