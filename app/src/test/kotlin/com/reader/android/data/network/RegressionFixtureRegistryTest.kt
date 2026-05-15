package com.reader.android.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RegressionFixtureRegistryTest {

    @Test
    fun `register and retrieve fixture with metadata`() {
        val reg = RegressionFixtureRegistry()
        val entry = FixtureEntry("search_biquge", ParserType.SEARCH, "笔趣阁", "search fixture", "<html></html>")
        val meta = FixtureMeta("search_biquge", ParserType.SEARCH, "笔趣阁", "search test")
        reg.registerFixture(entry, meta)
        assertEquals(1, reg.count())
        assertEquals("search test", reg.getMeta("search_biquge")?.description)
    }

    @Test
    fun `allLocalFixtures filters by local-only`() {
        val reg = RegressionFixtureRegistry()
        reg.registerFixture(
            FixtureEntry("a", ParserType.SEARCH, "", "", ""),
            FixtureMeta("a", ParserType.SEARCH, "", "", isLocalOnly = true)
        )
        reg.registerFixture(
            FixtureEntry("b", ParserType.SEARCH, "", "", ""),
            FixtureMeta("b", ParserType.SEARCH, "", "", isLocalOnly = false)
        )
        assertEquals(1, reg.allLocalFixtures().size)
    }

    @Test
    fun `allParserTypes returns distinct types`() {
        val reg = RegressionFixtureRegistry()
        reg.registerFixture(FixtureEntry("a", ParserType.SEARCH, "", "", ""), FixtureMeta("a", ParserType.SEARCH, "", ""))
        reg.registerFixture(FixtureEntry("b", ParserType.TOC, "", "", ""), FixtureMeta("b", ParserType.TOC, "", ""))
        assertEquals(2, reg.allParserTypes().size)
    }
}
