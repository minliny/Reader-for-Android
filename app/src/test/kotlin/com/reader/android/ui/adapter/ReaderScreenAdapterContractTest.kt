package com.reader.android.ui.adapter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class ReaderScreenAdapterContractTest {

    private val adapterSource: String by lazy {
        String(Files.readAllBytes(Paths.get("src/main/kotlin/com/reader/android/ui/adapter/ReaderScreenAdapterContract.kt")))
    }

    @Test
    fun `all fourteen screen modules have adapter contracts`() {
        assertEquals(14, ReaderScreenAdapterRegistry.contracts.size)
        assertEquals(ReaderScreenKey.entries.toSet(), ReaderScreenAdapterRegistry.contracts.map { it.screen }.toSet())
    }

    @Test
    fun `readiness classification counts are preserved`() {
        val counts = ReaderScreenAdapterRegistry.contracts.groupingBy { it.level }.eachCount()

        assertEquals(2, counts[ReaderIntegrationLevel.READY_EXISTING_FLOW])
        assertEquals(1, counts[ReaderIntegrationLevel.READY_FAKE_TO_STATE])
        assertEquals(1, counts[ReaderIntegrationLevel.READY_HOST_SHELL])
        assertEquals(9, counts[ReaderIntegrationLevel.NEEDS_ADAPTER])
        assertEquals(1, counts[ReaderIntegrationLevel.BLOCKED_BY_DESIGN_DECISION])
        assertEquals(null, counts[ReaderIntegrationLevel.BLOCKED_BY_CORE_GAP])
    }

    @Test
    fun `blocked source import is not marked ready`() {
        val sourceImport = ReaderScreenAdapterRegistry.contracts.single {
            it.screen == ReaderScreenKey.SOURCE_IMPORT
        }

        assertEquals(ReaderIntegrationLevel.BLOCKED_BY_DESIGN_DECISION, sourceImport.level)
        assertFalse(sourceImport.allowRealDataIntegration)
        assertTrue(sourceImport.blockedReason!!.contains("design decision"))
    }

    @Test
    fun `adapters map fixture input to screen states`() {
        ReaderScreenAdapterRegistry.all.forEach { adapter ->
            val state = adapter.map()
            assertEquals(adapter.contract.screen.name, state.screenKey)
            assertTrue(state.title.isNotBlank())
        }
    }

    @Test
    fun `adapter layer does not call data layer or runtime io`() {
        listOf(
            "com.reader.android.data",
            "Repository",
            "Parser",
            "Bridge",
            "HttpClient",
            ".execute(",
            ".get(",
            "Room",
            "DataStore"
        ).forEach { forbidden ->
            assertTrue("Adapter source must not contain $forbidden", forbidden !in adapterSource)
        }
    }
}
