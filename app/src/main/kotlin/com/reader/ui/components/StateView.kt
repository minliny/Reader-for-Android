package com.reader.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reader.ui.motion.MotionController

// TODO(ReaderTypography): 待主题层补齐 ReaderTypography（appTitle / sectionTitle）后替换下方硬编码 TextStyle
private val StateViewAppTitleStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = 19.sp,
    fontWeight = FontWeight(700)
)

private val StateViewSectionTitleStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = 15.sp,
    fontWeight = FontWeight(500)
)

// TODO(ReaderMotionTokens): 待 MotionTokens 补充 DurationLoadingSpin=800ms 后替换硬编码
private const val LOADING_SPIN_DURATION_MS = 800

/**
 * 应用基础设施层：结构化状态视图
 *
 * 四态：loading / error / empty / success
 * 契约：MOTION_CONTRACT.md 行 173-176 state.empty/error/success.enter
 */
sealed class StateViewData<out T> {
    object Loading : StateViewData<Nothing>()
    data class Error(val message: String, val retry: (() -> Unit)? = null) : StateViewData<Nothing>()
    data class Empty(
        val title: String,
        val message: String,
        val illustration: (@Composable () -> Unit)? = null,
        val action: (() -> Unit)? = null
    ) : StateViewData<Nothing>()
    data class Success<T>(val data: T) : StateViewData<T>()
}

/**
 * 结构化状态视图入口。
 *
 * 根据 [state] 分发到 loading / error / empty / success 四态。
 * reducedMotion 通过 [MotionController.reducedFrom] 解析（等价 ReducedMotionResolver.isReducedMotion()，
 * 但 ReducedMotionResolver 是 fun interface，需通过 MotionController 注入实例后调用）。
 */
@Composable
fun <T> StateView(
    state: StateViewData<T>,
    modifier: Modifier = Modifier,
    successContent: @Composable (T) -> Unit
) {
    val reducedMotion = MotionController.reducedFrom(null)

    when (state) {
        is StateViewData.Loading -> LoadingState(modifier, reducedMotion)
        is StateViewData.Error -> ErrorState(state, modifier, reducedMotion)
        is StateViewData.Empty -> EmptyState(state, modifier, reducedMotion)
        is StateViewData.Success -> successContent(state.data)
    }
}

/**
 * Loading 态：800ms 旋转 spinner（对应 fd-reader-loading-spin）。
 * reducedMotion 时替换为三个静态灰点。
 */
@Composable
private fun LoadingState(modifier: Modifier, reducedMotion: Boolean) {
    // 注册 state.loading 动效（fd-reader-loading-spin 800ms）
    LaunchedEffect(Unit) {
        MotionController.start(
            motionId = "state.loading",
            from = "idle",
            to = "loading",
            durationMs = MotionController.durationFor(
                LOADING_SPIN_DURATION_MS.toLong(),
                reducedMotion
            ),
            reducedMotion = reducedMotion
        )
    }

    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (reducedMotion) {
            // reduced motion：替换为静态状态点（三个点）
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(3) {
                    Box(
                        Modifier
                            .size(6.dp)
                            .background(Color.Gray, CircleShape)
                    )
                }
            }
        } else {
            // 800ms 旋转 spinner（对应 fd-reader-loading-spin）
            val infiniteTransition = rememberInfiniteTransition(label = "loading-spin")
            val rotation by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(LOADING_SPIN_DURATION_MS, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "rotation"
            )
            CircularProgressIndicator(
                modifier = Modifier.graphicsLayer { rotationZ = rotation },
                strokeWidth = 2.dp
            )
        }
    }
}

/**
 * Error 态：state.error.enter 契约——错误状态进入当前容器；重试按钮使用 button.*。
 */
@Composable
private fun ErrorState(state: StateViewData.Error, modifier: Modifier, reducedMotion: Boolean) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "加载失败",
            style = StateViewAppTitleStyle
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = state.message,
            style = StateViewSectionTitleStyle
        )
        state.retry?.let { retryAction ->
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = retryAction) {
                Text("重试")
            }
        }
    }
}

/**
 * Empty 态：state.empty.enter 契约——空状态插入当前内容容器，不替换整个页面 Shell。
 */
@Composable
private fun EmptyState(state: StateViewData.Empty, modifier: Modifier, reducedMotion: Boolean) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        state.illustration?.invoke()
        Spacer(Modifier.height(16.dp))
        Text(
            text = state.title,
            style = StateViewAppTitleStyle
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = state.message,
            style = StateViewSectionTitleStyle
        )
        state.action?.let { action ->
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = action) {
                Text("开始")
            }
        }
    }
}
