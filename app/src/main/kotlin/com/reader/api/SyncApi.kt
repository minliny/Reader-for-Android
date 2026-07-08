package com.reader.api

import org.json.JSONArray
import org.json.JSONObject

/**
 * P1-6+: Core sync.* bridge. All 4 sync capabilities are pure planners —
 * Core produces descriptors/plans, Android executes them via existing
 * webdav.* and backup.* Host capability handlers (see [com.reader.host.SyncPlanExecutor]).
 *
 * [SyncApi] only talks to Core (it never opens sockets): the returned
 * plans/descriptors are executed by [com.reader.host.SyncPlanExecutor].
 *
 * The [client] seam is a [CoreCaller] functional interface so JVM tests inject
 * a fake without loading the native Core `.so`. Production code uses
 * [CoreCaller.default], which delegates to the singleton [ReaderCoreClient] —
 * the same `sendAndAwait` pattern [BookApi]/[SourceApi] use.
 */
class SyncApi(private val client: CoreCaller = CoreCaller.default) {

    /** sync.merge: merge local + remote snapshots. Returns {snapshot, conflicts[]}. */
    suspend fun merge(
        local: JSONObject,
        remote: JSONObject,
        mergedSnapshotId: String = "",
        mergedDeviceId: String = "",
        mergedCreatedAt: Long = 0
    ): SyncMergeResult {
        val params = JSONObject().apply {
            put("local", local)
            put("remote", remote)
            if (mergedSnapshotId.isNotEmpty()) put("mergedSnapshotId", mergedSnapshotId)
            if (mergedDeviceId.isNotEmpty()) put("mergedDeviceId", mergedDeviceId)
            if (mergedCreatedAt > 0) put("mergedCreatedAt", mergedCreatedAt)
        }
        val result = client.sendAndAwait("sync.merge", params)
        return SyncMergeResult(
            snapshot = result.optJSONObject("snapshot") ?: JSONObject(),
            conflicts = result.optJSONArray("conflicts")?.let { arr ->
                (0 until arr.length()).map { arr.getJSONObject(it) }
            } ?: emptyList()
        )
    }

    /** sync.backup: plan backup restore from manifest + policy. Returns {plan}. */
    suspend fun backup(packageObj: JSONObject, policy: JSONObject): JSONObject {
        val params = JSONObject().apply {
            put("package", packageObj)
            put("policy", policy)
        }
        return client.sendAndAwait("sync.backup", params).getJSONObject("plan")
    }

    /** sync.webdav.plan: translate WebDavRequests into HostHttpRequests. */
    suspend fun webdavPlan(
        baseUrl: String,
        auth: String? = null,
        requests: List<JSONObject> = emptyList()
    ): List<JSONObject> {
        val params = JSONObject().apply {
            put("baseUrl", baseUrl)
            if (auth != null) put("auth", auth)
            if (requests.isNotEmpty()) {
                put("requests", JSONArray().apply { requests.forEach { put(it) } })
            }
        }
        val result = client.sendAndAwait("sync.webdav.plan", params)
        val arr = result.optJSONArray("requests") ?: JSONArray()
        return (0 until arr.length()).map { arr.getJSONObject(it) }
    }

    /** sync.backup.retention: plan backup retention deletes. */
    suspend fun backupRetention(
        config: JSONObject,
        preservingBackupId: String,
        evaluatedAt: Long,
        candidates: List<JSONObject> = emptyList()
    ): SyncRetentionResult {
        val params = JSONObject().apply {
            put("config", config)
            put("preservingBackupId", preservingBackupId)
            put("evaluatedAt", evaluatedAt)
            if (candidates.isNotEmpty()) {
                put("candidates", JSONArray().apply { candidates.forEach { put(it) } })
            }
        }
        val result = client.sendAndAwait("sync.backup.retention", params)
        val plan = result.optJSONObject("plan") ?: JSONObject()
        val pathsToDelete = plan.optJSONArray("pathsToDelete")?.let { arr ->
            (0 until arr.length()).map { arr.getString(it) }
        } ?: emptyList()
        return SyncRetentionResult(plan = plan, pathsToDelete = pathsToDelete)
    }
}

/**
 * Functional seam over [ReaderCoreClient.sendAndAwait]. Production code uses
 * [default] (delegates to the singleton [ReaderCoreClient]); JVM tests inject
 * a fake that returns canned JSON without loading the native Core `.so`.
 *
 * Mirrors the [BookApi]/[SourceApi] pattern of taking a client and calling
 * `client.sendAndAwait(...)` — but as an interface so the seam is mockable
 * ([ReaderCoreClient] itself is a final singleton that loads JNI on init).
 */
fun interface CoreCaller {
    suspend fun sendAndAwait(method: String, params: JSONObject): JSONObject

    companion object {
        val default: CoreCaller = CoreCaller { method, params ->
            ReaderCoreClient.get().sendAndAwait(method, params)
        }
    }
}

data class SyncMergeResult(
    val snapshot: JSONObject,
    val conflicts: List<JSONObject>
)

data class SyncRetentionResult(
    val plan: JSONObject,
    val pathsToDelete: List<String>
)
