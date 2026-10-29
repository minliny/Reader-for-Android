package io.reader.android

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.reader.core.ReaderCoreAssetException
import com.reader.core.ReaderCoreRuntime
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/**
 * Native-only A6 bridge contract. It creates no Reader production runtime or
 * manga request; it proves the same Core-owned runtime handle can transfer,
 * read and release an opaque response asset with exact owner identity.
 */
@RunWith(AndroidJUnit4::class)
class NativeAssetBridgeTest {
    @Test(timeout = 20_000)
    fun pinnedCoreAssetBridgeRoundTripsOwnedBytes() {
        assertEquals(PIN, BuildConfig.CORE_COMMIT)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val temporary = File(context.cacheDir, "A6NativeAsset/${UUID.randomUUID()}")
        val data = File(temporary, "data")
        val cache = File(temporary, "cache")
        assertTrue(data.mkdirs())
        assertTrue(cache.mkdirs())
        val payload = "reader-manga-asset-bridge".toByteArray(Charsets.UTF_8)
        val requestId = 470_003L
        val operationId = 77L
        try {
            ReaderCoreRuntime(JSONObject().put("dataDirectory", data.absolutePath)
                .put("cacheDirectory", cache.absolutePath).toString()).use { runtime ->
                val assetId = runtime.assetBegin(requestId, operationId, payload.size.toLong())
                assertTrue(assetId > 0)
                assertEquals(payload.size.toLong(), runtime.assetWrite(requestId, operationId, assetId, payload))
                assertEquals(payload.size.toLong(), runtime.assetCommit(requestId, operationId, assetId))
                assertArrayEquals(payload.copyOfRange(0, 8),
                    runtime.assetReadChunk(requestId, operationId, assetId, 0, 8))
                assertArrayEquals(payload.copyOfRange(8, payload.size),
                    runtime.assetReadChunk(requestId, operationId, assetId, 8, 1024))
                assertArrayEquals(ByteArray(0),
                    runtime.assetReadChunk(requestId, operationId, assetId, payload.size.toLong(), 1024))
                runtime.assetRelease(requestId, operationId, assetId)
                val failure = expectAssetFailure {
                    runtime.assetReadChunk(requestId, operationId, assetId, 0, 8)
                }
                assertTrue(failure.diagnostic().isNotEmpty())
            }
        } finally {
            assertTrue(!temporary.exists() || temporary.deleteRecursively())
        }
    }

    private fun expectAssetFailure(call: () -> Unit): ReaderCoreAssetException {
        try {
            call()
        } catch (error: ReaderCoreAssetException) {
            return error
        }
        throw AssertionError("Released Core asset must reject a later read")
    }

    companion object {
        private const val PIN = "c3109d570dd62a636eff1ffb88f4e242ede0bf3a"
    }
}
