package com.reader.android.data.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * CAP-5JS14: Targeted tests for BridgeBackedPolicyGate (fail-closed design).
 *
 * Known baseline:
 * - actualJS4AllowlistCount: 22 (implicit — no actual JS allowlist exists in this test scope)
 * - bridgeBackedTestOnlyAllowlistCount: 3
 * - productionBridgeEnabled: false
 */
class CAP5JS14BridgeBackedPolicyGateTest {

    private lateinit var allowlist: BridgeBackedTestOnlyAllowlist
    private lateinit var telemetry: BridgeBackedDryRunTelemetry
    private lateinit var fixtureRegistry: TestOnlyFixtureAvailability
    private lateinit var gate: BridgeBackedPolicyGate

    @Before
    fun setup() {
        allowlist = BridgeBackedTestOnlyAllowlist()
        telemetry = BridgeBackedDryRunTelemetry()
        fixtureRegistry = DefaultTestOnlyFixtureRegistry()
        gate = BridgeBackedPolicyGate(allowlist, telemetry, fixtureRegistry)
    }

    // ── Allowlist contract ──

    @Test
    fun `allowlist has exactly 3 entries`() {
        assertEquals(3, allowlist.count())
    }

    @Test
    fun `allowlist contains all expected source IDs`() {
        assertTrue(allowlist.contains("onehu_xyz"))
        assertTrue(allowlist.contains("qidian_com"))
        assertTrue(allowlist.contains("zhenhunxiaoshuo"))
    }

    @Test
    fun `allowlist returns correct bridge APIs`() {
        assertEquals("java.ajax", allowlist.requiredBridgeAPI("onehu_xyz"))
        assertEquals("java.refreshTocUrl", allowlist.requiredBridgeAPI("qidian_com"))
        assertEquals("java.getElements", allowlist.requiredBridgeAPI("zhenhunxiaoshuo"))
    }

    @Test
    fun `allowlist does not contain non-allowlisted source`() {
        assertFalse(allowlist.contains("unknown_source"))
        assertNull(allowlist.requiredBridgeAPI("unknown_source"))
    }

    // ── ALLOW scenarios ──

    @Test
    fun `onehu_xyz with java_ajax is ALLOWED`() {
        val decision = gate.evaluate("onehu_xyz", "java.ajax")
        assertTrue(decision is BridgeBackedPolicyDecision.ALLOW_TEST_ONLY_BRIDGE)
    }

    @Test
    fun `qidian_com with java_refreshTocUrl is ALLOWED`() {
        val decision = gate.evaluate("qidian_com", "java.refreshTocUrl")
        assertTrue(decision is BridgeBackedPolicyDecision.ALLOW_TEST_ONLY_BRIDGE)
    }

    @Test
    fun `zhenhunxiaoshuo with java_getElements is ALLOWED`() {
        val decision = gate.evaluate("zhenhunxiaoshuo", "java.getElements")
        assertTrue(decision is BridgeBackedPolicyDecision.ALLOW_TEST_ONLY_BRIDGE)
    }

    // ── DENY: Not in allowlist ──

    @Test
    fun `non-allowlisted source is DENIED`() {
        val decision = gate.evaluate("unknown_source", "java.ajax")
        assertTrue(decision is BridgeBackedPolicyDecision.DENY_NOT_IN_ALLOWLIST)
    }

    @Test
    fun `allowlisted source with wrong API is DENIED`() {
        // onehu_xyz requires java.ajax, not java.getElements
        val decision = gate.evaluate("onehu_xyz", "java.getElements")
        assertTrue(decision is BridgeBackedPolicyDecision.DENY_NOT_IN_ALLOWLIST)
    }

    // ── DENY: Disabled flag ──

    @Test
    fun `disabled bridge runtime is DENIED`() {
        val config = PolicyEvaluationConfig(bridgeRuntimeEnabled = false)
        val decision = gate.evaluate("onehu_xyz", "java.ajax", config)
        assertTrue(decision is BridgeBackedPolicyDecision.DENY_DISABLED_FLAG)
    }

    // ── DENY: Production context ──

    @Test
    fun `production context is DENIED`() {
        val config = PolicyEvaluationConfig(productionContext = true)
        val decision = gate.evaluate("onehu_xyz", "java.ajax", config)
        assertTrue(decision is BridgeBackedPolicyDecision.DENY_PRODUCTION_CONTEXT)
    }

    // ── DENY: Telemetry disabled ──

    @Test
    fun `telemetry disabled is DENIED`() {
        val config = PolicyEvaluationConfig(telemetryEnabled = false)
        val decision = gate.evaluate("onehu_xyz", "java.ajax", config)
        assertTrue(decision is BridgeBackedPolicyDecision.DENY_TELEMETRY_DISABLED)
    }

    // ── DENY: Recovered classification ──

    @Test
    fun `recovered classification request is DENIED`() {
        val config = PolicyEvaluationConfig(recoveredClassificationRequested = true)
        val decision = gate.evaluate("onehu_xyz", "java.ajax", config)
        assertTrue(decision is BridgeBackedPolicyDecision.DENY_RECOVERED_CLASSIFICATION)
    }

    // ── DENY: Network required ──

    @Test
    fun `network required is DENIED`() {
        val config = PolicyEvaluationConfig(networkRequired = true)
        val decision = gate.evaluate("onehu_xyz", "java.ajax", config)
        assertTrue(decision is BridgeBackedPolicyDecision.DENY_NETWORK_REQUIRED)
    }

    // ── DENY: Fixture missing ──

    @Test
    fun `fixture missing is DENIED`() {
        // Use a fixture registry that has no fixtures
        val emptyFixtureRegistry = object : TestOnlyFixtureAvailability {
            override fun hasFixture(sourceId: String, api: String): Boolean = false
        }
        val gateNoFixture = BridgeBackedPolicyGate(allowlist, telemetry, emptyFixtureRegistry)
        val decision = gateNoFixture.evaluate("onehu_xyz", "java.ajax")
        assertTrue(decision is BridgeBackedPolicyDecision.DENY_FIXTURE_MISSING)
    }

    // ── DENY: Unknown API ──

    @Test
    fun `unknown bridge API is DENIED`() {
        val decision = gate.evaluate("onehu_xyz", "java.unknownApi")
        assertTrue(decision is BridgeBackedPolicyDecision.DENY_UNKNOWN_API)
    }

    // ── Telemetry recording ──

    @Test
    fun `telemetry records allow decisions`() {
        gate.evaluate("onehu_xyz", "java.ajax")
        assertEquals(1, telemetry.totalDecisionsMade())
        assertEquals(1, telemetry.allowDecisions())
        assertEquals(0, telemetry.denyDecisions())
    }

    @Test
    fun `telemetry records deny decisions`() {
        gate.evaluate("unknown_source", "java.ajax")
        assertEquals(1, telemetry.totalDecisionsMade())
        assertEquals(0, telemetry.allowDecisions())
        assertEquals(1, telemetry.denyDecisions())
    }

    @Test
    fun `telemetry deny reason counts are accurate`() {
        gate.evaluate("unknown_source", "java.ajax")
        gate.evaluate("another_unknown", "java.getElements")
        gate.evaluate("onehu_xyz", "java.getElements") // wrong API for allowlist

        assertTrue(telemetry.denyReasonCounts().containsKey("DENY_NOT_IN_ALLOWLIST"))
        assertEquals(3, telemetry.denyReasonCounts()["DENY_NOT_IN_ALLOWLIST"])
        assertEquals(3, telemetry.denyDecisions())
    }

    @Test
    fun `telemetry can be reset`() {
        gate.evaluate("onehu_xyz", "java.ajax")
        assertTrue(telemetry.totalDecisionsMade() > 0)
        telemetry.reset()
        assertEquals(0, telemetry.totalDecisionsMade())
        assertEquals(0, telemetry.allowDecisions())
        assertEquals(0, telemetry.denyDecisions())
    }

    // ── Hold closed: default config is test-only dry-run ──

    @Test
    fun `default config matches test-only dry-run baseline`() {
        val config = PolicyEvaluationConfig()
        assertTrue(config.bridgeRuntimeEnabled)
        assertFalse(config.productionContext)
        assertTrue(config.telemetryEnabled)
        assertFalse(config.recoveredClassificationRequested)
        assertTrue(config.fixturesRequired)
        assertFalse(config.networkRequired)
    }

    // ── Default fixture registry ──

    @Test
    fun `default fixture registry has fixtures for all 3 allowlisted sources`() {
        val registry = DefaultTestOnlyFixtureRegistry()
        assertEquals(3, registry.count())
        assertTrue(registry.hasFixture("onehu_xyz", "java.ajax"))
        assertTrue(registry.hasFixture("qidian_com", "java.refreshTocUrl"))
        assertTrue(registry.hasFixture("zhenhunxiaoshuo", "java.getElements"))
    }

    // ── Fail-closed property ──

    @Test
    fun `default gate with default config ALLOWs valid source`() {
        // Default config = test-only dry-run — should ALLOW
        val decision = gate.evaluate("onehu_xyz", "java.ajax")
        assertTrue("Default config should ALLOW valid bridge source", decision is BridgeBackedPolicyDecision.ALLOW_TEST_ONLY_BRIDGE)
    }

    @Test
    fun `default gate DENY_ALL for every invalid condition`() {
        // Test that DENY takes priority over ALLOW
        assertEquals(BridgeBackedPolicyDecision.DENY_DISABLED_FLAG::class,
            gate.evaluate("onehu_xyz", "java.ajax", PolicyEvaluationConfig(bridgeRuntimeEnabled = false))::class)
        assertEquals(BridgeBackedPolicyDecision.DENY_PRODUCTION_CONTEXT::class,
            gate.evaluate("onehu_xyz", "java.ajax", PolicyEvaluationConfig(productionContext = true))::class)
        assertEquals(BridgeBackedPolicyDecision.DENY_TELEMETRY_DISABLED::class,
            gate.evaluate("onehu_xyz", "java.ajax", PolicyEvaluationConfig(telemetryEnabled = false))::class)
        assertEquals(BridgeBackedPolicyDecision.DENY_RECOVERED_CLASSIFICATION::class,
            gate.evaluate("onehu_xyz", "java.ajax", PolicyEvaluationConfig(recoveredClassificationRequested = true))::class)
        assertEquals(BridgeBackedPolicyDecision.DENY_NETWORK_REQUIRED::class,
            gate.evaluate("onehu_xyz", "java.ajax", PolicyEvaluationConfig(networkRequired = true))::class)
    }
}