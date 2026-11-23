package io.reader.android.ui

import io.reader.android.runtime.*
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class ReaderMangaDirectoryNavigationTest {
    @Test fun matchingIssuedCurrentTargetAllowsSparseChapterIndex() {
        val f = Fixture()
        ReaderMangaDirectoryNavigation.requireCurrent(f.request, f.chapter)
    }

    @Test fun sameIndexDifferentUrlOrMissingProofIsRejected() {
        val f = Fixture()
        val reusedIndex = f.request.copy(directoryTarget = f.target(url = "replacement-at-same-index"))
        reject(reusedIndex, f.chapter)
        reject(f.request.copy(directoryTarget = null), f.chapter)
    }

    @Test fun foreignBookSourceRulesOrCanonicalIndexIsRejected() {
        val f = Fixture()
        val foreignRequests = listOf(
            f.request.copy(chapter = f.ref.copy(sourceId = "other-source")),
            f.request.copy(chapter = f.ref.copy(bookId = "other-book")),
            f.request.copy(chapterIndex = 18),
            f.request.copy(directoryTarget = f.target(index = 18)),
            f.request.copy(directoryTarget = f.target(rules = "other-rules")),
            f.request.copy(directoryTarget = f.target(directory = f.directory(ref = f.ref.copy(bookId = "other-book")))),
            f.request.copy(directoryTarget = f.target(directory = f.directory(rules = "other-rules"))),
        )
        foreignRequests.forEach { reject(it, f.chapter) }
        reject(f.request, f.chapter.copy(manifest = f.chapter.manifest.copy(sourceRuleVersion = "new-current-rules")))
    }

    private fun reject(request: ReaderMangaChapterRequest, chapter: ReaderMangaChapterData) {
        assertTrue(runCatching { ReaderMangaDirectoryNavigation.requireCurrent(request, chapter) }.exceptionOrNull() is IllegalStateException)
    }

    private class Fixture {
        val ref = ReaderMangaChapterRef("source", "book")
        private val lease = ReaderOperationOwner.ClientLease(UUID.randomUUID(), UUID.randomUUID(), 1, 1)
        fun directory(ref: ReaderMangaChapterRef = this.ref, rules: String = "rules") =
            ReaderMangaDirectory("directory", ref, rules, "view", "catalog", "structure", Any(), lease, "runtime")
        private val issuedDirectory = directory()
        fun target(directory: ReaderMangaDirectory = issuedDirectory, index: Int = 17, url: String = "current-url", rules: String = "rules") =
            ReaderMangaDirectoryTarget(directory, "current", index, url, rules, "{}")
        val request = ReaderMangaChapterRequest(ref, 17, directoryTarget = target())
        val chapter = ReaderMangaChapterData(ReaderMangaManifest(ReaderMangaChapterIdentity(ref.sourceId, ref.bookId, "current-url"),
            "rules", "manifest", "decode", listOf(ReaderMangaManifestPage("page", 0, "resource"))), 17, "Current", true, emptyList())
    }
}
