package io.reader.rebuild.features.shell

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.widget.FrameLayout
import io.reader.rebuild.host.storage.LibraryRuntime
import io.reader.rebuild.host.system.SystemSurfaceHost
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ReaderActivity : Activity() {
    private val lifetime = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(FrameLayout(this))
        lifetime.launch {
            val runtime = try {
                LibraryRuntime.open(File(filesDir, "Library"), SystemSurfaceHost()) { Log.e("Reader", "Core event failed", it) }
            } catch (failure: Exception) {
                Log.e("Reader", "Library startup failed", failure)
                return@launch
            }
            try {
                kotlinx.coroutines.awaitCancellation()
            } finally {
                runtime.close()
            }
        }
    }

    override fun onDestroy() {
        lifetime.cancel()
        super.onDestroy()
    }
}
