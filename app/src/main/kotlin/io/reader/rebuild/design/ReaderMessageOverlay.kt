package io.reader.rebuild.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.reader.core.theme.ReaderDesignMetrics
import kotlinx.coroutines.delay

internal data class RecoveryAction(val title: String, val generation: Long)
internal data class RecoveryNoticeState(
    val operationId: String,
    val title: String,
    val detail: String,
    val generation: Long,
    val action: RecoveryAction?,
)

@Composable
internal fun readerFeedbackEnabled(ownerForeground: Boolean): Boolean {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val state by lifecycle.currentStateFlow.collectAsState()
    return ownerForeground && state.isAtLeast(Lifecycle.State.RESUMED)
}

/** Overlay children are measured against the already laid-out page. */
@Composable
internal fun ReaderMessageOverlay(
    ownerForeground: Boolean,
    toast: TransientToastState,
    recovery: RecoveryNoticeState?,
    onVerify: (operationId: String, generation: Long, actionGeneration: Long) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val active = readerFeedbackEnabled(ownerForeground)
    val message by toast.message.collectAsState()
    val currentNotice by rememberUpdatedState(if (active) recovery else null)
    DisposableEffect(toast) { onDispose { toast.hide() } }
    LaunchedEffect(active, message, toast) {
        if (!active) toast.hide()
        else message?.let {
            delay(toast.remaining(it))
            toast.expire(it)
        }
    }
    Box(modifier) {
        content()
        if (active) {
            BoxWithConstraints(
                Modifier.matchParentSize().windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                ),
            ) {
                val notice = recovery
                if (notice != null) {
                    RecoveryNotice(
                        notice,
                        Modifier.align(Alignment.TopCenter).fillMaxWidth()
                            .padding(ReaderDesignMetrics.spaceSmall.dp).heightIn(max = maxHeight / 2),
                    ) { action ->
                        val latest = currentNotice
                        if (latest?.operationId == notice.operationId && latest.generation == notice.generation &&
                            latest.action?.generation == action.generation
                        ) {
                            onVerify(notice.operationId, notice.generation, action.generation)
                        }
                    }
                }
                message?.let {
                    TransientToast(
                        it.text,
                        Modifier.align(Alignment.BottomCenter).windowInsetsPadding(
                            WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom),
                        ).padding(ReaderDesignMetrics.spaceMedium.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun TransientToast(text: String, modifier: Modifier) {
    val colors = LocalReaderColors.current
    Surface(
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
        color = colors.messageSurface, contentColor = colors.messageText,
        border = BorderStroke(1.dp, colors.messageBorder), shadowElevation = 4.dp,
        shape = RoundedCornerShape(ReaderDesignMetrics.pillRadius.dp),
    ) {
        Text(text, Modifier.padding(horizontal = ReaderDesignMetrics.spaceMedium.dp,
            vertical = ReaderDesignMetrics.spaceExtraSmall.dp), style = readerTextStyle(ReaderDesignMetrics.rowLabel))
    }
}

@Composable
private fun RecoveryNotice(
    notice: RecoveryNoticeState,
    modifier: Modifier,
    onAction: (RecoveryAction) -> Unit,
) {
    val colors = LocalReaderColors.current
    Surface(
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite }.pointerInput(Unit) {
            // This hit target shields the page behind it and leaves descendants' scroll and click gestures intact.
            awaitPointerEventScope {
                while (true) awaitPointerEvent()
            }
        },
        color = colors.noticeSurface, contentColor = colors.noticeText, shadowElevation = 4.dp,
        shape = RoundedCornerShape(ReaderDesignMetrics.cardRadius.dp),
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(ReaderDesignMetrics.cardPadding.dp)) {
            Text(notice.title, style = readerTextStyle(ReaderDesignMetrics.rowLabel))
            Text(notice.detail, style = readerTextStyle(ReaderDesignMetrics.rowDetail))
            notice.action?.let { action ->
                TextButton(onClick = { onAction(action) },
                    colors = ButtonDefaults.textButtonColors(contentColor = colors.noticeText)) {
                    Text(action.title, style = readerTextStyle(ReaderDesignMetrics.rowLabel))
                }
            }
        }
    }
}
