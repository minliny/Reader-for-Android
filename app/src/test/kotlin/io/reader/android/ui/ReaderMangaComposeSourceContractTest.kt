package io.reader.android.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Source boundary checks for the first reachable Manga Compose route. */
class ReaderMangaComposeSourceContractTest {
    private fun source(path: String): String {
        val candidates = sequenceOf(File("src/main/kotlin/$path"), File("app/src/main/kotlin/$path"))
        return candidates.first { it.isFile }.readText()
    }

    @Test
    fun surfaceConsumesOwnerStateWithoutOwningCoreOrResourceStore() {
        val surface = source("io/reader/android/ui/ReaderMangaReadingScreen.kt")
        assertTrue("surface must render the owner page list", "state.pages" in surface)
        assertTrue("surface must expose the bounded zoom controls", "reader_manga_zoom_in" in surface)
        assertFalse("Compose must not call Core directly", "ReaderLibrary" in surface)
        assertFalse("Compose must not own the resource store", "ReaderMangaResourceStore" in surface)
    }

    @Test
    fun mangaRouteIsAdmittedOnlyThroughTheOwnerAndMangaDetailAction() {
        val app = source("io/reader/android/ui/ReaderApp.kt")
        val model = source("io/reader/android/ui/ReaderViewModel.kt")
        val workspace = source("io/reader/android/ui/ReaderOnlineWorkspaceScreen.kt")
        assertTrue("route must be owned by the app model", "ReaderRoute.MangaReading" in model && "ReaderRoute.MangaReading" in app)
        assertTrue("online manga detail must have an explicit action", "reader_online_manga_read" in workspace)
        assertTrue("the owner adapter must be the only Library bridge", "ReaderMangaLibraryAdapter" in model)
    }
}
