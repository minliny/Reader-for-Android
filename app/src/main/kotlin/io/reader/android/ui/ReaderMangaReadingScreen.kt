package io.reader.android.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
internal fun ReaderMangaReadingScreen(state: ReaderMangaReadingUiState, model: ReaderViewModel) {
    val listState = rememberLazyListState()
    LaunchedEffect(state.generation, state.pages) {
        if (state.pages.isNotEmpty()) listState.scrollToItem(0)
    }
    LaunchedEffect(listState.firstVisibleItemIndex, state.generation) {
        state.pages.getOrNull(listState.firstVisibleItemIndex)?.let { model.setMangaOrdinal(it.ordinal) }
    }
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing).testTag("reader_manga_reading"),
    ) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton({ model.closeMangaReading() }, modifier = Modifier.testTag("reader_manga_exit")) { Text("返回") }
                    Text("漫画阅读", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text("进度暂不写入", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (state.phase == ReaderMangaReadingPhase.READY) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("第 ${state.currentOrdinal + 1} 页 / ${state.window?.totalPages ?: state.pages.size}", modifier = Modifier.weight(1f))
                        TextButton({ model.setMangaZoom(state.zoom - 0.25f) }, modifier = Modifier.testTag("reader_manga_zoom_out")) { Text("缩小") }
                        Text("${(state.zoom * 100).toInt()}%")
                        TextButton({ model.setMangaZoom(state.zoom + 0.25f) }, modifier = Modifier.testTag("reader_manga_zoom_in")) { Text("放大") }
                    }
                }
            }
        }
        when (state.phase) {
            ReaderMangaReadingPhase.LOADING -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.testTag("reader_manga_loading"))
            }
            ReaderMangaReadingPhase.ERROR -> ReaderPageState(
                "漫画暂时不可用", state.error ?: "资源读取失败。", "重试", model::retryMangaReading,
            )
            ReaderMangaReadingPhase.READY -> LazyColumn(
                state = listState, modifier = Modifier.fillMaxSize().testTag("reader_manga_pages"),
            ) {
                items(state.pages, key = { it.ordinal }) { page ->
                    val image = remember(page.decoded.transferId, page.decoded.bytes) {
                        BitmapFactory.decodeByteArray(page.decoded.bytes, 0, page.decoded.bytes.size)?.asImageBitmap()
                    }
                    if (image == null) {
                        Text("第 ${page.ordinal + 1} 页无法显示", modifier = Modifier.padding(24.dp))
                    } else {
                        Image(
                            image, "第 ${page.ordinal + 1} 页",
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier.fillMaxWidth().graphicsLayer {
                                scaleX = state.zoom; scaleY = state.zoom
                            }.pointerInput(state.generation, state.zoom) {
                                detectTransformGestures { _, _, zoom, _ -> model.setMangaZoom(state.zoom * zoom) }
                            }.testTag("reader_manga_page_${page.ordinal}"),
                        )
                    }
                }
            }
            ReaderMangaReadingPhase.IDLE, ReaderMangaReadingPhase.CLOSED -> Unit
        }
    }
}
