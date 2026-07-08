package com.reader.ui.shell

import androidx.lifecycle.ViewModel
import com.reader.ui.motion.MotionController
import com.reader.ui.motion.MotionIdConstants
import com.reader.ui.motion.ReducedMotionResolver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

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
 */
class AppShellViewModel(
    reducedMotionResolver: ReducedMotionResolver? = null
) : ViewModel() {

    private val _state = MutableStateFlow(
        ReaderUiState(reducedMotion = reducedMotionResolver?.isReducedMotion() ?: false)
    )
    val state: StateFlow<ReaderUiState> = _state.asStateFlow()

    init {
        // P6: attach the motion runtime so dispatch() can fire motion transactions.
        MotionController.setReducedMotionResolver(reducedMotionResolver)
        MotionController.attachToViewModel(this)
    }

    fun dispatch(intent: ReaderUiIntent) {
        // P6: map intent → Motion ID and start a motion transaction (runtime side-effect).
        // The reducer remains pure; this is the consume layer that MOTION_EFFECTS.md §6 requires.
        motionTransactionFor(intent)?.let { (motionId, from, to, durationMs) ->
            MotionController.start(
                motionId = motionId,
                from = from,
                to = to,
                durationMs = durationMs,
                reducedMotion = _state.value.reducedMotion
            )
        }
        _state.update { ReaderUiReducer.reduce(it, intent) }
    }

    /**
     * Maps a [ReaderUiIntent] to its Motion ID + from/to route ids + default duration.
     * Returns null for intents that don't have a motion contract (e.g. state field updates
     * that don't trigger a transition). Sourced from `MotionController.contractFor` /
     * `MotionIdConstants` — no fabricated Motion IDs.
     */
    private fun motionTransactionFor(
        intent: ReaderUiIntent
    ): Tuple4<String, String, String, Long>? {
        val current = _state.value
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
            is ReaderUiIntent.PushRoute -> Tuple4(
                MotionIdConstants.APP_ROUTE_PUSH_FORWARD,
                from,
                intent.route.routeId,
                MotionController.contractFor(MotionIdConstants.APP_ROUTE_PUSH_FORWARD)?.defaultDurationMs
                    ?: 160L
            )
            ReaderUiIntent.PopRoute -> Tuple4(
                MotionIdConstants.APP_ROUTE_POP_BACKWARD,
                from,
                current.backStack.dropLast(1).lastOrNull()?.routeId
                    ?: current.activeTab.routeId,
                MotionController.contractFor(MotionIdConstants.APP_ROUTE_POP_BACKWARD)?.defaultDurationMs
                    ?: 160L
            )
            is ReaderUiIntent.ReplaceRoute -> Tuple4(
                MotionIdConstants.APP_ROUTE_REPLACE,
                from,
                intent.route.routeId,
                MotionController.contractFor(MotionIdConstants.APP_ROUTE_REPLACE)?.defaultDurationMs
                    ?: 160L
            )
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
            // Intents with no motion contract — state field updates only.
            else -> null
        }
    }

    private data class Tuple4<A, B, C, D>(
        val a: A, val b: B, val c: C, val d: D
    )
}
