package com.reader.android.data

import com.reader.android.AppProvider
import com.reader.android.data.repository.FakeBookSourceRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AppProvider wiring tests. DAO/DB covered by Room unit tests above.
 * Tests provider lifecycle, network gate, and repository wiring.
 */
class RuntimeWiringIntegrationTest {

    @After
    fun tearDown() {
        AppProvider.close()
    }

    // ── P1-1: Provider initialization ──

    @Test
    fun `provider initializes with fake implementations`() {
        AppProvider.initForTesting()
        // Getting the bookSourceRepo should return a valid repository
        assertNotNull(AppProvider.bookSourceRepository)
    }

    @Test
    fun `provider book source repo is always available`() {
        AppProvider.initForTesting()
        val repo = AppProvider.bookSourceRepository
        assertNotNull(repo)
    }

    // ── P1-2: Fake repository works ──

    @Test
    fun `fake book source repository supports CRUD`() {
        val repo = FakeBookSourceRepository()
        assertEquals(0, repo.getAll().size)

        val source = com.reader.android.data.model.BookSource(
            sourceUrl = "http://example.com", sourceName = "测试源"
        )
        repo.add(source)
        assertEquals(1, repo.getAll().size)
        assertTrue(repo.getEnabled().isNotEmpty())

        repo.setEnabled("http://example.com", false)
        assertEquals(0, repo.getEnabled().size)
    }

    @Test
    fun `fake book source repository import json`() {
        val repo = FakeBookSourceRepository()
        val json = """[{"sourceUrl":"http://a.com","sourceName":"源A","enabled":true},{"sourceUrl":"http://b.com","sourceName":"源B","enabled":false}]"""
        val count = repo.importJson(json)
        assertEquals(2, count)
        assertEquals(2, repo.getAll().size)
        assertEquals(1, repo.getEnabled().size)
    }

    // ── P1-3: Network gate ──

    @Test
    fun `network gate default denies real network`() {
        AppProvider.initForTesting()
        assertFalse(AppProvider.isNetworkAllowed)
    }

    @Test
    fun `network gate rejects in default state`() {
        AppProvider.initForTesting()
        // Real network path must be gated
        assertFalse(AppProvider.isNetworkAllowed)
        // After explicit enable
        AppProvider.enableNetworkForTestingOnly()
        assertTrue(AppProvider.isNetworkAllowed)
    }

    @Test
    fun `provider close resets state`() {
        AppProvider.initForTesting()
        AppProvider.enableNetworkForTestingOnly()
        AppProvider.close()
        assertFalse(AppProvider.isNetworkAllowed)
    }

    @Test
    fun `provider close does not crash on re-close`() {
        AppProvider.initForTesting()
        AppProvider.close()
        AppProvider.close() // should not throw
    }
}
