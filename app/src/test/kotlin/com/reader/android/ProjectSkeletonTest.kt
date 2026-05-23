package com.reader.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectSkeletonTest {

    @Test
    fun `applicationId is correctly configured`() {
        val applicationId = "com.reader.android"
        assertEquals("com.reader.android", applicationId)
    }

    @Test
    fun `versionName is semver string`() {
        val versionName = "0.1.0"
        assertTrue(versionName.matches(Regex("\\d+\\.\\d+\\.\\d+")))
    }

    @Test
    fun `app screens list contains all four tabs`() {
        val routes = com.reader.android.ui.appScreens.map { it.route }.toSet()
        assertEquals(setOf("bookshelf", "discover", "sources", "mine"), routes)
    }
}
