package com.reader.android.ui.search

import com.reader.android.ui.adapter.ReaderIntegrationLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class SearchUiStateMappingTest {

    private val stateSource: String by lazy {
        String(Files.readAllBytes(Paths.get("src/main/kotlin/com/reader/android/ui/search/SearchUiState.kt")))
    }

    @Test
    fun `search fixture maps to explicit ui state`() {
        val state = SearchUiStateMapper.fromFixture()

        assertEquals(SearchFixture.sampleQuery, state.query)
        assertEquals(2, state.results.size)
        assertEquals(ReaderIntegrationLevel.NEEDS_ADAPTER, state.boundaryLevel)
        assertFalse(state.allowRealDataIntegration)
        assertFalse(state.isEmpty)
    }

    @Test
    fun `search loading empty and error states are expressible`() {
        val loading = SearchUiStateMapper.loading("山")
        val empty = SearchUiStateMapper.empty("无结果")
        val error = SearchUiStateMapper.error("山", "fixture error")

        assertTrue(loading.isLoading)
        assertTrue(empty.isEmpty)
        assertEquals("fixture error", error.errorMessage)
        assertEquals(ReaderIntegrationLevel.NEEDS_ADAPTER, error.boundaryLevel)
    }

    @Test
    fun `search result model carries ui required fields`() {
        val result = SearchFixture.results.first()

        assertTrue(result.id.isNotBlank())
        assertTrue(result.title.isNotBlank())
        assertTrue(result.author.isNotBlank())
        assertTrue(result.sourceName.isNotBlank())
        assertTrue(result.latestChapter.isNotBlank())
        assertTrue(result.detailTarget.isNotBlank())
    }

    @Test
    fun `search mapper remains ui only`() {
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
            assertTrue("Search state source must not contain $token", token !in stateSource)
        }
    }
}
