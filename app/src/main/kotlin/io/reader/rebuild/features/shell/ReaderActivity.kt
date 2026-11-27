package io.reader.rebuild.features.shell

import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.reader.rebuild.features.settings.ReaderPreferences
import io.reader.rebuild.features.library.BookReference
import io.reader.rebuild.core.readerResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class ReaderActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val owner = ViewModelProvider(this)[ReaderActivityOwner::class.java]
        setContent { ReaderApp(owner) }
    }
}

internal class ReaderActivityOwner(private val app: Application) : AndroidViewModel(app) {
    val navigation = ReaderNavigation()
    val library = MutableStateFlow<Result<ReaderSession>?>(null)
    val preferences = ReaderPreferences(app.getSharedPreferences("reader-preferences", Context.MODE_PRIVATE))
    private var opening = false

    init { open() }

    fun open() {
        if (opening || library.value?.isSuccess == true) return
        opening = true
        library.value = null
        viewModelScope.launch {
            try {
                library.value = readerResult { ReaderSession.open(app) }
                library.value?.getOrNull()?.recoverDownloads()
            } finally {
                opening = false
            }
        }
    }

    override fun onCleared() {
        library.value?.getOrNull()?.let { session ->
            CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch { session.close() }
        }
    }
}

internal class ReaderNavigation {
    private val path = mutableStateListOf<Route>()
    private var sequence = 0L
    var popping = false
        private set
    val current: Route? get() = path.lastOrNull()
    fun open(destination: Destination) {
        popping = false
        path += Route(++sequence, destination)
    }

    fun back(): Route? {
        popping = true
        return path.removeLastOrNull()
    }
    fun updateReadingBook(reference: BookReference) {
        val active = path.lastOrNull() ?: return
        val reading = active.page as? Destination.Read ?: return
        val previous = path.getOrNull(path.lastIndex - 1)
        if (previous?.page is Destination.Detail) {
            path[path.lastIndex - 1] = previous.copy(page = Destination.Detail(reference))
        }
        path[path.lastIndex] = active.copy(page = reading.copy(book = reference, directory = false, sources = false))
    }
}
