package com.reader.android.ui.detail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.reader.android.data.model.BookInfo
import com.reader.android.data.model.BookSource

class BookDetailViewModel {
    private val bridge = FakeCoreBridge()
    private val source = BookSource(sourceUrl = "", sourceName = "笔趣阁")

    var bookInfo by mutableStateOf<BookInfo?>(null)
        private set
    var isLoading by mutableStateOf(false)
        private set

    suspend fun load(detailUrl: String) {
        isLoading = true
        bookInfo = bridge.getBookInfo(detailUrl, source)
        isLoading = false
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailScreen(detailUrl: String, onBack: () -> Unit, onTOC: (String) -> Unit) {
    val viewModel = remember { BookDetailViewModel() }

    LaunchedEffect(detailUrl) {
        viewModel.load(detailUrl)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(viewModel.bookInfo?.name ?: "书籍详情") },
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
            viewModel.bookInfo != null -> {
                val info = viewModel.bookInfo!!
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    Text(info.name, style = MaterialTheme.typography.headlineMedium)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row {
                        Text("作者：${info.author}", style = MaterialTheme.typography.bodyLarge)
                        info.kind?.let { kind ->
                            Spacer(modifier = Modifier.width(16.dp))
                            Text("分类：$kind", style = MaterialTheme.typography.bodyLarge)
                        }
                    }

                    info.wordCount?.let { wc ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("字数：$wc", style = MaterialTheme.typography.bodyMedium)
                    }
                    info.latestChapter?.let { ch ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("最新：$ch", style = MaterialTheme.typography.bodyMedium)
                    }
                    info.updateTime?.let { time ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("更新：$time", style = MaterialTheme.typography.bodyMedium)
                    }

                    info.intro?.let { intro ->
                        Spacer(modifier = Modifier.height(12.dp))
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Text(
                                text = intro,
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    info.tocUrl?.let { tocUrl ->
                        Button(
                            onClick = { onTOC(tocUrl) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.List,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("查看目录")
                        }
                    }
                }
            }
        }
    }
}
