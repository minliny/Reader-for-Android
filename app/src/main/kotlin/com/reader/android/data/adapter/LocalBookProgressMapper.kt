package com.reader.android.data.adapter

import com.reader.android.data.storage.CachedChapter
import com.reader.android.data.storage.ReadingProgress

object LocalBookProgressMapper {

    fun toReadingProgress(
        source: LocalBookSource,
        metadata: LocalBookMetadata,
        currentChapter: LocalChapterRef? = null
    ): ReadingProgress {
        val ch = currentChapter ?: metadata.chapters.firstOrNull()
        return ReadingProgress(
            bookUrl = source.uri,
            bookName = metadata.title,
            author = metadata.author,
            currentChapterUrl = ch?.let { "${source.uri}#ch${it.index}" } ?: source.uri,
            currentChapterTitle = ch?.title ?: "",
            chapterIndex = ch?.index ?: 0,
            totalChapters = metadata.chapters.size
        )
    }

    fun toCachedChapter(source: LocalBookSource, chapter: LocalChapterRef, content: String): CachedChapter {
        return CachedChapter(
            contentUrl = "${source.uri}#ch${chapter.index}",
            content = content,
            title = chapter.title,
            nextPageUrl = null
        )
    }

    fun chapterUri(sourceUri: String, chapterIndex: Int): String {
        return "${sourceUri}#ch${chapterIndex}"
    }
}
