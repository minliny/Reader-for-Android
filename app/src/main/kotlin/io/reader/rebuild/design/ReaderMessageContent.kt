package io.reader.rebuild.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.reader.core.theme.ReaderDesignMetrics

internal sealed interface PageState {
    data object Loading : PageState
    data class Empty(val message: String) : PageState
    data class Failure(val message: String, val actionGeneration: Long?) : PageState
}

@Composable
internal fun ReaderPageState(
    state: PageState,
    modifier: Modifier = Modifier,
    onRetry: (expectedGeneration: Long) -> Unit = {},
    retainedContent: (@Composable () -> Unit)? = null,
) {
    if (retainedContent != null) {
        Box(modifier) { retainedContent() }
        return
    }
    val current by rememberUpdatedState(state)
    Column(
        modifier.fillMaxWidth().padding(ReaderDesignMetrics.screenInset.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ReaderDesignMetrics.spaceExtraSmall.dp),
    ) {
        when (state) {
            PageState.Loading -> {
                CircularProgressIndicator()
                Text("正在载入", style = readerTextStyle(ReaderDesignMetrics.rowDetail))
            }
            is PageState.Empty -> Text(state.message, style = readerTextStyle(ReaderDesignMetrics.rowDetail))
            is PageState.Failure -> {
                Text(state.message, style = readerTextStyle(ReaderDesignMetrics.rowDetail))
                state.actionGeneration?.let { generation ->
                    TextButton(onClick = {
                        if ((current as? PageState.Failure)?.actionGeneration == generation) onRetry(generation)
                    }) { Text("重试", style = readerTextStyle(ReaderDesignMetrics.rowLabel)) }
                }
            }
        }
    }
}

internal data class ReaderConfirmationState(
    val title: String,
    val message: String,
    val confirmTitle: String,
    val generation: Long,
)

@Composable
internal fun ReaderConfirmation(
    state: ReaderConfirmationState?,
    ownerForeground: Boolean,
    onDismiss: (Long) -> Unit,
    onConfirm: (Long) -> Unit,
) {
    val active = readerFeedbackEnabled(ownerForeground)
    val current by rememberUpdatedState(state?.takeIf { active })
    val shown = current ?: return
    AlertDialog(
        onDismissRequest = { if (current?.generation == shown.generation) onDismiss(shown.generation) },
        title = { Text(shown.title, style = readerTextStyle(ReaderDesignMetrics.pageTitle)) },
        text = { Text(shown.message, style = readerTextStyle(ReaderDesignMetrics.rowDetail)) },
        dismissButton = {
            TextButton(onClick = { if (current?.generation == shown.generation) onDismiss(shown.generation) }) {
                Text("取消", style = readerTextStyle(ReaderDesignMetrics.rowLabel))
            }
        },
        confirmButton = {
            TextButton(onClick = { if (current?.generation == shown.generation) onConfirm(shown.generation) }) {
                Text(shown.confirmTitle, style = readerTextStyle(ReaderDesignMetrics.rowLabel))
            }
        },
    )
}

internal data class ReaderTaskProgressState(
    val title: String,
    val completed: Int,
    val failed: Int,
    val inProgress: Int,
    val unexecuted: Int,
) {
    init { require(minOf(completed, failed, inProgress, unexecuted) >= 0) }
    val total: Long get() = completed.toLong() + failed + inProgress + unexecuted
}

@Composable
internal fun ReaderTaskProgress(state: ReaderTaskProgressState, modifier: Modifier = Modifier) {
    val colors = LocalReaderColors.current
    val fraction = if (state.total == 0L) 0f else
        ((state.completed.toLong() + state.failed).toDouble() / state.total).toFloat()
    Surface(modifier, shape = RoundedCornerShape(ReaderDesignMetrics.cardRadius.dp), color = colors.messageSurface,
        contentColor = colors.messageText) {
        Column(
            Modifier.padding(ReaderDesignMetrics.cardPadding.dp),
            verticalArrangement = Arrangement.spacedBy(ReaderDesignMetrics.rowBlockSpacing.dp),
        ) {
            Text(state.title, style = readerTextStyle(ReaderDesignMetrics.rowLabel))
            Row(Modifier.fillMaxWidth().height(ReaderDesignMetrics.shelfProgressStroke.dp)
                .clip(RoundedCornerShape(ReaderDesignMetrics.pillRadius.dp))
                .background(colors.progressTrack).semantics { progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f) }) {
                if (state.completed > 0) Box(Modifier.weight(state.completed.toFloat()).fillMaxHeight().background(colors.success))
                if (state.failed > 0) Box(Modifier.weight(state.failed.toFloat()).fillMaxHeight().background(colors.danger))
                if (state.inProgress > 0) Box(Modifier.weight(state.inProgress.toFloat()).fillMaxHeight().background(colors.accentTint))
                if (state.unexecuted > 0) Box(Modifier.weight(state.unexecuted.toFloat()).fillMaxHeight())
            }
            Text("已完成 ${state.completed} · 失败 ${state.failed} · 进行中 ${state.inProgress} · 未执行 ${state.unexecuted}",
                style = readerTextStyle(ReaderDesignMetrics.rowDetail))
        }
    }
}
