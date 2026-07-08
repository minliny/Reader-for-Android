package com.reader.host

import com.reader.android.data.adapter.BackupManifest
import com.reader.android.data.adapter.RestorePolicy
import com.reader.android.data.adapter.RestoreResult
import com.reader.android.data.adapter.BackupRestoreManager
import com.reader.android.data.adapter.WebDavClient
import com.reader.android.data.adapter.WebDavMethod
import com.reader.android.data.adapter.WebDavRequest
import com.reader.android.data.adapter.WebDavResponse
import com.reader.android.data.adapter.FakeWebDavClient
import com.reader.android.data.adapter.RetryPolicy
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

// ════════════════════════════════════════════════════════════════════════════
// P1-6: WebDAV / sync / backup capability handlers.
//
// These handlers close the "AndroidWebDavClient exists but is not wired into
// the Host capability surface" gap. They expose WebDAV file operations
// (connect / upload / download / list / delete / mkdir) + backup/restore
// orchestration through the same `HostRequest → HostAdapter.dispatch →
// HostReply` round-trip used by TTS, source, RSS, etc.
//
// All handlers delegate to a [WebDavClient] (OkHttp-backed in production,
// [FakeWebDavClient] in JVM tests) so they are pure-JVM and testable
// without a network. The [BackupRestoreManager] is injected alongside
// so backup/restore operations share the same client + retry policy.
// ════════════════════════════════════════════════════════════════════════════

/**
 * Bundles the [WebDavClient] + [BackupRestoreManager] that the webdav.*
 * handlers need. Created once in `ReaderCoreClient.buildHostRuntime` and
 * injected into each handler so the handler has no Android/Context dependency.
 *
 * When [client] is null (no credential configured or JVM test without
 * WebDAV wiring), the handlers return a structured "not configured" error
 * so the UI can surface "请先配置 WebDAV 凭据" instead of a crash.
 */
class WebDavContext(
    val client: WebDavClient?,
    val backupRestoreManager: BackupRestoreManager?
)

// ── webdav.connect ───────────────────────────────────────────────────────────

/**
 * `webdav.connect` — tests connectivity to a WebDAV server by issuing a
 * PROPFIND on the root URL. Returns `{connected, statusCode, message}`.
 *
 * This is the "测试网络连通性" button in SyncBackupScreen — it validates
 * that the stored credential + server URL are reachable before the user
 * attempts a backup.
 */
class WebDavConnectHandler(private val ctx: WebDavContext) : CapabilityHandler {
    override fun handle(request: HostRequest): HostReply {
        val params = try { JSONObject(request.paramsJson()) } catch (e: Exception) {
            return HostReply.error(INTERNAL, "invalid params: ${e.message}", false)
        }
        val client = ctx.client
            ?: return HostReply.error(NOT_CONFIGURED, "WebDAV client not configured", false)
        val url = params.optString("url", "")
        if (url.isEmpty()) return HostReply.error(INTERNAL, "url required", false)

        val response = runBlocking {
            client.execute(WebDavRequest(url = url, method = WebDavMethod.PROPFIND))
        }
        val connected = response.statusCode == 207 || response.statusCode == 200
        val result = JSONObject()
            .put("connected", connected)
            .put("statusCode", response.statusCode)
            .put("message", if (connected) "OK" else com.reader.android.data.adapter.WebDavErrorMapper.statusToMessage(response.statusCode))
        return HostReply.complete(result.toString())
    }

    companion object {
        const val CAPABILITY = "webdav.connect"
        private const val INTERNAL = "INTERNAL"
        private const val NOT_CONFIGURED = "NOT_CONFIGURED"
    }
}

// ── webdav.upload ────────────────────────────────────────────────────────────

/**
 * `webdav.upload` — PUTs a file body to a WebDAV URL.
 * Returns `{uploaded, statusCode, path}`.
 */
class WebDavUploadHandler(private val ctx: WebDavContext) : CapabilityHandler {
    override fun handle(request: HostRequest): HostReply {
        val params = try { JSONObject(request.paramsJson()) } catch (e: Exception) {
            return HostReply.error(INTERNAL, "invalid params: ${e.message}", false)
        }
        val client = ctx.client
            ?: return HostReply.error(NOT_CONFIGURED, "WebDAV client not configured", false)
        val url = params.optString("url", "")
        if (url.isEmpty()) return HostReply.error(INTERNAL, "url required", false)
        val body = params.optString("body", "")
        val headers = if (params.has("headers")) {
            val hdrs = params.getJSONObject("headers")
            val map = mutableMapOf<String, String>()
            hdrs.keys().forEach { map[it] = hdrs.getString(it) }
            map
        } else emptyMap()

        val response = runBlocking {
            client.execute(WebDavRequest(url = url, method = WebDavMethod.PUT, body = body, headers = headers))
        }
        val uploaded = response.statusCode in 200..299
        val result = JSONObject()
            .put("uploaded", uploaded)
            .put("statusCode", response.statusCode)
            .put("path", url)
        if (!uploaded) {
            result.put("message", com.reader.android.data.adapter.WebDavErrorMapper.statusToMessage(response.statusCode))
        }
        return HostReply.complete(result.toString())
    }

    companion object {
        const val CAPABILITY = "webdav.upload"
        private const val INTERNAL = "INTERNAL"
        private const val NOT_CONFIGURED = "NOT_CONFIGURED"
    }
}

// ── webdav.download ──────────────────────────────────────────────────────────

/**
 * `webdav.download` — GETs a file from a WebDAV URL.
 * Returns `{downloaded, statusCode, body, path}`.
 */
class WebDavDownloadHandler(private val ctx: WebDavContext) : CapabilityHandler {
    override fun handle(request: HostRequest): HostReply {
        val params = try { JSONObject(request.paramsJson()) } catch (e: Exception) {
            return HostReply.error(INTERNAL, "invalid params: ${e.message}", false)
        }
        val client = ctx.client
            ?: return HostReply.error(NOT_CONFIGURED, "WebDAV client not configured", false)
        val url = params.optString("url", "")
        if (url.isEmpty()) return HostReply.error(INTERNAL, "url required", false)

        val response = runBlocking {
            client.execute(WebDavRequest(url = url, method = WebDavMethod.GET))
        }
        val downloaded = response.statusCode == 200
        val result = JSONObject()
            .put("downloaded", downloaded)
            .put("statusCode", response.statusCode)
            .put("path", url)
            .put("body", response.body ?: "")
        if (!downloaded) {
            result.put("message", com.reader.android.data.adapter.WebDavErrorMapper.statusToMessage(response.statusCode))
        }
        return HostReply.complete(result.toString())
    }

    companion object {
        const val CAPABILITY = "webdav.download"
        private const val INTERNAL = "INTERNAL"
        private const val NOT_CONFIGURED = "NOT_CONFIGURED"
    }
}

// ── webdav.list ──────────────────────────────────────────────────────────────

/**
 * `webdav.list` — PROPFINDs a WebDAV URL to list directory contents.
 * Returns `{entries, statusCode}` where entries is an array of paths.
 *
 * The response body from a PROPFIND is a 207 multi-status XML document.
 * This handler returns the raw body so the caller can parse it; a future
 * enhancement can parse the XML here and return structured entries.
 */
class WebDavListHandler(private val ctx: WebDavContext) : CapabilityHandler {
    override fun handle(request: HostRequest): HostReply {
        val params = try { JSONObject(request.paramsJson()) } catch (e: Exception) {
            return HostReply.error(INTERNAL, "invalid params: ${e.message}", false)
        }
        val client = ctx.client
            ?: return HostReply.error(NOT_CONFIGURED, "WebDAV client not configured", false)
        val url = params.optString("url", "")
        if (url.isEmpty()) return HostReply.error(INTERNAL, "url required", false)

        val response = runBlocking {
            client.execute(WebDavRequest(url = url, method = WebDavMethod.PROPFIND))
        }
        val success = response.statusCode == 207 || response.statusCode == 200
        val result = JSONObject()
            .put("success", success)
            .put("statusCode", response.statusCode)
            .put("body", response.body ?: "")
        return HostReply.complete(result.toString())
    }

    companion object {
        const val CAPABILITY = "webdav.list"
        private const val INTERNAL = "INTERNAL"
        private const val NOT_CONFIGURED = "NOT_CONFIGURED"
    }
}

// ── webdav.delete ────────────────────────────────────────────────────────────

/**
 * `webdav.delete` — DELETEs a resource at a WebDAV URL.
 * Returns `{deleted, statusCode, path}`.
 */
class WebDavDeleteHandler(private val ctx: WebDavContext) : CapabilityHandler {
    override fun handle(request: HostRequest): HostReply {
        val params = try { JSONObject(request.paramsJson()) } catch (e: Exception) {
            return HostReply.error(INTERNAL, "invalid params: ${e.message}", false)
        }
        val client = ctx.client
            ?: return HostReply.error(NOT_CONFIGURED, "WebDAV client not configured", false)
        val url = params.optString("url", "")
        if (url.isEmpty()) return HostReply.error(INTERNAL, "url required", false)

        val response = runBlocking {
            client.execute(WebDavRequest(url = url, method = WebDavMethod.DELETE))
        }
        val deleted = response.statusCode in 200..299
        val result = JSONObject()
            .put("deleted", deleted)
            .put("statusCode", response.statusCode)
            .put("path", url)
        return HostReply.complete(result.toString())
    }

    companion object {
        const val CAPABILITY = "webdav.delete"
        private const val INTERNAL = "INTERNAL"
        private const val NOT_CONFIGURED = "NOT_CONFIGURED"
    }
}

// ── webdav.mkdir ─────────────────────────────────────────────────────────────

/**
 * `webdav.mkdir` — creates a collection (directory) at a WebDAV URL via MKCOL.
 * Returns `{created, statusCode, path}`.
 */
class WebDavMkdirHandler(private val ctx: WebDavContext) : CapabilityHandler {
    override fun handle(request: HostRequest): HostReply {
        val params = try { JSONObject(request.paramsJson()) } catch (e: Exception) {
            return HostReply.error(INTERNAL, "invalid params: ${e.message}", false)
        }
        val client = ctx.client
            ?: return HostReply.error(NOT_CONFIGURED, "WebDAV client not configured", false)
        val url = params.optString("url", "")
        if (url.isEmpty()) return HostReply.error(INTERNAL, "url required", false)

        val response = runBlocking {
            client.execute(WebDavRequest(url = url, method = WebDavMethod.MKCOL))
        }
        val created = response.statusCode in 200..299
        val result = JSONObject()
            .put("created", created)
            .put("statusCode", response.statusCode)
            .put("path", url)
        return HostReply.complete(result.toString())
    }

    companion object {
        const val CAPABILITY = "webdav.mkdir"
        private const val INTERNAL = "INTERNAL"
        private const val NOT_CONFIGURED = "NOT_CONFIGURED"
    }
}

// ── backup.create ────────────────────────────────────────────────────────────

/**
 * `backup.create` — creates a backup by uploading a manifest + entries to
 * the WebDAV server. The caller provides the manifest JSON + entry bodies;
 * this handler orchestrates the PUT operations via [BackupRestoreManager]'s
 * client.
 *
 * Returns `{backed_up, manifest_path, entry_count}`.
 */
class BackupCreateHandler(private val ctx: WebDavContext) : CapabilityHandler {
    override fun handle(request: HostRequest): HostReply {
        val params = try { JSONObject(request.paramsJson()) } catch (e: Exception) {
            return HostReply.error(INTERNAL, "invalid params: ${e.message}", false)
        }
        val client = ctx.client
            ?: return HostReply.error(NOT_CONFIGURED, "WebDAV client not configured", false)
        val manager = ctx.backupRestoreManager
            ?: return HostReply.error(NOT_CONFIGURED, "BackupRestoreManager not configured", false)
        val manifestJson = params.optString("manifest", "")
        if (manifestJson.isEmpty()) return HostReply.error(INTERNAL, "manifest required", false)
        val baseUrl = params.optString("baseUrl", "")
        if (baseUrl.isEmpty()) return HostReply.error(INTERNAL, "baseUrl required", false)

        val manifest = BackupManifest.fromJson(manifestJson)
        val manifestPath = "$baseUrl/manifest.json"

        // Upload each entry's body (if provided) + the manifest itself.
        val entries = params.optJSONArray("entries")
        var uploaded = 0
        if (entries != null) {
            for (i in 0 until entries.length()) {
                val entry = entries.getJSONObject(i)
                val path = entry.optString("path", "")
                val body = entry.optString("body", "")
                if (path.isNotEmpty()) {
                    val resp = runBlocking {
                        client.execute(WebDavRequest(url = path, method = WebDavMethod.PUT, body = body))
                    }
                    if (resp.statusCode in 200..299) uploaded++
                }
            }
        }

        // Upload manifest
        val manifestResp = runBlocking {
            client.execute(WebDavRequest(url = manifestPath, method = WebDavMethod.PUT, body = manifestJson))
        }
        val backedUp = manifestResp.statusCode in 200..299
        val result = JSONObject()
            .put("backed_up", backedUp)
            .put("manifest_path", manifestPath)
            .put("entry_count", uploaded)
            .put("statusCode", manifestResp.statusCode)
        return HostReply.complete(result.toString())
    }

    companion object {
        const val CAPABILITY = "backup.create"
        private const val INTERNAL = "INTERNAL"
        private const val NOT_CONFIGURED = "NOT_CONFIGURED"
    }
}

// ── backup.restore ───────────────────────────────────────────────────────────

/**
 * `backup.restore` — restores from a manifest by downloading entries +
 * validating them. Uses [BackupRestoreManager.validate] + [planRestore].
 *
 * Returns `{restored, skipped, success, message}`.
 */
class BackupRestoreHandler(private val ctx: WebDavContext) : CapabilityHandler {
    override fun handle(request: HostRequest): HostReply {
        val params = try { JSONObject(request.paramsJson()) } catch (e: Exception) {
            return HostReply.error(INTERNAL, "invalid params: ${e.message}", false)
        }
        val manager = ctx.backupRestoreManager
            ?: return HostReply.error(NOT_CONFIGURED, "BackupRestoreManager not configured", false)
        val manifestJson = params.optString("manifest", "")
        if (manifestJson.isEmpty()) return HostReply.error(INTERNAL, "manifest required", false)
        val policyStr = params.optString("policy", "FULL_REPLACE")
        val policy = runCatching { RestorePolicy.valueOf(policyStr) }.getOrDefault(RestorePolicy.FULL_REPLACE)

        val manifest = BackupManifest.fromJson(manifestJson)
        val valid = runBlocking { manager.validate(manifest) }
        if (!valid) {
            val result = manager.result(success = false, error = "manifest validation failed")
            return HostReply.complete(JSONObject()
                .put("success", false)
                .put("restored", 0)
                .put("skipped", 0)
                .put("message", "manifest validation failed")
                .toString())
        }

        val entriesToRestore = manager.planRestore(manifest, policy)
        var restored = 0
        var skipped = 0
        val client = ctx.client
        if (client != null && policy != RestorePolicy.DRY_RUN) {
            entriesToRestore.forEach { entry ->
                val resp = runBlocking {
                    client.execute(WebDavRequest(url = entry.path, method = WebDavMethod.GET))
                }
                if (resp.statusCode == 200) restored++ else skipped++
            }
        }
        val result = manager.result(success = true, restored = restored, skipped = skipped)
        return HostReply.complete(JSONObject()
            .put("success", result.success)
            .put("restored", result.restoredEntries)
            .put("skipped", result.skippedEntries)
            .put("message", result.errorMessage ?: "OK")
            .toString())
    }

    companion object {
        const val CAPABILITY = "backup.restore"
        private const val INTERNAL = "INTERNAL"
        private const val NOT_CONFIGURED = "NOT_CONFIGURED"
    }
}

/**
 * Convenience entry point: registers the full webdav/backup capability set
 * onto a [HostRuntime] chain. Called from `ReaderCoreClient.buildHostRuntime`
 * so production + JVM tests share the same registration code.
 */
fun WebDavContext.registerHandlers(runtime: HostRuntime): HostRuntime = runtime
    .register(WebDavConnectHandler.CAPABILITY, WebDavConnectHandler(this))
    .register(WebDavUploadHandler.CAPABILITY, WebDavUploadHandler(this))
    .register(WebDavDownloadHandler.CAPABILITY, WebDavDownloadHandler(this))
    .register(WebDavListHandler.CAPABILITY, WebDavListHandler(this))
    .register(WebDavDeleteHandler.CAPABILITY, WebDavDeleteHandler(this))
    .register(WebDavMkdirHandler.CAPABILITY, WebDavMkdirHandler(this))
    .register(BackupCreateHandler.CAPABILITY, BackupCreateHandler(this))
    .register(BackupRestoreHandler.CAPABILITY, BackupRestoreHandler(this))

/**
 * Builds a [WebDavContext] backed by [FakeWebDavClient] for JVM tests.
 * Production wires the real [com.reader.android.data.adapter.AndroidWebDavClient]
 * via AppProvider.
 */
fun fakeWebDavContext(): WebDavContext {
    val client = FakeWebDavClient()
    val manager = BackupRestoreManager(client)
    return WebDavContext(client, manager)
}

/**
 * Builds a [WebDavContext] with no client configured (null) — used when
 * the user hasn't set up WebDAV credentials yet. All handlers return
 * NOT_CONFIGURED errors so the UI can prompt "请先配置 WebDAV".
 */
fun unconfiguredWebDavContext(): WebDavContext = WebDavContext(client = null, backupRestoreManager = null)
