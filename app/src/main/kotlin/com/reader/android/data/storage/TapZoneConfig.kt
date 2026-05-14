package com.reader.android.data.storage

enum class TapAction {
    MENU,
    PREV_CHAPTER,
    NEXT_CHAPTER,
    PREV_PAGE,
    NEXT_PAGE,
    TOGGLE_FULLSCREEN,
    NONE
}

enum class VerticalZone { TOP, MIDDLE, BOTTOM }
enum class HorizontalZone { LEFT, CENTER, RIGHT }

data class TapZone(
    val vertical: VerticalZone,
    val horizontal: HorizontalZone,
    val action: TapAction = when {
        vertical == VerticalZone.TOP -> TapAction.MENU
        vertical == VerticalZone.BOTTOM -> TapAction.MENU
        horizontal == HorizontalZone.LEFT -> TapAction.PREV_PAGE
        horizontal == HorizontalZone.RIGHT -> TapAction.NEXT_PAGE
        else -> TapAction.MENU
    }
)

data class ClickAreaConfig(
    val zones: Map<String, TapAction> = defaultZones()
) {
    fun actionFor(vertical: VerticalZone, horizontal: HorizontalZone): TapAction {
        val key = "${vertical}_${horizontal}"
        return zones[key] ?: TapAction.NONE
    }

    companion object {
        fun defaultZones(): Map<String, TapAction> = mapOf(
            "TOP_LEFT" to TapAction.PREV_PAGE,
            "TOP_CENTER" to TapAction.MENU,
            "TOP_RIGHT" to TapAction.NEXT_PAGE,
            "MIDDLE_LEFT" to TapAction.PREV_CHAPTER,
            "MIDDLE_CENTER" to TapAction.TOGGLE_FULLSCREEN,
            "MIDDLE_RIGHT" to TapAction.NEXT_CHAPTER,
            "BOTTOM_LEFT" to TapAction.PREV_PAGE,
            "BOTTOM_CENTER" to TapAction.MENU,
            "BOTTOM_RIGHT" to TapAction.NEXT_PAGE
        )
    }
}
