package com.reader.android.ui.discover

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RssLocalAdapterTest {

    @Test
    fun `add and list subscriptions`() {
        val adapter = RssLocalAdapter()
        adapter.addSubscription(RssSubscriptionLocal("s1", "RSS源", "https://rss.example.com"))
        assertEquals(1, adapter.subscriptionCount())
        assertEquals(1, adapter.listSubscriptions().size)
    }

    @Test
    fun `blank url ignored`() {
        val adapter = RssLocalAdapter()
        assertFalse(adapter.addSubscription(RssSubscriptionLocal("s1", "RSS", "")))
        assertEquals(0, adapter.subscriptionCount())
    }

    @Test
    fun `remove subscription`() {
        val adapter = RssLocalAdapter()
        adapter.addSubscription(RssSubscriptionLocal("s1", "RSS", "url"))
        adapter.removeSubscription("s1")
        assertEquals(0, adapter.subscriptionCount())
    }

    @Test
    fun `set and list articles`() {
        val adapter = RssLocalAdapter()
        adapter.setArticles(RssLocalAdapter.fixtureArticles())
        assertEquals(3, adapter.articleCount())
        assertEquals("科幻世界最新短篇：星门之后", adapter.listArticles()[0].title)
    }

    @Test
    fun `adapter shell has fixture data`() {
        DiscoverAdapterShell.resetToFakeMode()
        assertEquals(2, DiscoverAdapterShell.subscriptionCount())
        assertEquals(3, DiscoverAdapterShell.articles().size)
    }

    @Test
    fun `no network references`() {
        val source = java.io.File(
            "src/main/kotlin/com/reader/android/ui/discover/RssLocalAdapter.kt"
        ).readText()
        assertFalse("Must not import HttpClient", "HttpClient" in source)
    }
}
