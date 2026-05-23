package com.reader.android.ui.booksource

import com.reader.android.data.repository.FakeBookSourceRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceManagementDataIntegrationTest {

    @Test
    fun `fake repository list maps to ui state without changing repository`() {
        val repository = FakeBookSourceRepository()
        val imported = repository.importJson(
            """
            [
              {"sourceUrl":"https://fixture.local/a","sourceName":"源 A","sourceGroup":"中文","searchUrl":"/search?q=key","tocUrl":"/toc","contentUrl":"/content"},
              {"sourceUrl":"https://fixture.local/b","sourceName":"源 B","sourceGroup":"备用","enabled":false}
            ]
            """.trimIndent()
        )

        val state = SourceManagementMapper.fromSources(repository.getAll())

        assertEquals(2, imported)
        assertEquals(2, state.sources.size)
        assertEquals("源 A", state.sources.first().name)
        assertFalse(state.sources.last().enabled)
    }

    @Test
    fun `source toggle from existing repository can be reflected in ui state`() {
        val repository = FakeBookSourceRepository()
        repository.importJson("""[{"sourceUrl":"https://fixture.local/a","sourceName":"源 A","enabled":true}]""")

        repository.setEnabled("https://fixture.local/a", false)
        val state = SourceManagementMapper.fromSources(repository.getAll())

        assertEquals(SourceUiStatus.Disabled, state.sources.single().status)
    }

    @Test
    fun `fake fallback remains available`() {
        val fallback = SourceManagementMapper.fakeFallback()

        assertTrue(fallback.sources.isNotEmpty())
        assertEquals("fake repository fallback", fallback.fakeRealModeLabel)
    }

    @Test
    fun `import success state carries imported count`() {
        val state = SourceManagementMapper.importSuccess(2)

        assertEquals(SourceImportStatus.Imported, state.status)
        assertEquals(2, state.importedCount)
    }
}
