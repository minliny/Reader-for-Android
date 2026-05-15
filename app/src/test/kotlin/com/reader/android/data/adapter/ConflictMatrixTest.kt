package com.reader.android.data.adapter

import org.junit.Assert.assertEquals
import org.junit.Test

class ConflictMatrixTest {

    @Test
    fun `all 6 scenarios resolve without error`() {
        val local = ProgressSyncRecord("url", "Book", "ch5", "Ch5", 4, 0.5f, 1000, "local")
        val remote = ProgressSyncRecord("url", "Book", "ch10", "Ch10", 9, 0.2f, 2000, "remote")

        ConflictScenario.entries.forEach { scenario ->
            val result = ConflictMatrix.resolve(scenario, local, remote)
            // Each scenario should produce a result (or null for MANUAL)
            if (scenario == ConflictScenario.CONCURRENT_EDIT) {
                assertEquals(null, result) // MANUAL returns null
            } else {
                assert(result != null) { "${scenario.name} returned null unexpectedly" }
            }
        }
    }

    @Test
    fun `LOCAL_ONLY returns local even with null remote`() {
        val local = ProgressSyncRecord("url", "Book", "ch3", "Ch3", 2, 0f, 0, "local")
        val result = ConflictMatrix.resolve(ConflictScenario.LOCAL_ONLY, local, null)
        assertEquals(local, result)
    }
}
