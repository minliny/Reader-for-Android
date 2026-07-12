package com.reader.ui.shell

import com.reader.api.Book
import com.reader.api.BookApi
import com.reader.api.BookDetailResult
import com.reader.api.BookTocResult
import com.reader.api.Chapter
import com.reader.api.ReaderCoreClient
import io.reader.ui.runtime.ReaderUIBookOpenLayout
import io.reader.ui.runtime.ReaderUIEffect
import io.reader.ui.runtime.ReaderUIEffectKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/** Real renderer metrics required before `reader.location.resolve` may run. */
internal data class ReaderBookOpenViewport(
    val correlationId: String,
    val viewportWidth: Int,
    val viewportHeight: Int,
    val fontScale: Double,
    /** Incremented when a new content subtree is installed in the renderer. */
    val contentGeneration: Long = 0L,
    val chapterOffset: Long = 0L,
    val chapterProgress: Double = 0.0
) {
    fun toRuntimeLayout(): ReaderUIBookOpenLayout = ReaderUIBookOpenLayout(
        chapterOffset = chapterOffset.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
        chapterProgress = chapterProgress,
        viewportWidth = viewportWidth,
        viewportHeight = viewportHeight,
        fontScale = fontScale
    )
}

/**
 * Typed, Core-owned reader anchor returned by `reader.location.resolve`.
 * This is not a renderer measurement: it is retained so progress/sync code
 * can later persist the canonical revision without reverse engineering JSON.
 */
internal data class ReaderBookOpenCanonicalLocation(
    val bookId: String,
    val chapterIndex: Int,
    val chapterOffset: Long,
    val chapterProgress: Double,
    val locationRevision: String,
    val resolverVersion: String,
    val reflowStrategy: String,
    val reflowPrimaryAnchor: String,
    val reflowFallbackAnchor: String,
    val reflowLayoutIndependent: Boolean
)

/**
 * Correlation-scoped domain data for the one active `book.open` transaction.
 * It deliberately contains no Compose or Core runtime dependency, so stale
 * callbacks can be rejected before they alter a rendered reader surface.
 */
internal data class ReaderBookOpenDomainState(
    val activeCorrelationId: String? = null,
    val displayedCorrelationId: String? = null,
    val sourceId: String = "",
    val bookId: String = "",
    val bookName: String = "",
    val book: Book? = null,
    val chapters: List<Chapter> = emptyList(),
    /**
     * Position in the returned TOC, not the Core chapter entry's `index`.
     * ReaderUIRuntime clamps `requestedChapterIndex` against `chapterCount`, so
     * it deliberately speaks in list positions. Core chapter entries may use
     * non-zero-based or sparse indices; those are recovered from the selected
     * entry when building content/location commands.
     */
    val selectedChapterPosition: Int = 0,
    val content: String = "",
    /** `content` may legitimately be empty, so completion cannot use isNotBlank(). */
    val contentLoaded: Boolean = false,
    /** Reject a layout callback belonging to a previous rendered content subtree. */
    val contentGeneration: Long = 0L,
    val stage: String? = null,
    val loading: Boolean = false,
    val awaitingViewport: Boolean = false,
    val viewportSubmitted: Boolean = false,
    val error: String? = null,
    val locationResolved: Boolean = false,
    val canonicalLocation: ReaderBookOpenCanonicalLocation? = null
)

internal class ReaderBookOpenDomainStore {
    private val mutableState = MutableStateFlow(ReaderBookOpenDomainState())
    val state: StateFlow<ReaderBookOpenDomainState> = mutableState.asStateFlow()

    @Synchronized
    fun begin(context: ReaderContext) {
        val baseBook = Book(
            bookUrl = context.bookUrl,
            name = context.bookName,
            origin = context.sourceId,
            sourceId = context.sourceId
        )
        mutableState.value = ReaderBookOpenDomainState(
            activeCorrelationId = context.entryRequestId,
            displayedCorrelationId = context.entryRequestId,
            sourceId = context.sourceId,
            bookId = context.bookUrl,
            bookName = context.bookName,
            book = baseBook,
            stage = "started",
            loading = true
        )
    }

    @Synchronized
    fun isCurrent(correlationId: String): Boolean =
        mutableState.value.activeCorrelationId == correlationId

    @Synchronized
    fun current(correlationId: String): ReaderBookOpenDomainState? =
        mutableState.value.takeIf { it.activeCorrelationId == correlationId }

    @Synchronized
    fun recordDetail(correlationId: String, detail: BookDetailResult): Boolean = updateCurrent(correlationId) {
        it.copy(book = detail.book, stage = "source.detail")
    }

    @Synchronized
    fun recordToc(correlationId: String, toc: BookTocResult): Boolean = updateCurrent(correlationId) {
        it.copy(chapters = toc.chapters, stage = "chapter.list")
    }

    @Synchronized
    fun selectChapter(correlationId: String, chapterPosition: Int): Boolean = updateCurrent(correlationId) { state ->
        // `chapterIndex` in the runtime effect is a clamped TOC position. Do
        // not reinterpret it as `Chapter.index`: e.g. [1, 2, 3] + position 1
        // must select the second entry (Core index 2), not the first entry.
        if (state.chapters.getOrNull(chapterPosition) == null) state
        else state.copy(selectedChapterPosition = chapterPosition)
    }

    @Synchronized
    fun selectedChapter(correlationId: String): Chapter? {
        val snapshot = current(correlationId) ?: return null
        return snapshot.chapters.getOrNull(snapshot.selectedChapterPosition)
    }

    @Synchronized
    fun recordContent(correlationId: String, content: String): Boolean = updateCurrent(correlationId) {
        it.copy(
            content = content,
            contentLoaded = true,
            contentGeneration = it.contentGeneration + 1L,
            stage = "content.load"
        )
    }

    @Synchronized
    fun awaitViewport(correlationId: String): Boolean = updateCurrent(correlationId) {
        it.copy(stage = "awaiting-layout", awaitingViewport = true, viewportSubmitted = false)
    }

    @Synchronized
    fun consumeViewport(correlationId: String): Boolean = updateCurrent(correlationId) { state ->
        if (!state.awaitingViewport || state.viewportSubmitted) state
        else state.copy(viewportSubmitted = true, awaitingViewport = false, stage = "reader.location.resolve")
    }

    @Synchronized
    fun recordCanonicalLocation(
        correlationId: String,
        location: ReaderBookOpenCanonicalLocation
    ): Boolean = updateCurrent(correlationId) {
        it.copy(canonicalLocation = location, stage = "reader.location.resolved")
    }

    @Synchronized
    fun complete(correlationId: String): Boolean = updateCurrent(correlationId) {
        it.copy(
            activeCorrelationId = null,
            stage = "completed",
            loading = false,
            awaitingViewport = false,
            error = null,
            locationResolved = true
        )
    }

    @Synchronized
    fun fail(correlationId: String, message: String): Boolean = updateCurrent(correlationId) {
        it.copy(
            activeCorrelationId = null,
            stage = "failed",
            loading = false,
            awaitingViewport = false,
            error = message
        )
    }

    @Synchronized
    fun cancel(correlationId: String): Boolean = updateCurrent(correlationId) {
        it.copy(
            activeCorrelationId = null,
            stage = "cancelled",
            loading = false,
            awaitingViewport = false,
            error = null
        )
    }

    private fun updateCurrent(
        correlationId: String,
        transform: (ReaderBookOpenDomainState) -> ReaderBookOpenDomainState
    ): Boolean {
        val current = mutableState.value
        if (current.activeCorrelationId != correlationId) return false
        mutableState.value = transform(current)
        return true
    }
}

/** Narrow seam for a real Core command handle and its numeric request id. */
internal interface ReaderBookOpenCommandHandle {
    val requestId: Long
    suspend fun await(): JSONObject
    fun cancel(): Boolean
}

internal interface ReaderBookOpenCommandClient {
    fun begin(method: String, params: JSONObject): ReaderBookOpenCommandHandle
}

/** Production adapter. `ReaderCoreClient` is resolved lazily only when Pilot is enabled. */
internal class ReaderCoreBookOpenCommandClient(
    private val coreProvider: () -> ReaderCoreClient = ReaderCoreClient::get
) : ReaderBookOpenCommandClient {
    override fun begin(method: String, params: JSONObject): ReaderBookOpenCommandHandle {
        val handle = coreProvider().beginCommand(method, params)
        return object : ReaderBookOpenCommandHandle {
            override val requestId: Long get() = handle.requestId
            override suspend fun await(): JSONObject = handle.await()
            override fun cancel(): Boolean = handle.cancel()
        }
    }
}

/** Runtime transition adapter used by the executor and deterministic JVM fakes. */
internal interface ReaderBookOpenRuntimeDriver {
    fun acceptBookOpenResult(
        coreType: String,
        correlationId: String,
        chapterCount: Int? = null,
        error: String? = null
    ): ReaderBookOpenRuntimeAdvance

    fun provideBookOpenLayout(
        correlationId: String,
        layout: ReaderUIBookOpenLayout
    ): ReaderBookOpenRuntimeAdvance

    fun cancelBookOpen(correlationId: String): Boolean
}

internal data class ReaderBookOpenRuntimeAdvance(
    val accepted: Boolean,
    val effects: List<ReaderUIEffect>
)

/**
 * The sole owner of `book.open` Core execution in Pilot mode. Runtime returns
 * exactly one next effect; this executor is the only place allowed to run it.
 */
internal open class ReaderBookOpenEffectExecutor(
    private val domainStore: ReaderBookOpenDomainStore,
    private val runtime: ReaderBookOpenRuntimeDriver,
    private val commandClientFactory: () -> ReaderBookOpenCommandClient = { ReaderCoreBookOpenCommandClient() }
) {
    private val handles = mutableMapOf<String, ReaderBookOpenCommandHandle>()
    private val issuedStages = mutableSetOf<String>()
    private var latestViewport: ReaderBookOpenViewport? = null

    @Synchronized
    open fun start(context: ReaderContext, firstEffect: ReaderUIEffect, scope: CoroutineScope) {
        // A renderer callback for a superseded book must never become the new
        // transaction's anchor. The next callback must carry the new correlation.
        latestViewport = null
        domainStore.begin(context)
        execute(firstEffect, scope)
    }

    @Synchronized
    open fun execute(effect: ReaderUIEffect, scope: CoroutineScope) {
        val correlationId = effect.correlationId ?: return
        if (effect.kind != ReaderUIEffectKind.CORE || !domainStore.isCurrent(correlationId)) return
        val stageKey = "$correlationId:${effect.type}"
        if (!issuedStages.add(stageKey)) return

        scope.launch {
            val command = try {
                buildCommand(effect)
            } catch (error: Exception) {
                handleFailure(effect, error.message ?: "BOOK_OPEN_INVALID_EFFECT")
                return@launch
            }
            val handle = try {
                commandClientFactory().begin(command.method, command.params)
            } catch (error: Exception) {
                handleFailure(effect, error.message ?: "BOOK_OPEN_COMMAND_START_FAILED")
                return@launch
            }

            synchronized(this@ReaderBookOpenEffectExecutor) {
                if (!domainStore.isCurrent(correlationId)) {
                    handle.cancel()
                    return@launch
                }
                handles[correlationId] = handle
            }

            try {
                val result = handle.await()
                synchronized(this@ReaderBookOpenEffectExecutor) {
                    if (handles[correlationId] === handle) handles.remove(correlationId)
                }
                if (!domainStore.isCurrent(correlationId)) return@launch
                handleSuccess(effect, result, scope)
            } catch (error: Exception) {
                synchronized(this@ReaderBookOpenEffectExecutor) {
                    if (handles[correlationId] === handle) handles.remove(correlationId)
                }
                if (domainStore.isCurrent(correlationId)) {
                    handleFailure(effect, error.message ?: "BOOK_OPEN_CORE_FAILURE")
                }
            }
        }
    }

    /** Stores real Compose metrics; only a content-complete transaction can consume them. */
    @Synchronized
    open fun onViewportLayoutReady(viewport: ReaderBookOpenViewport, scope: CoroutineScope) {
        if (viewport.viewportWidth <= 0 || viewport.viewportHeight <= 0 || viewport.fontScale <= 0.0) return
        val state = domainStore.state.value
        // A loading shell may be measured before chapter content exists. Only
        // accept a measurement emitted by the current rendered content
        // subtree, while Runtime is specifically waiting for layout.
        if (
            state.activeCorrelationId != viewport.correlationId ||
            !state.contentLoaded ||
            !state.awaitingViewport ||
            state.contentGeneration != viewport.contentGeneration
        ) return
        latestViewport = viewport
        provideViewportIfAwaiting(scope)
    }

    @Synchronized
    open fun cancel(correlationId: String): Boolean {
        // Invalidate before invoking the native Core cancellation so a result that
        // races with cancel cannot mutate the domain state.
        val invalidated = domainStore.cancel(correlationId)
        val runtimeCancelled = runtime.cancelBookOpen(correlationId)
        val handle = handles.remove(correlationId)
        val coreCancelled = handle?.cancel() ?: false
        return invalidated || runtimeCancelled || coreCancelled
    }

    @Synchronized
    open fun cancelActive(): Boolean = domainStore.state.value.activeCorrelationId?.let(::cancel) ?: false

    private fun handleSuccess(effect: ReaderUIEffect, result: JSONObject, scope: CoroutineScope) {
        val correlationId = requireNotNull(effect.correlationId)
        when (effect.type) {
            "source.detail" -> {
                domainStore.recordDetail(correlationId, BookApi.parseBookDetailResult(result))
                advance(effect, correlationId, scope)
            }
            "chapter.list" -> {
                val toc = BookApi.parseBookTocResult(result)
                domainStore.recordToc(correlationId, toc)
                advance(effect, correlationId, scope, chapterCount = toc.chapters.size)
            }
            "content.load" -> {
                domainStore.recordContent(correlationId, BookApi.parseContentResult(result))
                val next = runtime.acceptBookOpenResult(effect.type, correlationId)
                if (!next.accepted) return
                // Runtime intentionally returns no effect here. It must wait for
                // actual renderer dimensions before location resolution.
                domainStore.awaitViewport(correlationId)
                provideViewportIfAwaiting(scope)
            }
            "reader.location.resolve" -> {
                val location = try {
                    parseCanonicalLocation(
                        result = result,
                        expectedBookId = requireNotNull(domainStore.current(correlationId)).bookId,
                        expectedChapterIndex = domainStore.selectedChapter(correlationId)?.index
                            ?: effect.payload["chapterIndex"]?.toIntOrNull()
                            ?: error("reader.location.resolve requires a selected chapter")
                    )
                } catch (error: Exception) {
                    handleFailure(
                        effect,
                        "BOOK_OPEN_INVALID_LOCATION_RESULT:${error.message ?: "invalid result"}"
                    )
                    return
                }
                if (!domainStore.recordCanonicalLocation(correlationId, location)) return
                val next = runtime.acceptBookOpenResult(effect.type, correlationId)
                if (next.accepted) domainStore.complete(correlationId)
            }
            else -> handleFailure(effect, "BOOK_OPEN_UNSUPPORTED_STAGE:${effect.type}")
        }
    }

    private fun advance(
        effect: ReaderUIEffect,
        correlationId: String,
        scope: CoroutineScope,
        chapterCount: Int? = null
    ) {
        val next = runtime.acceptBookOpenResult(effect.type, correlationId, chapterCount = chapterCount)
        if (!next.accepted) return
        next.effects.forEach { nextEffect ->
            if (nextEffect.type == "content.load") {
                nextEffect.payload["chapterIndex"]?.toIntOrNull()?.let { index ->
                    domainStore.selectChapter(correlationId, index)
                }
            }
            execute(nextEffect, scope)
        }
    }

    private fun handleFailure(effect: ReaderUIEffect, message: String) {
        val correlationId = effect.correlationId ?: return
        val advance = runtime.acceptBookOpenResult(effect.type, correlationId, error = message)
        if (advance.accepted) domainStore.fail(correlationId, message)
    }

    private fun provideViewportIfAwaiting(scope: CoroutineScope) {
        val state = domainStore.state.value
        val correlationId = state.activeCorrelationId ?: return
        val viewport = latestViewport ?: return
        if (viewport.correlationId != correlationId) return
        if (!state.awaitingViewport || !domainStore.consumeViewport(correlationId)) return
        val next = try {
            runtime.provideBookOpenLayout(correlationId, viewport.toRuntimeLayout())
        } catch (error: Exception) {
            domainStore.fail(correlationId, error.message ?: "BOOK_OPEN_INVALID_LAYOUT")
            return
        }
        if (!next.accepted) return
        next.effects.forEach { execute(it, scope) }
    }

    private data class CoreCommand(val method: String, val params: JSONObject)

    private fun buildCommand(effect: ReaderUIEffect): CoreCommand {
        val correlationId = requireNotNull(effect.correlationId)
        val state = requireNotNull(domainStore.current(correlationId)) { "stale book.open effect" }
        val book = state.book ?: Book(
            bookUrl = state.bookId,
            name = state.bookName,
            origin = state.sourceId,
            sourceId = state.sourceId
        )
        return when (effect.type) {
            "source.detail" -> CoreCommand(
                method = "book.detail",
                params = BookApi.buildDetailParams(state.sourceId, book)
            )
            "chapter.list" -> CoreCommand(
                method = if (effect.payload["sourceKind"] == "local") "local_book.toc" else "book.toc",
                params = if (effect.payload["sourceKind"] == "local") {
                    JSONObject().put("bookId", state.bookId)
                } else {
                    BookApi.buildTocParams(state.sourceId, book)
                }
            )
            "content.load" -> {
                val chapter = requireNotNull(domainStore.selectedChapter(correlationId)) {
                    "content.load requires a selected TOC entry"
                }
                CoreCommand(
                    method = if (effect.payload["sourceKind"] == "local") {
                        "local_book.chapter.content"
                    } else {
                        "chapter.content"
                    },
                    params = if (effect.payload["sourceKind"] == "local") {
                        JSONObject()
                            .put("bookId", state.bookId)
                            .put("chapterIndex", chapter.index)
                    } else {
                        BookApi.buildContentParams(state.sourceId, book, chapter)
                    }
                )
            }
            "reader.location.resolve" -> {
                // Effect `chapterIndex` is a TOC position. Location/Core
                // expects the selected entry's actual index, which may be
                // non-zero-based or sparse.
                val chapterIndex = domainStore.selectedChapter(correlationId)?.index
                    ?: effect.payload["chapterIndex"]?.toIntOrNull()
                    ?: 0
                CoreCommand(
                    method = "reader.location.resolve",
                    params = BookApi.buildLocationResolveParams(
                        bookId = state.bookId,
                        chapterIndex = chapterIndex,
                        chapterOffset = effect.payload.requiredLong("chapterOffset"),
                        chapterProgress = effect.payload.requiredDouble("chapterProgress"),
                        viewportWidth = effect.payload.requiredInt("viewportWidth"),
                        viewportHeight = effect.payload.requiredInt("viewportHeight"),
                        fontScale = effect.payload.requiredDouble("fontScale")
                    )
                )
            }
            else -> error("unsupported book.open Core stage: ${effect.type}")
        }
    }

    private fun Map<String, String>.requiredLong(name: String): Long =
        get(name)?.toLongOrNull() ?: error("book.open missing/invalid $name")

    private fun Map<String, String>.requiredInt(name: String): Int =
        get(name)?.toIntOrNull() ?: error("book.open missing/invalid $name")

    private fun Map<String, String>.requiredDouble(name: String): Double =
        get(name)?.toDoubleOrNull() ?: error("book.open missing/invalid $name")

    /**
     * Core result boundary. A syntactically successful command must not finish
     * the Runtime ledger unless it returns the canonical identity requested by
     * this correlation. In particular, `{}` and a result for another book or
     * chapter are terminal failures rather than silent completion.
     */
    private fun parseCanonicalLocation(
        result: JSONObject,
        expectedBookId: String,
        expectedChapterIndex: Int
    ): ReaderBookOpenCanonicalLocation {
        require(result.optBoolean("resolved", false)) {
            "reader.location.resolve resolved must be true"
        }
        val canonical = result.optJSONObject("canonicalLocation")
            ?: error("reader.location.resolve missing canonicalLocation")
        val bookId = canonical.requiredNonBlankString("bookId")
        val chapterIndex = canonical.requiredNonNegativeInt("chapterIndex")
        val chapterOffset = canonical.requiredNonNegativeLong("chapterOffset")
        val chapterProgress = canonical.requiredProgress("chapterProgress")
        val locationRevision = canonical.requiredNonBlankString("locationRevision")
        val resolverVersion = result.requiredNonBlankString("resolverVersion")
        val reflow = result.optJSONObject("reflow")
            ?: error("reader.location.resolve missing reflow")
        val strategy = reflow.requiredNonBlankString("strategy")
        val primaryAnchor = reflow.requiredNonBlankString("primaryAnchor")
        val fallbackAnchor = reflow.requiredNonBlankString("fallbackAnchor")
        if (!reflow.has("layoutIndependent")) {
            error("reader.location.resolve missing reflow.layoutIndependent")
        }

        require(bookId == expectedBookId) {
            "reader.location.resolve bookId drift expected=$expectedBookId actual=$bookId"
        }
        require(chapterIndex == expectedChapterIndex) {
            "reader.location.resolve chapterIndex drift expected=$expectedChapterIndex actual=$chapterIndex"
        }
        return ReaderBookOpenCanonicalLocation(
            bookId = bookId,
            chapterIndex = chapterIndex,
            chapterOffset = chapterOffset,
            chapterProgress = chapterProgress,
            locationRevision = locationRevision,
            resolverVersion = resolverVersion,
            reflowStrategy = strategy,
            reflowPrimaryAnchor = primaryAnchor,
            reflowFallbackAnchor = fallbackAnchor,
            reflowLayoutIndependent = reflow.getBoolean("layoutIndependent")
        )
    }

    private fun JSONObject.requiredNonBlankString(name: String): String =
        optString(name).takeIf(String::isNotBlank)
            ?: error("reader.location.resolve missing/blank $name")

    private fun JSONObject.requiredNonNegativeInt(name: String): Int {
        if (!has(name)) error("reader.location.resolve missing $name")
        return getInt(name).also {
            require(it >= 0) { "reader.location.resolve $name must be >= 0" }
        }
    }

    private fun JSONObject.requiredNonNegativeLong(name: String): Long {
        if (!has(name)) error("reader.location.resolve missing $name")
        return getLong(name).also {
            require(it >= 0L) { "reader.location.resolve $name must be >= 0" }
        }
    }

    private fun JSONObject.requiredProgress(name: String): Double {
        if (!has(name)) error("reader.location.resolve missing $name")
        return getDouble(name).also {
            require(it.isFinite() && it in 0.0..1.0) {
                "reader.location.resolve $name must be between 0 and 1"
            }
        }
    }
}
