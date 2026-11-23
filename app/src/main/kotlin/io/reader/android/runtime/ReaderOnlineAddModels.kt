package io.reader.android.runtime

/** Issued by the same App catalog from its actual current confirmed detail preview. */
internal class ReaderOnlineAddAuthority(
    val preview: ReaderOnlineDetailPreview,
    val lease: ReaderOperationOwner.ClientLease,
    val runtimeSession: String,
)
enum class ReaderOnlineAddConfirmation { ADDED, ALREADY_PRESENT_BEFORE_NEW_INTENT, REJECTED, UNKNOWN }
data class ReaderOnlineAddResult(
    val operationId: String, val key: ShelfBookKey, val confirmation: ReaderOnlineAddConfirmation,
    val durable: Boolean, val originalAddTerminalJson: String? = null, val rejectionJson: String? = null,
    val created: Boolean? = null, val addedAt: Long? = null,
)
data class ReaderOnlineAddPending(val operationId: String, val key: ShelfBookKey, val candidateRetained: Boolean)
/** These projections are explicitly NOT an original-command receipt/query API. */
data class ReaderOnlineAddObservation(
    val pending: ReaderOnlineAddPending, val preparationStatusJson: String, val shelfProjectionJson: String,
    val originalConfirmed: Boolean = false,
)
class ReaderOnlineAddUnconfirmed(val operationId: String, val phase: String, cause: Throwable? = null) :
    Exception("ONLINE_ADD_UNCONFIRMED:$operationId/$phase", cause)
internal data class ReaderOnlineAddJournalSnapshot(val recordJson: String, val candidateRetained: Boolean, val canonicalCommitted: Boolean = true)
