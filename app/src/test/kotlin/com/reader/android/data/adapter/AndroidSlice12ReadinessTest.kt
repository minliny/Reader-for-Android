package com.reader.android.data.adapter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidSlice12ReadinessTest {

    @Test
    fun `matrix distinguishes local infrastructure from device completion`() {
        assertEquals(
            AndroidSlice12CapabilityStatus.LOCAL_GATE_AVAILABLE,
            AndroidSlice12CapabilityMatrix.status("release.identity")
        )
        assertEquals(
            AndroidSlice12CapabilityStatus.BLOCKED_MISSING_CORE_OWNER,
            AndroidSlice12CapabilityMatrix.status("settings.owner")
        )
        assertEquals(
            AndroidSlice12CapabilityStatus.BLOCKED_DEVICE_EVIDENCE,
            AndroidSlice12CapabilityMatrix.status("slice12.device")
        )
    }

    @Test
    fun `release gate blocks every missing identity and evidence domain`() {
        val decision = AndroidSlice12ReleaseGate.evaluate(emptyInputs())

        assertFalse(decision.ready)
        assertTrue(decision.blockers.any { it.contains("source SHA") })
        assertTrue(decision.blockers.any { it.contains("Core artifact") })
        assertTrue(decision.blockers.any { it.contains("slice-9") })
        assertTrue(decision.blockers.any { it.contains("TalkBack") })
        assertTrue(decision.blockers.any { it.contains("rollback") })
    }

    @Test
    fun `release gate rejects stale consumer lock even with device evidence`() {
        val source = "a".repeat(40)
        val manifest = "b".repeat(64)
        val decision = AndroidSlice12ReleaseGate.evaluate(
            passingInputs().copy(
                readerUiSourceSha = source,
                readerUiManifestSha = manifest,
                consumerSourceSha = "c".repeat(40),
                consumerManifestSha = "d".repeat(64)
            )
        )

        assertFalse(decision.ready)
        assertTrue(decision.blockers.contains("consumer source SHA does not match Reader UI source"))
        assertTrue(decision.blockers.contains("consumer manifest SHA does not match Reader UI manifest"))
    }

    @Test
    fun `release gate rejects dirty UI source and unverified manifest files`() {
        val decision = AndroidSlice12ReleaseGate.evaluate(
            passingInputs().copy(
                readerUiWorktreeClean = false,
                readerUiManifestFilesVerified = false
            )
        )

        assertFalse(decision.ready)
        assertTrue(decision.blockers.any { it.contains("worktree") })
        assertTrue(decision.blockers.any { it.contains("file hashes") })
    }

    @Test
    fun `release gate admits only complete recomputable physical evidence`() {
        val decision = AndroidSlice12ReleaseGate.evaluate(passingInputs())

        assertTrue(decision.ready)
        assertTrue(decision.blockers.isEmpty())
    }

    private fun emptyInputs() = AndroidSlice12ReleaseInputs(
        readerUiSourceSha = null,
        readerUiManifestSha = null,
        readerCoreArtifactSha = null,
        consumerLockSha = null,
        consumerSourceSha = null,
        consumerManifestSha = null,
        readerUiWorktreeClean = false,
        readerUiManifestFilesVerified = false,
        evidenceManifestSchemaVerified = false,
        slice9Passed = false,
        slice10Passed = false,
        slice11Passed = false,
        slice12Passed = false,
        completeJourneyDeviceEvidence = false,
        talkBackEvidence = false,
        performanceEvidence = false,
        rollbackEvidence = false
    )

    private fun passingInputs(): AndroidSlice12ReleaseInputs {
        val source = "a".repeat(40)
        val manifest = "b".repeat(64)
        return AndroidSlice12ReleaseInputs(
            readerUiSourceSha = source,
            readerUiManifestSha = manifest,
            readerCoreArtifactSha = "c".repeat(64),
            consumerLockSha = "d".repeat(64),
            consumerSourceSha = source,
            consumerManifestSha = manifest,
            readerUiWorktreeClean = true,
            readerUiManifestFilesVerified = true,
            evidenceManifestSchemaVerified = true,
            slice9Passed = true,
            slice10Passed = true,
            slice11Passed = true,
            slice12Passed = true,
            completeJourneyDeviceEvidence = true,
            talkBackEvidence = true,
            performanceEvidence = true,
            rollbackEvidence = true
        )
    }
}
