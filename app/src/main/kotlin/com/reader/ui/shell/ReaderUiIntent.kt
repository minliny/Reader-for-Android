package com.reader.ui.shell

import com.reader.ui.shell.RouteIds.IMMERSIVE_READING

/**
 * Intents dispatched into [ReaderUiReducer]. Each carries a [requestId] so the reducer can
 * implement "latest intent wins" and the async-result guard
 * (`motion.async.resultGuard`: requestId / from / to / context).
 */
sealed class ReaderUiIntent {
    abstract val requestId: String

    /**
     * `app.tab.switch` — main tab switch. NOT a route push: only [ReaderUiState.activeTab]
     * changes and the back stack of pushed routes is cleared (interrupt rule).
     */
    data class SelectTab(
        val tab: MainTab,
        override val requestId: String = generateRequestId()
    ) : ReaderUiIntent()

    /** `reader.entry.coverToImmersive` — enter immersive reading from a bookshelf cover. */
    data class EnterReaderFromCover(
        val sourceId: String,
        val bookUrl: String,
        val bookName: String,
        override val requestId: String = generateRequestId()
    ) : ReaderUiIntent()

    /** `reader.entry.actionToImmersive` — enter from continue-reading / chapter row / search result. */
    data class EnterReaderFromAction(
        val sourceId: String,
        val bookUrl: String,
        val bookName: String,
        override val requestId: String = generateRequestId()
    ) : ReaderUiIntent()

    /** `app.route.push` — push a non-tab route onto the back stack. */
    data class PushRoute(
        val route: ReaderRoute,
        override val requestId: String = generateRequestId()
    ) : ReaderUiIntent()

    /** `app.route.pop` — system back / explicit back. Pops the top non-tab route. */
    object PopRoute : ReaderUiIntent() {
        override val requestId: String = generateRequestId()
    }

    /**
     * Testable reduced-motion switch (MOTION_EFFECTS.md §8). Production also seeds this from
     * [com.reader.ui.motion.ReducedMotionResolver] at startup; this intent lets unit tests
     * and a debug toggle flip it without changing system settings.
     */
    data class SetReducedMotion(
        val enabled: Boolean,
        override val requestId: String = generateRequestId()
    ) : ReaderUiIntent()
}

private val requestCounter = java.util.concurrent.atomic.AtomicLong(0)

/** Monotonic request id generator — unique per process, deterministic enough for tests. */
fun generateRequestId(): String =
    "req-" + requestCounter.incrementAndGet()
