package com.reader.ui.bookshelf

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookshelfScreen(
    onSearch: () -> Unit,
    onOpenBook: (String) -> Unit,
    onImportSource: () -> Unit,
    vm: BookshelfViewModel = viewModel()
) {
    val state by vm.uiState.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reader") },
                actions = {
                    IconButton(onClick = onImportSource) { Icon(Icons.Filled.Add, "Import source") }
                    IconButton(onClick = onSearch) { Icon(Icons.Filled.Search, "Search") }
                }
            )
        }
    ) { padding ->
        when (val s = state) {
            is UiState.Loading -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            is UiState.Empty -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("书架为空", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("点击右上角搜索或导入书源", style = MaterialTheme.typography.bodyMedium)
                }
            }
            is UiState.Error -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("加载失败: ${s.message}", color = MaterialTheme.colorScheme.error)
            }
            is UiState.Success -> LazyVerticalGrid(
                columns = GridCells.Adaptive(120.dp),
                contentPadding = PaddingValues(16.dp, padding.calculateTopPadding(), 16.dp, 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(s.books, key = { it.bookUrl }) { book ->
                    BookCard(book = book, onClick = { onOpenBook(book.bookUrl) })
                }
            }
        }
    }
}

@Composable
private fun BookCard(book: com.reader.api.Book, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(8.dp)) {
            Surface(modifier = Modifier.fillMaxWidth().height(140.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                // 简化:无封面图(Coil 可后续加)
            }
            Spacer(Modifier.height(4.dp))
            Text(book.name, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(book.author, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
