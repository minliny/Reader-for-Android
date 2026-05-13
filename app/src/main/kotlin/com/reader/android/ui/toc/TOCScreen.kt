package com.reader.android.ui.toc

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.reader.android.data.bridge.FakeCoreBridge
import com.reader.android.data.model.BookSource
import com.reader.android.data.model.TOCItem

class TOCViewModel {
    private val bridge = FakeCoreBridge()
    private val source = BookSource(sourceUrl = "", sourceName = "笔趣阁")

    var chapters by mutableStateOf<List<TOCItem>>(emptyList())
        private set
    var isLoading by mutableStateOf(false)
        private set

    suspend fun load(tocUrl: String) {
        isLoading = true
        chapters = bridge.getTOC(tocUrl, source)
        isLoading = false
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TOCScreen(tocUrl: String, onBack: () -> Unit, onChapterClick: (String, String) -> Unit) {
    val viewModel = remember { TOCViewModel() }

    LaunchedEffect(tocUrl) {
        viewModel.load(tocUrl)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("目录") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        when {
            viewModel.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            viewModel.chapters.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text("暂无章节", style = MaterialTheme.typography.bodyLarge)
                }
            }
            else -> {
                LazyColumn(modifier = Modifier.padding(padding)) {
                    items(viewModel.chapters) { item ->
                        ChapterItem(
                            item = item,
                            depth = 0,
                            onChapterClick = onChapterClick
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChapterItem(
    item: TOCItem,
    depth: Int,
    onChapterClick: (String, String) -> Unit
) {
    if (item.url.isNotBlank()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onChapterClick(item.url, item.title) }
                .padding(start = (16 + depth * 20).dp, end = 16.dp, top = 12.dp, bottom = 12.dp)
        ) {
            Text(
                text = item.title,
                style = if (depth == 0) MaterialTheme.typography.bodyLarge
                        else MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    } else {
        // Volume header (no url)
        Text(
            text = item.title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = (16 + depth * 20).dp, top = 16.dp, bottom = 4.dp)
        )
    }

    item.children.forEach { child ->
        ChapterItem(
            item = child,
            depth = depth + 1,
            onChapterClick = onChapterClick
        )
    }

    if (depth == 0) {
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
    }
}
