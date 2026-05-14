package com.reader.android.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BookSourceRepositoryTest {

    private lateinit var repo: FakeBookSourceRepository

    @Before
    fun setUp() {
        repo = FakeBookSourceRepository()
    }

    @Test
    fun `import JSON populates sources and returns count`() {
        val json = """
        [
          {"sourceUrl":"http://a.com","sourceName":"源A","sourceGroup":"中文"},
          {"sourceUrl":"http://b.com","sourceName":"源B","sourceGroup":"备用","enabled":false}
        ]
        """.trimIndent()

        val count = repo.importJson(json)
        assertEquals(2, count)
        assertEquals(2, repo.getAll().size)
    }

    @Test
    fun `getEnabled returns only enabled sources`() {
        repo.importJson("""
        [{"sourceUrl":"http://a.com","sourceName":"源A","enabled":true},
         {"sourceUrl":"http://b.com","sourceName":"源B","enabled":false}]
        """.trimIndent())

        val enabled = repo.getEnabled()
        assertEquals(1, enabled.size)
        assertEquals("源A", enabled[0].sourceName)
        assertTrue(enabled[0].enabled)
    }

    @Test
    fun `toggle enabled flips state`() {
        repo.importJson("""[{"sourceUrl":"http://a.com","sourceName":"A","enabled":true}]""")
        repo.setEnabled("http://a.com", false)
        assertFalse(repo.getByUrl("http://a.com")!!.enabled)
        repo.setEnabled("http://a.com", true)
        assertTrue(repo.getByUrl("http://a.com")!!.enabled)
    }

    @Test
    fun `delete removes source`() {
        repo.importJson("""[{"sourceUrl":"http://a.com","sourceName":"A"}]""")
        assertEquals(1, repo.getAll().size)
        repo.remove("http://a.com")
        assertEquals(0, repo.getAll().size)
        assertNull(repo.getByUrl("http://a.com"))
    }

    @Test
    fun `getByUrl returns correct source`() {
        repo.importJson("""[{"sourceUrl":"http://a.com","sourceName":"源A"}]""")
        val src = repo.getByUrl("http://a.com")
        assertNotNull(src)
        assertEquals("源A", src!!.sourceName)
    }

    @Test
    fun `import preserves Legado-compatible fields`() {
        val json = """
        [{"sourceUrl":"http://test.com","sourceName":"测试源","sourceGroup":"中文",
          "searchUrl":"/search?q=key","tocUrl":"/toc","contentUrl":"/content",
          "enabled":true,"sourceComment":"test comment"}]
        """.trimIndent()

        val count = repo.importJson(json)
        assertEquals(1, count)
        val src = repo.getAll().first()
        assertEquals("测试源", src.sourceName)
        assertEquals("中文", src.sourceGroup)
        assertEquals("/search?q=key", src.searchUrl)
        assertEquals("/toc", src.tocUrl)
        assertEquals("/content", src.contentUrl)
    }
}
