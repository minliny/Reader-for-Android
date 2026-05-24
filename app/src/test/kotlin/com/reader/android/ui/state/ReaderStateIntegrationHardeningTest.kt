package com.reader.android.ui.state

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderStateIntegrationHardeningTest {

    @Test
    fun `every module has applicable hardened states`() {
        val coverage = ReaderStateMapper.moduleCoverage()

        assertEquals(ReaderModuleKey.entries.size, coverage.size)
        ReaderModuleKey.entries.forEach { module ->
            val moduleCoverage = coverage.single { it.module == module }
            assertTrue("${module.label} must expose at least three states", moduleCoverage.states.size >= 3)
        }
    }

    @Test
    fun `modules expose loading empty error or applicable equivalents`() {
        val coverage = ReaderStateMapper.moduleCoverage().associateBy { it.module }

        assertTrue(coverage.getValue(ReaderModuleKey.Bookshelf).covers(ReaderGlobalStateKey.Loading))
        assertTrue(coverage.getValue(ReaderModuleKey.Bookshelf).covers(ReaderGlobalStateKey.Empty))
        assertTrue(coverage.getValue(ReaderModuleKey.Bookshelf).covers(ReaderGlobalStateKey.Error))
        assertTrue(coverage.getValue(ReaderModuleKey.Search).covers(ReaderGlobalStateKey.Empty))
        assertTrue(coverage.getValue(ReaderModuleKey.BookDetail).covers(ReaderGlobalStateKey.Error))
        assertTrue(coverage.getValue(ReaderModuleKey.Reader).covers(ReaderGlobalStateKey.Offline))
        assertTrue(coverage.getValue(ReaderModuleKey.SourceManagement).covers(ReaderGlobalStateKey.ImportSuccess))
        assertTrue(coverage.getValue(ReaderModuleKey.SourceManagement).covers(ReaderGlobalStateKey.ImportFailure))
        assertTrue(coverage.getValue(ReaderModuleKey.DiscoverRss).covers(ReaderGlobalStateKey.Offline))
        assertTrue(coverage.getValue(ReaderModuleKey.WebDavSync).covers(ReaderGlobalStateKey.WebDavAuthError))
        assertTrue(coverage.getValue(ReaderModuleKey.WebDavSync).covers(ReaderGlobalStateKey.SyncConflict))
        assertTrue(coverage.getValue(ReaderModuleKey.MineSettings).covers(ReaderGlobalStateKey.PermissionRequired))
    }

    @Test
    fun `offline and permission states are reusable`() {
        val offlineModules = ReaderStateMapper.moduleCoverage()
            .filter { it.covers(ReaderGlobalStateKey.Offline) }
            .map { it.module }
        val permissionModules = ReaderStateMapper.moduleCoverage()
            .filter { it.covers(ReaderGlobalStateKey.PermissionRequired) }
            .map { it.module }

        assertTrue(ReaderModuleKey.Bookshelf in offlineModules)
        assertTrue(ReaderModuleKey.Reader in offlineModules)
        assertTrue(ReaderModuleKey.DiscoverRss in offlineModules)
        assertTrue(ReaderModuleKey.MineSettings in permissionModules)
    }
}
