package com.reader.ui.shell

/**
 * Pure reducer for [ReaderUiState]. No Android / Compose dependencies — fully unit-testable.
 *
 * Contract rules implemented (from `MOTION_CONTRACT.md`, `MOTION_EFFECTS.md`,
 * `FRONTEND_DEVELOPMENT_SLICE_MATRIX.md` Slice 1/2, `MOTION_PLATFORM_MAPPING.md`):
 *
 * - Tab switch is NOT a route push; back stack of pushed routes is cleared (interrupt).
 * - Reader entry final state is `immersive-reading`, never the control layer.
 * - Back from reader returns to the source tab.
 * - Continuous multi-cover taps keep only the last target (latest intent wins).
 * - Reduced motion is a first-class state field, not a silent render flag.
 * - Every final state uniquely explains route / back stack / ReaderContext / session /
 *   overlay / interrupt.
 */
object ReaderUiReducer {

    fun reduce(state: ReaderUiState, intent: ReaderUiIntent): ReaderUiState = when (intent) {
        is ReaderUiIntent.SelectTab -> selectTab(state, intent)

        is ReaderUiIntent.EnterReaderFromCover ->
            enterReader(
                state,
                entry = ReaderEntry.COVER_TO_IMMERSIVE,
                sourceId = intent.sourceId,
                bookUrl = intent.bookUrl,
                bookName = intent.bookName,
                requestId = intent.requestId
            )

        is ReaderUiIntent.EnterReaderFromAction ->
            enterReader(
                state,
                entry = ReaderEntry.ACTION_TO_IMMERSIVE,
                sourceId = intent.sourceId,
                bookUrl = intent.bookUrl,
                bookName = intent.bookName,
                requestId = intent.requestId
            )

        is ReaderUiIntent.PushRoute -> pushRoute(state, intent)

        ReaderUiIntent.PopRoute -> popRoute(state)

        is ReaderUiIntent.SetReducedMotion -> state.copy(reducedMotion = intent.enabled)
    }

    /**
     * `app.tab.switch`. Clears any pushed routes (Search / ImportSource / reader) — tab
     * switch is an interrupt (motion.interrupt.cancel) and must not leave dangling overlays
     * or a stale back stack.
     */
    private fun selectTab(state: ReaderUiState, intent: ReaderUiIntent.SelectTab): ReaderUiState {
        if (intent.tab == state.activeTab && state.backStack.isEmpty()) {
            // Re-selecting the current tab: only pressed feedback is allowed (MOTION_EFFECTS §4).
            return state
        }
        val cancelled = state.backStack.isNotEmpty()
        return state.copy(
            activeTab = intent.tab,
            currentRoute = ReaderRoute.TabShell(intent.tab),
            backStack = emptyList(),
            readerContext = null,
            activeSession = null,
            overlayState = OverlayState.None,
            motionInterrupt = if (cancelled) {
                MotionInterrupt(
                    requestId = intent.requestId,
                    from = state.currentRoute.routeId,
                    to = ReaderRoute.TabShell(intent.tab).routeId,
                    kind = InterruptKind.CANCEL
                )
            } else null
        )
    }

    /**
     * Reader entry (`reader.entry.coverToImmersive` / `reader.entry.actionToImmersive`).
     *
     * Final state: route = `immersive-reading`, ReaderContext set, control layer NOT shown.
     * Latest intent wins: if a previous in-flight `immersive-reading` is on top of the
     * stack (continuous multi-cover tap), it is replaced (motion.interrupt.cancel) rather
     * than stacked — so "连续点击只保留最后目标".
     */
    private fun enterReader(
        state: ReaderUiState,
        entry: ReaderEntry,
        sourceId: String,
        bookUrl: String,
        bookName: String,
        requestId: String
    ): ReaderUiState {
        val ctx = ReaderContext(
            sourceId = sourceId,
            bookUrl = bookUrl,
            bookName = bookName,
            entry = entry,
            entryRequestId = requestId
        )
        val newRoute = ReaderRoute.ImmersiveReading(ctx)
        val topIsInFlightReader = state.backStack.lastOrNull() is ReaderRoute.ImmersiveReading
        val newBackStack: List<ReaderRoute> = if (topIsInFlightReader) {
            // Replace the in-flight entry: latest intent wins, old one cancelled.
            state.backStack.dropLast(1) + newRoute
        } else {
            state.backStack + newRoute
        }
        return state.copy(
            backStack = newBackStack,
            currentRoute = newRoute,
            readerContext = ctx,
            // Entry starts in immersive reading — never auto-open the control layer.
            activeSession = null,
            overlayState = OverlayState.None,
            motionInterrupt = if (topIsInFlightReader) {
                MotionInterrupt(
                    requestId = requestId,
                    from = RouteIds.IMMERSIVE_READING,
                    to = RouteIds.IMMERSIVE_READING,
                    kind = InterruptKind.CANCEL
                )
            } else null
        )
    }

    /** `app.route.push` for non-tab routes (Search / ImportSource). */
    private fun pushRoute(state: ReaderUiState, intent: ReaderUiIntent.PushRoute): ReaderUiState {
        val route = intent.route
        // TabShell is not pushable — guard against misuse.
        require(route !is ReaderRoute.TabShell) { "TabShell is not pushable; use SelectTab." }
        return state.copy(
            backStack = state.backStack + route,
            currentRoute = route,
            motionInterrupt = null
        )
    }

    /**
     * `app.route.pop`. Pops the top non-tab route and returns to the source (the active tab
     * or the previous pushed route). ReaderContext is cleared when the reader is fully
     * popped (you've left the reader).
     */
    private fun popRoute(state: ReaderUiState): ReaderUiState {
        if (state.backStack.isEmpty()) return state
        val newBackStack = state.backStack.dropLast(1)
        val newRoute = newBackStack.lastOrNull() ?: ReaderRoute.TabShell(state.activeTab)
        val readerFullyPopped = newBackStack.none { it is ReaderRoute.ImmersiveReading }
        return state.copy(
            backStack = newBackStack,
            currentRoute = newRoute,
            readerContext = if (readerFullyPopped) null else state.readerContext,
            activeSession = if (readerFullyPopped) null else state.activeSession,
            overlayState = OverlayState.None,
            motionInterrupt = null
        )
    }
}
