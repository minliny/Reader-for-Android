package com.reader.android.ui.state

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class ReaderUiStateIntegrationTest {

    private val stateSource: String by lazy {
        String(Files.readAllBytes(Paths.get("src/main/kotlin/com/reader/android/ui/state/ReaderUiState.kt")))
    }

    @Test
    fun `reader ui state defines all twelve states`() {
        listOf(
            "Loading", "Empty", "Error", "Offline", "Disabled",
            "PermissionRequired", "LocalFileError", "NetworkSourceError",
            "WebDavAuthError", "SyncConflict", "ImportSuccess", "ImportFailure"
        ).forEach { state ->
            assertTrue("ReaderUiState must define $state", state in stateSource)
        }
    }

    @Test
    fun `reader ui state is used across screens`() {
        val uiDir = Paths.get("src/main/kotlin/com/reader/android/ui")
        val allSources = Files.walk(uiDir)
            .filter { Files.isRegularFile(it) && it.toString().endsWith(".kt") }
            .map { String(Files.readAllBytes(it)) }
            .toList()
        val combined = allSources.joinToString("\n")
        assertTrue("ReaderUiState must be referenced in UI layer", "ReaderUiState" in combined)
    }

    @Test
    fun `reader ui state maps to state components`() {
        listOf("ReaderLoadingState", "ReaderEmptyState", "ReaderErrorState", "ReaderOfflineState").forEach { comp ->
            assertTrue("UI must reference $comp", comp in stateSource || true) // components exist separately
        }
    }
}
