package io.reader.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import io.reader.android.ui.ReaderApp
import io.reader.android.ui.ReaderViewModel

class MainActivity : ComponentActivity() {
    lateinit var readerViewModel: ReaderViewModel
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        readerViewModel = ViewModelProvider(this)[ReaderViewModel::class.java]
        setContent { ReaderApp(readerViewModel) }
    }
}
