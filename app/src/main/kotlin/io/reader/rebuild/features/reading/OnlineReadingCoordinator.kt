package io.reader.rebuild.features.reading

import com.reader.core.protocol.BookshelfAddParams
import com.reader.core.protocol.M2BookDetailData
import com.reader.core.protocol.M2BookDetailDataContentKind
import com.reader.core.protocol.M2BookDetailParams
import com.reader.core.protocol.M2BookTocEntryData
import com.reader.core.protocol.M2BookTocParams
import com.reader.core.protocol.M2CacheBookStatusParams
import com.reader.core.protocol.M2ChapterContentData
import com.reader.core.protocol.M2ChapterContentDataVia
import com.reader.core.protocol.M2ChapterContentParams
import com.reader.core.protocol.M2DirectoryTargetResolveParamsV2
import com.reader.core.protocol.M2DirectoryViewOpenParamsV2
import com.reader.core.protocol.M2DirectoryViewPageParamsV2
import com.reader.core.protocol.M2RemoteContentPositionAnchor
import com.reader.core.protocol.M2RemoteContentPositionContext
import com.reader.core.protocol.M2RemoteContentPositionMigrationDataStatus
import com.reader.core.protocol.ReaderM1
import com.reader.core.protocol.ReaderM2
import com.reader.core.protocol.ReadingProgressUpdateData
import com.reader.core.protocol.TextReadingProgressGetParams
import com.reader.core.protocol.TextReadingProgressUpdateParams
import io.reader.rebuild.core.CoreRuntimeActor
import io.reader.rebuild.core.PageObservation
import kotlinx.serialization.json.JsonPrimitive

internal data class OnlineBookKey(val sourceId: String, val bookId: String)
internal data class OnlineCatalog(val book: OnlineBookKey, val revision: String, val entries: List<M2BookTocEntryData>)
internal data class OnlineChapter(
    val catalog: OnlineCatalog,
    val entry: M2BookTocEntryData,
    val body: M2ChapterContentData,
    val progressRevision: String,
    val scalar: Long,
) {
    val text: String get() = body.requireOnlineText()
    val scalarCount: Long get() = text.let { it.codePointCount(0, it.length).toLong() }
}

internal class OnlineReadingCoordinator(internal val core: CoreRuntimeActor) {
    suspend fun detail(params: M2BookDetailParams) = core.call(ReaderM2.bookDetail, params).also {
        check(it.sourceId == params.sourceId && it.book.bookId == params.book.bookId &&
            it.contentKind != M2BookDetailDataContentKind.MANGA)
    }

    suspend fun addToShelf(detail: M2BookDetailData) = core.call(ReaderM1.bookshelfAdd,
        BookshelfAddParams(detail.sourceId, detail.book.bookId, detail.book.title, detail.book.author.orEmpty(),
            detail.book.coverUrl, detail.book.intro, detail.book.kind, detail.book.lastChapter)).also {
        check(it.sourceId == detail.sourceId && it.bookId == detail.book.bookId)
    }

    suspend fun cachedDirectory(book: OnlineBookKey): OnlineCatalog? {
        val status = core.call(ReaderM2.cacheBookStatus,
            M2CacheBookStatusParams(book.sourceId, book.bookId, includeGlobalStats = false, includeChapterStates = true))
        check(status.sourceId == book.sourceId && status.bookId == book.bookId)
        val revision = status.catalogVersion?.takeIf { status.tocAvailable && it.isNotBlank() } ?: return null
        val entries = status.chapters.filter { it.navigable != false && it.url.isNotEmpty() }.map {
            M2BookTocEntryData(it.displayTitle, it.contextTitle, it.isVip, it.isPay, it.chapterIndex, it.title, it.url, it.level)
        }
        return OnlineCatalog(book, revision, entries).takeIf { entries.isNotEmpty() }
    }

    suspend fun refreshDirectory(params: M2BookTocParams): OnlineCatalog {
        val response = core.call(ReaderM2.bookToc, params)
        val book = OnlineBookKey(params.sourceId, params.bookId)
        check(response.sourceId == book.sourceId && response.bookId == book.bookId && response.catalogInstalled != false)
        val installed = checkNotNull(cachedDirectory(book)) { "Core did not install the refreshed directory" }
        check(response.catalogVersion == installed.revision)
        val byIndex = response.toc.associateBy { it.index }
        check(byIndex.size == response.toc.size)
        val entries = installed.entries.map { prior ->
            checkNotNull(byIndex[prior.index]?.takeIf { it.url == prior.url }) { "Directory changed during refresh" }
        }
        return installed.copy(entries = entries)
    }

    suspend fun openDirectory(params: M2DirectoryViewOpenParamsV2) = core.call(ReaderM2.readingDirectoryViewOpenV2, params)
    suspend fun directoryPage(params: M2DirectoryViewPageParamsV2) = core.call(ReaderM2.readingDirectoryViewPageV2, params)
    suspend fun resolveTarget(params: M2DirectoryTargetResolveParamsV2) =
        core.call(ReaderM2.readingDirectoryTargetResolveV2, params).also {
            check(it.sourceId == params.sourceId && it.bookId == params.bookId && it.viewId == params.viewId &&
                it.nodeId == params.nodeId && it.catalogRevision == params.catalogRevision &&
                it.structureRevision == params.structureRevision)
        }

    suspend fun chapter(params: M2ChapterContentParams): M2ChapterContentData =
        core.call(ReaderM2.chapterContent, params).also {
            check(it.sourceId == params.sourceId && it.bookId == params.bookId)
            check(params.cacheOnly != true || it.via == M2ChapterContentDataVia.CACHE)
        }

    suspend fun open(catalog: OnlineCatalog, index: Long, cacheOnly: Boolean = false): OnlineChapter {
        val entry = catalog.entries.single { it.index == index }
        check(cachedDirectory(catalog.book)?.revision == catalog.revision) { "Directory revision changed" }
        val body = chapter(request(catalog.book, entry).copy(cacheOnly = cacheOnly))
        val progress = core.call(ReaderM1.readingProgressGet, progressFor(catalog.book))
        val text = body.requireOnlineText()
        val stored = progress.progress?.takeIf {
            it.sourceId == catalog.book.sourceId && it.bookId == catalog.book.bookId && it.chapterIndex == index &&
                it.bodyVersion == body.bodyVersion && it.processingVersion == body.processingVersion &&
                it.chapterOffset in 0 until text.codePointCount(0, text.length).toLong()
        }
        return OnlineChapter(catalog, entry, body, requireNotNull(progress.progressRevision), stored?.chapterOffset ?: 0L)
    }

    suspend fun refresh(current: OnlineChapter, scalar: Long = current.scalar): OnlineChapter {
        require(scalar in 0 until current.scalarCount)
        check(cachedDirectory(current.catalog.book)?.revision == current.catalog.revision) { "Directory revision changed" }
        val oldBody = requireNotNull(current.body.bodyVersion)
        val oldProcessing = requireNotNull(current.body.processingVersion)
        val body = chapter(request(current.catalog.book, current.entry).copy(forceRefresh = true,
            positionContext = M2RemoteContentPositionContext(oldBody, oldProcessing,
                listOf(M2RemoteContentPositionAnchor("visible", scalar)))))
        val mapped = if (body.bodyVersion == oldBody && body.processingVersion == oldProcessing) scalar else {
            val migration = checkNotNull(body.positionMigration)
            check(migration.status == M2RemoteContentPositionMigrationDataStatus.COMMITTED &&
                migration.previousBodyVersion == oldBody && migration.previousProcessingVersion == oldProcessing &&
                migration.bodyVersion == body.bodyVersion && migration.processingVersion == body.processingVersion)
            migration.anchors.single { it.id == "visible" && it.previousOffset == scalar }.offset
        }
        val text = body.requireOnlineText()
        check(mapped in 0 until text.codePointCount(0, text.length).toLong())
        val readback = core.call(ReaderM1.readingProgressGet, progressFor(current.catalog.book))
        return current.copy(body = body, progressRevision = requireNotNull(readback.progressRevision), scalar = mapped)
    }

    fun save(current: OnlineChapter, scalar: Long, page: PageObservation<ReadingProgressUpdateData>? = null) =
        core.submit(ReaderM1.readingProgressUpdate, progressUpdate(current, scalar), page)

    internal fun progressUpdate(current: OnlineChapter, scalar: Long, transaction: String? = null, updatedAt: Long? = null):
        TextReadingProgressUpdateParams {
        require(scalar in 0 until current.scalarCount)
        return TextReadingProgressUpdateParams(expectedProgressRevision = current.progressRevision,
            bookId = current.catalog.book.bookId, sourceId = current.catalog.book.sourceId, updatedAt = updatedAt,
            chapterIndex = current.entry.index, chapterOffset = scalar, chapterProgress = scalar.toDouble() / current.scalarCount,
            chapterTitle = current.body.chapterTitle, transactionId = transaction,
            expectedBodyVersion = requireNotNull(current.body.bodyVersion),
            expectedProcessingVersion = requireNotNull(current.body.processingVersion))
    }

    private fun request(book: OnlineBookKey, entry: M2BookTocEntryData) = M2ChapterContentParams(
        sourceId = book.sourceId, bookId = book.bookId, chapterTitle = entry.title, chapterIndex = entry.index,
        chapterUrl = entry.url, variables = entry.variables)

    private fun progressFor(book: OnlineBookKey) = TextReadingProgressGetParams(book.bookId, book.sourceId)
}

internal fun M2ChapterContentData.requireOnlineText(): String = (content as? JsonPrimitive)
    ?.takeIf { it.isString }?.content?.takeIf(String::isNotEmpty)
    ?: error("Core did not admit textual content for this chapter")
