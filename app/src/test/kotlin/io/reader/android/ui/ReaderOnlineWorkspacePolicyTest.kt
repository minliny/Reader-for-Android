package io.reader.android.ui

import io.reader.android.runtime.ReaderOnlineOriginalObservation
import io.reader.android.runtime.ReaderOnlineOriginalState
import io.reader.android.runtime.ReaderOnlineSearchSession
import io.reader.android.runtime.ReaderOnlineSourceSelection
import io.reader.android.runtime.ReaderOperationOwner
import io.reader.android.runtime.ReaderSourceEntry
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderOnlineWorkspacePolicyTest {
    private val source = ReaderSourceEntry("source-a", "Source", "", true, false, "version-a", null)
    private val lease = ReaderOperationOwner.ClientLease(UUID.randomUUID(), UUID.randomUUID(), 1, 1)
    private val selection = ReaderOnlineSourceSelection(source.sourceId, source.sourceVersion, source.name, lease, "runtime-a")
    private val query = "作者 😀"
    private val session = ReaderOnlineSearchSession("original-session", 1, query, selection)
    private val state = OnlineWorkspaceState(revision = "results", page = OnlineWorkspacePage.RESULTS,
        selected = source, selection = selection, session = session, query = query)

    @Test fun sameQueryRetryRequiresCurrentSelectionRevisionAndNoUnresolvedOriginal() {
        val failed = state.copy(sessionFailed = true)
        val retry = checkNotNull(OnlineWorkspaceUiPolicy.searchRetry(failed, "results"))
        assertSame(selection, retry.first)
        assertEquals(query, retry.second)
        assertNull(OnlineWorkspaceUiPolicy.searchRetry(failed, "stale"))
        assertNull(OnlineWorkspaceUiPolicy.searchRetry(failed.copy(selected = source.copy(sourceVersion = "version-b")), "results"))
        val pending = ReaderOnlineOriginalObservation("old", "book.search", source.sourceId, "runtime-a", 14, "{}", null,
            ReaderOnlineOriginalState.NO_TERMINAL)
        assertNull(OnlineWorkspaceUiPolicy.searchRetry(failed.copy(pendingReads = listOf(pending)), "results"))
    }

    @Test fun stoppedSearchRejectsLatePagesAndKeepsTheOriginalBusyUntilSettled() {
        val running = state.copy(busy = true, searchInFlight = true)
        assertTrue(OnlineWorkspaceUiPolicy.publishesSearchPage(running, session))
        assertNull(OnlineWorkspaceUiPolicy.stopSearch(running, "stale"))
        assertNull(OnlineWorkspaceUiPolicy.stopSearch(running.copy(searchInFlight = false), "results"))
        val stopped = checkNotNull(OnlineWorkspaceUiPolicy.stopSearch(running, "results"))
        assertSame(session, stopped.session)
        assertTrue(stopped.busy)
        assertFalse(OnlineWorkspaceUiPolicy.publishesSearchPage(stopped, session))
        assertNull(OnlineWorkspaceUiPolicy.searchRetry(stopped, "results"))
        val settled = stopped.copy(busy = false, searchInFlight = false, session = null)
        assertEquals(query, OnlineWorkspaceUiPolicy.searchRetry(settled, "results")?.second)
        assertFalse(OnlineWorkspaceUiPolicy.publishesSearchPage(settled, session))
    }
}
