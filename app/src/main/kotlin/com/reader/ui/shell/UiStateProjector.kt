package com.reader.ui.shell

import io.reader.ui.contract.ActiveSession as ContractActiveSession
import io.reader.ui.contract.MainTab as ContractMainTab
import io.reader.ui.contract.Overlay as ContractOverlay
import io.reader.ui.contract.ReaderMode as ContractReaderMode
import io.reader.ui.contract.RouteRef
import io.reader.ui.contract.UiState as ContractUiState

/**
 * Maps between local rich [ReaderUiState] (with sealed hierarchies + Android-specific
 * fields) and generated contract [ContractUiState] (flat, serializable, for the Core/IPC
 * boundary).
 *
 * Forward ([toContractUiState]): lossy projection — Android-specific fields
 * (`backStack`, `readerContext`, `motionInterrupt`, `motionPhase`, `textSelection`,
 * `readerControl`, `asyncResult`, `viewport`, `moreMenu`, `sourceImport`,
 * `webDavConfig`, `permissions`, `rssList`, `pendingHostRequests`,
 * `lastHostRequestResult`) are DROPPED. The contract type has no fields for them.
 *
 * Reverse ([mergeIntoLocal]): merge — contract fields overwrite local counterparts
 * that Core owns (`activeTab`, `reducedMotion`); local Android-specific fields are
 * preserved.
 *
 * The contract [ContractUiState] uses `kotlinx.serialization.json.JsonElement` for
 * several sub-state fields (`bookshelf`/`discover`/`reader`/`settings`/`motion`/
 * `firstOpen`). Mapping the local rich sub-states into those opaque maps is left as
 * TODO in this first cut — only the common scalar/enum fields
 * (`route`, `tab`, `readerMode`, `overlay`, `activeSession`, `focusTarget`,
 * `loading`, `error`, `reducedMotion`) are projected. The complex sub-states default
 * to `null` in the forward direction and are untouched in the reverse direction.
 *
 * The projector is pure JVM (no Android `Context` dependency) — it is just data
 * mapping, so it is unit-testable on the JVM without a device.
 */
object UiStateProjector {

    // ── Forward: local → contract ────────────────────────────────────────────────

    /**
     * Lossy projection of the local [ReaderUiState] into the flat
     * [ContractUiState]. Android-specific fields are dropped (the contract type has
     * no slots for them). Complex sub-states (`bookshelf`/`discover`/`reader`/
     * `settings`/`motion`/`firstOpen`) are projected as `null` until their
     * JsonElement-based mappings are wired (see TODOs in this file).
     */
    fun ReaderUiState.toContractUiState(): ContractUiState = ContractUiState(
        route = currentRoute.toContractRouteRef(),
        tab = activeTab.toContractMainTab(),
        readerMode = deriveReaderMode(),
        overlay = overlayState.toContractOverlay(),
        activeSession = activeSession?.toContractActiveSession(),
        focusTarget = null, // TODO: derive from local focus state once contract semantics settle
        loading = false,    // TODO: derive from asyncResult / motionPhase once mapped
        error = null,       // TODO: map from a local error slice (none today)
        reducedMotion = reducedMotion,
        pageState = null,   // TODO: map from local page-state slice (none today)
        // Complex sub-states below — TODO: map once JsonElement-based contract slots
        // are wired. Leaving null is safe (all are nullable in the contract).
        bookshelf = null,
        discover = null,
        reader = null,
        settings = null,
        motion = null,
        firstOpen = null
    )

    /**
     * Map a local [ReaderRoute] to a flat [RouteRef] (id string + empty params).
     *
     * Uses the existing top-level [routeId] extension to derive the id string; the
     * local sealed hierarchy encodes richer data (e.g. [ReaderRoute.ImmersiveReading]
     * carries a [ReaderContext]) that the flat [RouteRef] cannot represent — those
     * are intentionally lossy.
     *
     * `stack` is left `null`: although the local [ReaderUiState.backStack] exists,
     * its entries are rich [ReaderRoute] instances, not flat [RouteRef]s. Projecting
     * them recursively is possible but is TODO; the first cut drops the back-stack
     * projection (it is an Android-specific concern at the IPC boundary).
     */
    fun ReaderRoute.toContractRouteRef(): RouteRef = RouteRef(
        id = routeId,
        params = emptyMap(),
        stack = null
    )

    /**
     * Map the local [MainTab] (BOOKSHELF/DISCOVER/RSS/SETTINGS) to the contract
     * [ContractMainTab] (Bookshelf/Discover/Rss/Settings). Order is fixed by the
     * cross-platform UI baseline.
     */
    fun MainTab.toContractMainTab(): ContractMainTab = when (this) {
        MainTab.BOOKSHELF -> ContractMainTab.Bookshelf
        MainTab.DISCOVER -> ContractMainTab.Discover
        MainTab.RSS -> ContractMainTab.Rss
        MainTab.SETTINGS -> ContractMainTab.Settings
    }

    /**
     * Map the local [OverlayState] sealed hierarchy to the contract
     * [ContractOverlay] enum. `None` → `null` (no overlay); the three concrete
     * overlay kinds collapse to their coarse contract counterparts
     * (Keyboard/Sheet/Dialog).
     */
    fun OverlayState.toContractOverlay(): ContractOverlay? = when (this) {
        OverlayState.None -> null
        is OverlayState.Keyboard -> ContractOverlay.Keyboard
        is OverlayState.Sheet -> ContractOverlay.Sheet
        is OverlayState.Dialog -> ContractOverlay.Dialog
    }

    /**
     * Map the local [ActiveSession] to the contract [ContractActiveSession] enum.
     * `TTS` → `Tts`, `AUTO_PAGE` → `AutoPage`, `NONE` → `null` (no session).
     * Local session playback/chapter/coundown detail is dropped (the contract enum
     * is a coarse kind, not a struct).
     */
    fun ActiveSession.toContractActiveSession(): ContractActiveSession? = when (type) {
        SessionType.TTS -> ContractActiveSession.Tts
        SessionType.AUTO_PAGE -> ContractActiveSession.AutoPage
        SessionType.NONE -> null
    }

    // ── Reverse: contract → local (merge, not replace) ──────────────────────────

    /**
     * Merge this contract [ContractUiState] into the local [ReaderUiState].
     *
     * Only fields that Core owns are overwritten (`activeTab`, `reducedMotion`).
     * All Android-specific local fields are preserved as-is:
     * `currentRoute`, `backStack`, `readerContext`, `activeSession`, `overlayState`,
     * `motionInterrupt`, `motionPhase`, `textSelection`, `readerControl`,
     * `asyncResult`, `viewport`, `moreMenu`, `sourceImport`, `webDavConfig`,
     * `permissions`, `rssList`, `pendingHostRequests`, `lastHostRequestResult`.
     *
     * Reverse-mapping the contract `route` ([RouteRef] id-string + params) back into
     * a specific [ReaderRoute] sealed subclass is intentionally NOT done: the local
     * reducer is the source of truth for routing, and the contract `RouteRef` is a
     * lossy projection of it (not the other way around). Round-tripping would lose
     * the rich data carried by e.g. [ReaderRoute.ImmersiveReading].
     *
     * Reverse-mapping `overlay`/`activeSession`/`readerMode`/`focusTarget`/
     * `loading`/`error`/`pageState`/`bookshelf`/`discover`/`reader`/`settings`/
     * `motion`/`firstOpen` is TODO — those either have no direct local counterpart
     * yet or require JsonElement decoding that is deferred.
     */
    fun ContractUiState.mergeIntoLocal(local: ReaderUiState): ReaderUiState = local.copy(
        activeTab = tab.fromContractMainTab(),
        reducedMotion = reducedMotion
        // NOTE: currentRoute is intentionally preserved — see kdoc above.
        // NOTE: all other local Android-specific fields are preserved by copy().
    )

    // ── Private helpers ───────────────────────────────────────────────────────────

    /**
     * Derive the contract [ContractReaderMode] from the local state. The local
     * [ReaderUiState] does not yet carry a direct `readerMode` counterpart, so we
     * default to [ContractReaderMode.Default]. When a local page-state / error
     * slice lands, this should map loading/empty/error/offline/permission accordingly.
     */
    private fun ReaderUiState.deriveReaderMode(): ContractReaderMode =
        ContractReaderMode.Default

    private fun ContractMainTab.fromContractMainTab(): MainTab = when (this) {
        ContractMainTab.Bookshelf -> MainTab.BOOKSHELF
        ContractMainTab.Discover -> MainTab.DISCOVER
        ContractMainTab.Rss -> MainTab.RSS
        ContractMainTab.Settings -> MainTab.SETTINGS
    }
}
