package com.reader.android.data.network

data class FixtureMeta(
    val id: String,
    val parserType: ParserType,
    val sourceName: String,
    val description: String,
    val isLocalOnly: Boolean = true
)

class RegressionFixtureRegistry(private val registry: FixtureRegistry = FixtureRegistry()) {
    private val metadata = mutableMapOf<String, FixtureMeta>()

    fun registerFixture(entry: FixtureEntry, meta: FixtureMeta) {
        registry.register(entry)
        metadata[entry.id] = meta
    }

    fun getMeta(id: String) = metadata[id]
    fun allLocalFixtures() = metadata.values.filter { it.isLocalOnly }
    fun count(): Int = registry.count()
    fun allParserTypes(): Set<ParserType> = registry.all().map { it.parserType }.toSet()
}
