package com.reader.android.ui.state

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class ReaderUiStateAdapterTest {

    private val stateSource: String by lazy {
        String(Files.readAllBytes(Paths.get("src/main/kotlin/com/reader/android/ui/state/ReaderUiState.kt")))
    }

    private val foundationSource: String by lazy {
        String(Files.readAllBytes(Paths.get("src/main/kotlin/com/reader/android/ui/state/ReaderUiStateFoundation.kt")))
    }

    @Test
    fun `reader ui state keeps twelve slice 7 states`() {
        val expected = listOf(
            "Loading",
            "Empty",
            "Error",
            "Offline",
            "Disabled",
            "PermissionRequired",
            "LocalFileError",
            "NetworkSourceError",
            "WebDavAuthError",
            "SyncConflict",
            "ImportSuccess",
            "ImportFailure"
        )

        expected.forEach { state ->
            assertTrue("ReaderUiState must keep $state", state in stateSource)
        }
        assertEquals(12, expected.size)
    }

    @Test
    fun `foundation exposes required ui state carriers`() {
        listOf(
            "ReaderScreenState",
            "ReaderListState",
            "ReaderActionState",
            "ReaderLoadingState",
            "ReaderEmptyState",
            "ReaderErrorState",
            "ReaderOfflineState",
            "ReaderPermissionState"
        ).forEach { token ->
            assertTrue("Foundation must contain $token", token in foundationSource)
        }
    }

    @Test
    fun `foundation does not reference data layer details`() {
        listOf(
            "com.reader.android.data",
            "Repository",
            "Parser",
            "Bridge",
            "HttpClient"
        ).forEach { forbidden ->
            assertTrue("Foundation must not depend on $forbidden", forbidden !in foundationSource)
        }
    }
}
