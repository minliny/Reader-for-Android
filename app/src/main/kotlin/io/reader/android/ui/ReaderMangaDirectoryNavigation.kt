package io.reader.android.ui

import io.reader.android.runtime.ReaderMangaChapterData
import io.reader.android.runtime.ReaderMangaChapterRequest

/** The Library resolves and validates issuance first. An index alone never identifies the displayed chapter. */
internal object ReaderMangaDirectoryNavigation {
    fun requireCurrent(request: ReaderMangaChapterRequest, chapter: ReaderMangaChapterData) {
        val target = checkNotNull(request.directoryTarget) { "MANGA_DIRECTORY_CURRENT_PROOF_REQUIRED" }
        val manifest = chapter.manifest
        check(request.chapter.sourceId == manifest.chapter.sourceId && request.chapter.bookId == manifest.chapter.bookId &&
            target.directory.chapter == request.chapter && request.chapterIndex == chapter.chapterIndex &&
            target.chapterIndex == chapter.chapterIndex && target.url == manifest.chapter.chapterId &&
            target.sourceRuleVersion == manifest.sourceRuleVersion && target.directory.sourceVersion == manifest.sourceRuleVersion) {
            "MANGA_DIRECTORY_CURRENT_CHANGED"
        }
    }
}
