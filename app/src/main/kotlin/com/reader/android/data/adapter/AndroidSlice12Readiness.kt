package com.reader.android.data.adapter

enum class AndroidSlice12CapabilityStatus {
    LOCAL_GATE_AVAILABLE,
    HOST_CAPABILITY_AVAILABLE,
    PARTIAL_LOCAL_IMPLEMENTATION,
    BLOCKED_MISSING_CORE_OWNER,
    BLOCKED_MISSING_LIFECYCLE_RESTORE,
    BLOCKED_MISSING_FORM_FACTOR_PROOF,
    BLOCKED_RELEASE_DEPENDENCY,
    BLOCKED_DEVICE_EVIDENCE
}

data class AndroidSlice12CapabilityEntry(
    val capability: String,
    val status: AndroidSlice12CapabilityStatus,
    val detail: String
)

/**
 * Executable local truth for Slice 12. A Compose route is not considered
 * complete lifecycle, accessibility, form-factor, or release evidence.
 */
object AndroidSlice12CapabilityMatrix {
    val entries: List<AndroidSlice12CapabilityEntry> = listOf(
        AndroidSlice12CapabilityEntry("permission.host", AndroidSlice12CapabilityStatus.HOST_CAPABILITY_AVAILABLE, "permission check/request/settings handlers exist; permanent-denial journey remains device-gated"),
        AndroidSlice12CapabilityEntry("system.integration", AndroidSlice12CapabilityStatus.HOST_CAPABILITY_AVAILABLE, "background/notification/share/clipboard handlers exist and remain business-entry evidence-gated"),
        AndroidSlice12CapabilityEntry("reduced-motion", AndroidSlice12CapabilityStatus.LOCAL_GATE_AVAILABLE, "system resolver plus duration collapse has JVM coverage"),
        AndroidSlice12CapabilityEntry("accessibility.semantics", AndroidSlice12CapabilityStatus.PARTIAL_LOCAL_IMPLEMENTATION, "canonical components project semantics but TalkBack focus journeys are absent"),
        AndroidSlice12CapabilityEntry("responsive.phone-tablet", AndroidSlice12CapabilityStatus.PARTIAL_LOCAL_IMPLEMENTATION, "viewport classes and responsive branches compile; rotation/safe-area/device proof is absent"),
        AndroidSlice12CapabilityEntry("settings.owner", AndroidSlice12CapabilityStatus.BLOCKED_MISSING_CORE_OWNER, "multiple settings screens still hold local fixture/default state without one frozen owner/migration result"),
        AndroidSlice12CapabilityEntry("process.restore", AndroidSlice12CapabilityStatus.BLOCKED_MISSING_LIFECYCLE_RESTORE, "complete navigation/session/async transaction restoration after process death is not frozen or proven"),
        AndroidSlice12CapabilityEntry("fold.posture", AndroidSlice12CapabilityStatus.BLOCKED_MISSING_FORM_FACTOR_PROOF, "no fold posture adapter or device evidence"),
        AndroidSlice12CapabilityEntry("release.identity", AndroidSlice12CapabilityStatus.LOCAL_GATE_AVAILABLE, "scripts/verify_slice12_release_readiness.mjs recomputes source/manifest/Core/consumer identities"),
        AndroidSlice12CapabilityEntry("slice9-11.dependencies", AndroidSlice12CapabilityStatus.BLOCKED_RELEASE_DEPENDENCY, "Slice 9-11 Android and three-platform execution manifests are not passed"),
        AndroidSlice12CapabilityEntry("slice12.device", AndroidSlice12CapabilityStatus.BLOCKED_DEVICE_EVIDENCE, "complete journey, TalkBack, performance, form-factor, migration, and rollback evidence are absent")
    )

    init {
        check(entries.map { it.capability }.toSet().size == entries.size)
    }

    fun status(capability: String): AndroidSlice12CapabilityStatus? =
        entries.singleOrNull { it.capability == capability }?.status
}

data class AndroidSlice12ReleaseInputs(
    val readerUiSourceSha: String?,
    val readerUiManifestSha: String?,
    val readerCoreArtifactSha: String?,
    val consumerLockSha: String?,
    val consumerSourceSha: String?,
    val consumerManifestSha: String?,
    val readerUiWorktreeClean: Boolean,
    val readerUiManifestFilesVerified: Boolean,
    val evidenceManifestSchemaVerified: Boolean,
    val slice9Passed: Boolean,
    val slice10Passed: Boolean,
    val slice11Passed: Boolean,
    val slice12Passed: Boolean,
    val completeJourneyDeviceEvidence: Boolean,
    val talkBackEvidence: Boolean,
    val performanceEvidence: Boolean,
    val rollbackEvidence: Boolean
)

data class AndroidSlice12ReleaseDecision(
    val ready: Boolean,
    val blockers: List<String>
)

/**
 * Pure admission rule shared by JVM tests and the file-system verifier.
 * Missing or malformed evidence always blocks; no caller can self-report a
 * tag or summary as a release identity.
 */
object AndroidSlice12ReleaseGate {
    private val sha256 = Regex("^[0-9a-f]{64}$")
    private val sourceSha = Regex("^(?:[0-9a-f]{40}|[0-9a-f]{64})$")

    fun evaluate(input: AndroidSlice12ReleaseInputs): AndroidSlice12ReleaseDecision {
        val blockers = buildList {
            if (input.readerUiSourceSha?.matches(sourceSha) != true) add("reader-ui source SHA is missing or invalid")
            if (input.readerUiManifestSha?.matches(sha256) != true) add("Reader UI manifest SHA-256 is missing or invalid")
            if (input.readerCoreArtifactSha?.matches(sha256) != true) add("Reader Core artifact SHA-256 is missing or invalid")
            if (input.consumerLockSha?.matches(sha256) != true) add("Android consumer lock SHA-256 is missing or invalid")
            if (!input.readerUiWorktreeClean) add("Reader UI worktree is not immutable/clean")
            if (!input.readerUiManifestFilesVerified) add("Reader UI manifest file hashes are not verified")
            if (input.consumerSourceSha != input.readerUiSourceSha) add("consumer source SHA does not match Reader UI source")
            if (input.consumerManifestSha != input.readerUiManifestSha) add("consumer manifest SHA does not match Reader UI manifest")
            if (!input.evidenceManifestSchemaVerified) add("Android execution evidence manifest is missing or schema-invalid")
            if (!input.slice9Passed) add("slice-9 is not passed")
            if (!input.slice10Passed) add("slice-10 is not passed")
            if (!input.slice11Passed) add("slice-11 is not passed")
            if (!input.slice12Passed) add("slice-12 is not passed")
            if (!input.completeJourneyDeviceEvidence) add("complete physical-device journey evidence is missing")
            if (!input.talkBackEvidence) add("TalkBack evidence is missing")
            if (!input.performanceEvidence) add("performance evidence is missing")
            if (!input.rollbackEvidence) add("upgrade/downgrade rollback evidence is missing")
        }
        return AndroidSlice12ReleaseDecision(ready = blockers.isEmpty(), blockers = blockers)
    }
}
