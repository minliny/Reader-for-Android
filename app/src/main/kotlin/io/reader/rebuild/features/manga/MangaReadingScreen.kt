package io.reader.rebuild.features.manga

import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import com.reader.core.protocol.M3MangaChapterWindow
import com.reader.core.protocol.M3MangaManifestPage
import com.reader.core.theme.ReaderIconRegistry
import io.reader.rebuild.core.readerResult
import io.reader.rebuild.design.LocalReaderColors
import io.reader.rebuild.design.ReaderIcon
import io.reader.rebuild.design.ReaderPageState
import io.reader.rebuild.design.PageState
import io.reader.rebuild.features.library.BookReference
import kotlinx.coroutines.launch

@Composable
internal fun MangaReadingScreen(
    book: BookReference,
    reader: MangaReadingCoordinator,
    onBack: () -> Unit,
    feedback: (String, String) -> Unit = { _, _ -> },
) {
    val colors = LocalReaderColors.current
    val scope = rememberCoroutineScope()
    var chapter by remember(book.key) { mutableStateOf<M3MangaChapterWindow?>(null) }
    var page by remember(book.key) { mutableStateOf<M3MangaManifestPage?>(null) }
    var frame by remember(book.key) { mutableStateOf<MangaPageFrame?>(null) }
    var failure by remember(book.key) { mutableStateOf<String?>(null) }
    var ordinal by remember(book.key) { mutableLongStateOf(0L) }
    var horizontal by remember(book.key) { mutableStateOf(false) }
    var controls by remember(book.key) { mutableStateOf(true) }

    fun load(target: Long = ordinal) {
        scope.launch {
            failure = null
            readerResult {
                val prepared = reader.open(book.sourceId, book.bookId, target)
                val window = reader.readWindow(prepared.targetOrdinal, 1)
                val selected = window.manifest.pages.single()
                frame?.close()
                frame = reader.page(window, selected, 720)
                chapter = window
                page = selected
                ordinal = selected.ordinal
            }.onFailure {
                failure = it.localizedMessage ?: "漫画页面加载失败"
                feedback("manga-load", failure ?: "漫画页面加载失败")
            }
        }
    }

    LaunchedEffect(book.key) { load() }
    DisposableEffect(reader) { onDispose { frame?.close() } }

    Box(Modifier.fillMaxSize().background(colors.background)) {
        val current = frame
        if (failure != null && current == null) {
            ReaderPageState(PageState.Failure(failure ?: "漫画页面加载失败", ordinal), Modifier.align(Alignment.Center),
                onRetry = { load(ordinal) })
        } else if (current?.content is MangaPageFrame.Content.Image) {
            val image = current.content as MangaPageFrame.Content.Image
            Canvas(Modifier.fillMaxSize().padding(top = 52.dp, bottom = if (controls) 92.dp else 12.dp)) {
                val width = size.width
                val height = size.height
                val ratio = image.tiles.aspectRatio.toFloat().coerceAtLeast(.05f)
                val destination = if (horizontal) {
                    val h = (width / ratio).coerceAtMost(height)
                    RectF(0f, (height - h) / 2f, width, (height + h) / 2f)
                } else {
                    val w = (height * ratio).coerceAtMost(width)
                    RectF((width - w) / 2f, 0f, (width + w) / 2f, height)
                }
                image.tiles.draw(drawContext.canvas.nativeCanvas, destination)
            }
        } else if (current?.content is MangaPageFrame.Content.Unavailable) {
            MangaUnavailable(onRetry = { load(ordinal) })
        }
        Surface(Modifier.fillMaxWidth().align(Alignment.TopCenter), color = colors.panel) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { ReaderIcon(ReaderIconRegistry.back, "退出漫画") }
                Text(book.title, Modifier.weight(1f), color = colors.primaryText)
                Text("${ordinal + 1}/${chapter?.totalPages ?: 0}", color = colors.secondaryText)
            }
        }
        if (controls) Surface(Modifier.fillMaxWidth().align(Alignment.BottomCenter), color = colors.panel) {
            Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { if (ordinal > 0) load(ordinal - 1) }, enabled = ordinal > 0) { Text("上一页") }
                Button(onClick = { load(ordinal + 1) }, enabled = ordinal + 1 < (chapter?.totalPages ?: 0)) { Text("下一页") }
                Button(onClick = { horizontal = !horizontal }) { Text(if (horizontal) "竖向" else "横向") }
                Button(onClick = {
                    val window = chapter ?: return@Button
                    val selected = page ?: return@Button
                    scope.launch { readerResult { reader.save(window, selected, .5, .5) }
                        .onSuccess { feedback("manga-save", "漫画位置已保存") }
                        .onFailure { feedback("manga-save", "漫画位置保存失败") } }
                }) { Text("保存") }
                Button(onClick = { controls = false }) { Text("收起") }
            }
        } else {
            Button(modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp), onClick = { controls = true }) { Text("控制") }
        }
    }
}

@Composable
private fun MangaUnavailable(onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Button(onClick = onRetry) { Text("原位重试") }
    }
}
