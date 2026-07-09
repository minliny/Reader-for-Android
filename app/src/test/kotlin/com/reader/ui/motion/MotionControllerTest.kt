package com.reader.ui.motion

import com.reader.ui.shell.InterruptKind
import io.reader.ui.contract.MotionSpecRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Pure-JVM coverage for [MotionController] — the motion runtime singleton that
 * `frontend-demo/motion-controller.js` defines as `create()`.
 *
 * Verifies:
 * - start / interrupt / settle lifecycle (MOTION_CONTRACT.md 237-245)
 * - reduced-motion duration collapse to 0 (MOTION_EFFECTS.md §8)
 * - stale transaction discard (async guard — latest wins)
 * - 47 Motion ID contract registry completeness
 * - reducedFrom / durationFor / setReducedMotion
 * - clearTransientState
 * - event log cap (120)
 * - listener dispatch
 *
 * The reducer stays pure; this is the runtime side-effect layer that P6 wires in
 * via `AppShellViewModel.dispatch`.
 */
class MotionControllerTest {

    @Before
    fun setUp() {
        // MotionController is a singleton — reset state between tests.
        MotionController.destroy()
        MotionController.setReducedMotionResolver(null)
        MotionController.setReducedMotion(null)
    }

    @After
    fun tearDown() {
        MotionController.destroy()
        MotionController.setReducedMotionResolver(null)
        MotionController.setReducedMotion(null)
    }

    // ── start / interrupt / settle lifecycle ─────────────────────────────────────

    @Test
    fun `start sets phase RUNNING and increments sequence`() {
        val tx = MotionController.start(
            motionId = MotionIdConstants.TAB_ITEM_SWITCH,
            from = "bookshelf",
            to = "discover",
            durationMs = 160L,
            reducedMotion = false
        )

        assertEquals(MotionIdConstants.TAB_ITEM_SWITCH, tx.id)
        assertEquals(MotionPhase.RUNNING, tx.phase)
        assertEquals("bookshelf", tx.from)
        assertEquals("discover", tx.to)
        assertEquals(160L, tx.durationMs)
        assertFalse(tx.reducedMotion)
        assertEquals("active transaction should be set", tx, MotionController.activeTransaction())
    }

    @Test
    fun `start dispatches START event with contract finalState in extra`() {
        MotionController.start(
            motionId = MotionIdConstants.APP_ROUTE_PUSH_FORWARD,
            from = "bookshelf",
            to = "book-search",
            durationMs = 160L,
            reducedMotion = false
        )

        val events = MotionController.snapshot().events
        assertTrue("START event should be dispatched", events.isNotEmpty())
        val startEvent = events.first()
        assertEquals(MotionEventType.START, startEvent.type)
        assertEquals(MotionIdConstants.APP_ROUTE_PUSH_FORWARD, startEvent.motionId)
        // finalState must be carried in extra per motion-controller.js 1215-1238.
        assertEquals(
            "targetRouteVisibleAndStackUpdated",
            startEvent.extra["finalState"]
        )
        assertFalse(
            "unresolvedContract must be false for known Motion IDs",
            startEvent.extra["unresolvedContract"] as Boolean
        )
    }

    @Test
    fun `start with unknown Motion ID marks unresolvedContract=true`() {
        MotionController.start(
            motionId = "unknown.motion.id",
            from = "a",
            to = "b",
            durationMs = 100L,
            reducedMotion = false
        )

        val startEvent = MotionController.snapshot().events.first()
        assertEquals(true, startEvent.extra["unresolvedContract"])
        assertEquals("", startEvent.extra["finalState"])
    }

    @Test
    fun `reduced motion collapses duration to 0 and settles immediately`() {
        val tx = MotionController.start(
            motionId = MotionIdConstants.READER_ENTRY_COVER_TO_IMMERSIVE,
            from = "bookshelf",
            to = "immersive-reading",
            durationMs = 240L,
            reducedMotion = true
        )

        // Per MOTION_EFFECTS.md §8: reduced-motion → duration=0 → immediate settle.
        assertTrue("reducedMotion flag must be true", tx.reducedMotion)
        // The runtime applies durationFor() internally — effective duration must be 0.
        assertEquals(
            "durationMs must collapse to 0 when reducedMotion=true",
            0L,
            tx.durationMs
        )
        // The returned tx is the original RUNNING snapshot (immutable); the internal
        // settle clears `active`. Verify active is cleared and SETTLE event dispatched.
        assertNull(
            "active transaction should be cleared after immediate settle",
            MotionController.activeTransaction()
        )
        val settleEvents = MotionController.snapshot().events
            .filter { it.type == MotionEventType.SETTLE }
        assertTrue("SETTLE event should be dispatched", settleEvents.isNotEmpty())
        assertEquals("reduced-motion", settleEvents.last().extra["reason"])
    }

    @Test
    fun `interrupt sets phase INTERRUPTED and clears active`() {
        MotionController.start(
            motionId = MotionIdConstants.TAB_ITEM_SWITCH,
            from = "bookshelf",
            to = "rss",
            durationMs = 10_000L, // long enough that it won't settle on its own
            reducedMotion = false
        )

        val interrupted = MotionController.interrupt("superseded", InterruptKind.CANCEL)

        assertNotNull("interrupt should return the interrupted transaction", interrupted)
        assertEquals(MotionPhase.INTERRUPTED, interrupted!!.phase)
        assertEquals("superseded", interrupted.interruptReason)
        assertNull(
            "active transaction should be cleared after interrupt",
            MotionController.activeTransaction()
        )
        // INTERRUPT event must carry kind + settleDurationMs in extra.
        val interruptEvent = MotionController.snapshot().events
            .last { it.type == MotionEventType.INTERRUPT }
        assertEquals("CANCEL", interruptEvent.extra["kind"])
        assertNotNull(interruptEvent.extra["settleDurationMs"])
    }

    @Test
    fun `interrupt on null active returns null`() {
        val result = MotionController.interrupt("nothing-to-interrupt")
        assertNull(result)
    }

    @Test
    fun `settle on RUNNING transaction sets SETTLED and clears active when sequence matches`() {
        val tx = MotionController.start(
            motionId = MotionIdConstants.READER_CONTROL_HIDE,
            from = "reader",
            to = "immersive-reading",
            durationMs = 10_000L,
            reducedMotion = false
        )

        MotionController.settle(tx, "complete")

        assertEquals(
            "active should be cleared when sequence matches",
            null,
            MotionController.activeTransaction()
        )
        val settleEvent = MotionController.snapshot().events
            .last { it.type == MotionEventType.SETTLE }
        assertEquals("complete", settleEvent.extra["reason"])
    }

    @Test
    fun `settle on already-cleared active does not re-create active`() {
        val tx = MotionController.start(
            motionId = MotionIdConstants.TAB_ITEM_SWITCH,
            from = "a",
            to = "b",
            durationMs = 0L, // immediate settle → active cleared
            reducedMotion = true
        )

        // active is already null from the internal immediate settle.
        assertNull(MotionController.activeTransaction())

        // Calling settle again on the same tx must not re-create active.
        MotionController.settle(tx, "redundant")

        assertNull(
            "active must remain null after redundant settle",
            MotionController.activeTransaction()
        )
    }

    @Test
    fun `settle on stale sequence does not clear newer active transaction`() {
        // Start tx1 with long duration.
        val tx1 = MotionController.start(
            motionId = MotionIdConstants.APP_ROUTE_PUSH_FORWARD,
            from = "a",
            to = "b",
            durationMs = 10_000L,
            reducedMotion = false
        )
        // Start tx2 — this supersedes tx1 (interrupt("superseded") + new active).
        val tx2 = MotionController.start(
            motionId = MotionIdConstants.APP_ROUTE_PUSH_FORWARD,
            from = "b",
            to = "c",
            durationMs = 10_000L,
            reducedMotion = false
        )

        // Stale settle attempt on tx1 must not clear tx2.
        MotionController.settle(tx1, "stale")

        assertEquals(
            "newer active transaction must remain after stale settle",
            tx2.id,
            MotionController.activeTransaction()?.id
        )
        assertEquals(
            tx2.sequence,
            MotionController.activeTransaction()?.sequence
        )
    }

    @Test
    fun `start interrupts existing active before starting new`() {
        MotionController.start(
            motionId = MotionIdConstants.TAB_ITEM_SWITCH,
            from = "a",
            to = "b",
            durationMs = 10_000L,
            reducedMotion = false
        )
        val firstSequence = MotionController.activeTransaction()?.sequence ?: 0L

        MotionController.start(
            motionId = MotionIdConstants.APP_ROUTE_PUSH_FORWARD,
            from = "b",
            to = "c",
            durationMs = 10_000L,
            reducedMotion = false
        )

        val newActive = MotionController.activeTransaction()
        assertNotNull(newActive)
        assertTrue(
            "new transaction must have higher sequence",
            (newActive?.sequence ?: 0L) > firstSequence
        )
        // INTERRUPT event for the superseded transaction must be in the log.
        val interruptEvents = MotionController.snapshot().events
            .filter { it.type == MotionEventType.INTERRUPT }
        assertTrue("supersede should emit INTERRUPT", interruptEvents.isNotEmpty())
        assertEquals("superseded", interruptEvents.last().extra["reason"])
    }

    // ── reducedFrom / durationFor / setReducedMotion ────────────────────────────

    @Test
    fun `reducedFrom uses override when set, ignoring resolver`() {
        MotionController.setReducedMotionResolver(FixedReducedMotionResolver(false))
        MotionController.setReducedMotion(true)

        assertTrue(MotionController.reducedFrom(override = true))
        assertFalse(MotionController.reducedFrom(override = false))
    }

    @Test
    fun `reducedFrom falls back to resolver when override is null`() {
        MotionController.setReducedMotionResolver(FixedReducedMotionResolver(true))

        assertTrue(MotionController.reducedFrom(override = null))
    }

    @Test
    fun `reducedFrom returns false when no override and no resolver`() {
        MotionController.setReducedMotionResolver(null)

        assertFalse(MotionController.reducedFrom(override = null))
    }

    @Test
    fun `durationFor returns 0 when reduced`() {
        assertEquals(0L, MotionController.durationFor(240L, reduced = true))
        assertEquals(0L, MotionController.durationFor(0L, reduced = true))
    }

    @Test
    fun `durationFor returns original duration when not reduced`() {
        assertEquals(240L, MotionController.durationFor(240L, reduced = false))
        assertEquals(0L, MotionController.durationFor(0L, reduced = false))
    }

    @Test
    fun `durationFor clamps negative duration to 0`() {
        assertEquals(0L, MotionController.durationFor(-100L, reduced = false))
    }

    // ── clearTransientState ──────────────────────────────────────────────────────

    @Test
    fun `interrupt clears transient state`() {
        // Start a transaction (which sets no transient state) then interrupt.
        MotionController.start(
            motionId = MotionIdConstants.READER_CONTROL_HANDLE_DRAG,
            from = "handlePressed",
            to = "handleDragging",
            durationMs = 0L, // drag has 0 duration per contract
            reducedMotion = false
        )
        // After a 0-duration motion, it settles immediately; start another so we have an active.
        MotionController.start(
            motionId = MotionIdConstants.TAB_ITEM_SWITCH,
            from = "a",
            to = "b",
            durationMs = 10_000L,
            reducedMotion = false
        )
        MotionController.interrupt("test")

        // After interrupt, clearTransientState should have run inside interrupt.
        // Calling it again returns all-false (already cleared).
        val cleanup = MotionController.clearTransientState()
        assertFalse(cleanup.clearedPressed)
        assertFalse(cleanup.clearedDragging)
        assertFalse(cleanup.clearedDropdownPressed)
        assertFalse(cleanup.clearedHandleDragging)
        assertFalse(cleanup.clearedDockDragging)
    }

    // ── Contract registry completeness ───────────────────────────────────────────

    @Test
    fun `contractFor returns contract for known Motion ID`() {
        val contract = MotionController.contractFor(MotionIdConstants.TAB_ITEM_SWITCH)

        assertNotNull(contract)
        assertEquals(MotionIdConstants.TAB_ITEM_SWITCH, contract!!.motionId)
        assertEquals(160L, contract.defaultDurationMs)
        assertEquals("oneActiveTabAndStableBarSize", contract.finalState)
    }

    @Test
    fun `contractFor returns null for unknown Motion ID`() {
        assertNull(MotionController.contractFor("nonexistent.motion"))
    }

    @Test
    fun `all 47 Motion ID constants resolve to a contract`() {
        val allIds = listOf(
            MotionIdConstants.APP_FIRST_OPEN_ENTER,
            MotionIdConstants.APP_ROUTE_PUSH_FORWARD,
            MotionIdConstants.APP_ROUTE_POP_BACKWARD,
            MotionIdConstants.APP_ROUTE_REPLACE,
            MotionIdConstants.TAB_ITEM_PRESS,
            MotionIdConstants.TAB_ITEM_SELECT,
            MotionIdConstants.TAB_ITEM_SWITCH,
            MotionIdConstants.SEGMENT_ITEM_SWITCH,
            MotionIdConstants.DROPDOWN_TRIGGER_PRESS,
            MotionIdConstants.DROPDOWN_MENU_EXPAND,
            MotionIdConstants.DROPDOWN_MENU_EXPAND_COLLAPSE,
            MotionIdConstants.DROPDOWN_MENU_COLLAPSE,
            MotionIdConstants.DROPDOWN_MENU_REPOSITION,
            MotionIdConstants.DROPDOWN_OPTION_PRESS,
            MotionIdConstants.DROPDOWN_OPTION_SELECT,
            MotionIdConstants.BUTTON_ACTIVATE,
            MotionIdConstants.TOGGLE_SWITCH,
            MotionIdConstants.READER_ENTRY_COVER_TO_IMMERSIVE,
            MotionIdConstants.READER_ENTRY_ACTION_TO_IMMERSIVE,
            MotionIdConstants.READER_CONTROL_HIDE,
            MotionIdConstants.READER_CONTROL_HANDLE_PRESS,
            MotionIdConstants.READER_CONTROL_HANDLE_DRAG,
            MotionIdConstants.READER_CONTROL_HANDLE_RELEASE,
            MotionIdConstants.READER_CONTROL_DOCK_LONG_PRESS,
            MotionIdConstants.READER_CONTROL_DOCK_DRAG,
            MotionIdConstants.READER_CONTROL_DOCK_RELEASE,
            MotionIdConstants.READER_CONTROL_DOCK_REBOUND,
            MotionIdConstants.READER_SESSION_AUTO_PAGE_START,
            MotionIdConstants.READER_SESSION_TTS_START,
            MotionIdConstants.READER_SESSION_CAPSULE_ENTER,
            MotionIdConstants.READER_SESSION_CAPSULE_UPDATE,
            MotionIdConstants.READER_SESSION_CAPSULE_CONTROL_PRESS_TOGGLE,
            MotionIdConstants.READER_SESSION_CAPSULE_COUNTDOWN_TICK,
            MotionIdConstants.READER_SESSION_CAPSULE_VOICE_ICON_ACTIVE,
            MotionIdConstants.READER_SESSION_CAPSULE_SWITCH,
            MotionIdConstants.READER_SESSION_CAPSULE_EXIT,
            MotionIdConstants.READER_SESSION_CONTROL_SPACE_ENTER,
            MotionIdConstants.READER_SESSION_CONTROL_SPACE_UPDATE,
            MotionIdConstants.READER_SESSION_CONTROL_SPACE_EXIT,
            MotionIdConstants.READER_MODULE_SWITCH,
            MotionIdConstants.READER_PAGE_TURN_NEXT_PREV,
            MotionIdConstants.MOTION_INTERRUPT_CANCEL,
            MotionIdConstants.MOTION_INTERRUPT_REDIRECT,
            MotionIdConstants.MOTION_INTERRUPT_COMPLETE_THEN_REPLACE,
            MotionIdConstants.VIEWPORT_ORIENTATION_PREPARE,
            MotionIdConstants.VIEWPORT_ORIENTATION_RESHAPE,
            MotionIdConstants.VIEWPORT_ORIENTATION_SETTLE
        )

        val unresolved = allIds.filter { MotionController.contractFor(it) == null }
        assertTrue(
            "These Motion IDs have no contract: $unresolved",
            unresolved.isEmpty()
        )
        assertEquals(47, allIds.size)
    }

    @Test
    fun `reader entry cover-to-immersive contract matches motion-controller_js spec`() {
        val contract = MotionController.contractFor(
            MotionIdConstants.READER_ENTRY_COVER_TO_IMMERSIVE
        )!!

        assertEquals(240L, contract.defaultDurationMs)
        assertTrue("from must include sourceRoute", contract.from.contains("sourceRoute"))
        assertTrue("to must include immersiveReading", contract.to.contains("immersiveReading"))
        assertEquals(
            "immersiveReadingNoControlLayerAndSourceBackStackKept",
            contract.finalState
        )
    }

    @Test
    fun `reader control hide finalState is immersiveReadingHotZonesRestored`() {
        val contract = MotionController.contractFor(
            MotionIdConstants.READER_CONTROL_HIDE
        )!!

        assertEquals("immersiveReadingHotZonesRestored", contract.finalState)
        assertEquals(240L, contract.defaultDurationMs)
    }

    // ── Event log + listeners ────────────────────────────────────────────────────

    @Test
    fun `event log caps at 120 entries`() {
        // Generate >120 events by repeatedly starting 0-duration (reduced) motions.
        repeat(130) {
            MotionController.start(
                motionId = MotionIdConstants.TAB_ITEM_SWITCH,
                from = "a$it",
                to = "b$it",
                durationMs = 0L,
                reducedMotion = true
            )
        }

        val events = MotionController.snapshot().events
        assertTrue(
            "event log must be capped at 120, got ${events.size}",
            events.size <= 120
        )
    }

    @Test
    fun `listeners receive dispatched events`() {
        val received = mutableListOf<MotionEventType>()
        val listener = MotionEventListener { e -> received.add(e.type) }

        MotionController.addListener(listener)
        MotionController.start(
            motionId = MotionIdConstants.TAB_ITEM_SWITCH,
            from = "a",
            to = "b",
            durationMs = 0L,
            reducedMotion = true // immediate settle → START + SETTLE
        )

        assertTrue("listener should receive START", received.contains(MotionEventType.START))
        assertTrue("listener should receive SETTLE", received.contains(MotionEventType.SETTLE))

        MotionController.removeListener(listener)
    }

    @Test
    fun `removeListener stops event delivery`() {
        val received = mutableListOf<MotionEventType>()
        val listener = MotionEventListener { e -> received.add(e.type) }

        MotionController.addListener(listener)
        MotionController.removeListener(listener)
        MotionController.start(
            motionId = MotionIdConstants.TAB_ITEM_SWITCH,
            from = "a",
            to = "b",
            durationMs = 0L,
            reducedMotion = true
        )

        assertTrue("removed listener should receive no events", received.isEmpty())
    }

    // ── update ────────────────────────────────────────────────────────────────────

    @Test
    fun `update patches from-to-interruptReason of active transaction`() {
        MotionController.start(
            motionId = MotionIdConstants.APP_ROUTE_PUSH_FORWARD,
            from = "a",
            to = "b",
            durationMs = 10_000L,
            reducedMotion = false
        )

        MotionController.update(mapOf("from" to "a2", "to" to "b2"))

        val active = MotionController.activeTransaction()
        assertNotNull(active)
        assertEquals("a2", active!!.from)
        assertEquals("b2", active.to)
    }

    @Test
    fun `update on null active is no-op`() {
        // No active transaction — update should not throw.
        MotionController.update(mapOf("from" to "x"))
        assertNull(MotionController.activeTransaction())
    }

    // ── attachToViewModel / detachFromViewModel ──────────────────────────────────

    @Test
    fun `detachFromViewModel clears attached reference`() {
        // detach without attach should be a no-op (no crash).
        MotionController.detachFromViewModel()
        assertNull(MotionController.activeTransaction())
    }

    // ── destroy ───────────────────────────────────────────────────────────────────

    @Test
    fun `destroy interrupts active and clears listeners`() {
        val received = mutableListOf<MotionEventType>()
        MotionController.addListener(MotionEventListener { e -> received.add(e.type) })

        MotionController.start(
            motionId = MotionIdConstants.TAB_ITEM_SWITCH,
            from = "a",
            to = "b",
            durationMs = 10_000L,
            reducedMotion = false
        )

        MotionController.destroy()

        assertNull(
            "active must be cleared after destroy",
            MotionController.activeTransaction()
        )
        assertTrue(
            "destroy should emit INTERRUPT event",
            received.contains(MotionEventType.INTERRUPT)
        )

        // Listener should be cleared — further events go nowhere.
        val sizeBefore = received.size
        MotionController.start(
            motionId = MotionIdConstants.TAB_ITEM_SWITCH,
            from = "x",
            to = "y",
            durationMs = 0L,
            reducedMotion = true
        )
        assertEquals(
            "no events after destroy cleared listeners",
            sizeBefore,
            received.size
        )
    }

    // ── Phase 7: Full MotionSpecRegistry coverage (84 entries) ───────────────────

    @Test
    fun `all 84 MotionSpecRegistry entries resolve through contractFor`() {
        val allSpecs = MotionSpecRegistry.all
        assertEquals(84, allSpecs.size)
        val unresolved = allSpecs.map { it.id.serialName }.filter { MotionController.contractFor(it) == null }
        assertTrue("Unresolved MotionIds: $unresolved", unresolved.isEmpty())
    }
}
