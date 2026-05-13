package com.minliny.reader.android.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.minliny.reader.android.ui.navigation.ReaderBottomBar
import com.minliny.reader.android.ui.navigation.ReaderNavHost
import com.minliny.reader.android.ui.theme.ReaderTheme

@Composable
fun ReaderApp() {
    ReaderTheme {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                ReaderBottomBar()
            }
        ) { innerPadding ->
            ReaderNavHost(
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}
