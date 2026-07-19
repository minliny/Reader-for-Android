package com.reader.ui.shell

import com.reader.android.BuildConfig
import com.reader.ui.motion.MotionIdConstants
import io.reader.ui.runtime.GeneratedRuntimeActions
import io.reader.ui.runtime.ReaderUIEffect
import io.reader.ui.runtime.ReaderUIEffectKind
import io.reader.ui.runtime.ReaderUIJSONPayload
import io.reader.ui.runtime.ReaderUIJSONResult
import io.reader.ui.runtime.ReaderUIRuntime
import io.reader.ui.runtime.ReaderUIRuntimeException
import io.reader.ui.runtime.ReaderUIState
import io.reader.ui.runtime.ReaderUITransition
import io.reader.ui.runtime.ReaderUIPlaybackTransition
import io.reader.ui.runtime.validateReaderUITypedResult
import kotlinx.serialization.json.JsonPrimitive

/**
 * Exact Android runtime allowlist. Production authority is limited to the
 * directory, book.open, and playback cohorts; the other covered events stay
 * in live Shadow unless a test explicitly opts into an experimental seam.
 */
internal val READER_UI_RUNTIME_COVERED_EVENTS: Set<String> = linkedSetOf(
    "book.open",
    "reader.directory.open",
    "reader.directory.close",
    "reader.page.next",
    "reader.page.prev",
    "reader.tts.start",
    "reader.tts.stop",
    "reader.autoPage.start",
    "reader.autoPage.stop",
    "import.start",
    "import.apply",
    "import.cancel",
    "source.switch.open",
    "source.switch.cancel",
    "source.switch.confirm",
    "source.switch.rollback",
    "reader.sourceSwitch.open",
    "reader.sourceSwitch.close",
    "reader.replace.apply",
    "reader.replace.create",
    "reader.replace.validate",
    "rss.refresh",
    "rss.subscription.add",
    "rss.subscription.delete",
    "rss.subscription.edit",
    "rss.entry.open",
    "rss.favorite.add",
    "rss.favorite.remove",
    "sync.run",
    "webdav.config.test",
    "sync.start",
    "sync.progress",
    "sync.complete",
    "sync.conflict",
    "sync.resolve"
)

internal val READER_UI_RUNTIME_DIRECTORY_PILOT_EVENTS: Set<String> = linkedSetOf(
    "reader.directory.open",
    "reader.directory.close"
)

/**
 * Schema-3 Reader control actions that are intentionally outside the current
 * schema-2 consumer lock. They may only run through the explicit local
 * candidate seam below; [AppShellViewModel] never dispatches them in
 * production while the lock remains on schema 2.
 */
internal val READER_UI_RUNTIME_READER_CONTROL_LOCAL_CANDIDATE_EVENTS: Set<String> = linkedSetOf(
    "reader.control.toggle",
    "reader.module.switch"
)

private val READER_CONTROL_MODULES: Set<String> = setOf(
    "directory",
    "tts",
    "appearance",
    "settings"
)

private val READER_CONTROL_OVERLAY_FAMILY: Set<String> =
    READER_CONTROL_MODULES + "reader-control"

/** Retained alias for source compatibility with the R7 live-shadow tests. */
internal val READER_UI_RUNTIME_SHADOW_EVENTS: Set<String> = READER_UI_RUNTIME_COVERED_EVENTS

/**
 * Classify the canonical book-open branch from identities already owned by
 * Android/Core. Core materialized local books use the literal source id
 * `local`; Android's `fixture://` reader and direct file/content URIs are also
 * host-local. Every other source id belongs to the remote book-source path.
 */
internal fun canonicalBookOpenSourceKind(sourceId: String, bookId: String): String =
    if (
        sourceId == "local" ||
        sourceId.startsWith("local://") ||
        sourceId.startsWith("fixture://") ||
        sourceId.startsWith("file://") ||
        sourceId.startsWith("content://") ||
        bookId.startsWith("local://") ||
        bookId.startsWith("fixture://") ||
        bookId.startsWith("file://") ||
        bookId.startsWith("content://")
    ) {
        "local"
    } else {
        "remote"
    }

enum class ReaderUiRuntimeDispatchMode { SHADOW, PILOT, LOCAL_CANDIDATE }

/** Runtime counters. Pilot and shadow both count as covered canonical events. */
data class ReaderUiRuntimeShadowMetrics(
    val covered: Long = 0,
    val fallback: Long = 0,
    val runtimeError: Long = 0,
    val mismatch: Long = 0
)

/** Last covered runtime transition, retained for diagnostics and JVM proof. */
data class ReaderUiRuntimeShadowObservation(
    val event: String,
    val correlationId: String?,
    val runtimeState: ReaderUIState?,
    val runtimeEffects: List<ReaderUIEffect>,
    val mismatches: List<String>,
    /** Runtime-owned supersession evidence for result-dependent transactions. */
    val cancelledCorrelationIds: List<String> = emptyList(),
    val mode: ReaderUiRuntimeDispatchMode = ReaderUiRuntimeDispatchMode.SHADOW,
    val runtimeErrorCode: String? = null,
    val runtimeErrorMessage: String? = null
)

private sealed interface DirectoryNativeProjection {
    data class OpenRoute(val route: ReaderRoute.ReaderControl) : DirectoryNativeProjection
    data class OpenSheet(val content: SheetContent.ReaderSetting) : DirectoryNativeProjection
    data object CloseRoute : DirectoryNativeProjection
    data object CloseSheet : DirectoryNativeProjection
}

private data class CanonicalRuntimeDispatch(
    val event: String,
    val payload: ReaderUIJSONPayload = emptyMap(),
    val correlationId: String? = null,
    val directoryProjection: DirectoryNativeProjection? = null
)

internal sealed interface ReaderUiRuntimePilotResult {
    data object NotPilot : ReaderUiRuntimePilotResult

    data class Applied(
        val productionState: ReaderUiState,
        val changed: Boolean
    ) : ReaderUiRuntimePilotResult

    /** Runtime rejected or failed the event; production state must remain unchanged. */
    data object FailedClosed : ReaderUiRuntimePilotResult
}

/** Semantic delta derived from the canonical Runtime state transition. */
internal enum class ReaderControlCandidateDelta { SHOW, HIDE, SWITCH, NO_OP }

/**
 * Result of the schema-3 local candidate adapter. This result contains no
 * extra overlay/module state: [productionState] is produced exclusively by
 * the existing [ReaderUiReducer] and its Reader control intents.
 */
internal sealed interface ReaderControlCandidateDispatch {
    data object NotEnabled : ReaderControlCandidateDispatch

    data class Applied(
        val productionState: ReaderUiState,
        val delta: ReaderControlCandidateDelta,
        val motionId: String?,
        val changed: Boolean
    ) : ReaderControlCandidateDispatch

    /** Runtime or projection rejected the candidate; native state is unchanged. */
    data object FailedClosed : ReaderControlCandidateDispatch
}

/** Separate, opt-in transaction Pilot; it is intentionally absent from the lock-backed directory cohort. */
internal sealed interface ReaderBookOpenPilotDispatch {
    data object NotEnabled : ReaderBookOpenPilotDispatch
    data class Applied(val transition: ReaderUITransition) : ReaderBookOpenPilotDispatch
    data object FailedClosed : ReaderBookOpenPilotDispatch
}

/**
 * Long-lived coordinator for the actual [AppShellViewModel] dispatch path.
 *
 * Directory Pilot ownership:
 *
 * 1. The canonical directory event is dispatched to [ReaderUIRuntime] first.
 * 2. A successful transition is projected narrowly to the existing Android
 *    ReaderControl directory route/sheet; [ReaderUiReducer] is not called.
 * 3. Directory runtime failures fail closed and leave production state intact.
 * 4. The pair cannot execute Core/Host effects.
 *
 * Book-open and playback have their own production Pilot gates. Import,
 * source-switch, replace-rule, RSS, Sync, and page events remain diagnostic
 * live-shadow observations after native production commits. Setting
 * `readerUiDirectoryPilotEnabled=false` restores the directory native + shadow
 * path without changing any other cohort.
 */
internal class ReaderUiRuntimeCoordinator(
    private val runtime: ReaderUIRuntime = ReaderUIRuntime(),
    coveredEvents: Set<String> = READER_UI_RUNTIME_COVERED_EVENTS,
    val directoryPilotEnabled: Boolean = BuildConfig.READER_UI_DIRECTORY_PILOT_ENABLED,
    /** Default true: lock-backed production Pilot for `book.open`. */
    val bookOpenPilotEnabled: Boolean = BuildConfig.READER_UI_BOOK_OPEN_PILOT_ENABLED,
    /** Default true: lock-backed production Pilot for TTS/auto-page. */
    val playbackPilotEnabled: Boolean = BuildConfig.READER_UI_PLAYBACK_PILOT_ENABLED,
    /** Experimental only; production default false and absent from the lock cohorts. */
    val importPilotEnabled: Boolean = BuildConfig.READER_UI_IMPORT_PILOT_ENABLED,
    /** Experimental only; production default false and absent from the lock cohorts. */
    val sourceSwitchPilotEnabled: Boolean = BuildConfig.READER_UI_SOURCE_SWITCH_PILOT_ENABLED,
    /** Experimental only; production default false and absent from the lock cohorts. */
    val replaceRulePilotEnabled: Boolean = BuildConfig.READER_UI_REPLACE_RULE_PILOT_ENABLED,
    /** Experimental only; production default false and absent from the lock cohorts. */
    val rssPilotEnabled: Boolean = BuildConfig.READER_UI_RSS_PILOT_ENABLED,
    /**
     * Experimental Sync Pilot seam. Production defaults to false and has no
     * sync event/executor wiring, so the seven covered events remain Shadow.
     */
    val syncPilotEnabled: Boolean = BuildConfig.READER_UI_SYNC_PILOT_ENABLED,
    /**
     * Local/test-only schema-3 reader-control seam. It deliberately has no
     * BuildConfig or production caller so it cannot bypass the schema-2 lock.
     */
    val readerControlLocalCandidateEnabled: Boolean = false,
    private val runtimeDispatchOverride: ((
        event: String,
        payload: ReaderUIJSONPayload,
        correlationId: String?
    ) -> ReaderUITransition)? = null
) : ReaderBookOpenRuntimeDriver, ReaderPlaybackRuntimeDriver, ReaderImportRuntimeDriver, ReaderSourceSwitchRuntimeDriver, ReaderReplaceRuleRuntimeDriver, ReaderRssPilotRuntimeDriver, ReaderSyncPilotRuntimeDriver {
    val coveredEvents: Set<String> = coveredEvents.toSet()
    val pilotEvents: Set<String> = if (directoryPilotEnabled) {
        READER_UI_RUNTIME_DIRECTORY_PILOT_EVENTS
    } else {
        emptySet()
    }

    @Volatile
    private var metricsSnapshot: ReaderUiRuntimeShadowMetrics = ReaderUiRuntimeShadowMetrics()

    @Volatile
    private var observationSnapshot: ReaderUiRuntimeShadowObservation? = null

    /** Active import correlation; the stale-result guard rejects superseded ids. */
    @Volatile
    private var activeImportCorrelationId: String? = null

    /** Active source switch correlation; the stale-result guard rejects superseded ids. */
    @Volatile
    private var activeSourceSwitchCorrelationId: String? = null

    @Volatile
    private var activeSourceSwitchEvent: String? = null

    /** Active replace rule correlation; the stale-result guard rejects superseded ids. */
    @Volatile
    private var activeReplaceRuleCorrelationId: String? = null

    @Volatile
    private var activeReplaceRuleEvent: String? = null

    /** Active RSS correlation; the stale-result guard rejects superseded ids. */
    @Volatile
    private var activeRssCorrelationId: String? = null

    @Volatile
    private var activeRssEvent: String? = null

    /** Active sync correlation; the stale-result guard rejects superseded ids. */
    @Volatile
    private var activeSyncCorrelationId: String? = null

    @Volatile
    private var activeSyncEvent: String? = null

    private val activeSyncPendingCoreTypes: MutableSet<String> = linkedSetOf()

    val metrics: ReaderUiRuntimeShadowMetrics
        get() = metricsSnapshot

    val lastObservation: ReaderUiRuntimeShadowObservation?
        get() = observationSnapshot

    /** Runtime semantic state; directory overlay ownership lives here in Pilot mode. */
    val runtimeState: ReaderUIState
        get() = runtime.state

    override val playbackRuntimeState: ReaderUIState
        get() = runtime.state

    override val importRuntimeState: ReaderUIState
        get() = runtime.state

    override val sourceSwitchRuntimeState: ReaderUIState
        get() = runtime.state

    override val replaceRuleRuntimeState: ReaderUIState
        get() = runtime.state

    override val rssRuntimeState: ReaderUIState
        get() = runtime.state

    override val syncRuntimeState: ReaderUIState
        get() = runtime.state

    init {
        val unknown = this.coveredEvents - GeneratedRuntimeActions.byEvent.keys
        require(unknown.isEmpty()) {
            "Android runtime allowlist references non-generated events: ${unknown.sorted()}"
        }
        require(READER_UI_RUNTIME_DIRECTORY_PILOT_EVENTS.all(this.coveredEvents::contains)) {
            "Android directory Pilot pair must remain covered as one cohort"
        }
        require(READER_UI_RUNTIME_SOURCE_SWITCH_PILOT_EVENTS.all(this.coveredEvents::contains)) {
            "Android source switch Pilot events must remain covered as one cohort"
        }
        require(READER_UI_RUNTIME_REPLACE_RULE_PILOT_EVENTS.all(this.coveredEvents::contains)) {
            "Android replace rule Pilot events must remain covered as one cohort"
        }
        require(READER_UI_RUNTIME_RSS_PILOT_EVENTS.all(this.coveredEvents::contains)) {
            "Android RSS Pilot events must remain covered as one cohort"
        }
        require(READER_UI_RUNTIME_SYNC_PILOT_EVENTS.all(this.coveredEvents::contains)) {
            "Android sync Pilot events must remain covered as one cohort"
        }
        require(
            READER_UI_RUNTIME_READER_CONTROL_LOCAL_CANDIDATE_EVENTS
                .intersect(this.coveredEvents)
                .isEmpty()
        ) {
            "Schema-3 Reader control candidates must stay outside the schema-2 production allowlist"
        }
        if (readerControlLocalCandidateEnabled) {
            require(GeneratedRuntimeActions.schemaVersion >= 3) {
                "Reader control local candidates require Runtime Actions schema 3"
            }
            require(
                READER_UI_RUNTIME_READER_CONTROL_LOCAL_CANDIDATE_EVENTS
                    .all(GeneratedRuntimeActions.byEvent::containsKey)
            ) {
                "Reader control local candidates must be generated Runtime actions"
            }
        }
    }

    /**
     * Consumes the two schema-3 Reader control actions through a deliberately
     * explicit local-candidate entry point. The current production dispatch
     * path cannot reach this method and the candidate events remain absent
     * from [coveredEvents]/[pilotEvents] while the consumer lock is schema 2.
     *
     * Runtime owns the atomic overlay transition. Android then projects only
     * that delta through the existing Reader control intents and reducer.
     * Route/tab/stack changes, effects, foreign overlays, and wrong routes all
     * fail closed with [productionBefore] left untouched.
     */
    @Synchronized
    internal fun dispatchReaderControlLocalCandidate(
        event: String,
        payload: ReaderUIJSONPayload,
        productionBefore: ReaderUiState
    ): ReaderControlCandidateDispatch {
        if (!readerControlLocalCandidateEnabled) return ReaderControlCandidateDispatch.NotEnabled
        if (event !in READER_UI_RUNTIME_READER_CONTROL_LOCAL_CANDIDATE_EVENTS) {
            return ReaderControlCandidateDispatch.NotEnabled
        }

        val canonical = CanonicalRuntimeDispatch(event = event, payload = payload)
        var candidateRuntime: ReaderUIRuntime? = null
        return try {
            if (productionBefore.currentRoute.routeId != RouteIds.IMMERSIVE_READING) {
                throw ReaderUIRuntimeException(
                    "READER_ROUTE_GUARD",
                    "$event requires the Android immersive-reading route"
                )
            }

            val hydratedState = hydrateReaderControlCandidateState(productionBefore)
            candidateRuntime = ReaderUIRuntime(hydratedState)
            val transition = candidateRuntime.dispatchJSON(
                event = canonical.event,
                jsonPayload = canonical.payload,
                correlationId = canonical.correlationId
            )
            if (transition.effects.isNotEmpty()) {
                throw ReaderUIRuntimeException(
                    "READER_CONTROL_EFFECT_BOUNDARY",
                    "$event unexpectedly emitted Core/Host effects"
                )
            }
            validateReaderControlRuntimeBoundary(event, transition)
            val delta = deriveReaderControlCandidateDelta(event, transition)
            val productionAfter = projectReaderControlCandidate(
                delta = delta,
                runtimeAfter = transition.state,
                productionBefore = productionBefore
            )
            if (productionAfter.copy(readerControl = productionBefore.readerControl) != productionBefore) {
                throw ReaderUIRuntimeException(
                    "READER_CONTROL_PROJECTION_BOUNDARY",
                    "$event mutated Android state outside ReaderControlState"
                )
            }

            val motionId = when (delta) {
                ReaderControlCandidateDelta.SHOW -> MotionIdConstants.READER_CONTROL_SHOW
                ReaderControlCandidateDelta.HIDE -> MotionIdConstants.READER_CONTROL_HIDE
                ReaderControlCandidateDelta.SWITCH -> MotionIdConstants.READER_MODULE_SWITCH
                ReaderControlCandidateDelta.NO_OP -> null
            }
            observationSnapshot = ReaderUiRuntimeShadowObservation(
                event = event,
                correlationId = null,
                runtimeState = transition.state,
                runtimeEffects = transition.effects,
                mismatches = emptyList(),
                mode = ReaderUiRuntimeDispatchMode.LOCAL_CANDIDATE
            )
            ReaderControlCandidateDispatch.Applied(
                productionState = productionAfter,
                delta = delta,
                motionId = motionId,
                changed = productionAfter != productionBefore
            )
        } catch (error: Exception) {
            recordRuntimeError(
                canonical = canonical,
                mode = ReaderUiRuntimeDispatchMode.LOCAL_CANDIDATE,
                error = error,
                runtimeState = candidateRuntime?.state ?: runtime.state
            )
            ReaderControlCandidateDispatch.FailedClosed
        }
    }

    /**
     * Handle the directory Pilot before the native reducer. [NotPilot] means the
     * caller must continue through the existing native path exactly once.
     */
    @Synchronized
    internal fun dispatchPilot(
        intent: ReaderUiIntent,
        productionBefore: ReaderUiState
    ): ReaderUiRuntimePilotResult {
        if (!directoryPilotEnabled) return ReaderUiRuntimePilotResult.NotPilot
        val canonical = canonicalPilotDispatch(intent, productionBefore)
            ?: return ReaderUiRuntimePilotResult.NotPilot
        if (canonical.event !in pilotEvents) return ReaderUiRuntimePilotResult.NotPilot

        metricsSnapshot = metricsSnapshot.copy(covered = metricsSnapshot.covered + 1)
        return try {
            val transition = dispatchRuntime(canonical)
            if (transition.effects.isNotEmpty()) {
                throw ReaderUIRuntimeException(
                    "PILOT_EFFECT_BOUNDARY",
                    "${canonical.event} unexpectedly emitted Core/Host effects"
                )
            }
            val productionAfter = projectDirectoryTransition(
                canonical = canonical,
                transition = transition,
                productionBefore = productionBefore
            )
            val mismatches = compareStableProjection(
                canonical = canonical,
                transition = transition,
                productionBefore = productionBefore,
                productionAfter = productionAfter
            )
            if (mismatches.isNotEmpty()) {
                metricsSnapshot = metricsSnapshot.copy(mismatch = metricsSnapshot.mismatch + 1)
            }
            observationSnapshot = ReaderUiRuntimeShadowObservation(
                event = canonical.event,
                correlationId = canonical.correlationId,
                runtimeState = transition.state,
                runtimeEffects = transition.effects,
                mismatches = mismatches,
                mode = ReaderUiRuntimeDispatchMode.PILOT
            )
            ReaderUiRuntimePilotResult.Applied(
                productionState = productionAfter,
                changed = productionAfter != productionBefore
            )
        } catch (error: Exception) {
            recordRuntimeError(canonical, ReaderUiRuntimeDispatchMode.PILOT, error)
            ReaderUiRuntimePilotResult.FailedClosed
        }
    }

    /**
     * Starts the 2.5 result-dependent `book.open` transaction before native
     * presentation projection. This has its own default-off switch and must
     * not mutate the directory Pilot cohort or its consumer lock.
     */
    @Synchronized
    internal fun dispatchBookOpenPilot(intent: ReaderUiIntent): ReaderBookOpenPilotDispatch {
        if (!bookOpenPilotEnabled) return ReaderBookOpenPilotDispatch.NotEnabled
        val canonical = canonicalBookOpenDispatch(intent) ?: return ReaderBookOpenPilotDispatch.NotEnabled
        var runtimeDispatchCompleted = false
        return try {
            val transition = dispatchRuntime(canonical)
            runtimeDispatchCompleted = true
            val firstEffect = transition.effects.singleOrNull()
            if (
                firstEffect?.kind != ReaderUIEffectKind.CORE ||
                firstEffect.correlationId != canonical.correlationId
            ) {
                throw ReaderUIRuntimeException(
                    "BOOK_OPEN_EFFECT_BOUNDARY",
                    "book.open must emit exactly one correlated next Core effect"
                )
            }
            observationSnapshot = ReaderUiRuntimeShadowObservation(
                event = canonical.event,
                correlationId = canonical.correlationId,
                runtimeState = transition.state,
                runtimeEffects = transition.effects,
                mismatches = emptyList(),
                cancelledCorrelationIds = transition.cancelledCorrelationIds,
                mode = ReaderUiRuntimeDispatchMode.PILOT
            )
            ReaderBookOpenPilotDispatch.Applied(transition)
        } catch (error: Exception) {
            // `dispatchRuntime` may already have installed the transaction
            // before a post-dispatch boundary check rejects it. Keep Pilot
            // fail-closed in both layers: no orphaned Runtime ledger may
            // survive a failed admission.
            if (runtimeDispatchCompleted) canonical.correlationId?.let(runtime::cancelBookOpen)
            recordRuntimeError(canonical, ReaderUiRuntimeDispatchMode.PILOT, error)
            ReaderBookOpenPilotDispatch.FailedClosed
        }
    }

    /**
     * Intercepts all six playback events before the native reducer only when
     * the paired default-off Pilot is explicitly enabled. A Runtime guard or
     * boundary failure is terminal for the event; there is no legacy fallback.
     */
    @Synchronized
    internal fun dispatchPlaybackPilot(intent: ReaderUiIntent): ReaderPlaybackPilotDispatch {
        if (!playbackPilotEnabled) return ReaderPlaybackPilotDispatch.NotEnabled
        val canonical = canonicalPlaybackDispatch(intent) ?: return ReaderPlaybackPilotDispatch.NotEnabled
        metricsSnapshot = metricsSnapshot.copy(covered = metricsSnapshot.covered + 1)
        return try {
            val transition = dispatchRuntime(canonical)
            validatePlaybackEffectBoundary(canonical.event, transition.effects)
            observationSnapshot = ReaderUiRuntimeShadowObservation(
                event = canonical.event,
                correlationId = canonical.correlationId,
                runtimeState = transition.state,
                runtimeEffects = transition.effects,
                mismatches = emptyList(),
                cancelledCorrelationIds = transition.cancelledCorrelationIds,
                mode = ReaderUiRuntimeDispatchMode.PILOT
            )
            ReaderPlaybackPilotDispatch.Applied(
                event = canonical.event,
                state = transition.state,
                effects = transition.effects,
                cancelledCorrelationIds = transition.cancelledCorrelationIds
            )
        } catch (error: Exception) {
            recordRuntimeError(canonical, ReaderUiRuntimeDispatchMode.PILOT, error)
            ReaderPlaybackPilotDispatch.FailedClosed
        }
    }

    /**
     * Experimental import Pilot entry point. Each of the
     * three import events emits exactly one next Core effect; the coordinator
     * fail-closes and clears the active correlation on any boundary violation.
     */
    @Synchronized
    internal fun dispatchImportPilot(intent: ReaderUiIntent): ReaderImportPilotDispatch {
        if (!importPilotEnabled) return ReaderImportPilotDispatch.NotEnabled
        if (legacyUntypedImportEvent(intent) != null) {
            recordUntypedImportBridgeFailure(intent, ReaderUiRuntimeDispatchMode.PILOT)
            return ReaderImportPilotDispatch.FailedClosed
        }
        val canonical = canonicalImportDispatch(intent) ?: return ReaderImportPilotDispatch.NotEnabled
        var runtimeDispatchCompleted = false
        return try {
            val transition = dispatchRuntime(canonical)
            runtimeDispatchCompleted = true
            val firstEffect = transition.effects.singleOrNull()
            if (
                firstEffect?.kind != ReaderUIEffectKind.CORE ||
                firstEffect.correlationId != canonical.correlationId
            ) {
                throw ReaderUIRuntimeException(
                    "IMPORT_EFFECT_BOUNDARY",
                    "${canonical.event} must emit exactly one correlated next Core effect"
                )
            }
            activeImportCorrelationId = canonical.correlationId
            observationSnapshot = ReaderUiRuntimeShadowObservation(
                event = canonical.event,
                correlationId = canonical.correlationId,
                runtimeState = transition.state,
                runtimeEffects = transition.effects,
                mismatches = emptyList(),
                cancelledCorrelationIds = transition.cancelledCorrelationIds,
                mode = ReaderUiRuntimeDispatchMode.PILOT
            )
            ReaderImportPilotDispatch.Applied(transition)
        } catch (error: Exception) {
            if (runtimeDispatchCompleted) canonical.correlationId?.let(::cancelImport)
            recordRuntimeError(canonical, ReaderUiRuntimeDispatchMode.PILOT, error)
            ReaderImportPilotDispatch.FailedClosed
        }
    }

    /**
     * Experimental source-switch Pilot entry point. Two semantic groups share
     * this dispatch:
     *
     * - Overlay cohort (effectPolicy: "none"): 4 route/overlay events. The
     *   coordinator projects the route/overlay change and returns
     *   [ReaderSourceSwitchPilotDispatch.OverlayApplied]; the native reducer
     *   must not run.
     * - Effect cohort (effectPolicy: "exactly-once"): 2 effectful events.
     *   Each emits exactly one next Core effect; the coordinator validates the
     *   boundary, stores the active correlation, and returns
     *   [ReaderSourceSwitchPilotDispatch.EffectApplied] for the executor.
     */
    @Synchronized
    internal fun dispatchSourceSwitchPilot(
        intent: ReaderUiIntent,
        productionBefore: ReaderUiState
    ): ReaderSourceSwitchPilotDispatch {
        if (!sourceSwitchPilotEnabled) return ReaderSourceSwitchPilotDispatch.NotEnabled
        if (legacyUntypedSourceSwitchEvent(intent) != null) {
            recordUntypedSourceSwitchBridgeFailure(intent, ReaderUiRuntimeDispatchMode.PILOT)
            return ReaderSourceSwitchPilotDispatch.FailedClosed
        }
        val canonical = canonicalSourceSwitchDispatch(intent, productionBefore)
            ?: return ReaderSourceSwitchPilotDispatch.NotEnabled
        metricsSnapshot = metricsSnapshot.copy(covered = metricsSnapshot.covered + 1)
        return try {
            val transition = dispatchRuntime(canonical)
            if (canonical.event in READER_UI_RUNTIME_SOURCE_SWITCH_EFFECT_PILOT_EVENTS) {
                val firstEffect = transition.effects.singleOrNull()
                if (
                    firstEffect?.kind != ReaderUIEffectKind.CORE ||
                    firstEffect.correlationId != canonical.correlationId
                ) {
                    throw ReaderUIRuntimeException(
                        "SOURCE_SWITCH_EFFECT_BOUNDARY",
                        "${canonical.event} must emit exactly one correlated next Core effect"
                    )
                }
                activeSourceSwitchCorrelationId = canonical.correlationId
                activeSourceSwitchEvent = canonical.event
                observationSnapshot = ReaderUiRuntimeShadowObservation(
                    event = canonical.event,
                    correlationId = canonical.correlationId,
                    runtimeState = transition.state,
                    runtimeEffects = transition.effects,
                    mismatches = emptyList(),
                    cancelledCorrelationIds = transition.cancelledCorrelationIds,
                    mode = ReaderUiRuntimeDispatchMode.PILOT
                )
                ReaderSourceSwitchPilotDispatch.EffectApplied(transition)
            } else {
                if (transition.effects.isNotEmpty()) {
                    throw ReaderUIRuntimeException(
                        "SOURCE_SWITCH_OVERLAY_BOUNDARY",
                        "${canonical.event} unexpectedly emitted Core/Host effects"
                    )
                }
                val productionAfter = projectSourceSwitchTransition(
                    canonical = canonical,
                    transition = transition,
                    productionBefore = productionBefore
                )
                observationSnapshot = ReaderUiRuntimeShadowObservation(
                    event = canonical.event,
                    correlationId = canonical.correlationId,
                    runtimeState = transition.state,
                    runtimeEffects = transition.effects,
                    mismatches = emptyList(),
                    mode = ReaderUiRuntimeDispatchMode.PILOT
                )
                ReaderSourceSwitchPilotDispatch.OverlayApplied(
                    productionState = productionAfter,
                    changed = productionAfter != productionBefore
                )
            }
        } catch (error: Exception) {
            if (canonical.event in READER_UI_RUNTIME_SOURCE_SWITCH_EFFECT_PILOT_EVENTS) {
                canonical.correlationId?.let(::cancelSourceSwitch)
            }
            recordRuntimeError(canonical, ReaderUiRuntimeDispatchMode.PILOT, error)
            ReaderSourceSwitchPilotDispatch.FailedClosed
        }
    }

    /** Lossless test/future-integration seam for the two effectful W3 events. */
    @Synchronized
    internal fun dispatchSourceSwitchPilotJSON(
        event: String,
        payload: ReaderUIJSONPayload,
        correlationId: String?
    ): ReaderSourceSwitchPilotDispatch {
        if (!sourceSwitchPilotEnabled) return ReaderSourceSwitchPilotDispatch.NotEnabled
        if (event !in READER_UI_RUNTIME_SOURCE_SWITCH_EFFECT_PILOT_EVENTS) {
            return ReaderSourceSwitchPilotDispatch.NotEnabled
        }
        val canonical = CanonicalRuntimeDispatch(event, payload, correlationId)
        metricsSnapshot = metricsSnapshot.copy(covered = metricsSnapshot.covered + 1)
        var runtimeDispatchCompleted = false
        return try {
            val transition = dispatchRuntime(canonical)
            runtimeDispatchCompleted = true
            val effect = transition.effects.singleOrNull()
            if (effect?.kind != ReaderUIEffectKind.CORE || effect.correlationId != correlationId) {
                throw ReaderUIRuntimeException(
                    "SOURCE_SWITCH_EFFECT_BOUNDARY",
                    "$event must emit exactly one correlated next Core effect"
                )
            }
            activeSourceSwitchCorrelationId = correlationId
            activeSourceSwitchEvent = event
            observationSnapshot = ReaderUiRuntimeShadowObservation(
                event = event,
                correlationId = correlationId,
                runtimeState = transition.state,
                runtimeEffects = transition.effects,
                mismatches = emptyList(),
                cancelledCorrelationIds = transition.cancelledCorrelationIds,
                mode = ReaderUiRuntimeDispatchMode.PILOT
            )
            ReaderSourceSwitchPilotDispatch.EffectApplied(transition)
        } catch (error: Exception) {
            if (runtimeDispatchCompleted) correlationId?.let(::cancelSourceSwitch)
            recordRuntimeError(canonical, ReaderUiRuntimeDispatchMode.PILOT, error)
            ReaderSourceSwitchPilotDispatch.FailedClosed
        }
    }

    /**
     * Experimental replace-rules Pilot entry point.
     * Each of the three replace rule events emits exactly one next Core effect;
     * the coordinator fail-closes and clears the active correlation on any
     * boundary violation.
     */
    @Synchronized
    internal fun dispatchReplaceRulePilot(intent: ReaderUiIntent): ReaderReplaceRulePilotDispatch {
        if (!replaceRulePilotEnabled) return ReaderReplaceRulePilotDispatch.NotEnabled
        if (legacyUntypedReplaceRuleEvent(intent) != null) {
            recordUntypedReplaceRuleBridgeFailure(intent, ReaderUiRuntimeDispatchMode.PILOT)
            return ReaderReplaceRulePilotDispatch.FailedClosed
        }
        val canonical = canonicalReplaceRuleDispatch(intent) ?: return ReaderReplaceRulePilotDispatch.NotEnabled
        var runtimeDispatchCompleted = false
        return try {
            val transition = dispatchRuntime(canonical)
            runtimeDispatchCompleted = true
            val firstEffect = transition.effects.singleOrNull()
            if (
                firstEffect?.kind != ReaderUIEffectKind.CORE ||
                firstEffect.correlationId != canonical.correlationId
            ) {
                throw ReaderUIRuntimeException(
                    "REPLACE_RULE_EFFECT_BOUNDARY",
                    "${canonical.event} must emit exactly one correlated next Core effect"
                )
            }
            activeReplaceRuleCorrelationId = canonical.correlationId
            activeReplaceRuleEvent = canonical.event
            observationSnapshot = ReaderUiRuntimeShadowObservation(
                event = canonical.event,
                correlationId = canonical.correlationId,
                runtimeState = transition.state,
                runtimeEffects = transition.effects,
                mismatches = emptyList(),
                cancelledCorrelationIds = transition.cancelledCorrelationIds,
                mode = ReaderUiRuntimeDispatchMode.PILOT
            )
            ReaderReplaceRulePilotDispatch.Applied(transition)
        } catch (error: Exception) {
            if (runtimeDispatchCompleted) canonical.correlationId?.let(::cancelReplaceRule)
            recordRuntimeError(canonical, ReaderUiRuntimeDispatchMode.PILOT, error)
            ReaderReplaceRulePilotDispatch.FailedClosed
        }
    }

    /** Lossless test/future-integration seam; production remains native + Shadow. */
    @Synchronized
    internal fun dispatchReplaceRulePilotJSON(
        event: String,
        payload: ReaderUIJSONPayload,
        correlationId: String?
    ): ReaderReplaceRulePilotDispatch {
        if (!replaceRulePilotEnabled) return ReaderReplaceRulePilotDispatch.NotEnabled
        if (event !in READER_UI_RUNTIME_REPLACE_RULE_PILOT_EVENTS) {
            return ReaderReplaceRulePilotDispatch.NotEnabled
        }
        val canonical = CanonicalRuntimeDispatch(event, payload, correlationId)
        var runtimeDispatchCompleted = false
        return try {
            val transition = dispatchRuntime(canonical)
            runtimeDispatchCompleted = true
            val effect = transition.effects.singleOrNull()
            if (effect?.kind != ReaderUIEffectKind.CORE || effect.correlationId != correlationId) {
                throw ReaderUIRuntimeException(
                    "REPLACE_RULE_EFFECT_BOUNDARY",
                    "$event must emit exactly one correlated next Core effect"
                )
            }
            activeReplaceRuleCorrelationId = correlationId
            activeReplaceRuleEvent = event
            observationSnapshot = ReaderUiRuntimeShadowObservation(
                event = event,
                correlationId = correlationId,
                runtimeState = transition.state,
                runtimeEffects = transition.effects,
                mismatches = emptyList(),
                cancelledCorrelationIds = transition.cancelledCorrelationIds,
                mode = ReaderUiRuntimeDispatchMode.PILOT
            )
            ReaderReplaceRulePilotDispatch.Applied(transition)
        } catch (error: Exception) {
            if (runtimeDispatchCompleted) correlationId?.let(::cancelReplaceRule)
            recordRuntimeError(canonical, ReaderUiRuntimeDispatchMode.PILOT, error)
            ReaderReplaceRulePilotDispatch.FailedClosed
        }
    }

    /**
     * Experimental RSS Pilot entry point. Each of the seven
     * RSS events emits exactly one next Core effect; the coordinator
     * fail-closes and clears the active correlation on any boundary violation.
     * Unlike the intent-based pilots above, RSS events are dispatched directly
     * by event name + payload because they originate from the RSS UI screens
     * rather than the canonical [ReaderUiIntent] reducer path.
     */
    @Synchronized
    internal fun dispatchRssPilot(
        event: String,
        payload: ReaderUIJSONPayload,
        correlationId: String?
    ): ReaderRssPilotDispatch {
        if (!rssPilotEnabled) return ReaderRssPilotDispatch.NotEnabled
        if (event !in READER_UI_RUNTIME_RSS_PILOT_EVENTS) return ReaderRssPilotDispatch.NotEnabled
        metricsSnapshot = metricsSnapshot.copy(covered = metricsSnapshot.covered + 1)
        var runtimeDispatchCompleted = false
        return try {
            val transition = dispatchRuntime(
                CanonicalRuntimeDispatch(
                    event = event,
                    payload = payload,
                    correlationId = correlationId
                )
            )
            runtimeDispatchCompleted = true
            val firstEffect = transition.effects.singleOrNull()
            if (
                firstEffect?.kind != ReaderUIEffectKind.CORE ||
                firstEffect.correlationId != correlationId
            ) {
                throw ReaderUIRuntimeException(
                    "RSS_EFFECT_BOUNDARY",
                    "$event must emit exactly one correlated next Core effect"
                )
            }
            activeRssCorrelationId = correlationId
            activeRssEvent = event
            observationSnapshot = ReaderUiRuntimeShadowObservation(
                event = event,
                correlationId = correlationId,
                runtimeState = transition.state,
                runtimeEffects = transition.effects,
                mismatches = emptyList(),
                cancelledCorrelationIds = transition.cancelledCorrelationIds,
                mode = ReaderUiRuntimeDispatchMode.PILOT
            )
            ReaderRssPilotDispatch.Dispatched(transition)
        } catch (error: Exception) {
            if (runtimeDispatchCompleted) correlationId?.let(::cancelRss)
            recordRuntimeError(
                CanonicalRuntimeDispatch(event = event, payload = payload, correlationId = correlationId),
                ReaderUiRuntimeDispatchMode.PILOT,
                error
            )
            ReaderRssPilotDispatch.FailedClosed
        }
    }

    /**
     * Exercises the dormant Sync Pilot seam when a test or future integration
     * explicitly enables it. The current production UI does not call this
     * method or wire [ReaderSyncEffectExecutor]; default-Shadow therefore
     * remains the only declared production mode for these seven events.
     */
    @Synchronized
    internal fun dispatchSyncPilot(
        event: String,
        payload: ReaderUIJSONPayload,
        correlationId: String?
    ): ReaderSyncPilotDispatch {
        if (!syncPilotEnabled) return ReaderSyncPilotDispatch.NotEnabled
        if (event !in READER_UI_RUNTIME_SYNC_PILOT_EVENTS) return ReaderSyncPilotDispatch.NotEnabled
        metricsSnapshot = metricsSnapshot.copy(covered = metricsSnapshot.covered + 1)
        var runtimeDispatchCompleted = false
        return try {
            val transition = dispatchRuntime(
                CanonicalRuntimeDispatch(
                    event = event,
                    payload = payload,
                    correlationId = correlationId
                )
            )
            runtimeDispatchCompleted = true
            validateSyncEffectBoundary(event, transition.effects, correlationId)
            activeSyncCorrelationId = correlationId
            activeSyncEvent = event
            activeSyncPendingCoreTypes.clear()
            activeSyncPendingCoreTypes += READER_UI_RUNTIME_SYNC_PILOT_CORE_SEQUENCE.getValue(event)
            observationSnapshot = ReaderUiRuntimeShadowObservation(
                event = event,
                correlationId = correlationId,
                runtimeState = transition.state,
                runtimeEffects = transition.effects,
                mismatches = emptyList(),
                cancelledCorrelationIds = transition.cancelledCorrelationIds,
                mode = ReaderUiRuntimeDispatchMode.PILOT
            )
            ReaderSyncPilotDispatch.Dispatched(transition)
        } catch (error: Exception) {
            if (runtimeDispatchCompleted) correlationId?.let(::cancelSync)
            recordRuntimeError(
                CanonicalRuntimeDispatch(event = event, payload = payload, correlationId = correlationId),
                ReaderUiRuntimeDispatchMode.PILOT,
                error
            )
            ReaderSyncPilotDispatch.FailedClosed
        }
    }

    private fun validateSyncEffectBoundary(
        event: String,
        effects: List<ReaderUIEffect>,
        correlationId: String?
    ) {
        val expectedCore = READER_UI_RUNTIME_SYNC_PILOT_CORE_SEQUENCE[event]
            ?: throw ReaderUIRuntimeException(
                "SYNC_EFFECT_BOUNDARY",
                "$event has no expected coreSequence"
            )
        val expectedHost = READER_UI_RUNTIME_SYNC_PILOT_HOST_REQUEST[event]

        val coreEffects = effects.filter { it.kind == ReaderUIEffectKind.CORE }
        val hostEffects = effects.filter { it.kind == ReaderUIEffectKind.HOST }

        if (coreEffects.map { it.type } != expectedCore) {
            throw ReaderUIRuntimeException(
                "SYNC_EFFECT_BOUNDARY",
                "$event must emit Core sequence $expectedCore, got ${coreEffects.map { it.type }}"
            )
        }
        if (expectedHost != null) {
            if (hostEffects.size != 1 || hostEffects.single().type != expectedHost) {
                throw ReaderUIRuntimeException(
                    "SYNC_EFFECT_BOUNDARY",
                    "$event must emit exactly one Host effect $expectedHost, got ${hostEffects.map { it.type }}"
                )
            }
        } else {
            if (hostEffects.isNotEmpty()) {
                throw ReaderUIRuntimeException(
                    "SYNC_EFFECT_BOUNDARY",
                    "$event must not emit Host effects, got ${hostEffects.map { it.type }}"
                )
            }
        }
        if (effects.any { it.correlationId != correlationId }) {
            throw ReaderUIRuntimeException(
                "SYNC_EFFECT_BOUNDARY",
                "$event emitted an effect with a drifted correlationId"
            )
        }
    }

    /**
     * Observe one non-Pilot production dispatch after the native reducer has
     * committed. Runtime state/effects never feed back into production here.
     */
    @Synchronized
    fun observe(
        intent: ReaderUiIntent,
        productionBefore: ReaderUiState,
        productionAfter: ReaderUiState
    ) {
        // The legacy native import intents do not carry the discriminated W1
        // input, parse result/transaction, or rollback token required by R14.
        // Keep native state authoritative and record a Shadow fail-closed
        // observation without dispatching a fabricated Runtime payload.
        if (legacyUntypedImportEvent(intent) != null) {
            recordUntypedImportBridgeFailure(intent, ReaderUiRuntimeDispatchMode.SHADOW)
            return
        }
        if (legacyUntypedSourceSwitchEvent(intent) != null) {
            recordUntypedSourceSwitchBridgeFailure(intent, ReaderUiRuntimeDispatchMode.SHADOW)
            return
        }
        if (legacyUntypedReplaceRuleEvent(intent) != null) {
            recordUntypedReplaceRuleBridgeFailure(intent, ReaderUiRuntimeDispatchMode.SHADOW)
            return
        }
        val canonical = canonicalShadowDispatch(intent, productionBefore, productionAfter)
        if (canonical == null || canonical.event !in coveredEvents) {
            metricsSnapshot = metricsSnapshot.copy(fallback = metricsSnapshot.fallback + 1)
            return
        }
        check(canonical.event !in pilotEvents) {
            "Pilot event ${canonical.event} reached native reducer/shadow path"
        }

        metricsSnapshot = metricsSnapshot.copy(covered = metricsSnapshot.covered + 1)
        try {
            val transition = dispatchRuntime(canonical)
            val mismatches = compareStableProjection(
                canonical = canonical,
                transition = transition,
                productionBefore = productionBefore,
                productionAfter = productionAfter
            )
            if (mismatches.isNotEmpty()) {
                metricsSnapshot = metricsSnapshot.copy(mismatch = metricsSnapshot.mismatch + 1)
            }
            observationSnapshot = ReaderUiRuntimeShadowObservation(
                event = canonical.event,
                correlationId = canonical.correlationId,
                runtimeState = transition.state,
                runtimeEffects = transition.effects,
                mismatches = mismatches,
                cancelledCorrelationIds = transition.cancelledCorrelationIds,
                mode = ReaderUiRuntimeDispatchMode.SHADOW
            )
        } catch (error: Exception) {
            recordRuntimeError(canonical, ReaderUiRuntimeDispatchMode.SHADOW, error)
        }
    }

    private fun dispatchRuntime(canonical: CanonicalRuntimeDispatch): ReaderUITransition =
        runtimeDispatchOverride?.invoke(
            canonical.event,
            canonical.payload,
            canonical.correlationId
        ) ?: runtime.dispatchJSON(
            event = canonical.event,
            jsonPayload = canonical.payload,
            correlationId = canonical.correlationId
        )

    private fun validateReaderControlRuntimeBoundary(
        event: String,
        transition: ReaderUITransition
    ) {
        if (transition.event != event) {
            throw ReaderUIRuntimeException(
                "READER_CONTROL_EVENT_BOUNDARY",
                "$event returned transition ${transition.event}"
            )
        }
        if (
            transition.previous.routeId != RouteIds.IMMERSIVE_READING ||
            transition.state.routeId != transition.previous.routeId ||
            transition.state.routeStack != transition.previous.routeStack ||
            transition.state.tab != transition.previous.tab
        ) {
            throw ReaderUIRuntimeException(
                "READER_CONTROL_ROUTE_BOUNDARY",
                "$event must keep the Runtime route, stack, and tab unchanged"
            )
        }
        if (transition.state.copy(overlay = transition.previous.overlay) != transition.previous) {
            throw ReaderUIRuntimeException(
                "READER_CONTROL_STATE_BOUNDARY",
                "$event may mutate only the Runtime overlay"
            )
        }
    }

    /**
     * One-way admission projection from the existing native state into an
     * isolated Runtime candidate. The long-lived schema-2 Runtime is never
     * trusted to be coincidentally synchronized and is never mutated here.
     */
    private fun hydrateReaderControlCandidateState(production: ReaderUiState): ReaderUIState {
        if (production.overlayState !is OverlayState.None) {
            throw ReaderUIRuntimeException(
                "READER_CONTROL_OVERLAY_GUARD",
                "Reader control candidate cannot replace ${production.overlayState::class.java.simpleName}"
            )
        }
        if (production.readerControl.activeModule !in READER_CONTROL_MODULES) {
            throw ReaderUIRuntimeException(
                "READER_CONTROL_NATIVE_STATE_GUARD",
                "Unknown native Reader module ${production.readerControl.activeModule}"
            )
        }
        val semanticOverlay = when {
            !production.readerControl.visible -> null
            production.readerControl.phase == MotionPhase.LEAVING -> null
            else -> production.readerControl.activeModule
        }
        return runtime.state.copy(
            routeId = production.currentRoute.routeId,
            routeStack = production.backStack.map { it.routeId },
            tab = production.activeTab.routeId,
            overlay = semanticOverlay,
            reducedMotion = production.reducedMotion
        )
    }

    private fun deriveReaderControlCandidateDelta(
        event: String,
        transition: ReaderUITransition
    ): ReaderControlCandidateDelta {
        val before = transition.previous.overlay
        val after = transition.state.overlay
        return when (event) {
            "reader.control.toggle" -> when {
                before == null && after == "reader-control" -> ReaderControlCandidateDelta.SHOW
                before in READER_CONTROL_OVERLAY_FAMILY && after == null -> ReaderControlCandidateDelta.HIDE
                else -> throw ReaderUIRuntimeException(
                    "READER_CONTROL_DELTA_BOUNDARY",
                    "$event produced unsupported overlay delta $before -> $after"
                )
            }
            "reader.module.switch" -> {
                if (before !in READER_CONTROL_OVERLAY_FAMILY || after !in READER_CONTROL_OVERLAY_FAMILY) {
                    throw ReaderUIRuntimeException(
                        "READER_CONTROL_DELTA_BOUNDARY",
                        "$event produced unsupported overlay delta $before -> $after"
                    )
                }
                if (before == after) {
                    ReaderControlCandidateDelta.NO_OP
                } else {
                    ReaderControlCandidateDelta.SWITCH
                }
            }
            else -> throw ReaderUIRuntimeException(
                "READER_CONTROL_EVENT_BOUNDARY",
                "$event is not a Reader control candidate"
            )
        }
    }

    private fun projectReaderControlCandidate(
        delta: ReaderControlCandidateDelta,
        runtimeAfter: ReaderUIState,
        productionBefore: ReaderUiState
    ): ReaderUiState = when (delta) {
        ReaderControlCandidateDelta.SHOW -> ReaderUiReducer.reduce(
            productionBefore,
            ReaderUiIntent.ShowReaderControl
        )
        ReaderControlCandidateDelta.HIDE -> ReaderUiReducer.reduce(
            productionBefore,
            ReaderUiIntent.HideReaderControl
        )
        ReaderControlCandidateDelta.SWITCH -> ReaderUiReducer.reduce(
            productionBefore,
            ReaderUiIntent.SwitchReaderModule(module = requireNotNull(runtimeAfter.overlay))
        )
        ReaderControlCandidateDelta.NO_OP -> productionBefore
    }

    private fun recordRuntimeError(
        canonical: CanonicalRuntimeDispatch,
        mode: ReaderUiRuntimeDispatchMode,
        error: Exception,
        runtimeState: ReaderUIState = runtime.state
    ) {
        metricsSnapshot = metricsSnapshot.copy(runtimeError = metricsSnapshot.runtimeError + 1)
        observationSnapshot = ReaderUiRuntimeShadowObservation(
            event = canonical.event,
            correlationId = canonical.correlationId,
            runtimeState = runtimeState,
            runtimeEffects = emptyList(),
            mismatches = emptyList(),
            mode = mode,
            runtimeErrorCode = (error as? ReaderUIRuntimeException)?.code
                ?: error::class.java.simpleName,
            runtimeErrorMessage = error.message
        )
    }

    private fun canonicalPilotDispatch(
        intent: ReaderUiIntent,
        before: ReaderUiState
    ): CanonicalRuntimeDispatch? = when (intent) {
        is ReaderUiIntent.OpenSheet -> {
            val content = intent.content as? SheetContent.ReaderSetting
            if (content?.module == DIRECTORY_OVERLAY) {
                CanonicalRuntimeDispatch(
                    event = "reader.directory.open",
                    correlationId = intent.requestId,
                    directoryProjection = DirectoryNativeProjection.OpenSheet(content)
                )
            } else null
        }
        ReaderUiIntent.CloseSheet -> {
            if (before.hasDirectorySheet() && !before.hasDirectoryRoute()) {
                CanonicalRuntimeDispatch(
                    event = "reader.directory.close",
                    correlationId = intent.requestId,
                    directoryProjection = DirectoryNativeProjection.CloseSheet
                )
            } else null
        }
        is ReaderUiIntent.PushRoute -> {
            val route = intent.route as? ReaderRoute.ReaderControl
            if (route?.isDirectoryRoute() == true) {
                CanonicalRuntimeDispatch(
                    event = "reader.directory.open",
                    correlationId = intent.requestId,
                    directoryProjection = DirectoryNativeProjection.OpenRoute(route)
                )
            } else null
        }
        ReaderUiIntent.PopRoute -> {
            val remainingRoutes = before.backStack.dropLast(1)
            if (
                before.currentRoute.isDirectoryRoute() &&
                remainingRoutes.none { it.isDirectoryRoute() } &&
                !before.hasDirectorySheet()
            ) {
                CanonicalRuntimeDispatch(
                    event = "reader.directory.close",
                    correlationId = intent.requestId,
                    directoryProjection = DirectoryNativeProjection.CloseRoute
                )
            } else null
        }
        else -> null
    }

    private fun canonicalShadowDispatch(
        intent: ReaderUiIntent,
        before: ReaderUiState,
        after: ReaderUiState
    ): CanonicalRuntimeDispatch? = when (intent) {
        is ReaderUiIntent.EnterReaderFromCover,
        is ReaderUiIntent.EnterReaderFromAction -> canonicalBookOpenDispatch(intent)
        is ReaderUiIntent.OpenSheet -> {
            val module = (intent.content as? SheetContent.ReaderSetting)?.module
            if (module == DIRECTORY_OVERLAY) {
                CanonicalRuntimeDispatch("reader.directory.open", correlationId = intent.requestId)
            } else null
        }
        ReaderUiIntent.CloseSheet -> {
            if (before.hasDirectorySurface() && !after.hasDirectorySurface()) {
                CanonicalRuntimeDispatch("reader.directory.close", correlationId = intent.requestId)
            } else null
        }
        is ReaderUiIntent.PushRoute -> {
            if (intent.route.isDirectoryRoute()) {
                CanonicalRuntimeDispatch("reader.directory.open", correlationId = intent.requestId)
            } else null
        }
        ReaderUiIntent.PopRoute -> {
            if (before.hasDirectorySurface() && !after.hasDirectorySurface()) {
                CanonicalRuntimeDispatch("reader.directory.close", correlationId = intent.requestId)
            } else null
        }
        is ReaderUiIntent.StartTtsSession -> CanonicalRuntimeDispatch(
            event = "reader.tts.start",
            // Pending transaction semantics: typed chapter/content is bound
            // from Host DomainContext, never copied from the UI text payload.
            payload = emptyMap(),
            correlationId = intent.requestId
        )
        is ReaderUiIntent.StartAutoPageSession -> CanonicalRuntimeDispatch(
            event = "reader.autoPage.start",
            payload = mapOf("intervalMs" to JsonPrimitive(READER_AUTO_PAGE_DEFAULT_INTERVAL_MS)),
            correlationId = intent.requestId
        )
        is ReaderUiIntent.TurnPageNext -> CanonicalRuntimeDispatch(
            event = "reader.page.next",
            correlationId = intent.requestId
        )
        is ReaderUiIntent.TurnPagePrev -> CanonicalRuntimeDispatch(
            event = "reader.page.prev",
            correlationId = intent.requestId
        )
        ReaderUiIntent.StopSession -> when (before.activeSession?.type) {
            SessionType.AUTO_PAGE -> CanonicalRuntimeDispatch("reader.autoPage.stop")
            SessionType.TTS -> CanonicalRuntimeDispatch("reader.tts.stop")
            SessionType.NONE, null -> CanonicalRuntimeDispatch("reader.tts.stop")
        }
        is ReaderUiIntent.SourceSwitchOpen -> CanonicalRuntimeDispatch(
            event = "source.switch.open",
            correlationId = intent.requestId
        )
        ReaderUiIntent.SourceSwitchClose -> CanonicalRuntimeDispatch(
            event = "source.switch.cancel",
            correlationId = intent.requestId
        )
        is ReaderUiIntent.SourceSwitchConfirm -> CanonicalRuntimeDispatch(
            event = "source.switch.confirm",
            payload = mapOf("sourceId" to JsonPrimitive(intent.sourceId)),
            correlationId = intent.requestId
        )
        ReaderUiIntent.SourceSwitchCancel -> CanonicalRuntimeDispatch(
            event = "source.switch.rollback",
            correlationId = intent.requestId
        )
        else -> null
    }

    private fun canonicalPlaybackDispatch(intent: ReaderUiIntent): CanonicalRuntimeDispatch? = when (intent) {
        is ReaderUiIntent.TurnPageNext -> CanonicalRuntimeDispatch(
            "reader.page.next",
            correlationId = intent.requestId
        )
        is ReaderUiIntent.TurnPagePrev -> CanonicalRuntimeDispatch(
            "reader.page.prev",
            correlationId = intent.requestId
        )
        is ReaderUiIntent.StartTtsSession -> CanonicalRuntimeDispatch(
            "reader.tts.start",
            correlationId = intent.requestId
        )
        is ReaderUiIntent.StartAutoPageSession -> CanonicalRuntimeDispatch(
            "reader.autoPage.start",
            payload = mapOf("intervalMs" to JsonPrimitive(READER_AUTO_PAGE_DEFAULT_INTERVAL_MS)),
            correlationId = intent.requestId
        )
        ReaderUiIntent.StopSession -> when {
            runtime.state.autoPageTransaction != null -> CanonicalRuntimeDispatch("reader.autoPage.stop")
            runtime.state.ttsTransaction != null -> CanonicalRuntimeDispatch("reader.tts.stop")
            else -> CanonicalRuntimeDispatch("reader.tts.stop")
        }
        else -> null
    }

    private fun validatePlaybackEffectBoundary(event: String, effects: List<ReaderUIEffect>) {
        val types = effects.map(ReaderUIEffect::type)
        when (event) {
            "reader.page.next", "reader.page.prev" -> if (types.any { it == "reader.location.resolve" }) {
                throw ReaderUIRuntimeException(
                    "PLAYBACK_EFFECT_BOUNDARY",
                    "$event emitted location before measured layout"
                )
            }
            "reader.tts.start" -> if (types.lastOrNull() != "tts.queue.plan") {
                throw ReaderUIRuntimeException(
                    "PLAYBACK_EFFECT_BOUNDARY",
                    "reader.tts.start must finish admission with tts.queue.plan"
                )
            }
            "reader.autoPage.start" -> if (types.lastOrNull() != "timer.foreground.arm") {
                throw ReaderUIRuntimeException(
                    "PLAYBACK_EFFECT_BOUNDARY",
                    "reader.autoPage.start must arm one foreground timer"
                )
            }
        }
        if (effects.any { it.correlationId.isNullOrBlank() }) {
            throw ReaderUIRuntimeException(
                "PLAYBACK_EFFECT_BOUNDARY",
                "$event emitted an uncorrelated effect"
            )
        }
    }

    private fun canonicalBookOpenDispatch(intent: ReaderUiIntent): CanonicalRuntimeDispatch? = when (intent) {
        is ReaderUiIntent.EnterReaderFromCover -> CanonicalRuntimeDispatch(
            event = "book.open",
            payload = mapOf(
                "sourceId" to JsonPrimitive(intent.sourceId),
                "bookId" to JsonPrimitive(intent.bookUrl),
                "sourceKind" to JsonPrimitive(canonicalBookOpenSourceKind(intent.sourceId, intent.bookUrl))
            ),
            correlationId = intent.requestId
        )
        is ReaderUiIntent.EnterReaderFromAction -> CanonicalRuntimeDispatch(
            event = "book.open",
            payload = mapOf(
                "sourceId" to JsonPrimitive(intent.sourceId),
                "bookId" to JsonPrimitive(intent.bookUrl),
                "sourceKind" to JsonPrimitive(canonicalBookOpenSourceKind(intent.sourceId, intent.bookUrl))
            ),
            correlationId = intent.requestId
        )
        else -> null
    }

    /** No legacy ReaderUiIntent currently contains a complete typed W1 transaction. */
    private fun canonicalImportDispatch(@Suppress("UNUSED_PARAMETER") intent: ReaderUiIntent): CanonicalRuntimeDispatch? = null

    private fun legacyUntypedImportEvent(intent: ReaderUiIntent): String? = when (intent) {
        is ReaderUiIntent.ParseSourceImport -> "import.start"
        is ReaderUiIntent.ConfirmSourceImport -> "import.apply"
        ReaderUiIntent.DismissSourceImportResult -> "import.cancel"
        else -> null
    }

    private fun recordUntypedImportBridgeFailure(
        intent: ReaderUiIntent,
        mode: ReaderUiRuntimeDispatchMode
    ) {
        val event = requireNotNull(legacyUntypedImportEvent(intent))
        recordRuntimeError(
            CanonicalRuntimeDispatch(event = event, correlationId = intent.requestId),
            mode,
            ReaderUIRuntimeException(
                "IMPORT_TYPED_DATA_UNAVAILABLE",
                when (event) {
                    "import.start" -> "legacy ParseSourceImport lacks canonical kind + input"
                    "import.apply" -> "legacy ConfirmSourceImport lacks transactionId + parsed Core result"
                    else -> "legacy DismissSourceImportResult lacks the persisted rollbackToken"
                }
            )
        )
    }

    private fun legacyUntypedSourceSwitchEvent(intent: ReaderUiIntent): String? = when (intent) {
        is ReaderUiIntent.SourceSwitchConfirm -> "source.switch.confirm"
        ReaderUiIntent.SourceSwitchCancel -> "source.switch.rollback"
        else -> null
    }

    private fun recordUntypedSourceSwitchBridgeFailure(
        intent: ReaderUiIntent,
        mode: ReaderUiRuntimeDispatchMode
    ) {
        val event = requireNotNull(legacyUntypedSourceSwitchEvent(intent))
        recordRuntimeError(
            CanonicalRuntimeDispatch(event = event, correlationId = intent.requestId),
            mode,
            ReaderUIRuntimeException(
                "SOURCE_SWITCH_TYPED_DATA_UNAVAILABLE",
                if (event == "source.switch.confirm") {
                    "legacy SourceSwitchConfirm lacks from/target/newToc/chapter/timestamp DTOs"
                } else {
                    "legacy SourceSwitchCancel lacks the persisted rollbackToken"
                }
            )
        )
    }

    private fun legacyUntypedReplaceRuleEvent(intent: ReaderUiIntent): String? = when (intent) {
        is ReaderUiIntent.ReplaceRuleAdd -> "reader.replace.create"
        is ReaderUiIntent.ReplaceRuleUpdate,
        is ReaderUiIntent.ReplaceRuleDelete,
        is ReaderUiIntent.ReplaceRuleToggle -> "reader.replace.apply"
        is ReaderUiIntent.ReplaceRulesLoaded -> "reader.replace.validate"
        else -> null
    }

    private fun recordUntypedReplaceRuleBridgeFailure(
        intent: ReaderUiIntent,
        mode: ReaderUiRuntimeDispatchMode
    ) {
        val event = requireNotNull(legacyUntypedReplaceRuleEvent(intent))
        recordRuntimeError(
            CanonicalRuntimeDispatch(event = event, correlationId = intent.requestId),
            mode,
            ReaderUIRuntimeException(
                "REPLACE_RULE_TYPED_DATA_UNAVAILABLE",
                when (event) {
                    "reader.replace.create" -> "legacy ReplaceRuleAdd lacks the canonical typed rule params"
                    "reader.replace.apply" -> "legacy replace mutation lacks text/book/origin/target apply context"
                    else -> "legacy ReplaceRulesLoaded lacks pattern/isRegex/scope validation input"
                }
            )
        )
    }

    private fun canonicalSourceSwitchDispatch(
        intent: ReaderUiIntent,
        before: ReaderUiState
    ): CanonicalRuntimeDispatch? = when (intent) {
        is ReaderUiIntent.SourceSwitchOpen -> CanonicalRuntimeDispatch(
            event = "source.switch.open",
            correlationId = intent.requestId
        )
        ReaderUiIntent.SourceSwitchClose -> CanonicalRuntimeDispatch(
            event = "source.switch.cancel",
            correlationId = intent.requestId
        )
        is ReaderUiIntent.SourceSwitchConfirm -> CanonicalRuntimeDispatch(
            event = "source.switch.confirm",
            payload = mapOf("sourceId" to JsonPrimitive(intent.sourceId)),
            correlationId = intent.requestId
        )
        ReaderUiIntent.SourceSwitchCancel -> CanonicalRuntimeDispatch(
            event = "source.switch.rollback",
            correlationId = intent.requestId
        )
        else -> null
    }

    private fun canonicalReplaceRuleDispatch(intent: ReaderUiIntent): CanonicalRuntimeDispatch? = when (intent) {
        is ReaderUiIntent.ReplaceRuleAdd -> CanonicalRuntimeDispatch(
            event = "reader.replace.create",
            payload = mapOf(
                "name" to JsonPrimitive(intent.name),
                "pattern" to JsonPrimitive(intent.pattern),
                "replacement" to JsonPrimitive(intent.replacement),
                "scope" to JsonPrimitive(intent.scope)
            ),
            correlationId = intent.requestId
        )
        is ReaderUiIntent.ReplaceRuleUpdate -> CanonicalRuntimeDispatch(
            event = "reader.replace.apply",
            payload = buildMap {
                put("ruleId", JsonPrimitive(intent.id))
                intent.name?.let { put("name", JsonPrimitive(it)) }
                intent.pattern?.let { put("pattern", JsonPrimitive(it)) }
                intent.replacement?.let { put("replacement", JsonPrimitive(it)) }
                intent.scope?.let { put("scope", JsonPrimitive(it)) }
            },
            correlationId = intent.requestId
        )
        is ReaderUiIntent.ReplaceRuleDelete -> CanonicalRuntimeDispatch(
            event = "reader.replace.apply",
            payload = mapOf("ruleId" to JsonPrimitive(intent.id)),
            correlationId = intent.requestId
        )
        is ReaderUiIntent.ReplaceRuleToggle -> CanonicalRuntimeDispatch(
            event = "reader.replace.apply",
            payload = mapOf("ruleId" to JsonPrimitive(intent.id)),
            correlationId = intent.requestId
        )
        is ReaderUiIntent.ReplaceRulesLoaded -> CanonicalRuntimeDispatch(
            event = "reader.replace.validate",
            correlationId = intent.requestId
        )
        else -> null
    }

    /**
     * Projects a source switch overlay/route transition to native production
     * state. Mirrors the directory-pair projector: the runtime semantic change
     * is authoritative, and the native route/overlay is narrowed to match.
     */
    private fun projectSourceSwitchTransition(
        canonical: CanonicalRuntimeDispatch,
        transition: ReaderUITransition,
        productionBefore: ReaderUiState
    ): ReaderUiState = when (canonical.event) {
        "source.switch.open" -> {
            // Mirrors ReaderUiReducer.SourceSwitchOpen: push SourceSwitchFlow
            // route and enter Loading. The fallback context is constructed
            // from the intent fields when no readerContext exists.
            val route = ReaderRoute.SourceSwitchFlow(context = productionBefore.readerContext)
            if (
                productionBefore.currentRoute is ReaderRoute.SourceSwitchFlow &&
                productionBefore.sourceSwitch == SourceSwitchState.Loading
            ) {
                productionBefore
            } else {
                productionBefore.copy(
                    backStack = productionBefore.backStack + route,
                    currentRoute = route,
                    sourceSwitch = SourceSwitchState.Loading,
                    motionInterrupt = null
                )
            }
        }
        "source.switch.cancel" -> {
            val newBackStack = productionBefore.backStack.dropLast(1)
            val newRoute = newBackStack.lastOrNull() ?: ReaderRoute.TabShell(productionBefore.activeTab)
            productionBefore.copy(
                backStack = newBackStack,
                currentRoute = newRoute,
                sourceSwitch = SourceSwitchState.Idle,
                motionInterrupt = null
            )
        }
        "reader.sourceSwitch.open" -> {
            if (transition.state.overlay != SOURCE_SWITCH_OVERLAY) productionBefore
            else productionBefore.copy(overlayState = OverlayState.None)
        }
        "reader.sourceSwitch.close" -> {
            productionBefore.copy(overlayState = OverlayState.None)
        }
        else -> productionBefore
    }

    @Synchronized
    override fun acceptBookOpenResult(
        coreType: String,
        correlationId: String,
        chapterCount: Int?,
        error: String?
    ): ReaderBookOpenRuntimeAdvance {
        val transition = runtime.acceptBookOpenResult(coreType, correlationId, chapterCount, error)
        observationSnapshot = ReaderUiRuntimeShadowObservation(
            event = "book.open",
            correlationId = correlationId,
            runtimeState = transition.state,
            runtimeEffects = transition.effects,
            mismatches = emptyList(),
            mode = ReaderUiRuntimeDispatchMode.PILOT
        )
        return ReaderBookOpenRuntimeAdvance(transition.accepted, transition.effects)
    }

    @Synchronized
    override fun provideBookOpenLayout(
        correlationId: String,
        layout: io.reader.ui.runtime.ReaderUIBookOpenLayout
    ): ReaderBookOpenRuntimeAdvance {
        val transition = runtime.provideBookOpenLayout(correlationId, layout)
        observationSnapshot = ReaderUiRuntimeShadowObservation(
            event = "book.open",
            correlationId = correlationId,
            runtimeState = transition.state,
            runtimeEffects = transition.effects,
            mismatches = emptyList(),
            mode = ReaderUiRuntimeDispatchMode.PILOT
        )
        return ReaderBookOpenRuntimeAdvance(transition.accepted, transition.effects)
    }

    @Synchronized
    override fun cancelBookOpen(correlationId: String): Boolean {
        val transition = runtime.cancelBookOpen(correlationId)
        if (transition.accepted) {
            observationSnapshot = ReaderUiRuntimeShadowObservation(
                event = "book.open",
                correlationId = correlationId,
                runtimeState = transition.state,
                runtimeEffects = emptyList(),
                mismatches = emptyList(),
                mode = ReaderUiRuntimeDispatchMode.PILOT
            )
        }
        return transition.accepted
    }

    @Synchronized
    override fun providePageLayout(
        correlationId: String,
        layout: io.reader.ui.runtime.ReaderUIPageLayout
    ): ReaderPlaybackRuntimeAdvance = recordPlaybackAdvance(
        event = "reader.page.layout",
        correlationId = correlationId,
        transition = runtime.providePageLayout(correlationId, layout)
    )

    @Synchronized
    override fun acceptPageLocationResult(
        correlationId: String,
        canonicalLocation: String?,
        pageIndex: Int?,
        error: String?
    ): ReaderPlaybackRuntimeAdvance = recordPlaybackAdvance(
        event = "reader.page.location",
        correlationId = correlationId,
        transition = runtime.acceptPageLocationResult(
            correlationId,
            canonicalLocation,
            pageIndex,
            error
        )
    )

    @Synchronized
    override fun acceptPageProgressResult(
        correlationId: String,
        stored: Boolean?,
        error: String?
    ): ReaderPlaybackRuntimeAdvance = recordPlaybackAdvance(
        event = "reader.page.progress",
        correlationId = correlationId,
        transition = runtime.acceptPageProgressResult(
            correlationId = correlationId,
            stored = stored,
            error = error
        )
    )

    @Synchronized
    override fun acceptTTSCoreResult(
        coreType: String,
        correlationId: String,
        error: String?
    ): ReaderPlaybackRuntimeAdvance = recordPlaybackAdvance(
        event = coreType,
        correlationId = correlationId,
        transition = runtime.acceptTTSCoreResult(coreType, correlationId, error)
    )

    @Synchronized
    override fun acceptTTSSystemStart(
        correlationId: String,
        error: String?
    ): ReaderPlaybackRuntimeAdvance = recordPlaybackAdvance(
        event = "tts.system.start",
        correlationId = correlationId,
        transition = runtime.acceptTTSSystemStart(correlationId, error)
    )

    @Synchronized
    override fun acceptAutoPageTimerFired(
        correlationId: String,
        generation: Int
    ): ReaderPlaybackRuntimeAdvance = recordPlaybackAdvance(
        event = "timer.foreground.fired",
        correlationId = correlationId,
        transition = runtime.acceptAutoPageTimerFired(correlationId, generation)
    )

    @Synchronized
    override fun stopTTS(correlationId: String?): ReaderPlaybackRuntimeAdvance = recordPlaybackAdvance(
        event = "reader.tts.stop",
        correlationId = correlationId,
        transition = runtime.stopTTS(correlationId)
    )

    @Synchronized
    override fun stopAutoPage(correlationId: String?): ReaderPlaybackRuntimeAdvance = recordPlaybackAdvance(
        event = "reader.autoPage.stop",
        correlationId = correlationId,
        transition = runtime.stopAutoPage(correlationId)
    )

    @Synchronized
    override fun suspendAutoPageForBackground(
        correlationId: String?
    ): ReaderPlaybackRuntimeAdvance = recordPlaybackAdvance(
        event = "reader.autoPage.background",
        correlationId = correlationId,
        transition = runtime.suspendAutoPageForBackground(correlationId)
    )

    @Synchronized
    override fun cancelPageStep(correlationId: String): ReaderPlaybackRuntimeAdvance = recordPlaybackAdvance(
        event = "reader.page.cancel",
        correlationId = correlationId,
        transition = runtime.cancelPageStep(correlationId)
    )

    @Synchronized
    override fun acceptImportResult(
        coreType: String,
        correlationId: String,
        error: String?
    ): ReaderImportRuntimeAdvance {
        if (activeImportCorrelationId != correlationId) {
            return ReaderImportRuntimeAdvance(accepted = false)
        }
        activeImportCorrelationId = null
        observationSnapshot = ReaderUiRuntimeShadowObservation(
            event = "import",
            correlationId = correlationId,
            runtimeState = runtime.state,
            runtimeEffects = emptyList(),
            mismatches = emptyList(),
            mode = ReaderUiRuntimeDispatchMode.PILOT
        )
        return ReaderImportRuntimeAdvance(accepted = true)
    }

    @Synchronized
    override fun cancelImport(correlationId: String): Boolean {
        if (activeImportCorrelationId != correlationId) return false
        activeImportCorrelationId = null
        return true
    }

    @Synchronized
    override fun acceptSourceSwitchResult(
        coreType: String,
        correlationId: String,
        result: ReaderUIJSONResult,
        error: String?
    ): ReaderSourceSwitchRuntimeAdvance {
        if (activeSourceSwitchCorrelationId != correlationId) {
            return ReaderSourceSwitchRuntimeAdvance(accepted = false)
        }
        val event = requireNotNull(activeSourceSwitchEvent)
        if (error == null) validateReaderUITypedResult(event, coreType, result)
        activeSourceSwitchCorrelationId = null
        activeSourceSwitchEvent = null
        observationSnapshot = ReaderUiRuntimeShadowObservation(
            event = event,
            correlationId = correlationId,
            runtimeState = runtime.state,
            runtimeEffects = emptyList(),
            mismatches = emptyList(),
            mode = ReaderUiRuntimeDispatchMode.PILOT
        )
        return ReaderSourceSwitchRuntimeAdvance(accepted = true)
    }

    @Synchronized
    override fun cancelSourceSwitch(correlationId: String): Boolean {
        if (activeSourceSwitchCorrelationId != correlationId) return false
        activeSourceSwitchCorrelationId = null
        activeSourceSwitchEvent = null
        return true
    }

    @Synchronized
    override fun acceptReplaceRuleResult(
        coreType: String,
        correlationId: String,
        result: ReaderUIJSONResult,
        error: String?
    ): ReaderReplaceRuleRuntimeAdvance {
        if (activeReplaceRuleCorrelationId != correlationId) {
            return ReaderReplaceRuleRuntimeAdvance(accepted = false)
        }
        val event = requireNotNull(activeReplaceRuleEvent)
        if (error == null) validateReaderUITypedResult(event, coreType, result)
        activeReplaceRuleCorrelationId = null
        activeReplaceRuleEvent = null
        observationSnapshot = ReaderUiRuntimeShadowObservation(
            event = event,
            correlationId = correlationId,
            runtimeState = runtime.state,
            runtimeEffects = emptyList(),
            mismatches = emptyList(),
            mode = ReaderUiRuntimeDispatchMode.PILOT
        )
        return ReaderReplaceRuleRuntimeAdvance(accepted = true)
    }

    @Synchronized
    override fun cancelReplaceRule(correlationId: String): Boolean {
        if (activeReplaceRuleCorrelationId != correlationId) return false
        activeReplaceRuleCorrelationId = null
        activeReplaceRuleEvent = null
        return true
    }

    @Synchronized
    override fun acceptRssResult(
        coreType: String,
        correlationId: String,
        result: ReaderUIJSONResult,
        error: String?
    ): ReaderRssRuntimeAdvance {
        if (activeRssCorrelationId != correlationId) {
            return ReaderRssRuntimeAdvance(accepted = false)
        }
        val event = requireNotNull(activeRssEvent)
        if (error == null) validateReaderUITypedResult(event, coreType, result)
        activeRssCorrelationId = null
        activeRssEvent = null
        observationSnapshot = ReaderUiRuntimeShadowObservation(
            event = event,
            correlationId = correlationId,
            runtimeState = runtime.state,
            runtimeEffects = emptyList(),
            mismatches = emptyList(),
            mode = ReaderUiRuntimeDispatchMode.PILOT
        )
        return ReaderRssRuntimeAdvance(accepted = true)
    }

    @Synchronized
    override fun cancelRss(correlationId: String): Boolean {
        if (activeRssCorrelationId != correlationId) return false
        activeRssCorrelationId = null
        activeRssEvent = null
        return true
    }

    @Synchronized
    override fun acceptSyncResult(
        coreType: String,
        correlationId: String,
        result: ReaderUIJSONResult,
        error: String?
    ): ReaderSyncRuntimeAdvance {
        if (activeSyncCorrelationId != correlationId) {
            return ReaderSyncRuntimeAdvance(accepted = false)
        }
        val event = requireNotNull(activeSyncEvent)
        if (coreType !in activeSyncPendingCoreTypes) {
            return ReaderSyncRuntimeAdvance(accepted = false)
        }
        if (error == null) validateReaderUITypedResult(event, coreType, result)
        if (error != null) {
            activeSyncPendingCoreTypes.clear()
        } else {
            activeSyncPendingCoreTypes.remove(coreType)
        }
        val completed = activeSyncPendingCoreTypes.isEmpty()
        if (completed) {
            activeSyncCorrelationId = null
            activeSyncEvent = null
        }
        observationSnapshot = ReaderUiRuntimeShadowObservation(
            event = event,
            correlationId = correlationId,
            runtimeState = runtime.state,
            runtimeEffects = emptyList(),
            mismatches = emptyList(),
            mode = ReaderUiRuntimeDispatchMode.PILOT
        )
        return ReaderSyncRuntimeAdvance(accepted = true, completed = completed)
    }

    @Synchronized
    override fun cancelSync(correlationId: String): Boolean {
        if (activeSyncCorrelationId != correlationId) return false
        activeSyncCorrelationId = null
        activeSyncEvent = null
        activeSyncPendingCoreTypes.clear()
        return true
    }

    private fun recordPlaybackAdvance(
        event: String,
        correlationId: String?,
        transition: ReaderUIPlaybackTransition
    ): ReaderPlaybackRuntimeAdvance {
        if (transition.accepted) {
            observationSnapshot = ReaderUiRuntimeShadowObservation(
                event = event,
                correlationId = correlationId,
                runtimeState = transition.state,
                runtimeEffects = transition.effects,
                mismatches = emptyList(),
                cancelledCorrelationIds = transition.cancelledCorrelationIds,
                mode = ReaderUiRuntimeDispatchMode.PILOT
            )
        }
        return ReaderPlaybackRuntimeAdvance(
            accepted = transition.accepted,
            state = transition.state,
            effects = transition.effects,
            cancelledCorrelationIds = transition.cancelledCorrelationIds
        )
    }

    private fun projectDirectoryTransition(
        canonical: CanonicalRuntimeDispatch,
        transition: ReaderUITransition,
        productionBefore: ReaderUiState
    ): ReaderUiState {
        val projection = requireNotNull(canonical.directoryProjection) {
            "Directory Pilot event is missing a native projection"
        }
        return when (projection) {
            is DirectoryNativeProjection.OpenRoute -> {
                if (transition.state.overlay != DIRECTORY_OVERLAY) {
                    throw ReaderUIRuntimeException(
                        "PILOT_STATE_BOUNDARY",
                        "${canonical.event} did not establish directory overlay"
                    )
                }
                val routeAlreadyPresented =
                    productionBefore.currentRoute.routeId == projection.route.routeId &&
                        productionBefore.backStack.lastOrNull()?.routeId == projection.route.routeId
                if (routeAlreadyPresented) productionBefore else productionBefore.copy(
                    backStack = productionBefore.backStack + projection.route,
                    currentRoute = projection.route,
                    motionInterrupt = null
                )
            }
            is DirectoryNativeProjection.OpenSheet -> {
                if (transition.state.overlay != DIRECTORY_OVERLAY) {
                    throw ReaderUIRuntimeException(
                        "PILOT_STATE_BOUNDARY",
                        "${canonical.event} did not establish directory overlay"
                    )
                }
                val projected = OverlayState.Sheet(projection.content)
                if (productionBefore.overlayState == projected) productionBefore else {
                    productionBefore.copy(overlayState = projected)
                }
            }
            DirectoryNativeProjection.CloseRoute -> {
                if (!transition.clearedDirectoryOverlay()) return productionBefore
                val newBackStack = productionBefore.backStack.dropLast(1)
                val newRoute = newBackStack.lastOrNull()
                    ?: ReaderRoute.TabShell(productionBefore.activeTab)
                val readerFullyPopped = newBackStack.none { it is ReaderRoute.ImmersiveReading }
                productionBefore.copy(
                    backStack = newBackStack,
                    currentRoute = newRoute,
                    readerContext = if (readerFullyPopped) null else productionBefore.readerContext,
                    activeSession = if (readerFullyPopped) null else productionBefore.activeSession,
                    motionInterrupt = null
                )
            }
            DirectoryNativeProjection.CloseSheet -> {
                if (!transition.clearedDirectoryOverlay()) return productionBefore
                if (productionBefore.hasDirectorySheet()) {
                    productionBefore.copy(overlayState = OverlayState.None)
                } else {
                    productionBefore
                }
            }
        }
    }

    private fun ReaderUITransition.clearedDirectoryOverlay(): Boolean =
        previous.overlay == DIRECTORY_OVERLAY && state.overlay != DIRECTORY_OVERLAY

    /** Compare only stable projections with a real native equivalent. */
    private fun compareStableProjection(
        canonical: CanonicalRuntimeDispatch,
        transition: ReaderUITransition,
        productionBefore: ReaderUiState,
        productionAfter: ReaderUiState
    ): List<String> = buildList {
        when (canonical.event) {
            "book.open" -> {
                if (transition.state.routeId != productionAfter.currentRoute.routeId) {
                    add(
                        "route runtime=${transition.state.routeId} " +
                            "native=${productionAfter.currentRoute.routeId}"
                    )
                }
                val nativeLoading = productionAfter.asyncResult.state == AsyncResultStateValue.PENDING
                if (transition.state.loading != nativeLoading) {
                    add("loading runtime=${transition.state.loading} native=$nativeLoading")
                }
                if (transition.effects.any { it.correlationId != canonical.correlationId }) {
                    add("runtime effect correlationId drift")
                }
            }
            "reader.directory.open" -> {
                if (transition.state.overlay != DIRECTORY_OVERLAY) {
                    add("runtime directory overlay was not opened")
                }
                if (!productionAfter.hasDirectorySurface()) {
                    add("native directory surface was not opened")
                }
            }
            "reader.directory.close" -> {
                if (transition.clearedDirectoryOverlay()) {
                    if (productionAfter.hasDirectorySurface()) {
                        add("native directory surface remained open")
                    }
                } else if (productionAfter != productionBefore) {
                    add("native directory projection changed after conditional close no-op")
                }
            }
            "reader.tts.start" -> {
                if (transition.state.activeSession != "tts") {
                    add("runtime activeSession=${transition.state.activeSession}, expected tts")
                }
                if (productionAfter.activeSession?.type != SessionType.TTS) {
                    add("native activeSession=${productionAfter.activeSession?.type}, expected TTS")
                }
            }
            "reader.autoPage.start" -> {
                if (transition.state.activeSession != "auto-page") {
                    add("runtime activeSession=${transition.state.activeSession}, expected auto-page")
                }
                if (productionAfter.activeSession?.type != SessionType.AUTO_PAGE) {
                    add("native activeSession=${productionAfter.activeSession?.type}, expected AUTO_PAGE")
                }
            }
        }

        val nativeHostEffects = productionAfter.pendingHostRequests
            .drop(productionBefore.pendingHostRequests.size)
            .map { it.capability }
        val runtimeHostEffects = transition.effects
            .filter { it.kind == ReaderUIEffectKind.HOST }
            .map { it.type }
        if (runtimeHostEffects != nativeHostEffects) {
            add("host effects runtime=$runtimeHostEffects native=$nativeHostEffects")
        }
    }

    private fun ReaderUiState.hasDirectorySheet(): Boolean =
        ((overlayState as? OverlayState.Sheet)?.content as? SheetContent.ReaderSetting)
            ?.module == DIRECTORY_OVERLAY

    private fun ReaderUiState.hasDirectoryRoute(): Boolean =
        currentRoute.isDirectoryRoute() || backStack.any { it.isDirectoryRoute() }

    private fun ReaderUiState.hasDirectorySurface(): Boolean =
        hasDirectorySheet() || currentRoute.isDirectoryRoute()

    private fun ReaderRoute.isDirectoryRoute(): Boolean =
        this is ReaderRoute.ReaderControl && id in DIRECTORY_ROUTE_IDS

    private companion object {
        const val DIRECTORY_OVERLAY = "directory"
        const val SOURCE_SWITCH_OVERLAY = "source-switch"
        val DIRECTORY_ROUTE_IDS = setOf(
            RouteIds.READER_TOC_BOOKMARKS,
            RouteIds.READER_FULL_DIRECTORY
        )
    }
}
