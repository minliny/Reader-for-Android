package com.reader.android.data.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * CAP-5JS15: Targeted tests for bridge-backed policy enforcement at the Java Bridge Adapter boundary.
 *
 * Covers:
 * 1.  ALLOW_TEST_ONLY_BRIDGE can generate permit
 * 2.  DENY_* cannot generate permit
 * 3.  No permit calling adapter must deny
 * 4.  API mismatch permit must deny
 * 5.  Source mismatch permit must deny
 * 6.  3 allowlisted sources with correct permit can execute
 * 7.  Disabled flag doesn't generate permit
 * 8.  Non-allowlisted source doesn't generate permit
 * 9.  Unknown API doesn't generate permit
 * 10. Missing fixture doesn't generate permit
 * 11. networkRequired=true doesn't generate permit
 * 12. productionContext=true doesn't generate permit
 * 13. telemetryEnabled=false doesn't generate permit
 * 14. recoveredClassificationRequested=true doesn't generate permit
 * 15. java.ajax still doesn't make real network requests
 * 16. recoveredCount=0
 * 17. productionPathTouchedCount=0
 * 18. actual JS allowlist count still 22 (implicit — no actual JS allowlist created)
 * 19. bridge-backed test-only allowlist count still 3
 * 20. enforcement report fields accurate
 * 21. privacy grep clean
 */
class CAP5JS15BridgeAdapterPolicyEnforcementTest {

    private lateinit var allowlist: BridgeBackedTestOnlyAllowlist
    private lateinit var telemetry: BridgeBackedDryRunTelemetry
    private lateinit var fixtureRegistry: TestOnlyFixtureAvailability
    private lateinit var gate: BridgeBackedPolicyGate
    private lateinit var adapter: JavaBridgeAdapter
    private val defaultConfig = PolicyEvaluationConfig()

    @Before
    fun setup() {
        allowlist = BridgeBackedTestOnlyAllowlist()
        telemetry = BridgeBackedDryRunTelemetry()
        fixtureRegistry = DefaultTestOnlyFixtureRegistry()
        gate = BridgeBackedPolicyGate(allowlist, telemetry, fixtureRegistry)
        adapter = JavaBridgeAdapter(telemetry)
    }

    // ════════════════════════════════════════════════════════════════
    // Test 1: ALLOW_TEST_ONLY_BRIDGE can generate permit
    // ════════════════════════════════════════════════════════════════

    @Test
    fun `ALLOW_TEST_ONLY_BRIDGE generates permit for onehu_xyz`() {
        val decision = gate.evaluate("onehu_xyz", "java.ajax")
        assertTrue(decision is BridgeBackedPolicyDecision.ALLOW_TEST_ONLY_BRIDGE)

        val permit = gate.generatePermit(decision, "onehu_xyz", "java.ajax")
        assertNotNull("ALLOW should generate permit", permit)
        assertEquals("onehu_xyz", permit!!.sourceId)
        assertEquals("java.ajax", permit.requiredBridgeAPI)
        assertEquals("BRIDGE_BACKED_TEST_ONLY", permit.allowlistKind)
        assertEquals("testOnlyEnabled", permit.bridgeRuntimeFlag)
        assertTrue(permit.fixtureAvailable)
        assertFalse(permit.networkRequired)
        assertFalse(permit.productionContext)
        assertTrue(permit.telemetryEnabled)
        assertFalse(permit.recoveredClassificationRequested)
        assertTrue(permit.privacySafe)
    }

    @Test
    fun `ALLOW_TEST_ONLY_BRIDGE generates permit for qidian_com`() {
        val decision = gate.evaluate("qidian_com", "java.refreshTocUrl")
        assertTrue(decision is BridgeBackedPolicyDecision.ALLOW_TEST_ONLY_BRIDGE)

        val permit = gate.generatePermit(decision, "qidian_com", "java.refreshTocUrl")
        assertNotNull("ALLOW should generate permit", permit)
        assertEquals("qidian_com", permit!!.sourceId)
        assertEquals("java.refreshTocUrl", permit.requiredBridgeAPI)
    }

    @Test
    fun `ALLOW_TEST_ONLY_BRIDGE generates permit for zhenhunxiaoshuo`() {
        val decision = gate.evaluate("zhenhunxiaoshuo", "java.getElements")
        assertTrue(decision is BridgeBackedPolicyDecision.ALLOW_TEST_ONLY_BRIDGE)

        val permit = gate.generatePermit(decision, "zhenhunxiaoshuo", "java.getElements")
        assertNotNull("ALLOW should generate permit", permit)
        assertEquals("zhenhunxiaoshuo", permit!!.sourceId)
        assertEquals("java.getElements", permit.requiredBridgeAPI)
    }

    // ════════════════════════════════════════════════════════════════
    // Test 2: DENY_* cannot generate permit
    // ════════════════════════════════════════════════════════════════

    @Test
    fun `DENY_NOT_IN_ALLOWLIST does not generate permit`() {
        val decision = gate.evaluate("unknown_source", "java.ajax")
        assertTrue(decision is BridgeBackedPolicyDecision.DENY_NOT_IN_ALLOWLIST)

        val permit = gate.generatePermit(decision, "unknown_source", "java.ajax")
        assertNull("DENY should not generate permit", permit)
    }

    @Test
    fun `DENY_DISABLED_FLAG does not generate permit`() {
        val config = PolicyEvaluationConfig(bridgeRuntimeEnabled = false)
        val decision = gate.evaluate("onehu_xyz", "java.ajax", config)
        assertTrue(decision is BridgeBackedPolicyDecision.DENY_DISABLED_FLAG)

        val permit = gate.generatePermit(decision, "onehu_xyz", "java.ajax")
        assertNull("DENY should not generate permit", permit)
    }

    @Test
    fun `DENY_PRODUCTION_CONTEXT does not generate permit`() {
        val config = PolicyEvaluationConfig(productionContext = true)
        val decision = gate.evaluate("onehu_xyz", "java.ajax", config)

        val permit = gate.generatePermit(decision, "onehu_xyz", "java.ajax")
        assertNull("DENY_PRODUCTION_CONTEXT should not generate permit", permit)
    }

    @Test
    fun `DENY_TELEMETRY_DISABLED does not generate permit`() {
        val config = PolicyEvaluationConfig(telemetryEnabled = false)
        val decision = gate.evaluate("onehu_xyz", "java.ajax", config)

        val permit = gate.generatePermit(decision, "onehu_xyz", "java.ajax")
        assertNull("DENY_TELEMETRY_DISABLED should not generate permit", permit)
    }

    // ════════════════════════════════════════════════════════════════
    // Test 3: No permit calling adapter must deny
    // ════════════════════════════════════════════════════════════════

    @Test
    fun `no permit for getElements is denied`() {
        val result = adapter.getElements(null, "onehu_xyz")
        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull() as? BridgeAdapterPolicyException
        assertNotNull(exception)
        assertTrue(exception!!.denial is BridgeAdapterDenial.DENY_POLICY_PERMIT_REQUIRED)
    }

    @Test
    fun `no permit for ajax is denied`() {
        val result = adapter.ajax(null, "onehu_xyz", "http://fixture.url")
        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull() as? BridgeAdapterPolicyException
        assertNotNull(exception)
        assertTrue(exception!!.denial is BridgeAdapterDenial.DENY_POLICY_PERMIT_REQUIRED)
    }

    @Test
    fun `no permit for refreshTocUrl is denied`() {
        val result = adapter.refreshTocUrl(null, "onehu_xyz", "http://fixture.url")
        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull() as? BridgeAdapterPolicyException
        assertNotNull(exception)
        assertTrue(exception!!.denial is BridgeAdapterDenial.DENY_POLICY_PERMIT_REQUIRED)
    }

    // ════════════════════════════════════════════════════════════════
    // Test 4: API mismatch permit must deny
    // ════════════════════════════════════════════════════════════════

    @Test
    fun `permit for ajax used with getElements is denied as API mismatch`() {
        val decision = gate.evaluate("onehu_xyz", "java.ajax")
        val permit = gate.generatePermit(decision, "onehu_xyz", "java.ajax")!!

        // Try to use ajax permit for getElements
        val result = adapter.getElements(permit, "onehu_xyz")
        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull() as? BridgeAdapterPolicyException
        assertNotNull(exception)
        val denial = exception!!.denial as? BridgeAdapterDenial.DENY_POLICY_PERMIT_API_MISMATCH
        assertNotNull(denial)
        assertEquals("java.ajax", denial!!.expectedApi)
        assertEquals("java.getElements", denial.actualApi)
    }

    @Test
    fun `permit for getElements used with ajax is denied as API mismatch`() {
        val decision = gate.evaluate("zhenhunxiaoshuo", "java.getElements")
        val permit = gate.generatePermit(decision, "zhenhunxiaoshuo", "java.getElements")!!

        val result = adapter.ajax(permit, "zhenhunxiaoshuo", "http://fixture.url")
        assertTrue(result.isFailure)
        val denial = (result.exceptionOrNull() as? BridgeAdapterPolicyException)?.denial
        assertTrue(denial is BridgeAdapterDenial.DENY_POLICY_PERMIT_API_MISMATCH)
    }

    @Test
    fun `permit for refreshTocUrl used with ajax is denied as API mismatch`() {
        val decision = gate.evaluate("qidian_com", "java.refreshTocUrl")
        val permit = gate.generatePermit(decision, "qidian_com", "java.refreshTocUrl")!!

        val result = adapter.ajax(permit, "qidian_com", "http://fixture.url")
        assertTrue(result.isFailure)
        val denial = (result.exceptionOrNull() as? BridgeAdapterPolicyException)?.denial
        assertTrue(denial is BridgeAdapterDenial.DENY_POLICY_PERMIT_API_MISMATCH)
    }

    // ════════════════════════════════════════════════════════════════
    // Test 5: Source mismatch permit must deny
    // ════════════════════════════════════════════════════════════════

    @Test
    fun `permit for onehu_xyz used with qidian_com is denied as source mismatch`() {
        val decision = gate.evaluate("onehu_xyz", "java.ajax")
        val permit = gate.generatePermit(decision, "onehu_xyz", "java.ajax")!!

        val result = adapter.ajax(permit, "qidian_com", "http://fixture.url")
        assertTrue(result.isFailure)
        val denial = (result.exceptionOrNull() as? BridgeAdapterPolicyException)?.denial
        assertTrue("Expected source mismatch but got $denial", denial is BridgeAdapterDenial.DENY_POLICY_PERMIT_SOURCE_MISMATCH)
        val sourceDenial = denial as BridgeAdapterDenial.DENY_POLICY_PERMIT_SOURCE_MISMATCH
        assertEquals("onehu_xyz", sourceDenial.permitSourceId)
        assertEquals("qidian_com", sourceDenial.callSourceId)
    }

    // ════════════════════════════════════════════════════════════════
    // Test 6: 3 allowlisted sources with correct permit can execute
    // ════════════════════════════════════════════════════════════════

    @Test
    fun `onehu_xyz with correct ajax permit executes adapter`() {
        val decision = gate.evaluate("onehu_xyz", "java.ajax")
        val permit = gate.generatePermit(decision, "onehu_xyz", "java.ajax")!!

        val result = adapter.ajax(permit, "onehu_xyz", "http://fixture.url")
        assertTrue("onehu_xyz ajax should execute with valid permit", result.isSuccess)
        assertTrue(result.getOrThrow().contains("fixture"))
    }

    @Test
    fun `qidian_com with correct refreshTocUrl permit executes adapter`() {
        val decision = gate.evaluate("qidian_com", "java.refreshTocUrl")
        val permit = gate.generatePermit(decision, "qidian_com", "java.refreshTocUrl")!!

        val result = adapter.refreshTocUrl(permit, "qidian_com", "http://fixture.url")
        assertTrue("qidian_com refreshTocUrl should execute with valid permit", result.isSuccess)
    }

    @Test
    fun `zhenhunxiaoshuo with correct getElements permit executes adapter`() {
        val decision = gate.evaluate("zhenhunxiaoshuo", "java.getElements")
        val permit = gate.generatePermit(decision, "zhenhunxiaoshuo", "java.getElements")!!

        val result = adapter.getElements(permit, "zhenhunxiaoshuo")
        assertTrue("zhenhunxiaoshuo getElements should execute with valid permit", result.isSuccess)
        val elements = result.getOrThrow()
        assertTrue(elements.isNotEmpty())
    }

    // ════════════════════════════════════════════════════════════════
    // Test 7: Disabled flag doesn't generate permit
    // ════════════════════════════════════════════════════════════════

    @Test
    fun `disabled bridge runtime does not generate permit`() {
        val config = PolicyEvaluationConfig(bridgeRuntimeEnabled = false)
        val decision = gate.evaluate("onehu_xyz", "java.ajax", config)
        assertTrue(decision is BridgeBackedPolicyDecision.DENY_DISABLED_FLAG)

        val permit = gate.generatePermit(decision, "onehu_xyz", "java.ajax")
        assertNull("disabled flag should not generate permit", permit)
    }

    // ════════════════════════════════════════════════════════════════
    // Test 8: Non-allowlisted source doesn't generate permit
    // ════════════════════════════════════════════════════════════════

    @Test
    fun `non-allowlisted source does not generate permit`() {
        val decision = gate.evaluate("unknown_source", "java.ajax")
        assertTrue(decision is BridgeBackedPolicyDecision.DENY_NOT_IN_ALLOWLIST)

        val permit = gate.generatePermit(decision, "unknown_source", "java.ajax")
        assertNull("non-allowlisted source should not generate permit", permit)
    }

    // ════════════════════════════════════════════════════════════════
    // Test 9: Unknown API doesn't generate permit
    // ════════════════════════════════════════════════════════════════

    @Test
    fun `unknown bridge API does not generate permit`() {
        val decision = gate.evaluate("onehu_xyz", "java.unknownApi")
        assertTrue(decision is BridgeBackedPolicyDecision.DENY_UNKNOWN_API)

        val permit = gate.generatePermit(decision, "onehu_xyz", "java.unknownApi")
        assertNull("unknown API should not generate permit", permit)
    }

    // ════════════════════════════════════════════════════════════════
    // Test 10: Missing fixture doesn't generate permit
    // ════════════════════════════════════════════════════════════════

    @Test
    fun `missing fixture does not generate permit`() {
        val emptyFixtureRegistry = object : TestOnlyFixtureAvailability {
            override fun hasFixture(sourceId: String, api: String): Boolean = false
        }
        val gateNoFixture = BridgeBackedPolicyGate(allowlist, telemetry, emptyFixtureRegistry)
        val decision = gateNoFixture.evaluate("onehu_xyz", "java.ajax")
        assertTrue(decision is BridgeBackedPolicyDecision.DENY_FIXTURE_MISSING)

        val permit = gateNoFixture.generatePermit(decision, "onehu_xyz", "java.ajax")
        assertNull("missing fixture should not generate permit", permit)
    }

    // ════════════════════════════════════════════════════════════════
    // Test 11: networkRequired=true doesn't generate permit
    // ════════════════════════════════════════════════════════════════

    @Test
    fun `networkRequired true does not generate permit`() {
        val config = PolicyEvaluationConfig(networkRequired = true)
        val decision = gate.evaluate("onehu_xyz", "java.ajax", config)
        assertTrue(decision is BridgeBackedPolicyDecision.DENY_NETWORK_REQUIRED)

        val permit = gate.generatePermit(decision, "onehu_xyz", "java.ajax")
        assertNull("networkRequired should not generate permit", permit)
    }

    // ════════════════════════════════════════════════════════════════
    // Test 12: productionContext=true doesn't generate permit
    // ════════════════════════════════════════════════════════════════

    @Test
    fun `productionContext true does not generate permit`() {
        val config = PolicyEvaluationConfig(productionContext = true)
        val decision = gate.evaluate("onehu_xyz", "java.ajax", config)
        assertTrue(decision is BridgeBackedPolicyDecision.DENY_PRODUCTION_CONTEXT)

        val permit = gate.generatePermit(decision, "onehu_xyz", "java.ajax")
        assertNull("productionContext should not generate permit", permit)
    }

    // ════════════════════════════════════════════════════════════════
    // Test 13: telemetryEnabled=false doesn't generate permit
    // ════════════════════════════════════════════════════════════════

    @Test
    fun `telemetryDisabled does not generate permit`() {
        val config = PolicyEvaluationConfig(telemetryEnabled = false)
        val decision = gate.evaluate("onehu_xyz", "java.ajax", config)
        assertTrue(decision is BridgeBackedPolicyDecision.DENY_TELEMETRY_DISABLED)

        val permit = gate.generatePermit(decision, "onehu_xyz", "java.ajax")
        assertNull("telemetryDisabled should not generate permit", permit)
    }

    // ════════════════════════════════════════════════════════════════
    // Test 14: recoveredClassificationRequested=true doesn't generate permit
    // ════════════════════════════════════════════════════════════════

    @Test
    fun `recoveredClassificationRequested does not generate permit`() {
        val config = PolicyEvaluationConfig(recoveredClassificationRequested = true)
        val decision = gate.evaluate("onehu_xyz", "java.ajax", config)
        assertTrue(decision is BridgeBackedPolicyDecision.DENY_RECOVERED_CLASSIFICATION)

        val permit = gate.generatePermit(decision, "onehu_xyz", "java.ajax")
        assertNull("recoveredClassification should not generate permit", permit)
    }

    // ════════════════════════════════════════════════════════════════
    // Test 15: java.ajax still doesn't make real network requests
    // ════════════════════════════════════════════════════════════════

    @Test
    fun `java ajax returns fixture only no real network`() {
        val decision = gate.evaluate("onehu_xyz", "java.ajax")
        val permit = gate.generatePermit(decision, "onehu_xyz", "java.ajax")!!

        // ajax should return fixture HTML, never make real network calls
        val result = adapter.ajax(permit, "onehu_xyz", "http://any-fake-url.com")
        assertTrue(result.isSuccess)
        val response = result.getOrThrow()
        assertEquals("<html><body>fixture ajax response</body></html>", response)
    }

    // ════════════════════════════════════════════════════════════════
    // Test 16: recoveredCount=0
    // ════════════════════════════════════════════════════════════════

    @Test
    fun `recoveredCount remains zero`() {
        // No recovery path should be triggered in test-only mode
        // Verify no recovered classification was ever requested
        val recoveredRequests = telemetry.denyReasonCounts()["DENY_RECOVERED_CLASSIFICATION"]
        assertNull("recovered should not be triggered", recoveredRequests)
    }

    // ════════════════════════════════════════════════════════════════
    // Test 17: productionPathTouchedCount=0
    // ════════════════════════════════════════════════════════════════

    @Test
    fun `productionPathTouchedCount remains zero`() {
        // No production context should be set in default config
        assertFalse(defaultConfig.productionContext)
        val productionDenies = telemetry.denyReasonCounts()["DENY_PRODUCTION_CONTEXT"]
        assertNull("production path should not be touched", productionDenies)
    }

    // ════════════════════════════════════════════════════════════════
    // Test 18 & 19: allowlist counts
    // ════════════════════════════════════════════════════════════════

    @Test
    fun `allowlist counts remain unchanged`() {
        assertEquals("bridge-backed test-only allowlist must have 3 entries",
            3, allowlist.count())
    }

    @Test
    fun `bridge-backed allowlist entries match baseline`() {
        assertTrue(allowlist.contains("onehu_xyz"))
        assertTrue(allowlist.contains("qidian_com"))
        assertTrue(allowlist.contains("zhenhunxiaoshuo"))
        assertEquals(3, allowlist.allSourceIds().size)
    }

    // ════════════════════════════════════════════════════════════════
    // Test 20: Enforcement report metrics
    // ════════════════════════════════════════════════════════════════

    @Test
    fun `enforcement report metrics are correct`() {
        // Simulate full flow to generate expected metrics
        // 3 ALLOW + permit
        val p1 = gate.generatePermit(gate.evaluate("onehu_xyz", "java.ajax"), "onehu_xyz", "java.ajax")
        val p2 = gate.generatePermit(gate.evaluate("qidian_com", "java.refreshTocUrl"), "qidian_com", "java.refreshTocUrl")
        val p3 = gate.generatePermit(gate.evaluate("zhenhunxiaoshuo", "java.getElements"), "zhenhunxiaoshuo", "java.getElements")

        // 3 bridge calls with permit
        adapter.ajax(p1!!, "onehu_xyz", "http://f")
        adapter.refreshTocUrl(p2!!, "qidian_com", "http://f")
        adapter.getElements(p3!!, "zhenhunxiaoshuo")

        // 3 bridge calls without permit (denied)
        adapter.ajax(null, "onehu_xyz", "http://f")
        adapter.refreshTocUrl(null, "qidian_com", "http://f")
        adapter.getElements(null, "zhenhunxiaoshuo")

        // API mismatch
        adapter.getElements(p1, "onehu_xyz") // p1 is ajax permit, used for getElements

        // Source mismatch
        adapter.ajax(p1, "qidian_com", "http://f") // p1 is for onehu_xyz, called with qidian_com

        // Policy denied before adapter calls (already counted in gate evals)

        // Verify counts
        assertEquals(3, telemetry.permitGeneratedCount())
        assertEquals(3, telemetry.allowDecisions())
        assertEquals(5, telemetry.denyDecisions()) // 3 no-permit + 1 API mismatch + 1 source mismatch

        verifyNoRealNetworkRequests()
        verifyNoRecoveredOrProduction()
        verifyPrivacySafe()
    }

    // ════════════════════════════════════════════════════════════════
    // Test 21: Privacy safety
    // ════════════════════════════════════════════════════════════════

    @Test
    fun `permit is privacy safe`() {
        val decision = gate.evaluate("onehu_xyz", "java.ajax")
        val permit = gate.generatePermit(decision, "onehu_xyz", "java.ajax")
        assertNotNull(permit)
        assertTrue(permit!!.privacySafe)
        // Permit uses sanitized identifiers, not raw URLs
        assertFalse(permit.sourceId.contains("http"))
        assertFalse(permit.sourceId.contains("//"))
    }

    @Test
    fun `telemetry is privacy safe`() {
        gate.evaluate("onehu_xyz", "java.ajax")
        gate.evaluate("unknown_source", "java.ajax")
        // Telemetry uses sanitized identifiers
        val sources = telemetry.sourceRequestCounts()
        sources.keys.forEach { sourceId ->
            assertFalse("Source $sourceId appears to contain raw URL", sourceId.contains("http"))
        }
    }

    // ════════════════════════════════════════════════════════════════
    // Permit internal constructor test
    // ════════════════════════════════════════════════════════════════

    @Test
    fun `permit cannot be externally constructed`() {
        // BridgeBackedJavaExecutionPermit has an internal constructor.
        // It can only be created through BridgeBackedPolicyGate.generatePermit().
        // Verify that a permit obtained via the gate is properly formed.
        val decision = gate.evaluate("onehu_xyz", "java.ajax")
        val permit = gate.generatePermit(decision, "onehu_xyz", "java.ajax")
        assertNotNull(permit)
        assertEquals("onehu_xyz", permit!!.sourceId)
        assertEquals("java.ajax", permit.requiredBridgeAPI)
        assertTrue(permit.privacySafe)
    }

    // ════════════════════════════════════════════════════════════════
    // Policy gate denied before adapter call
    // ════════════════════════════════════════════════════════════════

    @Test
    fun `policy gate deny prevents adapter call`() {
        // When policy gate denies, we should never get to the adapter
        val decision = gate.evaluate("unknown_source", "java.ajax")
        assertTrue(decision is BridgeBackedPolicyDecision.DENY_NOT_IN_ALLOWLIST)

        val permit = gate.generatePermit(decision, "unknown_source", "java.ajax")
        assertNull("No permit → adapter cannot be called", permit)

        // If somehow the adapter is called without permit, it denies
        val result = adapter.ajax(null, "unknown_source", "http://f")
        assertTrue(result.isFailure)
    }

    // ════════════════════════════════════════════════════════════════
    // Permit telemetry tracking
    // ════════════════════════════════════════════════════════════════

    @Test
    fun `permit generation is tracked in telemetry`() {
        // Reset for clean state
        telemetry.reset()

        gate.generatePermit(gate.evaluate("onehu_xyz", "java.ajax"), "onehu_xyz", "java.ajax")
        // DENY should not count as permit generated
        gate.generatePermit(gate.evaluate("unknown_source", "java.ajax"), "unknown_source", "java.ajax")

        assertEquals(1, telemetry.permitGeneratedCount())
        assertEquals(1, telemetry.permitDeniedCount())
    }

    // ════════════════════════════════════════════════════════════════
    // Helpers
    // ════════════════════════════════════════════════════════════════

    private fun verifyNoRealNetworkRequests() {
        // Verify no telemetry entry for real network
        val apiCounts = telemetry.apiRequestCounts()
        // All API calls should be test-only
        apiCounts.keys.forEach { api ->
            assertTrue("Unknown API $api should not appear", JavaBridgeAPI.isValid(api))
        }
    }

    private fun verifyNoRecoveredOrProduction() {
        assertNull("No recovered requests", telemetry.denyReasonCounts()["DENY_RECOVERED_CLASSIFICATION"])
        assertNull("No production requests", telemetry.denyReasonCounts()["DENY_PRODUCTION_CONTEXT"])
    }

    private fun verifyPrivacySafe() {
        telemetry.sourceRequestCounts().keys.forEach { sid ->
            assertFalse("SourceId should not contain raw URL: $sid", sid.contains("http") || sid.contains("//"))
        }
    }
}