package com.reader.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
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
fun SearchScreen(
    onBookClick: (com.reader.api.SearchBook) -> Unit,
    vm: SearchViewModel = viewModel()
) {
    val query by vm.query.collectAsState()
    val state by vm.uiState.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("搜索") })
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = vm::updateQuery,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("输入书名或作者") },
                trailingIcon = {
                    IconButton(onClick = vm::search) { Icon(Icons.Filled.Search, "Search") }
                }
            )
            Spacer(Modifier.height(12.dp))
            when (val s = state) {
                is SearchUiState.Idle -> {}
                is SearchUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                is SearchUiState.Empty -> Text("无搜索结果")
                is SearchUiState.Error -> Text(s.message, color = MaterialTheme.colorScheme.error)
                is SearchUiState.Success -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(s.results, key = { it.bookUrl + it.origin }) { book ->
                        ListItem(
                            headlineContent = { Text(book.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = { Text(book.author, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            modifier = Modifier.clickable { onBookClick(book) }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
