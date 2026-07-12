package com.reader.ui.shell

import com.reader.android.BuildConfig
import io.reader.ui.runtime.ReaderUIEffectKind
import io.reader.ui.runtime.ReaderUIRuntime
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderReplaceRulePilotTest {
    private val fixtures = ReaderUiTypedContractFixtures

    @Test
    fun `replace rule remains default Shadow in production build`() {
        assertFalse(BuildConfig.READER_UI_REPLACE_RULE_PILOT_ENABLED)
        assertFalse(ReaderUiRuntimeCoordinator().replaceRulePilotEnabled)
    }

    @Test
    fun `legacy replace intents fail closed without fabricating R14 apply context`() {
        val coordinator = ReaderUiRuntimeCoordinator(replaceRulePilotEnabled = true)
        val intents = listOf<ReaderUiIntent>(
            ReaderUiIntent.ReplaceRuleAdd("r", "a", "b", "all", "legacy-add"),
            ReaderUiIntent.ReplaceRuleUpdate("rule-1", pattern = "b", requestId = "legacy-update"),
            ReaderUiIntent.ReplaceRuleDelete("rule-1", "legacy-delete"),
            ReaderUiIntent.ReplaceRuleToggle("rule-1", "legacy-toggle"),
            ReaderUiIntent.ReplaceRulesLoaded(emptyList(), "legacy-validate")
        )

        intents.forEach { intent ->
            assertEquals(ReaderReplaceRulePilotDispatch.FailedClosed, coordinator.dispatchReplaceRulePilot(intent))
            assertEquals("REPLACE_RULE_TYPED_DATA_UNAVAILABLE", coordinator.lastObservation?.runtimeErrorCode)
        }
    }

    @Test
    fun `typed replace create dispatches lossless persist effect`() {
        val coordinator = ReaderUiRuntimeCoordinator(replaceRulePilotEnabled = true)
        val result = coordinator.dispatchReplaceRulePilotJSON(
            "reader.replace.create",
            fixtures.payload("replace-create-valid"),
            "replace-create-1"
        )

        assertTrue(result is ReaderReplaceRulePilotDispatch.Applied)
        val effect = (result as ReaderReplaceRulePilotDispatch.Applied).transition.effects.single()
        assertEquals(ReaderUIEffectKind.CORE, effect.kind)
        assertEquals("replace.persist", effect.type)
        assertFalse(effect.legacyPayloadIsComplete)
    }

    @Test
    fun `typed replace apply dispatches apply effect`() {
        val coordinator = ReaderUiRuntimeCoordinator(replaceRulePilotEnabled = true)
        val result = coordinator.dispatchReplaceRulePilotJSON(
            "reader.replace.apply",
            fixtures.payload("replace-apply-valid"),
            "replace-apply-1"
        )

        assertTrue(result is ReaderReplaceRulePilotDispatch.Applied)
        assertEquals(
            "replace.apply",
            (result as ReaderReplaceRulePilotDispatch.Applied).transition.effects.single().type
        )
    }

    @Test
    fun `typed replace validate dispatches validate effect`() {
        val coordinator = ReaderUiRuntimeCoordinator(replaceRulePilotEnabled = true)
        val result = coordinator.dispatchReplaceRulePilotJSON(
            "reader.replace.validate",
            fixtures.payload("replace-validate-valid"),
            "replace-validate-1"
        )

        assertTrue(result is ReaderReplaceRulePilotDispatch.Applied)
        assertEquals(
            "replace.validate",
            (result as ReaderReplaceRulePilotDispatch.Applied).transition.effects.single().type
        )
    }

    @Test
    fun `typed replace boundary failure clears active correlation`() {
        val runtime = ReaderUIRuntime()
        val coordinator = ReaderUiRuntimeCoordinator(
            runtime = runtime,
            replaceRulePilotEnabled = true,
            runtimeDispatchOverride = { event, payload, correlationId ->
                runtime.dispatchJSON(event, payload, correlationId).copy(effects = emptyList())
            }
        )
        val result = coordinator.dispatchReplaceRulePilotJSON(
            "reader.replace.create",
            fixtures.payload("replace-create-valid"),
            "replace-boundary-1"
        )

        assertEquals(ReaderReplaceRulePilotDispatch.FailedClosed, result)
        assertEquals("REPLACE_RULE_EFFECT_BOUNDARY", coordinator.lastObservation?.runtimeErrorCode)
        assertFalse(coordinator.acceptReplaceRuleResult("replace.persist", "replace-boundary-1").accepted)
    }

    @Test
    fun `typed replace stale guard rejects superseded correlation and validates current result`() {
        val coordinator = ReaderUiRuntimeCoordinator(replaceRulePilotEnabled = true)
        coordinator.dispatchReplaceRulePilotJSON(
            "reader.replace.create",
            fixtures.payload("replace-create-valid"),
            "replace-old"
        )
        coordinator.dispatchReplaceRulePilotJSON(
            "reader.replace.apply",
            fixtures.payload("replace-apply-valid"),
            "replace-new"
        )

        assertFalse(coordinator.acceptReplaceRuleResult("replace.persist", "replace-old").accepted)
        assertTrue(
            coordinator.acceptReplaceRuleResult(
                "replace.apply",
                "replace-new",
                fixtures.result("reader-replace-apply-replace-apply-valid")
            ).accepted
        )
    }

    @Test
    fun `default Shadow keeps native replace reducer authoritative`() {
        val nativeReducerCalls = AtomicInteger()
        val coordinator = ReaderUiRuntimeCoordinator(replaceRulePilotEnabled = false)
        val vm = AppShellViewModel(
            readerUiRuntimeCoordinator = coordinator,
            nativeReducer = { state, intent ->
                nativeReducerCalls.incrementAndGet()
                ReaderUiReducer.reduce(state, intent)
            }
        )

        vm.dispatch(ReaderUiIntent.ReplaceRuleAdd("native", "a", requestId = "shadow-replace"))

        assertEquals(1, nativeReducerCalls.get())
        assertEquals("native", vm.state.value.replaceRules.single().name)
        assertEquals("REPLACE_RULE_TYPED_DATA_UNAVAILABLE", coordinator.lastObservation?.runtimeErrorCode)
        assertEquals(ReaderUiRuntimeDispatchMode.SHADOW, coordinator.lastObservation?.mode)
    }
}
