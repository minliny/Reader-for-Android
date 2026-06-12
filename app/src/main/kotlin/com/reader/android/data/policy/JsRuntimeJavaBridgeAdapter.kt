package com.reader.android.data.policy

/**
 * Known java bridge API identifiers.
 */
enum class JavaBridgeAPI(val apiName: String) {
    GET_ELEMENTS("java.getElements"),
    AJAX("java.ajax"),
    REFRESH_TOC_URL("java.refreshTocUrl");

    companion object {
        fun fromName(name: String): JavaBridgeAPI? =
            entries.firstOrNull { it.apiName == name }

        fun isValid(name: String): Boolean =
            entries.any { it.apiName == name }
    }
}

/**
 * Denial reasons specific to the Java Bridge Adapter enforcement boundary.
 */
sealed class BridgeAdapterDenial {
    /** No permit provided when calling the adapter. */
    data object DENY_POLICY_PERMIT_REQUIRED : BridgeAdapterDenial()

    /** The permit's API does not match the API being called. */
    data class DENY_POLICY_PERMIT_API_MISMATCH(val expectedApi: String, val actualApi: String) : BridgeAdapterDenial()

    /** The permit's sourceId does not match the calling sourceId. */
    data class DENY_POLICY_PERMIT_SOURCE_MISMATCH(val permitSourceId: String, val callSourceId: String) : BridgeAdapterDenial()
}

/**
 * Guarded java bridge adapter that enforces permit/evidence at its boundary.
 *
 * Every java bridge API call requires a valid [BridgeBackedJavaExecutionPermit].
 * The adapter validates:
 * 1. Permit must not be null (DENY_POLICY_PERMIT_REQUIRED)
 * 2. Permit's requiredBridgeAPI must match the called API (DENY_POLICY_PERMIT_API_MISMATCH)
 * 3. Permit's sourceId must match the calling sourceId (DENY_POLICY_PERMIT_SOURCE_MISMATCH)
 *
 * All denials are recorded via [BridgeBackedDryRunTelemetry].
 */
class JavaBridgeAdapter(
    private val telemetry: BridgeBackedDryRunTelemetry
) {

    /**
     * Execute java.getElements for [sourceId], guarded by [permit].
     *
     * Returns a fixture result if permit is valid.
     */
    fun getElements(
        permit: BridgeBackedJavaExecutionPermit?,
        sourceId: String
    ): Result<List<String>> {
        val denial = validatePermit(permit, sourceId, "java.getElements") ?: run {
            return Result.success(listOf("element1", "element2", "element3"))
        }
        return handleDenial(sourceId, "java.getElements", denial)
    }

    /**
     * Execute java.ajax for [sourceId], guarded by [permit].
     *
     * Only returns fixture results (no real network calls).
     */
    fun ajax(
        permit: BridgeBackedJavaExecutionPermit?,
        sourceId: String,
        url: String
    ): Result<String> {
        val denial = validatePermit(permit, sourceId, "java.ajax") ?: run {
            // java.ajax returns fixture HTML, never real network
            return Result.success("<html><body>fixture ajax response</body></html>")
        }
        return handleDenial(sourceId, "java.ajax", denial)
    }

    /**
     * Execute java.refreshTocUrl for [sourceId], guarded by [permit].
     */
    fun refreshTocUrl(
        permit: BridgeBackedJavaExecutionPermit?,
        sourceId: String,
        url: String
    ): Result<String> {
        val denial = validatePermit(permit, sourceId, "java.refreshTocUrl") ?: run {
            return Result.success("fixture_refresh_toc_ok")
        }
        return handleDenial(sourceId, "java.refreshTocUrl", denial)
    }

    // ── Internal: Permit Validation ──

    /**
     * Validate [permit] against adapter boundary rules.
     *
     * @return null if validation passes (permit is valid), or a [BridgeAdapterDenial] describing the failure.
     */
    private fun validatePermit(
        permit: BridgeBackedJavaExecutionPermit?,
        callSourceId: String,
        actualAPI: String
    ): BridgeAdapterDenial? {
        // 1. Permit must exist
        if (permit == null) {
            return BridgeAdapterDenial.DENY_POLICY_PERMIT_REQUIRED
        }

        // 2. Permit API must match called API
        if (permit.requiredBridgeAPI != actualAPI) {
            return BridgeAdapterDenial.DENY_POLICY_PERMIT_API_MISMATCH(
                expectedApi = permit.requiredBridgeAPI,
                actualApi = actualAPI
            )
        }

        // 3. Permit sourceId must match calling sourceId
        if (permit.sourceId != callSourceId) {
            return BridgeAdapterDenial.DENY_POLICY_PERMIT_SOURCE_MISMATCH(
                permitSourceId = permit.sourceId,
                callSourceId = callSourceId
            )
        }

        return null // validation passed
    }

    /**
     * Record a denial in telemetry and return a failed Result.
     */
    private fun <T> handleDenial(
        sourceId: String,
        api: String,
        denial: BridgeAdapterDenial
    ): Result<T> {
        val reason = when (denial) {
            is BridgeAdapterDenial.DENY_POLICY_PERMIT_REQUIRED -> "DENY_POLICY_PERMIT_REQUIRED"
            is BridgeAdapterDenial.DENY_POLICY_PERMIT_API_MISMATCH -> "DENY_POLICY_PERMIT_API_MISMATCH"
            is BridgeAdapterDenial.DENY_POLICY_PERMIT_SOURCE_MISMATCH -> "DENY_POLICY_PERMIT_SOURCE_MISMATCH"
        }
        telemetry.recordDeny(sourceId, api, reason)
        return Result.failure(BridgeAdapterPolicyException(denial))
    }
}

/**
 * Exception thrown when the Java Bridge Adapter denies a call due to permit validation failure.
 */
class BridgeAdapterPolicyException(val denial: BridgeAdapterDenial) :
    Exception("Bridge adapter denied: $denial")