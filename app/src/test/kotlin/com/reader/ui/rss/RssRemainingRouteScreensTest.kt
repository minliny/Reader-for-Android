package com.reader.ui.rss

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RssRemainingRouteScreensTest {
    @Test
    fun remainingRouteIdsCoverRequestedScope() {
        assertEquals(
            setOf(
                "rss-source-feed",
                "rss-source-category-releases",
                "rss-source-category-issues",
                "rss-source-category-discussions",
                "rss-favorite-groups",
                "rss-favorite-group-edit",
                "rss-favorite-clear",
                "rss-empty",
                "rss-error"
            ),
            rssRemainingDemoRouteIds()
        )
    }

    @Test
    fun sourceCategoryRoutesResolveCanonicalTitles() {
        assertEquals("GitHub Releases", rssSourceFeedRouteState("rss-source-feed").title)
        assertEquals("Releases", rssSourceFeedRouteState("rss-source-category-releases").title)
        assertEquals("Issues", rssSourceFeedRouteState("rss-source-category-issues").title)
        assertEquals("Discussions", rssSourceFeedRouteState("rss-source-category-discussions").title)
    }

    @Test
    fun emptyAndErrorStatesKeepDemoActions() {
        val empty = rssStateRouteState("rss-empty")
        val error = rssStateRouteState("rss-error")

        assertNotNull(empty)
        assertEquals("rss-all", empty!!.primaryRouteId)
        assertEquals("rss-subscription-management", empty.secondaryRouteId)

        assertNotNull(error)
        assertEquals("rss-refreshing", error!!.primaryRouteId)
        assertEquals("rss-subscription-management", error.secondaryRouteId)
        assertEquals(2, error.errors.size)
    }

    @Test
    fun unsupportedRouteReturnsNull() {
        assertNull(rssRemainingDemoRouteState("rss-source-edit"))
        assertNull(rssStateRouteState("rss-source-feed"))
    }
}
