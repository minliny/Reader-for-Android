package com.reader.android.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.reader.android.ui.theme.ReaderTheme

@Composable
fun ReaderAndroidApp() {
    ReaderTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = ReaderTheme.colors.paperBg
        ) {
            AppNavigation()
        }
    }
}
