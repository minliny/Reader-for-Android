package com.reader.android.ui.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.unit.dp
import com.reader.android.data.bridge.FakeCoreBridge
import com.reader.android.data.model.BookSource
import com.reader.android.data.model.ContentPage

class ReaderViewModel {
    private val bridge = FakeCoreBridge()
    private val source = BookSource(sourceUrl = "", sourceName = "笔趣阁")

    var content by mutableStateOf<ContentPage?>(null)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var chapterTitle by mutableStateOf("")
        private set

    suspend fun load(contentUrl: String, title: String = "") {
        isLoading = true
        chapterTitle = title
        content = bridge.getContent(contentUrl, source)
        isLoading = false
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    contentUrl: String? = null,
    chapterTitle: String = "",
    onBack: (() -> Unit)? = null,
    onNextChapter: ((String, String) -> Unit)? = null
) {
    val viewModel = remember { ReaderViewModel() }

    LaunchedEffect(contentUrl) {
        if (contentUrl != null) {
            viewModel.load(contentUrl, chapterTitle)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = viewModel.chapterTitle.ifEmpty { "阅读" },
                        maxLines = 1
                    )
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    }
                }
            )
        },
        bottomBar = {
            val nextUrl = viewModel.content?.nextPageUrl
            if (nextUrl != null && onNextChapter != null) {
                BottomAppBar {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(onClick = {
                            onNextChapter(nextUrl, "下一章")
                        }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "下一章"
                            )
                        }
                    }
                }
            }
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
            viewModel.content != null -> {
                val text = viewModel.content!!.content
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyLarge,
                        lineHeight = MaterialTheme.typography.bodyLarge.lineHeight
                    )
                }
            }
            contentUrl == null -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text("选择一个章节开始阅读", style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}
