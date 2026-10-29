package io.reader.android.runtime

import io.reader.android.runtime.ebookdraft.AuthorizedEbookOriginal
import io.reader.android.runtime.ebookdraft.EbookBinaryAssets
import io.reader.android.runtime.ebookdraft.EbookLeaseGuard
import io.reader.android.runtime.ebookdraft.EbookRenderLease
import io.reader.android.runtime.ebookdraft.requireCanonicalEbookEntry
import kotlinx.coroutines.runBlocking

/** Optional typed capability of the SAME actor-owned port. No second runtime. */
internal interface ReaderEbookBinaryRuntimePort : ReaderCoreEventActor.RuntimePort {
    fun readEpubEntry(authorizedArchivePath: String, canonicalEntry: String, maximumBytes: Long): ByteArray
}

/** No path/identity comes from a WebView message. Created by the admitted App/client factory only. */
internal class ReaderEbookNativeAssetRead(
    val lease: EbookRenderLease,
    val permit: ReaderEbookNativeReadPermit,
    val authorizedArchivePath: String,
    val canonicalEntry: String,
    val maximumBytes: Long,
) {
    fun requireNativeAdmission(runtimeSession: String) {
        permit.requireCurrent(lease)
        check(lease.runtimeSession == runtimeSession) { "FOREIGN_EBOOK_ASSET_RUNTIME" }
        require(maximumBytes in 1..32L * 1024 * 1024)
        require(authorizedArchivePath.startsWith('/') && '\u0000' !in authorizedArchivePath)
        requireCanonicalEbookEntry(canonicalEntry)
    }
}

/** Existing ReaderOperationOwner whole turn; implementations must reject nested owner-lane entry. */
internal interface ReaderEbookAssetTurn {
    suspend fun read(original: ReaderEbookNativeAssetRead): ByteArray
}

internal data class ReaderEbookOwnedPublication(
    val original: AuthorizedEbookOriginal,
    val binary: ReaderEbookOwnedAssets,
)

/** App-owned injected provider port. WebView callback threads never call JNI directly. */
internal class ReaderEbookOwnedAssets internal constructor(
    private val lease: EbookRenderLease,
    private val original: AuthorizedEbookOriginal,
    private val permit: ReaderEbookNativeReadPermit,
    private val guard: EbookLeaseGuard,
    private val turn: ReaderEbookAssetTurn,
) : EbookBinaryAssets {
    init { check(original.bookId == lease.bookId && permit.lease == lease) }

    override fun read(authorizedArchivePath: String, canonicalEntry: String, maximumBytes: Long): ByteArray {
        // readCanonicalEntry has already taken its serial resource permit and
        // calculated min(type bound, remaining session bytes), before this call.
        requireCurrent()
        check(authorizedArchivePath == original.authorizedArchivePath) { "FOREIGN_PRIVATE_ORIGINAL_PATH" }
        val request = ReaderEbookNativeAssetRead(lease, permit, authorizedArchivePath, canonicalEntry, maximumBytes)
        request.requireNativeAdmission(lease.runtimeSession)
        val bytes = runBlocking { turn.read(request) }
        // A retired render or changed private original discards the returned
        // bytes only after the real synchronous JNI action has left its owner.
        requireCurrent()
        original.requireUnchanged()
        check(bytes.size.toLong() <= maximumBytes) { "NATIVE_ASSET_BOUND_EXCEEDED" }
        return bytes
    }

    private fun requireCurrent() {
        permit.requireCurrent(lease)
        guard.requireCurrent(lease)
    }

    override fun retire() { permit.retire() }
}
