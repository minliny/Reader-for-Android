package io.reader.rebuild.features.shell

import android.animation.ValueAnimator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.reader.core.theme.ReaderDesignMetrics

/** Only the destination remains composed. Presentation effects never retain the previous page owner. */
@Composable
internal fun ReaderRouteMotion(route: Long?, popping: Boolean, content: @Composable () -> Unit) {
    val progress = remember { Animatable(1f) }
    val reduceMotion = !ValueAnimator.areAnimatorsEnabled()
    val leg = with(LocalDensity.current) { ReaderDesignMetrics.spaceMedium.dp.toPx() }
    val logicalDirection = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
    val distance = if (reduceMotion) 0f else (if (popping) -leg else leg) * logicalDirection
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(route) {
        if (!started) {
            started = true
            return@LaunchedEffect
        }
        started = true
        val duration = when {
            reduceMotion -> ReaderDesignMetrics.reducedMotionFadeMillis
            popping -> ReaderDesignMetrics.navigationPopMillis
            else -> ReaderDesignMetrics.navigationPushMillis
        }
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMillis = duration, easing = FastOutSlowInEasing))
    }
    Box(Modifier.fillMaxSize().graphicsLayer {
        translationX = distance * (1f - progress.value)
        alpha = if (reduceMotion) progress.value else 0.88f + progress.value * 0.12f
    }) { content() }
}
