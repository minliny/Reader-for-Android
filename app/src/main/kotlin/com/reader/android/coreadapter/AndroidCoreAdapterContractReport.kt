package com.reader.android.coreadapter

import org.json.JSONArray
import org.json.JSONObject

object AndroidCoreAdapterContractIds {
    const val PLATFORM_FAMILY = "Android"
    const val RUNNER_IDENTIFIER = "reader-core.android.adapter.contract-runner"

    val REQUIRED_EVIDENCE_ARTIFACTS = listOf(
        "metadata",
        "expected",
        "matrix",
        "regressionResult"
    )

    val ADAPTER_KINDS = listOf(
        "archive",
        "localFileAccess",
        "markupParser",
        "feedParser",
        "textEncodingDetector",
        "runtimeHost"
    )

    val RUNTIME_CI_EVIDENCE_IDS = listOf(
        "credential_redaction_revocation_matrix",
        "product_gated_js_bridge_release_runner",
        "runtime_rollback_audit",
        "secure_storage_platform_audit",
        "session_cookie_login_platform_runner",
        "webview_cookie_mirror_audit",
        "webview_dom_platform_smoke_runner"
    )
}

data class CleanRoomStatement(
    val maintained: Boolean = true,
    val externalGplCodeCopied: Boolean = false,
    val boundary: String = "Android-owned platform adapter descriptors only; no Reader-Core internals or Legado implementation code."
)

data class EvidenceArtifactRequirement(
    val name: String,
    val required: Boolean = true,
    val status: String = "requiredForCoreSampleAlignment"
)

data class AdapterExecutionProof(
    val status: String,
    val proofScope: String,
    val mayFeedCoreEvidence: Boolean
)

data class AndroidAdapterContract(
    val kind: String,
    val platformOwner: String,
    val contractStatus: String,
    val implementationRefs: List<String>,
    val platformInputs: List<String>,
    val requiredEvidenceArtifacts: List<String>,
    val coreBoundary: String,
    val execution: AdapterExecutionProof,
    val sensitiveDataPolicy: String = "No credentials, private content, raw cookie values, or user file contents are exported."
)

data class RuntimeCiEvidenceDescriptor(
    val evidenceId: String,
    val status: String = "notExecuted",
    val redactionPolicy: String = "noCredentialsNoPrivateContentNoRawCookieValues",
    val claim: String = "Descriptor only; no Android runtime smoke was executed by this report."
)

data class AndroidCoreAdapterContractReport(
    val platformFamily: String = AndroidCoreAdapterContractIds.PLATFORM_FAMILY,
    val runnerIdentifier: String = AndroidCoreAdapterContractIds.RUNNER_IDENTIFIER,
    val requiredEvidenceArtifacts: List<EvidenceArtifactRequirement>,
    val adapterContracts: List<AndroidAdapterContract>,
    val runtimeCiEvidence: List<RuntimeCiEvidenceDescriptor>,
    val cleanRoom: CleanRoomStatement = CleanRoomStatement()
) {
    fun toJson(): String = AndroidCoreAdapterContractReportJson.toJson(this)
}

object AndroidCoreAdapterContractReportFactory {
    fun defaultReport(): AndroidCoreAdapterContractReport {
        val requiredArtifactNames = AndroidCoreAdapterContractIds.REQUIRED_EVIDENCE_ARTIFACTS
        return AndroidCoreAdapterContractReport(
            requiredEvidenceArtifacts = requiredArtifactNames.map { EvidenceArtifactRequirement(it) },
            adapterContracts = listOf(
                AndroidAdapterContract(
                    kind = "archive",
                    platformOwner = "Android",
                    contractStatus = "jvmContractTested",
                    implementationRefs = listOf(
                        "com.reader.android.data.adapter.EpubContainerParser",
                        "com.reader.android.data.adapter.OpfParser",
                        "com.reader.android.data.adapter.EpubInventory"
                    ),
                    platformInputs = listOf(
                        "EPUB container XML descriptor",
                        "OPF package metadata descriptor",
                        "Archive inventory model"
                    ),
                    requiredEvidenceArtifacts = requiredArtifactNames,
                    coreBoundary = "Android owns archive/file access handoff; Core consumes public local-book/archive descriptors.",
                    execution = AdapterExecutionProof(
                        status = "jvmContractTested",
                        proofScope = "Parser/model contract tests only; no device file picker or archive IO smoke executed.",
                        mayFeedCoreEvidence = true
                    )
                ),
                AndroidAdapterContract(
                    kind = "localFileAccess",
                    platformOwner = "Android",
                    contractStatus = "descriptorOnly",
                    implementationRefs = listOf(
                        "com.reader.android.data.adapter.LocalBookSource",
                        "com.reader.android.data.adapter.LocalBookFormat",
                        "com.reader.android.data.adapter.LocalBookImportResult"
                    ),
                    platformInputs = listOf(
                        "content:// URI descriptor",
                        "file URI or path descriptor",
                        "persistable read-permission requirement",
                        "display-name and MIME metadata"
                    ),
                    requiredEvidenceArtifacts = requiredArtifactNames,
                    coreBoundary = "Android owns ContentResolver/SAF permission handling; Core should receive redacted local-book descriptors.",
                    execution = AdapterExecutionProof(
                        status = "notExecuted",
                        proofScope = "No ContentResolver, SAF, or device storage smoke executed in this JVM report.",
                        mayFeedCoreEvidence = true
                    )
                ),
                AndroidAdapterContract(
                    kind = "markupParser",
                    platformOwner = "Android",
                    contractStatus = "jvmContractTested",
                    implementationRefs = listOf(
                        "com.reader.android.data.adapter.OpfParser",
                        "com.reader.android.data.adapter.WebDavXmlParser"
                    ),
                    platformInputs = listOf(
                        "OPF XML descriptor",
                        "WebDAV multistatus XML descriptor"
                    ),
                    requiredEvidenceArtifacts = requiredArtifactNames,
                    coreBoundary = "Android owns host-side XML adapter proof; Core-owned parser behavior is not reimplemented here.",
                    execution = AdapterExecutionProof(
                        status = "jvmContractTested",
                        proofScope = "Existing JVM parser tests cover deterministic XML fixture handling.",
                        mayFeedCoreEvidence = true
                    )
                ),
                AndroidAdapterContract(
                    kind = "feedParser",
                    platformOwner = "Android",
                    contractStatus = "jvmContractTested",
                    implementationRefs = listOf(
                        "com.reader.android.data.network.RssParser",
                        "com.reader.android.data.network.RssFeed",
                        "com.reader.android.data.network.RssSubscription"
                    ),
                    platformInputs = listOf(
                        "RSS 2.0 XML descriptor",
                        "subscription metadata descriptor"
                    ),
                    requiredEvidenceArtifacts = requiredArtifactNames,
                    coreBoundary = "Android owns feed fetch/subscription host integration; Core receives feed/parser evidence artifacts only.",
                    execution = AdapterExecutionProof(
                        status = "jvmContractTested",
                        proofScope = "Existing RSS parser tests cover deterministic feed fixtures; no live network fetch.",
                        mayFeedCoreEvidence = true
                    )
                ),
                AndroidAdapterContract(
                    kind = "textEncodingDetector",
                    platformOwner = "Android",
                    contractStatus = "jvmContractTested",
                    implementationRefs = listOf(
                        "com.reader.android.data.adapter.EncodingDetector",
                        "com.reader.android.data.adapter.EncodingResult"
                    ),
                    platformInputs = listOf(
                        "TXT byte prefix descriptor",
                        "BOM and high-byte-ratio evidence"
                    ),
                    requiredEvidenceArtifacts = requiredArtifactNames,
                    coreBoundary = "Android owns native TXT byte access and detector evidence; Core consumes the reported encoding decision.",
                    execution = AdapterExecutionProof(
                        status = "jvmContractTested",
                        proofScope = "Existing detector tests cover UTF BOM, UTF-8 validity, and GBK heuristic fixtures.",
                        mayFeedCoreEvidence = true
                    )
                ),
                AndroidAdapterContract(
                    kind = "runtimeHost",
                    platformOwner = "Android",
                    contractStatus = "contractOnly",
                    implementationRefs = listOf(
                        "com.reader.android.data.adapter.WebRuntimeAdapter",
                        "com.reader.android.data.adapter.CookieStore",
                        "com.reader.android.data.adapter.RuntimeScope",
                        "com.reader.android.data.adapter.JsRequest",
                        "com.reader.android.data.adapter.JsResponse"
                    ),
                    platformInputs = listOf(
                        "WebView runtime descriptor",
                        "CookieManager mirror descriptor",
                        "Keystore-backed secret descriptor",
                        "per-source runtime isolation descriptor"
                    ),
                    requiredEvidenceArtifacts = requiredArtifactNames,
                    coreBoundary = "Android owns WebView, CookieManager, Keystore, and runtime host smoke execution.",
                    execution = AdapterExecutionProof(
                        status = "notExecuted",
                        proofScope = "Only fake/runtime contract models exist here; no WebView, CookieManager, or Keystore smoke executed.",
                        mayFeedCoreEvidence = true
                    )
                )
            ),
            runtimeCiEvidence = AndroidCoreAdapterContractIds.RUNTIME_CI_EVIDENCE_IDS.map {
                RuntimeCiEvidenceDescriptor(evidenceId = it)
            }
        )
    }
}

object AndroidCoreAdapterContractReportJson {
    fun toJson(report: AndroidCoreAdapterContractReport): String = JSONObject().apply {
        put("platformFamily", report.platformFamily)
        put("runnerIdentifier", report.runnerIdentifier)
        put("requiredEvidenceArtifacts", report.requiredEvidenceArtifacts.toJsonArray { it.toJson() })
        put("adapterContracts", report.adapterContracts.toJsonArray { it.toJson() })
        put("runtimeCiEvidence", report.runtimeCiEvidence.toJsonArray { it.toJson() })
        put("cleanRoom", report.cleanRoom.toJson())
    }.toString()

    private fun CleanRoomStatement.toJson(): JSONObject = JSONObject().apply {
        put("maintained", maintained)
        put("externalGplCodeCopied", externalGplCodeCopied)
        put("boundary", boundary)
    }

    private fun EvidenceArtifactRequirement.toJson(): JSONObject = JSONObject().apply {
        put("name", name)
        put("required", required)
        put("status", status)
    }

    private fun AdapterExecutionProof.toJson(): JSONObject = JSONObject().apply {
        put("status", status)
        put("proofScope", proofScope)
        put("mayFeedCoreEvidence", mayFeedCoreEvidence)
    }

    private fun AndroidAdapterContract.toJson(): JSONObject = JSONObject().apply {
        put("kind", kind)
        put("platformOwner", platformOwner)
        put("contractStatus", contractStatus)
        put("implementationRefs", implementationRefs.toJsonArray())
        put("platformInputs", platformInputs.toJsonArray())
        put("requiredEvidenceArtifacts", requiredEvidenceArtifacts.toJsonArray())
        put("coreBoundary", coreBoundary)
        put("execution", execution.toJson())
        put("sensitiveDataPolicy", sensitiveDataPolicy)
    }

    private fun RuntimeCiEvidenceDescriptor.toJson(): JSONObject = JSONObject().apply {
        put("evidenceId", evidenceId)
        put("status", status)
        put("redactionPolicy", redactionPolicy)
        put("claim", claim)
    }

    private fun List<String>.toJsonArray(): JSONArray = JSONArray().apply {
        forEach { put(it) }
    }

    private fun <T> List<T>.toJsonArray(mapper: (T) -> JSONObject): JSONArray = JSONArray().apply {
        forEach { put(mapper(it)) }
    }
}
