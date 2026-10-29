package io.reader.android.ui.ebookdraft

import android.content.Context
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import io.reader.android.runtime.ebookdraft.EbookLeaseGuard
import io.reader.android.ui.ReaderPageState
import io.reader.android.ui.background

/** New prepared capability for each physical WebView, including Activity recreation. No VM field is assumed. */
@Composable
internal fun ReaderEbookBody(input: ReaderEbookRenderInput, guard: EbookLeaseGuard,
    onReady: (ReaderEbookReady) -> Unit, onVisibleAnchor: (ReaderEbookVisibleAnchor) -> Boolean,
    onFailure: (ReaderEbookRenderFailure) -> Unit, onBodyTap: (ReaderEbookBodyTap) -> Unit,
    onSelectionChanged: (ReaderEbookSelectionState) -> Unit, modifier: Modifier = Modifier) {
    key(input.prepared.lease.renderId) {
        var phase by remember { mutableStateOf(ReaderEbookPhase.Restoring) }
        Box(modifier.testTag("reader_ebook_body")) {
            AndroidView(factory = { context -> ReaderEbookHostView(context, input, guard, { phase = it }, onReady, onVisibleAnchor, onFailure, onBodyTap, onSelectionChanged) },
                modifier = Modifier.fillMaxSize(), onReset = null, onRelease = { it.release() }, update = { it.bind(input) })
            if (phase != ReaderEbookPhase.Ready) Surface(color = input.paper.background(), modifier = Modifier.fillMaxSize().testTag("reader_ebook_restore_loading")) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (phase == ReaderEbookPhase.Failed) ReaderPageState("暂时无法显示原文") else CircularProgressIndicator()
                }
            }
        }
    }
}

private class ReaderEbookHostView(context: Context, initial: ReaderEbookRenderInput, guard: EbookLeaseGuard,
    phase: (ReaderEbookPhase) -> Unit, ready: (ReaderEbookReady) -> Unit, visible: (ReaderEbookVisibleAnchor) -> Boolean,
    failure: (ReaderEbookRenderFailure) -> Unit, bodyTap: (ReaderEbookBodyTap) -> Unit,
    selectionChanged: (ReaderEbookSelectionState) -> Unit) : FrameLayout(context) {
    private var owner: ReaderEbookWebViewOwner? = null
    init {
        try {
            val created = ReaderEbookWebViewOwner(context, initial, guard, phase, ready, visible, failure, bodyTap, selectionChanged)
            owner = created; created.start(); addView(created.view, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        } catch (error: Exception) {
            android.util.Log.w("ReaderEbook", "stage=webview-start renderId=${initial.prepared.lease.renderId} epoch=0", error)
            owner?.close(); owner = null; initial.prepared.retire(); phase(ReaderEbookPhase.Failed)
            failure(ReaderEbookRenderFailure(initial.prepared.lease, 0, null, "webview-start", error))
        }
    }
    fun bind(input: ReaderEbookRenderInput) { owner?.bind(input) }
    fun release() { owner?.close(); owner = null; removeAllViews() }
}
