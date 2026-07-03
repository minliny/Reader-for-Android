package com.reader.ui

import androidx.compose.runtime.Composable
import com.reader.ui.motion.ReducedMotionResolver
import com.reader.ui.shell.AppShell

/**
 * Root composable. Delegates to [AppShell], the native Compose App Shell that hosts the four
 * main tabs and the immersive-reading route. Route state is owned by the single-state
 * [com.reader.ui.shell.AppShellViewModel] + reducer flow per
 * FRONTEND_DEVELOPMENT_SLICE_MATRIX.md Slice 1.
 */
@Composable
fun ReaderApp(reducedMotionResolver: ReducedMotionResolver? = null) {
    AppShell(reducedMotionResolver = reducedMotionResolver)
}
