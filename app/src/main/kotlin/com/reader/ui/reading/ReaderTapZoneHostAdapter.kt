package com.reader.ui.reading

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.IntSize
import com.reader.ui.shell.ReaderBookOpenDomainState
import com.reader.ui.shell.ReaderPlaybackDomainState

/** Exact generated TapZones geometry and stable native region identities. */
internal object ReaderTapZoneContract {
    const val PreviousRatio = 0.26f
    const val ControlRatio = 0.48f
    const val NextRatio = 0.26f
    const val PreviousTag = "reader-tap-zone-previous"
    const val ControlTag = "reader-tap-zone-control"
    const val NextTag = "reader-tap-zone-next"
}

/** Host-layout truth for the currently visible page inside one chapter. */
internal data class ReaderTapZoneLayoutBoundary(
    val hasPreviousPage: Boolean,
    val hasNextPage: Boolean
)

/**
 * Final interaction state consumed by Compose. It is a projection, not a
 * reducer or a second reading state store.
 */
internal data class ReaderTapZoneHostState(
    val enabled: Boolean,
    val previousEnabled: Boolean,
    val controlEnabled: Boolean,
    val nextEnabled: Boolean
) {
    companion object {
        val Disabled = ReaderTapZoneHostState(
            enabled = false,
            previousEnabled = false,
            controlEnabled = false,
            nextEnabled = false
        )
    }
}

internal data class ReaderTapZoneCallbacks(
    val onPrevious: (() -> Unit)? = null,
    val onControl: (() -> Unit)? = null,
    val onControlLongPress: (() -> Unit)? = null,
    val onNext: (() -> Unit)? = null
)

/**
 * Narrow host adapter. Loading/readiness comes from the reader domain; page
 * edges come from measured Compose layout. Canonical fixture booleans are
 * intentionally not accepted as input.
 */
internal object ReaderTapZoneHostAdapter {
    fun fromSnapshot(
        loading: Boolean,
        contentReady: Boolean,
        layoutBoundary: ReaderTapZoneLayoutBoundary?
    ): ReaderTapZoneHostState {
        if (loading || !contentReady) return ReaderTapZoneHostState.Disabled
        // The current page-turn consumer measures and commits within the active
        // chapter only. Do not enable a chapter-edge tap from TOC presence until
        // that consumer can atomically cross chapters.
        return ReaderTapZoneHostState(
            enabled = true,
            previousEnabled = layoutBoundary?.hasPreviousPage == true,
            controlEnabled = true,
            nextEnabled = layoutBoundary?.hasNextPage == true
        )
    }

    fun fromPilot(
        bookOpen: ReaderBookOpenDomainState,
        playback: ReaderPlaybackDomainState,
        layoutBoundary: ReaderTapZoneLayoutBoundary?
    ): ReaderTapZoneHostState {
        val contentReady = bookOpen.contentLoaded &&
            bookOpen.locationResolved &&
            bookOpen.error == null &&
            playback.error == null
        return fromSnapshot(
            loading = bookOpen.loading,
            contentReady = contentReady,
            layoutBoundary = layoutBoundary
        )
    }

    fun fromLegacy(
        readingState: ReadingUiState,
        layoutBoundary: ReaderTapZoneLayoutBoundary?
    ): ReaderTapZoneHostState {
        if (readingState !is ReadingUiState.Ready) return ReaderTapZoneHostState.Disabled
        return fromSnapshot(
            loading = false,
            contentReady = true,
            layoutBoundary = layoutBoundary
        )
    }
}

/** Resolve exact within-chapter page edges from the same measured geometry used by pagination. */
internal fun readerTapZoneLayoutBoundary(
    currentLineTopPx: Float,
    textHeightPx: Int,
    viewportHeightPx: Int
): ReaderTapZoneLayoutBoundary? {
    if (
        !currentLineTopPx.isFinite() || currentLineTopPx < 0f ||
        textHeightPx <= 0 || viewportHeightPx <= 0
    ) return null
    return ReaderTapZoneLayoutBoundary(
        hasPreviousPage = currentLineTopPx > 0f,
        hasNextPage = currentLineTopPx + viewportHeightPx < textHeightPx.toFloat()
    )
}

/** Single measured-layout entry used by both production reader surfaces. */
internal fun measuredReaderTapZoneBoundary(
    content: String,
    committedOffset: Long,
    textLayout: TextLayoutResult?,
    viewport: IntSize?
): ReaderTapZoneLayoutBoundary? {
    val layout = textLayout ?: return null
    val size = viewport ?: return null
    if (content.isEmpty() || layout.lineCount <= 0) return null
    val safeOffset = committedOffset
        .coerceIn(0L, content.length.toLong())
        .toInt()
        .coerceAtMost(content.lastIndex)
    return readerTapZoneLayoutBoundary(
        currentLineTopPx = layout.getLineTop(layout.getLineForOffset(safeOffset)),
        textHeightPx = layout.size.height,
        viewportHeightPx = size.height
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ReaderTapZoneHost(
    state: ReaderTapZoneHostState,
    callbacks: ReaderTapZoneCallbacks,
    modifier: Modifier = Modifier
) {
    val effectiveState = state.withAvailableCallbacks(callbacks)
    Row(modifier = modifier) {
        Box(
            modifier = Modifier
                .weight(ReaderTapZoneContract.PreviousRatio)
                .fillMaxHeight()
                .testTag(ReaderTapZoneContract.PreviousTag)
                .semantics {
                    contentDescription = "上一页"
                    role = Role.Button
                    if (!effectiveState.previousEnabled) disabled()
                }
                .clickable(enabled = effectiveState.previousEnabled) { callbacks.onPrevious?.invoke() }
        )
        Box(
            modifier = Modifier
                .weight(ReaderTapZoneContract.ControlRatio)
                .fillMaxHeight()
                .testTag(ReaderTapZoneContract.ControlTag)
                .semantics {
                    contentDescription = "打开阅读控制层"
                    role = Role.Button
                    if (!effectiveState.controlEnabled) disabled()
                }
                .combinedClickable(
                    enabled = effectiveState.controlEnabled,
                    onClick = { callbacks.onControl?.invoke() },
                    onLongClick = callbacks.onControlLongPress
                )
        )
        Box(
            modifier = Modifier
                .weight(ReaderTapZoneContract.NextRatio)
                .fillMaxHeight()
                .testTag(ReaderTapZoneContract.NextTag)
                .semantics {
                    contentDescription = "下一页"
                    role = Role.Button
                    if (!effectiveState.nextEnabled) disabled()
                }
                .clickable(enabled = effectiveState.nextEnabled) { callbacks.onNext?.invoke() }
        )
    }
}

/** Missing Host consumers always fail closed, including rollback builds. */
internal fun ReaderTapZoneHostState.withAvailableCallbacks(
    callbacks: ReaderTapZoneCallbacks
): ReaderTapZoneHostState = ReaderTapZoneHostState(
    enabled = enabled,
    previousEnabled = enabled && previousEnabled && callbacks.onPrevious != null,
    controlEnabled = enabled && controlEnabled && callbacks.onControl != null,
    nextEnabled = enabled && nextEnabled && callbacks.onNext != null
)
