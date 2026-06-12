package com.reader.android.data.policy

/**
 * Test-only allowlist for bridge-backed java bridge API execution.
 *
 * Contains 3 entries matching known bridge-backed book sources:
 *   onehu_xyz    → java.ajax
 *   qidian_com   → java.refreshTocUrl
 *   zhenhunxiaoshuo → java.getElements
 *
 * All identifiers are privacy-safe sanitized names (no raw URLs, cookies, tokens, or headers).
 */
class BridgeBackedTestOnlyAllowlist {

    /** internal immutable map: sanitizedSourceId → requiredBridgeAPI */
    private val entries: Map<String, String> = mapOf(
        "onehu_xyz" to "java.ajax",
        "qidian_com" to "java.refreshTocUrl",
        "zhenhunxiaoshuo" to "java.getElements"
    )

    /** Returns true if [sourceId] is in the test-only allowlist. */
    fun contains(sourceId: String): Boolean = entries.containsKey(sourceId)

    /** Returns the required bridge API for [sourceId], or null if not allowlisted. */
    fun requiredBridgeAPI(sourceId: String): String? = entries[sourceId]

    /** All allowlisted source IDs (privacy-safe sanitized identifiers). */
    fun allSourceIds(): Set<String> = entries.keys

    /** Count of allowlisted entries. */
    fun count(): Int = entries.size
}