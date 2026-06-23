package com.reader.android.coreadapter

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidLegadoParityEvidenceRunnerTest {

    private val bundle = AndroidLegadoParityEvidenceRunner.buildDeviceExecutedInstrumentedBundle()

    @Test
    fun `complete evidence covers every Core Android manifest feature`() {
        val expectedKeys = AndroidCoreFeatureManifest.requiredFeatureIds.flatMap { (kind, featureIds) ->
            featureIds.map { "$kind:$it" }
        }.toSet()
        val passedKeys = bundle.evidenceCases.filter { it.passed }.map { "${it.kind}:${it.featureId}" }.toSet()

        assertEquals(expectedKeys, passedKeys)
        assertTrue(bundle.missingRequiredFeatureKeys.isEmpty())
        assertTrue(bundle.canFeedCorePlatformEvidence)
    }

    @Test
    fun `runtime evidence records device executed instrumented smoke`() {
        val runtime = bundle.runtimeHostEvidence

        assertEquals("DEVICE_EXECUTED_INSTRUMENTED", runtime.executionMode)
        assertTrue(runtime.deviceExecutorReady)
        assertTrue(runtime.deviceExecutorUsed)
        assertFalse(runtime.externalNetworkUsed)
        assertEquals(AndroidCoreAdapterContractIds.RUNTIME_CI_EVIDENCE_IDS, runtime.runtimeEvidenceIds)
    }

    @Test
    fun `json export redacts sensitive material and keeps Core root immutable`() {
        val json = bundle.toJson()
        val obj = JSONObject(json)

        assertEquals("Android", obj.getString("platformFamily"))
        assertEquals("DEVICE_EXECUTED_INSTRUMENTED", obj.getString("parityStatus"))
        assertTrue(obj.getBoolean("canFeedCorePlatformEvidence"))
        assertFalse(obj.getBoolean("readerCoreRootArtifactsMutated"))
        assertFalse(obj.getBoolean("canMutateProductionReleaseGate"))
        assertFalse(json.contains("Set-Cookie:"))
        assertFalse(json.contains("password="))
        assertFalse(json.contains("token='secret'"))
        assertFalse(json.contains("https://example.invalid/ch2"))
    }

    @Test
    fun `contract report now binds required feature ids for Core intake`() {
        val report = AndroidCoreAdapterContractReportFactory.defaultReport()

        report.adapterContracts.forEach { adapter ->
            assertEquals(
                AndroidCoreFeatureManifest.requiredFeatureIds.getValue(adapter.kind),
                adapter.requiredFeatureIds
            )
            assertTrue(adapter.execution.mayFeedCoreEvidence)
        }
    }
}
