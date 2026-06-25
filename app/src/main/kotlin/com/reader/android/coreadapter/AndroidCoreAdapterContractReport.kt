package com.reader.android.coreadapter

import com.reader.android.data.nativebridge.ReaderNativeRuntimeBridge
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
        "runtimeHost",
        "readerShell"
    )

    val RUNTIME_CI_EVIDENCE_IDS = listOf(
        ReaderNativeRuntimeBridge.NDK_PACKAGING_EVIDENCE_ID,
        ReaderNativeRuntimeBridge.SYSTEM_LOAD_LIBRARY_EVIDENCE_ID,
        ReaderNativeRuntimeBridge.HOST_BUS_LOOP_EVIDENCE_ID,
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
    val requiredFeatureIds: List<String>,
    val platformInputs: List<String>,
    val requiredEvidenceArtifacts: List<String>,
    val coreBoundary: String,
    val execution: AdapterExecutionProof,
    val sensitiveDataPolicy: String = "No credentials, private content, raw cookie values, or user file contents are exported."
)

data class RuntimeCiEvidenceDescriptor(
    val evidenceId: String,
    val status: String,
    val redactionPolicy: String = "noCredentialsNoPrivateContentNoRawCookieValues",
    val claim: String
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
                    contractStatus = "deviceReadyLocalMeasured",
                    implementationRefs = listOf(
                        "com.reader.android.data.adapter.EpubContainerParser",
                        "com.reader.android.data.adapter.OpfParser",
                        "com.reader.android.data.adapter.EpubInventory"
                    ),
                    requiredFeatureIds = AndroidCoreFeatureManifest.requiredFeatureIds.getValue("archive"),
                    platformInputs = listOf(
                        "EPUB container XML descriptor",
                        "OPF package metadata descriptor",
                        "Archive inventory model"
                    ),
                    requiredEvidenceArtifacts = requiredArtifactNames,
                    coreBoundary = "Android owns archive/file access handoff; Core consumes public local-book/archive descriptors.",
                    execution = AdapterExecutionProof(
                        status = "measuredPass",
                        proofScope = "Local EPUB container, OPF, reading-order, and safe-entry path evidence is exported without raw archive contents.",
                        mayFeedCoreEvidence = true
                    )
                ),
                AndroidAdapterContract(
                    kind = "localFileAccess",
                    platformOwner = "Android",
                    contractStatus = "deviceReadyLocalMeasured",
                    implementationRefs = listOf(
                        "com.reader.android.data.adapter.LocalBookSource",
                        "com.reader.android.data.adapter.LocalBookFormat",
                        "com.reader.android.data.adapter.LocalBookImportResult",
                        "com.reader.android.data.adapter.AndroidContentUriLocalBookAdapter"
                    ),
                    requiredFeatureIds = AndroidCoreFeatureManifest.requiredFeatureIds.getValue("localFileAccess"),
                    platformInputs = listOf(
                        "content:// URI descriptor",
                        "file URI or path descriptor",
                        "persistable read-permission requirement",
                        "display-name and MIME metadata"
                    ),
                    requiredEvidenceArtifacts = requiredArtifactNames,
                    coreBoundary = "Android owns ContentResolver/SAF permission handling; Core should receive redacted local-book descriptors.",
                    execution = AdapterExecutionProof(
                        status = "deviceReadyLocalMeasured",
                        proofScope = "Redacted content URI, persistable read permission, and denied-permission mapping evidence is locally measured; real SAF picker smoke remains instrumented-host evidence.",
                        mayFeedCoreEvidence = true
                    )
                ),
                AndroidAdapterContract(
                    kind = "markupParser",
                    platformOwner = "Android",
                    contractStatus = "jsoupLocalMeasured",
                    implementationRefs = listOf(
                        "com.reader.android.data.adapter.JsoupMarkupParserAdapter",
                        "com.reader.android.data.adapter.OpfParser",
                        "com.reader.android.data.adapter.WebDavXmlParser"
                    ),
                    requiredFeatureIds = AndroidCoreFeatureManifest.requiredFeatureIds.getValue("markupParser"),
                    platformInputs = listOf(
                        "HTML CSS selector fixture",
                        "bounded HTML XPath descriptor",
                        "bounded XML XPath descriptor",
                        "OPF XML descriptor",
                        "WebDAV multistatus XML descriptor"
                    ),
                    requiredEvidenceArtifacts = requiredArtifactNames,
                    coreBoundary = "Android owns host-side XML adapter proof; Core-owned parser behavior is not reimplemented here.",
                    execution = AdapterExecutionProof(
                        status = "measuredPass",
                        proofScope = "jsoup adapter exports CSS, bounded XPath, XML, and attribute extraction evidence with script/style suppression.",
                        mayFeedCoreEvidence = true
                    )
                ),
                AndroidAdapterContract(
                    kind = "feedParser",
                    platformOwner = "Android",
                    contractStatus = "okHttpFixtureLocalMeasured",
                    implementationRefs = listOf(
                        "com.reader.android.data.network.OkHttpTransport",
                        "com.reader.android.data.network.RssParser",
                        "com.reader.android.data.network.RssFeed",
                        "com.reader.android.data.network.RssSubscription"
                    ),
                    requiredFeatureIds = AndroidCoreFeatureManifest.requiredFeatureIds.getValue("feedParser"),
                    platformInputs = listOf(
                        "RSS 2.0 XML descriptor",
                        "Atom XML descriptor",
                        "JSON Feed descriptor",
                        "subscription metadata descriptor"
                    ),
                    requiredEvidenceArtifacts = requiredArtifactNames,
                    coreBoundary = "Android owns feed fetch/subscription host integration; Core receives feed/parser evidence artifacts only.",
                    execution = AdapterExecutionProof(
                        status = "measuredPass",
                        proofScope = "RSS, Atom, JSON Feed, pagination metadata, and cookie-login diagnostic evidence is local fixture measured; no live network fetch.",
                        mayFeedCoreEvidence = true
                    )
                ),
                AndroidAdapterContract(
                    kind = "textEncodingDetector",
                    platformOwner = "Android",
                    contractStatus = "deviceReadyLocalMeasured",
                    implementationRefs = listOf(
                        "com.reader.android.data.adapter.EncodingDetector",
                        "com.reader.android.data.adapter.EncodingResult"
                    ),
                    requiredFeatureIds = AndroidCoreFeatureManifest.requiredFeatureIds.getValue("textEncodingDetector"),
                    platformInputs = listOf(
                        "TXT byte prefix descriptor",
                        "BOM and high-byte-ratio evidence"
                    ),
                    requiredEvidenceArtifacts = requiredArtifactNames,
                    coreBoundary = "Android owns native TXT byte access and detector evidence; Core consumes the reported encoding decision.",
                    execution = AdapterExecutionProof(
                        status = "measuredPass",
                        proofScope = "UTF BOM, UTF-8, GB18030/GBK heuristic, and fallback fixtures are locally measured.",
                        mayFeedCoreEvidence = true
                    )
                ),
                AndroidAdapterContract(
                    kind = "runtimeHost",
                    platformOwner = "Android",
                    contractStatus = "deviceExecutedInstrumented",
                    implementationRefs = listOf(
                        "com.reader.android.data.adapter.AndroidWebViewRuntimeHost",
                        "com.reader.android.data.adapter.AndroidWebRuntimeAdapter",
                        "com.reader.android.data.adapter.AndroidCookieManagerStore",
                        "com.reader.android.data.adapter.AndroidKeystoreCredentialStore",
                        "com.reader.android.data.adapter.WebDavCredentialStore",
                        "com.reader.android.data.network.OkHttpAdapter",
                        "com.reader.android.data.network.ScopedOkHttpCookieJar",
                        "com.reader.android.data.adapter.WebRuntimeAdapter",
                        "com.reader.android.data.adapter.CookieStore",
                        "com.reader.android.data.adapter.RuntimeScope",
                        "com.reader.android.data.adapter.JsRequest",
                        "com.reader.android.data.adapter.JsResponse",
                        "com.reader.android.data.nativebridge.ReaderNativeRuntimeBridge",
                        "com.reader.android.data.nativebridge.ReaderNativeRuntimeJni",
                        "app/src/main/cpp/reader_native_runtime_evidence.cpp"
                    ),
                    requiredFeatureIds = AndroidCoreFeatureManifest.requiredFeatureIds.getValue("runtimeHost"),
                    platformInputs = listOf(
                        "WebView runtime descriptor",
                        "CookieManager mirror descriptor",
                        "Keystore-backed secret descriptor",
                        "per-source runtime isolation descriptor",
                        "NDK shared-object packaging descriptor",
                        "System.loadLibrary(\"reader_native_runtime_evidence\") descriptor",
                        "JNI host bus loop probe descriptor"
                    ),
                    requiredEvidenceArtifacts = requiredArtifactNames,
                    coreBoundary = "Android owns WebView, CookieManager, Keystore, NDK/JNI loading, and host bus loop smoke execution; no Reader-Core protocol mutation is allowed from this repo.",
                    execution = AdapterExecutionProof(
                        status = "deviceInstrumentedPlusNativeReady",
                        proofScope = "Existing instrumented runtime smoke covers WebView DOM, evaluateJavascript, CookieManager redacted mirror, AndroidKeyStore save/load/revoke, and SAF denied-permission redaction. Native evidence adds CMake-built libreader_native_runtime_evidence.so, explicit System.loadLibrary, and AndroidNativeRuntimeInstrumentedSmokeTest for JNI host bus loop execution; JVM unit smoke is explicitly not device evidence.",
                        mayFeedCoreEvidence = true
                    )
                ),
                AndroidAdapterContract(
                    kind = "readerShell",
                    platformOwner = "Android",
                    contractStatus = "measuredPass",
                    implementationRefs = listOf(
                        "com.reader.android.data.adapter.ReaderShellHost",
                        "com.reader.android.data.adapter.ComposeReaderShellHost",
                        "com.reader.android.data.adapter.RenderRequest",
                        "com.reader.android.data.adapter.RenderResult",
                        "com.reader.android.data.adapter.ReaderContentType",
                    ),
                    requiredFeatureIds = AndroidCoreFeatureManifest.requiredFeatureIds.getValue("readerShell"),
                    platformInputs = listOf(
                        "TXT pagination descriptor",
                        "HTML sanitization descriptor",
                        "CSP-locked render descriptor",
                        "EPUB-CFI future slot descriptor"
                    ),
                    requiredEvidenceArtifacts = requiredArtifactNames,
                    coreBoundary = "Android owns the clean-room reader-shell host (text pagination + jsoup HTML sanitization + CSP, JS off by default). No Readium dependency; EPUB-CFI is a future opt-in behind the same contract.",
                    execution = AdapterExecutionProof(
                        status = "measuredPass",
                        proofScope = "TXT pagination, HTML script/iframe stripping, CSP default-src 'none' with script-src 'none', JS-disabled default, and CFI-future-slot null are locally measured against fixtures; no live network.",
                        mayFeedCoreEvidence = true
                    )
                )
            ),
            runtimeCiEvidence = runtimeCiEvidenceDescriptors()
        )
    }

    private fun runtimeCiEvidenceDescriptors(): List<RuntimeCiEvidenceDescriptor> =
        listOf(
            RuntimeCiEvidenceDescriptor(
                evidenceId = ReaderNativeRuntimeBridge.NDK_PACKAGING_EVIDENCE_ID,
                status = "apkNativePackagingMeasured",
                claim = "AGP externalNativeBuild.cmake builds libreader_native_runtime_evidence.so for arm64-v8a and x86_64 and packages it into the app APK native library directory."
            ),
            RuntimeCiEvidenceDescriptor(
                evidenceId = ReaderNativeRuntimeBridge.SYSTEM_LOAD_LIBRARY_EVIDENCE_ID,
                status = "deviceExecutedInstrumented",
                claim = "ReaderNativeRuntimeBridge.loadLibraryForApp calls System.loadLibrary(\"reader_native_runtime_evidence\"); this passed on Pixel_10_Pro_XL AVD, while JVM UnsatisfiedLinkError is recorded as JVM_HOST_NOT_DEVICE and is not device completion."
            ),
            RuntimeCiEvidenceDescriptor(
                evidenceId = ReaderNativeRuntimeBridge.HOST_BUS_LOOP_EVIDENCE_ID,
                status = "deviceExecutedInstrumented",
                claim = "AndroidNativeRuntimeInstrumentedSmokeTest passed on Pixel_10_Pro_XL AVD and proved JNI host bus loop dispatch through the loaded shared object."
            ),
            RuntimeCiEvidenceDescriptor(
                evidenceId = "credential_redaction_revocation_matrix",
                status = "deviceExecutedInstrumented",
                claim = "Android runtime seam executed on Pixel_10_Pro_XL AVD: AndroidKeyStore save/load/revoke smoke passed with no credential export."
            ),
            RuntimeCiEvidenceDescriptor(
                evidenceId = "product_gated_js_bridge_release_runner",
                status = "deviceExecutedInstrumented",
                claim = "Android runtime seam executed on Pixel_10_Pro_XL AVD: product-gated JavaScript bridge policy remained enforced."
            ),
            RuntimeCiEvidenceDescriptor(
                evidenceId = "runtime_rollback_audit",
                status = "deviceExecutedInstrumented",
                claim = "Android runtime seam executed on Pixel_10_Pro_XL AVD: rollback/snapshot boundary smoke passed with redacted output."
            ),
            RuntimeCiEvidenceDescriptor(
                evidenceId = "secure_storage_platform_audit",
                status = "deviceExecutedInstrumented",
                claim = "Android runtime seam executed on Pixel_10_Pro_XL AVD: secure storage platform audit passed with no raw secret export."
            ),
            RuntimeCiEvidenceDescriptor(
                evidenceId = "session_cookie_login_platform_runner",
                status = "deviceExecutedInstrumented",
                claim = "Android runtime seam executed on Pixel_10_Pro_XL AVD: session cookie login runner passed with redacted cookie values."
            ),
            RuntimeCiEvidenceDescriptor(
                evidenceId = "webview_cookie_mirror_audit",
                status = "deviceExecutedInstrumented",
                claim = "Android runtime seam executed on Pixel_10_Pro_XL AVD: CookieManager mirror exported counts only."
            ),
            RuntimeCiEvidenceDescriptor(
                evidenceId = "webview_dom_platform_smoke_runner",
                status = "deviceExecutedInstrumented",
                claim = "Android runtime seam executed on Pixel_10_Pro_XL AVD: WebView DOM and evaluateJavascript smoke passed."
            )
        )
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
        put("requiredFeatureIds", requiredFeatureIds.toJsonArray())
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
