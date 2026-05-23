package com.reader.android.ui.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebDavRssFixtureTest {

    @Test
    fun `discover fixture is short and state friendly`() {
        val state = DiscoverRssWebDavMapper.discover()

        assertTrue(state.items.size in 1..3)
        assertTrue(state.items.all { it.id.isNotBlank() && it.title.isNotBlank() })
    }

    @Test
    fun `rss subscription disabled state is preserved`() {
        val subscriptions = DiscoverRssWebDavMapper.subscriptions()

        assertTrue(subscriptions.feeds.any { !it.enabled })
    }

    @Test
    fun `webdav states cover not configured configured running and failure`() {
        val notConfigured = DiscoverRssWebDavMapper.webDavNotConfigured()
        val configured = DiscoverRssWebDavMapper.webDavConfigured()
        val running = DiscoverRssWebDavMapper.webDavSyncRunning()
        val failure = DiscoverRssWebDavMapper.webDavAuthError()

        assertFalse(notConfigured.account.isConfigured)
        assertTrue(configured.account.isConfigured)
        assertTrue(running.syncState == WebDavSyncState.Running)
        assertTrue(failure.syncState == WebDavSyncState.Failure)
    }

    @Test
    fun `fixtures do not store real secrets`() {
        val fixtureText = listOf(
            DiscoverRssWebDavFixture.discoverItems.joinToString(),
            DiscoverRssWebDavFixture.rssFeeds.joinToString(),
            DiscoverRssWebDavFixture.rssArticles.joinToString(),
            DiscoverRssWebDavFixture.remoteBooks.joinToString(),
            DiscoverRssWebDavMapper.webDavConfigured().toString()
        ).joinToString("\n")

        listOf("Authorization", "Bearer ", "token=", "password=", "secret=").forEach { token ->
            assertTrue("Fixture must not contain $token", token !in fixtureText)
        }
    }
}
