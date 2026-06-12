package com.reader.android.data.policy

/**
 * Sealed class representing the outcome of a bridge-backed policy evaluation.
 */
sealed class BridgeBackedPolicyDecision {
    /** Source is allowlisted and bridge API execution is permitted in test-only mode. */
    data object ALLOW_TEST_ONLY_BRIDGE : BridgeBackedPolicyDecision()

    // ── Deny reasons ──

    /** Source ID is not in the test-only allowlist. */
    data object DENY_NOT_IN_ALLOWLIST : BridgeBackedPolicyDecision()

    /** Bridge-backed runtime flag is disabled. */
    data object DENY_DISABLED_FLAG : BridgeBackedPolicyDecision()

    /** Running in production context — bridge execution not permitted. */
    data object DENY_PRODUCTION_CONTEXT : BridgeBackedPolicyDecision()

    /** Telemetry is disabled — cannot safely execute bridge calls. */
    data object DENY_TELEMETRY_DISABLED : BridgeBackedPolicyDecision()

    /** The requested API requires network but network is not available/configured. */
    data object DENY_NETWORK_REQUIRED : BridgeBackedPolicyDecision()

    /** Source is classified as recovered — bridge execution not permitted. */
    data object DENY_RECOVERED_CLASSIFICATION : BridgeBackedPolicyDecision()

    /** Required fixture is missing — cannot fulfill the request. */
    data object DENY_FIXTURE_MISSING : BridgeBackedPolicyDecision()

    /** Unknown or unsupported bridge API. */
    data object DENY_UNKNOWN_API : BridgeBackedPolicyDecision()
}

/**
 * Fail-closed policy gate for bridge-backed java bridge API execution.
 *
 * Evaluates whether a given (sourceId, requiredBridgeAPI) pair is permitted
 * based on:
 * - Test-only allowlist membership
 * - Bridge runtime flag state
 * - Production context guard
 * - Telemetry enabled guard
 * - Fixture availability
 * - Network requirement
 * - Recovery classification
 *
 * Stay fail-closed: any disallowed condition → DENY, never fall through to ALLOW.
 */
class BridgeBackedPolicyGate(
    private val allowlist: BridgeBackedTestOnlyAllowlist,
    private val telemetry: BridgeBackedDryRunTelemetry,
    private val fixtureRegistry: TestOnlyFixtureAvailability
) {

    /**
     * Evaluate whether [sourceId] should be allowed to execute [requiredBridgeAPI].
     *
     * Fail-closed: returns DENY unless every condition is met.
     */
    fun evaluate(
        sourceId: String,
        requiredBridgeAPI: String,
        config: PolicyEvaluationConfig = PolicyEvaluationConfig()
    ): BridgeBackedPolicyDecision {
        // 1. Check bridge runtime flag
        if (!config.bridgeRuntimeEnabled) {
            telemetry.recordDeny(sourceId, requiredBridgeAPI, "DENY_DISABLED_FLAG")
            return BridgeBackedPolicyDecision.DENY_DISABLED_FLAG
        }

        // 2. Check production context
        if (config.productionContext) {
            telemetry.recordDeny(sourceId, requiredBridgeAPI, "DENY_PRODUCTION_CONTEXT")
            return BridgeBackedPolicyDecision.DENY_PRODUCTION_CONTEXT
        }

        // 3. Check telemetry enabled
        if (!config.telemetryEnabled) {
            telemetry.recordDeny(sourceId, requiredBridgeAPI, "DENY_TELEMETRY_DISABLED")
            return BridgeBackedPolicyDecision.DENY_TELEMETRY_DISABLED
        }

        // 4. Check recovery classification
        if (config.recoveredClassificationRequested) {
            telemetry.recordDeny(sourceId, requiredBridgeAPI, "DENY_RECOVERED_CLASSIFICATION")
            return BridgeBackedPolicyDecision.DENY_RECOVERED_CLASSIFICATION
        }

        // 5. Check allowlist membership
        if (!allowlist.contains(sourceId)) {
            telemetry.recordDeny(sourceId, requiredBridgeAPI, "DENY_NOT_IN_ALLOWLIST")
            return BridgeBackedPolicyDecision.DENY_NOT_IN_ALLOWLIST
        }

        // 6. Check that the API is a known java bridge API (before allowlist API match)
        if (!config.isKnownBridgeAPI(requiredBridgeAPI)) {
            telemetry.recordDeny(sourceId, requiredBridgeAPI, "DENY_UNKNOWN_API")
            return BridgeBackedPolicyDecision.DENY_UNKNOWN_API
        }

        // 7. Validate required API is what the allowlist expects
        val expectedAPI = allowlist.requiredBridgeAPI(sourceId)
        if (expectedAPI != requiredBridgeAPI) {
            telemetry.recordDeny(sourceId, requiredBridgeAPI, "DENY_NOT_IN_ALLOWLIST")
            return BridgeBackedPolicyDecision.DENY_NOT_IN_ALLOWLIST
        }

        // 8. Check fixture availability
        if (config.fixturesRequired && !fixtureRegistry.hasFixture(sourceId, requiredBridgeAPI)) {
            telemetry.recordDeny(sourceId, requiredBridgeAPI, "DENY_FIXTURE_MISSING")
            return BridgeBackedPolicyDecision.DENY_FIXTURE_MISSING
        }

        // 9. Check network requirement
        if (config.networkRequired) {
            telemetry.recordDeny(sourceId, requiredBridgeAPI, "DENY_NETWORK_REQUIRED")
            return BridgeBackedPolicyDecision.DENY_NETWORK_REQUIRED
        }

        // ALL conditions met
        telemetry.recordAllow(sourceId, requiredBridgeAPI)
        return BridgeBackedPolicyDecision.ALLOW_TEST_ONLY_BRIDGE
    }
}

/**
 * Configuration for policy evaluation.
 * Defaults match the test-only dry-run baseline:
 * - bridgeRuntimeEnabled=true
 * - productionContext=false
 * - telemetryEnabled=true
 * - recoveredClassificationRequested=false
 * - fixturesRequired=true
 * - networkRequired=false
 */
data class PolicyEvaluationConfig(
    val bridgeRuntimeEnabled: Boolean = true,
    val productionContext: Boolean = false,
    val telemetryEnabled: Boolean = true,
    val recoveredClassificationRequested: Boolean = false,
    val fixturesRequired: Boolean = true,
    val networkRequired: Boolean = false
) {
    /** Known java bridge APIs. */
    fun isKnownBridgeAPI(api: String): Boolean = KNOWN_BRIDGE_APIS.contains(api)

    companion object {
        val KNOWN_BRIDGE_APIS = setOf(
            "java.ajax",
            "java.refreshTocUrl",
            "java.getElements"
        )
    }
}

/**
 * Interface for checking fixture availability in the policy gate.
 * Allows test-driven fixture availability without coupling to fixture internals.
 */
interface TestOnlyFixtureAvailability {
    fun hasFixture(sourceId: String, api: String): Boolean
}

/**
 * Default implementation — all 3 allowlisted sources have fixtures available.
 */
class DefaultTestOnlyFixtureRegistry : TestOnlyFixtureAvailability {
    /** All 3 allowlisted sources have fixtures available in test-only mode. */
    private val availableFixtures: Set<String> = setOf(
        "onehu_xyz",
        "qidian_com",
        "zhenhunxiaoshuo"
    )

    override fun hasFixture(sourceId: String, api: String): Boolean =
        availableFixtures.contains(sourceId)

    fun count(): Int = availableFixtures.size
}