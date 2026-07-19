package com.reader.host

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * JVM unit tests for the Slice B Core Runtime capability handlers.
 *
 * Each handler is exercised through its [CapabilityHandler.handle] entry
 * point so the test covers both param validation and the underlying
 * executor. Real Android primitives (WebView, DataStore, NotificationManager)
 * are NOT touched — those are proven at the instrumented/App tier.
 */
class CoreRuntimeCapabilityHandlersJvmTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    // ── file.read / file.write / file.delete ──────────────────────────────

    @Test
    fun `file_write then file_read round-trips content`() {
        val fs = InMemoryHostFileSystem()
        val write = FileWriteHandler(fs)
        val read = FileReadHandler(fs)

        val writeReply = write.handle(
            HostRequest(1L, 101L, FileWriteHandler.CAPABILITY,
                JSONObject().apply {
                    put("path", "cache/chapter-1.txt")
                    put("content", "hello world")
                }.toString())
        )
        assertTrue("write must complete, got: ${writeReply.kind()}", writeReply.isComplete())
        val writeResult = JSONObject((writeReply as HostReply.Complete).resultJson())
        assertTrue(writeResult.getBoolean("written"))
        assertEquals("hello world".length.toLong(), writeResult.getLong("byteLength"))

        val readReply = read.handle(
            HostRequest(1L, 102L, FileReadHandler.CAPABILITY,
                JSONObject().apply { put("path", "cache/chapter-1.txt") }.toString())
        )
        assertTrue("read must complete", readReply.isComplete())
        val result = JSONObject((readReply as HostReply.Complete).resultJson())
        assertEquals("hello world", result.getString("content"))
        assertEquals("hello world".length, result.getInt("byteLength"))
    }

    @Test
    fun `file_write accepts contentBase64 and file_read can slice as base64`() {
        val fs = InMemoryHostFileSystem()
        val encoded = java.util.Base64.getEncoder().encodeToString("abcdef".toByteArray())
        val writeReply = FileWriteHandler(fs).handle(
            HostRequest(1L, 112L, FileWriteHandler.CAPABILITY,
                JSONObject().apply {
                    put("path", "binary.dat")
                    put("contentBase64", encoded)
                }.toString())
        )
        assertTrue("write must complete", writeReply.isComplete())

        val readReply = FileReadHandler(fs).handle(
            HostRequest(1L, 113L, FileReadHandler.CAPABILITY,
                JSONObject().apply {
                    put("path", "binary.dat")
                    put("encoding", "base64")
                    put("byteOffset", 2)
                    put("maxBytes", 3)
                }.toString())
        )
        val result = JSONObject((readReply as HostReply.Complete).resultJson())
        assertEquals(java.util.Base64.getEncoder().encodeToString("cde".toByteArray()), result.getString("contentBase64"))
        assertEquals(3, result.getInt("byteLength"))
    }

    @Test
    fun `file_read missing file returns NOT_FOUND`() {
        val fs = InMemoryHostFileSystem()
        val read = FileReadHandler(fs)
        val reply = read.handle(
            HostRequest(1L, 103L, FileReadHandler.CAPABILITY,
                JSONObject().apply { put("path", "nope.txt") }.toString())
        )
        assertTrue("must error", reply.isError())
        assertEquals("NOT_FOUND", (reply as HostReply.Error).code())
        assertFalse("NOT_FOUND is non-retryable", reply.retryable())
    }

    @Test
    fun `file_write with append appends to existing content`() {
        val fs = InMemoryHostFileSystem()
        FileWriteHandler(fs).handle(
            HostRequest(1L, 104L, FileWriteHandler.CAPABILITY,
                JSONObject().apply { put("path", "log.txt"); put("content", "line1\n") }.toString())
        )
        FileWriteHandler(fs).handle(
            HostRequest(1L, 105L, FileWriteHandler.CAPABILITY,
                JSONObject().apply {
                    put("path", "log.txt"); put("content", "line2\n"); put("append", true)
                }.toString())
        )
        val read = FileReadHandler(fs).handle(
            HostRequest(1L, 106L, FileReadHandler.CAPABILITY,
                JSONObject().apply { put("path", "log.txt") }.toString())
        )
        val content = JSONObject((read as HostReply.Complete).resultJson()).getString("content")
        assertEquals("line1\nline2\n", content)
    }

    @Test
    fun `file_delete removes existing file and returns deleted=true`() {
        val fs = InMemoryHostFileSystem()
        FileWriteHandler(fs).handle(
            HostRequest(1L, 107L, FileWriteHandler.CAPABILITY,
                JSONObject().apply { put("path", "to-delete.txt"); put("content", "x") }.toString())
        )
        val del = FileDeleteHandler(fs).handle(
            HostRequest(1L, 108L, FileDeleteHandler.CAPABILITY,
                JSONObject().apply { put("path", "to-delete.txt") }.toString())
        )
        assertTrue("delete must complete", del.isComplete())
        assertTrue(JSONObject((del as HostReply.Complete).resultJson()).getBoolean("deleted"))
    }

    @Test
    fun `file_delete on missing file returns deleted=false`() {
        val fs = InMemoryHostFileSystem()
        val del = FileDeleteHandler(fs).handle(
            HostRequest(1L, 109L, FileDeleteHandler.CAPABILITY,
                JSONObject().apply { put("path", "ghost.txt") }.toString())
        )
        assertTrue("delete must complete", del.isComplete())
        assertFalse(JSONObject((del as HostReply.Complete).resultJson()).getBoolean("deleted"))
    }

    @Test
    fun `file_read rejects path traversal with SECURITY`() {
        val fs = InMemoryHostFileSystem()
        val reply = FileReadHandler(fs).handle(
            HostRequest(1L, 110L, FileReadHandler.CAPABILITY,
                JSONObject().apply { put("path", "../../etc/passwd") }.toString())
        )
        assertTrue("must error", reply.isError())
        assertEquals("SECURITY", (reply as HostReply.Error).code())
    }

    @Test
    fun `filesystem root prefix collision is rejected`() {
        val root = tempFolder.newFolder("sandbox")
        val sibling = tempFolder.newFolder("sandbox-escape")
        val relativeEscape = "../${sibling.name}/secret.txt"

        try {
            resolveSafely(root, relativeEscape)
            error("expected SecurityException for sibling prefix collision")
        } catch (error: SecurityException) {
            assertTrue(error.message!!.contains("escape"))
        }
    }

    @Test
    fun `file_read rejects blank path`() {
        val fs = InMemoryHostFileSystem()
        val reply = FileReadHandler(fs).handle(
            HostRequest(1L, 111L, FileReadHandler.CAPABILITY,
                JSONObject().apply { put("path", "") }.toString())
        )
        assertTrue("must error", reply.isError())
        assertEquals("INTERNAL", (reply as HostReply.Error).code())
    }

    // ── cache.get / cache.put ─────────────────────────────────────────────

    @Test
    fun `cache_put then cache_get round-trips value`() {
        val cache = DefaultHostCache()
        CachePutHandler(cache).handle(
            HostRequest(1L, 201L, CachePutHandler.CAPABILITY,
                JSONObject().apply { put("namespace", "books"); put("key", "k1"); put("value", "v1") }.toString())
        )
        val reply = CacheGetHandler(cache).handle(
            HostRequest(1L, 202L, CacheGetHandler.CAPABILITY,
                JSONObject().apply { put("namespace", "books"); put("key", "k1") }.toString())
        )
        assertTrue("must complete", reply.isComplete())
        val result = JSONObject((reply as HostReply.Complete).resultJson())
        assertTrue(result.getBoolean("hit"))
        assertEquals("v1", result.getString("value"))
    }

    @Test
    fun `cache_get on missing key returns hit false without value`() {
        val cache = DefaultHostCache()
        val reply = CacheGetHandler(cache).handle(
            HostRequest(1L, 203L, CacheGetHandler.CAPABILITY,
                JSONObject().apply { put("namespace", "books"); put("key", "absent") }.toString())
        )
        assertTrue("must complete", reply.isComplete())
        val result = JSONObject((reply as HostReply.Complete).resultJson())
        assertFalse("hit should be false", result.getBoolean("hit"))
        assertFalse("miss must not include value", result.has("value"))
    }

    @Test
    fun `cache_put with ttl expires entry`() {
        val cache = DefaultHostCache()
        CachePutHandler(cache).handle(
            HostRequest(1L, 204L, CachePutHandler.CAPABILITY,
                JSONObject().apply {
                    put("namespace", "books"); put("key", "ephemeral"); put("value", "soon-gone"); put("ttlMillis", 1)
                }.toString())
        )
        Thread.sleep(20)
        val reply = CacheGetHandler(cache).handle(
            HostRequest(1L, 205L, CacheGetHandler.CAPABILITY,
                JSONObject().apply { put("namespace", "books"); put("key", "ephemeral") }.toString())
        )
        assertTrue("must complete", reply.isComplete())
        assertFalse("expired entry should be a miss",
            JSONObject((reply as HostReply.Complete).resultJson()).getBoolean("hit"))
    }

    // ── persistence.get / persistence.put ─────────────────────────────────

    @Test
    fun `persistence_put then persistence_get round-trips value`() {
        val store = HostCachePersistenceAdapter(DefaultHostCache())
        PersistencePutHandler(store).handle(
            HostRequest(1L, 301L, PersistencePutHandler.CAPABILITY,
                JSONObject().apply { put("namespace", "reader"); put("key", "p1"); put("value", "persisted") }.toString())
        )
        val reply = PersistenceGetHandler(store).handle(
            HostRequest(1L, 302L, PersistenceGetHandler.CAPABILITY,
                JSONObject().apply { put("namespace", "reader"); put("key", "p1") }.toString())
        )
        assertTrue("must complete", reply.isComplete())
        val result = JSONObject((reply as HostReply.Complete).resultJson())
        assertTrue(result.getBoolean("found"))
        assertEquals("persisted", result.getString("value"))
        assertEquals("1", result.getString("revision"))
    }

    @Test
    fun `persistence_get on missing key returns found false without value`() {
        val store = HostCachePersistenceAdapter(DefaultHostCache())
        val reply = PersistenceGetHandler(store).handle(
            HostRequest(1L, 303L, PersistenceGetHandler.CAPABILITY,
                JSONObject().apply { put("namespace", "reader"); put("key", "missing") }.toString())
        )
        assertTrue("must complete", reply.isComplete())
        val result = JSONObject((reply as HostReply.Complete).resultJson())
        assertFalse(result.getBoolean("found"))
        assertFalse(result.has("value"))
    }

    @Test
    fun `persistence_put enforces expected revision`() {
        val store = HostCachePersistenceAdapter(DefaultHostCache())
        val first = PersistencePutHandler(store).handle(
            HostRequest(1L, 304L, PersistencePutHandler.CAPABILITY,
                JSONObject().apply { put("namespace", "reader"); put("key", "p2"); put("value", "one") }.toString())
        )
        assertEquals("1", JSONObject((first as HostReply.Complete).resultJson()).getString("revision"))

        val conflict = PersistencePutHandler(store).handle(
            HostRequest(1L, 305L, PersistencePutHandler.CAPABILITY,
                JSONObject().apply {
                    put("namespace", "reader")
                    put("key", "p2")
                    put("value", "two")
                    put("expectedRevision", "0")
                }.toString())
        )
        assertTrue(conflict.isError())
        assertEquals("CONFLICT", (conflict as HostReply.Error).code())
    }

    // ── log.emit / time.now / system.info ─────────────────────────────────

    @Test
    fun `log_emit captures level and message`() {
        val logger = CapturingHostLogger()
        val reply = LogEmitHandler(logger).handle(
            HostRequest(1L, 401L, LogEmitHandler.CAPABILITY,
                JSONObject().apply {
                    put("level", "warn"); put("message", "test warning")
                }.toString())
        )
        assertTrue("must complete", reply.isComplete())
        assertTrue(JSONObject((reply as HostReply.Complete).resultJson()).getBoolean("emitted"))
        assertEquals(1, logger.entries.size)
        assertEquals("warn", logger.entries[0].level)
        assertEquals("test warning", logger.entries[0].message)
    }

    @Test
    fun `log_emit swallows sink failure and still returns emitted=true`() {
        val failingLogger = object : HostLogger {
            override fun emit(level: String, message: String, metadata: JSONObject?) {
                throw RuntimeException("sink exploded")
            }
        }
        val reply = LogEmitHandler(failingLogger).handle(
            HostRequest(1L, 402L, LogEmitHandler.CAPABILITY,
                JSONObject().apply { put("level", "info"); put("message", "still ok") }.toString())
        )
        assertTrue("must complete even if sink throws", reply.isComplete())
    }

    @Test
    fun `log_emit rejects blank message`() {
        val reply = LogEmitHandler(CapturingHostLogger()).handle(
            HostRequest(1L, 403L, LogEmitHandler.CAPABILITY,
                JSONObject().apply { put("level", "info"); put("message", "") }.toString())
        )
        assertTrue("must error", reply.isError())
        assertEquals("INTERNAL", (reply as HostReply.Error).code())
    }

    @Test
    fun `time_now returns unixMillis and iso8601`() {
        val reply = TimeNowHandler().handle(
            HostRequest(1L, 501L, TimeNowHandler.CAPABILITY, "{}")
        )
        assertTrue("must complete", reply.isComplete())
        val result = JSONObject((reply as HostReply.Complete).resultJson())
        assertTrue("unixMillis must be positive", result.getLong("unixMillis") > 0)
        val iso = result.getString("iso8601")
        assertTrue("iso8601 must end with Z, got: $iso", iso.endsWith("Z"))
    }

    @Test
    fun `system_info returns android platform and version fields`() {
        val reply = SystemInfoHandler(appVersionName = "9.9.9", appVersionCode = 99).handle(
            HostRequest(1L, 601L, SystemInfoHandler.CAPABILITY, "{}")
        )
        assertTrue("must complete", reply.isComplete())
        val result = JSONObject((reply as HostReply.Complete).resultJson())
        val info = result.getJSONObject("info")
        assertEquals("android", info.getString("platform"))
        assertEquals("9.9.9", info.getString("appVersionName"))
        assertEquals(99, info.getInt("appVersionCode"))
        // On JVM tests Build.VERSION is stubbed; just assert the key exists.
        assertTrue("osVersion must be present", info.has("osVersion"))
    }
}
