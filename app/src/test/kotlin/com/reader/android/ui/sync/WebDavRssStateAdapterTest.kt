package com.reader.android.ui.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebDavRssStateAdapterTest {

    @Test
    fun `webdav fixture maps to config state`() {
        val state = DiscoverRssWebDavMapper.webDavConfigured()

        assertTrue(state.account.isConfigured)
        assertEquals(WebDavAuthState.Configured, state.authState)
        assertEquals(WebDavSyncState.Success, state.syncState)
        assertTrue(state.canConnect)
    }

    @Test
    fun `webdav auth error is expressible`() {
        val state = DiscoverRssWebDavMapper.webDavAuthError()

        assertEquals(WebDavAuthState.AuthError, state.authState)
        assertEquals(WebDavSyncState.Failure, state.syncState)
        assertEquals("WebDAV 授权失败", state.error!!.title)
        assertFalse(state.canConnect)
    }

    @Test
    fun `backup settings express enabled and disabled`() {
        val enabled = DiscoverRssWebDavMapper.backup(enabled = true)
        val disabled = DiscoverRssWebDavMapper.backup(enabled = false)

        assertTrue(enabled.autoBackupEnabled)
        assertTrue(enabled.webDavBackupEnabled)
        assertFalse(disabled.autoBackupEnabled)
        assertFalse(disabled.webDavBackupEnabled)
    }

    @Test
    fun `progress sync expresses running success failure and conflict`() {
        val running = DiscoverRssWebDavMapper.progressSync(ProgressSyncState.Running)
        val success = DiscoverRssWebDavMapper.progressSync(ProgressSyncState.Success)
        val failure = DiscoverRssWebDavMapper.progressSync(ProgressSyncState.Failure)
        val conflict = DiscoverRssWebDavMapper.progressSync(ProgressSyncState.Conflict)

        assertEquals(ProgressSyncState.Running, running.syncState)
        assertEquals(ProgressSyncState.Success, success.syncState)
        assertEquals("同步失败", failure.error!!.title)
        assertEquals(1, conflict.conflictCount)
    }

    @Test
    fun `rss fixture maps to rss list state`() {
        val state = DiscoverRssWebDavMapper.rssList()

        assertTrue(state.feeds.isNotEmpty())
        assertTrue(state.articles.isNotEmpty())
        assertEquals("rss-feed-1", state.selectedFeedId)
    }

    @Test
    fun `rss empty loading error and offline are expressible`() {
        val empty = DiscoverRssWebDavMapper.rssEmpty()
        val loading = DiscoverRssWebDavMapper.rssLoading()
        val error = DiscoverRssWebDavMapper.rssError("fixture error")
        val offline = DiscoverRssWebDavMapper.rssOffline()

        assertTrue(empty.isEmpty)
        assertTrue(loading.isLoading)
        assertEquals("fixture error", error.error!!.message)
        assertTrue(offline.offline)
    }

    @Test
    fun `remote webdav books are expressible`() {
        val state = DiscoverRssWebDavMapper.remoteBooks()

        assertTrue(state.books.isNotEmpty())
        assertFalse(state.isEmpty)
        assertEquals(WebDavSyncState.Success, state.books.first().syncState)
    }
}
