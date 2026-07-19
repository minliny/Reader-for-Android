package com.reader.ui.shell

import com.reader.api.Book
import com.reader.api.BookDetailResult
import com.reader.api.BookTocResult
import com.reader.api.Chapter
import io.reader.ui.runtime.ReaderUIRuntime
import io.reader.ui.runtime.ReaderUIState
import io.reader.ui.runtime.ReaderUIAutoPageTransaction
import io.reader.ui.runtime.ReaderUIPageTransaction
import io.reader.ui.runtime.ReaderUIEffect
import io.reader.ui.runtime.ReaderUIEffectKind
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicInteger

class ReaderPlaybackEffectExecutorTest {

    @Test
    fun `page commit waits for matching durable progress row after canonical location`() = runBlocking {
        val locationDeferred = CompletableDeferred<JSONObject>()
        val progressDeferred = CompletableDeferred<JSONObject>()
        val harness = Harness(locationDeferred, progressDeferred)
        val dispatch = harness.coordinator.dispatchPlaybackPilot(ReaderUiIntent.TurnPageNext())
            as ReaderPlaybackPilotDispatch.Applied
        harness.apply(dispatch, this)
        harness.executor.awaitSerialIdle()

        val pending = harness.domain.state.value
        assertNotNull(pending.pageCorrelationId)
        assertEquals(0, pending.committedPageIndex)
        harness.executor.onPageLayoutReady(
            ReaderPlaybackPageMeasurement(
                correlationId = requireNotNull(pending.pageCorrelationId),
                contentGeneration = 1,
                contentLength = CONTENT.length,
                direction = "next",
                anchor = "chapter:7:char-offset:4",
                targetPageIndex = 2,
                chapterIndex = 7,
                chapterOffset = 4,
                chapterProgress = 0.5,
                viewportWidth = 1080,
                viewportHeight = 1920,
                fontScale = 1.15
            ),
            this
        )
        // The command is pending: visible page/location must not speculate.
        eventually { harness.client.methods.contains("reader.location.resolve") }
        assertEquals(0, harness.domain.state.value.committedPageIndex)

        locationDeferred.complete(locationResult(offset = 4, progress = 0.5))
        eventually { harness.client.methods.contains("reading.progress.update") }
        assertEquals(0, harness.domain.state.value.committedPageIndex)
        assertEquals("persisting-progress", harness.domain.state.value.stage)
        val progressParams = harness.client.params
            .single { it.first == "reading.progress.update" }.second
        assertEquals("source-1", progressParams.getString("sourceId"))
        assertEquals("book-1", progressParams.getString("bookId"))
        assertEquals(7, progressParams.getInt("chapterIndex"))
        assertEquals(4L, progressParams.getLong("chapterOffset"))
        assertEquals(0.5, progressParams.getDouble("chapterProgress"), 0.0)
        assertEquals("rev-4", progressParams.getString("locationRevision"))
        progressDeferred.complete(progressResult(progressParams))
        harness.executor.awaitSerialIdle()
        assertEquals(2, harness.domain.state.value.committedPageIndex)
        assertEquals(4L, harness.domain.state.value.committedLocation?.chapterOffset)
        val params = harness.client.params.single { it.first == "reader.location.resolve" }.second
        assertEquals(4, params.getJSONObject("anchor").getInt("chapterOffset"))
        assertEquals(1080, params.getJSONObject("layout").getInt("viewportWidth"))
        assertOrdered(
            harness.domain.state.value.effectTrace,
            "terminal:reader.location.resolve:",
            "start:reader.progress.update:",
            "terminal:reader.progress.update:"
        )
        assertFalse(
            harness.coordinator.acceptPageProgressResult(
                requireNotNull(pending.pageCorrelationId),
                stored = true
            ).accepted
        )
        harness.executor.cancelAll()
    }

    @Test
    fun `stale current progress row is terminal and preserves last committed page`() = runBlocking {
        val progressDeferred = CompletableDeferred<JSONObject>()
        val harness = Harness(progressDeferred = progressDeferred)
        val dispatch = harness.coordinator.dispatchPlaybackPilot(ReaderUiIntent.TurnPageNext())
            as ReaderPlaybackPilotDispatch.Applied
        harness.apply(dispatch, this)
        harness.executor.awaitSerialIdle()
        val correlationId = requireNotNull(harness.domain.state.value.pageCorrelationId)
        harness.executor.onPageLayoutReady(
            ReaderPlaybackPageMeasurement(
                correlationId, 1, CONTENT.length, "next", "chapter:7:char-offset:4",
                3, 7, 4, 0.5, 1080, 1920, 1.0
            ),
            this
        )
        eventually { harness.client.methods.contains("reading.progress.update") }
        val intended = harness.client.params.single { it.first == "reading.progress.update" }.second
        progressDeferred.complete(
            progressResult(intended)
                .put("updatedAt", intended.getLong("updatedAt") + 10L)
                .put("chapterOffset", 99L)
        )
        harness.executor.awaitSerialIdle()

        assertEquals(0, harness.domain.state.value.committedPageIndex)
        assertEquals(0L, harness.domain.state.value.committedLocation?.chapterOffset)
        assertEquals(null, harness.domain.state.value.pendingProgress)
        assertTrue(harness.domain.state.value.error?.contains("PAGE_INVALID_PROGRESS") == true)
        harness.executor.cancelAll()
    }

    @Test
    fun `progress command is not cancelled after commit boundary and mirrors once`() = runBlocking {
        val progressDeferred = CompletableDeferred<JSONObject>()
        val mirrored = mutableListOf<ReaderPlaybackPendingProgress>()
        val harness = Harness(
            progressDeferred = progressDeferred,
            progressMirror = ReaderPlaybackProgressMirror { mirrored += it }
        )
        val dispatch = harness.coordinator.dispatchPlaybackPilot(ReaderUiIntent.TurnPageNext())
            as ReaderPlaybackPilotDispatch.Applied
        harness.apply(dispatch, this)
        harness.executor.awaitSerialIdle()
        val correlationId = requireNotNull(harness.domain.state.value.pageCorrelationId)
        assertFalse(harness.coordinator.acceptPageProgressResult(correlationId, stored = true).accepted)
        harness.executor.onPageLayoutReady(
            ReaderPlaybackPageMeasurement(
                correlationId, 1, CONTENT.length, "next", "chapter:7:char-offset:4",
                6, 7, 4, 0.5, 1080, 1920, 1.0
            ),
            this
        )
        eventually { harness.client.methods.contains("reading.progress.update") }

        harness.executor.teardownForReaderExit(this)
        harness.executor.cancelAll(this)
        assertFalse("reading.progress.update" in harness.client.cancelledMethods)
        assertEquals(0, harness.domain.state.value.committedPageIndex)

        val params = harness.client.params.single { it.first == "reading.progress.update" }.second
        progressDeferred.complete(progressResult(params))
        harness.executor.awaitSerialIdle()
        assertEquals(6, harness.domain.state.value.committedPageIndex)
        assertEquals(1, mirrored.size)
        assertFalse(harness.coordinator.acceptPageProgressResult(correlationId, stored = true).accepted)
    }

    @Test
    fun `route and legacy page mutations are blocked while progress commit is pending`() {
        val transaction = ReaderUIPageTransaction(
            correlationId = "commit-boundary",
            direction = "next",
            source = "manual",
            contractEvent = "reader.page.next",
            stage = "persisting-progress",
            pendingCanonicalLocation = "book-1:7:4:rev-4",
            pendingPageIndex = 2
        )
        val coordinator = ReaderUiRuntimeCoordinator(
            runtime = ReaderUIRuntime(
                ReaderUIState(
                    routeId = "immersive-reading",
                    pageTransaction = transaction
                )
            ),
            directoryPilotEnabled = false,
            bookOpenPilotEnabled = false,
            playbackPilotEnabled = true
        )
        var nativeReducerCalls = 0
        val viewModel = AppShellViewModel(
            readerUiRuntimeCoordinator = coordinator,
            nativeReducer = { state, _ -> nativeReducerCalls += 1; state }
        )

        viewModel.dispatch(ReaderUiIntent.PopRoute)
        viewModel.dispatch(ReaderUiIntent.UpdateReaderPage(page = 9, progress = 0.9f))

        assertEquals(0, nativeReducerCalls)
        assertEquals("commit-boundary", coordinator.playbackRuntimeState.pageTransaction?.correlationId)
        assertEquals("persisting-progress", coordinator.playbackRuntimeState.pageTransaction?.stage)
    }

    @Test
    fun `page identity drift is terminal and preserves last committed page`() = runBlocking {
        val deferred = CompletableDeferred<JSONObject>()
        val harness = Harness(locationDeferred = deferred)
        val dispatch = harness.coordinator.dispatchPlaybackPilot(ReaderUiIntent.TurnPageNext())
            as ReaderPlaybackPilotDispatch.Applied
        harness.apply(dispatch, this)
        harness.executor.awaitSerialIdle()
        val correlationId = requireNotNull(harness.domain.state.value.pageCorrelationId)
        harness.executor.onPageLayoutReady(
            ReaderPlaybackPageMeasurement(
                correlationId,
                1,
                CONTENT.length,
                "next",
                "chapter:7:char-offset:4",
                3,
                7,
                4,
                0.5,
                1080,
                1920,
                1.0
            ),
            this
        )
        eventually { harness.client.methods.contains("reader.location.resolve") }
        deferred.complete(
            locationResult(offset = 4, progress = 0.5).also {
                it.getJSONObject("canonicalLocation").put("chapterIndex", 99)
            }
        )
        harness.executor.awaitSerialIdle()

        assertEquals(0, harness.domain.state.value.committedPageIndex)
        assertEquals(0L, harness.domain.state.value.committedLocation?.chapterOffset)
        assertTrue(harness.domain.state.value.error?.contains("PAGE_INVALID_LOCATION") == true)
        harness.executor.cancelAll()
    }

    @Test
    fun `TTS executes plan then queue then correlated speech and done report next`() = runBlocking {
        val harness = Harness()
        val dispatch = harness.coordinator.dispatchPlaybackPilot(
            ReaderUiIntent.StartTtsSession(text = "legacy payload must be ignored", requestId = "tts-1")
        ) as ReaderPlaybackPilotDispatch.Applied
        harness.apply(dispatch, this)
        harness.executor.awaitSerialIdle()

        assertEquals(listOf("tts.slice", "tts.queue.play"), harness.client.methods.take(2))
        assertEquals(1L, harness.domain.state.value.speechStartCount)
        assertEquals("tts", harness.domain.state.value.activeSession)
        val utteranceId = requireNotNull(harness.domain.state.value.activeUtteranceId)
        assertTrue(utteranceId.startsWith("tts-1:slice:0:"))

        harness.speech.complete(utteranceId)
        eventually { harness.client.methods.contains("tts.queue.stop") }
        harness.executor.awaitSerialIdle()
        assertEquals(
            listOf(
                "tts.slice",
                "tts.queue.play",
                "tts.queue.report-status",
                "tts.queue.next",
                "tts.queue.stop"
            ),
            harness.client.methods
        )
        assertEquals("done", harness.client.params
            .single { it.first == "tts.queue.report-status" }.second.getString("status"))
        assertEquals(1, harness.client.maxConcurrent.get())
        assertEquals(null, harness.domain.state.value.activeSession)
        harness.executor.cancelAll()
    }

    @Test
    fun `TTS stop invalidates utterance completion before Core teardown and discards late callback`() = runBlocking {
        val harness = Harness()
        harness.apply(
            harness.coordinator.dispatchPlaybackPilot(
                ReaderUiIntent.StartTtsSession(text = "ignored", requestId = "tts-stop")
            ) as ReaderPlaybackPilotDispatch.Applied,
            this
        )
        harness.executor.awaitSerialIdle()
        val utteranceId = requireNotNull(harness.domain.state.value.activeUtteranceId)

        harness.apply(
            harness.coordinator.dispatchPlaybackPilot(ReaderUiIntent.StopSession)
                as ReaderPlaybackPilotDispatch.Applied,
            this
        )
        harness.executor.awaitSerialIdle()
        harness.speech.complete(utteranceId)
        kotlinx.coroutines.delay(50)

        assertEquals(listOf("tts.slice", "tts.queue.play", "tts.queue.stop"), harness.client.methods)
        assertFalse(harness.client.methods.contains("tts.queue.report-status"))
        assertEquals(null, harness.domain.state.value.activeSession)
        val trace = harness.domain.state.value.effectTrace
        assertOrdered(
            trace,
            "terminal:tts.system.stop:tts-stop",
            "start:tts.queue.stop:tts-stop"
        )
        harness.executor.cancelAll()
    }

    @Test
    fun `TTS replacement by auto page awaits system and Core stop before timer arm`() = runBlocking {
        val harness = Harness()
        harness.apply(
            harness.coordinator.dispatchPlaybackPilot(
                ReaderUiIntent.StartTtsSession(text = "ignored", requestId = "tts-old")
            ) as ReaderPlaybackPilotDispatch.Applied,
            this
        )
        harness.executor.awaitSerialIdle()

        harness.apply(
            harness.coordinator.dispatchPlaybackPilot(ReaderUiIntent.StartAutoPageSession())
                as ReaderPlaybackPilotDispatch.Applied,
            this
        )
        harness.executor.awaitSerialIdle()

        val trace = harness.domain.state.value.effectTrace
        assertOrdered(
            trace,
            "terminal:tts.system.stop:tts-old",
            "start:tts.queue.stop:tts-old",
            "terminal:tts.queue.stop:tts-old",
            "start:timer.foreground.arm:"
        )
        assertEquals(1, harness.client.maxConcurrent.get())
        assertEquals("auto-page", harness.domain.state.value.activeSession)
        harness.executor.cancelAll()
    }

    @Test
    fun `auto page replacement by TTS cancels timer terminal before plan starts`() = runBlocking {
        val harness = Harness()
        val auto = harness.coordinator.dispatchPlaybackPilot(ReaderUiIntent.StartAutoPageSession())
            as ReaderPlaybackPilotDispatch.Applied
        harness.apply(auto, this)
        harness.executor.awaitSerialIdle()
        val autoCorrelation = requireNotNull(auto.state.autoPageTransaction?.correlationId)

        harness.apply(
            harness.coordinator.dispatchPlaybackPilot(
                ReaderUiIntent.StartTtsSession(text = "ignored", requestId = "tts-new")
            ) as ReaderPlaybackPilotDispatch.Applied,
            this
        )
        harness.executor.awaitSerialIdle()

        val trace = harness.domain.state.value.effectTrace
        assertOrdered(
            trace,
            "terminal:timer.foreground.cancel:$autoCorrelation",
            "start:tts.queue.plan:tts-new"
        )
        assertEquals(1, harness.client.maxConcurrent.get())
        assertEquals("tts", harness.domain.state.value.activeSession)
        harness.executor.cancelAll()
    }

    @Test
    fun `foreground one shot timer enters canonical page path rearms only after commit and background cancels`() = runBlocking {
        val auto = ReaderUIAutoPageTransaction(
            correlationId = "auto-fast",
            intervalMs = 250,
            generation = 9
        )
        val runtimeState = ReaderUIState(
            routeId = "immersive-reading",
            activeSession = "auto-page",
            autoPageTransaction = auto,
            playbackGeneration = 9
        )
        val coordinator = ReaderUiRuntimeCoordinator(
            runtime = ReaderUIRuntime(runtimeState),
            directoryPilotEnabled = false,
            bookOpenPilotEnabled = false,
            playbackPilotEnabled = true
        )
        val domain = ReaderPlaybackDomainStore()
        val client = FakeCommandClient()
        val executor = ReaderPlaybackEffectExecutor(
            domainStore = domain,
            runtime = coordinator,
            commandClientFactory = { client },
            speechEngineFactory = { FakeSpeechEngine() }
        )
        executor.applyTransition(
            event = "reader.autoPage.start",
            state = runtimeState,
            effects = listOf(timerEffect(auto)),
            cancelledCorrelationIds = emptyList(),
            bookOpen = readyBookState(),
            scope = this
        )
        executor.awaitSerialIdle()
        assertEquals(1L, domain.state.value.timerArmCount)

        eventually(timeoutMs = 1_000) { domain.state.value.pageCorrelationId != null }
        val pageCorrelation = requireNotNull(domain.state.value.pageCorrelationId)
        executor.onPageLayoutReady(
            ReaderPlaybackPageMeasurement(
                correlationId = pageCorrelation,
                contentGeneration = 1,
                contentLength = CONTENT.length,
                direction = "next",
                anchor = "chapter:7:char-offset:4",
                targetPageIndex = 1,
                chapterIndex = 7,
                chapterOffset = 4,
                chapterProgress = 0.5,
                viewportWidth = 1080,
                viewportHeight = 1920,
                fontScale = 1.0
            ),
            this
        )
        executor.awaitSerialIdle()
        assertEquals(1, domain.state.value.committedPageIndex)
        assertEquals(2L, domain.state.value.timerArmCount)

        executor.onAppBackgrounded(this)
        executor.awaitSerialIdle()
        assertEquals(null, domain.state.value.activeSession)
        assertTrue(domain.state.value.effectTrace.any {
            it == "terminal:timer.foreground.cancel:auto-fast"
        })
        kotlinx.coroutines.delay(300)
        assertEquals(null, domain.state.value.pageCorrelationId)
        executor.cancelAll()
    }

    @Test
    fun `background queued during auto page progress commit prevents stale timer rearm`() = runBlocking {
        val auto = ReaderUIAutoPageTransaction(
            correlationId = "auto-background-race",
            intervalMs = 250,
            generation = 11
        )
        val runtimeState = ReaderUIState(
            routeId = "immersive-reading",
            activeSession = "auto-page",
            autoPageTransaction = auto,
            playbackGeneration = 11
        )
        val coordinator = ReaderUiRuntimeCoordinator(
            runtime = ReaderUIRuntime(runtimeState),
            directoryPilotEnabled = false,
            bookOpenPilotEnabled = false,
            playbackPilotEnabled = true
        )
        val domain = ReaderPlaybackDomainStore()
        val progressDeferred = CompletableDeferred<JSONObject>()
        val client = FakeCommandClient(progressDeferred = progressDeferred)
        val executor = ReaderPlaybackEffectExecutor(
            domainStore = domain,
            runtime = coordinator,
            commandClientFactory = { client },
            speechEngineFactory = { FakeSpeechEngine() },
            progressMirror = ReaderPlaybackProgressMirror { }
        )
        executor.applyTransition(
            event = "reader.autoPage.start",
            state = runtimeState,
            effects = listOf(timerEffect(auto)),
            cancelledCorrelationIds = emptyList(),
            bookOpen = readyBookState(),
            scope = this
        )
        eventually(timeoutMs = 1_000) { domain.state.value.pageCorrelationId != null }
        val pageCorrelation = requireNotNull(domain.state.value.pageCorrelationId)
        executor.onPageLayoutReady(
            ReaderPlaybackPageMeasurement(
                pageCorrelation, 1, CONTENT.length, "next", "chapter:7:char-offset:4",
                1, 7, 4, 0.5, 1080, 1920, 1.0
            ),
            this
        )
        eventually { client.methods.contains("reading.progress.update") }

        executor.onAppBackgrounded(this)
        val params = client.params.single { it.first == "reading.progress.update" }.second
        progressDeferred.complete(progressResult(params))
        executor.awaitSerialIdle()

        assertEquals(1, domain.state.value.committedPageIndex)
        assertEquals(null, domain.state.value.activeSession)
        assertEquals(1L, domain.state.value.timerArmCount)
        kotlinx.coroutines.delay(300)
        assertEquals(null, domain.state.value.pageCorrelationId)
        executor.cancelAll()
    }

    @Test
    fun `paired Pilot bypasses native reducer and legacy HostRequest queue`() {
        val runtime = ReaderUiRuntimeCoordinator(
            runtime = ReaderUIRuntime(ReaderUIState(routeId = "immersive-reading")),
            directoryPilotEnabled = false,
            bookOpenPilotEnabled = false,
            playbackPilotEnabled = true
        )
        val bookStore = readyBookStore()
        val domain = ReaderPlaybackDomainStore()
        var executorCalls = 0
        val executor = object : ReaderPlaybackEffectExecutor(
            domainStore = domain,
            runtime = runtime,
            commandClientFactory = { FakeCommandClient() },
            speechEngineFactory = { FakeSpeechEngine() }
        ) {
            override fun applyTransition(
                event: String,
                state: ReaderUIState,
                effects: List<io.reader.ui.runtime.ReaderUIEffect>,
                cancelledCorrelationIds: List<String>,
                bookOpen: ReaderBookOpenDomainState,
                scope: kotlinx.coroutines.CoroutineScope
            ) {
                executorCalls += 1
            }
        }
        var nativeReducerCalls = 0
        val vm = AppShellViewModel(
            readerUiRuntimeCoordinator = runtime,
            nativeReducer = { state, _ -> nativeReducerCalls += 1; state },
            readerBookOpenDomainStore = bookStore,
            readerPlaybackDomainStore = domain,
            readerPlaybackEffectExecutor = executor
        )

        vm.dispatch(ReaderUiIntent.StartTtsSession(text = "legacy", requestId = "paired"))

        assertEquals(1, executorCalls)
        assertEquals(0, nativeReducerCalls)
        assertTrue(vm.state.value.pendingHostRequests.isEmpty())
    }

    private class Harness(
        val locationDeferred: CompletableDeferred<JSONObject>? = null,
        val progressDeferred: CompletableDeferred<JSONObject>? = null,
        progressMirror: ReaderPlaybackProgressMirror = ReaderPlaybackProgressMirror { }
    ) {
        val coordinator = ReaderUiRuntimeCoordinator(
            runtime = ReaderUIRuntime(ReaderUIState(routeId = "immersive-reading")),
            directoryPilotEnabled = false,
            bookOpenPilotEnabled = false,
            playbackPilotEnabled = true
        )
        val domain = ReaderPlaybackDomainStore()
        val client = FakeCommandClient(locationDeferred, progressDeferred)
        val speech = FakeSpeechEngine()
        val executor = ReaderPlaybackEffectExecutor(
            domainStore = domain,
            runtime = coordinator,
            commandClientFactory = { client },
            speechEngineFactory = { speech },
            progressMirror = progressMirror
        )
        private val bookOpen = readyBookState()

        fun apply(
            dispatch: ReaderPlaybackPilotDispatch.Applied,
            scope: kotlinx.coroutines.CoroutineScope
        ) {
            executor.applyTransition(
                dispatch.event,
                dispatch.state,
                dispatch.effects,
                dispatch.cancelledCorrelationIds,
                bookOpen,
                scope
            )
        }
    }

    private class FakeCommandClient(
        private val locationDeferred: CompletableDeferred<JSONObject>? = null,
        private val progressDeferred: CompletableDeferred<JSONObject>? = null
    ) : ReaderCorePlaybackCommandClient {
        val methods = mutableListOf<String>()
        val params = mutableListOf<Pair<String, JSONObject>>()
        val concurrent = AtomicInteger(0)
        val maxConcurrent = AtomicInteger(0)
        val cancelledMethods = mutableListOf<String>()
        private val requestIds = AtomicInteger(0)

        override fun beginCommand(method: String, params: JSONObject): ReaderPlaybackCommandHandle {
            synchronized(this) {
                methods += method
                this.params += method to JSONObject(params.toString())
            }
            return object : ReaderPlaybackCommandHandle {
                override val requestId: Long = requestIds.incrementAndGet().toLong()
                override suspend fun await(): JSONObject {
                    val now = concurrent.incrementAndGet()
                    maxConcurrent.updateAndGet { previous -> maxOf(previous, now) }
                    return try {
                        when (method) {
                            "reader.location.resolve" -> locationDeferred?.await()
                                ?: locationResult(offset = 4, progress = 0.5)
                            "reading.progress.update" -> progressDeferred?.await()
                                ?: progressResult(params)
                            "tts.slice" -> ttsPlanResult()
                            "tts.queue.play" -> queueSnapshot("playing", 0, 0)
                            "tts.queue.report-status" -> queueSnapshot("playing", 0, 1)
                            "tts.queue.next" -> queueSnapshot("completed", null, 1)
                            "tts.queue.stop" -> queueSnapshot("stopped", 0, 1)
                            else -> error("unexpected method=$method")
                        }
                    } finally {
                        concurrent.decrementAndGet()
                    }
                }
                override fun cancel(): Boolean {
                    synchronized(this@FakeCommandClient) { cancelledMethods += method }
                    if (method == "reader.location.resolve") locationDeferred?.cancel()
                    if (method == "reading.progress.update") progressDeferred?.cancel()
                    return true
                }
            }
        }
    }

    private class FakeSpeechEngine : ReaderPlaybackSpeechEngine {
        private val completions = mutableMapOf<String, CompletableDeferred<Unit>>()
        override suspend fun init(): Result<Unit> = Result.success(Unit)
        override fun begin(utterance: com.reader.android.data.adapter.TtsUtterance): ReaderPlaybackSpeechHandle {
            val completion = CompletableDeferred<Unit>()
            synchronized(this) { completions[utterance.utteranceId] = completion }
            return object : ReaderPlaybackSpeechHandle {
                override val utteranceId: String = utterance.utteranceId
                override suspend fun awaitStarted() = Unit
                override suspend fun awaitCompletion() = completion.await()
                override fun cancel(): Boolean = completion.completeExceptionally(
                    CancellationException("cancelled $utteranceId")
                )
            }
        }
        override suspend fun stop() = Unit
        fun complete(utteranceId: String) {
            synchronized(this) { completions[utteranceId] }?.complete(Unit)
        }
    }

    companion object {
        private const val CONTENT = "第一段。第二段。"

        private fun readyBookState(): ReaderBookOpenDomainState = ReaderBookOpenDomainState(
            displayedCorrelationId = "book-open",
            sourceId = "source-1",
            bookId = "book-1",
            bookName = "Book",
            book = Book(bookUrl = "book-1", name = "Book", sourceId = "source-1"),
            chapters = listOf(Chapter("Chapter", "https://chapter/7", 7)),
            selectedChapterPosition = 0,
            content = CONTENT,
            contentLoaded = true,
            contentGeneration = 1,
            stage = "completed",
            loading = false,
            locationResolved = true,
            canonicalLocation = canonicalLocation(offset = 0, progress = 0.0)
        )

        private fun readyBookStore(): ReaderBookOpenDomainStore = ReaderBookOpenDomainStore().apply {
            val context = ReaderContext(
                sourceId = "source-1",
                bookUrl = "book-1",
                bookName = "Book",
                entry = ReaderEntry.COVER_TO_IMMERSIVE,
                entryRequestId = "book-open"
            )
            begin(context)
            recordDetail(
                "book-open",
                BookDetailResult(
                    "source-1",
                    Book(bookUrl = "book-1", name = "Book", sourceId = "source-1"),
                    "",
                    emptyMap()
                )
            )
            recordToc(
                "book-open",
                BookTocResult("source-1", "book-1", listOf(Chapter("Chapter", "https://chapter/7", 7)))
            )
            selectChapter("book-open", 0)
            recordContent("book-open", CONTENT)
            recordCanonicalLocation("book-open", canonicalLocation(offset = 0, progress = 0.0))
            complete("book-open")
        }

        private fun canonicalLocation(offset: Long, progress: Double) = ReaderBookOpenCanonicalLocation(
            bookId = "book-1",
            chapterIndex = 7,
            chapterOffset = offset,
            chapterProgress = progress,
            locationRevision = "rev-$offset",
            resolverVersion = "reader.location.resolve.v1.reflow",
            reflowStrategy = "char-offset",
            reflowPrimaryAnchor = "char-offset",
            reflowFallbackAnchor = "progress",
            reflowLayoutIndependent = true
        )

        private fun locationResult(offset: Long, progress: Double) = JSONObject()
            .put("resolved", true)
            .put("canonicalLocation", JSONObject()
                .put("bookId", "book-1")
                .put("chapterIndex", 7)
                .put("chapterOffset", offset)
                .put("chapterProgress", progress)
                .put("locationRevision", "rev-$offset"))
            .put("resolverVersion", "reader.location.resolve.v1.reflow")
            .put("reflow", JSONObject()
                .put("strategy", "char-offset")
                .put("primaryAnchor", "char-offset")
                .put("fallbackAnchor", "progress")
                .put("layoutIndependent", true))

        private fun progressResult(params: JSONObject): JSONObject = JSONObject()
            .put("sourceId", params.getString("sourceId"))
            .put("bookId", params.getString("bookId"))
            .put("updatedAt", params.getLong("updatedAt"))
            .put("chapterIndex", params.getInt("chapterIndex"))
            .put("chapterOffset", params.getLong("chapterOffset"))
            .put("chapterProgress", params.getDouble("chapterProgress"))
            .put("locationRevision", params.getString("locationRevision"))
            .put("stored", true)

        private fun ttsPlanResult(): JSONObject {
            val plan = JSONObject()
                .put("chapter", chapterJson())
                .put("strategy", "paragraph")
                .put("slices", JSONArray().put(JSONObject()
                    .put("index", 0)
                    .put("text", CONTENT)
                    .put("charStart", 0)
                    .put("charEnd", CONTENT.length)
                    .put("paragraphIndex", 0)))
                .put("sourceCharCount", CONTENT.codePointCount(0, CONTENT.length))
            return JSONObject().put("plan", plan)
        }

        private fun queueSnapshot(state: String, current: Int?, completed: Int): JSONObject =
            JSONObject().put("snapshot", JSONObject()
                .put("state", state)
                .put("currentSliceIndex", current ?: JSONObject.NULL)
                .put("totalSlices", 1)
                .put("completedSlices", completed)
                .put("chapter", chapterJson()))

        private fun chapterJson(): JSONObject = JSONObject()
            .put("sourceId", "source-1")
            .put("bookId", "book-1")
            .put("chapterIndex", 7)
            .put("chapterTitle", "Chapter")
            .put("chapterUrl", "https://chapter/7")

        private fun timerEffect(auto: ReaderUIAutoPageTransaction): ReaderUIEffect = ReaderUIEffect(
            kind = ReaderUIEffectKind.HOST,
            type = "timer.foreground.arm",
            payload = mapOf(
                "timerId" to auto.correlationId,
                "correlationId" to auto.correlationId,
                "delayMs" to auto.intervalMs.toString(),
                "generation" to auto.generation.toString(),
                "oneShot" to "true",
                "foregroundOnly" to "true"
            ),
            correlationId = auto.correlationId
        )

        private fun assertOrdered(trace: List<String>, vararg markers: String) {
            var previous = -1
            markers.forEach { marker ->
                val index = trace.indexOfFirst { it == marker || it.startsWith(marker) }
                assertTrue("missing trace marker=$marker in $trace", index >= 0)
                assertTrue("out of order marker=$marker in $trace", index > previous)
                previous = index
            }
        }

        private suspend fun eventually(timeoutMs: Long = 2_000, condition: () -> Boolean) {
            val deadline = System.currentTimeMillis() + timeoutMs
            while (!condition() && System.currentTimeMillis() < deadline) kotlinx.coroutines.delay(10)
            assertTrue("condition not reached within ${timeoutMs}ms", condition())
        }
    }
}
