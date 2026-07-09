package com.reader.ui.reading

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.reader.ui.shell.SourceSwitchResult
import com.reader.ui.shell.SourceSwitchState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * FlowShell 换源 screen smoke 测试 — B1-Android Step 5。
 *
 * 渲染 [ReaderSourceSwitchWindow]（FlowShell comparisonRegion 的核心 surface）并断言
 * 关键元素（"换源" 标题、排序提示、候选源名称）在 Results / Loading 态下可见。
 *
 * 颜色走 readerExtraColors() 语义层（--fd-ds-color-* token 族），不依赖 raw rgba；
 * readerExtraColors() 在 createComposeRule() 环境下返回 staticCompositionLocalOf 默认值，
 * 不需要额外提供 LocalReaderExtraColors。
 *
 * 运行命令：
 *   ./gradlew :app:connectedDebugAndroidTest \
 *     -Pandroid.testInstrumentationRunnerArguments.class=com.reader.ui.reading.SourceSwitchScreenSmokeTest
 */
@RunWith(AndroidJUnit4::class)
class SourceSwitchScreenSmokeTest {

    @get:Rule
    val composeRule = createComposeRule()

    /**
     * Results 态：断言标题"换源"、排序提示"按延迟排序"、候选源名称与速度可见。
     * selectedSourceId="s1" → "优书网" 行高亮（isCurrent=true）。
     */
    @Test
    fun resultsState_showsHeaderAndCandidateNames() {
        val results = listOf(
            SourceSwitchResult(
                sourceId = "s1",
                sourceName = "优书网",
                latestChapter = "第 32 章 雨夜",
                speedLevel = 1
            ),
            SourceSwitchResult(
                sourceId = "s2",
                sourceName = "笔趣阁镜像",
                latestChapter = "第 32 章 雨夜",
                speedLevel = 2
            )
        )
        composeRule.setContent {
            ReaderSourceSwitchWindow(
                sourceSwitch = SourceSwitchState.Results(
                    results = results,
                    selectedSourceId = "s1"
                ),
                onSelectSource = {},
                onClose = {}
            )
        }
        // 标题"换源"可见
        composeRule.onNodeWithText("换源").assertIsDisplayed()
        // 排序提示可见
        composeRule.onNodeWithText("按延迟排序").assertIsDisplayed()
        // 候选源名称可见
        composeRule.onNodeWithText("优书网").assertIsDisplayed()
        composeRule.onNodeWithText("笔趣阁镜像").assertIsDisplayed()
        // 速度文本可见（speedLevel * 100 ms）
        composeRule.onNodeWithText("100 ms").assertIsDisplayed()
        composeRule.onNodeWithText("200 ms").assertIsDisplayed()
    }

    /**
     * Loading 态：断言标题"换源"可见，且展示 3 行"加载中…"占位。
     */
    @Test
    fun loadingState_showsPlaceholderRows() {
        composeRule.setContent {
            ReaderSourceSwitchWindow(
                sourceSwitch = SourceSwitchState.Loading,
                onSelectSource = {},
                onClose = {}
            )
        }
        // 标题仍然可见
        composeRule.onNodeWithText("换源").assertIsDisplayed()
        // 加载态展示 3 行"加载中…"占位
        composeRule.onAllNodesWithText("加载中…").assertCountEquals(3)
    }
}
