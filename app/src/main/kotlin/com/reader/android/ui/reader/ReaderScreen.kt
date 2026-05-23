package com.reader.android.ui.reader

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.reader.android.data.bridge.FakeCoreBridge
import com.reader.android.data.model.BookSource
import com.reader.android.data.model.ContentPage
import com.reader.android.data.network.ContentParser
import com.reader.android.data.network.HttpClient
import com.reader.android.ui.components.ReaderLoadingState
import com.reader.android.ui.reader.components.BrightnessDock
import com.reader.android.ui.reader.components.ReaderControlBase
import com.reader.android.ui.reader.components.ReaderNightState
import com.reader.android.ui.theme.ReaderTheme

class ReaderViewModel(private val useRealHttp: Boolean = false) {
    private val bridge = FakeCoreBridge()
    private val httpClient = HttpClient()
    private val parser = ContentParser()
    private val source = BookSource(sourceUrl = "", sourceName = "笔趣阁")

    var content by mutableStateOf<ContentPage?>(null)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var chapterTitle by mutableStateOf("")
        private set
    var error by mutableStateOf<String?>(null)
        private set

    suspend fun load(contentUrl: String, title: String = "") {
        isLoading = true
        chapterTitle = title
        error = null
        try {
            if (useRealHttp) {
                val response = httpClient.get(contentUrl)
                content = parser.parseContentResponse(response.body)
            } else {
                content = bridge.getContent(contentUrl, source)
            }
        } catch (e: Exception) {
            error = e.message ?: "加载失败"
        }
        isLoading = false
    }
}

@Composable
fun ReaderScreen(
    contentUrl: String? = null,
    chapterTitle: String = "",
    onBack: (() -> Unit)? = null,
    onNextChapter: ((String, String) -> Unit)? = null
) {
    val viewModel = remember { ReaderViewModel() }
    var isNight by remember { mutableStateOf(false) }

    LaunchedEffect(contentUrl) {
        if (contentUrl != null) {
            viewModel.load(contentUrl, chapterTitle)
        }
    }

    ReaderNightState(isNight = isNight) {
        when {
            viewModel.isLoading -> {
                ReaderLoadingState(modifier = Modifier.fillMaxSize(), message = "加载中")
            }
            viewModel.content != null -> {
                val text = viewModel.content!!.content
                ReaderControlBase(
                    chapterTitle = viewModel.chapterTitle.ifBlank { chapterTitle.ifBlank { "阅读" } },
                    bookTitle = viewModel.chapterTitle.ifBlank { chapterTitle },
                    sourceName = "本地书籍",
                    chapterProgress = 0.25f,
                    brightnessDock = BrightnessDock.Left,
                    onBackClick = { onBack?.invoke() },
                    onNightModeClick = { isNight = !isNight }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp, vertical = 128.dp)
                    ) {
                        Text(
                            text = text,
                            color = ReaderTheme.colors.bodyText,
                            style = ReaderTheme.typography.readerBody,
                            lineHeight = ReaderTheme.typography.readerBody.lineHeight
                        )
                    }
                }
            }
            contentUrl == null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(128.dp)
                ) {
                    Text(
                        "选择一个章节开始阅读",
                        color = ReaderTheme.colors.bodyText,
                        style = ReaderTheme.typography.stateMessage
                    )
                }
            }
        }
    }
}
