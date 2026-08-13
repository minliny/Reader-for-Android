package com.reader.android.ui.reader.source

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class SourceSwitchFlowStructureTest {

    private val source: String by lazy {
        String(
            Files.readAllBytes(
                Paths.get("src/main/kotlin/com/reader/android/ui/reader/source/SourceSwitchFlowScreen.kt")
            )
        )
    }

    @Test
    fun `source switch flow exposes FlowShell compose input contract`() {
        listOf(
            "SourceSwitchFlowScreen",
            "SourceSwitchFlowUiState",
            "SourceSwitchFlowState",
            "SourceCandidateState",
            "SourceSwitchFlowFixture",
            "flowFrame",
            "stepRegion",
            "comparisonRegion",
            "resultRegion",
            "stateHost"
        ).forEach { token ->
            assertTrue("SourceSwitchFlowScreen must preserve $token", token in source)
        }
    }

    @Test
    fun `source switch flow preserves required copy and events`() {
        listOf(
            "换源",
            "当前来源",
            "检测中",
            "可用",
            "失效",
            "切换来源",
            "重试",
            "返回阅读页",
            "onBackToReading",
            "onOpenSourceSheet",
            "onFilterChange",
            "onStartDetect",
            "onCancelDetect",
            "onSwitchSource",
            "onRetry",
            "onGrantPermission"
        ).forEach { token ->
            assertTrue("Source switch flow must preserve $token", token in source)
        }
    }

    @Test
    fun `source switch flow covers six state matrix previews`() {
        listOf(
            "SourceSwitchFlowDefaultPreview",
            "SourceSwitchFlowLoadingPreview",
            "SourceSwitchFlowEmptyPreview",
            "SourceSwitchFlowErrorPreview",
            "SourceSwitchFlowOfflinePreview",
            "SourceSwitchFlowPermissionPreview",
            "SourceSwitchFlowState.Default",
            "SourceSwitchFlowState.Loading",
            "SourceSwitchFlowState.Empty",
            "SourceSwitchFlowState.Error",
            "SourceSwitchFlowState.Offline",
            "SourceSwitchFlowState.Permission",
            "widthDp = 1690",
            "heightDp = 931"
        ).forEach { token ->
            assertTrue("Source switch flow must include state matrix token $token", token in source)
        }
    }

    @Test
    fun `source switch flow remains independent from main tab and settings shells`() {
        listOf(
            "ReaderMainTabShell",
            "ReaderMainTabBar",
            "SettingsRootScreen",
            "MainTabShell"
        ).forEach { forbidden ->
            assertTrue("Source switch flow must not depend on $forbidden", forbidden !in source)
        }
    }
}
