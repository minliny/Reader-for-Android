package io.reader.android.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** One appearance owner per ReaderApp/window; layout Insets stay with each screen owner. */
@Composable
internal fun ReaderSystemBarAppearance(background: Color) {
    val activity = LocalActivity.current ?: return // Preview/non-Activity host has no system window.
    val view = LocalView.current
    if (view.isInEditMode) return
    val window = activity.window
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val controller = remember(window, view) { WindowCompat.getInsetsController(window, view) }
    val useDarkIcons by rememberUpdatedState(background.luminance() > 0.5f)

    // Do not key this effect to the theme/route/color: an appearance change must
    // not restore stale flags between two commits in the SAME window owner.
    DisposableEffect(controller, lifecycle) {
        val previousStatus = controller.isAppearanceLightStatusBars
        val previousNavigation = controller.isAppearanceLightNavigationBars
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                controller.isAppearanceLightStatusBars = useDarkIcons
                controller.isAppearanceLightNavigationBars = useDarkIcons
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            // Captured controller belongs to this exact old window, never a new Activity.
            controller.isAppearanceLightStatusBars = previousStatus
            controller.isAppearanceLightNavigationBars = previousNavigation
        }
    }
    SideEffect {
        // "Light appearance" requests dark glyphs. Actual paper/theme background
        // drives both status and navigation indicators, independently of appDark.
        controller.isAppearanceLightStatusBars = useDarkIcons
        controller.isAppearanceLightNavigationBars = useDarkIcons
    }
}
