package io.reader.android

import android.content.Context
import android.os.Build
import android.os.Process
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.reader.core.ReaderCoreRuntime
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Selector: io.reader.android.NativeRuntimeTest#pinnedCoreLoadsAndReturnsOriginalCommand
 * Exercises the real JNI load and one original core.info reply. Runtime storage
 * is isolated in a disposable cache directory; no production files or books.
 */
@RunWith(AndroidJUnit4::class)
class NativeRuntimeTest {
    @Test(timeout = 15_000)
    fun pinnedCoreLoadsAndReturnsOriginalCommand() {
        assertTrue("The selected JNI lane requires a 64-bit process", Process.is64Bit())
        assertTrue("The selected target must support arm64-v8a", "arm64-v8a" in Build.SUPPORTED_64_BIT_ABIS)
        // This invokes System.loadLibrary through the exact pinned Java wrapper.
        assertEquals(1, ReaderCoreRuntime.abiVersion())

        val context = ApplicationProvider.getApplicationContext<Context>()
        val temporary = File(context.cacheDir, "A0NativeRuntime/${UUID.randomUUID()}")
        val data = File(temporary, "data")
        val cache = File(temporary, "cache")
        try {
            assertTrue("Isolated runtime data directory must be created", data.mkdirs())
            assertTrue("Isolated runtime cache directory must be created", cache.mkdirs())
            val configuration = JSONObject().put("dataDirectory", data.absolutePath)
                .put("cacheDirectory", cache.absolutePath)
            ReaderCoreRuntime(configuration.toString()).use { runtime ->
                val originalRequestId = 470_001L
                runtime.sendCommand("core.info", originalRequestId, "{}")
                val deadline = SystemClock.elapsedRealtime() + 10_000
                var reply: String? = null
                while (reply == null && SystemClock.elapsedRealtime() < deadline) {
                    reply = runtime.pollEvent(100, TimeUnit.MILLISECONDS)
                }
                val event = JSONObject(reply ?: throw AssertionError("Original core.info reply exceeded its bound"))
                assertEquals(1, event.getInt("protocolVersion"))
                assertEquals(originalRequestId, event.getLong("requestId"))
                assertEquals("result", event.getString("type"))
                val info = event.getJSONObject("data")
                assertEquals(1, info.getInt("abiVersion"))
                assertEquals(1, info.getInt("protocolVersion"))
                val identity = info.getJSONObject("buildIdentity")
                assertEquals(BuildConfig.CORE_COMMIT, identity.getString("gitCommit"))
                assertFalse("The JNI library must bind the fixed clean Core archive", identity.getBoolean("gitDirty"))
            }
        } finally {
            assertTrue("Disposable runtime cache must be removed after close", !temporary.exists() || temporary.deleteRecursively())
        }
    }
}
