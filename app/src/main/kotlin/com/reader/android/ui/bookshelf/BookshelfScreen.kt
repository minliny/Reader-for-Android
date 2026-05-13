package com.reader.android.ui.bookshelf

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookshelfScreen(onSearchClick: () -> Unit = {}) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("书架") })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onSearchClick) {
                Icon(Icons.Filled.Search, contentDescription = "搜索")
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "书架为空",
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}
