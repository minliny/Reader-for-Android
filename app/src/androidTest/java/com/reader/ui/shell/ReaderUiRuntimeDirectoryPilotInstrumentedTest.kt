package com.reader.ui.shell

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.reader.android.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** API/device proof that the production composite runtime drives the R8 route projection. */
@RunWith(AndroidJUnit4::class)
class ReaderUiRuntimeDirectoryPilotInstrumentedTest {

    @Test
    fun directoryEntryAndBackUsePilotRuntimeOnDevice() {
        assertTrue(BuildConfig.READER_UI_DIRECTORY_PILOT_ENABLED)
        val vm = AppShellViewModel()

        vm.dispatch(
            ReaderUiIntent.PushRoute(
                route = ReaderRoute.ReaderControl(id = RouteIds.READER_TOC_BOOKMARKS),
                requestId = "device-directory-open"
            )
        )
        assertEquals("directory", vm.readerUiRuntimeShadowState.overlay)
        assertEquals(RouteIds.READER_TOC_BOOKMARKS, vm.state.value.currentRoute.routeId)
        assertEquals(ReaderUiRuntimeDispatchMode.PILOT, vm.readerUiRuntimeShadowObservation?.mode)
        assertTrue(vm.readerUiRuntimeShadowObservation?.runtimeEffects.orEmpty().isEmpty())

        // Same intent used by AppShell's system/top-bar Back handling.
        vm.dispatch(ReaderUiIntent.PopRoute)
        assertEquals(null, vm.readerUiRuntimeShadowState.overlay)
        assertEquals(MainTab.BOOKSHELF.routeId, vm.state.value.currentRoute.routeId)
        assertEquals(ReaderUiRuntimeShadowMetrics(covered = 2), vm.readerUiRuntimeShadowMetrics)
    }
}
