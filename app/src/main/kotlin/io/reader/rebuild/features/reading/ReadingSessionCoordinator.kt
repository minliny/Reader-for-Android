package io.reader.rebuild.features.reading

import com.reader.core.protocol.BookshelfGetParams
import com.reader.core.protocol.LocalBookChapterContentData
import com.reader.core.protocol.LocalBookChapterContentParams
import com.reader.core.protocol.LocalBookChapterPresentationData
import com.reader.core.protocol.LocalBookChapterPresentationParams
import com.reader.core.protocol.ReaderM1
import com.reader.core.protocol.ReadingProgressUpdateData
import com.reader.core.protocol.TextReadingProgressGetParams
import com.reader.core.protocol.TextReadingProgressUpdateParams
import io.reader.rebuild.core.CoreOperation
import io.reader.rebuild.core.CoreRuntimeActor
import io.reader.rebuild.core.PageObservation
import io.reader.rebuild.host.storage.AuthorizedArchive
import io.reader.rebuild.host.storage.FileAndAssetHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class TextChapter(
    val content: LocalBookChapterContentData,
    val revision: String,
    val savedScalar: Long,
) {
    val scalarCount get() = content.content.codePointCount(0, content.content.length).toLong()
}

internal data class EbookChapter(
    val text: TextChapter,
    val descriptor: LocalBookChapterPresentationData,
    val archive: AuthorizedArchive,
    val xhtml: String,
)

internal class ReadingSessionCoordinator(private val core: CoreRuntimeActor, private val files: FileAndAssetHost) {
    suspend fun text(bookId: String, chapter: Long): TextChapter {
        val body = core.call(ReaderM1.localBookChapterContent, LocalBookChapterContentParams(bookId = bookId, chapterIndex = chapter))
        check(body.bookId == bookId && body.chapterIndex == chapter && body.sourceId == "local")
        val state = core.call(ReaderM1.readingProgressGet, TextReadingProgressGetParams(bookId, body.sourceId))
        val current = state.progress?.takeIf {
            it.bookId == body.bookId && it.sourceId == body.sourceId && it.chapterIndex == body.chapterIndex &&
                it.bodyVersion == body.bodyVersion && it.processingVersion == body.processingVersion
        }
        return TextChapter(body, requireNotNull(state.progressRevision), current?.chapterOffset ?: 0L)
    }

    suspend fun ebook(bookId: String, chapter: Long): EbookChapter = withContext(Dispatchers.IO) {
        val body = text(bookId, chapter)
        val shelf = core.call(ReaderM1.bookshelfGet, BookshelfGetParams(body.content.sourceId, bookId))
        val archive = files.authorize(requireNotNull(shelf.original?.sha256))
        val entry = core.call(ReaderM1.localBookChapterPresentationV1, LocalBookChapterPresentationParams(
            bookId, chapter, archive.file.absolutePath,
        ))
        check(entry.sourceId == body.content.sourceId && entry.bookId == bookId && entry.chapterIndex == chapter &&
            entry.bodyVersion == body.content.bodyVersion && entry.processingVersion == body.content.processingVersion)
        EbookChapter(body, entry, archive, archive.read(entry.entryPath, 8L * 1024 * 1024).decodeToString())
    }

    fun save(chapter: TextChapter, scalar: Long, page: PageObservation<ReadingProgressUpdateData>? = null):
        CoreOperation<ReadingProgressUpdateData> {
        require(scalar in 0 until chapter.scalarCount)
        val body = chapter.content
        return core.submit(ReaderM1.readingProgressUpdate, TextReadingProgressUpdateParams(
            bookId = body.bookId, sourceId = body.sourceId, chapterIndex = body.chapterIndex,
            chapterOffset = scalar, chapterProgress = scalar.toDouble() / chapter.scalarCount,
            expectedBodyVersion = requireNotNull(body.bodyVersion),
            expectedProcessingVersion = requireNotNull(body.processingVersion), expectedProgressRevision = chapter.revision,
        ), page)
    }
}
