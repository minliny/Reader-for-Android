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

}
