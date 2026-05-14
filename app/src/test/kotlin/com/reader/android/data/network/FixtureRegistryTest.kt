package com.reader.android.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class FixtureRegistryTest {

    @Test
    fun `register and retrieve fixture by id`() {
        val registry = FixtureRegistry()
        registry.register(FixtureEntry(
            id = "search_biquge",
            parserType = ParserType.SEARCH,
            sourceName = "笔趣阁",
            description = "Standard search result page",
            htmlFixture = "<a href='/b/1'>书名</a> 作者：作者名",
            expectedResultCount = 1
        ))
        val entry = registry.get("search_biquge")
        assertNotNull(entry)
        assertEquals("笔趣阁", entry!!.sourceName)
        assertEquals(ParserType.SEARCH, entry.parserType)
    }

    @Test
    fun `get non-existent fixture returns null`() {
        assertNull(FixtureRegistry().get("nonexistent"))
    }

    @Test
    fun `getByParserType filters correctly`() {
        val registry = FixtureRegistry()
        registry.register(FixtureEntry("s1", ParserType.SEARCH, "", "", ""))
        registry.register(FixtureEntry("s2", ParserType.SEARCH, "", "", ""))
        registry.register(FixtureEntry("t1", ParserType.TOC, "", "", ""))

        assertEquals(2, registry.getByParserType(ParserType.SEARCH).size)
        assertEquals(1, registry.getByParserType(ParserType.TOC).size)
    }

    @Test
    fun `count returns total entries`() {
        val registry = FixtureRegistry()
        assertEquals(0, registry.count())
        registry.register(FixtureEntry("a", ParserType.CONTENT, "", "", ""))
        assertEquals(1, registry.count())
    }

    @Test
    fun `all returns all entries`() {
        val registry = FixtureRegistry()
        registry.register(FixtureEntry("a", ParserType.SEARCH, "", "", ""))
        registry.register(FixtureEntry("b", ParserType.BOOK_INFO, "", "", ""))
        assertEquals(2, registry.all().size)
    }
}
