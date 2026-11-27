package io.reader.rebuild.features.shell

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.widget.FrameLayout
import io.reader.rebuild.core.HostCapabilityRouter
import io.reader.rebuild.host.network.NetworkAndWebHost
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
            val network = NetworkAndWebHost.android(applicationContext, File(filesDir, "NetworkHost"))
            val runtime = try {
                val host = HostCapabilityRouter(SystemSurfaceHost(), network)
                LibraryRuntime.open(File(filesDir, "Library"), host) { Log.e("Reader", "Core event failed", it) }
            } catch (failure: Exception) {
                network.close()
                Log.e("Reader", "Library startup failed", failure)
                return@launch
            }
            try {
                kotlinx.coroutines.awaitCancellation()
            } finally {
                try { runtime.close() } finally { network.close() }
            }
        }
    }

    override fun onDestroy() {
        lifetime.cancel()
        super.onDestroy()
    }
}
