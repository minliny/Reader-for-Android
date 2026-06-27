package com.reader.ui.reading

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadingScreen(
    sourceId: String,
    bookUrl: String,
    bookName: String,
    vm: ReadingViewModel = viewModel()
) {
    val state by vm.uiState.collectAsState()
    val content by vm.content.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (val s = state) {
                            is ReadingUiState.Ready -> s.book.name.ifEmpty { "Reader" }
                            else -> bookName.ifEmpty { "Reader" }
                        }
                    )
                }
            )
        },
        bottomBar = {
            if (state is ReadingUiState.Ready) {
                BottomAppBar {
                    IconButton(onClick = vm::prevChapter) { Icon(Icons.Filled.SkipPrevious, "Prev") }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = vm::nextChapter) { Icon(Icons.Filled.SkipNext, "Next") }
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (val s = state) {
                is ReadingUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                is ReadingUiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(s.message, color = MaterialTheme.colorScheme.error)
                }
                is ReadingUiState.Ready -> Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
                ) {
                    Text(content, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
