package io.reader.rebuild.features.reading

import android.webkit.WebView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import io.reader.rebuild.core.readerResult
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement

@Composable
internal fun ReadingSurfaceContent(
    chapter: ReaderChapter,
    appearance: ReadingAppearance,
    fallback: Boolean,
    onPosition: (Long) -> Unit,
    onReady: (ReadingSurface<*>?) -> Unit,
    onTap: () -> Unit,
    onBoundary: (Int) -> Unit,
    onFallback: () -> Unit,
) {
    val position by rememberUpdatedState(onPosition)
    val ready by rememberUpdatedState(onReady)
    val tap by rememberUpdatedState(onTap)
    val boundary by rememberUpdatedState(onBoundary)
    val fail by rememberUpdatedState(onFallback)
    val ebook = chapter.ebook
    if (ebook != null && !fallback) {
        var adapter by remember(ebook) { mutableStateOf<EbookWebViewAdapter?>(null) }
        var loaded by remember(ebook) { mutableStateOf(false) }
        val style by rememberUpdatedState(appearance.style)
        AndroidView(
            factory = { context ->
                val web = WebView(context)
                readerResult {
                    EbookWebViewAdapter(web) { event ->
                        when (event.kind) {
                            "ready" -> {
                                loaded = true
                                ready(adapter)
                                adapter?.currentPosition { if (it != null) position(it.scalar) }
                            }
                            "tap" -> tap()
                            "position" -> readerResult { Json.decodeFromJsonElement<ReadingPosition>(event.value) }
                                .onSuccess { position(it.scalar) }
                            "failure" -> fail()
                        }
                    }.also {
                        adapter = it
                        it.open(ebook, chapter.initialScalar, style)
                    }
                }.onFailure { web.post { fail() } }
                web
            },
            modifier = Modifier.fillMaxSize(),
            update = {},
            onRelease = { web ->
                ready(null)
                if (adapter != null) adapter?.destroy() else web.destroy()
                adapter = null
            },
        )
        LaunchedEffect(adapter, appearance) { adapter?.reflow(appearance.style, null) }
        LaunchedEffect(ebook) { delay(8_000); if (!loaded) fail() }
    } else {
        val document = remember(chapter.text) { TextSurfaceChapter(chapter.text) }
        AndroidView(
            factory = { context ->
                StaticLayoutReadingSurface(context).also { view ->
                    view.onAnchor = { position(it) }
                    view.onSurfaceTap = { tap() }
                    view.onPageBoundary = { boundary(it) }
                    view.scrolling = appearance.scrolling
                    view.open(document, chapter.initialScalar, appearance.style)
                    ready(view)
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = {
                it.scrolling = appearance.scrolling
                it.reflow(appearance.style, null)
            },
            onRelease = { it.retire(); ready(null) },
        )
    }
}
