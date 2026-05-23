package com.reader.android.ui.fixtures

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class ReaderUiFixturesTest {

    private val fixtureSource: String by lazy {
        String(Files.readAllBytes(Paths.get("src/main/kotlin/com/reader/android/ui/fixtures/ReaderUiFixtures.kt")))
    }

    @Test
    fun `fixtures cover required fake data groups`() {
        assertTrue(ReaderUiFixtures.bookshelfBooks.isNotEmpty())
        assertTrue(ReaderUiFixtures.searchResults.isNotEmpty())
        assertTrue(ReaderUiFixtures.bookDetail.title.isNotBlank())
        assertTrue(ReaderUiFixtures.readerChapter.contentPreview.isNotBlank())
        assertTrue(ReaderUiFixtures.sources.isNotEmpty())
        assertTrue(ReaderUiFixtures.rssSources.isNotEmpty())
        assertFalse(ReaderUiFixtures.webDavStatus.configured)
        assertTrue(ReaderUiFixtures.globalErrors.isNotEmpty())
    }

    @Test
    fun `fixtures are marked as ui fixtures and do not contain real endpoints or secrets`() {
        listOf(
            "http://",
            "https://",
            "token",
            "password",
            "Authorization",
            "Cookie"
        ).forEach { forbidden ->
            assertTrue("Fixtures must not contain $forbidden", forbidden !in fixtureSource)
        }
        assertTrue("Fixture ids should be clearly marked", "fixture-" in fixtureSource)
    }

    @Test
    fun `fixtures do not access network storage bridge or repository`() {
        listOf(
            "com.reader.android.data",
            "Repository",
            "Parser",
            "Bridge",
            "HttpClient",
            "DataStore",
            "Room",
            "execute("
        ).forEach { forbidden ->
            assertTrue("Fixtures must not depend on $forbidden", forbidden !in fixtureSource)
        }
    }
}
