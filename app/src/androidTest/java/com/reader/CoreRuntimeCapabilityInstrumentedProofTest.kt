package com.reader

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.reader.host.CacheGetHandler
import com.reader.host.CachePutHandler
import com.reader.host.DefaultHostCache
import com.reader.host.DefaultHostFileSystem
import com.reader.host.FileDeleteHandler
import com.reader.host.FileReadHandler
import com.reader.host.FileWriteHandler
import com.reader.host.HostCachePersistenceAdapter
import com.reader.host.HostReply
import com.reader.host.HostRequest
import com.reader.host.LogEmitHandler
import com.reader.host.PersistenceGetHandler
import com.reader.host.PersistencePutHandler
import com.reader.host.SystemInfoHandler
import com.reader.host.TimeNowHandler
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Slice B instrumented proof: verifies the 10 new Core Runtime capability
 * handlers execute against real Android storage ([Context.filesDir]) and
 * return well-formed `host.complete` / `host.error` replies.
 *
 * **Proof tier**: executor (real Android, no JNI/Core round-trip). Each
 * handler is invoked directly with a [HostRequest]; the underlying executor
 * touches real disk (filesDir) / real clock / real Build fields.
 *
 * App-level workflow proof (Core → host.request → handler → host.complete
 * round-trip through the JNI poll thread) lives in
 * [AppLevelReadingChainProofTest] / [CoreRuntimeCapabilityAppProofTest].
 */
@RunWith(AndroidJUnit4::class)
class CoreRuntimeCapabilityInstrumentedProofTest {

    private lateinit var ctx: Context
    private lateinit var fs: DefaultHostFileSystem

    @Before
    fun setUp() {
        ctx = InstrumentationRegistry.getInstrumentation().targetContext
        fs = DefaultHostFileSystem(ctx.filesDir)
    }

    // ── file.read / file.write / file.delete ──────────────────────────────

    @Test
    fun fileWriteThenReadRoundTripsOnDevice() {
        val path = "proof/chapter-${System.nanoTime()}.txt"
        val content = "on-device proof content"

        val writeReply = FileWriteHandler(fs).handle(
            HostRequest(1L, 101L, FileWriteHandler.CAPABILITY,
                JSONObject().apply { put("path", path); put("content", content) }.toString())
        )
        assertTrue("write must complete", writeReply.isComplete())

        val readReply = FileReadHandler(fs).handle(
            HostRequest(1L, 102L, FileReadHandler.CAPABILITY,
                JSONObject().apply { put("path", path) }.toString())
        )
        assertTrue("read must complete", readReply.isComplete())
        assertEquals(content,
            JSONObject((readReply as HostReply.Complete).resultJson()).getString("content"))

        // cleanup
        FileDeleteHandler(fs).handle(
            HostRequest(1L, 103L, FileDeleteHandler.CAPABILITY,
                JSONObject().apply { put("path", path) }.toString())
        )
    }

    @Test
    fun fileReadMissingReturnsNotFoundOnDevice() {
        val reply = FileReadHandler(fs).handle(
            HostRequest(1L, 104L, FileReadHandler.CAPABILITY,
                JSONObject().apply { put("path", "proof/nonexistent-${System.nanoTime()}.txt") }.toString())
        )
        assertTrue("must error", reply.isError())
        assertEquals("NOT_FOUND", (reply as HostReply.Error).code())
    }

    // ── cache.get / cache.put ─────────────────────────────────────────────

    @Test
    fun cachePutThenGetRoundTripsOnDevice() {
        val cache = DefaultHostCache()
        CachePutHandler(cache).handle(
            HostRequest(1L, 201L, CachePutHandler.CAPABILITY,
                JSONObject().apply { put("namespace", "proof"); put("key", "proof-k"); put("value", "proof-v") }.toString())
        )
        val reply = CacheGetHandler(cache).handle(
            HostRequest(1L, 202L, CacheGetHandler.CAPABILITY,
                JSONObject().apply { put("namespace", "proof"); put("key", "proof-k") }.toString())
        )
        assertTrue("must complete", reply.isComplete())
        val result = JSONObject((reply as HostReply.Complete).resultJson())
        assertTrue(result.getBoolean("hit"))
        assertEquals("proof-v", result.getString("value"))
    }

    // ── persistence.get / persistence.put ─────────────────────────────────

    @Test
    fun persistencePutThenGetRoundTripsOnDevice() {
        val store = HostCachePersistenceAdapter(DefaultHostCache())
        PersistencePutHandler(store).handle(
            HostRequest(1L, 301L, PersistencePutHandler.CAPABILITY,
                JSONObject().apply { put("namespace", "proof"); put("key", "proof-p"); put("value", "persisted") }.toString())
        )
        val reply = PersistenceGetHandler(store).handle(
            HostRequest(1L, 302L, PersistenceGetHandler.CAPABILITY,
                JSONObject().apply { put("namespace", "proof"); put("key", "proof-p") }.toString())
        )
        assertTrue("must complete", reply.isComplete())
        val result = JSONObject((reply as HostReply.Complete).resultJson())
        assertTrue(result.getBoolean("found"))
        assertEquals("persisted", result.getString("value"))
        assertEquals("1", result.getString("revision"))
    }

    // ── log.emit / time.now / system.info ─────────────────────────────────

    @Test
    fun logEmitReturnsLoggedOnDevice() {
        val reply = LogEmitHandler().handle(
            HostRequest(1L, 401L, LogEmitHandler.CAPABILITY,
                JSONObject().apply { put("level", "info"); put("message", "instrumented proof") }.toString())
        )
        assertTrue("must complete", reply.isComplete())
        assertTrue(JSONObject((reply as HostReply.Complete).resultJson()).getBoolean("emitted"))
    }

    @Test
    fun timeNowReturnsUnixMillisOnDevice() {
        val reply = TimeNowHandler().handle(
            HostRequest(1L, 501L, TimeNowHandler.CAPABILITY, "{}")
        )
        assertTrue("must complete", reply.isComplete())
        val result = JSONObject((reply as HostReply.Complete).resultJson())
        assertTrue("unixMillis must be positive", result.getLong("unixMillis") > 0)
        assertTrue("iso8601 must end with Z", result.getString("iso8601").endsWith("Z"))
    }

    @Test
    fun systemInfoReturnsRealAndroidFieldsOnDevice() {
        val reply = SystemInfoHandler().handle(
            HostRequest(1L, 601L, SystemInfoHandler.CAPABILITY, "{}")
        )
        assertTrue("must complete", reply.isComplete())
        val info = JSONObject((reply as HostReply.Complete).resultJson()).getJSONObject("info")
        assertEquals("android", info.getString("platform"))
        // On a real device osVersion must be a real SDK_INT, not "unknown".
        val osVersion = info.getString("osVersion")
        assertTrue("osVersion must be a real SDK int on device, got: $osVersion",
            osVersion != "unknown" && osVersion.toIntOrNull() != null)
        assertTrue("manufacturer must be non-empty", info.getString("manufacturer").isNotEmpty())
        assertTrue("model must be non-empty", info.getString("model").isNotEmpty())
    }
}
