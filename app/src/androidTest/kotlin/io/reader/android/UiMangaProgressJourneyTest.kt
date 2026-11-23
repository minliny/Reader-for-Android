package io.reader.android

import android.content.Context
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.reader.android.runtime.ReaderLibrary
import io.reader.android.runtime.ReaderMangaChapterRef
import io.reader.android.runtime.ReaderMangaLocation
import io.reader.android.runtime.ReaderMangaProgressPolicy
import io.reader.android.runtime.ReaderMangaProgressSaveRequest
import io.reader.android.runtime.SourceImportConfirmation
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Real Core manga CAS/ACK and Android journal; no UI anchor or process-death claim. */
@RunWith(AndroidJUnit4::class)
class UiMangaProgressJourneyTest {
    @Test(timeout = 120_000)
    fun originalMangaProgressAcknowledgementSurvivesLibraryReplacement(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sourceId = "android-manga-progress-${UUID.randomUUID()}"
        val fixture = UiMangaComposeJourneyTest.Fixture()
        val library = ReaderLibrary(context)
        try {
            val imported = library.importSourceJson(sourceId, fixture.sourceJson())
            assertTrue(imported.durable)
            assertEquals(SourceImportConfirmation.APPLIED, imported.confirmation)
            val selected = library.selectOnlineSource(sourceId)
            val search = library.beginOnlineSearch(selected, "Comic")
            val book = library.onlineSearchPage(search, 1).books.single()
            val detail = library.previewOnlineBook(search, book)
            val chapterRequest = library.prepareMangaChapter(detail)
            library.closeOnlineSearch(search)
            val chapter = library.mangaChapter(chapterRequest)
            val owner = ReaderMangaChapterRef(sourceId, book.key.bookId)
            val before = library.mangaProgress(owner)
            assertNull(before.location)
            assertEquals(0L, before.token.revision)
            val page = chapter.manifest.pages[1]
            val location = ReaderMangaLocation(chapter.manifest.chapter, chapter.manifest.manifestVersion,
                page.pageId, page.ordinal, 0.0, 0.4, before.token.revision + 1)
            val saved = library.saveMangaProgress(ReaderMangaProgressSaveRequest(chapter.manifest, location, before.token))
            assertEquals(location, saved.location)
            assertEquals(before.token.epoch, saved.token.epoch)
            assertEquals(before.token.revision + 1, saved.token.revision)
            assertTrue(library.pendingMangaProgress().isEmpty())

            val completed = File(context.filesDir, "Reader/Operations/Completed").listFiles().orEmpty()
                .filter { it.name.startsWith("android-manga-progress-") && it.extension == "json" }
                .map { JSONObject(it.readText()) }
                .single { it.getString("sourceId") == sourceId }
            assertTrue(completed.getBoolean("completed"))
            assertTrue(completed.getBoolean("transportConfirmed"))
            assertEquals(saved, ReaderMangaProgressPolicy.acknowledgement(completed, completed.getJSONObject("wireTerminal")))
            assertEquals(saved, library.mangaProgress(owner))
            marker("manga-progress-original-ACK", Bundle().apply {
                putString("operationId", completed.getString("id"))
                putString("sourceId", sourceId)
                putLong("epoch", saved.token.epoch)
                putLong("revision", saved.token.revision)
                putString("pageId", page.pageId)
                putDouble("y", location.y)
            })
            library.close()
            ReaderLibrary(context).use { replacement ->
                assertEquals(saved, replacement.mangaProgress(owner))
                assertTrue(replacement.pendingMangaProgress().isEmpty())
                val source = replacement.sources(sourceId).single()
                val deleted = replacement.deleteSource(sourceId, source.sourceVersion)
                assertTrue(deleted.durable)
                assertEquals(SourceImportConfirmation.APPLIED, deleted.confirmation)
                marker("manga-progress-new-client-read-and-source-cleanup-ACK")
            }
        } finally {
            library.close()
            fixture.close()
        }
    }

    private fun marker(step: String, extras: Bundle = Bundle()) {
        extras.putString("readerStep", step)
        extras.putString("readerMethod", "originalMangaProgressAcknowledgementSurvivesLibraryReplacement")
        InstrumentationRegistry.getInstrumentation().sendStatus(2, extras)
    }
}
