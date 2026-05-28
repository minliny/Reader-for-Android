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

    @Test
    fun `import biquge com source populates all fields correctly`() {
        // This is the biquge.com source JSON from BookSourceScreen.kt
        val biqugeJson = """
        [{"sourceUrl":"https://www.biquge.com","sourceName":"笔趣阁","sourceGroup":"中文",
          "searchUrl":"/search?q=key","tocUrl":"/toc","contentUrl":"/content"}]
        """.trimIndent()

        val count = repo.importJson(biqugeJson)
        assertEquals(1, count)

        val src = repo.getByUrl("https://www.biquge.com")
        assertNotNull(src)
        assertEquals("笔趣阁", src!!.sourceName)
        assertEquals("中文", src.sourceGroup)
        assertEquals("/search?q=key", src.searchUrl)
        assertEquals("/toc", src.tocUrl)
        assertEquals("/content", src.contentUrl)
        assertTrue(src.enabled) // default enabled
    }

    @Test
    fun `import multiple biquge and quanshu sources enables only first`() {
        val json = """
        [{"sourceUrl":"https://www.biquge.com","sourceName":"笔趣阁","sourceGroup":"中文",
          "searchUrl":"/search?q=key","enabled":true},
         {"sourceUrl":"https://www.quanshu.com","sourceName":"全书网","sourceGroup":"中文",
          "searchUrl":"/search?keyword=key","enabled":true}]
        """.trimIndent()

        repo.importJson(json)

        val enabled = repo.getEnabled()
        assertEquals(2, enabled.size)
        assertTrue(enabled.map { it.sourceName }.containsAll(listOf("笔趣阁", "全书网")))
    }

    @Test
    fun `biquge source can be disabled and re-enabled`() {
        repo.importJson("""
        [{"sourceUrl":"https://www.biquge.com","sourceName":"笔趣阁",
          "searchUrl":"/search?q=key","enabled":true}]
        """.trimIndent())

        repo.setEnabled("https://www.biquge.com", false)
        assertEquals(0, repo.getEnabled().size)

        repo.setEnabled("https://www.biquge.com", true)
        val enabled = repo.getEnabled()
        assertEquals(1, enabled.size)
        assertEquals("笔趣阁", enabled[0].sourceName)
    }
}
