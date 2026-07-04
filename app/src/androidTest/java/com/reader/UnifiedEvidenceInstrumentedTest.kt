package com.reader

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.reader.api.ReaderCoreClient
import com.reader.host.OkHttpHostTransport
import com.reader.ui.evidence.EvidenceRunner
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test for the Android unified evidence runner.
 *
 * Runs [EvidenceRunner.run] on a simulator/emulator and asserts that the
 * resulting artifact conforms to the unified-evidence/1 schema invariants:
 *  - schemaVersion == "unified-evidence/1"
 *  - platform == "android"
 *  - tier == "simulator"
 *  - capabilities.size >= 15 (all canonical capabilities present)
 *  - summary.total == capabilities.size
 *  - at least runtime.ping status == "pass" (proves Core round-trip via JNI)
 *
 * Layering note (charter §10.3): this is a simulator device proof, NOT a real
 * device proof. Real device proof is deferred to S7.
 */
@RunWith(AndroidJUnit4::class)
class UnifiedEvidenceInstrumentedTest {

    @Before
    fun setUp() {
        // Initialize Core with the production-style HTTP transport. The smoke
        // methods (runtime.ping / runtime.hostSmoke) do not perform real HTTP,
        // so no network is exercised.
        ReaderCoreClient.initForTest(OkHttpHostTransport())
    }

    @After
    fun tearDown() {
        ReaderCoreClient.resetForTest()
    }

    @Test
    fun runProducesValidUnifiedEvidenceArtifact() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val artifact = EvidenceRunner.run(
            tier = "simulator",
            filesDir = context.filesDir
        )

        assertEquals("unified-evidence/1", artifact.schemaVersion)
        assertEquals("android", artifact.platform)
        assertEquals("simulator", artifact.tier)

        assertTrue(
            "capabilities.size >= 15, got ${artifact.capabilities.size}",
            artifact.capabilities.size >= 15
        )

        assertEquals(
            "summary.total must equal capabilities.size",
            artifact.capabilities.size,
            artifact.summary.total
        )

        // All 15 canonical capabilities must be present (defensive — the runner
        // emits exactly 15, but future extensions should not drop canonicals).
        val canonicalCaps = UnifiedEvidenceArtifact_canonicalCapabilities()
        val presentCaps = artifact.capabilities.map { it.capability }.toSet()
        for (cap in canonicalCaps) {
            assertTrue(
                "missing canonical capability: $cap",
                presentCaps.contains(cap)
            )
        }

        // At least runtime.ping should pass — proves the Core round-trip via
        // JNI works on the simulator.
        val ping = artifact.capabilities.first { it.capability == "runtime.ping" }
        assertEquals(
            "runtime.ping status (Core round-trip via JNI)",
            "pass",
            ping.status
        )

        // Verify the JSON serialization round-trips the invariants (catches
        // any drift between the data class and the org.json serializer).
        val json = artifact.toJson()
        assertEquals("unified-evidence/1", json.getString("schemaVersion"))
        assertEquals("android", json.getString("platform"))
        assertEquals("simulator", json.getString("tier"))
        assertEquals(
            artifact.capabilities.size,
            json.getJSONArray("capabilities").length()
        )
    }

    private fun UnifiedEvidenceArtifact_canonicalCapabilities(): List<String> =
        com.reader.ui.evidence.UnifiedEvidenceArtifact.CANONICAL_CAPABILITIES
}
