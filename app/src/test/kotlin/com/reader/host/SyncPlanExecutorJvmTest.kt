package com.reader.host

import com.reader.android.data.adapter.BackupRestoreManager
import com.reader.android.data.adapter.FakeWebDavClient
import com.reader.android.data.adapter.WebDavMethod
import com.reader.android.data.adapter.WebDavRequest
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * JVM proof for the P1-6+ sync.* bridge executor ([SyncPlanExecutor]).
 *
 * Wires the real webdav.* Host capability handlers (the same ones registered
 * in production via [ReaderCoreClient.buildHostRuntime]) over a
 * [FakeWebDavClient] (in-memory store), then dispatches sync.webdav.plan /
 * sync.backup.retention output through [SyncPlanExecutor].
 *
 * This proves the planner → executor closure: Core's descriptors are
 * translated into webdav.* capability dispatches with the correct HTTP method
 * → capability mapping, and the FakeWebDavClient side effects (PUT stores,
 * DELETE removes) confirm the handlers actually ran.
 */
class SyncPlanExecutorJvmTest {

    private lateinit var client: FakeWebDavClient
    private lateinit var executor: SyncPlanExecutor
    private lateinit var runtime: HostRuntime

    @Before
    fun setUp() {
        client = FakeWebDavClient()
        val ctx = WebDavContext(client, BackupRestoreManager(client))
        runtime = ctx.registerHandlers(HostRuntime.over(stubTransport()))
        executor = SyncPlanExecutor(runtime.adapter())
    }

    @After
    fun tearDown() {
        runtime.stop()
    }

    private fun stubTransport(): HostTransport = object : HostTransport {
        override fun pollEventJson(timeoutMillis: Long): String? = null
        override fun sendCommand(commandJson: String) {}
    }

    private fun planRequest(
        method: String,
        url: String,
        body: String = "",
        headers: JSONObject? = null
    ): JSONObject = JSONObject().apply {
        put("method", method)
        put("url", url)
        if (body.isNotEmpty()) put("body", body)
        if (headers != null) put("headers", headers)
    }

    private fun get(path: String) = runBlocking {
        client.execute(WebDavRequest(path, WebDavMethod.GET))
    }

    // ── executeWebDavPlan: HTTP method → capability mapping ──────────────────

    @Test
    fun `executeWebDavPlan_dispatchesPutToWebdavUpload`() {
        val results = executor.executeWebDavPlan(
            listOf(planRequest("PUT", "https://dav.ex.com/reader/a.txt", "body-1"))
        )
        assertEquals(1, results.size)
        val r = results[0]
        assertEquals("PUT", r.method)
        assertTrue(r.success)
        assertTrue(r.detail, r.detail.contains("201"))
        assertEquals(201, r.statusCode)
        // Side effect: PUT stored the body via webdav.upload handler
        val stored = get("https://dav.ex.com/reader/a.txt")
        assertEquals(200, stored.statusCode)
        assertEquals("body-1", stored.body)
    }

    @Test
    fun `executeWebDavPlan_dispatchesGetToWebdavDownload`() {
        client.putContent("https://dav.ex.com/reader/a.txt", "hello")
        val results = executor.executeWebDavPlan(
            listOf(planRequest("GET", "https://dav.ex.com/reader/a.txt"))
        )
        assertEquals(1, results.size)
        assertTrue(results[0].success)
        assertTrue(results[0].detail, results[0].detail.contains("200"))
        assertEquals("GET", results[0].method)
    }

    @Test
    fun `executeWebDavPlan_dispatchesDeleteToWebdavDelete`() {
        client.putContent("https://dav.ex.com/reader/a.txt", "hello")
        val results = executor.executeWebDavPlan(
            listOf(planRequest("DELETE", "https://dav.ex.com/reader/a.txt"))
        )
        assertEquals(1, results.size)
        assertTrue(results[0].success)
        assertTrue(results[0].detail, results[0].detail.contains("204"))
        // Side effect: file removed via webdav.delete handler
        assertEquals(404, get("https://dav.ex.com/reader/a.txt").statusCode)
    }

    @Test
    fun `executeWebDavPlan_dispatchesMkcolToWebdavMkdir`() {
        val results = executor.executeWebDavPlan(
            listOf(planRequest("MKCOL", "https://dav.ex.com/reader/newdir/"))
        )
        assertEquals(1, results.size)
        assertTrue(results[0].success)
        assertTrue(results[0].detail, results[0].detail.contains("201"))
        assertEquals("MKCOL", results[0].method)
    }

    @Test
    fun `executeWebDavPlan_dispatchesPropfindToWebdavList`() {
        val results = executor.executeWebDavPlan(
            listOf(planRequest("PROPFIND", "https://dav.ex.com/reader/"))
        )
        assertEquals(1, results.size)
        assertTrue(results[0].success)
        assertTrue(results[0].detail, results[0].detail.contains("207"))
        assertEquals("PROPFIND", results[0].method)
    }

    @Test
    fun `executeWebDavPlan_returnsUnsupportedForUnknownMethod`() {
        val results = executor.executeWebDavPlan(
            listOf(planRequest("POST", "https://dav.ex.com/reader/x"))
        )
        assertEquals(1, results.size)
        val r = results[0]
        assertFalse(r.success)
        assertTrue(r.detail, r.detail.contains("unsupported method"))
        assertTrue(r.detail, r.detail.contains("POST"))
        assertEquals("POST", r.method)
    }

    // ── executeRetention: sync.backup.retention output ───────────────────────

    @Test
    fun `executeRetention_deletesAllPathsViaWebdavDelete`() {
        client.putContent("https://dav.ex.com/reader/b1.json", "1")
        client.putContent("https://dav.ex.com/reader/b2.json", "2")
        client.putContent("https://dav.ex.com/reader/b3.json", "3")
        val paths = listOf(
            "https://dav.ex.com/reader/b1.json",
            "https://dav.ex.com/reader/b2.json",
            "https://dav.ex.com/reader/b3.json"
        )

        val results = executor.executeRetention(paths)

        assertEquals(3, results.size)
        assertTrue(results.all { it.success })
        assertTrue(results.all { it.method == "DELETE" })
        // All three paths removed from the fake server
        paths.forEach { p ->
            assertEquals(404, get(p).statusCode)
        }
    }

    @Test
    fun `executeRetention_returnsResultsForEachPath`() {
        client.putContent("https://dav.ex.com/reader/b1.json", "1")
        val paths = listOf(
            "https://dav.ex.com/reader/b1.json",
            "https://dav.ex.com/reader/b2.json" // never PUT; FakeWebDavClient still returns 204 on DELETE
        )

        val results = executor.executeRetention(paths)

        assertEquals(2, results.size)
        assertEquals("https://dav.ex.com/reader/b1.json", results[0].url)
        assertEquals("https://dav.ex.com/reader/b2.json", results[1].url)
        assertTrue(results[0].success)
        assertEquals("HTTP 204", results[0].detail)
        assertEquals(204, results[0].statusCode)
        assertTrue(results[1].success)
        assertEquals("HTTP 204", results[1].detail)
        assertEquals(204, results[1].statusCode)
        assertEquals("DELETE", results[0].method)
        assertEquals("DELETE", results[1].method)
    }

    // ── 业务失败语义：reply.isComplete() 不等于业务成功 ──────────────────────
    // WebDAV handler 在 4xx/5xx 时仍返回 HostReply.complete(...)，但业务布尔字段
    // (uploaded/downloaded/deleted/created/success) 为 false。executor 必须据此
    // 报告 success=false，而不是仅凭 transport 完成就标记成功。

    @Test
    fun `executeWebDavPlan_reportsFailureWhenUploadReturns500`() {
        val url = "https://dav.ex.com/reader/fail-upload.txt"
        client.forceStatusCode(url, 500)
        val results = executor.executeWebDavPlan(
            listOf(planRequest("PUT", url, "body"))
        )
        assertEquals(1, results.size)
        val r = results[0]
        assertFalse(r.success)
        assertEquals(500, r.statusCode)
        assertTrue(r.detail, r.detail.contains("500"))
    }

    @Test
    fun `executeWebDavPlan_reportsFailureWhenDownloadReturns404`() {
        val url = "https://dav.ex.com/reader/missing.txt"
        client.forceStatusCode(url, 404)
        val results = executor.executeWebDavPlan(
            listOf(planRequest("GET", url))
        )
        assertEquals(1, results.size)
        val r = results[0]
        assertFalse(r.success)
        assertEquals(404, r.statusCode)
        assertTrue(r.detail, r.detail.contains("404"))
    }

    @Test
    fun `executeWebDavPlan_reportsFailureWhenDeleteReturns500`() {
        val url = "https://dav.ex.com/reader/fail-delete.txt"
        client.putContent(url, "x")
        client.forceStatusCode(url, 500)
        val results = executor.executeWebDavPlan(
            listOf(planRequest("DELETE", url))
        )
        assertEquals(1, results.size)
        val r = results[0]
        assertFalse(r.success)
        assertEquals(500, r.statusCode)
        assertTrue(r.detail, r.detail.contains("500"))
    }

    @Test
    fun `executeWebDavPlan_reportsFailureWhenMkcolReturns500`() {
        val url = "https://dav.ex.com/reader/fail-dir/"
        client.forceStatusCode(url, 500)
        val results = executor.executeWebDavPlan(
            listOf(planRequest("MKCOL", url))
        )
        assertEquals(1, results.size)
        val r = results[0]
        assertFalse(r.success)
        assertEquals(500, r.statusCode)
        assertTrue(r.detail, r.detail.contains("500"))
    }

    @Test
    fun `executeWebDavPlan_reportsFailureWhenPropfindReturns500`() {
        val url = "https://dav.ex.com/reader/fail-list/"
        client.forceStatusCode(url, 500)
        val results = executor.executeWebDavPlan(
            listOf(planRequest("PROPFIND", url))
        )
        assertEquals(1, results.size)
        val r = results[0]
        assertFalse(r.success)
        assertEquals(500, r.statusCode)
        assertTrue(r.detail, r.detail.contains("500"))
    }

    @Test
    fun `executeRetention_reportsFailureWhenDeleteReturns500`() {
        val url = "https://dav.ex.com/reader/retention-fail.json"
        client.putContent(url, "1")
        client.forceStatusCode(url, 500)
        val results = executor.executeRetention(listOf(url))
        assertEquals(1, results.size)
        val r = results[0]
        assertFalse(r.success)
        assertEquals(500, r.statusCode)
        assertTrue(r.detail, r.detail.contains("500"))
    }
}
