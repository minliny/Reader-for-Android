package com.reader.ui.components

import android.os.Build
import android.view.Choreographer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.reader.ui.theme.readerExtraColors
import com.reader.ui.tokens.ReaderTokenAdapter
import com.reader.ui.tokens.ReaderZIndexToken

/**
 * 帧率监控
 *
 * 契约来源：审计报告 A7（无帧率监控）+ MOTION_IMPLEMENTATION_GAP_AUDIT.md 行 55
 *
 * 用 Choreographer 回调测量帧间隔，计算 FPS。
 * debug 模式下显示 overlay，release 模式不显示。
 */
class FpsMonitor {
    private var frameCount = 0
    private var lastTimeMs = 0L
    private var callback: Choreographer.FrameCallback? = null
    private var fps: Float = 0f
    private var listeners = mutableListOf<(Float) -> Unit>()

    val currentFps: Float get() = fps

    fun start() {
        if (callback != null) return
        lastTimeMs = System.currentTimeMillis()
        frameCount = 0
        callback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                frameCount++
                val now = System.currentTimeMillis()
                val elapsed = now - lastTimeMs
                if (elapsed >= 500) {  // 每 500ms 计算一次
                    fps = frameCount * 1000f / elapsed
                    listeners.forEach { it(fps) }
                    frameCount = 0
                    lastTimeMs = now
                }
                Choreographer.getInstance().postFrameCallback(this)
            }
        }
        Choreographer.getInstance().postFrameCallback(callback!!)
    }

    fun stop() {
        callback?.let { Choreographer.getInstance().removeFrameCallback(it) }
        callback = null
    }

    fun addListener(listener: (Float) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (Float) -> Unit) {
        listeners.remove(listener)
    }
}

/**
 * 单例 FpsMonitor 实例
 */
object FpsMonitorSingleton {
    val instance = FpsMonitor()
}

/**
 * Debug 模式 FPS overlay
 * 在 AppShell 顶层调用，仅在 BuildConfig.DEBUG 时显示
 */
@Composable
fun FpsOverlay(
    modifier: Modifier = Modifier
) {
    // 只在 debug 模式显示
    if (!com.reader.android.BuildConfig.DEBUG) return

    var fps by remember { mutableStateOf(0f) }

    DisposableEffect(Unit) {
        val listener: (Float) -> Unit = { value -> fps = value }
        FpsMonitorSingleton.instance.addListener(listener)
        FpsMonitorSingleton.instance.start()
        onDispose {
            FpsMonitorSingleton.instance.removeListener(listener)
            // 不在 onDispose stop，让 monitor 持续运行
        }
    }

    val extra = readerExtraColors()
    // FPS 状态色映射到语义 token：good→forest / warn→accent / bad→danger
    val color = when {
        fps >= 55 -> extra.forest   // 绿色
        fps >= 30 -> extra.accent   // 黄色
        else -> extra.danger        // 红色
    }

    Box(
        modifier = modifier
            .zIndex(ReaderTokenAdapter.zIndex(ReaderZIndexToken.OVERLAY))
            .background(color, RoundedCornerShape(4.dp))
            .padding(4.dp)
    ) {
        Text(
            text = "%.0f FPS".format(fps),
            color = Color.White,
            fontWeight = FontWeight(700)
        )
    }
}
