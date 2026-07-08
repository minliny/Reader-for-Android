package com.reader.ui.shell

import io.reader.ui.contract.ActiveSession as ContractActiveSession
import io.reader.ui.contract.MainTab as ContractMainTab
import io.reader.ui.contract.Overlay as ContractOverlay
import io.reader.ui.contract.ReaderMode as ContractReaderMode
import io.reader.ui.contract.RouteRef
import io.reader.ui.contract.UiState as ContractUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM coverage for [UiStateProjector] — the lossy mapping layer between the
 * local rich [ReaderUiState] (sealed hierarchies + Android-specific fields) and the
 * generated flat [ContractUiState] (for the Core/IPC boundary).
 *
 * These tests run on the JVM (no device) — the projector is intentionally free of
 * Android / Compose dependencies so the mapping contract is verifiable in
 * `./gradlew test`.
 *
 * **What is proven**:
 *  - Forward: `currentRoute` → `route` (RouteRef id string), all 4 `MainTab` →
 *    contract `MainTab`, all 4 `OverlayState` variants → contract `Overlay?`,
 *    `ActiveSession` types → contract `ActiveSession?`, `reducedMotion` round-trips.
 *  - Forward: Android-specific fields (`backStack`, `pendingHostRequests`,
 *    `lastHostRequestResult`, `readerContext`, `motionInterrupt`) are dropped — the
 *    contract `UiState` has no slots for them.
 *  - Reverse: `mergeIntoLocal` preserves Android-specific local fields and
 *    overwrites only Core-owned fields (`activeTab`, `reducedMotion`).
 */
class UiStateProjectorJvmTest {

    private fun fixtureReaderContext(): ReaderContext = ReaderContext(
        sourceId = "fixture://demo",
        bookUrl = "fixture://demo/雨夜",
        bookName = "雨夜",
        entry = ReaderEntry.ACTION_TO_IMMERSIVE,
        entryRequestId = "req-1"
    )

    // ── toContract: route mapping ────────────────────────────────────────────────

    @Test
    fun `toContract_tabShellMapsToBookshelfRouteRef`() {
        val local = ReaderUiState(activeTab = MainTab.BOOKSHELF)
        val contract = with(UiStateProjector) { local.toContractUiState() }

        assertEquals("bookshelf", contract.route.id)
        assertTrue(contract.route.params.isEmpty())
        assertNull(contract.route.stack)
    }

    @Test
    fun `toContract_immersiveReadingMapsToImmersiveReadingRouteRef`() {
        val local = ReaderUiState(
            currentRoute = ReaderRoute.ImmersiveReading(context = fixtureReaderContext())
        )
        val contract = with(UiStateProjector) { local.toContractUiState() }

        assertEquals("immersive-reading", contract.route.id)
        // Rich ReaderContext is lossy-dropped (RouteRef has no slot for it).
        assertNull(contract.route.stack)
    }

    // ── toContract: MainTab mapping ──────────────────────────────────────────────

    @Test
    fun `toContract_mainTabMapsCorrectly`() {
        val expected = mapOf(
            MainTab.BOOKSHELF to ContractMainTab.Bookshelf,
            MainTab.DISCOVER to ContractMainTab.Discover,
            MainTab.RSS to ContractMainTab.Rss,
            MainTab.SETTINGS to ContractMainTab.Settings
        )
        // Cover all 4 canonical tabs (ORDER is bookshelf/discover/rss/settings).
        assertEquals(4, MainTab.ORDER.size)

        MainTab.ORDER.forEach { tab ->
            val local = ReaderUiState(activeTab = tab)
            val contract = with(UiStateProjector) { local.toContractUiState() }
            assertEquals(
                "tab $tab should map to ${expected[tab]}",
                expected[tab],
                contract.tab
            )
        }
    }

    // ── toContract: OverlayState mapping ─────────────────────────────────────────

    @Test
    fun `toContract_overlayStateMapsCorrectly`() {
        with(UiStateProjector) {
            // None → null
            ReaderUiState(overlayState = OverlayState.None).toContractUiState().let { c ->
                assertNull("None → null", c.overlay)
            }
            // Keyboard → Keyboard
            ReaderUiState(overlayState = OverlayState.Keyboard(inputId = "search"))
                .toContractUiState().let { c ->
                    assertEquals(ContractOverlay.Keyboard, c.overlay)
                }
            // Sheet → Sheet
            ReaderUiState(
                overlayState = OverlayState.Sheet(SheetContent.ReaderSetting("directory"))
            ).toContractUiState().let { c ->
                assertEquals(ContractOverlay.Sheet, c.overlay)
            }
            // Dialog → Dialog
            ReaderUiState(
                overlayState = OverlayState.Dialog(DialogContent.SourceSwitch("src-1"))
            ).toContractUiState().let { c ->
                assertEquals(ContractOverlay.Dialog, c.overlay)
            }
        }
    }

    // ── toContract: ActiveSession mapping ─────────────────────────────────────────

    @Test
    fun `toContract_activeSessionMapsCorrectly`() {
        with(UiStateProjector) {
            // TTS → Tts
            ReaderUiState(
                activeSession = ActiveSession(type = SessionType.TTS, playing = true)
            ).toContractUiState().let { c ->
                assertEquals(ContractActiveSession.Tts, c.activeSession)
            }
            // AUTO_PAGE → AutoPage
            ReaderUiState(
                activeSession = ActiveSession(type = SessionType.AUTO_PAGE, playing = true)
            ).toContractUiState().let { c ->
                assertEquals(ContractActiveSession.AutoPage, c.activeSession)
            }
            // NONE → null
            ReaderUiState(
                activeSession = ActiveSession(type = SessionType.NONE, playing = false)
            ).toContractUiState().let { c ->
                assertNull(c.activeSession)
            }
            // null local session → null contract session
            ReaderUiState(activeSession = null).toContractUiState().let { c ->
                assertNull(c.activeSession)
            }
        }
    }

    // ── toContract: Android-specific fields are dropped ─────────────────────────

    @Test
    fun `toContract_dropsAndroidSpecificFields`() {
        val local = ReaderUiState(
            activeTab = MainTab.BOOKSHELF,
            backStack = listOf(ReaderRoute.Search, ReaderRoute.LocalImport),
            pendingHostRequests = listOf(
                HostRequestDispatch(
                    dispatchId = "req-1",
                    capability = "tts.system.start",
                    paramsJson = "{}"
                )
            ),
            lastHostRequestResult = HostRequestResult(
                dispatchId = "req-1",
                capability = "tts.system.start",
                success = true
            ),
            readerContext = fixtureReaderContext(),
            motionInterrupt = MotionInterrupt(
                requestId = "req-1",
                from = "bookshelf",
                to = "search",
                kind = InterruptKind.REDIRECT
            )
        )
        val contract = with(UiStateProjector) { local.toContractUiState() }

        // The contract UiState has NO fields for backStack, pendingHostRequests,
        // lastHostRequestResult, readerContext, or motionInterrupt — they are
        // Android-specific and dropped at the IPC boundary. The strongest
        // assertion is the surviving lossy projection: route id + tab +
        // reducedMotion (all other contract fields default).
        assertEquals("bookshelf", contract.route.id)
        // backStack is NOT projected into route.stack (left null on purpose).
        assertNull(contract.route.stack)
        assertEquals(ContractMainTab.Bookshelf, contract.tab)
        assertFalse(contract.reducedMotion)
        // Sanity: contract carries no readerContext-shaped slot.
        assertNull(contract.reader)
    }

    // ── mergeIntoLocal: preserves Android-specific fields ───────────────────────

    @Test
    fun `mergeIntoLocal_preservesAndroidSpecificFields`() {
        val local = ReaderUiState(
            activeTab = MainTab.DISCOVER,
            backStack = listOf(ReaderRoute.Search),
            pendingHostRequests = listOf(
                HostRequestDispatch(
                    dispatchId = "req-1",
                    capability = "tts.system.start",
                    paramsJson = "{}"
                )
            ),
            lastHostRequestResult = HostRequestResult(
                dispatchId = "req-1",
                capability = "tts.system.start",
                success = true
            ),
            readerContext = fixtureReaderContext(),
            currentRoute = ReaderRoute.Search
        )
        val contract = ContractUiState(
            route = RouteRef(id = "bookshelf"),
            tab = ContractMainTab.Bookshelf,
            readerMode = ContractReaderMode.Default,
            loading = false,
            reducedMotion = false
        )

        val merged = with(UiStateProjector) { contract.mergeIntoLocal(local) }

        // Android-specific fields preserved from local.
        assertEquals(local.backStack, merged.backStack)
        assertEquals(local.pendingHostRequests, merged.pendingHostRequests)
        assertEquals(local.lastHostRequestResult, merged.lastHostRequestResult)
        assertEquals(local.readerContext, merged.readerContext)
        // currentRoute is intentionally NOT overwritten by the contract route.
        assertEquals(local.currentRoute, merged.currentRoute)
    }

    // ── mergeIntoLocal: overwrites Core-owned fields ─────────────────────────────

    @Test
    fun `mergeIntoLocal_overwritesCoreOwnedFields`() {
        val local = ReaderUiState(
            activeTab = MainTab.DISCOVER,
            reducedMotion = false
        )
        val contract = ContractUiState(
            route = RouteRef(id = "bookshelf"),
            tab = ContractMainTab.Rss,
            readerMode = ContractReaderMode.Default,
            loading = false,
            reducedMotion = true
        )

        val merged = with(UiStateProjector) { contract.mergeIntoLocal(local) }

        // Core-owned fields overwritten from contract.
        assertEquals(MainTab.RSS, merged.activeTab)
        assertTrue(merged.reducedMotion)
    }
}
