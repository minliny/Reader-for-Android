package io.reader.android.runtime

/** Opaque Host presentation lease. Core book.search has numeric pages, not a cursor or close RPC. */
class ReaderOnlineSourceSelection internal constructor(
    val sourceId: String, val sourceVersion: String, val name: String,
    internal val lease: ReaderOperationOwner.ClientLease, val runtimeSession: String,
)
class ReaderOnlineSearchSession internal constructor(
    val id: String, val generation: Long, val keyword: String, val source: ReaderOnlineSourceSelection,
)
/** The original Core candidate JSON is retained unchanged for book.detail; no Host rule processing. */
class ReaderOnlineBook internal constructor(
    val key: ShelfBookKey, val title: String, val author: String?, val coverUrl: String?, val intro: String?,
    val originalCoreBookJson: String,
)
enum class ReaderOnlineContentKind { TEXT, MANGA, AUDIO, UNKNOWN }
data class ReaderOnlineSearchPage(
    val session: ReaderOnlineSearchSession, val page: Int, val books: List<ReaderOnlineBook>,
    val contentKind: ReaderOnlineContentKind,
    /** Null means Core gives no total/hasMore. An empty page ends this Host session's paging. */
    val hasMore: Boolean?,
    val originalResultJson: String,
)
/** Metadata preview only: no reading token, position, local format, Original permission or shelf admission. */
data class ReaderOnlineDetailPreview(
    val key: ShelfBookKey, val sourceVersion: String, val contentKind: ReaderOnlineContentKind,
    val title: String, val author: String, val coverUrl: String?, val intro: String?, val kind: String?,
    val lastChapter: String?, val tocUrl: String?, val variables: Map<String, String>,
    val originalCoreBookJson: String, val originalResultJson: String,
)
enum class ReaderOnlineOriginalState { NO_TERMINAL, TERMINAL_PROTOCOL_UNCONFIRMED, ACKNOWLEDGED_RESULT, ACKNOWLEDGED_ERROR }
data class ReaderOnlineOriginalObservation(
    val id: String, val method: String, val sourceId: String, val runtimeSession: String,
    val requestId: Long?, val originalParametersJson: String, val parsedTerminalJson: String?,
    val state: ReaderOnlineOriginalState,
)
class ReaderOnlineOriginalPending(val original: ReaderOnlineOriginalObservation, cause: Throwable? = null) :
    Exception("ONLINE_ORIGINAL_PENDING:${original.id}", cause)
