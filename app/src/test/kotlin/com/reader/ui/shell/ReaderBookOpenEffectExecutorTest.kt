package com.reader.ui.shell

import io.reader.ui.runtime.ReaderUIBookOpenLayout
import io.reader.ui.runtime.ReaderUIEffect
import io.reader.ui.runtime.ReaderUIEffectKind
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * Transaction-level proof for the default-off book.open Pilot executor.
 *
 * These are deliberately JVM-only: Core is represented by cancellable command
 * handles so the tests can assert ordering, DTO choice, stale rejection, and
 * layout gating without claiming device proof.
 */
class ReaderBookOpenEffectExecutorTest {

    @Test
    fun `remote toc position selects sparse Core entry index for content and location`() {
        val store = ReaderBookOpenDomainStore()
        val runtime = SequencingRuntime(sourceKind = "remote", selectedPosition = 1)
        val client = ImmediateCommandClient { method, params ->
            when (method) {
                "book.detail" -> detailResult()
                "book.toc" -> tocResult(indices = listOf(1, 7, 13))
                "chapter.content" -> JSONObject().put("content", "chapter seven")
                "reader.location.resolve" -> resolveResult(params)
                else -> error("unexpected command $method")
            }
        }
        val executor = ReaderBookOpenEffectExecutor(store, runtime) { client }
        val scope = testScope()

        try {
            executor.start(
                context = context(correlationId = "remote-open"),
                firstEffect = coreEffect("source.detail", "remote-open", "remote"),
                scope = scope
            )

            assertEquals(
                listOf("book.detail", "book.toc", "chapter.content"),
                client.calls.map { it.method }
            )
            // ReaderUIRuntime clamped position 1; the second Core TOC entry
            // has an actual index of 7. Content must receive 7, not 1.
            assertEquals(7, client.calls[2].params.getInt("chapterIndex"))
            assertTrue(store.state.value.contentLoaded)
            assertTrue(store.state.value.awaitingViewport)
            assertEquals(1L, store.state.value.contentGeneration)

            // A callback with a pre-content generation must not consume the
            // pending layout slot, even if the correlation happens to match.
            executor.onViewportLayoutReady(
                viewport("remote-open", contentGeneration = 0L),
                scope
            )
            assertTrue(runtime.layouts.isEmpty())

            executor.onViewportLayoutReady(
                viewport("remote-open", contentGeneration = 1L),
                scope
            )

            assertEquals(1, runtime.layouts.size)
            assertEquals(
                listOf("book.detail", "book.toc", "chapter.content", "reader.location.resolve"),
                client.calls.map { it.method }
            )
            // Location also uses the Core entry index rather than the runtime
            // TOC position carried in the effect payload.
            assertEquals(7, client.calls.last().params.getInt("chapterIndex"))
            assertTrue(store.state.value.locationResolved)
            assertEquals("book-1", store.state.value.canonicalLocation?.bookId)
            assertEquals(7, store.state.value.canonicalLocation?.chapterIndex)
            assertEquals("reader.location.resolve.v1.reflow", store.state.value.canonicalLocation?.resolverVersion)
            assertEquals(null, store.state.value.activeCorrelationId)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `local book pipeline uses local Core commands and selected entry index`() {
        val store = ReaderBookOpenDomainStore()
        val runtime = SequencingRuntime(sourceKind = "local", selectedPosition = 0)
        val client = ImmediateCommandClient { method, params ->
            when (method) {
                "local_book.toc" -> tocResult(indices = listOf(9))
                "local_book.chapter.content" -> JSONObject().put("content", "local chapter")
                "reader.location.resolve" -> resolveResult(params)
                else -> error("unexpected local command $method")
            }
        }
        val executor = ReaderBookOpenEffectExecutor(store, runtime) { client }
        val scope = testScope()

        try {
            executor.start(
                context = context(
                    correlationId = "local-open",
                    sourceId = "local",
                    bookUrl = "local://book/one"
                ),
                firstEffect = coreEffect("chapter.list", "local-open", "local"),
                scope = scope
            )

            assertEquals(
                listOf("local_book.toc", "local_book.chapter.content"),
                client.calls.map { it.method }
            )
            assertEquals("local://book/one", client.calls[0].params.getString("bookId"))
            assertEquals("local://book/one", client.calls[1].params.getString("bookId"))
            assertEquals(9, client.calls[1].params.getInt("chapterIndex"))

            executor.onViewportLayoutReady(
                viewport("local-open", contentGeneration = store.state.value.contentGeneration),
                scope
            )
            assertEquals("reader.location.resolve", client.calls.last().method)
            assertEquals(9, client.calls.last().params.getInt("chapterIndex"))
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `pre content measurement is ignored and cancel aborts real handle before late result`() = runBlocking {
        val store = ReaderBookOpenDomainStore()
        val runtime = SequencingRuntime(sourceKind = "remote", selectedPosition = 0)
        val client = DeferredCommandClient()
        val executor = ReaderBookOpenEffectExecutor(store, runtime) { client }
        val scope = testScope()

        try {
            executor.start(
                context = context(correlationId = "late-open"),
                firstEffect = coreEffect("source.detail", "late-open", "remote"),
                scope = scope
            )
            assertEquals(listOf("book.detail"), client.calls.map { it.method })

            // The spinner/pre-content phase has no current content generation;
            // it cannot release reader.location.resolve.
            executor.onViewportLayoutReady(viewport("late-open", contentGeneration = 0L), scope)
            assertTrue(runtime.layouts.isEmpty())

            assertTrue(executor.cancel("late-open"))
            assertTrue(client.handles.single().cancelled)
            assertEquals(listOf("late-open"), runtime.cancelled)
            assertEquals(null, store.state.value.activeCorrelationId)

            // A native handle may still deliver after cancellation. Its result
            // must not advance Runtime or repopulate the correlation-scoped UI.
            client.handles.single().result.complete(detailResult())
            yield()
            assertTrue(runtime.acceptedStages.isEmpty())
            assertFalse(store.state.value.contentLoaded)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `stale content layout cannot resolve the newer correlation`() {
        val store = ReaderBookOpenDomainStore()
        val runtime = SequencingRuntime(sourceKind = "remote", selectedPosition = 0, emitLocation = false)
        val executor = ReaderBookOpenEffectExecutor(store, runtime) { ImmediateCommandClient { _, _ -> JSONObject() } }
        val scope = testScope()

        try {
            store.begin(context(correlationId = "old-open"))
            store.recordContent("old-open", "old")
            store.awaitViewport("old-open")
            store.begin(context(correlationId = "new-open"))
            store.recordContent("new-open", "new")
            store.awaitViewport("new-open")

            executor.onViewportLayoutReady(viewport("old-open", contentGeneration = 1L), scope)
            assertTrue(runtime.layouts.isEmpty())
            assertTrue(store.state.value.awaitingViewport)

            executor.onViewportLayoutReady(viewport("new-open", contentGeneration = 1L), scope)
            assertEquals(listOf("new-open"), runtime.layouts.map { it.first })
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `VM admission failure clears Runtime ledger and a later book open retries`() {
        val store = ReaderBookOpenDomainStore()
        val coordinator = ReaderUiRuntimeCoordinator(bookOpenPilotEnabled = true)
        val client = DeferredCommandClient()
        val executor = ThrowOnceExecutor(store, coordinator) { client }
        val scope = testScope()
        val nativeReducerCalls = AtomicInteger()
        val vm = AppShellViewModel(
            readerUiRuntimeCoordinator = coordinator,
            nativeReducer = { state, intent ->
                nativeReducerCalls.incrementAndGet()
                ReaderUiReducer.reduce(state, intent)
            },
            readerBookOpenDomainStore = store,
            readerBookOpenEffectExecutor = executor,
            readerBookOpenScope = scope
        )
        val productionBefore = vm.state.value

        try {
            vm.dispatch(
                ReaderUiIntent.EnterReaderFromAction(
                    sourceId = "source-1",
                    bookUrl = "book-1",
                    bookName = "Book One",
                    requestId = "admission-fails"
                )
            )

            assertEquals(productionBefore, vm.state.value)
            assertEquals(0, nativeReducerCalls.get())
            assertNull(coordinator.runtimeState.bookOpenTransaction)

            vm.dispatch(
                ReaderUiIntent.EnterReaderFromAction(
                    sourceId = "source-1",
                    bookUrl = "book-2",
                    bookName = "Book Two",
                    requestId = "admission-retry"
                )
            )

            assertEquals(RouteIds.IMMERSIVE_READING, vm.state.value.currentRoute.routeId)
            assertEquals("admission-retry", vm.state.value.readerContext?.entryRequestId)
            assertEquals("admission-retry", coordinator.runtimeState.bookOpenTransaction?.correlationId)
            assertEquals(1, nativeReducerCalls.get())
            assertEquals(listOf("book.detail"), client.calls.map { it.method })
        } finally {
            executor.cancel("admission-retry")
            scope.cancel()
        }
    }

    @Test
    fun `malformed or identity drift location result fails the transaction terminally`() {
        listOf(
            "malformed" to { _: JSONObject -> JSONObject() },
            "book identity drift" to { params: JSONObject ->
                resolveResult(params).apply {
                    getJSONObject("canonicalLocation").put("bookId", "another-book")
                }
            },
            "chapter identity drift" to { params: JSONObject ->
                resolveResult(params).apply {
                    getJSONObject("canonicalLocation").put("chapterIndex", 99)
                }
            }
        ).forEachIndexed { ordinal, (label, locationResult) ->
            val store = ReaderBookOpenDomainStore()
            val runtime = SequencingRuntime(sourceKind = "remote", selectedPosition = 0)
            val client = ImmediateCommandClient { method, params ->
                when (method) {
                    "book.detail" -> detailResult()
                    "book.toc" -> tocResult(indices = listOf(3))
                    "chapter.content" -> JSONObject().put("content", "chapter")
                    "reader.location.resolve" -> locationResult(params)
                    else -> error("unexpected command $method")
                }
            }
            val executor = ReaderBookOpenEffectExecutor(store, runtime) { client }
            val scope = testScope()
            val correlationId = "terminal-location-$ordinal"

            try {
                executor.start(
                    context = context(correlationId = correlationId),
                    firstEffect = coreEffect("source.detail", correlationId, "remote"),
                    scope = scope
                )
                executor.onViewportLayoutReady(
                    viewport(correlationId, contentGeneration = store.state.value.contentGeneration),
                    scope
                )

                assertEquals("failed", store.state.value.stage)
                assertEquals(null, store.state.value.activeCorrelationId)
                assertFalse(store.state.value.locationResolved)
                assertTrue(
                    "$label should retain a typed result-boundary error",
                    store.state.value.error.orEmpty().startsWith("BOOK_OPEN_INVALID_LOCATION_RESULT:")
                )
                assertEquals(null, store.state.value.canonicalLocation)
            } finally {
                scope.cancel()
            }
        }
    }

    private fun context(
        correlationId: String,
        sourceId: String = "source-1",
        bookUrl: String = "book-1"
    ) = ReaderContext(
        sourceId = sourceId,
        bookUrl = bookUrl,
        bookName = "Book One",
        entry = ReaderEntry.ACTION_TO_IMMERSIVE,
        entryRequestId = correlationId
    )

    private fun coreEffect(type: String, correlationId: String, sourceKind: String) = ReaderUIEffect(
        kind = ReaderUIEffectKind.CORE,
        type = type,
        payload = mapOf("sourceKind" to sourceKind),
        correlationId = correlationId
    )

    private fun viewport(correlationId: String, contentGeneration: Long) = ReaderBookOpenViewport(
        correlationId = correlationId,
        viewportWidth = 390,
        viewportHeight = 844,
        fontScale = 1.0,
        contentGeneration = contentGeneration,
        chapterOffset = 24L,
        chapterProgress = 0.25
    )

    private fun detailResult() = JSONObject()
        .put("sourceId", "source-1")
        .put("tocUrl", "https://example.test/toc")
        .put("book", JSONObject()
            .put("bookId", "book-1")
            .put("title", "Book One")
            .put("author", "Author")
        )

    private fun tocResult(indices: List<Int>) = JSONObject()
        .put("sourceId", "source-1")
        .put("bookId", "book-1")
        .put("toc", org.json.JSONArray().apply {
            indices.forEachIndexed { position, index ->
                put(JSONObject()
                    .put("title", "Chapter $index")
                    .put("url", "https://example.test/chapter/$index")
                    .put("index", index)
                    .put("variables", JSONObject().put("position", position))
                )
            }
        })

    private fun resolveResult(params: JSONObject) = JSONObject()
        .put("canonicalLocation", JSONObject()
            .put("bookId", params.getString("bookId"))
            .put("chapterIndex", params.getInt("chapterIndex"))
            .put("chapterOffset", params.getJSONObject("anchor").getLong("chapterOffset"))
            .put("chapterProgress", params.getJSONObject("anchor").getDouble("chapterProgress"))
            .put(
                "locationRevision",
                "reader-location-v1:${params.getString("bookId")}:" +
                    "${params.getInt("chapterIndex")}:" +
                    params.getJSONObject("anchor").getLong("chapterOffset")
            )
        )
        .put("resolverVersion", "reader.location.resolve.v1.reflow")
        .put("resolved", true)
        .put("reflow", JSONObject()
            .put("strategy", "offsetAnchor")
            .put("primaryAnchor", "chapterOffset")
            .put("fallbackAnchor", "chapterProgress")
            .put("layoutIndependent", true)
        )

    private fun testScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    private data class CommandCall(val method: String, val params: JSONObject)

    private class ImmediateCommandClient(
        private val responder: (String, JSONObject) -> JSONObject
    ) : ReaderBookOpenCommandClient {
        val calls = mutableListOf<CommandCall>()
        private var nextRequestId = 1L

        override fun begin(method: String, params: JSONObject): ReaderBookOpenCommandHandle {
            calls += CommandCall(method, params)
            val result = responder(method, params)
            return object : ReaderBookOpenCommandHandle {
                override val requestId: Long = nextRequestId++
                override suspend fun await(): JSONObject = result
                override fun cancel(): Boolean = true
            }
        }
    }

    private class DeferredCommandClient : ReaderBookOpenCommandClient {
        val calls = mutableListOf<CommandCall>()
        val handles = mutableListOf<DeferredHandle>()
        private var nextRequestId = 1L

        override fun begin(method: String, params: JSONObject): ReaderBookOpenCommandHandle {
            calls += CommandCall(method, params)
            return DeferredHandle(nextRequestId++).also(handles::add)
        }

        class DeferredHandle(override val requestId: Long) : ReaderBookOpenCommandHandle {
            val result = CompletableDeferred<JSONObject>()
            var cancelled: Boolean = false

            override suspend fun await(): JSONObject = result.await()
            override fun cancel(): Boolean {
                cancelled = true
                return true
            }
        }
    }

    /** Minimal Runtime ledger fake; it preserves the one-next-effect protocol. */
    private class SequencingRuntime(
        private val sourceKind: String,
        private val selectedPosition: Int,
        private val emitLocation: Boolean = true
    ) : ReaderBookOpenRuntimeDriver {
        val acceptedStages = mutableListOf<String>()
        val layouts = mutableListOf<Pair<String, ReaderUIBookOpenLayout>>()
        val cancelled = mutableListOf<String>()

        override fun acceptBookOpenResult(
            coreType: String,
            correlationId: String,
            chapterCount: Int?,
            error: String?
        ): ReaderBookOpenRuntimeAdvance {
            acceptedStages += coreType
            val next = when (coreType) {
                "source.detail" -> ReaderUIEffect(
                    kind = ReaderUIEffectKind.CORE,
                    type = "chapter.list",
                    payload = mapOf("sourceKind" to sourceKind),
                    correlationId = correlationId
                )
                "chapter.list" -> ReaderUIEffect(
                    kind = ReaderUIEffectKind.CORE,
                    type = "content.load",
                    payload = mapOf(
                        "sourceKind" to sourceKind,
                        "chapterIndex" to selectedPosition.toString()
                    ),
                    correlationId = correlationId
                )
                else -> null
            }
            return ReaderBookOpenRuntimeAdvance(accepted = true, effects = listOfNotNull(next))
        }

        override fun provideBookOpenLayout(
            correlationId: String,
            layout: ReaderUIBookOpenLayout
        ): ReaderBookOpenRuntimeAdvance {
            layouts += correlationId to layout
            val effect = if (!emitLocation) null else ReaderUIEffect(
                kind = ReaderUIEffectKind.CORE,
                type = "reader.location.resolve",
                payload = mapOf(
                    "chapterOffset" to layout.chapterOffset.toString(),
                    "chapterProgress" to layout.chapterProgress.toString(),
                    "viewportWidth" to layout.viewportWidth.toString(),
                    "viewportHeight" to layout.viewportHeight.toString(),
                    "fontScale" to layout.fontScale.toString()
                ),
                correlationId = correlationId
            )
            return ReaderBookOpenRuntimeAdvance(accepted = true, effects = listOfNotNull(effect))
        }

        override fun cancelBookOpen(correlationId: String): Boolean {
            cancelled += correlationId
            return true
        }
    }

    /** Fails only the first synchronous executor admission to prove VM rollback/retry. */
    private class ThrowOnceExecutor(
        domainStore: ReaderBookOpenDomainStore,
        runtime: ReaderBookOpenRuntimeDriver,
        commandClientFactory: () -> ReaderBookOpenCommandClient
    ) : ReaderBookOpenEffectExecutor(domainStore, runtime, commandClientFactory) {
        private var failStart = true

        override fun start(context: ReaderContext, firstEffect: ReaderUIEffect, scope: CoroutineScope) {
            if (failStart) {
                failStart = false
                throw IllegalStateException("injected book.open executor admission failure")
            }
            super.start(context, firstEffect, scope)
        }
    }
}
