package com.reader.ui.evidence

import org.json.JSONArray
import org.json.JSONObject

/**
 * Unified evidence artifact (unified-evidence/1) — Kotlin mirror of the schema
 * at `protocol/unified-evidence.schema.json` in Reader-Core-Native.
 *
 * Every platform (iOS / Android / HarmonyOS / CLI) emits this single schema so
 * `reports/tooling/` can compare parity. The artifact MUST include all 15
 * canonical capabilities listed in [CANONICAL_CAPABILITIES].
 *
 * Serialization uses the Android-built-in `org.json` API to avoid adding the
 * kotlinx.serialization plugin (not present in `app/build.gradle.kts`).
 *
 * Status enum (kept as a plain String to stay serialization-friendly):
 *   - "pass"     : capability executed successfully OR Core returned a
 *                  structured error (proves the Core round-trip works).
 *   - "fail"     : capability timed out or threw an unexpected exception.
 *   - "skipped"  : capability intentionally not run for this run.
 *   - "blocked"  : capability not yet wired on this platform.
 */
data class UnifiedEvidenceArtifact(
    val schemaVersion: String = SCHEMA_VERSION,
    val platform: String = "android",
    val tier: String,
    val generatedAt: String,
    val coreCommit: String,
    val hostCommit: String,
    val device: DeviceInfo? = null,
    val capabilities: List<CapabilityResult>,
    val summary: EvidenceSummary,
    val hostRequestLoop: HostRequestLoopEvidence? = null,
    val notes: List<String> = emptyList()
) {

    /**
     * Recompute the [EvidenceSummary] from the current [capabilities] list.
     * Per the schema, "skipped" total includes both "skipped" and "blocked"
     * statuses (the validator counts both as skipped).
     */
    fun computeSummary(): EvidenceSummary {
        val total = capabilities.size
        val passed = capabilities.count { it.status == "pass" }
        val failed = capabilities.count { it.status == "fail" }
        val skipped = capabilities.count {
            it.status == "skipped" || it.status == "blocked"
        }
        val passRate = if (total == 0) 0.0 else passed.toDouble() / total.toDouble()
        return EvidenceSummary(
            total = total,
            passed = passed,
            failed = failed,
            skipped = skipped,
            passRate = passRate
        )
    }

    fun toJson(): JSONObject {
        val json = JSONObject()
        json.put("schemaVersion", schemaVersion)
        json.put("platform", platform)
        json.put("tier", tier)
        json.put("generatedAt", generatedAt)
        json.put("coreCommit", coreCommit)
        json.put("hostCommit", hostCommit)
        device?.let { json.put("device", it.toJson()) }
        val capsArray = JSONArray()
        capabilities.forEach { capsArray.put(it.toJson()) }
        json.put("capabilities", capsArray)
        json.put("summary", summary.toJson())
        hostRequestLoop?.let { json.put("hostRequestLoop", it.toJson()) }
        val notesArray = JSONArray()
        notes.forEach { notesArray.put(it) }
        json.put("notes", notesArray)
        return json
    }

    fun toJsonString(): String = toJson().toString(2)

    companion object {
        const val SCHEMA_VERSION = "unified-evidence/1"

        /**
         * The 15 canonical capabilities every unified-evidence/1 artifact MUST
         * include (in any order). Mirrors the `capability` enum in the JSON
         * schema and the Python validator's `CANONICAL_CAPABILITIES` set.
         */
        val CANONICAL_CAPABILITIES: List<String> = listOf(
            "source.import",
            "book.search",
            "book.detail",
            "book.toc",
            "chapter.content",
            "manga.pages.extract",
            "rss.parse",
            "local_book.parse",
            "reading.progress.update",
            "bookmark.crud",
            "tts.queue",
            "http-tts",
            "sync.webdav",
            "runtime.ping",
            "host.request"
        )
    }
}

data class DeviceInfo(
    val model: String,
    val osVersion: String,
    val arch: String
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("model", model)
        put("osVersion", osVersion)
        put("arch", arch)
    }
}

data class CapabilityResult(
    val capability: String,
    val status: String,
    val method: String? = null,
    val durationMs: Long? = null,
    val redactedEvidence: String? = null,
    val error: String? = null,
    val hostRequests: List<HostRequestRecord> = emptyList()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("capability", capability)
        put("status", status)
        method?.let { put("method", it) }
        durationMs?.let { put("durationMs", it) }
        redactedEvidence?.let { put("redactedEvidence", it) }
        error?.let { put("error", it) }
        if (hostRequests.isNotEmpty()) {
            val arr = JSONArray()
            hostRequests.forEach { arr.put(it.toJson()) }
            put("hostRequests", arr)
        }
    }
}

data class HostRequestRecord(
    val capability: String,
    val operationId: Long,
    val completed: Boolean
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("capability", capability)
        put("operationId", operationId)
        put("completed", completed)
    }
}

data class EvidenceSummary(
    val total: Int,
    val passed: Int,
    val failed: Int,
    val skipped: Int,
    val passRate: Double
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("total", total)
        put("passed", passed)
        put("failed", failed)
        put("skipped", skipped)
        put("passRate", passRate)
    }
}

data class HostRequestLoopEvidence(
    val requestId: Long,
    val capability: String,
    val operationId: Long,
    val resultBookCount: Int,
    val durationMs: Long
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("requestId", requestId)
        put("capability", capability)
        put("operationId", operationId)
        put("resultBookCount", resultBookCount)
        put("durationMs", durationMs)
    }
}
