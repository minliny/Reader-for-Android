package com.reader.android.data.storage

import org.junit.Assert.assertEquals
import org.junit.Test

class TapZoneConfigTest {

    @Test
    fun `default config has 9 zones`() {
        val config = ClickAreaConfig()
        assertEquals(9, config.zones.size)
    }

    @Test
    fun `actionFor returns correct action for top center`() {
        val config = ClickAreaConfig()
        assertEquals(TapAction.MENU, config.actionFor(VerticalZone.TOP, HorizontalZone.CENTER))
    }

    @Test
    fun `actionFor returns correct action for middle left`() {
        val config = ClickAreaConfig()
        assertEquals(TapAction.PREV_CHAPTER, config.actionFor(VerticalZone.MIDDLE, HorizontalZone.LEFT))
    }

    @Test
    fun `actionFor returns correct action for middle right`() {
        val config = ClickAreaConfig()
        assertEquals(TapAction.NEXT_CHAPTER, config.actionFor(VerticalZone.MIDDLE, HorizontalZone.RIGHT))
    }

    @Test
    fun `actionFor returns correct action for bottom right`() {
        val config = ClickAreaConfig()
        assertEquals(TapAction.NEXT_PAGE, config.actionFor(VerticalZone.BOTTOM, HorizontalZone.RIGHT))
    }

    @Test
    fun `custom zone mapping overrides default`() {
        val custom = ClickAreaConfig(
            zones = mapOf("TOP_CENTER" to TapAction.NEXT_CHAPTER)
        )
        assertEquals(TapAction.NEXT_CHAPTER, custom.actionFor(VerticalZone.TOP, HorizontalZone.CENTER))
    }

    @Test
    fun `missing zone returns NONE`() {
        val empty = ClickAreaConfig(zones = emptyMap())
        assertEquals(TapAction.NONE, empty.actionFor(VerticalZone.TOP, HorizontalZone.LEFT))
    }

    @Test
    fun `TapAction enum has 7 values`() {
        assertEquals(7, TapAction.entries.size)
    }

    @Test
    fun `all 9 zone keys present in default config`() {
        val config = ClickAreaConfig()
        val expectedKeys = listOf(
            "TOP_LEFT", "TOP_CENTER", "TOP_RIGHT",
            "MIDDLE_LEFT", "MIDDLE_CENTER", "MIDDLE_RIGHT",
            "BOTTOM_LEFT", "BOTTOM_CENTER", "BOTTOM_RIGHT"
        )
        expectedKeys.forEach { key ->
            val action = config.zones[key]
            assertEquals("Missing key: $key", true, action != null)
        }
    }
}
