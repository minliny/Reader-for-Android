package io.reader.android.runtime

import io.reader.android.runtime.ebookdraft.EbookLeaseGuard
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/** Single-use opaque Host capability. Closing never enters an owner turn or cancels an admitted call. */
internal class ReaderEbookViewLease internal constructor(private val libraryIdentity: Any,
    internal val ownerLease: ReaderOperationOwner.ClientLease,
    private val retire: (ReaderEbookViewLease) -> Unit) : AutoCloseable {
    val physicalViewId: UUID = UUID.randomUUID()
    private val retired = AtomicBoolean(false)
    private val claimed = AtomicBoolean(false)
    internal fun requireCurrent(identity: Any) {
        check(identity === libraryIdentity && !retired.get()) { "RETIRED_OR_FOREIGN_EBOOK_VIEW" }
    }
    internal fun claim(identity: Any) {
        requireCurrent(identity)
        check(claimed.compareAndSet(false, true)) { "EBOOK_VIEW_ALREADY_PREPARED" }
        requireCurrent(identity)
    }
    override fun close() { if (retired.compareAndSet(false, true)) retire(this) }
}

internal class ReaderEbookViewPreparation internal constructor(val view: ReaderEbookViewLease,
    val publication: ReaderEbookPreparedPublication, val guard: EbookLeaseGuard) : AutoCloseable {
    fun requireCurrent() = guard.requireCurrent(publication.lease)
    override fun close() = view.close()
}
