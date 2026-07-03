package com.reader.ui.motion

import androidx.compose.ui.unit.dp
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Motion contract adapter for Reader for Android.
 *
 * Token names and values mirror `Reader UI` `frontend-demo/MOTION_CONTRACT.md` §3 and
 * `docs/ui-handoff/MOTION_PLATFORM_MAPPING.md` §1 verbatim. Android easing names may differ
 * from the Web demo as long as visual results are equivalent (MOTION_CONTRACT.md §3).
 *
 * Nothing here is derived from Web CSS variables, `data-*` selectors, or fixture route
 * stacks — only the token *names* and *semantics* are inherited from the UI contract.
 */
object MotionIds {
    // App launch
    const val APP_LAUNCH_FIRST_OPEN = "app.launch.firstOpen"

    // Main tab bar (Slice 1)
    const val TAB_ITEM_PRESS = "tab.item.press"
    const val TAB_ITEM_SELECT = "tab.item.select"
    const val TAB_ITEM_SWITCH = "tab.item.switch"
    const val APP_TAB_SWITCH = "app.tab.switch"

    // Reader entry (Slice 2)
    const val READER_ENTRY_COVER_TO_IMMERSIVE = "reader.entry.coverToImmersive"
    const val READER_ENTRY_ACTION_TO_IMMERSIVE = "reader.entry.actionToImmersive"

    // Route stack (Slice 2)
    const val APP_ROUTE_PUSH = "app.route.push"
    const val APP_ROUTE_POP = "app.route.pop"
    const val APP_ROUTE_REPLACE = "app.route.replace"

    // Interrupt family (Slice 0/1/2 reducer hook)
    const val MOTION_INTERRUPT_CANCEL = "motion.interrupt.cancel"
    const val MOTION_INTERRUPT_REDIRECT = "motion.interrupt.redirect"
    const val MOTION_INTERRUPT_COMPLETE_THEN_REPLACE = "motion.interrupt.completeThenReplace"

    // Async result guard (Slice 2 reader loading)
    const val MOTION_ASYNC_RESULT_GUARD = "motion.async.resultGuard"

    // Viewport (Slice 6 — state-preservation contract wired from Slice 1/2, full verification deferred)
    const val VIEWPORT_ORIENTATION_PREPARE = "viewport.orientation.prepare"
    const val VIEWPORT_ORIENTATION_RESHAPE = "viewport.orientation.reshape"
    const val VIEWPORT_ORIENTATION_SETTLE = "viewport.orientation.settle"
}

/**
 * App-shell motion tokens. Names match `MOTION_PLATFORM_MAPPING.md` §1
 * (`AppMotionTokens.DurationFirstOpen`, `AppMotionTokens.PressScale`, ...).
 */
object AppMotionTokens {
    /** `app.motion.duration.firstOpen` = 280ms — cold-start first screen entry. */
    val DurationFirstOpen: Duration = 280.milliseconds

    /** `app.motion.duration.tabPress` = 80ms — tab button press/cancel feedback. */
    val DurationTabPress: Duration = 80.milliseconds

    /** `app.motion.duration.tabSelect` = 120ms — single tab entering/leaving active. */
    val DurationTabSelect: Duration = 120.milliseconds

    /** `app.motion.duration.tabSwitch` = 160ms — active A → B migration. */
    val DurationTabSwitch: Duration = 160.milliseconds

    /** `app.motion.duration.buttonPress` = 80ms — generic button press feedback. */
    val DurationButtonPress: Duration = 80.milliseconds

    /** `app.motion.scale.press` = 1 → 0.98 → 1. */
    const val PressScale: Float = 0.98f

    /** `app.motion.distance.dropdownY` = 6px (kept for later slices). */
    val DropdownY = 6.dp
}

/**
 * Reader-shell motion tokens. Names match `MOTION_PLATFORM_MAPPING.md` §1
 * (`ReaderMotionTokens.DurationReaderEntry`, `ReaderMotionTokens.ReaderEntryY`, ...).
 */
object ReaderMotionTokens {
    /** `reader.motion.duration.instant` = 0ms — reduced motion / pure state switch. */
    val DurationInstant: Duration = 0.milliseconds

    /** `reader.motion.duration.micro` = 80ms — press feedback, light select. */
    val DurationMicro: Duration = 80.milliseconds

    /** `reader.motion.duration.fast` = 120ms — tab select, chip/toggle. */
    val DurationFast: Duration = 120.milliseconds

    /** `reader.motion.duration.base` = 160ms — keyboard, sheet, dialog, focus lift. */
    val DurationBase: Duration = 160.milliseconds

    /** `reader.motion.duration.readerEntry` = 240ms — cover / continue-reading → immersive. */
    val DurationReaderEntry: Duration = 240.milliseconds

    /** `reader.motion.duration.sessionReturn` = 200ms — session start → return to immersive. */
    val DurationSessionReturn: Duration = 200.milliseconds

    /** `reader.motion.duration.interruptSettle` = 80ms — post-interrupt minimum settle. */
    val DurationInterruptSettle: Duration = 80.milliseconds

    /** `reader.motion.duration.viewportReshape` = 240ms — fold / orientation reshape. */
    val DurationViewportReshape: Duration = 240.milliseconds

    /** `reader.motion.duration.orientationFreeze` = 80ms — orientation prepare freeze. */
    val DurationOrientationFreeze: Duration = 80.milliseconds

    /** `reader.motion.duration.orientationSettle` = 240ms — orientation settle. */
    val DurationOrientationSettle: Duration = 240.milliseconds

    /** `reader.motion.distance.readerEntryY` = 12px — text layer rise on reader entry. */
    val ReaderEntryY = 12.dp

    /** `reader.motion.scale.coverPress` = 0.98 — cover pressed scale. */
    const val CoverPressScale: Float = 0.98f
}

/**
 * Resolves the effective duration under the reduced-motion rule
 * (MOTION_CONTRACT.md §7 / MOTION_EFFECTS.md §8).
 *
 * Under reduced motion all durations collapse to `DurationInstant` (0ms); only color,
 * opacity, focus ring, and selected-state changes are preserved. Callers that animate
 * displacement MUST additionally zero out the displacement when [reducedMotion] is true.
 */
fun effectiveDuration(base: Duration, reducedMotion: Boolean): Duration =
    if (reducedMotion) ReaderMotionTokens.DurationInstant else base
