package com.reader.ui.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * 18 个 keyframes 的 Compose 等价实现
 *
 * 契约来源：
 * - motion-tokens.css 行 613-841（14 个 keyframes）
 * - styles/01-shell-layout.css 行 2625-2988（3 个 keyframes：tts-cursor-pulse / page-next / page-prev）
 * - styles/04-settings-source.css 行 1173-1187（1 个 keyframe：chapter-download-complete）
 *
 * fd-reader-loading-spin（800ms）已在 StateView.kt 实现，此处不重复。
 *
 * 每个 keyframe 遵循：
 * 1. 完整的 animateFloat / keyframes / infiniteRepeatable 配置
 * 2. reducedMotion 时 duration=0 直接落位（Animatable 初始值即终态，跳过 animateTo）
 * 3. 使用 ReaderMotionTokens / AppMotionTokens 的 duration token；缺失 token 以硬编码 + TODO 标注
 * 4. 通过 MotionController.start() 注册动效 transaction
 */

// ── 缺失 token 硬编码（待 ReaderMotionTokens / AppMotionTokens 补齐后替换） ──────────

// TODO(ReaderMotionTokens): 待补充以下 duration token 后替换硬编码
private const val DURATION_VOICE_PULSE_MS = 960       // fd-reader-session-voice-pulse
private const val DURATION_CONTROL_SPACE_ENTER_MS = 180 // fd-reader-control-space-enter
private const val DURATION_READER_PAGE_MS = 220        // fd-reader-page-next / prev
private const val DURATION_TTS_CURSOR_PULSE_MS = 1400  // fd-reader-tts-cursor-pulse
private const val DURATION_CHAPTER_DOWNLOAD_MS = 520   // fd-chapter-download-complete

// TODO(ReaderMotionTokens): 待补充以下 scale token 后替换硬编码
private const val SCALE_DIALOG_ENTER = 0.96f           // --reader-motion-scale-dialog-enter
private const val SCALE_CAPSULE_ENTER = 0.96f          // --reader-motion-scale-capsule-enter
private const val SCALE_VOICE_PULSE = 1.08f            // --reader-motion-scale-voice-pulse
private const val SCALE_RUNNING_SPACE_DOCK = 0.92f     // --reader-motion-scale-running-space-dock

// TODO(ReaderMotionTokens): 待补充以下 distance token 后替换硬编码（dp 值取自 CSS var 语义）
private val FIRST_OPEN_Y = 8.dp          // --fd-motion-effective-first-open-y
private val ORIENTATION_Y = 8.dp         // --fd-motion-effective-orientation-y
private val CAPSULE_Y = 4.dp             // --fd-motion-effective-capsule-y
private val CAPSULE_TICK_Y = 2.dp        // --fd-motion-effective-capsule-tick-y
private val RUNNING_SPACE_Y = 12.dp      // --fd-motion-effective-running-space-y
private val OVERLAY_SHEET_Y = 14.dp      // motion-tokens.css:776 translateY(14px)
private val PAGE_TURN_X = 16.dp          // 01-shell-layout.css:2971 translateX(16px)
private val TTS_CURSOR_X = 1.dp          // 01-shell-layout.css:2634 translateX(1px)

/**
 * 注册一次 motion transaction。
 * reducedMotion 时 durationFor 会将 duration 归零，MotionController 立即 settle。
 */
private fun registerMotion(
    motionId: String,
    from: String,
    to: String,
    durationMs: Long,
    reducedMotion: Boolean
) {
    MotionController.start(
        motionId = motionId,
        from = from,
        to = to,
        durationMs = MotionController.durationFor(durationMs, reducedMotion),
        reducedMotion = reducedMotion
    )
}

// ════════════════════════════════════════════════════════════════════════════════
// 一、一次性入场动画（Animatable + LaunchedEffect(Unit)）
// ════════════════════════════════════════════════════════════════════════════════

/**
 * 1. fd-app-first-open-enter（280ms 淡入 + 8px 位移）
 *
 * 契约：motion-tokens.css 行 705-714
 * from: opacity 0, translateY(first-open-y=8px)
 * to:   opacity 1, translateY(0)
 *
 * duration token: AppMotionTokens.DurationFirstOpen = 280ms
 */
@Composable
fun Modifier.appFirstOpenEnter(reducedMotion: Boolean = false): Modifier {
    val durationMs = AppMotionTokens.DurationFirstOpen.inWholeMilliseconds.toInt()
    val density = LocalDensity.current
    val displacementPx = with(density) { FIRST_OPEN_Y.toPx() }

    val alpha = remember { Animatable(if (reducedMotion) 1f else 0f) }
    val translationY = remember { Animatable(if (reducedMotion) 0f else displacementPx) }

    LaunchedEffect(Unit) {
        registerMotion("app.firstOpen.enter", "coldStart", "shellVisible", durationMs.toLong(), reducedMotion)
        if (!reducedMotion) {
            launch { alpha.animateTo(1f, tween(durationMs, easing = FastOutSlowInEasing)) }
            launch { translationY.animateTo(0f, tween(durationMs, easing = FastOutSlowInEasing)) }
        }
    }

    return this.graphicsLayer {
        this.alpha = alpha.value
        this.translationY = translationY.value
    }
}

/**
 * 2. fd-viewport-orientation-reshape（240ms 重排）
 *
 * 契约：motion-tokens.css 行 716-727
 * from: opacity 0.94, translateY(orientation-y), saturate(0.98)
 * to:   opacity 1, translateY(0), filter none
 *
 * duration token: ReaderMotionTokens.DurationViewportReshape = 240ms
 * 注：Compose graphicsLayer 不支持 saturate filter，此处仅动画 opacity + translationY。
 */
@Composable
fun Modifier.viewportOrientationReshape(reducedMotion: Boolean = false): Modifier {
    val durationMs = ReaderMotionTokens.DurationViewportReshape.inWholeMilliseconds.toInt()
    val density = LocalDensity.current
    val displacementPx = with(density) { ORIENTATION_Y.toPx() }

    val alpha = remember { Animatable(if (reducedMotion) 1f else 0.94f) }
    val translationY = remember { Animatable(if (reducedMotion) 0f else displacementPx) }

    LaunchedEffect(Unit) {
        registerMotion("viewport.orientation.reshape", "viewportFrozen", "viewportReshaped", durationMs.toLong(), reducedMotion)
        if (!reducedMotion) {
            launch { alpha.animateTo(1f, tween(durationMs, easing = FastOutSlowInEasing)) }
            launch { translationY.animateTo(0f, tween(durationMs, easing = FastOutSlowInEasing)) }
        }
    }

    return this.graphicsLayer {
        this.alpha = alpha.value
        this.translationY = translationY.value
    }
}

/**
 * 3. fd-viewport-orientation-anchor-settle（240ms 锚定）
 *
 * 契约：motion-tokens.css 行 729-740
 * from: opacity 0.92, translateY(-orientation-y), saturate(0.96)
 * to:   opacity 1, translateY(0), filter none
 *
 * duration token: ReaderMotionTokens.DurationOrientationSettle = 240ms
 * 注：方向与 reshape 相反（负方向位移回归）。
 */
@Composable
fun Modifier.viewportOrientationAnchorSettle(reducedMotion: Boolean = false): Modifier {
    val durationMs = ReaderMotionTokens.DurationOrientationSettle.inWholeMilliseconds.toInt()
    val density = LocalDensity.current
    val displacementPx = with(density) { (-ORIENTATION_Y).toPx() }

    val alpha = remember { Animatable(if (reducedMotion) 1f else 0.92f) }
    val translationY = remember { Animatable(if (reducedMotion) 0f else displacementPx) }

    LaunchedEffect(Unit) {
        registerMotion("viewport.orientation.settle", "viewportReshaped", "viewportStable", durationMs.toLong(), reducedMotion)
        if (!reducedMotion) {
            launch { alpha.animateTo(1f, tween(durationMs, easing = FastOutSlowInEasing)) }
            launch { translationY.animateTo(0f, tween(durationMs, easing = FastOutSlowInEasing)) }
        }
    }

    return this.graphicsLayer {
        this.alpha = alpha.value
        this.translationY = translationY.value
    }
}

/**
 * 4. fd-motion-interrupt-settle（80ms 收尾）
 *
 * 契约：motion-tokens.css 行 742-751
 * from: opacity 0.96, saturate(0.97)
 * to:   opacity 1, filter none
 *
 * duration token: ReaderMotionTokens.DurationInterruptSettle = 80ms
 */
@Composable
fun Modifier.motionInterruptSettle(reducedMotion: Boolean = false): Modifier {
    val durationMs = ReaderMotionTokens.DurationInterruptSettle.inWholeMilliseconds.toInt()
    val alpha = remember { Animatable(if (reducedMotion) 1f else 0.96f) }

    LaunchedEffect(Unit) {
        registerMotion("motion.interrupt.cancel", "motionRunning", "latestCommittedState", durationMs.toLong(), reducedMotion)
        if (!reducedMotion) {
            alpha.animateTo(1f, tween(durationMs, easing = FastOutSlowInEasing))
        }
    }

    return this.graphicsLayer { this.alpha = alpha.value }
}

/**
 * 5. fd-motion-async-complete（80ms 淡入）
 *
 * 契约：motion-tokens.css 行 753-760
 * from: opacity 0.96
 * to:   opacity 1
 *
 * duration token: ReaderMotionTokens.DurationMicro = 80ms
 */
@Composable
fun Modifier.motionAsyncComplete(reducedMotion: Boolean = false): Modifier {
    val durationMs = ReaderMotionTokens.DurationMicro.inWholeMilliseconds.toInt()
    val alpha = remember { Animatable(if (reducedMotion) 1f else 0.96f) }

    LaunchedEffect(Unit) {
        registerMotion("motion.async.resultGuard", "asyncPending", "asyncResolved", durationMs.toLong(), reducedMotion)
        if (!reducedMotion) {
            alpha.animateTo(1f, tween(durationMs, easing = FastOutSlowInEasing))
        }
    }

    return this.graphicsLayer { this.alpha = alpha.value }
}

/**
 * 6. fd-motion-overlay-dialog-enter（240ms scale 0.96→1）
 *
 * 契约：motion-tokens.css 行 762-771
 * from: opacity 0, translateY(-44%) scale(0.96)
 * to:   opacity 1, translateY(-50%) scale(1)
 *
 * duration token: ReaderMotionTokens.DurationReaderEntry = 240ms
 * 注：CSS 的 -44%→-50% 是居中微调，Compose 中由父容器居中；此处仅做 scale + opacity + 微位移。
 */
@Composable
fun Modifier.motionOverlayDialogEnter(reducedMotion: Boolean = false): Modifier {
    val durationMs = ReaderMotionTokens.DurationReaderEntry.inWholeMilliseconds.toInt()
    val alpha = remember { Animatable(if (reducedMotion) 1f else 0f) }
    val scale = remember { Animatable(if (reducedMotion) 1f else SCALE_DIALOG_ENTER) }

    LaunchedEffect(Unit) {
        registerMotion("overlay.dialog.enter", "hidden", "visible", durationMs.toLong(), reducedMotion)
        if (!reducedMotion) {
            launch { alpha.animateTo(1f, tween(durationMs, easing = FastOutSlowInEasing)) }
            launch { scale.animateTo(1f, tween(durationMs, easing = FastOutSlowInEasing)) }
        }
    }

    return this.graphicsLayer {
        this.alpha = alpha.value
        this.scaleX = scale.value
        this.scaleY = scale.value
    }
}

/**
 * 7. fd-motion-overlay-sheet-enter（160ms 底部滑入）
 *
 * 契约：motion-tokens.css 行 773-782
 * from: opacity 0.72, translateY(14px)
 * to:   opacity 1, translateY(0)
 *
 * duration token: ReaderMotionTokens.DurationBase = 160ms
 */
@Composable
fun Modifier.motionOverlaySheetEnter(reducedMotion: Boolean = false): Modifier {
    val durationMs = ReaderMotionTokens.DurationBase.inWholeMilliseconds.toInt()
    val density = LocalDensity.current
    val displacementPx = with(density) { OVERLAY_SHEET_Y.toPx() }

    val alpha = remember { Animatable(if (reducedMotion) 1f else 0.72f) }
    val translationY = remember { Animatable(if (reducedMotion) 0f else displacementPx) }

    LaunchedEffect(Unit) {
        registerMotion("overlay.sheet.enter", "hidden", "visible", durationMs.toLong(), reducedMotion)
        if (!reducedMotion) {
            launch { alpha.animateTo(1f, tween(durationMs, easing = FastOutSlowInEasing)) }
            launch { translationY.animateTo(0f, tween(durationMs, easing = FastOutSlowInEasing)) }
        }
    }

    return this.graphicsLayer {
        this.alpha = alpha.value
        this.translationY = translationY.value
    }
}

/**
 * 8. fd-reader-session-capsule-enter（160ms scale 0.96→1）
 *
 * 契约：motion-tokens.css 行 694-703
 * from: opacity 0, translateY(capsule-y) scale(0.96)
 * to:   opacity 1, translateY(0) scale(1)
 *
 * duration token: ReaderMotionTokens.DurationBase = 160ms
 */
@Composable
fun Modifier.readerSessionCapsuleEnter(reducedMotion: Boolean = false): Modifier {
    val durationMs = ReaderMotionTokens.DurationBase.inWholeMilliseconds.toInt()
    val density = LocalDensity.current
    val displacementPx = with(density) { CAPSULE_Y.toPx() }

    val alpha = remember { Animatable(if (reducedMotion) 1f else 0f) }
    val translationY = remember { Animatable(if (reducedMotion) 0f else displacementPx) }
    val scale = remember { Animatable(if (reducedMotion) 1f else SCALE_CAPSULE_ENTER) }

    LaunchedEffect(Unit) {
        registerMotion("reader.session.capsule.enter", "capsuleHidden", "capsuleVisible", durationMs.toLong(), reducedMotion)
        if (!reducedMotion) {
            launch { alpha.animateTo(1f, tween(durationMs, easing = FastOutSlowInEasing)) }
            launch { translationY.animateTo(0f, tween(durationMs, easing = FastOutSlowInEasing)) }
            launch { scale.animateTo(1f, tween(durationMs, easing = FastOutSlowInEasing)) }
        }
    }

    return this.graphicsLayer {
        this.alpha = alpha.value
        this.translationY = translationY.value
        this.scaleX = scale.value
        this.scaleY = scale.value
    }
}

/**
 * 10. fd-reader-control-space-enter（180ms dock 0.92→1）
 *
 * 契约：motion-tokens.css 行 817-826
 * from: opacity 0, translateY(-running-space-y) scale(0.92)
 * to:   opacity 1, translateY(0) scale(1)
 *
 * duration token: 硬编码 180ms（TODO: 待 ReaderMotionTokens 补充）
 */
@Composable
fun Modifier.readerControlSpaceEnter(reducedMotion: Boolean = false): Modifier {
    val durationMs = DURATION_CONTROL_SPACE_ENTER_MS
    val density = LocalDensity.current
    val displacementPx = with(density) { (-RUNNING_SPACE_Y).toPx() }

    val alpha = remember { Animatable(if (reducedMotion) 1f else 0f) }
    val translationY = remember { Animatable(if (reducedMotion) 0f else displacementPx) }
    val scale = remember { Animatable(if (reducedMotion) 1f else SCALE_RUNNING_SPACE_DOCK) }

    LaunchedEffect(Unit) {
        registerMotion("reader.session.controlSpace.enter", "capsuleVisible", "controlSpaceVisible", durationMs.toLong(), reducedMotion)
        if (!reducedMotion) {
            launch { alpha.animateTo(1f, tween(durationMs, easing = FastOutSlowInEasing)) }
            launch { translationY.animateTo(0f, tween(durationMs, easing = FastOutSlowInEasing)) }
            launch { scale.animateTo(1f, tween(durationMs, easing = FastOutSlowInEasing)) }
        }
    }

    return this.graphicsLayer {
        this.alpha = alpha.value
        this.translationY = translationY.value
        this.scaleX = scale.value
        this.scaleY = scale.value
    }
}

/**
 * 14. fd-motion-dropdown-switch-to（160ms）
 *
 * 契约：motion-tokens.css 行 613-623
 * from: opacity 0.72, translateY(dropdown-y * 0.5 = 3px)
 * to:   opacity 1, translateY(0)
 *
 * duration token: ReaderMotionTokens.DurationBase = 160ms
 * dropdown-y 取 AppMotionTokens.DropdownY = 6dp，其 0.5 = 3dp
 */
@Composable
fun Modifier.motionDropdownSwitchTo(reducedMotion: Boolean = false): Modifier {
    val durationMs = ReaderMotionTokens.DurationBase.inWholeMilliseconds.toInt()
    val density = LocalDensity.current
    val displacementPx = with(density) { (AppMotionTokens.DropdownY * 0.5f).toPx() }

    val alpha = remember { Animatable(if (reducedMotion) 1f else 0.72f) }
    val translationY = remember { Animatable(if (reducedMotion) 0f else displacementPx) }

    LaunchedEffect(Unit) {
        registerMotion("dropdown.menu.reposition", "openAtPreviousAnchor", "openAtLegalAnchor", durationMs.toLong(), reducedMotion)
        if (!reducedMotion) {
            launch { alpha.animateTo(1f, tween(durationMs, easing = FastOutSlowInEasing)) }
            launch { translationY.animateTo(0f, tween(durationMs, easing = FastOutSlowInEasing)) }
        }
    }

    return this.graphicsLayer {
        this.alpha = alpha.value
        this.translationY = translationY.value
    }
}

// ════════════════════════════════════════════════════════════════════════════════
// 二、触发式动画（Animatable + LaunchedEffect(trigger)）
// ════════════════════════════════════════════════════════════════════════════════

/**
 * 9. fd-reader-session-capsule-switch（120ms）
 *
 * 契约：motion-tokens.css 行 784-793
 * 0%:   opacity 0.72, scale 0.98
 * 100%: opacity 1, scale 1
 *
 * duration token: ReaderMotionTokens.DurationFast = 120ms
 *
 * @param trigger 触发标识，变化时重播动画
 */
@Composable
fun Modifier.readerSessionCapsuleSwitch(trigger: Any?, reducedMotion: Boolean = false): Modifier {
    val durationMs = ReaderMotionTokens.DurationFast.inWholeMilliseconds.toInt()
    val alpha = remember { Animatable(1f) }
    val scale = remember { Animatable(1f) }

    LaunchedEffect(trigger) {
        if (trigger == null) return@LaunchedEffect
        registerMotion("reader.session.capsule.switch", "session.previousType", "session.nextType", durationMs.toLong(), reducedMotion)
        if (!reducedMotion) {
            alpha.snapTo(0.72f)
            scale.snapTo(0.98f)
            launch { alpha.animateTo(1f, tween(durationMs, easing = FastOutSlowInEasing)) }
            launch { scale.animateTo(1f, tween(durationMs, easing = FastOutSlowInEasing)) }
        }
    }

    return this.graphicsLayer {
        this.alpha = alpha.value
        this.scaleX = scale.value
        this.scaleY = scale.value
    }
}

/**
 * 10. fd-reader-session-capsule-tick（120ms 数字替换）
 *
 * 契约：motion-tokens.css 行 795-804
 * 0%:   opacity 0, translateY(capsule-tick-y)
 * 100%: opacity 1, translateY(0)
 *
 * duration token: ReaderMotionTokens.DurationFast = 120ms
 *
 * @param trigger 触发标识，变化时重播动画
 */
@Composable
fun Modifier.readerSessionCapsuleTick(trigger: Any?, reducedMotion: Boolean = false): Modifier {
    val durationMs = ReaderMotionTokens.DurationFast.inWholeMilliseconds.toInt()
    val density = LocalDensity.current
    val displacementPx = with(density) { CAPSULE_TICK_Y.toPx() }

    val alpha = remember { Animatable(1f) }
    val translationY = remember { Animatable(0f) }

    LaunchedEffect(trigger) {
        if (trigger == null) return@LaunchedEffect
        registerMotion("reader.session.capsule.countdownTick", "countdown.previous", "countdown.next", durationMs.toLong(), reducedMotion)
        if (!reducedMotion) {
            alpha.snapTo(0f)
            translationY.snapTo(displacementPx)
            launch { alpha.animateTo(1f, tween(durationMs, easing = FastOutSlowInEasing)) }
            launch { translationY.animateTo(0f, tween(durationMs, easing = FastOutSlowInEasing)) }
        }
    }

    return this.graphicsLayer {
        this.alpha = alpha.value
        this.translationY = translationY.value
    }
}

/**
 * 13. fd-reader-control-space-update（120ms scale 1→0.992→1）
 *
 * 契约：motion-tokens.css 行 828-841
 * 0%:   scale 1
 * 55%:  scale 0.992
 * 100%: scale 1
 *
 * duration token: ReaderMotionTokens.DurationFast = 120ms
 * 注：CSS box-shadow keyframe 在 Compose 中省略（graphicsLayer 不支持阴影 keyframe）。
 *
 * @param trigger 触发标识，变化时重播动画
 */
@Composable
fun Modifier.readerControlSpaceUpdate(trigger: Any?, reducedMotion: Boolean = false): Modifier {
    val durationMs = ReaderMotionTokens.DurationFast.inWholeMilliseconds.toInt()
    val scale = remember { Animatable(1f) }

    LaunchedEffect(trigger) {
        if (trigger == null) return@LaunchedEffect
        registerMotion("reader.session.controlSpace.update", "session.previousState", "session.nextState", durationMs.toLong(), reducedMotion)
        if (!reducedMotion) {
            scale.snapTo(1f)
            scale.animateTo(
                targetValue = 1f,
                animationSpec = keyframes {
                    durationMillis = durationMs
                    1f at 0
                    0.992f at (durationMs * 0.55).toInt()
                    1f at durationMs
                }
            )
        }
    }

    return this.graphicsLayer {
        this.scaleX = scale.value
        this.scaleY = scale.value
    }
}

/**
 * 15. fd-reader-page-next（220ms 横向 16px 位移）
 *
 * 契约：styles/01-shell-layout.css 行 2968-2977
 * from: opacity 0, translateX(16px)
 * to:   opacity 1, translateX(0)
 *
 * duration token: 硬编码 220ms（TODO: 待 ReaderMotionTokens 补充）
 *
 * @param trigger 触发标识，变化时重播动画
 */
@Composable
fun Modifier.readerPageNext(trigger: Any?, reducedMotion: Boolean = false): Modifier {
    val durationMs = DURATION_READER_PAGE_MS
    val density = LocalDensity.current
    val displacementPx = with(density) { PAGE_TURN_X.toPx() }

    val alpha = remember { Animatable(1f) }
    val translationX = remember { Animatable(0f) }

    LaunchedEffect(trigger) {
        if (trigger == null) return@LaunchedEffect
        registerMotion("reader.page.turn.next/prev", "page.current", "page.next", durationMs.toLong(), reducedMotion)
        if (!reducedMotion) {
            alpha.snapTo(0f)
            translationX.snapTo(displacementPx)
            launch { alpha.animateTo(1f, tween(durationMs, easing = FastOutSlowInEasing)) }
            launch { translationX.animateTo(0f, tween(durationMs, easing = FastOutSlowInEasing)) }
        }
    }

    return this.graphicsLayer {
        this.alpha = alpha.value
        this.translationX = translationX.value
    }
}

/**
 * 16. fd-reader-page-prev（220ms 横向 -16px 位移）
 *
 * 契约：styles/01-shell-layout.css 行 2979-2988
 * from: opacity 0, translateX(-16px)
 * to:   opacity 1, translateX(0)
 *
 * duration token: 硬编码 220ms（TODO: 待 ReaderMotionTokens 补充）
 *
 * @param trigger 触发标识，变化时重播动画
 */
@Composable
fun Modifier.readerPagePrev(trigger: Any?, reducedMotion: Boolean = false): Modifier {
    val durationMs = DURATION_READER_PAGE_MS
    val density = LocalDensity.current
    val displacementPx = with(density) { (-PAGE_TURN_X).toPx() }

    val alpha = remember { Animatable(1f) }
    val translationX = remember { Animatable(0f) }

    LaunchedEffect(trigger) {
        if (trigger == null) return@LaunchedEffect
        registerMotion("reader.page.turn.next/prev", "page.current", "page.prev", durationMs.toLong(), reducedMotion)
        if (!reducedMotion) {
            alpha.snapTo(0f)
            translationX.snapTo(displacementPx)
            launch { alpha.animateTo(1f, tween(durationMs, easing = FastOutSlowInEasing)) }
            launch { translationX.animateTo(0f, tween(durationMs, easing = FastOutSlowInEasing)) }
        }
    }

    return this.graphicsLayer {
        this.alpha = alpha.value
        this.translationX = translationX.value
    }
}

/**
 * 19. fd-chapter-download-complete（520ms）
 *
 * 契约：styles/04-settings-source.css 行 1173-1187
 * 0%:   opacity 0.62, scale 0.72
 * 58%:  opacity 1, scale 1.15
 * 100%: opacity 1, scale 1
 *
 * duration token: 硬编码 520ms（TODO: 待 ReaderMotionTokens 补充）
 *
 * @param trigger 触发标识，变化时重播动画
 */
@Composable
fun Modifier.chapterDownloadComplete(trigger: Any?, reducedMotion: Boolean = false): Modifier {
    val durationMs = DURATION_CHAPTER_DOWNLOAD_MS
    val alpha = remember { Animatable(1f) }
    val scale = remember { Animatable(1f) }

    LaunchedEffect(trigger) {
        if (trigger == null) return@LaunchedEffect
        registerMotion("chapter.download.complete", "downloading", "downloaded", durationMs.toLong(), reducedMotion)
        if (!reducedMotion) {
            alpha.snapTo(0.62f)
            scale.snapTo(0.72f)
            launch {
                alpha.animateTo(
                    targetValue = 1f,
                    animationSpec = keyframes {
                        durationMillis = durationMs
                        0.62f at 0
                        1f at (durationMs * 0.58).toInt()
                        1f at durationMs
                    }
                )
            }
            launch {
                scale.animateTo(
                    targetValue = 1f,
                    animationSpec = keyframes {
                        durationMillis = durationMs
                        0.72f at 0
                        1.15f at (durationMs * 0.58).toInt()
                        1f at durationMs
                    }
                )
            }
        }
    }

    return this.graphicsLayer {
        this.alpha = alpha.value
        this.scaleX = scale.value
        this.scaleY = scale.value
    }
}

// ════════════════════════════════════════════════════════════════════════════════
// 三、无限循环动画（rememberInfiniteTransition）
// ════════════════════════════════════════════════════════════════════════════════

/**
 * 11. fd-reader-session-voice-pulse（960ms pulse）
 *
 * 契约：motion-tokens.css 行 806-815
 * 0%, 100%: opacity 0.82, scale 1
 * 50%:      opacity 1, scale(voice-pulse-scale=1.08)
 *
 * duration token: 硬编码 960ms（TODO: 待 ReaderMotionTokens 补充）
 * 使用 RepeatMode.Reverse 实现往返：0.82→1→0.82, 1→1.08→1
 */
@Composable
fun Modifier.readerSessionVoicePulse(reducedMotion: Boolean = false): Modifier {
    if (reducedMotion) return this  // reduced: 直接落位，不脉冲

    val halfDuration = DURATION_VOICE_PULSE_MS / 2
    val transition = rememberInfiniteTransition(label = "voice-pulse")

    val alpha by transition.animateFloat(
        initialValue = 0.82f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(halfDuration, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = SCALE_VOICE_PULSE,
        animationSpec = infiniteRepeatable(
            animation = tween(halfDuration, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    LaunchedEffect(Unit) {
        registerMotion(
            "reader.session.capsule.voiceIcon.active",
            "ttsPlaying",
            "ttsPlayingVisualActive",
            DURATION_VOICE_PULSE_MS.toLong(),
            reducedMotion
        )
    }

    return this.graphicsLayer {
        this.alpha = alpha
        this.scaleX = scale
        this.scaleY = scale
    }
}

/**
 * 17. fd-reader-tts-cursor-pulse（1400ms pulse）
 *
 * 契约：styles/01-shell-layout.css 行 2625-2636
 * 0%, 100%: opacity 0.42, translateX(0)
 * 50%:      opacity 0.82, translateX(1px)
 *
 * duration token: 硬编码 1400ms（TODO: 待 ReaderMotionTokens 补充）
 * 使用 RepeatMode.Reverse 实现往返：0.42→0.82→0.42, 0→1px→0
 */
@Composable
fun Modifier.readerTtsCursorPulse(reducedMotion: Boolean = false): Modifier {
    if (reducedMotion) return this  // reduced: 直接落位，不脉冲

    val halfDuration = DURATION_TTS_CURSOR_PULSE_MS / 2
    val density = LocalDensity.current
    val displacementPx = with(density) { TTS_CURSOR_X.toPx() }

    val transition = rememberInfiniteTransition(label = "tts-cursor-pulse")

    val alpha by transition.animateFloat(
        initialValue = 0.42f,
        targetValue = 0.82f,
        animationSpec = infiniteRepeatable(
            animation = tween(halfDuration, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    val translationX by transition.animateFloat(
        initialValue = 0f,
        targetValue = displacementPx,
        animationSpec = infiniteRepeatable(
            animation = tween(halfDuration, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "translationX"
    )

    LaunchedEffect(Unit) {
        registerMotion(
            "reader.tts.cursor.pulse",
            "ttsCursorIdle",
            "ttsCursorActive",
            DURATION_TTS_CURSOR_PULSE_MS.toLong(),
            reducedMotion
        )
    }

    return this.graphicsLayer {
        this.alpha = alpha
        this.translationX = translationX
    }
}
