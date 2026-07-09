package com.reader.ui.tokens

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.reader.ui.contract.Token
import io.reader.ui.contract.TokenCategory
import io.reader.ui.contract.TokenRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderTokenAdapterTest {
    @Test
    fun `slice one tab tokens resolve through adapter`() {
        assertEquals(68.dp, ReaderTokenAdapter.size(ReaderSizeToken.BOTTOM_BAR_HEIGHT))
        assertEquals(68.dp, ReaderTokenAdapter.size(ReaderSizeToken.MAIN_NAV_HEIGHT))
        assertEquals(999.dp, ReaderTokenAdapter.radius(ReaderRadiusToken.CONTROL))
        assertEquals(20f, ReaderTokenAdapter.zIndex(ReaderZIndexToken.MAIN_NAV), 0.0f)
        assertEquals(160, ReaderTokenAdapter.durationMillis(ReaderDurationToken.TAB_SWITCH))
    }

    @Test
    fun `reduced motion collapses durations to zero`() {
        assertEquals(0, ReaderTokenAdapter.durationMillis(ReaderDurationToken.TAB_SWITCH, reducedMotion = true))
        assertEquals(0, ReaderTokenAdapter.durationMillis(ReaderDurationToken.READER_ENTRY, reducedMotion = true))
    }

    @Test
    fun `generated motion duration token resolves through adapter`() {
        val token = TokenRegistry.token("--fd-ds-motion-duration-tabSwitch")

        assertNotNull(token)
        token!!
        assertTrue(ReaderTokenAdapter.supports(token))
        assertEquals(160, ReaderTokenAdapter.durationMillis(token))
        assertEquals(0, ReaderTokenAdapter.durationMillis(token, reducedMotion = true))
    }

    @Test
    fun `motion duration semantic name reads generated token registry value`() {
        val token = Token(
            name = "app.motion.duration.tabSwitch",
            category = TokenCategory.MotionDuration,
            value = "999ms"
        )

        assertTrue(ReaderTokenAdapter.supports(token))
        assertEquals(160, ReaderTokenAdapter.durationMillis(token))
    }

    @Test
    fun `light and dark colors are semantic contract values`() {
        assertEquals(Color(0xFFFFF8F4), ReaderTokenAdapter.color(ReaderColorToken.PAPER))
        assertEquals(Color(0xFF24211E), ReaderTokenAdapter.color(ReaderColorToken.PAPER, ReaderTokenMode.DARK))
        assertTrue(ReaderColorToken.entries.all { it.contractName.startsWith("--fd-ds-color-") })
    }
}
