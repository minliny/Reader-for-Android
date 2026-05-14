package com.reader.android.data.adapter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalBookLifecycleTest {

    @Test
    fun `REPLACE always reimports`() {
        assertTrue(LocalBookLifecycle.shouldReimport(existing = true, ReimportStrategy.REPLACE))
        assertTrue(LocalBookLifecycle.shouldReimport(existing = false, ReimportStrategy.REPLACE))
    }

    @Test
    fun `SKIP_IF_EXISTS skips when existing`() {
        assertFalse(LocalBookLifecycle.shouldReimport(true, ReimportStrategy.SKIP_IF_EXISTS))
        assertTrue(LocalBookLifecycle.shouldReimport(false, ReimportStrategy.SKIP_IF_EXISTS))
    }

    @Test
    fun `MERGE_KEEP_PROGRESS preserves progress`() {
        assertTrue(LocalBookLifecycle.shouldKeepProgress(ReimportStrategy.MERGE_KEEP_PROGRESS))
        assertFalse(LocalBookLifecycle.shouldKeepProgress(ReimportStrategy.REPLACE))
    }

    @Test
    fun `getUrlsToCleanup generates chapter URIs`() {
        val urls = LocalBookLifecycle.getUrlsToCleanup("content://book.txt", 3)
        assertEquals(3, urls.size)
        assertTrue(urls[0].contains("#ch0"))
        assertTrue(urls[2].contains("#ch2"))
    }

    @Test
    fun `getProgressId uses source URI`() {
        assertEquals("content://book.txt", LocalBookLifecycle.getProgressId("content://book.txt"))
    }
}
