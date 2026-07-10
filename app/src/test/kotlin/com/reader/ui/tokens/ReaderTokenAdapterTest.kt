package com.reader.ui.tokens

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.reader.ui.contract.Token
import io.reader.ui.contract.TokenCategory
import io.reader.ui.contract.TokenRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

    @Test
    fun `spacing tokens resolve through adapter`() {
        assertEquals(8.dp, ReaderTokenAdapter.spacing(ReaderSpacingToken.XS))
        assertEquals(16.dp, ReaderTokenAdapter.spacing(ReaderSpacingToken.MD))
        assertEquals(48.dp, ReaderTokenAdapter.spacing(ReaderSpacingToken.XL))
        assertEquals(14.dp, ReaderTokenAdapter.spacing(ReaderSpacingToken.CARD_PADDING))
        assertTrue(ReaderSpacingToken.entries.all { it.contractName.startsWith("--fd-ds-space-") || it.contractName.startsWith("--fd-ds-safe-area-") })
    }

    @Test
    fun `type tokens resolve through adapter including new contract sizes`() {
        assertEquals(20.sp, ReaderTokenAdapter.type(ReaderTypeToken.APP_TITLE))
        assertEquals(18.sp, ReaderTokenAdapter.type(ReaderTypeToken.READER_BODY))
        // New type tokens added for ReaderTextStyles contract alignment.
        assertEquals(29.sp, ReaderTokenAdapter.type(ReaderTypeToken.APP_BAR_TITLE))
        assertEquals(29.sp, ReaderTokenAdapter.type(ReaderTypeToken.BACK_BAR_TITLE))
        assertEquals(11.sp, ReaderTokenAdapter.type(ReaderTypeToken.ACTION_LABEL))
        assertEquals(13.sp, ReaderTokenAdapter.type(ReaderTypeToken.CHAPTER_TITLE))
        assertEquals(19.sp, ReaderTokenAdapter.type(ReaderTypeToken.EMPTY_HEADING))
        assertEquals(23.sp, ReaderTokenAdapter.type(ReaderTypeToken.READER_CHAPTER_TITLE))
        assertEquals(16.sp, ReaderTokenAdapter.type(ReaderTypeToken.TOP_BAR_TITLE))
    }

    @Test
    fun `easing tokens resolve through adapter`() {
        // Easing is a function — verify identity easing passes values through unchanged.
        val standard = ReaderTokenAdapter.easing(ReaderEasingToken.STANDARD)
        assertEquals(0.0f, standard.transform(0.0f), 0.0f)
        assertEquals(0.5f, standard.transform(0.5f), 0.0f)
        assertEquals(1.0f, standard.transform(1.0f), 0.0f)
        assertTrue(ReaderEasingToken.entries.all { it.contractName.startsWith("--fd-ds-motion-easing-") })
    }

    @Test
    fun `font token resolves to FontFamily Default for sans`() {
        val token = TokenRegistry.token("--fd-ds-font-sans")
        assertNotNull(token)
        token!!
        assertTrue(ReaderTokenAdapter.supports(token))
        assertEquals(FontFamily.Default, ReaderTokenAdapter.font(token))
    }

    @Test
    fun `font tokens map to Compose FontFamily per platforms kotlin`() {
        val serif = TokenRegistry.token("--fd-ds-font-serif")!!
        val kai = TokenRegistry.token("--fd-ds-font-kai")!!
        val fangsong = TokenRegistry.token("--fd-ds-font-fangsong")!!
        val mono = TokenRegistry.token("--fd-ds-font-mono")!!

        assertEquals(FontFamily.Serif, ReaderTokenAdapter.font(serif))
        // kai/fangsong have no native Compose equivalent — contract maps them to FontFamily.Serif.
        assertEquals(FontFamily.Serif, ReaderTokenAdapter.font(kai))
        assertEquals(FontFamily.Serif, ReaderTokenAdapter.font(fangsong))
        assertEquals(FontFamily.Monospace, ReaderTokenAdapter.font(mono))

        // Non-font categories return null.
        val nonFont = Token(
            name = "--fd-ds-color-paper",
            category = TokenCategory.Color,
            value = "#fff8f4"
        )
        assertNull(ReaderTokenAdapter.font(nonFont))
    }

    @Test
    fun `text constraint token resolves reader line length as integer`() {
        val token = TokenRegistry.token("--fd-ds-text-reader-line-length")
        assertNotNull(token)
        token!!
        assertTrue(ReaderTokenAdapter.supports(token))
        assertEquals(31, ReaderTokenAdapter.textConstraint(token))
    }

    @Test
    fun `text constraint tokens parse line counts`() {
        val navLabelLines = TokenRegistry.token("--fd-ds-text-nav-label-lines")!!
        val bookTitleLines = TokenRegistry.token("--fd-ds-text-book-title-lines")!!

        assertEquals(1, ReaderTokenAdapter.textConstraint(navLabelLines))
        assertEquals(2, ReaderTokenAdapter.textConstraint(bookTitleLines))

        // Non-text-constraint categories return null.
        val nonTextConstraint = Token(
            name = "--fd-ds-color-paper",
            category = TokenCategory.Color,
            value = "#fff8f4"
        )
        assertNull(ReaderTokenAdapter.textConstraint(nonTextConstraint))
    }
}
