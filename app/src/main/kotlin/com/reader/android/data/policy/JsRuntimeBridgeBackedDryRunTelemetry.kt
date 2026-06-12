package com.reader.android.data.policy

/**
 * Privacy-safe dry-run telemetry recorder for bridge-backed policy decisions.
 *
 * Records decision counts and aggregated metadata.
 * Does NOT store: raw source URLs, cookies, session tokens, request headers, or response bodies.
 * All source references use privacy-safe sanitized identifiers.
 */
class BridgeBackedDryRunTelemetry {

    // ── Decision Counters ──

    private var totalDecisions: Int = 0
    private var allowCount: Int = 0
    private var denyCount: Int = 0

    private val denyReasonCounts = mutableMapOf<String, Int>()
    private val apiRequestCounts = mutableMapOf<String, Int>()
    private val sourceRequestCounts = mutableMapOf<String, Int>()

    // ── Audit Trail (privacy-safe, aggregated only) ──

    private val auditEntries = mutableListOf<AuditEntry>()

    data class AuditEntry(
        val decision: String,
        val api: String,
        val sourceId: String,       // privacy-safe sanitized identifier
        val reason: String? = null,   // null for ALLOW entries
        val permitGenerated: Boolean = false
    )

    // ── Recording ──

    fun recordAllow(sourceId: String, api: String) {
        totalDecisions++
        allowCount++
        sourceRequestCounts[sourceId] = (sourceRequestCounts[sourceId] ?: 0) + 1
        apiRequestCounts[api] = (apiRequestCounts[api] ?: 0) + 1
    }

    fun recordDeny(sourceId: String, api: String, reason: String) {
        totalDecisions++
        denyCount++
        denyReasonCounts[reason] = (denyReasonCounts[reason] ?: 0) + 1
        sourceRequestCounts[sourceId] = (sourceRequestCounts[sourceId] ?: 0) + 1
        apiRequestCounts[api] = (apiRequestCounts[api] ?: 0) + 1
    }

    fun recordPermitGenerated(sourceId: String, api: String) {
        auditEntries.add(
            AuditEntry(
                decision = "ALLOW",
                api = api,
                sourceId = sourceId,
                reason = null,
                permitGenerated = true
            )
        )
    }

    fun recordPermitDenied(sourceId: String, api: String, reason: String) {
        auditEntries.add(
            AuditEntry(
                decision = "DENY",
                api = api,
                sourceId = sourceId,
                reason = reason,
                permitGenerated = false
            )
        )
    }

    // ── Query ──

    fun totalDecisionsMade(): Int = totalDecisions
    fun allowDecisions(): Int = allowCount
    fun denyDecisions(): Int = denyCount
    fun denyReasonCounts(): Map<String, Int> = denyReasonCounts.toMap()
    fun apiRequestCounts(): Map<String, Int> = apiRequestCounts.toMap()
    fun sourceRequestCounts(): Map<String, Int> = sourceRequestCounts.toMap()
    fun auditEntries(): List<AuditEntry> = auditEntries.toList()

    /** Count of permit-generating decisions (subset of allowCount). */
    fun permitGeneratedCount(): Int = auditEntries.count { it.permitGenerated }

    /** Count of permit-denied decisions. */
    fun permitDeniedCount(): Int = auditEntries.count { !it.permitGenerated }

    /** Reset all state. For test isolation. */
    fun reset() {
        totalDecisions = 0
        allowCount = 0
        denyCount = 0
        denyReasonCounts.clear()
        apiRequestCounts.clear()
        sourceRequestCounts.clear()
        auditEntries.clear()
    }
}