package com.reader.ui.shell

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.reader.android.BuildConfig
import io.reader.ui.runtime.ReaderUIEffect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * API-35 smoke for the independent book.open rollout switch. H4-B promotes
 * book.open to Pilot by default; the rollback case is selected explicitly with
 * `-PreaderUiBookOpenPilotEnabled=false` to verify shadow restoration.
 */
@RunWith(AndroidJUnit4::class)
class ReaderBookOpenPilotInstrumentedTest {

    @Test
    fun defaultBuildAdmitsBookOpenPilotOnDevice() {
        assertTrue(BuildConfig.READER_UI_BOOK_OPEN_PILOT_ENABLED)
        val coordinator = ReaderUiRuntimeCoordinator(bookOpenPilotEnabled = true)
        val store = ReaderBookOpenDomainStore()
        val executor = RecordingAdmissionExecutor(store, coordinator)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val vm = AppShellViewModel(
            readerUiRuntimeCoordinator = coordinator,
            readerBookOpenDomainStore = store,
            readerBookOpenEffectExecutor = executor,
            readerBookOpenScope = scope
        )

        try {
            vm.dispatch(
                ReaderUiIntent.EnterReaderFromAction(
                    sourceId = "source-device",
                    bookUrl = "book-device",
                    bookName = "Device Book",
                    requestId = "device-pilot-default"
                )
            )

            assertEquals(RouteIds.IMMERSIVE_READING, vm.state.value.currentRoute.routeId)
            assertEquals(ReaderUiRuntimeDispatchMode.PILOT, vm.readerUiRuntimeShadowObservation?.mode)
            assertEquals(1, executor.entries.size)
            assertEquals(
                "device-pilot-default",
                coordinator.runtimeState.bookOpenTransaction?.correlationId
            )
        } finally {
            executor.cancel("device-pilot-default")
            scope.cancel()
        }
    }

    @Test
    fun explicitBuildFlagAdmitsOnePilotExecutorBeforeNativePresentation() {
        assumeTrue(BuildConfig.READER_UI_BOOK_OPEN_PILOT_ENABLED)
        val coordinator = ReaderUiRuntimeCoordinator(bookOpenPilotEnabled = true)
        val store = ReaderBookOpenDomainStore()
        val executor = RecordingAdmissionExecutor(store, coordinator)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val vm = AppShellViewModel(
            readerUiRuntimeCoordinator = coordinator,
            readerBookOpenDomainStore = store,
            readerBookOpenEffectExecutor = executor,
            readerBookOpenScope = scope
        )

        try {
            vm.dispatch(
                ReaderUiIntent.EnterReaderFromAction(
                    sourceId = "source-device",
                    bookUrl = "book-device",
                    bookName = "Device Book",
                    requestId = "device-pilot-open"
                )
            )

            assertEquals(RouteIds.IMMERSIVE_READING, vm.state.value.currentRoute.routeId)
            assertEquals(1, executor.entries.size)
            assertEquals("source.detail", executor.entries.single().type)
            assertEquals("device-pilot-open", coordinator.runtimeState.bookOpenTransaction?.correlationId)
            assertTrue(store.state.value.activeCorrelationId == "device-pilot-open")
        } finally {
            executor.cancel("device-pilot-open")
            scope.cancel()
        }
    }

    private class RecordingAdmissionExecutor(
        private val store: ReaderBookOpenDomainStore,
        runtime: ReaderBookOpenRuntimeDriver
    ) : ReaderBookOpenEffectExecutor(store, runtime) {
        val entries = mutableListOf<ReaderUIEffect>()

        override fun start(context: ReaderContext, firstEffect: ReaderUIEffect, scope: CoroutineScope) {
            store.begin(context)
            entries += firstEffect
        }
    }
}
