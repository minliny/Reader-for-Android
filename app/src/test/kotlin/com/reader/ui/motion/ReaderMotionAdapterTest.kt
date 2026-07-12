package com.reader.ui.motion

import io.reader.ui.contract.MotionId
import io.reader.ui.contract.MotionSpecRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderMotionAdapterTest {
    @Test
    fun `slice one tab switch maps to Compose motion spec`() {
        val spec = ReaderMotionAdapter.specFor(MotionIdConstants.TAB_ITEM_SWITCH)

        assertEquals(MotionIdConstants.TAB_ITEM_SWITCH, spec.motionId)
        assertEquals(160, spec.durationMillis)
        assertFalse(spec.allowsMovement)
        assertFalse(spec.reducedMotion)
        assertTrue(spec.finalState.isNotBlank())
    }

    @Test
    fun `generated tab and app shell motion ids map to Compose motion spec`() {
        val tab = ReaderMotionAdapter.specFor(MotionId.TabSwitch)
        val route = ReaderMotionAdapter.specFor(MotionId.AppRoutePushForward)

        assertEquals("tab.switch", tab.motionId)
        assertEquals(160, tab.durationMillis)
        assertEquals("app.route.push.forward", route.motionId)
        assertEquals(160, route.durationMillis)
        assertTrue(ReaderMotionAdapter.hasMotion(MotionId.TabSwitch))
        assertTrue(ReaderMotionAdapter.hasMotion(MotionId.AppRouteReplace))
    }

    @Test
    fun `generated motion spec registry is the adapter source`() {
        val registrySpec = MotionSpecRegistry.spec(MotionId.TabSwitch)
        val adapterSpec = ReaderMotionAdapter.specFor(MotionId.TabSwitch)

        assertNotNull(registrySpec)
        assertEquals("app.motion.duration.tabSwitch", registrySpec!!.tokens?.durationToken)
        assertEquals(registrySpec.durationMs, adapterSpec.durationMillis)
    }

    @Test
    fun `reduced motion removes duration and movement`() {
        val spec = ReaderMotionAdapter.specFor(
            MotionIdConstants.READER_ENTRY_COVER_TO_IMMERSIVE,
            reducedMotion = true
        )

        assertEquals(0, spec.durationMillis)
        assertFalse(spec.allowsMovement)
        assertTrue(spec.reducedMotion)
    }

    @Test
    fun `p0 route and reader motions are registered`() {
        val required = listOf(
            MotionIdConstants.APP_FIRST_OPEN_ENTER,
            MotionIdConstants.APP_ROUTE_PUSH_FORWARD,
            MotionIdConstants.APP_ROUTE_POP_BACKWARD,
            MotionIdConstants.APP_ROUTE_REPLACE,
            MotionIdConstants.TAB_ITEM_SELECT,
            MotionIdConstants.TAB_ITEM_SWITCH,
            MotionIdConstants.READER_ENTRY_COVER_TO_IMMERSIVE,
            MotionIdConstants.READER_ENTRY_ACTION_TO_IMMERSIVE,
            MotionIdConstants.READER_PAGE_TURN_NEXT_PREV,
            MotionIdConstants.READER_CONTROL_HIDE,
            MotionIdConstants.MOTION_INTERRUPT_CANCEL,
            MotionIdConstants.MOTION_INTERRUPT_REDIRECT,
            MotionIdConstants.MOTION_INTERRUPT_COMPLETE_THEN_REPLACE,
            MotionIdConstants.VIEWPORT_ORIENTATION_RESHAPE
        )

        assertTrue(required.all { ReaderMotionAdapter.hasMotion(it) })
    }

    @Test
    fun `all 93 generated motions expose unique exact serializer wire names`() {
        assertEquals(93, MotionId.entries.size)
        assertEquals(MotionId.entries.toSet(), MotionSpecRegistry.all.map { it.id }.toSet())

        val serialNames = MotionId.entries.map { it.serialName }
        assertEquals(93, serialNames.toSet().size)
        assertEquals(
            mapOf(
                MotionId.DropdownMenuExpand to "dropdown.menu.expand",
                MotionId.DropdownMenuReposition to "dropdown.menu.reposition",
                MotionId.DropdownOptionPress to "dropdown.option.press",
                MotionId.ReaderControlHandleDrag to "reader.control.handle.drag",
                MotionId.ReaderControlShow to "reader.control.show",
                MotionId.ReaderPageTurnNextPrev to "reader.page.turn.next-prev",
                MotionId.ReaderSessionCapsuleControlPressToggle to "reader.session.capsule.control.press-toggle",
                MotionId.TabItemPress to "tab.item.press",
                MotionId.TabItemSwitch to "tab.item.switch"
            ),
            listOf(
                MotionId.DropdownMenuExpand,
                MotionId.DropdownMenuReposition,
                MotionId.DropdownOptionPress,
                MotionId.ReaderControlHandleDrag,
                MotionId.ReaderControlShow,
                MotionId.ReaderPageTurnNextPrev,
                MotionId.ReaderSessionCapsuleControlPressToggle,
                MotionId.TabItemPress,
                MotionId.TabItemSwitch
            ).associateWith { it.serialName }
        )
    }

    @Test
    fun `generated visual pattern drives movement policy for new motions`() {
        val handleDrag = ReaderMotionAdapter.specFor(MotionId.ReaderControlHandleDrag)
        val tabPress = ReaderMotionAdapter.specFor(MotionId.TabItemPress)
        val reducedHandleDrag = ReaderMotionAdapter.specFor(
            MotionId.ReaderControlHandleDrag,
            reducedMotion = true
        )

        assertTrue(handleDrag.allowsMovement)
        assertFalse(tabPress.allowsMovement)
        assertFalse(reducedHandleDrag.allowsMovement)
        assertEquals(0, reducedHandleDrag.durationMillis)
    }
}
