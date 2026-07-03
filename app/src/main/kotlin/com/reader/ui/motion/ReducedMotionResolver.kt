package com.reader.ui.motion

import android.content.Context
import android.provider.Settings
import androidx.core.content.getSystemService

/**
 * Resolves the platform reduced-motion preference.
 *
 * Per `MOTION_EFFECTS.md` §8 and `UI_PLATFORM_EVIDENCE_REQUESTS.md`, the Android side must
 * expose reduced-motion as a real testable switch — not a silent hardcoded value. The
 * contract's rejection rule forbids "reduced-motion that only closes some animations while
 * still preserving large displacement, looping pulse, or spinner".
 *
 * This resolver reads the system animator/transition duration scales (the closest Android
 * equivalent to `prefers-reduced-motion`) and is also overridable for tests via
 * [SetReducedMotion][com.reader.ui.shell.ReaderUiIntent.SetReducedMotion] intents.
 */
fun interface ReducedMotionResolver {
    fun isReducedMotion(): Boolean
}

/**
 * Production resolver: treats the system as reduced-motion when either the global animator
 * duration scale or the transition animation scale is 0 (developer option "Remove
 * animations" / "Animation off").
 */
class SystemAnimationScaleReducedMotionResolver(
    private val context: Context
) : ReducedMotionResolver {
    override fun isReducedMotion(): Boolean {
        val resolver = context.getSystemService<android.content.ContentResolver>()
            ?: context.contentResolver
        val animator = Settings.Global.getFloat(
            resolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        )
        val transition = Settings.Global.getFloat(
            resolver,
            Settings.Global.TRANSITION_ANIMATION_SCALE,
            1f
        )
        return animator == 0f || transition == 0f
    }
}

/** Test double that returns a fixed value. */
class FixedReducedMotionResolver(private val value: Boolean) : ReducedMotionResolver {
    override fun isReducedMotion(): Boolean = value
}
