package com.reader.ui.reading

import com.reader.ui.shell.AsyncResultStateValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Phase 6 / audit task 5 — asyncResult UI visual feedback mapping proof.
 *
 * [asyncResultOverlayLabel] is the pure mapping from [AsyncResultStateValue]
 * to a user-visible overlay label. It is called by [ReaderControlReadingSurface]
 * to decide whether to render a stale-result indicator.
 *
 * This test validates the mapping so the async-result guard's DISCARDED /
 * SUPERSEDED / CANCELLED states are guaranteed to surface a visible label,
 * while IDLE / PENDING / COMPLETED stay silent (the VM's own ReadingUiState
 * already drives loading / ready visuals for those).
 */
class AsyncResultOverlayLabelJvmTest {

    @Test
    fun `IDLE produces no overlay`() {
        assertNull(asyncResultOverlayLabel(AsyncResultStateValue.IDLE))
    }

    @Test
    fun `PENDING produces no overlay`() {
        // PENDING is the in-flight state — the VM's ReadingUiState.Loading
        // (CircularProgressIndicator) already covers the visual.
        assertNull(asyncResultOverlayLabel(AsyncResultStateValue.PENDING))
    }

    @Test
    fun `COMPLETED produces no overlay`() {
        // COMPLETED means the result landed and the VM's ReadingUiState.Ready
        // renders the content — no overlay needed.
        assertNull(asyncResultOverlayLabel(AsyncResultStateValue.COMPLETED))
    }

    @Test
    fun `DISCARDED surfaces switching label`() {
        // Stale result discarded by the guard — user should see that a newer
        // entry is loading, rather than silently showing stale text.
        val label = asyncResultOverlayLabel(AsyncResultStateValue.DISCARDED)
        assertEquals("正在切换到最新…", label)
    }

    @Test
    fun `SUPERSEDED surfaces switching label`() {
        val label = asyncResultOverlayLabel(AsyncResultStateValue.SUPERSEDED)
        assertEquals("正在切换到最新…", label)
    }

    @Test
    fun `CANCELLED surfaces cancelled label`() {
        val label = asyncResultOverlayLabel(AsyncResultStateValue.CANCELLED)
        assertEquals("加载已取消", label)
    }

    /**
     * Full guard cycle: IDLE → PENDING → DISCARDED → PENDING → COMPLETED.
     * The overlay should only appear on DISCARDED; the other states stay
     * silent because the VM's own state machine covers them.
     */
    @Test
    fun `overlay only appears on discarded in stale-guard cycle`() {
        // Entry A starts
        assertNull(asyncResultOverlayLabel(AsyncResultStateValue.PENDING))
        // Entry B supersedes A; A's result arrives late → DISCARDED
        assertEquals("正在切换到最新…", asyncResultOverlayLabel(AsyncResultStateValue.DISCARDED))
        // Entry B starts
        assertNull(asyncResultOverlayLabel(AsyncResultStateValue.PENDING))
        // Entry B completes
        assertNull(asyncResultOverlayLabel(AsyncResultStateValue.COMPLETED))
    }
}
