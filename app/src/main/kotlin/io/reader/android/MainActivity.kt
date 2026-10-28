package io.reader.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.ComposeFoundationFlags
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.lifecycle.ViewModelProvider
import io.reader.android.ui.ReaderApp
import io.reader.android.ui.ReaderViewModel

class MainActivity : ComponentActivity() {
    lateinit var readerViewModel: ReaderViewModel
        private set

    @OptIn(ExperimentalFoundationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Match Foundation 1.10.6's official stability default while retaining
        // the fixed 1.10.0 dependency. Continuation verifies the observed lazy
        // layout failure; this does not establish that prefetch caused it.
        ComposeFoundationFlags.isPausableCompositionInPrefetchEnabled = false
        readerViewModel = ViewModelProvider(this)[ReaderViewModel::class.java]
        setContent { ReaderApp(readerViewModel) }
    }
}
