package io.reader.android.runtime

import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean

/** Revocation only: constructing this object grants no source, client, chapter or network authority. */
class ReaderOnlineWindowSchedule {
    private val stopped = AtomicBoolean(false)
    internal val isOpen: Boolean get() = !stopped.get()
    fun stop() { stopped.set(true) }
    internal fun requireOpen() { check(isOpen) { "ONLINE_WINDOW_SCHEDULING_RETIRED" } }
}

/** Only the same App reader service can issue a window/neighbor identity. Retiring grants no permission. */
class ReaderOnlineReadingWindow internal constructor(
    val id: String, val key: ShelfBookKey, val sessionToken: String, val chapterIndex: Int,
    val bodyVersion: String, val processingVersion: String, val sourceVersion: String,
    val catalogRevision: String, val preparationRevision: Long?,
    neighbors: List<ReaderOnlineNeighbor>, internal val owner: Any,
    internal val lease: ReaderOperationOwner.ClientLease, internal val runtimeSession: String,
    internal val opened: ReaderOnlineChapter, private val schedule: ReaderOnlineWindowSchedule,
) {
    val neighbors: List<ReaderOnlineNeighbor> = Collections.unmodifiableList(ArrayList(neighbors))
    val isRetired: Boolean get() = !schedule.isOpen
    /** Stop future slot admission only. An already accepted original is never cancelled/ACKed here. */
    fun retire() { schedule.stop() }
}
class ReaderOnlineNeighbor internal constructor(
    val windowId: String, val offset: Int, val chapterIndex: Int, val title: String,
    internal val owner: Any,
)
/** Core-returned cache/preparation facts. Cache presence alone is never a readable entry or saved position. */
data class ReaderOnlineNeighborState(
    val neighbor: ReaderOnlineNeighbor, val kind: String, val reason: String,
    val cacheState: String, val cachedBytes: Long, val bodyVersion: String?, val processingVersion: String?,
) { val ready: Boolean get() = kind == "ready" && bodyVersion != null && processingVersion != null }
class ReaderOnlineReadingWindowSnapshot(val window: ReaderOnlineReadingWindow, states: List<ReaderOnlineNeighborState>) {
    val states: List<ReaderOnlineNeighborState> = Collections.unmodifiableList(ArrayList(states))
}
