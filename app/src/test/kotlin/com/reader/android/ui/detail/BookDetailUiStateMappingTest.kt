package com.reader.android.ui.detail

import com.reader.android.ui.adapter.ReaderIntegrationLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class BookDetailUiStateMappingTest {

    private val stateSource: String by lazy {
        String(Files.readAllBytes(Paths.get("src/main/kotlin/com/reader/android/ui/detail/BookDetailUiState.kt")))
    }

    @Test
    fun `book detail fixture maps to explicit ui state`() {
        val state = BookDetailUiStateMapper.fromFixture()

        assertTrue(state.hasContent)
        assertEquals("纸上群山", state.detail!!.title)
        assertEquals(ReaderIntegrationLevel.NEEDS_ADAPTER, state.boundaryLevel)
        assertFalse(state.allowRealDataIntegration)
    }

    @Test
    fun `toc preview is represented in detail state`() {
        val preview = BookDetailUiStateMapper.fromFixture().detail!!.tocPreview

        assertEquals(12, preview.chapterCount)
        assertTrue(preview.firstChapterTitle.isNotBlank())
        assertTrue(preview.latestChapterTitle.isNotBlank())
        assertTrue(preview.tocTarget.isNotBlank())
    }

    @Test
    fun `detail loading empty and error states are expressible`() {
        val loading = BookDetailUiStateMapper.loading()
        val empty = BookDetailUiStateMapper.empty()
        val error = BookDetailUiStateMapper.error("fixture error")

        assertTrue(loading.isLoading)
        assertFalse(empty.hasContent)
        assertEquals("fixture error", error.errorMessage)
        assertEquals(ReaderIntegrationLevel.NEEDS_ADAPTER, error.boundaryLevel)
    }

    @Test
    fun `detail mapper remains ui only`() {
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
            assertTrue("Detail state source must not contain $token", token !in stateSource)
        }
    }
}
