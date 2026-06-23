package com.reader.android.coreadapter

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidCoreAdapterContractReportTest {

    private val report = AndroidCoreAdapterContractReportFactory.defaultReport()

    @Test
    fun `default report uses Reader-Core Android runner identity`() {
        assertEquals("Android", report.platformFamily)
        assertEquals(
            "reader-core.android.adapter.contract-runner",
            report.runnerIdentifier
        )
        assertEquals(
            listOf("metadata", "expected", "matrix", "regressionResult"),
            report.requiredEvidenceArtifacts.map { it.name }
        )
    }

    @Test
    fun `default report covers the required adapter kinds in order`() {
        assertEquals(
            listOf(
                "archive",
                "localFileAccess",
                "markupParser",
                "feedParser",
                "textEncodingDetector",
                "runtimeHost"
            ),
            report.adapterContracts.map { it.kind }
        )
    }

    @Test
    fun `every adapter binds the required evidence artifact names`() {
        val artifactNames = report.requiredEvidenceArtifacts.map { it.name }

        report.adapterContracts.forEach { adapter ->
            assertEquals(
                "Adapter ${adapter.kind} must export Core-aligned evidence artifact slots",
                artifactNames,
                adapter.requiredEvidenceArtifacts
            )
            assertEquals("Android", adapter.platformOwner)
            assertTrue(adapter.coreBoundary.isNotBlank())
            assertTrue(adapter.execution.mayFeedCoreEvidence)
        }
    }

    @Test
    fun `runtime CI evidence ids are descriptor only until Android smoke runs`() {
        assertEquals(
            listOf(
                "credential_redaction_revocation_matrix",
                "product_gated_js_bridge_release_runner",
                "runtime_rollback_audit",
                "secure_storage_platform_audit",
                "session_cookie_login_platform_runner",
                "webview_cookie_mirror_audit",
                "webview_dom_platform_smoke_runner"
            ),
            report.runtimeCiEvidence.map { it.evidenceId }
        )
        report.runtimeCiEvidence.forEach { evidence ->
            assertEquals("notExecuted", evidence.status)
            assertTrue(evidence.claim.contains("no Android runtime smoke"))
            assertEquals("noCredentialsNoPrivateContentNoRawCookieValues", evidence.redactionPolicy)
        }
    }

    @Test
    fun `local file access remains a content URI permission descriptor`() {
        val localFileAccess = report.adapterContracts.single { it.kind == "localFileAccess" }

        assertEquals("descriptorOnly", localFileAccess.contractStatus)
        assertEquals("notExecuted", localFileAccess.execution.status)
        assertTrue(localFileAccess.platformInputs.contains("content:// URI descriptor"))
        assertTrue(localFileAccess.platformInputs.contains("persistable read-permission requirement"))
        assertTrue(localFileAccess.coreBoundary.contains("ContentResolver"))
    }

    @Test
    fun `runtime host does not claim WebView CookieManager or Keystore execution`() {
        val runtimeHost = report.adapterContracts.single { it.kind == "runtimeHost" }

        assertEquals("contractOnly", runtimeHost.contractStatus)
        assertEquals("notExecuted", runtimeHost.execution.status)
        assertTrue(runtimeHost.platformInputs.contains("WebView runtime descriptor"))
        assertTrue(runtimeHost.platformInputs.contains("CookieManager mirror descriptor"))
        assertTrue(runtimeHost.platformInputs.contains("Keystore-backed secret descriptor"))
        assertTrue(runtimeHost.execution.proofScope.contains("no WebView"))
    }

    @Test
    fun `json export preserves contract identity and redaction boundaries`() {
        val json = report.toJson()
        val obj = JSONObject(json)

        assertEquals("Android", obj.getString("platformFamily"))
        assertEquals(
            "reader-core.android.adapter.contract-runner",
            obj.getString("runnerIdentifier")
        )
        assertEquals(6, obj.getJSONArray("adapterContracts").length())
        assertEquals(7, obj.getJSONArray("runtimeCiEvidence").length())
        assertTrue(obj.getJSONObject("cleanRoom").getBoolean("maintained"))
        assertFalse(obj.getJSONObject("cleanRoom").getBoolean("externalGplCodeCopied"))
        assertFalse(json.contains("Cookie:"))
        assertFalse(json.contains("Set-Cookie:"))
        assertFalse(json.contains("password="))
        assertFalse(json.contains("token="))
    }
}
