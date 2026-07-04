package com.reader.ui.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceDemoRouteStateTest {
    @Test
    fun requestedSourceRoutesResolveToThreeStateMachines() {
        assertEquals(
            setOf(
                "source-import-options",
                "source-batch",
                "source-groups",
                "source-detail",
                "source-detect",
                "source-rule-edit",
                "source-debug",
                "source-debug-search-result",
                "source-debug-detail-result",
                "source-debug-catalog-result",
                "source-debug-content-log",
                "source-edit-debug",
                "source-logs",
                "source-code-view",
                "source-delete-confirm"
            ),
            sourceDemoRouteIds()
        )

        SourceDemoRouteIds.requestedRouteIds.forEach { routeId ->
            assertNotNull("$routeId should resolve", sourceDemoRouteState(routeId))
        }
        assertEquals(SourceDemoRouteFamily.Management, sourceDemoRouteState(SourceDemoRouteIds.SourceImportOptions)!!.family)
        assertEquals(SourceDemoRouteFamily.RuleEdit, sourceDemoRouteState(SourceDemoRouteIds.SourceRuleEdit)!!.family)
        assertEquals(SourceDemoRouteFamily.Debug, sourceDemoRouteState(SourceDemoRouteIds.SourceDebug)!!.family)
        assertNull(sourceDemoRouteState("source-switch"))
    }

    @Test
    fun debugResultPagesKeepDemoSegmentTargetsAndEditAction() {
        listOf(
            SourceDemoRouteIds.SourceDebugSearchResult,
            SourceDemoRouteIds.SourceDebugDetailResult,
            SourceDemoRouteIds.SourceDebugCatalogResult
        ).forEach { routeId ->
            val state = sourceDemoRouteState(routeId) as SourceDebugRouteState

            assertEquals(routeId, state.segmentActions[0].routeId)
            assertEquals(SourceDemoRouteIds.SourceCodeView, state.segmentActions[1].routeId)
            assertEquals(SourceDemoRouteIds.SourceDebugContentLog, state.segmentActions[2].routeId)
            assertEquals(SourceDemoRouteIds.SourceRuleEdit, state.actions.last().routeId)
            assertEquals("重新调测", state.actions.first().label)
        }
    }

    @Test
    fun logPagesKeepDemoTargets() {
        val contentLog = sourceDemoRouteState(SourceDemoRouteIds.SourceDebugContentLog) as SourceDebugRouteState
        assertEquals(SourceDemoRouteIds.SourceDebug, contentLog.segmentActions[0].routeId)
        assertEquals(SourceDemoRouteIds.SourceCodeView, contentLog.segmentActions[1].routeId)
        assertEquals(SourceDemoRouteIds.SourceDebugContentLog, contentLog.segmentActions[2].routeId)
        assertEquals(SourceDemoRouteIds.SourceDebug, contentLog.actions[1].routeId)
        assertEquals(SourceDemoRouteIds.SourceRuleEdit, contentLog.actions[2].routeId)
        assertEquals("copy-content-log", contentLog.trailingAction!!.actionKey)

        val logs = sourceDemoRouteState(SourceDemoRouteIds.SourceLogs) as SourceManagementRouteState
        assertEquals(SourceDemoRouteIds.SourceDeleteConfirm, logs.trailingAction!!.routeId)
        assertEquals(SourceDemoRouteIds.SourceDebugContentLog, logs.logRows.first().routeId)
        assertEquals(SourceDemoRouteIds.SourceDebug, logs.logRows[1].routeId)
    }

    @Test
    fun sourceCodeViewKeepsDemoTargets() {
        val state = sourceDemoRouteState(SourceDemoRouteIds.SourceCodeView) as SourceDebugRouteState

        assertEquals(SourceDemoRouteIds.SourceDebug, state.segmentActions[0].routeId)
        assertEquals(SourceDemoRouteIds.SourceCodeView, state.segmentActions[1].routeId)
        assertEquals(SourceDemoRouteIds.SourceDebugContentLog, state.segmentActions[2].routeId)
        assertEquals(SourceDemoRouteIds.SourceDebug, state.actions.last().routeId)
        assertEquals("copy-source-code", state.trailingAction!!.actionKey)
        assertTrue(state.codeLines.first().contains("<html>"))
    }

    @Test
    fun deleteConfirmKeepsDemoDestructiveTargets() {
        val state = sourceDemoRouteState(SourceDemoRouteIds.SourceDeleteConfirm) as SourceManagementRouteState
        val cancel = state.dialogActions[0]
        val confirm = state.dialogActions[1]

        assertEquals(SourceDemoRouteIds.SourceBatch, cancel.routeId)
        assertTrue(cancel.routeBack)
        assertEquals(SourceDemoRouteIds.SourceManagement, confirm.routeId)
        assertTrue(confirm.replace)
        assertTrue(confirm.danger)
        assertEquals("delete-confirm", confirm.actionKey)
    }

    @Test
    fun sourceDetectStepTargetsMatchDemoDebugRoutes() {
        val state = sourceDemoRouteState(SourceDemoRouteIds.SourceDetect) as SourceDebugRouteState

        assertEquals(SourceDemoRouteIds.SourceCodeView, state.detectSteps[0].routeId)
        assertEquals(SourceDemoRouteIds.SourceDebugSearchResult, state.detectSteps[1].routeId)
        assertEquals(SourceDemoRouteIds.SourceDebugDetailResult, state.detectSteps[2].routeId)
        assertEquals(SourceDemoRouteIds.SourceDebugCatalogResult, state.detectSteps[3].routeId)
        assertEquals(SourceDemoRouteIds.SourceDebug, state.detectSteps[4].routeId)
    }
}
