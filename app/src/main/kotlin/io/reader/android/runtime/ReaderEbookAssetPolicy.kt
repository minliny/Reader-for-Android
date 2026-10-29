package io.reader.android.runtime

import io.reader.android.runtime.ebookdraft.EbookRenderLease
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Extraction of the provider's existing Host bounds, not a second budget. */
internal class ReaderEbookAssetBudget {
    private var attempts = 0
    private var servedBytes = 0L

    @Synchronized fun attempted() {
        check(attempts < 512) { "RESOURCE_SESSION_ATTEMPT_LIMIT" }
        attempts++ // Queue timeout / failed Native reads also consume one attempt.
    }

    @Synchronized fun nativeMaximum(typeMaximum: Long): Long {
        require(typeMaximum in 1..32L * 1024 * 1024)
        val remaining = 64L * 1024 * 1024 - servedBytes
        check(remaining > 0) { "RESOURCE_SESSION_BYTE_LIMIT" }
        return minOf(typeMaximum, remaining)
    }

    @Synchronized fun returned(actualBytes: Long, nativeMaximum: Long) {
        check(actualBytes in 0..nativeMaximum && actualBytes <= 64L * 1024 * 1024 - servedBytes) {
            "RESOURCE_SESSION_BYTE_LIMIT"
        }
        servedBytes += actualBytes
    }
}

/** One active renderer for the App's one reading surface. No Native call or UI callback here. */
internal class ReaderEbookAssetRegistry {
    private val active = AtomicReference<ReaderEbookNativeReadPermit?>()

    fun issue(lease: EbookRenderLease, ownerLease: ReaderOperationOwner.ClientLease,
        currentClient: AtomicReference<ReaderOperationOwner.ClientLease?>,
        clientRetired: AtomicBoolean, assertCurrentAdmission: () -> Unit): ReaderEbookNativeReadPermit {
        assertCurrentAdmission()
        check(lease.ownerId == ownerLease.ownerId && lease.clientId == ownerLease.clientId &&
            lease.ownerGeneration == ownerLease.ownerGeneration && currentClient.get() == ownerLease) { "FOREIGN_EBOOK_CLIENT" }
        check(!clientRetired.get()) { "RETIRED_EBOOK_CLIENT" }
        val permit = ReaderEbookNativeReadPermit(lease, ownerLease, currentClient, active, clientRetired, assertCurrentAdmission)
        active.getAndSet(permit)?.retire()
        return permit
    }

    fun retire() { active.getAndSet(null)?.retire() }
}

/** App-issued full identity. Short real admission observation plus atomic checks run inside the Native actor action. */
internal class ReaderEbookNativeReadPermit internal constructor(
    val lease: EbookRenderLease,
    private val ownerLease: ReaderOperationOwner.ClientLease,
    private val currentClient: AtomicReference<ReaderOperationOwner.ClientLease?>,
    private val active: AtomicReference<ReaderEbookNativeReadPermit?>,
    private val clientRetired: AtomicBoolean,
    private val assertCurrentAdmission: () -> Unit,
) {
    private val retired = AtomicBoolean(false)
    fun retire() { retired.set(true) }
    fun requireCurrent(expected: EbookRenderLease) {
        assertCurrentAdmission() // Real operations phase/client, not its publication mirror.
        check(expected == lease && currentClient.get() == ownerLease && active.get() === this &&
            !retired.get() && !clientRetired.get()) {
            "RETIRED_EBOOK_NATIVE_READ"
        }
        assertCurrentAdmission() // Native admission linearizes here; never hold this lock across Native.
    }
}
