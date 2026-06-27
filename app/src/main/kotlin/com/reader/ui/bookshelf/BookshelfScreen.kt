package com.reader.ui.bookshelf

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun BookshelfScreen(
    onSearch: () -> Unit,
    onOpenBook: (String) -> Unit,
    onImportSource: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Bookshelf (placeholder — Task 9 实现)")
    }
}
