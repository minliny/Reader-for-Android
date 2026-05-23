package com.reader.android.ui.booksource

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class SourceManagementBoundaryGuardTest {

    private val stateSource: String by lazy {
        String(Files.readAllBytes(Paths.get("src/main/kotlin/com/reader/android/ui/booksource/SourceManagementUiState.kt")))
    }

    private val screenSource: String by lazy {
        listOf(
            "src/main/kotlin/com/reader/android/ui/booksource/BookSourceScreen.kt",
            "src/main/kotlin/com/reader/android/ui/booksource/SourceDetailScreen.kt",
            "src/main/kotlin/com/reader/android/ui/booksource/SourceEditScreen.kt",
            "src/main/kotlin/com/reader/android/ui/booksource/SourceImportScreen.kt"
        ).joinToString("\n") { path ->
            String(Files.readAllBytes(Paths.get(path)))
        }
    }

    @Test
    fun `source management mapper does not call parser bridge or network internals`() {
        val forbidden = listOf(
            "Parser",
            "Bridge",
            "Http" + "Client",
            ".execute(",
            ".get(",
            "Room",
            "DataStore"
        )

        forbidden.forEach { token ->
            assertTrue("Source state source must not contain $token", token !in stateSource)
        }
    }

    @Test
    fun `source management screens preserve repository boundary`() {
        listOf(
            "SourceManagementViewModel",
            "BookSourceRepository",
            "FakeBookSourceRepository",
            "SourceManagementMapper"
        ).forEach { token ->
            assertTrue("BookSourceScreen must preserve $token", token in screenSource)
        }
    }

    @Test
    fun `source management does not reintroduce legacy ui or normalized html runtime`() {
        val forbidden = listOf(
            "bg-" + "surface-container",
            "bg-" + "surface-container-high",
            "bg-" + "surface-container-highest",
            "text-" + "on-surface",
            "text-" + "on-surface-variant",
            "shadow-" + "lg",
            "shadow-" + "md",
            "#" + "fdf6ec",
            "#" + "eae1da",
            "#" + "f5ece6",
            "#" + "efe7e0",
            "#" + "8b5000",
            "Web" + "View",
            "normalized-" + "html"
        )

        forbidden.forEach { token ->
            assertTrue("Source management UI must not contain $token", token !in (screenSource + stateSource))
        }
    }
}
