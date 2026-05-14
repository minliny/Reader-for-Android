package com.reader.android.data.network

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionRepositoryTest {

    private val repo = FakeSubscriptionRepository()

    @Test
    fun `add and get subscription`() = runBlocking {
        repo.add(RssSubscription("http://feed.com/rss", "Test Feed"))
        val sub = repo.get("http://feed.com/rss")
        assertEquals("Test Feed", sub?.title)
    }

    @Test
    fun `remove subscription`() = runBlocking {
        repo.add(RssSubscription("http://x.com"))
        repo.remove("http://x.com")
        assertNull(repo.get("http://x.com"))
    }

    @Test
    fun `getAll returns all subscriptions`() = runBlocking {
        repo.add(RssSubscription("http://a.com"))
        repo.add(RssSubscription("http://b.com"))
        assertEquals(2, repo.getAll().size)
    }

    @Test
    fun `needsUpdate returns true for new subscription`() = runBlocking {
        repo.add(RssSubscription("http://new.com"))
        assertTrue(repo.needsUpdate("http://new.com"))
    }

    @Test
    fun `needsUpdate returns false after markUpdated`() = runBlocking {
        repo.add(RssSubscription("http://feed.com"))
        repo.markUpdated("http://feed.com", "guid-1")
        assertFalse(repo.needsUpdate("http://feed.com"))
    }
}
