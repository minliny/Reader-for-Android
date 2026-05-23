package com.reader.android.ui.search

import com.reader.android.ui.adapter.BookDetailAdapter
import com.reader.android.ui.adapter.ReaderIntegrationLevel
import com.reader.android.ui.adapter.SearchAdapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class SearchDetailBoundaryPlanningTest {

    private val searchScreenSource: String by lazy {
        String(Files.readAllBytes(Paths.get("src/main/kotlin/com/reader/android/ui/search/SearchScreen.kt")))
    }

    private val detailScreenSource: String by lazy {
        String(Files.readAllBytes(Paths.get("src/main/kotlin/com/reader/android/ui/detail/BookDetailScreen.kt")))
    }

    private val uiStateSources: String by lazy {
        listOf(
            "src/main/kotlin/com/reader/android/ui/search/SearchUiState.kt",
            "src/main/kotlin/com/reader/android/ui/detail/BookDetailUiState.kt"
        ).joinToString("\n") { path ->
            String(Files.readAllBytes(Paths.get(path)))
        }
    }

    @Test
    fun `search and detail remain needs adapter before real integration`() {
        assertEquals(ReaderIntegrationLevel.NEEDS_ADAPTER, SearchAdapter.contract.level)
        assertEquals(ReaderIntegrationLevel.NEEDS_ADAPTER, BookDetailAdapter.contract.level)
        assertFalse(SearchAdapter.contract.allowRealDataIntegration)
        assertFalse(BookDetailAdapter.contract.allowRealDataIntegration)
    }

    @Test
    fun `current screens still expose fake real switch but slice 12 does not enable it`() {
        listOf(
            "SearchViewModel",
            "BookDetailViewModel",
            "useRealHttp",
            "Fake" + "CoreBridge",
            "Http" + "Client"
        ).forEach { token ->
            assertTrue("Current screen source should still expose $token", token in (searchScreenSource + detailScreenSource))
        }

        assertFalse(SearchUiStateMapper.fromFixture().allowRealDataIntegration)
        assertFalse(com.reader.android.ui.detail.BookDetailUiStateMapper.fromFixture().allowRealDataIntegration)
    }

    @Test
    fun `slice 12 state layer does not reference runtime data dependencies`() {
        val forbidden = listOf(
            "com.reader.android.data",
            "Repository",
            "Parser",
            "Bridge",
            "Http" + "Client",
            ".execute(",
            ".get(",
            "Room",
            "DataStore"
        )

        forbidden.forEach { token ->
            assertTrue("Slice 12 UI state must not contain $token", token !in uiStateSources)
        }
    }

    @Test
    fun `slice 12 state layer does not reintroduce legacy ui or normalized html runtime`() {
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
            assertTrue("Slice 12 UI state must not contain $token", token !in uiStateSources)
        }
    }
}
