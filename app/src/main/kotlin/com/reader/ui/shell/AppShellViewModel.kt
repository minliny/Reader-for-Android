package com.reader.ui.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reader.android.data.adapter.TtsProgressUpdate
import com.reader.ui.motion.MotionController
import com.reader.ui.motion.MotionIdConstants
import com.reader.ui.motion.MotionPolicyAdapter
import com.reader.ui.motion.serialName
import com.reader.ui.motion.ReducedMotionResolver
import io.reader.ui.contract.MotionOperation
import io.reader.ui.contract.RouteShell
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope

/**
 * Holds the single [ReaderUiState] and dispatches [ReaderUiIntent]s through
 * [ReaderUiReducer]. This is the only mutator of the shell UI state — animations, tab
 * switches, route pushes, and reader entries all flow through [dispatch].
 *
 * Wires the platform [ReducedMotionResolver] at construction so the reduced-motion flag is
 * a real, observable state field rather than a render-side hack.
 *
 * P6 wiring: [MotionController] is attached at construction so every [dispatch] can map
 * the intent to its Motion ID (from `MotionController.contractFor`) and start a motion
 * transaction. The reducer stays pure; MotionController is the runtime side-effect layer.
 *
 * P1-4: [ttsProgressFlow] is collected so the TTS session controller's
 * paragraph-level progress is written back to `activeSession.ttsSentenceIndex`
 * / `ttsChapterIndex` via `UpdateTtsProgress` — closing the "UI action →
 * Host/Core state writeback" loop.
 */
class AppShellViewModel internal constructor(
    reducedMotionResolver: ReducedMotionResolver? = null,
    ttsProgressFlow: Flow<TtsProgressUpdate?>? = null,
    private val readerUiRuntimeCoordinator: ReaderUiRuntimeCoordinator = ReaderUiRuntimeCoordinator(),
    private val nativeReducer: (ReaderUiState, ReaderUiIntent) -> ReaderUiState = ReaderUiReducer::reduce,
    private val readerBookOpenDomainStore: ReaderBookOpenDomainStore = ReaderBookOpenDomainStore(),
    private val readerBookOpenEffectExecutor: ReaderBookOpenEffectExecutor = ReaderBookOpenEffectExecutor(
        domainStore = readerBookOpenDomainStore,
        runtime = readerUiRuntimeCoordinator
    ),
    /** Test seam; production uses the lifecycle-owned [viewModelScope]. */
    private val readerBookOpenScope: CoroutineScope? = null,
    private val readerPlaybackDomainStore: ReaderPlaybackDomainStore = ReaderPlaybackDomainStore(),
    private val readerPlaybackEffectExecutor: ReaderPlaybackEffectExecutor = ReaderPlaybackEffectExecutor(
        domainStore = readerPlaybackDomainStore,
        runtime = readerUiRuntimeCoordinator
    ),
    /** Test seam shared by command, speech and foreground one-shot timer work. */
    private val readerPlaybackScope: CoroutineScope? = null
) : ViewModel() {

    private val _state = MutableStateFlow(
        ReaderUiState(reducedMotion = reducedMotionResolver?.isReducedMotion() ?: false)
    )
    val state: StateFlow<ReaderUiState> = _state.asStateFlow()

    /** Diagnostic-only live-shadow counters; never consumed by the renderer. */
    val readerUiRuntimeShadowMetrics: ReaderUiRuntimeShadowMetrics
        get() = readerUiRuntimeCoordinator.metrics

    /** Last covered live-shadow transition, including unexecuted runtime effects. */
    val readerUiRuntimeShadowObservation: ReaderUiRuntimeShadowObservation?
        get() = readerUiRuntimeCoordinator.lastObservation

    /** Diagnostic-only runtime state used by parity tests and debug evidence. */
    val readerUiRuntimeShadowState: io.reader.ui.runtime.ReaderUIState
        get() = readerUiRuntimeCoordinator.runtimeState

    /** Result-dependent Core transaction state rendered by the opt-in book.open Pilot. */
    internal val readerBookOpenDomainState: StateFlow<ReaderBookOpenDomainState>
        get() = readerBookOpenDomainStore.state

    /** Default false BuildConfig gate; independent from the directory consumer-lock cohort. */
    internal val readerBookOpenPilotEnabled: Boolean
        get() = readerUiRuntimeCoordinator.bookOpenPilotEnabled

    internal val readerPlaybackPilotEnabled: Boolean
        get() = readerUiRuntimeCoordinator.playbackPilotEnabled

    internal val readerPlaybackDomainState: StateFlow<ReaderPlaybackDomainState>
        get() = readerPlaybackDomainStore.state

    init {
        // P6: attach the motion runtime so dispatch() can fire motion transactions.
        MotionController.setReducedMotionResolver(reducedMotionResolver)
        MotionController.attachToViewModel(this)
        readerPlaybackEffectExecutor.setProjectionSink(::projectPlaybackRuntimeState)
        // P1-4: collect TTS progress and dispatch UpdateTtsProgress so the
        // reducer's activeSession reflects the real playback position.
        if (ttsProgressFlow != null) {
            viewModelScope.launch {
                ttsProgressFlow.collect { progress ->
                    if (progress != null) {
                        dispatch(ReaderUiIntent.UpdateTtsProgress(
                            sentenceIndex = progress.paragraphIndex,
                            chapterIndex = progress.chapterIndex
                        ))
                    }
                }
            }
        }
    }

    @Synchronized
    fun dispatch(intent: ReaderUiIntent) {
        val productionBefore = _state.value

        // A new book replaces every in-flight playback generation before the
        // book.open transaction is admitted. This avoids Runtime emitting
        // playback teardown effects into the book.open-only executor.
        val isBookReplacement = intent is ReaderUiIntent.EnterReaderFromCover ||
            intent is ReaderUiIntent.EnterReaderFromAction
        val playbackBeforeReplacement = readerUiRuntimeCoordinator.playbackRuntimeState
        val playbackTeardownQueued = readerPlaybackPilotEnabled && isBookReplacement &&
            (playbackBeforeReplacement.pageTransaction != null ||
                playbackBeforeReplacement.ttsTransaction != null ||
                playbackBeforeReplacement.autoPageTransaction != null)
        if (playbackTeardownQueued) {
            readerPlaybackEffectExecutor.teardownForReaderExit(
                readerPlaybackScope ?: viewModelScope
            )
        }

        // Pause/resume and sentence-skip are not part of the promoted pairs.
        // While a paired Pilot session exists they must fail closed instead
        // of falling through to the legacy HostRequest/TtsSessionController.
        if (
            readerPlaybackPilotEnabled &&
            readerUiRuntimeCoordinator.playbackRuntimeState.ttsTransaction != null &&
            (intent == ReaderUiIntent.ToggleSessionPlaying ||
                (intent is ReaderUiIntent.DispatchHostRequest && intent.capability.startsWith("tts.")))
        ) {
            return
        }

        // Paired R8 Pilot owns reducer + Core/Host execution together. A
        // missing Core reader snapshot or Runtime rejection fails closed;
        // the old reducer, HostRequest queue and TtsSessionController path
        // must remain at zero calls for this event.
        if (readerPlaybackPilotEnabled && intent.isPlaybackPilotIntent()) {
            if (!readerPlaybackEffectExecutor.canAdmit(intent, readerBookOpenDomainStore.state.value)) {
                return
            }
            when (val playback = readerUiRuntimeCoordinator.dispatchPlaybackPilot(intent)) {
                is ReaderPlaybackPilotDispatch.Applied -> {
                    startMotionTransaction(intent, productionBefore)
                    readerPlaybackEffectExecutor.applyTransition(
                        event = playback.event,
                        state = playback.state,
                        effects = playback.effects,
                        cancelledCorrelationIds = playback.cancelledCorrelationIds,
                        bookOpen = readerBookOpenDomainStore.state.value,
                        scope = readerPlaybackScope ?: viewModelScope
                    )
                    return
                }
                ReaderPlaybackPilotDispatch.FailedClosed -> return
                ReaderPlaybackPilotDispatch.NotEnabled -> return
            }
        }

        // `book.open` is a separate, default-off Pilot. Runtime owns the
        // result-dependent Core ledger; the native reducer is used once only
        // for route/context presentation and must not re-observe this event.
        when (val bookOpenPilot = readerUiRuntimeCoordinator.dispatchBookOpenPilot(intent)) {
            is ReaderBookOpenPilotDispatch.Applied -> {
                // The Runtime transaction is the cleanup authority. Prefer
                // its canonical correlation so a malformed returned effect
                // cannot leave a live transaction behind before executor.begin.
                val correlationId = bookOpenPilot.transition.state.bookOpenTransaction?.correlationId
                    ?: bookOpenPilot.transition.effects.singleOrNull()?.correlationId
                val context = readerContextForBookOpen(intent)
                if (context == null || correlationId != context.entryRequestId) {
                    correlationId?.let(readerBookOpenEffectExecutor::cancel)
                    return
                }
                if (playbackTeardownQueued) {
                    readerPlaybackEffectExecutor.afterSerialTeardown(
                        readerPlaybackScope ?: viewModelScope
                    ) {
                        synchronized(this@AppShellViewModel) {
                            val currentBookCorrelation = readerUiRuntimeCoordinator.runtimeState
                                .bookOpenTransaction?.correlationId
                            if (currentBookCorrelation != correlationId) {
                                readerBookOpenEffectExecutor.cancel(correlationId)
                            } else {
                                admitBookOpenPilot(intent, context, correlationId, bookOpenPilot.transition)
                            }
                        }
                    }
                } else {
                    admitBookOpenPilot(intent, context, correlationId, bookOpenPilot.transition)
                }
                return
            }
            ReaderBookOpenPilotDispatch.FailedClosed -> return
            ReaderBookOpenPilotDispatch.NotEnabled -> Unit
        }

        when (val pilot = readerUiRuntimeCoordinator.dispatchPilot(intent, productionBefore)) {
            is ReaderUiRuntimePilotResult.Applied -> {
                // R8 directory Pilot: runtime owns the semantic overlay. Only a
                // successful transition may start presentation motion and write
                // the narrow native route/sheet projection. The native reducer
                // and its effect path are deliberately skipped.
                if (pilot.changed) {
                    startMotionTransaction(intent, productionBefore)
                    _state.value = pilot.productionState
                }
            }
            ReaderUiRuntimePilotResult.FailedClosed -> {
                // Runtime guard/error is authoritative for the Pilot pair. Keep
                // production state and motion untouched; never bypass it through
                // ReaderUiReducer.
                return
            }
            ReaderUiRuntimePilotResult.NotPilot -> {
                // Existing production ownership for uncovered and shadow events:
                // native reducer/effects execute exactly once, then runtime only
                // observes the committed result.
                startMotionTransaction(intent, productionBefore)
                val productionAfter = nativeReducer(productionBefore, intent)
                _state.value = productionAfter
                cancelBookOpenIfReaderExited(productionBefore, productionAfter)
                if (
                    readerPlaybackPilotEnabled &&
                    productionBefore.readerContext != null && productionAfter.readerContext == null
                ) {
                    readerPlaybackEffectExecutor.teardownForReaderExit(
                        readerPlaybackScope ?: viewModelScope
                    )
                }
                readerUiRuntimeCoordinator.observe(intent, productionBefore, productionAfter)
            }
        }
    }

    /** Called by the real Compose reading surface after it receives layout coordinates. */
    internal fun onReaderBookOpenViewportLayoutReady(viewport: ReaderBookOpenViewport) {
        if (!readerBookOpenPilotEnabled) return
        readerBookOpenEffectExecutor.onViewportLayoutReady(
            viewport = viewport,
            scope = readerBookOpenScope ?: viewModelScope
        )
    }

    internal fun onReaderPlaybackPageLayoutReady(measurement: ReaderPlaybackPageMeasurement) {
        if (!readerPlaybackPilotEnabled) return
        readerPlaybackEffectExecutor.onPageLayoutReady(
            measurement,
            readerPlaybackScope ?: viewModelScope
        )
    }

    internal fun onReaderAppBackgrounded() {
        if (!readerPlaybackPilotEnabled) return
        readerPlaybackEffectExecutor.onAppBackgrounded(readerPlaybackScope ?: viewModelScope)
    }

    override fun onCleared() {
        readerBookOpenEffectExecutor.cancelActive()
        readerPlaybackEffectExecutor.cancelAll(readerPlaybackScope ?: viewModelScope)
        super.onCleared()
    }

    @Synchronized
    private fun projectPlaybackRuntimeState(
        runtime: io.reader.ui.runtime.ReaderUIState,
        domain: ReaderPlaybackDomainState
    ) {
        val current = _state.value
        val location = domain.committedLocation
        val readerContext = current.readerContext?.let { context ->
            if (location != null && location.bookId == context.bookUrl) {
                context.copy(
                    chapterIndex = location.chapterIndex,
                    page = domain.committedPageIndex,
                    progress = location.chapterProgress.toFloat()
                )
            } else {
                context
            }
        }
        val activeSession = when (runtime.activeSession) {
            "tts" -> ActiveSession(
                type = SessionType.TTS,
                playing = true,
                ttsSentenceIndex = domain.ttsSnapshot?.currentSliceIndex ?: 0,
                ttsChapterIndex = location?.chapterIndex ?: readerContext?.chapterIndex ?: 0
            )
            "auto-page" -> ActiveSession(
                type = SessionType.AUTO_PAGE,
                playing = true,
                countdownSeconds = READER_AUTO_PAGE_DEFAULT_INTERVAL_MS / 1_000
            )
            else -> null
        }
        _state.value = current.copy(
            readerContext = readerContext,
            activeSession = activeSession
        )
    }

    private fun ReaderUiIntent.isPlaybackPilotIntent(): Boolean = when (this) {
        ReaderUiIntent.TurnPageNext,
        ReaderUiIntent.TurnPagePrev,
        is ReaderUiIntent.StartTtsSession,
        ReaderUiIntent.StartAutoPageSession,
        ReaderUiIntent.StopSession -> true
        else -> false
    }

    private fun readerContextForBookOpen(intent: ReaderUiIntent): ReaderContext? = when (intent) {
        is ReaderUiIntent.EnterReaderFromCover -> ReaderContext(
            sourceId = intent.sourceId,
            bookUrl = intent.bookUrl,
            bookName = intent.bookName,
            entry = ReaderEntry.COVER_TO_IMMERSIVE,
            entryRequestId = intent.requestId
        )
        is ReaderUiIntent.EnterReaderFromAction -> ReaderContext(
            sourceId = intent.sourceId,
            bookUrl = intent.bookUrl,
            bookName = intent.bookName,
            entry = ReaderEntry.ACTION_TO_IMMERSIVE,
            entryRequestId = intent.requestId
        )
        else -> null
    }

    private fun admitBookOpenPilot(
        intent: ReaderUiIntent,
        context: ReaderContext,
        correlationId: String,
        transition: io.reader.ui.runtime.ReaderUITransition
    ) {
        val before = _state.value
        try {
            transition.cancelledCorrelationIds.forEach(readerBookOpenEffectExecutor::cancel)
            // Only commit presentation after all synchronous admission
            // checks passed. When replacing a playback session this method is
            // invoked after its serial system/Core teardown is terminal.
            readerBookOpenEffectExecutor.start(
                context = context,
                firstEffect = transition.effects.single(),
                scope = readerBookOpenScope ?: viewModelScope
            )
            val productionAfter = nativeReducer(before, intent)
            startMotionTransaction(intent, before)
            _state.value = productionAfter
        } catch (_: Exception) {
            _state.value = before
            readerBookOpenEffectExecutor.cancel(correlationId)
        }
    }

    private fun cancelBookOpenIfReaderExited(before: ReaderUiState, after: ReaderUiState) {
        if (!readerBookOpenPilotEnabled || after.readerContext != null) return
        before.readerContext?.entryRequestId?.let(readerBookOpenEffectExecutor::cancel)
    }

    private fun startMotionTransaction(intent: ReaderUiIntent, stateBefore: ReaderUiState) {
        // P6: map intent → Motion ID and start a motion transaction (runtime side-effect).
        // The reducer remains pure; this is the consume layer that MOTION_EFFECTS.md §6 requires.
        motionTransactionFor(intent, stateBefore)?.let { (motionId, from, to, durationMs) ->
            MotionController.start(
                motionId = motionId,
                from = from,
                to = to,
                durationMs = durationMs,
                reducedMotion = stateBefore.reducedMotion
            )
        }
    }

    /**
     * Maps a [ReaderUiIntent] to its Motion ID + from/to route ids + default duration.
     * Returns null for intents that don't have a motion contract (e.g. state field updates
     * that don't trigger a transition). Sourced from `MotionController.contractFor` /
     * `MotionIdConstants` — no fabricated Motion IDs.
     */
    private fun motionTransactionFor(
        intent: ReaderUiIntent,
        current: ReaderUiState = _state.value
    ): Tuple4<String, String, String, Long>? {
        val from = current.currentRoute.routeId
        return when (intent) {
            is ReaderUiIntent.SelectTab -> Tuple4(
                MotionIdConstants.TAB_ITEM_SWITCH,
                from,
                intent.tab.routeId,
                MotionController.contractFor(MotionIdConstants.TAB_ITEM_SWITCH)?.defaultDurationMs
                    ?: 160L
            )
            is ReaderUiIntent.EnterReaderFromCover -> Tuple4(
                MotionIdConstants.READER_ENTRY_COVER_TO_IMMERSIVE,
                from,
                RouteIds.IMMERSIVE_READING,
                MotionController.contractFor(MotionIdConstants.READER_ENTRY_COVER_TO_IMMERSIVE)?.defaultDurationMs
                    ?: 240L
            )
            is ReaderUiIntent.EnterReaderFromAction -> Tuple4(
                MotionIdConstants.READER_ENTRY_ACTION_TO_IMMERSIVE,
                from,
                RouteIds.IMMERSIVE_READING,
                MotionController.contractFor(MotionIdConstants.READER_ENTRY_ACTION_TO_IMMERSIVE)?.defaultDurationMs
                    ?: 240L
            )
            is ReaderUiIntent.PushRoute -> {
                // 接入 MotionPolicyAdapter：让 Push 走 policy 解析，获取最匹配的 MotionId。
                // 解析失败时回退到 APP_ROUTE_PUSH_FORWARD 常量，保证向后兼容。
                val toRoute = intent.route.routeId
                val resolved = MotionPolicyAdapter.resolveRouteTransition(
                    fromRoute = from,
                    toRoute = toRoute,
                    fromShell = current.currentRoute.shell(),
                    toShell = intent.route.shell(),
                    operation = MotionOperation.Push
                )
                val motionId = resolved?.serialName ?: MotionIdConstants.APP_ROUTE_PUSH_FORWARD
                Tuple4(
                    motionId,
                    from,
                    toRoute,
                    MotionController.contractFor(motionId)?.defaultDurationMs
                        ?: MotionController.contractFor(MotionIdConstants.APP_ROUTE_PUSH_FORWARD)?.defaultDurationMs
                        ?: 160L
                )
            }
            ReaderUiIntent.PopRoute -> {
                val toRoute = current.backStack.dropLast(1).lastOrNull()?.routeId
                    ?: current.activeTab.routeId
                val toShell = current.backStack.dropLast(1).lastOrNull()?.shell()
                    ?: RouteShell.MainTabShell
                val resolved = MotionPolicyAdapter.resolveRouteTransition(
                    fromRoute = from,
                    toRoute = toRoute,
                    fromShell = current.currentRoute.shell(),
                    toShell = toShell,
                    operation = MotionOperation.Pop
                )
                val motionId = resolved?.serialName ?: MotionIdConstants.APP_ROUTE_POP_BACKWARD
                Tuple4(
                    motionId,
                    from,
                    toRoute,
                    MotionController.contractFor(motionId)?.defaultDurationMs
                        ?: MotionController.contractFor(MotionIdConstants.APP_ROUTE_POP_BACKWARD)?.defaultDurationMs
                        ?: 160L
                )
            }
            is ReaderUiIntent.ReplaceRoute -> {
                val toRoute = intent.route.routeId
                val resolved = MotionPolicyAdapter.resolveRouteTransition(
                    fromRoute = from,
                    toRoute = toRoute,
                    fromShell = current.currentRoute.shell(),
                    toShell = intent.route.shell(),
                    operation = MotionOperation.Replace
                )
                val motionId = resolved?.serialName ?: MotionIdConstants.APP_ROUTE_REPLACE
                Tuple4(
                    motionId,
                    from,
                    toRoute,
                    MotionController.contractFor(motionId)?.defaultDurationMs
                        ?: MotionController.contractFor(MotionIdConstants.APP_ROUTE_REPLACE)?.defaultDurationMs
                        ?: 160L
                )
            }
            ReaderUiIntent.HideReaderControl -> Tuple4(
                MotionIdConstants.READER_CONTROL_HIDE,
                RouteIds.READER_CONTROL,
                RouteIds.IMMERSIVE_READING,
                MotionController.contractFor(MotionIdConstants.READER_CONTROL_HIDE)?.defaultDurationMs
                    ?: 160L
            )
            is ReaderUiIntent.SwitchReaderModule -> Tuple4(
                MotionIdConstants.READER_MODULE_SWITCH,
                from,
                from,
                MotionController.contractFor(MotionIdConstants.READER_MODULE_SWITCH)?.defaultDurationMs
                    ?: 160L
            )
            ReaderUiIntent.StartAutoPageSession -> Tuple4(
                MotionIdConstants.READER_SESSION_AUTO_PAGE_START,
                from,
                RouteIds.IMMERSIVE_READING,
                MotionController.contractFor(MotionIdConstants.READER_SESSION_AUTO_PAGE_START)?.defaultDurationMs
                    ?: 200L
            )
            is ReaderUiIntent.StartTtsSession -> Tuple4(
                MotionIdConstants.READER_SESSION_TTS_START,
                from,
                RouteIds.IMMERSIVE_READING,
                MotionController.contractFor(MotionIdConstants.READER_SESSION_TTS_START)?.defaultDurationMs
                    ?: 200L
            )
            ReaderUiIntent.ViewportPrepare -> Tuple4(
                MotionIdConstants.VIEWPORT_ORIENTATION_PREPARE,
                from,
                from,
                MotionController.contractFor(MotionIdConstants.VIEWPORT_ORIENTATION_PREPARE)?.defaultDurationMs
                    ?: 80L
            )
            ReaderUiIntent.ViewportReshape -> Tuple4(
                MotionIdConstants.VIEWPORT_ORIENTATION_RESHAPE,
                from,
                from,
                MotionController.contractFor(MotionIdConstants.VIEWPORT_ORIENTATION_RESHAPE)?.defaultDurationMs
                    ?: 120L
            )
            ReaderUiIntent.ViewportSettle -> Tuple4(
                MotionIdConstants.VIEWPORT_ORIENTATION_SETTLE,
                from,
                from,
                MotionController.contractFor(MotionIdConstants.VIEWPORT_ORIENTATION_SETTLE)?.defaultDurationMs
                    ?: 120L
            )
            // ── 翻页 ──
            ReaderUiIntent.TurnPageNext -> Tuple4(
                MotionIdConstants.READER_PAGE_TURN_NEXT_PREV,
                "page.current", "page.next",
                MotionController.contractFor(MotionIdConstants.READER_PAGE_TURN_NEXT_PREV)?.defaultDurationMs
                    ?: 220L
            )
            ReaderUiIntent.TurnPagePrev -> Tuple4(
                MotionIdConstants.READER_PAGE_TURN_NEXT_PREV,
                "page.current", "page.previous",
                MotionController.contractFor(MotionIdConstants.READER_PAGE_TURN_NEXT_PREV)?.defaultDurationMs
                    ?: 220L
            )

            // ── 会话控制 ──
            ReaderUiIntent.StopSession -> Tuple4(
                MotionIdConstants.READER_SESSION_CAPSULE_EXIT,
                "capsuleVisible", "capsuleHidden",
                MotionController.contractFor(MotionIdConstants.READER_SESSION_CAPSULE_EXIT)?.defaultDurationMs
                    ?: 160L
            )
            ReaderUiIntent.ToggleSessionPlaying -> Tuple4(
                MotionIdConstants.READER_SESSION_CAPSULE_CONTROL_PRESS_TOGGLE,
                "playing.previous", "playing.next",
                MotionController.contractFor(MotionIdConstants.READER_SESSION_CAPSULE_CONTROL_PRESS_TOGGLE)?.defaultDurationMs
                    ?: 120L
            )

            // ── Overlay 弹层 ──
            is ReaderUiIntent.OpenKeyboard -> Tuple4(
                MotionIdConstants.OVERLAY_KEYBOARD_ENTER_EXIT,
                "hidden", "visible",
                MotionController.contractFor(MotionIdConstants.OVERLAY_KEYBOARD_ENTER_EXIT)?.defaultDurationMs
                    ?: 240L
            )
            ReaderUiIntent.CloseKeyboard -> Tuple4(
                MotionIdConstants.OVERLAY_KEYBOARD_ENTER_EXIT,
                "visible", "hidden",
                MotionController.contractFor(MotionIdConstants.OVERLAY_KEYBOARD_ENTER_EXIT)?.defaultDurationMs
                    ?: 240L
            )
            is ReaderUiIntent.OpenSheet -> Tuple4(
                MotionIdConstants.OVERLAY_SHEET_ENTER,
                "hidden", "visible",
                MotionController.contractFor(MotionIdConstants.OVERLAY_SHEET_ENTER)?.defaultDurationMs
                    ?: 240L
            )
            ReaderUiIntent.CloseSheet -> Tuple4(
                MotionIdConstants.OVERLAY_SHEET_EXIT,
                "visible", "hidden",
                MotionController.contractFor(MotionIdConstants.OVERLAY_SHEET_EXIT)?.defaultDurationMs
                    ?: 240L
            )
            is ReaderUiIntent.OpenDialog -> Tuple4(
                MotionIdConstants.OVERLAY_DIALOG_ENTER,
                "hidden", "visible",
                MotionController.contractFor(MotionIdConstants.OVERLAY_DIALOG_ENTER)?.defaultDurationMs
                    ?: 240L
            )
            ReaderUiIntent.CloseDialog -> Tuple4(
                MotionIdConstants.OVERLAY_DIALOG_EXIT,
                "visible", "hidden",
                MotionController.contractFor(MotionIdConstants.OVERLAY_DIALOG_EXIT)?.defaultDurationMs
                    ?: 240L
            )
            is ReaderUiIntent.OpenMoreMenu -> Tuple4(
                MotionIdConstants.DROPDOWN_MENU_EXPAND,
                "closed", "open",
                MotionController.contractFor(MotionIdConstants.DROPDOWN_MENU_EXPAND)?.defaultDurationMs
                    ?: 160L
            )
            ReaderUiIntent.CloseMoreMenu -> Tuple4(
                MotionIdConstants.DROPDOWN_MENU_COLLAPSE,
                "open", "closed",
                MotionController.contractFor(MotionIdConstants.DROPDOWN_MENU_COLLAPSE)?.defaultDurationMs
                    ?: 120L
            )
            // Intents with no motion contract — state field updates only.
            else -> null
        }
    }

    private data class Tuple4<A, B, C, D>(
        val a: A, val b: B, val c: C, val d: D
    )
}
