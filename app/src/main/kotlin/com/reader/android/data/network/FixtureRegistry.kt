package com.reader.android.data.network

enum class ParserType { SEARCH, BOOK_INFO, TOC, CONTENT }

data class FixtureEntry(
    val id: String,
    val parserType: ParserType,
    val sourceName: String,
    val description: String,
    val htmlFixture: String,
    val expectedResultCount: Int? = null,
    val expectedTitle: String? = null
)

class FixtureRegistry {
    private val entries = mutableMapOf<String, FixtureEntry>()

    fun register(entry: FixtureEntry) {
        entries[entry.id] = entry
    }

    fun get(id: String): FixtureEntry? = entries[id]

    fun getByParserType(type: ParserType): List<FixtureEntry> =
        entries.values.filter { it.parserType == type }

    fun all(): List<FixtureEntry> = entries.values.toList()

    fun count(): Int = entries.size
}
