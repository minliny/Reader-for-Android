package com.reader.ui.motion

import io.reader.ui.contract.MotionContainerRole
import io.reader.ui.contract.MotionId
import io.reader.ui.contract.MotionOperation
import io.reader.ui.contract.MotionPolicy
import io.reader.ui.contract.MotionPolicyMatch
import io.reader.ui.contract.MotionPolicyRegistry
import io.reader.ui.contract.MotionRequest
import io.reader.ui.contract.RouteShell

/**
 * Android-friendly wrapper over the contract's [MotionPolicyRegistry].
 *
 * The contract already provides [MotionPolicyMatch.matches] and [MotionPolicyMatch.specificity];
 * this adapter adds a resolve() entry point that picks the highest-priority + highest-specificity
 * policy for a given request, plus convenience overloads for the common route-transition and
 * gesture scenarios.
 *
 * Contract source: generated/kotlin/MotionPolicy.kt. Version 3.0 intentionally has no
 * catch-all fallback; unmatched requests return null.
 * Frontend equivalent: motion-controller.js resolveMotionFor() (this is the Android counterpart).
 *
 * Usage:
 *   val motionId = MotionPolicyAdapter.resolveRouteTransition(
 *       fromShell = RouteShell.MainTabShell,
 *       toShell = RouteShell.ReaderShell,
 *       operation = MotionOperation.Push
 *   )
 *   motionId?.let { MotionController.start(it.serialName, ...) }
 */
object MotionPolicyAdapter {

    /** All motion policies from the generated contract. */
    val all: List<MotionPolicy> get() = MotionPolicyRegistry.all

    /**
     * Resolve the best-matching [MotionId] for [request].
     *
     * Selection: filter policies whose [MotionPolicyMatch.matches] returns true for [request],
     * then pick the one with the highest composite key (priority * 1000 + specificity).
     * This mirrors the contract's ReaderMotionResolver semantics.
     *
     * Version 3.0 is fail-safe: an unmatched request returns null instead of silently selecting
     * a generic animation.
     */
    fun resolve(request: MotionRequest): MotionId? {
        return all
            .filter { it.match.matches(request) }
            .maxByOrNull { it.priority * 1000 + it.match.specificity }
            ?.motionId
    }

    /**
     * Convenience overload for route-transition scenarios.
     *
     * Maps [fromRoute]/[toRoute]/[fromShell]/[toShell]/[operation] into a [MotionPolicyMatch]
     * and resolves. RouteId strings use the contract's serialName (e.g. "bookshelf", "book-detail").
     *
     * Example: bookshelf → book-detail (MainTabShell → ReaderShell, Push) resolves to
     * ReaderEntryCoverToImmersive via the "bookshelf-cover-to-reader" policy (priority=350).
     */
    fun resolveRouteTransition(
        fromRoute: String? = null,
        toRoute: String? = null,
        fromShell: RouteShell? = null,
        toShell: RouteShell? = null,
        operation: MotionOperation
    ): MotionId? = resolve(
        MotionPolicyMatch(
            fromRoute = fromRoute,
            toRoute = toRoute,
            fromShell = fromShell,
            toShell = toShell,
            operation = operation,
            containerRole = MotionContainerRole.AppShell
        )
    )

    /**
     * Convenience overload for gesture / direct-manipulation scenarios.
     *
     * [sourceRole] examples: "handle", "dock", "slider", "tabItem", "toggle", "inputFocus".
     * [containerRole] examples: ReaderSurface, ListItem, MainTabShell.
     *
     * Example: handle drag-start in ReaderSurface resolves to ReaderControlHandlePress
     * via the "reader-control-handle-press" policy (priority=300).
     */
    fun resolveGesture(
        sourceRole: String,
        operation: MotionOperation,
        containerRole: MotionContainerRole
    ): MotionId? = resolve(
        MotionPolicyMatch(
            sourceRole = sourceRole,
            operation = operation,
            containerRole = containerRole
        )
    )

    /**
     * Convenience overload for overlay (sheet/dialog/toast/dropdown) scenarios.
     *
     * [targetRole] examples: "sheet", "dialog", "toast", "dropdown".
     * [containerRole] examples: ReaderShell, SettingsShell, OverlayHost.
     *
     * Example: sheet enter in ReaderShell resolves to OverlaySheetEnter
     * via the "reader-overlay-sheet-enter" policy (priority=300).
     */
    fun resolveOverlay(
        targetRole: String,
        operation: MotionOperation,
        containerRole: MotionContainerRole
    ): MotionId? = resolve(
        MotionPolicyMatch(
            targetRole = targetRole,
            operation = operation,
            containerRole = containerRole
        )
    )
}
