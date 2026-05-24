package com.reader.android.ui.state

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderGlobalStateMatrixTest {

    @Test
    fun `all twelve global states are expressible`() {
        assertEquals(12, ReaderStateMapper.globalStateKeys.size)
        assertEquals(12, ReaderStateFixtures.globalStates.size)
        assertEquals(
            ReaderGlobalStateKey.entries.toSet(),
            ReaderStateFixtures.pageStates.map { it.key }.toSet()
        )
    }

    @Test
    fun `global states map to state page models`() {
        ReaderStateFixtures.globalStates.forEach { state ->
            val pageState = ReaderStateMapper.toPageState(state)
            assertTrue(pageState.title.isNotBlank())
            assertTrue(pageState.message.isNotBlank())
        }
    }

    @Test
    fun `webdav auth error and sync conflict are display states only`() {
        val authError = ReaderStateMapper.toPageState(ReaderUiState.WebDavAuthError)
        val conflict = ReaderStateMapper.toPageState(
            ReaderUiState.SyncConflict(localVersion = "local", remoteVersion = "remote")
        )

        assertEquals(ReaderGlobalStateKey.WebDavAuthError, authError.key)
        assertEquals("重新配置", authError.actionLabel)
        assertEquals(ReaderGlobalStateKey.SyncConflict, conflict.key)
        assertEquals("处理冲突", conflict.actionLabel)
        assertFalse(authError.message.contains("http://"))
        assertFalse(authError.message.contains("https://"))
    }
}
