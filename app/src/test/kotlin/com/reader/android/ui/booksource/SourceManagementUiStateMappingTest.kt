package com.reader.android.ui.booksource

import com.reader.android.data.model.BookSource
import com.reader.android.ui.adapter.ReaderIntegrationLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceManagementUiStateMappingTest {

    @Test
    fun `existing source list maps to source management ui state`() {
        val state = SourceManagementMapper.fromSources(SourceManagementFixture.sources)

        assertEquals(2, state.sources.size)
        assertEquals(ReaderIntegrationLevel.READY_EXISTING_FLOW, state.integrationLevel)
        assertTrue(state.allowRealDataIntegration)
        assertEquals("existing repository flow", state.fakeRealModeLabel)
    }

    @Test
    fun `disabled source is expressible`() {
        val state = SourceManagementMapper.fromSources(SourceManagementFixture.sources)
        val disabled = state.sources.single { !it.enabled }

        assertEquals(SourceUiStatus.Disabled, disabled.status)
        assertEquals("已禁用", disabled.status.label)
    }

    @Test
    fun `blank json import shows local prompt`() {
        val importState = SourceManagementMapper.importPreview("   ")

        assertEquals(SourceImportStatus.BlankJson, importState.status)
        assertTrue(importState.message.contains("为空"))
    }

    @Test
    fun `url input validation preserves custom and repository url states`() {
        val customBlank = SourceManagementMapper.validateUrlInput("", SourceImportMode.CustomUrl)
        val repositoryInvalid = SourceManagementMapper.validateUrlInput("fixture.local/repo.json", SourceImportMode.RepositoryUrl)
        val repositoryValid = SourceManagementMapper.validateUrlInput("https://fixture.local/repo.json", SourceImportMode.RepositoryUrl)

        assertFalse(customBlank.isValid)
        assertFalse(repositoryInvalid.isValid)
        assertTrue(repositoryValid.isValid)
        assertTrue(repositoryInvalid.message.contains("http://"))
    }

    @Test
    fun `source test loading success and failure are expressible`() {
        val loading = SourceManagementMapper.testLoading("source-a")
        val success = SourceManagementMapper.testSuccess("source-a")
        val failure = SourceManagementMapper.testFailure("source-a", "测试失败")

        assertEquals(SourceTestStatus.Loading, loading.status)
        assertEquals(SourceTestStatus.Success, success.status)
        assertEquals(SourceTestStatus.Failure, failure.status)
        assertEquals("测试失败", failure.message)
    }

    @Test
    fun `source with missing required fields maps to error state`() {
        val state = SourceManagementMapper.fromSources(
            listOf(BookSource(sourceUrl = "", sourceName = ""))
        )

        val source = state.sources.single()
        assertEquals(SourceUiStatus.Error, source.status)
        assertTrue(source.errorMessage!!.contains("sourceUrl"))
    }
}
