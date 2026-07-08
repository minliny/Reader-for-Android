package com.reader.api

import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * JVM proof for the P1-6+ sync.* bridge ([SyncApi]).
 *
 * Verifies that each of the 4 sync.* capabilities is invoked with the correct
 * Core method name + params, and that the Core `result` JSON is parsed into
 * the expected Kotlin data structures. The native Core `.so` is NOT loaded
 * here: a fake [CoreCaller] captures the dispatched method/params and returns
 * canned JSON. The on-device end-to-end path (JNI Core → sync.* planner →
 * SyncPlanExecutor) is covered by instrumented proof.
 *
 * Capability coverage:
 *  - sync.merge            (snapshot + conflicts parsing)
 *  - sync.backup           (package + policy → plan)
 *  - sync.webdav.plan      (baseUrl + auth + requests → HostHttpRequests[])
 *  - sync.backup.retention (config + preservingBackupId + evaluatedAt → pathsToDelete[])
 */
class SyncApiJvmTest {

    /** Captures the last dispatched method/params and returns a canned result. */
    private class FakeCoreCaller : CoreCaller {
        var lastMethod: String? = null
        var lastParams: JSONObject? = null
        var nextResult: JSONObject = JSONObject()
        var callCount = 0

        override suspend fun sendAndAwait(method: String, params: JSONObject): JSONObject {
            lastMethod = method
            lastParams = params
            callCount++
            return nextResult
        }
    }

    @Test
    fun `syncApi_mergeSendsCorrectParamsAndParsesResult`() = runBlocking {
        val fake = FakeCoreCaller().apply {
            nextResult = JSONObject().apply {
                put("snapshot", JSONObject().put("id", "merged-1").put("books", JSONArray()))
                put("conflicts", JSONArray().apply {
                    put(JSONObject().put("path", "/a").put("resolution", "local"))
                    put(JSONObject().put("path", "/b").put("resolution", "remote"))
                })
            }
        }
        val api = SyncApi(fake)
        val local = JSONObject().put("id", "local-1")
        val remote = JSONObject().put("id", "remote-1")

        val result = api.merge(
            local = local,
            remote = remote,
            mergedSnapshotId = "msid",
            mergedDeviceId = "dev1",
            mergedCreatedAt = 123L
        )

        // Method + params dispatched to Core
        assertEquals("sync.merge", fake.lastMethod)
        assertEquals(1, fake.callCount)
        val params = fake.lastParams!!
        assertEquals(local.toString(), params.getJSONObject("local").toString())
        assertEquals(remote.toString(), params.getJSONObject("remote").toString())
        assertEquals("msid", params.getString("mergedSnapshotId"))
        assertEquals("dev1", params.getString("mergedDeviceId"))
        assertEquals(123L, params.getLong("mergedCreatedAt"))
        // Result parsing
        assertEquals("merged-1", result.snapshot.getString("id"))
        assertEquals(2, result.conflicts.size)
        assertEquals("/a", result.conflicts[0].getString("path"))
        assertEquals("remote", result.conflicts[1].getString("resolution"))
    }

    @Test
    fun `syncApi_backupSendsPackageAndPolicyAndReturnsPlan`() = runBlocking {
        val fake = FakeCoreCaller().apply {
            nextResult = JSONObject().apply {
                put("plan", JSONObject().put("strategy", "FULL_REPLACE").put("steps", JSONArray()))
            }
        }
        val api = SyncApi(fake)
        val pkg = JSONObject().put("manifestPath", "/reader/manifest.json")
        val policy = JSONObject().put("type", "FULL_REPLACE")

        val plan = api.backup(packageObj = pkg, policy = policy)

        assertEquals("sync.backup", fake.lastMethod)
        val params = fake.lastParams!!
        assertEquals(pkg.toString(), params.getJSONObject("package").toString())
        assertEquals(policy.toString(), params.getJSONObject("policy").toString())
        assertEquals("FULL_REPLACE", plan.getString("strategy"))
    }

    @Test
    fun `syncApi_webdavPlanSendsBaseUrlAndAuthAndReturnsRequests`() = runBlocking {
        val fake = FakeCoreCaller().apply {
            nextResult = JSONObject().apply {
                put("requests", JSONArray().apply {
                    put(JSONObject().put("method", "PUT").put("url", "https://dav.ex.com/reader/a.txt"))
                    put(JSONObject().put("method", "GET").put("url", "https://dav.ex.com/reader/b.txt"))
                })
            }
        }
        val api = SyncApi(fake)
        val inputRequests = listOf(
            JSONObject().put("kind", "upload").put("path", "/a.txt")
        )

        val requests = api.webdavPlan(
            baseUrl = "https://dav.ex.com",
            auth = "Bearer token",
            requests = inputRequests
        )

        assertEquals("sync.webdav.plan", fake.lastMethod)
        val params = fake.lastParams!!
        assertEquals("https://dav.ex.com", params.getString("baseUrl"))
        assertEquals("Bearer token", params.getString("auth"))
        assertEquals(1, params.getJSONArray("requests").length())
        assertEquals("/a.txt", params.getJSONArray("requests").getJSONObject(0).getString("path"))
        // Parsed output
        assertEquals(2, requests.size)
        assertEquals("PUT", requests[0].getString("method"))
        assertEquals("https://dav.ex.com/reader/a.txt", requests[0].getString("url"))
        assertEquals("GET", requests[1].getString("method"))
    }

    @Test
    fun `syncApi_backupRetentionSendsConfigAndReturnsPathsToDelete`() = runBlocking {
        val fake = FakeCoreCaller().apply {
            nextResult = JSONObject().apply {
                put("plan", JSONObject().apply {
                    put("pathsToDelete", JSONArray().apply {
                        put("/reader/backup/old1.json")
                        put("/reader/backup/old2.json")
                        put("/reader/backup/old3.json")
                    })
                    put("evaluatedAt", 999L)
                })
            }
        }
        val api = SyncApi(fake)
        val config = JSONObject().put("maxBackups", 5)
        val candidates = listOf(JSONObject().put("backupId", "b1"))

        val result = api.backupRetention(
            config = config,
            preservingBackupId = "keep-1",
            evaluatedAt = 999L,
            candidates = candidates
        )

        assertEquals("sync.backup.retention", fake.lastMethod)
        val params = fake.lastParams!!
        assertEquals(config.toString(), params.getJSONObject("config").toString())
        assertEquals("keep-1", params.getString("preservingBackupId"))
        assertEquals(999L, params.getLong("evaluatedAt"))
        assertEquals(1, params.getJSONArray("candidates").length())
        assertEquals("b1", params.getJSONArray("candidates").getJSONObject(0).getString("backupId"))
        // Parsed plan + paths
        assertNotNull(result.plan)
        assertEquals(999L, result.plan.getLong("evaluatedAt"))
        assertEquals(3, result.pathsToDelete.size)
        assertEquals("/reader/backup/old1.json", result.pathsToDelete[0])
        assertEquals("/reader/backup/old2.json", result.pathsToDelete[1])
        assertEquals("/reader/backup/old3.json", result.pathsToDelete[2])
    }
}
