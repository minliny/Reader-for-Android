package com.reader.ui.shell

import com.reader.android.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * W1 authority boundary proof.
 *
 * Production's legacy import intents do not contain the R14 discriminated
 * input/parse result/rollback token. They therefore remain native-authoritative
 * Shadow and must fail closed before ReaderUIRuntime, never fabricate payloads.
 */
class ReaderImportPilotTest {

    @Test
    fun `import remains default Shadow in production build`() {
        assertFalse(BuildConfig.READER_UI_IMPORT_PILOT_ENABLED)
        val coordinator = ReaderUiRuntimeCoordinator()
        assertFalse(coordinator.importPilotEnabled)
    }

    @Test
    fun `legacy raw json start fails before Runtime instead of claiming canonical input`() {
        val runtimeCalls = AtomicInteger()
        val coordinator = ReaderUiRuntimeCoordinator(
            importPilotEnabled = true,
            runtimeDispatchOverride = { _, _, _ ->
                runtimeCalls.incrementAndGet()
                error("legacy W1 input must not enter Runtime")
            }
        )

        val result = coordinator.dispatchImportPilot(
            ReaderUiIntent.ParseSourceImport(
                json = "[{\"name\":\"source-a\"}]",
                requestId = "import-start-legacy"
            )
        )

        assertEquals(ReaderImportPilotDispatch.FailedClosed, result)
        assertEquals(0, runtimeCalls.get())
        assertEquals("import.start", coordinator.lastObservation?.event)
        assertEquals("IMPORT_TYPED_DATA_UNAVAILABLE", coordinator.lastObservation?.runtimeErrorCode)
        assertEquals(ReaderUiRuntimeDispatchMode.PILOT, coordinator.lastObservation?.mode)
    }

    @Test
    fun `legacy conflict mode apply fails before Runtime without parsed transaction`() {
        val runtimeCalls = AtomicInteger()
        val coordinator = ReaderUiRuntimeCoordinator(
            importPilotEnabled = true,
            runtimeDispatchOverride = { _, _, _ ->
                runtimeCalls.incrementAndGet()
                error("legacy W1 apply must not enter Runtime")
            }
        )

        val result = coordinator.dispatchImportPilot(
            ReaderUiIntent.ConfirmSourceImport(
                conflictMode = "overwrite",
                requestId = "import-apply-legacy"
            )
        )

        assertEquals(ReaderImportPilotDispatch.FailedClosed, result)
        assertEquals(0, runtimeCalls.get())
        assertEquals("import.apply", coordinator.lastObservation?.event)
        assertEquals("IMPORT_TYPED_DATA_UNAVAILABLE", coordinator.lastObservation?.runtimeErrorCode)
    }

    @Test
    fun `legacy empty cancel fails before Runtime without rollback token`() {
        val runtimeCalls = AtomicInteger()
        val coordinator = ReaderUiRuntimeCoordinator(
            importPilotEnabled = true,
            runtimeDispatchOverride = { _, _, _ ->
                runtimeCalls.incrementAndGet()
                error("legacy W1 cancel must not enter Runtime")
            }
        )

        val result = coordinator.dispatchImportPilot(ReaderUiIntent.DismissSourceImportResult)

        assertEquals(ReaderImportPilotDispatch.FailedClosed, result)
        assertEquals(0, runtimeCalls.get())
        assertEquals("import.cancel", coordinator.lastObservation?.event)
        assertEquals("IMPORT_TYPED_DATA_UNAVAILABLE", coordinator.lastObservation?.runtimeErrorCode)
    }

    @Test
    fun `default Shadow keeps native reducer authoritative and never dispatches legacy payload`() {
        val nativeReducerCalls = AtomicInteger()
        val runtimeCalls = AtomicInteger()
        val coordinator = ReaderUiRuntimeCoordinator(
            importPilotEnabled = false,
            runtimeDispatchOverride = { _, _, _ ->
                runtimeCalls.incrementAndGet()
                error("legacy W1 Shadow input must not enter Runtime")
            }
        )
        val vm = AppShellViewModel(
            readerUiRuntimeCoordinator = coordinator,
            nativeReducer = { state, intent ->
                nativeReducerCalls.incrementAndGet()
                ReaderUiReducer.reduce(state, intent)
            }
        )

        vm.dispatch(
            ReaderUiIntent.ParseSourceImport(
                json = "[]",
                requestId = "import-shadow-legacy"
            )
        )

        assertEquals(1, nativeReducerCalls.get())
        assertEquals(0, runtimeCalls.get())
        assertEquals(ReaderUiRuntimeDispatchMode.SHADOW, vm.readerUiRuntimeShadowObservation?.mode)
        assertEquals("import.start", vm.readerUiRuntimeShadowObservation?.event)
        assertEquals(
            "IMPORT_TYPED_DATA_UNAVAILABLE",
            vm.readerUiRuntimeShadowObservation?.runtimeErrorCode
        )
    }
}
