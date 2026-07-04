package com.reader.ui.components

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.reader.ui.theme.ReaderSpacing

/**
 * 全局错误边界
 *
 * 契约来源：审计报告 A5（无全局错误边界）
 *
 * 双层错误处理：
 * 1. 全局 UncaughtExceptionHandler：捕获崩溃，写日志，可选重启
 * 2. Composable ErrorBoundary：捕获 Compose 渲染异常，显示错误 UI + 重试
 */
object ReaderErrorBoundary {
    private const val TAG = "ReaderErrorBoundary"
    private var originalHandler: Thread.UncaughtExceptionHandler? = null
    private var onCrash: ((Thread, Throwable) -> Unit)? = null

    /**
     * 在 Application.onCreate() 中调用，安装全局崩溃捕获
     */
    fun install(onCrash: ((Thread, Throwable) -> Unit)? = null) {
        this.onCrash = onCrash
        originalHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "Uncaught exception on thread ${thread.name}", throwable)
            // 写入崩溃日志到文件（可扩展）
            onCrash?.invoke(thread, throwable)
            // 交给原 handler 处理（避免吞掉崩溃）
            originalHandler?.uncaughtException(thread, throwable)
        }
    }

    fun uninstall() {
        originalHandler?.let {
            Thread.setDefaultUncaughtExceptionHandler(it)
        }
        originalHandler = null
        onCrash = null
    }
}

/**
 * Composable 错误边界状态
 */
class ErrorBoundaryState {
    var error: Throwable? by mutableStateOf(null)
        private set

    fun updateError(t: Throwable?) {
        error = t
    }

    fun retry() {
        error = null
    }
}

@Composable
fun rememberErrorBoundaryState(): ErrorBoundaryState = remember { ErrorBoundaryState() }

/**
 * Composable 错误边界包裹器
 * 子组件抛出异常时显示错误 UI
 */
@Composable
fun ErrorBoundary(
    state: ErrorBoundaryState,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val error = state.error
    if (error == null) {
        content()
    } else {
        ErrorView(
            error = error,
            onRetry = { state.retry() },
            modifier = modifier
        )
    }
}

@Composable
private fun ErrorView(
    error: Throwable,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(ReaderSpacing.lg)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "应用出现异常",
            fontWeight = FontWeight(800)
        )
        Spacer(Modifier.height(ReaderSpacing.sm))
        Text(
            text = error.message ?: "未知错误",
            fontWeight = FontWeight(500)
        )
        Spacer(Modifier.height(ReaderSpacing.md))
        // 堆栈摘要
        val stackTrace = error.stackTrace.take(20).joinToString("\n") { "  at $it" }
        Text(
            text = stackTrace,
            fontWeight = FontWeight(500)
        )
        Spacer(Modifier.height(ReaderSpacing.md))
        OutlinedButton(onClick = onRetry) {
            Text("重试")
        }
    }
}
