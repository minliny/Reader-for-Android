package com.reader.android.data.repository

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class DataStoreBookSourceRepositoryContractTest {

    private val source: String by lazy {
        String(Files.readAllBytes(Paths.get("src/main/kotlin/com/reader/android/data/repository/DataStoreBookSourceRepository.kt")))
    }

    @Test
    fun `load reads one DataStore snapshot instead of collecting forever`() {
        assertTrue("DataStore load must use first() for one-shot initialization", ".first()" in source)
        assertTrue("DataStore load must not use endless collect", ".collect {" !in source)
    }

    @Test
    fun `mutations persist book source changes`() {
        listOf(
            "override fun add",
            "override fun remove",
            "override fun setEnabled",
            "override fun importJson"
        ).forEach { method ->
            val body = source.substringAfter(method).substringBefore("\n    override fun", missingDelimiterValue = source.substringAfter(method))
            assertTrue("$method must save DataStore state", "saveBlocking()" in body)
        }
    }

    @Test
    fun `import merges by source url instead of duplicating`() {
        assertTrue("Import must de-duplicate by sourceUrl", "linkedMapOf<String, BookSource>()" in source)
        assertTrue("Import must key existing sources by sourceUrl", "byUrl[it.sourceUrl] = it" in source)
    }
}
