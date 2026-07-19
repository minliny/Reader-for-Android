package com.reader.ui.shell

import com.reader.android.BuildConfig
import io.reader.ui.runtime.GeneratedRuntimeActions
import io.reader.ui.runtime.ReaderUIEffect
import io.reader.ui.runtime.ReaderUIEffectKind
import io.reader.ui.runtime.ReaderUIRuntime
import io.reader.ui.runtime.ReaderUIRuntimeException
import io.reader.ui.runtime.ReaderUIState
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderUiRuntimeCoordinatorTest {

    @Test
    fun `production path keeps one runtime while only directory pair is Pilot`() {
        // book.open is explicitly in shadow rollback mode here; the Pilot path
        // is proven by the dedicated book.open pilot parity tests below.
        // playbackPilotEnabled=false retains the R7 live-shadow path for the
        // TTS/auto-page assertions; the playback Pilot path is proven by the
        // dedicated playback pilot parity tests below.
        val coordinator = ReaderUiRuntimeCoordinator(
            bookOpenPilotEnabled = false,
            playbackPilotEnabled = false
        )
        val nativeReducerCalls = AtomicInteger()
        val vm = AppShellViewModel(
            readerUiRuntimeCoordinator = coordinator,
            nativeReducer = { state, intent ->
                nativeReducerCalls.incrementAndGet()
                ReaderUiReducer.reduce(state, intent)
            }
        )

        vm.dispatch(
            ReaderUiIntent.EnterReaderFromAction(
                sourceId = "source-1",
                bookUrl = "book-1",
                bookName = "Book One",
                requestId = "open-1"
            )
        )

        // book.open remains native production + runtime shadow.
        assertEquals(RouteIds.IMMERSIVE_READING, vm.state.value.currentRoute.routeId)
        assertEquals("book-1", vm.state.value.readerContext?.bookUrl)
        // ReaderUIRuntime 2.5 starts the transaction at the canonical reader
        // route (2.3's transient `book-detail` expectation is obsolete).
        assertEquals(RouteIds.IMMERSIVE_READING, vm.readerUiRuntimeShadowState.routeId)
        assertTrue(vm.readerUiRuntimeShadowState.loading)
        assertEquals(1, nativeReducerCalls.get())

        val context = requireNotNull(vm.state.value.readerContext)
        vm.dispatch(
            ReaderUiIntent.PushRoute(
                route = ReaderRoute.ReaderControl(
                    id = RouteIds.READER_TOC_BOOKMARKS,
                    context = context
                ),
                requestId = "directory-open-1"
            )
        )
        assertEquals(RouteIds.READER_TOC_BOOKMARKS, vm.state.value.currentRoute.routeId)
        assertEquals("directory", vm.readerUiRuntimeShadowState.overlay)
        assertEquals(ReaderUiRuntimeDispatchMode.PILOT, vm.readerUiRuntimeShadowObservation?.mode)
        // Runtime + narrow projector handled the semantic event; reducer did not.
        assertEquals(1, nativeReducerCalls.get())

        // This is the same PopRoute emitted by the production Back handler.
        vm.dispatch(ReaderUiIntent.PopRoute)
        assertEquals(RouteIds.IMMERSIVE_READING, vm.state.value.currentRoute.routeId)
        assertEquals(null, vm.readerUiRuntimeShadowState.overlay)
        assertEquals(1, nativeReducerCalls.get())

        vm.dispatch(
            ReaderUiIntent.StartTtsSession(
                text = "runtime shadow text",
                chapterTitle = "Chapter One",
                chapterIndex = 0,
                requestId = "tts-1"
            )
        )
        assertEquals(SessionType.TTS, vm.state.value.activeSession?.type)
        assertEquals(1, vm.state.value.pendingHostRequests.size)
        val ttsObservation = requireNotNull(vm.readerUiRuntimeShadowObservation)
        assertEquals(ReaderUiRuntimeDispatchMode.SHADOW, ttsObservation.mode)
        assertEquals(
            // New pending semantics: Runtime emits only Core plan here; the
            // system TTS Host effect is result-dependent after queue start.
            listOf(ReaderUIEffectKind.CORE),
            ttsObservation.runtimeEffects.map { it.kind }
        )
        assertEquals("awaiting-plan", ttsObservation.runtimeState?.ttsTransaction?.stage)

        vm.dispatch(ReaderUiIntent.StartAutoPageSession())
        assertEquals(SessionType.AUTO_PAGE, vm.state.value.activeSession?.type)
        assertEquals("auto-page", vm.readerUiRuntimeShadowState.activeSession)
        assertEquals(1, vm.state.value.pendingHostRequests.size)
        assertEquals(3, nativeReducerCalls.get())

        assertEquals(
            ReaderUiRuntimeShadowMetrics(
                covered = 5,
                fallback = 0,
                runtimeError = 0,
                mismatch = 3
            ),
            vm.readerUiRuntimeShadowMetrics
        )
    }

    @Test
    fun `continuous directory routes use Pilot for semantics and native once for presentation-only pop`() {
        val nativeReducerCalls = AtomicInteger()
        val vm = AppShellViewModel(
            readerUiRuntimeCoordinator = ReaderUiRuntimeCoordinator(),
            nativeReducer = { state, intent ->
                nativeReducerCalls.incrementAndGet()
                ReaderUiReducer.reduce(state, intent)
            }
        )

        vm.dispatch(
            ReaderUiIntent.PushRoute(
                ReaderRoute.ReaderControl(id = RouteIds.READER_TOC_BOOKMARKS),
                requestId = "directory-quick"
            )
        )
        vm.dispatch(
            ReaderUiIntent.PushRoute(
                ReaderRoute.ReaderControl(id = RouteIds.READER_FULL_DIRECTORY),
                requestId = "directory-full"
            )
        )
        assertEquals("directory", vm.readerUiRuntimeShadowState.overlay)
        assertEquals(0, nativeReducerCalls.get())

        // full-directory -> quick-directory is only native presentation. It is
        // not reader.directory.close and therefore falls back exactly once.
        vm.dispatch(ReaderUiIntent.PopRoute)
        assertEquals(RouteIds.READER_TOC_BOOKMARKS, vm.state.value.currentRoute.routeId)
        assertEquals("directory", vm.readerUiRuntimeShadowState.overlay)
        assertEquals(1, nativeReducerCalls.get())

        vm.dispatch(ReaderUiIntent.PopRoute)
        assertEquals(MainTab.BOOKSHELF.routeId, vm.state.value.currentRoute.routeId)
        assertEquals(null, vm.readerUiRuntimeShadowState.overlay)
        assertEquals(1, nativeReducerCalls.get())
        assertEquals(
            ReaderUiRuntimeShadowMetrics(covered = 3, fallback = 1),
            vm.readerUiRuntimeShadowMetrics
        )
    }

    @Test
    fun `overlay replacement makes actual Back close a no-op without native fallback`() {
        val runtime = ReaderUIRuntime()
        val nativeReducerCalls = AtomicInteger()
        val coordinator = ReaderUiRuntimeCoordinator(runtime = runtime)
        val vm = AppShellViewModel(
            readerUiRuntimeCoordinator = coordinator,
            nativeReducer = { state, intent ->
                nativeReducerCalls.incrementAndGet()
                ReaderUiReducer.reduce(state, intent)
            }
        )
        vm.dispatch(
            ReaderUiIntent.PushRoute(
                ReaderRoute.ReaderControl(id = RouteIds.READER_TOC_BOOKMARKS),
                requestId = "directory-open"
            )
        )
        val productionBeforeClose = vm.state.value

        // Test-only setup mirrors the generated runtime protocol fixture; no
        // platform-local replacement event is added to the production allowlist.
        runtime.dispatch("overlay.sheet.open")
        vm.dispatch(ReaderUiIntent.PopRoute)

        assertEquals("sheet", runtime.state.overlay)
        assertEquals(productionBeforeClose, vm.state.value)
        assertEquals(0, nativeReducerCalls.get())
        assertEquals(ReaderUiRuntimeDispatchMode.PILOT, vm.readerUiRuntimeShadowObservation?.mode)
        assertTrue(vm.readerUiRuntimeShadowObservation?.mismatches.orEmpty().isEmpty())
    }

    @Test
    fun `directory runtime failure fails closed before reducer motion or projection`() {
        val runtime = ReaderUIRuntime()
        val nativeReducerCalls = AtomicInteger()
        val coordinator = ReaderUiRuntimeCoordinator(
            runtime = runtime,
            runtimeDispatchOverride = { event, payload, correlationId ->
                if (event == "reader.directory.open") {
                    throw ReaderUIRuntimeException("TEST_PILOT_FAILURE", "injected directory failure")
                }
                runtime.dispatchJSON(event, payload, correlationId)
            }
        )
        val vm = AppShellViewModel(
            readerUiRuntimeCoordinator = coordinator,
            nativeReducer = { state, intent ->
                nativeReducerCalls.incrementAndGet()
                ReaderUiReducer.reduce(state, intent)
            }
        )
        val productionBefore = vm.state.value

        vm.dispatch(
            ReaderUiIntent.PushRoute(
                ReaderRoute.ReaderControl(id = RouteIds.READER_TOC_BOOKMARKS),
                requestId = "directory-failure"
            )
        )

        assertEquals(productionBefore, vm.state.value)
        assertEquals(null, runtime.state.overlay)
        assertEquals(0, nativeReducerCalls.get())
        assertEquals(
            ReaderUiRuntimeShadowMetrics(covered = 1, runtimeError = 1),
            vm.readerUiRuntimeShadowMetrics
        )
        assertEquals("TEST_PILOT_FAILURE", vm.readerUiRuntimeShadowObservation?.runtimeErrorCode)
        assertEquals(ReaderUiRuntimeDispatchMode.PILOT, vm.readerUiRuntimeShadowObservation?.mode)
    }

    @Test
    fun `book open effect boundary failure clears the Runtime transaction`() {
        val runtime = ReaderUIRuntime()
        val coordinator = ReaderUiRuntimeCoordinator(
            runtime = runtime,
            bookOpenPilotEnabled = true,
            runtimeDispatchOverride = { event, payload, correlationId ->
                val transition = runtime.dispatchJSON(event, payload, correlationId)
                // Simulate a corrupted post-dispatch boundary: Runtime has
                // already installed bookOpenTransaction, but the handoff no
                // longer contains its required one next Core effect.
                if (event == "book.open") transition.copy(effects = emptyList()) else transition
            }
        )

        val result = coordinator.dispatchBookOpenPilot(
            ReaderUiIntent.EnterReaderFromAction(
                sourceId = "source-1",
                bookUrl = "book-1",
                bookName = "Book One",
                requestId = "boundary-open"
            )
        )

        assertEquals(ReaderBookOpenPilotDispatch.FailedClosed, result)
        assertNull("failed admission must not leave an orphan ledger", runtime.state.bookOpenTransaction)
        assertEquals(MainTab.BOOKSHELF.routeId, runtime.state.routeId)
        assertEquals("BOOK_OPEN_EFFECT_BOUNDARY", coordinator.lastObservation?.runtimeErrorCode)
    }

    @Test
    fun `directory sheet entry and close are projected by Pilot without reducer`() {
        val nativeReducerCalls = AtomicInteger()
        val vm = AppShellViewModel(
            readerUiRuntimeCoordinator = ReaderUiRuntimeCoordinator(),
            nativeReducer = { state, intent ->
                nativeReducerCalls.incrementAndGet()
                ReaderUiReducer.reduce(state, intent)
            }
        )

        vm.dispatch(
            ReaderUiIntent.OpenSheet(
                SheetContent.ReaderSetting("directory"),
                requestId = "directory-sheet-open"
            )
        )
        assertEquals(
            OverlayState.Sheet(SheetContent.ReaderSetting("directory")),
            vm.state.value.overlayState
        )
        vm.dispatch(ReaderUiIntent.CloseSheet)

        assertEquals(OverlayState.None, vm.state.value.overlayState)
        assertEquals(null, vm.readerUiRuntimeShadowState.overlay)
        assertEquals(0, nativeReducerCalls.get())
    }

    @Test
    fun `rollback toggle restores native reducer plus directory shadow`() {
        val nativeReducerCalls = AtomicInteger()
        val coordinator = ReaderUiRuntimeCoordinator(directoryPilotEnabled = false)
        val vm = AppShellViewModel(
            readerUiRuntimeCoordinator = coordinator,
            nativeReducer = { state, intent ->
                nativeReducerCalls.incrementAndGet()
                ReaderUiReducer.reduce(state, intent)
            }
        )

        vm.dispatch(
            ReaderUiIntent.PushRoute(
                ReaderRoute.ReaderControl(id = RouteIds.READER_TOC_BOOKMARKS),
                requestId = "rollback-directory-open"
            )
        )

        assertTrue(coordinator.pilotEvents.isEmpty())
        assertEquals(1, nativeReducerCalls.get())
        assertEquals(RouteIds.READER_TOC_BOOKMARKS, vm.state.value.currentRoute.routeId)
        assertEquals("directory", vm.readerUiRuntimeShadowState.overlay)
        assertEquals(ReaderUiRuntimeDispatchMode.SHADOW, vm.readerUiRuntimeShadowObservation?.mode)
        assertEquals(ReaderUiRuntimeShadowMetrics(covered = 1), vm.readerUiRuntimeShadowMetrics)
    }

    @Test
    fun `shadow book open supersedes the old correlation after native production commits`() {
        val coordinator = ReaderUiRuntimeCoordinator(bookOpenPilotEnabled = false)
        val vm = AppShellViewModel(readerUiRuntimeCoordinator = coordinator)

        vm.dispatch(
            ReaderUiIntent.EnterReaderFromCover(
                sourceId = "source-a",
                bookUrl = "book-a",
                bookName = "Book A",
                requestId = "open-a"
            )
        )
        vm.dispatch(
            ReaderUiIntent.EnterReaderFromCover(
                sourceId = "source-b",
                bookUrl = "book-b",
                bookName = "Book B",
                requestId = "open-b"
            )
        )

        assertEquals("book-b", vm.state.value.readerContext?.bookUrl)
        assertEquals(RouteIds.IMMERSIVE_READING, vm.state.value.currentRoute.routeId)
        assertEquals(2L, vm.readerUiRuntimeShadowMetrics.covered)
        assertEquals(0L, vm.readerUiRuntimeShadowMetrics.runtimeError)
        assertEquals("open-b", vm.readerUiRuntimeShadowState.bookOpenTransaction?.correlationId)
        assertEquals(null, vm.readerUiRuntimeShadowState.error)
        assertEquals("open-b", vm.readerUiRuntimeShadowObservation?.correlationId)
        assertEquals(listOf("open-a"), vm.readerUiRuntimeShadowObservation?.cancelledCorrelationIds)
        assertEquals(ReaderUiRuntimeDispatchMode.SHADOW, vm.readerUiRuntimeShadowObservation?.mode)
    }

    @Test
    fun `uncovered production intent falls back only to native reducer`() {
        val coordinator = ReaderUiRuntimeCoordinator()
        val vm = AppShellViewModel(readerUiRuntimeCoordinator = coordinator)
        val runtimeBefore = vm.readerUiRuntimeShadowState

        vm.dispatch(ReaderUiIntent.UpdateAppThemeMode(mode = "dark", requestId = "theme-1"))

        assertEquals("dark", vm.state.value.appThemeMode)
        assertEquals(runtimeBefore, vm.readerUiRuntimeShadowState)
        assertEquals(
            ReaderUiRuntimeShadowMetrics(fallback = 1),
            vm.readerUiRuntimeShadowMetrics
        )
    }

    @Test
    fun `consumer lock keeps 35 covered with 7 Pilot and 28 default Shadow events`() {
        val lock = JSONObject(findConsumerLock().readText())
        val expectedReaderUiVersion = verifiedArtifactReaderUiVersion(lock)
        val rollout = lock.getJSONObject("rollout")
        val covered = rollout.getJSONArray("coveredEvents").strings()
        val coordinator = ReaderUiRuntimeCoordinator()

        assertEquals(READER_UI_RUNTIME_COVERED_EVENTS.toList(), covered)
        assertEquals(35, covered.size)
        assertEquals(covered.toSet(), coordinator.coveredEvents)
        assertTrue(coordinator.coveredEvents.all(GeneratedRuntimeActions.byEvent::containsKey))
        assertEquals("shadow", rollout.getString("mode"))

        val cohorts = rollout.getJSONArray("cohorts")
        assertEquals(3, cohorts.length())
        val cohortObjects = (0 until cohorts.length()).map { cohorts.getJSONObject(it) }
        val declaredPilotEvents = cohortObjects
            .filter { it.getString("mode") == "pilot" }
            .flatMap { it.getJSONArray("events").strings() }
            .toSet()
        val declaredShadowEvents = covered.toSet() - declaredPilotEvents
        assertEquals(7, declaredPilotEvents.size)
        assertEquals(28, declaredShadowEvents.size)
        assertEquals(
            READER_UI_RUNTIME_DIRECTORY_PILOT_EVENTS +
                setOf("book.open") +
                (READER_UI_RUNTIME_PLAYBACK_PILOT_EVENTS -
                    setOf("reader.page.next", "reader.page.prev")),
            declaredPilotEvents
        )
        assertEquals(
            READER_UI_RUNTIME_IMPORT_PILOT_EVENTS +
                READER_UI_RUNTIME_SOURCE_SWITCH_PILOT_EVENTS +
                READER_UI_RUNTIME_REPLACE_RULE_PILOT_EVENTS +
                READER_UI_RUNTIME_RSS_PILOT_EVENTS +
                READER_UI_RUNTIME_SYNC_PILOT_EVENTS +
                setOf("reader.page.next", "reader.page.prev"),
            declaredShadowEvents
        )
        assertEquals(
            setOf("reader-directory-pair-pilot", "book-open-pilot", "playback-pilot"),
            cohortObjects.map { it.getString("id") }.toSet()
        )
        val directory = (0 until cohorts.length()).map { cohorts.getJSONObject(it) }
            .first { it.getString("id") == "reader-directory-pair-pilot" }
        assertEquals("pilot", directory.getString("mode"))
        assertEquals("none", directory.getString("effectPolicy"))
        assertTrue(directory.getString("evidence").isNotBlank())
        assertTrue(directory.getString("rollback").contains("readerUiDirectoryPilotEnabled=false"))
        assertEquals(
            READER_UI_RUNTIME_DIRECTORY_PILOT_EVENTS,
            directory.getJSONArray("events").strings().toSet()
        )
        assertEquals(READER_UI_RUNTIME_DIRECTORY_PILOT_EVENTS, coordinator.pilotEvents)
        assertEquals(
            coordinator.coveredEvents.size - coordinator.pilotEvents.size,
            coordinator.coveredEvents.minus(coordinator.pilotEvents).size
        )
        assertTrue(expectedReaderUiVersion.matches(STRICT_SEMVER))
        assertEquals("1.2.0", lock.getString("hostRequestSchemaVersion"))
        assertEquals(2, lock.getInt("runtimeActionsSchemaVersion"))
        assertEquals(
            "0ac249341d8de651314687d8352bc1c3f62d3778371ff500f1f0a025a64be82c",
            lock.getString("runtimeActionsSha256")
        )
    }

    @Test
    fun `book open pilot enabled by default in production build`() {
        assertTrue(BuildConfig.READER_UI_BOOK_OPEN_PILOT_ENABLED)
        val coordinator = ReaderUiRuntimeCoordinator()
        assertTrue(coordinator.bookOpenPilotEnabled)
    }

    @Test
    fun `book open pilot dispatches runtime transaction before native presentation`() {
        val runtime = ReaderUIRuntime()
        val coordinator = ReaderUiRuntimeCoordinator(
            runtime = runtime,
            bookOpenPilotEnabled = true
        )

        val result = coordinator.dispatchBookOpenPilot(
            ReaderUiIntent.EnterReaderFromAction(
                sourceId = "source-1",
                bookUrl = "book-1",
                bookName = "Book One",
                requestId = "pilot-open-1"
            )
        )

        assertTrue(result is ReaderBookOpenPilotDispatch.Applied)
        val transition = (result as ReaderBookOpenPilotDispatch.Applied).transition
        assertEquals("pilot-open-1", transition.state.bookOpenTransaction?.correlationId)
        assertEquals("book.open", coordinator.lastObservation?.event)
        assertEquals(ReaderUiRuntimeDispatchMode.PILOT, coordinator.lastObservation?.mode)
        val effect = transition.effects.single()
        assertEquals(ReaderUIEffectKind.CORE, effect.kind)
        assertEquals("pilot-open-1", effect.correlationId)
    }

    @Test
    fun `book open pilot effect boundary failure clears transaction`() {
        val runtime = ReaderUIRuntime()
        val coordinator = ReaderUiRuntimeCoordinator(
            runtime = runtime,
            bookOpenPilotEnabled = true,
            runtimeDispatchOverride = { event, payload, correlationId ->
                val transition = runtime.dispatchJSON(event, payload, correlationId)
                // Drift the effect correlationId so the single-effect boundary
                // check rejects an otherwise well-formed Runtime admission.
                if (event == "book.open") {
                    val original = transition.effects.single()
                    transition.copy(
                        effects = listOf(original.copy(correlationId = "drifted-correlation"))
                    )
                } else transition
            }
        )

        val result = coordinator.dispatchBookOpenPilot(
            ReaderUiIntent.EnterReaderFromAction(
                sourceId = "source-1",
                bookUrl = "book-1",
                bookName = "Book One",
                requestId = "boundary-pilot-open"
            )
        )

        assertEquals(ReaderBookOpenPilotDispatch.FailedClosed, result)
        assertNull("boundary failure must clear the Runtime ledger", runtime.state.bookOpenTransaction)
        assertEquals(MainTab.BOOKSHELF.routeId, runtime.state.routeId)
        assertEquals("BOOK_OPEN_EFFECT_BOUNDARY", coordinator.lastObservation?.runtimeErrorCode)
        assertEquals(ReaderUiRuntimeDispatchMode.PILOT, coordinator.lastObservation?.mode)
    }

    @Test
    fun `book open pilot stale result guard rejects old correlation`() {
        val runtime = ReaderUIRuntime()
        val coordinator = ReaderUiRuntimeCoordinator(
            runtime = runtime,
            bookOpenPilotEnabled = true
        )

        coordinator.dispatchBookOpenPilot(
            ReaderUiIntent.EnterReaderFromAction(
                sourceId = "source-1",
                bookUrl = "book-1",
                bookName = "Book One",
                requestId = "stale-old"
            )
        )
        assertEquals("stale-old", runtime.state.bookOpenTransaction?.correlationId)

        val secondResult = coordinator.dispatchBookOpenPilot(
            ReaderUiIntent.EnterReaderFromAction(
                sourceId = "source-1",
                bookUrl = "book-2",
                bookName = "Book Two",
                requestId = "stale-new"
            )
        )

        assertTrue(secondResult is ReaderBookOpenPilotDispatch.Applied)
        assertEquals("stale-new", runtime.state.bookOpenTransaction?.correlationId)
        assertEquals(
            listOf("stale-old"),
            (secondResult as ReaderBookOpenPilotDispatch.Applied).transition.cancelledCorrelationIds
        )

        // A stale result for the superseded correlation must not advance the
        // current transaction; the runtime rejects it as out-of-sequence.
        val staleAdvance = coordinator.acceptBookOpenResult(
            coreType = "source.detail",
            correlationId = "stale-old",
            chapterCount = null,
            error = null
        )
        assertFalse(staleAdvance.accepted)
        assertEquals("stale-new", runtime.state.bookOpenTransaction?.correlationId)
    }

    @Test
    fun `book open pilot rollback restores shadow mode`() {
        val nativeReducerCalls = AtomicInteger()
        val coordinator = ReaderUiRuntimeCoordinator(bookOpenPilotEnabled = false)
        val vm = AppShellViewModel(
            readerUiRuntimeCoordinator = coordinator,
            nativeReducer = { state, intent ->
                nativeReducerCalls.incrementAndGet()
                ReaderUiReducer.reduce(state, intent)
            }
        )

        vm.dispatch(
            ReaderUiIntent.EnterReaderFromAction(
                sourceId = "source-1",
                bookUrl = "book-1",
                bookName = "Book One",
                requestId = "rollback-shadow-open"
            )
        )

        assertFalse(coordinator.bookOpenPilotEnabled)
        assertEquals(1, nativeReducerCalls.get())
        assertEquals(RouteIds.IMMERSIVE_READING, vm.state.value.currentRoute.routeId)
        assertEquals("book-1", vm.state.value.readerContext?.bookUrl)
        assertEquals(ReaderUiRuntimeDispatchMode.SHADOW, vm.readerUiRuntimeShadowObservation?.mode)
        assertEquals(
            "rollback-shadow-open",
            vm.readerUiRuntimeShadowState.bookOpenTransaction?.correlationId
        )
    }

    @Test
    fun `book open pilot does not double dispatch native reducer`() {
        val nativeReducerCalls = AtomicInteger()
        val coordinator = ReaderUiRuntimeCoordinator(bookOpenPilotEnabled = true)
        val store = ReaderBookOpenDomainStore()
        val executor = RecordingBookOpenExecutor(store, coordinator)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
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

        try {
            vm.dispatch(
                ReaderUiIntent.EnterReaderFromAction(
                    sourceId = "source-1",
                    bookUrl = "book-1",
                    bookName = "Book One",
                    requestId = "pilot-once-open"
                )
            )

            // Pilot path calls nativeReducer exactly once for presentation;
            // it does not also observe book.open through the shadow path.
            assertEquals(1, nativeReducerCalls.get())
            assertEquals(RouteIds.IMMERSIVE_READING, vm.state.value.currentRoute.routeId)
            assertEquals("book-1", vm.state.value.readerContext?.bookUrl)
            assertEquals(ReaderUiRuntimeDispatchMode.PILOT, vm.readerUiRuntimeShadowObservation?.mode)
            assertEquals(
                "pilot-once-open",
                coordinator.runtimeState.bookOpenTransaction?.correlationId
            )
            assertEquals(1, executor.entries.size)
            assertEquals(ReaderUIEffectKind.CORE, executor.entries.single().kind)
        } finally {
            executor.cancel("pilot-once-open")
            scope.cancel()
        }
    }

    @Test
    fun `playback pilot enabled by default in production build`() {
        assertTrue(BuildConfig.READER_UI_PLAYBACK_PILOT_ENABLED)
        val coordinator = ReaderUiRuntimeCoordinator()
        assertTrue(coordinator.playbackPilotEnabled)
    }

    @Test
    fun `import remains default Shadow in production build`() {
        assertFalse(BuildConfig.READER_UI_IMPORT_PILOT_ENABLED)
        val coordinator = ReaderUiRuntimeCoordinator()
        assertFalse(coordinator.importPilotEnabled)
    }

    @Test
    fun `source switch remains default Shadow in production build`() {
        assertFalse(BuildConfig.READER_UI_SOURCE_SWITCH_PILOT_ENABLED)
        val coordinator = ReaderUiRuntimeCoordinator()
        assertFalse(coordinator.sourceSwitchPilotEnabled)
    }

    @Test
    fun `replace rule remains default Shadow in production build`() {
        assertFalse(BuildConfig.READER_UI_REPLACE_RULE_PILOT_ENABLED)
        val coordinator = ReaderUiRuntimeCoordinator()
        assertFalse(coordinator.replaceRulePilotEnabled)
    }

    @Test
    fun `sync remains default Shadow in production build`() {
        assertFalse(BuildConfig.READER_UI_SYNC_PILOT_ENABLED)
        val coordinator = ReaderUiRuntimeCoordinator()
        assertFalse(coordinator.syncPilotEnabled)
        assertEquals(
            ReaderSyncPilotDispatch.NotEnabled,
            coordinator.dispatchSyncPilot(
                event = "sync.run",
                payload = emptyMap(),
                correlationId = "sync-shadow-default"
            )
        )
    }

    @Test
    fun `tts start pilot dispatches runtime transaction`() {
        val runtime = ReaderUIRuntime(ReaderUIState(routeId = "immersive-reading"))
        val coordinator = ReaderUiRuntimeCoordinator(
            runtime = runtime,
            directoryPilotEnabled = false,
            bookOpenPilotEnabled = false,
            playbackPilotEnabled = true
        )

        val result = coordinator.dispatchPlaybackPilot(
            ReaderUiIntent.StartTtsSession(text = "ignored", requestId = "tts-pilot-start")
        )

        assertTrue(result is ReaderPlaybackPilotDispatch.Applied)
        val applied = result as ReaderPlaybackPilotDispatch.Applied
        assertEquals("reader.tts.start", applied.event)
        assertEquals("tts-pilot-start", applied.state.ttsTransaction?.correlationId)
        assertEquals("reader.tts.start", coordinator.lastObservation?.event)
        assertEquals(ReaderUiRuntimeDispatchMode.PILOT, coordinator.lastObservation?.mode)
    }

    @Test
    fun `tts stop pilot clears transaction`() {
        val runtime = ReaderUIRuntime(ReaderUIState(routeId = "immersive-reading"))
        val coordinator = ReaderUiRuntimeCoordinator(
            runtime = runtime,
            directoryPilotEnabled = false,
            bookOpenPilotEnabled = false,
            playbackPilotEnabled = true
        )

        coordinator.dispatchPlaybackPilot(
            ReaderUiIntent.StartTtsSession(text = "ignored", requestId = "tts-pilot-stop")
        )
        assertNotNull(runtime.state.ttsTransaction)

        val result = coordinator.dispatchPlaybackPilot(ReaderUiIntent.StopSession)

        assertTrue(result is ReaderPlaybackPilotDispatch.Applied)
        val applied = result as ReaderPlaybackPilotDispatch.Applied
        assertEquals("reader.tts.stop", applied.event)
        assertNull(applied.state.ttsTransaction)
        assertEquals("reader.tts.stop", coordinator.lastObservation?.event)
        assertEquals(ReaderUiRuntimeDispatchMode.PILOT, coordinator.lastObservation?.mode)
    }

    @Test
    fun `auto page start pilot dispatches runtime transaction`() {
        val runtime = ReaderUIRuntime(ReaderUIState(routeId = "immersive-reading"))
        val coordinator = ReaderUiRuntimeCoordinator(
            runtime = runtime,
            directoryPilotEnabled = false,
            bookOpenPilotEnabled = false,
            playbackPilotEnabled = true
        )

        val result = coordinator.dispatchPlaybackPilot(ReaderUiIntent.StartAutoPageSession())

        assertTrue(result is ReaderPlaybackPilotDispatch.Applied)
        val applied = result as ReaderPlaybackPilotDispatch.Applied
        assertEquals("reader.autoPage.start", applied.event)
        assertNotNull(applied.state.autoPageTransaction)
        assertEquals("reader.autoPage.start", coordinator.lastObservation?.event)
        assertEquals(ReaderUiRuntimeDispatchMode.PILOT, coordinator.lastObservation?.mode)
    }

    @Test
    fun `auto page stop pilot clears transaction`() {
        val runtime = ReaderUIRuntime(ReaderUIState(routeId = "immersive-reading"))
        val coordinator = ReaderUiRuntimeCoordinator(
            runtime = runtime,
            directoryPilotEnabled = false,
            bookOpenPilotEnabled = false,
            playbackPilotEnabled = true
        )

        coordinator.dispatchPlaybackPilot(ReaderUiIntent.StartAutoPageSession())
        assertNotNull(runtime.state.autoPageTransaction)

        val result = coordinator.dispatchPlaybackPilot(ReaderUiIntent.StopSession)

        assertTrue(result is ReaderPlaybackPilotDispatch.Applied)
        val applied = result as ReaderPlaybackPilotDispatch.Applied
        assertEquals("reader.autoPage.stop", applied.event)
        assertNull(applied.state.autoPageTransaction)
        assertEquals("reader.autoPage.stop", coordinator.lastObservation?.event)
        assertEquals(ReaderUiRuntimeDispatchMode.PILOT, coordinator.lastObservation?.mode)
    }

    @Test
    fun `repeated page and auto page actions keep runtime exactly once while fresh clicks supersede`() {
        val pageRuntime = ReaderUIRuntime(ReaderUIState(routeId = "immersive-reading"))
        val pageCoordinator = ReaderUiRuntimeCoordinator(
            runtime = pageRuntime,
            directoryPilotEnabled = false,
            bookOpenPilotEnabled = false,
            playbackPilotEnabled = true
        )
        val firstPage = ReaderUiIntent.TurnPageNext()
        val secondPage = ReaderUiIntent.TurnPageNext()
        val previousPage = ReaderUiIntent.TurnPagePrev()
        assertEquals(3, setOf(firstPage.requestId, secondPage.requestId, previousPage.requestId).size)

        assertTrue(pageCoordinator.dispatchPlaybackPilot(firstPage) is ReaderPlaybackPilotDispatch.Applied)
        assertTrue(pageCoordinator.dispatchPlaybackPilot(firstPage) is ReaderPlaybackPilotDispatch.FailedClosed)
        assertEquals(firstPage.requestId, pageRuntime.state.pageTransaction?.correlationId)

        val second = pageCoordinator.dispatchPlaybackPilot(secondPage) as ReaderPlaybackPilotDispatch.Applied
        assertEquals(listOf(firstPage.requestId), second.cancelledCorrelationIds)
        assertEquals(secondPage.requestId, pageRuntime.state.pageTransaction?.correlationId)
        val previous = pageCoordinator.dispatchPlaybackPilot(previousPage) as ReaderPlaybackPilotDispatch.Applied
        assertEquals(listOf(secondPage.requestId), previous.cancelledCorrelationIds)
        assertEquals(previousPage.requestId, pageRuntime.state.pageTransaction?.correlationId)

        val autoRuntime = ReaderUIRuntime(ReaderUIState(routeId = "immersive-reading"))
        val autoCoordinator = ReaderUiRuntimeCoordinator(
            runtime = autoRuntime,
            directoryPilotEnabled = false,
            bookOpenPilotEnabled = false,
            playbackPilotEnabled = true
        )
        val firstAuto = ReaderUiIntent.StartAutoPageSession()
        val secondAuto = ReaderUiIntent.StartAutoPageSession()
        assertFalse(firstAuto.requestId == secondAuto.requestId)
        assertTrue(autoCoordinator.dispatchPlaybackPilot(firstAuto) is ReaderPlaybackPilotDispatch.Applied)
        assertTrue(autoCoordinator.dispatchPlaybackPilot(firstAuto) is ReaderPlaybackPilotDispatch.FailedClosed)
        val replacement = autoCoordinator.dispatchPlaybackPilot(secondAuto) as ReaderPlaybackPilotDispatch.Applied
        assertTrue(firstAuto.requestId in replacement.cancelledCorrelationIds)
        assertEquals(secondAuto.requestId, autoRuntime.state.autoPageTransaction?.correlationId)
    }

    @Test
    fun `concurrent page clicks receive unique correlations and serialize through supersede ledger`() {
        val runtime = ReaderUIRuntime(ReaderUIState(routeId = "immersive-reading"))
        val coordinator = ReaderUiRuntimeCoordinator(
            runtime = runtime,
            directoryPilotEnabled = false,
            bookOpenPilotEnabled = false,
            playbackPilotEnabled = true
        )
        val intents = List(8) { ReaderUiIntent.TurnPageNext() }
        assertEquals(intents.size, intents.map { it.requestId }.toSet().size)
        val ready = CountDownLatch(intents.size)
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(intents.size)
        try {
            val futures = intents.map { intent ->
                executor.submit<ReaderPlaybackPilotDispatch> {
                    ready.countDown()
                    check(start.await(5, TimeUnit.SECONDS))
                    coordinator.dispatchPlaybackPilot(intent)
                }
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS))
            start.countDown()
            val results = futures.map { it.get(5, TimeUnit.SECONDS) }
                .map { it as ReaderPlaybackPilotDispatch.Applied }
            val cancelled = results.flatMap { it.cancelledCorrelationIds }
            val active = requireNotNull(runtime.state.pageTransaction?.correlationId)

            assertEquals(intents.size - 1, cancelled.size)
            assertEquals(cancelled.size, cancelled.toSet().size)
            assertEquals(intents.map { it.requestId }.toSet(), cancelled.toSet() + active)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `page pair remains shadow mode in consumer lock cohort`() {
        val lock = JSONObject(findConsumerLock().readText())
        val rollout = lock.getJSONObject("rollout")
        val cohorts = rollout.getJSONArray("cohorts")
        val playback = (0 until cohorts.length()).map { cohorts.getJSONObject(it) }
            .first { it.getString("id") == "playback-pilot" }
        val playbackEvents = playback.getJSONArray("events").strings().toSet()
        assertEquals(
            setOf(
                "reader.tts.start",
                "reader.tts.stop",
                "reader.autoPage.start",
                "reader.autoPage.stop"
            ),
            playbackEvents
        )
        // page.next/prev are NOT in the playback-pilot cohort
        assertFalse("reader.page.next" in playbackEvents)
        assertFalse("reader.page.prev" in playbackEvents)
        // They remain in covered events (shadow coverage)
        val covered = rollout.getJSONArray("coveredEvents").strings().toSet()
        assertTrue("reader.page.next" in covered)
        assertTrue("reader.page.prev" in covered)
    }

    private class RecordingBookOpenExecutor(
        private val store: ReaderBookOpenDomainStore,
        runtime: ReaderBookOpenRuntimeDriver
    ) : ReaderBookOpenEffectExecutor(store, runtime) {
        val entries = mutableListOf<ReaderUIEffect>()

        override fun start(context: ReaderContext, firstEffect: ReaderUIEffect, scope: CoroutineScope) {
            store.begin(context)
            entries += firstEffect
        }
    }

    private fun findConsumerLock(): File {
        val workingDirectory = System.getProperty("user.dir") ?: "."
        var cursor: File? = File(workingDirectory).absoluteFile
        repeat(5) {
            val candidate = File(cursor, "READER_UI_CONSUMER.json")
            if (candidate.isFile) return candidate
            cursor = cursor?.parentFile
        }
        error("READER_UI_CONSUMER.json not found from $workingDirectory")
    }

    /**
     * The release verifier writes this version and identity into the consumer lock.
     * The local composite Reader-UI checkout may already be ahead of the consumed artifact,
     * so the lock remains the expected release source until an atomic lock-only bump lands.
     */
    private fun verifiedArtifactReaderUiVersion(lock: JSONObject): String {
        assertEquals(2, lock.getInt("schemaVersion"))
        assertEquals("android", lock.getString("host"))
        val identity = lock.getJSONObject("releaseIdentity")
        assertEquals(
            "${identity.getString("sourceSha")}:${identity.getString("manifestSha256")}",
            identity.getString("releaseId")
        )
        return lock.getString("readerUiVersion")
    }

    private fun org.json.JSONArray.strings(): List<String> =
        (0 until length()).map(::getString)

    private companion object {
        val STRICT_SEMVER = Regex(
            "^(?:0|[1-9]\\d*)\\.(?:0|[1-9]\\d*)\\.(?:0|[1-9]\\d*)" +
                "(?:-(?:(?:0|[1-9]\\d*)|(?:\\d*[A-Za-z-][0-9A-Za-z-]*))" +
                "(?:\\.(?:(?:0|[1-9]\\d*)|(?:\\d*[A-Za-z-][0-9A-Za-z-]*)))*)?$"
        )
    }
}
