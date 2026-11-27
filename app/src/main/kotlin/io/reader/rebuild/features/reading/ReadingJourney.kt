package io.reader.rebuild.features.reading

import com.reader.core.protocol.BookmarkCreateParams
import com.reader.core.protocol.BookmarkData
import com.reader.core.protocol.BookmarkDeleteParams
import com.reader.core.protocol.BookmarkListParams
import com.reader.core.protocol.LocalBookPresentationKindDataKind
import com.reader.core.protocol.LocalBookTocParams
import com.reader.core.protocol.M2BookTocParams
import com.reader.core.protocol.M2ChangeBookSourceCandidate
import com.reader.core.protocol.M2ChangeBookSourceParams
import com.reader.core.protocol.M2SourceListParams
import com.reader.core.protocol.M2SourceSwitchBookKey
import com.reader.core.protocol.M2SourceSwitchChapterData
import com.reader.core.protocol.M2SourceSwitchCommitParams
import com.reader.core.protocol.M2SourceSwitchTargetBookData
import com.reader.core.protocol.M2SourceSwitchTransactionParams
import com.reader.core.protocol.M2SourceSwitchTransactionPhase
import com.reader.core.protocol.M2SourceSwitchTransactionQueryParams
import com.reader.core.protocol.ReaderM1
import com.reader.core.protocol.ReaderM2
import com.reader.core.protocol.RemoteContentPositionScope
import com.reader.core.protocol.TextReadingProgressGetParams
import io.reader.rebuild.core.CoreRuntimeActor
import io.reader.rebuild.core.readerResult
import io.reader.rebuild.features.library.BookReference
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class ReaderChapter(
    val book: BookReference,
    val entries: List<DirectoryEntry>,
    val local: TextChapter? = null,
    val ebook: EbookChapter? = null,
    val online: OnlineChapter? = null,
    val initialScalar: Long,
    val ebookFallback: Boolean = false,
    val ownerId: UUID = UUID.randomUUID(),
) {
    val text: String get() = local?.content?.content ?: checkNotNull(online).text
    val index: Long get() = local?.content?.chapterIndex ?: checkNotNull(online).entry.index
    val title: String get() = local?.content?.chapterTitle ?: checkNotNull(online).entry.title
    val scalarCount: Long get() = local?.scalarCount ?: checkNotNull(online).scalarCount
    val positionScope get() = RemoteContentPositionScope(book.sourceId, book.bookId, index,
        requireNotNull(local?.content?.bodyVersion ?: online?.body?.bodyVersion),
        requireNotNull(local?.content?.processingVersion ?: online?.body?.processingVersion),
    )
}

internal data class ReadingSwitchRecovery(
    val query: M2SourceSwitchTransactionQueryParams,
    val from: M2SourceSwitchBookKey,
    val target: M2SourceSwitchBookKey,
) {
    companion object {
        fun fromFailure(error: SwitchUnresolved): ReadingSwitchRecovery {
            val commit = requireNotNull(error.query.expected)
            return ReadingSwitchRecovery(error.query, commit.from,
                M2SourceSwitchBookKey(commit.target.sourceId, commit.target.bookId))
        }
    }
}

internal class ReadingJourney(
    private val core: CoreRuntimeActor,
    private val local: ReadingSessionCoordinator,
    private val online: OnlineReadingCoordinator,
    private val switching: SourceSwitchCoordinator,
) {
    private val gate = Mutex()
    private var current: ReaderChapter? = null
    private var lastSaved: Long? = null

    suspend fun open(book: BookReference, index: Long?, checkpoint: Long?, bookmark: BookmarkData? = null): ReaderChapter =
        gate.withLock {
            if (checkpoint != null) saveLocked(checkpoint)
            val progress = if (index == null) core.call(ReaderM1.readingProgressGet,
                TextReadingProgressGetParams(book.bookId, book.sourceId)).progress else null
            val requested = index ?: progress?.chapterIndex
            val opened = if (book.local) openLocal(book, requested) else openOnline(book, requested)
            val target = if (bookmark != null) {
                check(bookmark.positionScope == opened.positionScope && bookmark.chapterPos in 0 until opened.scalarCount) {
                    "书签对应的正文已变化"
                }
                opened.copy(initialScalar = bookmark.chapterPos)
            } else opened
            lastSaved = null
            target.also { current = it }
        }

    private suspend fun openLocal(book: BookReference, requested: Long?): ReaderChapter {
        val params = LocalBookTocParams(book.bookId)
        val toc = core.call(ReaderM1.localBookToc, params)
        check(toc.bookId == book.bookId && toc.sourceId == book.sourceId)
        val entries = toc.toc.filter { it.url.isNotBlank() || it.title.isNotBlank() }
            .map { DirectoryEntry(it.index, it.displayTitle ?: it.title) }
        check(entries.isNotEmpty()) { "没有可打开的章节" }
        val index = requested ?: entries.first().index
        check(entries.any { it.index == index }) { "该章节已不可用" }
        val kind = core.call(ReaderM1.localBookPresentationKindV1, params)
        val ebook = if (kind.kind == LocalBookPresentationKindDataKind.EBOOK)
            readerResult { local.ebook(book.bookId, index) }.getOrNull() else null
        val body = ebook?.text ?: local.text(book.bookId, index)
        check(body.scalarCount > 0) { "该章节没有可校验的正文" }
        return ReaderChapter(book, entries, local = body, ebook = ebook, initialScalar = body.savedScalar,
            ebookFallback = kind.kind == LocalBookPresentationKindDataKind.EBOOK && ebook == null)
    }

    private suspend fun openOnline(book: BookReference, requested: Long?): ReaderChapter {
        val catalog = directory(book, refresh = false)
        val chapter = online.open(catalog, requested ?: catalog.entries.first().index)
        return project(book, chapter)
    }

    private suspend fun directory(book: BookReference, refresh: Boolean): OnlineCatalog {
        if (!refresh) online.cachedDirectory(OnlineBookKey(book.sourceId, book.bookId))?.let { return it }
        return online.refreshDirectory(M2BookTocParams(sourceId = book.sourceId, bookId = book.bookId,
            tocUrl = book.tocUrl, variables = book.variables))
    }

    suspend fun refreshDirectory(book: BookReference): List<DirectoryEntry> = gate.withLock {
        val previous = current?.online
        val refreshed = directory(book, refresh = true)
        if (previous != null && refreshed.entries.any { it.index == previous.entry.index && it.url == previous.entry.url }) {
            current = current?.copy(entries = entries(refreshed), online = previous.copy(catalog = refreshed))
        }
        entries(refreshed)
    }

    suspend fun refreshContent(scalar: Long): ReaderChapter = gate.withLock {
        val before = requireNotNull(current)
        val chapter = online.refresh(requireNotNull(before.online), scalar)
        lastSaved = null
        project(before.book, chapter).also { current = it }
    }

    suspend fun save(owner: ReaderChapter, scalar: Long) = gate.withLock {
        if (current?.ownerId == owner.ownerId) saveLocked(scalar)
    }

    private suspend fun saveLocked(scalar: Long) {
        val previous = current ?: return
        if (scalar !in 0 until previous.scalarCount || lastSaved == scalar) return
        val operation = if (previous.local != null) local.save(previous.local, scalar)
        else online.save(requireNotNull(previous.online), scalar)
        val receipt = operation.await()
        check(receipt.stored && receipt.chapterIndex == previous.index && receipt.chapterOffset == scalar) {
            "未能确认保存原位置"
        }
        val revision = requireNotNull(receipt.progressRevision)
        current = previous.copy(local = previous.local?.copy(revision = revision),
            online = previous.online?.copy(progressRevision = revision))
        lastSaved = scalar
    }

    suspend fun candidates(book: BookReference, query: String): List<M2ChangeBookSourceCandidate> {
        val available = core.call(ReaderM2.sourceList, M2SourceListParams(enabledOnly = true)).sources
            .map { it.sourceId }.filter { it != book.sourceId }
        if (available.isEmpty()) return emptyList()
        return switching.candidates(M2ChangeBookSourceParams(book.sourceId, book.bookId, query, available)).candidates
    }

    suspend fun switch(candidate: M2ChangeBookSourceCandidate, scalar: Long): ReaderChapter = gate.withLock {
        val before = requireNotNull(current)
        val currentOnline = requireNotNull(before.online)
        val target = BookReference(candidate.sourceId, candidate.bookUrl, candidate.bookName, candidate.author, candidate.coverUrl)
        val detail = online.detail(target.detailParams)
        val book = target.fromDetail(detail)
        val catalog = directory(book, refresh = false)
        val draft = M2SourceSwitchCommitParams(from = M2SourceSwitchBookKey(before.book.sourceId, before.book.bookId),
            target = M2SourceSwitchTargetBookData(book.sourceId, book.bookId, book.title, book.author, book.coverUrl,
                book.intro, book.kind, book.lastChapter),
            newToc = catalog.entries.map { M2SourceSwitchChapterData(it.url, it.title, it.url, it.index) },
            currentChapterTitle = before.title, currentChapterIndex = before.index, updatedAt = System.currentTimeMillis(),
        )
        val result = try {
            switching.switch(currentOnline, draft, scalar)
        } catch (failure: SwitchUnresolved) {
            throw failure
        } catch (failure: Exception) {
            // The source write may be confirmed even when choosing the target fails.
            // Refresh only the revision of the unchanged retained source projection.
            readerResult { online.open(currentOnline.catalog, currentOnline.entry.index, cacheOnly = true) }
                .getOrNull()?.takeIf {
                    it.body.bodyVersion == currentOnline.body.bodyVersion &&
                        it.body.processingVersion == currentOnline.body.processingVersion
                }?.let {
                    current = before.copy(online = currentOnline.copy(progressRevision = it.progressRevision))
                    lastSaved = it.scalar
                }
            throw failure
        }
        lastSaved = result.scalar
        project(book, result).also { current = it }
    }

    suspend fun pendingSwitch(book: BookReference): ReadingSwitchRecovery? {
        if (book.local) return null
        val pending = switching.pending().pending.firstOrNull { entry ->
            listOf(entry.from, entry.target).any { it.sourceId == book.sourceId && it.bookId == book.bookId }
        } ?: return null
        return ReadingSwitchRecovery(M2SourceSwitchTransactionQueryParams(pending.transactionId), pending.from, pending.target)
    }

    suspend fun recover(original: ReadingSwitchRecovery, rollback: Boolean): ReaderChapter? = gate.withLock {
        suspend fun receipt(query: M2SourceSwitchTransactionQueryParams) = switching.query(query).transaction?.also {
            check(it.operationId == original.query.operationId &&
                it.from.sourceId == original.from.sourceId && it.from.bookId == original.from.bookId &&
                it.target.sourceId == original.target.sourceId && it.target.bookId == original.target.bookId)
        }
        var observed = receipt(original.query) ?: return@withLock null
        if (rollback && observed.phase == M2SourceSwitchTransactionPhase.PENDING) {
            val result = core.call(ReaderM2.sourceSwitchRollback, M2SourceSwitchTransactionParams(original.query.operationId))
            check(result.transactionId == original.query.operationId)
            observed = requireNotNull(receipt(original.query.copy(expectedCanonical = null)))
            check(observed.phase == M2SourceSwitchTransactionPhase.ROLLED_BACK)
        }
        val key = when (observed.phase) {
            M2SourceSwitchTransactionPhase.FINALIZED -> observed.target
            M2SourceSwitchTransactionPhase.ROLLED_BACK -> observed.from
            else -> return@withLock null
        }
        val membership = core.call(ReaderM1.bookshelfGet, com.reader.core.protocol.BookshelfGetParams(key.sourceId, key.bookId))
        val shelfBook = requireNotNull(membership.book)
        val exact = BookReference(shelfBook)
        val restored = openOnline(exact, observed.canonicalProgress?.chapterIndex ?: shelfBook.readingPosition?.chapterIndex)
        lastSaved = restored.initialScalar
        restored.also { current = it }
    }

    suspend fun bookmarks(book: BookReference): List<BookmarkData> =
        core.call(ReaderM1.bookmarkList, BookmarkListParams(bookName = book.title, bookAuthor = book.author.orEmpty())).bookmarks.filter {
            it.positionScope?.let { scope -> scope.bookId == book.bookId && scope.sourceId == book.sourceId } == true
        }

    suspend fun bookmark(chapter: ReaderChapter, scalar: Long) {
        val map = TextScalarMap(chapter.text)
        val snippet = map.substring(scalar, (scalar + 36).coerceAtMost(map.chapterEnd)).orEmpty()
        val stored = core.call(ReaderM1.bookmarkCreate, BookmarkCreateParams(
            bookName = chapter.book.title, bookAuthor = chapter.book.author.orEmpty(),
            chapterIndex = chapter.index, chapterPos = scalar, chapterName = chapter.title, bookText = snippet,
            content = snippet, positionScope = chapter.positionScope,
        )).bookmark
        check(stored.positionScope == chapter.positionScope && stored.chapterPos == scalar)
    }

    suspend fun deleteBookmark(bookmark: BookmarkData) {
        val receipt = core.call(ReaderM1.bookmarkDelete, BookmarkDeleteParams(bookmark.time))
        check(receipt.deleted && receipt.time == bookmark.time)
    }

    private fun project(book: BookReference, chapter: OnlineChapter) =
        ReaderChapter(book, entries(chapter.catalog), online = chapter, initialScalar = chapter.scalar)

    private fun entries(catalog: OnlineCatalog) = catalog.entries.map { DirectoryEntry(it.index, it.displayTitle ?: it.title) }
}
