package com.reader.ui.shell

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.ui.unit.IntOffset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.reader.ui.motion.MotionController
import com.reader.ui.motion.MotionIdConstants
import com.reader.ui.motion.ReaderMotionTokens

/**
 * 路由级转场工具
 *
 * 契约来源：MOTION_CONTRACT.md 行 144-145 app.route.push/pop
 * - push：新路由从右侧滑入（+16dp 位移）+ 淡入，160ms
 * - pop：旧路由向右滑出，160ms
 * - replace：原地替换无位移
 *
 * 等价 keyframes：fd-reader-page-next/prev 220ms 横向 16px（翻页用）
 * 路由级用 160ms（fd-motion-overlay-sheet-enter 节奏）
 */
@Composable
fun ReaderRouteTransition(
    targetRoute: ReaderRoute,
    transitionDirection: RouteTransitionDirection,
    modifier: Modifier = Modifier,
    content: @Composable (ReaderRoute) -> Unit
) {
    val reducedMotion = MotionController.reducedFrom(null)
    val durationMs = if (reducedMotion) 0 else ReaderMotionTokens.DurationBase.inWholeMilliseconds.toInt()

    // 注册 Motion ID
    LaunchedEffect(targetRoute, transitionDirection) {
        val motionId = when (transitionDirection) {
            RouteTransitionDirection.PUSH_FORWARD -> MotionIdConstants.APP_ROUTE_PUSH_FORWARD
            RouteTransitionDirection.POP_BACKWARD -> MotionIdConstants.APP_ROUTE_POP_BACKWARD
            RouteTransitionDirection.REPLACE -> MotionIdConstants.APP_ROUTE_REPLACE
        }
        MotionController.start(
            motionId = motionId,
            from = "route.current",
            to = when (transitionDirection) {
                RouteTransitionDirection.PUSH_FORWARD -> "route.targetOnStack"
                RouteTransitionDirection.POP_BACKWARD -> "route.previousOnStack"
                RouteTransitionDirection.REPLACE -> "route.replacedTarget"
            },
            durationMs = durationMs.toLong(),
            reducedMotion = reducedMotion
        )
    }

    // slide 用 IntOffset spec（位移），fade 用 Float spec（alpha）
    val slideSpec = tween<IntOffset>(durationMs)
    val fadeSpec = tween<Float>(durationMs)

    AnimatedContent(
        targetState = targetRoute,
        modifier = modifier,
        transitionSpec = {
            when (transitionDirection) {
                RouteTransitionDirection.PUSH_FORWARD -> {
                    // 新路由从右侧滑入
                    (slideInHorizontally(slideSpec, initialOffsetX = { it / 8 }) + fadeIn(fadeSpec)) togetherWith
                        (slideOutHorizontally(slideSpec, targetOffsetX = { -it / 8 }) + fadeOut(fadeSpec))
                }
                RouteTransitionDirection.POP_BACKWARD -> {
                    // 旧路由向右滑出
                    (slideInHorizontally(slideSpec, initialOffsetX = { -it / 8 }) + fadeIn(fadeSpec)) togetherWith
                        (slideOutHorizontally(slideSpec, targetOffsetX = { it / 8 }) + fadeOut(fadeSpec))
                }
                RouteTransitionDirection.REPLACE -> {
                    fadeIn(fadeSpec) togetherWith fadeOut(fadeSpec)
                }
            }
        },
        label = "route-transition"
    ) { route ->
        content(route)
    }
}

enum class RouteTransitionDirection {
    PUSH_FORWARD,   // 路由入栈
    POP_BACKWARD,   // 路由出栈
    REPLACE         // 路由替换
}

/**
 * 根据路由变化推断转场方向
 * 栈深度增加 = PUSH_FORWARD，栈深度减少 = POP_BACKWARD，栈深度不变 = REPLACE
 */
fun inferTransitionDirection(
    oldStack: List<ReaderRoute>,
    newStack: List<ReaderRoute>
): RouteTransitionDirection {
    return when {
        newStack.size > oldStack.size -> RouteTransitionDirection.PUSH_FORWARD
        newStack.size < oldStack.size -> RouteTransitionDirection.POP_BACKWARD
        else -> RouteTransitionDirection.REPLACE
    }
}
