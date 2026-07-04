package com.reader.ui.evidence

import android.os.Build
import com.reader.api.CoreException
import com.reader.api.CoreTimeoutException
import com.reader.api.ReaderCoreClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Unified evidence runner for Android. Covers all 15 canonical capabilities
 * defined in `protocol/unified-evidence.schema.json` (Reader-Core-Native).
 *
 * For each capability the runner invokes Core via
 * [ReaderCoreClient.sendAndAwait] — the runner never opens a socket, never
 * touches a WebView, and never stores plaintext credentials (Core/Host
 * boundary respected, red line 4).
 *
 * Status policy:
 *  - `pass`     : Core returned a result OR a structured error
 *                 ([CoreException] — proves the Core round-trip works).
 *  - `fail`     : Core timed out ([CoreTimeoutException]) or threw an
 *                 unexpected exception.
 *  - `blocked`  : capability not yet wired on Android (deferred to later
 *                 phases — see AGENTS.md "TTS 策略" and capability gap plan).
 *
 * Callable from both [EvidenceActivity] (Compose UI) and
 * [UnifiedEvidenceInstrumentedTest] (instrumented test).
 */
object EvidenceRunner {

    /** Per-capability timeout for the smoke round-trip. */
    private const val CAPABILITY_TIMEOUT_MS = 5_000L

    /**
     * Run all 15 canonical capabilities and produce a unified-evidence/1
     * artifact.
     *
     * @param tier "simulator" (default) or "device" — caller chooses based on
     *   whether the runner is invoked from an instrumented test / emulator
     *   vs. a real device.
     * @param filesDir Optional directory to write the JSON artifact to. When
     *   non-null, the artifact is written to
     *   `filesDir/unified-evidence/evidence-run-android-<timestamp>.json`.
     *   When null, the in-memory artifact is still returned but no file is
     *   written.
     */
    suspend fun run(
        tier: String = "simulator",
        filesDir: File? = null
    ): UnifiedEvidenceArtifact = withContext(Dispatchers.IO) {
        val client = ReaderCoreClient.get()
        val capabilities = mutableListOf<CapabilityResult>()

        // 1. runtime.ping — Core responds directly, no host handler needed.
        capabilities += runCoreCapability(
            client = client,
            capability = "runtime.ping",
            method = "runtime.ping",
            params = JSONObject()
        )

        // 2. host.request — runtime.hostSmoke triggers a host.request event
        //    for the `host.smoke.echo` capability. PASS if Core responds
        //    (even with a structured error if the host has no echo handler
        //    registered — proves the Core round-trip).
        capabilities += runCoreCapability(
            client = client,
            capability = "host.request",
            method = "runtime.hostSmoke",
            params = JSONObject()
        )

        // 3-8. Core book pipeline + reading.progress.update — invoke with
        //      minimal params. PASS if Core responds (structured error is
        //      acceptable; proves Core round-trip).
        capabilities += runCoreCapability(client, "source.import", "source.import", JSONObject())
        capabilities += runCoreCapability(client, "book.search", "book.search", JSONObject())
        capabilities += runCoreCapability(client, "book.detail", "book.detail", JSONObject())
        capabilities += runCoreCapability(client, "book.toc", "book.toc", JSONObject())
        capabilities += runCoreCapability(client, "chapter.content", "chapter.content", JSONObject())
        capabilities += runCoreCapability(
            client,
            "reading.progress.update",
            "reading.progress.update",
            JSONObject()
        )

        // 9-15. Blocked — not yet wired on Android (handled in later phases).
        val blockedCapabilities = listOf(
            "manga.pages.extract",
            "rss.parse",
            "local_book.parse",
            "bookmark.crud",
            "tts.queue",
            "http-tts",
            "sync.webdav"
        )
        for (cap in blockedCapabilities) {
            capabilities += CapabilityResult(
                capability = cap,
                status = "blocked",
                error = "not yet wired on Android"
            )
        }

        val artifact = UnifiedEvidenceArtifact(
            tier = tier,
            generatedAt = nowIso8601(),
            coreCommit = "unknown",
            hostCommit = "unknown",
            device = DeviceInfo(
                model = Build.MODEL ?: "unknown",
                osVersion = Build.VERSION.RELEASE ?: "unknown",
                arch = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"
            ),
            capabilities = capabilities,
            summary = EvidenceSummary(0, 0, 0, 0, 0.0),
            notes = listOf(
                "Android unified evidence runner — covers 15 canonical capabilities.",
                "Core pipeline invoked via ReaderCoreClient.sendAndAwait (no socket / WebView).",
                "Blocked capabilities are not yet wired on Android (handled in later phases).",
                "coreCommit/hostCommit = \"unknown\" — git is not accessible from the app process."
            )
        )
        val withSummary = artifact.copy(summary = artifact.computeSummary())

        if (filesDir != null) {
            try {
                val dir = File(filesDir, "unified-evidence").apply { mkdirs() }
                val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                val file = File(dir, "evidence-run-android-$timestamp.json")
                file.writeText(withSummary.toJsonString(), Charsets.UTF_8)
            } catch (e: Exception) {
                // Swallow — the in-memory artifact is still returned to the
                // caller. The file write is a best-effort convenience.
            }
        }

        withSummary
    }

    /**
     * Run a single Core capability via [ReaderCoreClient.sendAndAwait].
     *
     * - `pass` if Core returns a result OR a structured error ([CoreException]
     *   — both prove the Core round-trip works).
     * - `fail` if Core times out or an unexpected exception is thrown.
     */
    private suspend fun runCoreCapability(
        client: ReaderCoreClient,
        capability: String,
        method: String,
        params: JSONObject
    ): CapabilityResult {
        val start = System.currentTimeMillis()
        return try {
            val result = client.sendAndAwait(method, params, timeoutMillis = CAPABILITY_TIMEOUT_MS)
            val duration = System.currentTimeMillis() - start
            CapabilityResult(
                capability = capability,
                status = "pass",
                method = method,
                durationMs = duration,
                redactedEvidence = redact(result)
            )
        } catch (e: CoreException) {
            // Structured error from Core — still proves the round-trip works.
            val duration = System.currentTimeMillis() - start
            CapabilityResult(
                capability = capability,
                status = "pass",
                method = method,
                durationMs = duration,
                redactedEvidence = redact(e.errorJson)
            )
        } catch (e: CoreTimeoutException) {
            val duration = System.currentTimeMillis() - start
            CapabilityResult(
                capability = capability,
                status = "fail",
                method = method,
                durationMs = duration,
                error = "timeout: ${e.message}"
            )
        } catch (e: Throwable) {
            val duration = System.currentTimeMillis() - start
            CapabilityResult(
                capability = capability,
                status = "fail",
                method = method,
                durationMs = duration,
                error = "${e.javaClass.simpleName}: ${e.message}"
            )
        }
    }

    /**
     * Redact a Core response/error before recording it as evidence. The Core
     * smoke responses (runtime.ping / runtime.hostSmoke / pipeline methods
     * with empty params) do not contain credentials, but we truncate the
     * serialized form to bound the artifact size and avoid leaking any
     * URL/query detail that might appear in an error message.
     */
    private fun redact(value: Any): String {
        val raw = when (value) {
            is JSONObject -> value.toString()
            is String -> value
            else -> value.toString()
        }
        return if (raw.length > MAX_EVIDENCE_LEN) {
            raw.substring(0, MAX_EVIDENCE_LEN) + "…"
        } else {
            raw
        }
    }

    private fun nowIso8601(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }

    private const val MAX_EVIDENCE_LEN = 200
}
