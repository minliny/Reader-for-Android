package io.reader.android.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.reader.android.runtime.ReaderChapter
import io.reader.android.runtime.ReaderEbookViewLease
import io.reader.android.runtime.ReaderEbookViewPreparation
import io.reader.android.ui.ebookdraft.ReaderEbookBody
import io.reader.android.ui.ebookdraft.ReaderEbookGeometry
import io.reader.android.ui.ebookdraft.ReaderEbookReady
import io.reader.android.ui.ebookdraft.ReaderEbookRenderInput
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** VM-owned current mount identity; no path, Uri, derived format or independent position store. */
internal class ReaderEbookUiMount(val readingOwner: Long, val chapter: ReaderChapter, val view: ReaderEbookViewLease) {
    var preparation: ReaderEbookViewPreparation? = null
    var ready: ReaderEbookReady? = null
    var selectionActive = false
}

/** A committed physical mount mints once. It never waits for a WebView callback while holding a Library turn. */
@Composable
internal fun ReaderEbookReadingBody(state: ReaderUiState, model: ReaderViewModel, modifier: Modifier = Modifier) {
    val reading = state.reading ?: return
    val chapter = reading.chapter
    val scope = rememberCoroutineScope()
    var prepared by remember(reading.identity, chapter.sessionToken) { mutableStateOf<ReaderEbookViewPreparation?>(null) }
    var preparationFailed by remember(reading.identity, chapter.sessionToken) { mutableStateOf(false) }
    DisposableEffect(model, reading.identity, chapter.sessionToken) {
        var view: ReaderEbookViewLease? = null
        val job = scope.launch {
            try {
                val admitted = model.attachEbookView(reading.identity, chapter.sessionToken)
                view = admitted
                prepared = model.prepareEbookView(reading.identity, chapter.sessionToken, admitted)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                preparationFailed = true
                model.ebookPreparationFailed(reading.identity, chapter.sessionToken, view, error)
            }
        }
        onDispose {
            // Retire the exact token before cancelling its UI observer. Already-admitted Host work retains its owner.
            view?.let(model::releaseEbookView)
            job.cancel()
        }
    }
    val publication = prepared
    if (publication == null) {
        Box(modifier.testTag("reader_ebook_prepare_loading"), contentAlignment = Alignment.Center) {
            if (preparationFailed) ReaderPageState("暂时无法显示原文") else CircularProgressIndicator()
        }
    } else {
        val density = LocalDensity.current
        val geometry = with(density) { ReaderEbookGeometry(22.dp.roundToPx(), 32.dp.roundToPx(), 22.dp.roundToPx(),
            52.dp.roundToPx(), this.density, fontScale) }
        ReaderEbookBody(
            input = ReaderEbookRenderInput(prepared = publication.publication, chapter = chapter,
                currentAnchor = reading.visibleScalarOffset, paper = state.paper, fontSizeSp = state.fontSize,
                geometry = geometry, userScrollEnabled = !state.controlsVisible && !state.busy),
            guard = publication.guard,
            onReady = { model.ebookReady(publication.view, it) },
            onVisibleAnchor = { model.ebookVisibleAnchor(publication.view, it) },
            onFailure = { model.ebookRenderFailed(publication.view, it) },
            onBodyTap = { model.ebookBodyTap(publication.view, it) },
            onSelectionChanged = { model.ebookSelectionChanged(publication.view, it) },
            modifier = modifier.fillMaxSize(),
        )
    }
}
