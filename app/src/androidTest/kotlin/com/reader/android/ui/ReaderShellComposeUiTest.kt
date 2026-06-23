package com.reader.android.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.reader.android.ui.components.ReaderMainTab
import com.reader.android.ui.components.ReaderMainTabBar
import com.reader.android.ui.reader.ReaderRuntimeFixture
import com.reader.android.ui.reader.ReaderScreen
import com.reader.android.ui.theme.ReaderTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ReaderShellComposeUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun mainTabBarKeepsFourFixedButtonsAndHandlesSelection() {
        var selectedFromCallback = -1

        composeRule.setContent {
            ReaderTheme {
                var selectedIndex by remember { mutableStateOf(0) }
                ReaderMainTabBar(
                    tabs = appScreens.map { screen ->
                        ReaderMainTab(
                            label = screen.label,
                            contentDescription = screen.label,
                            icon = screen.icon
                        )
                    },
                    selectedIndex = selectedIndex,
                    onTabSelected = { index ->
                        selectedIndex = index
                        selectedFromCallback = index
                    }
                )
            }
        }

        listOf("书架", "发现", "RSS", "设置").forEach { label ->
            composeRule.onNodeWithTag("MainTab-$label").assertExists()
            composeRule.onNodeWithText(label).assertExists()
        }

        composeRule.onNodeWithTag("MainTab-RSS").performClick()
        composeRule.runOnIdle {
            assertEquals(2, selectedFromCallback)
        }
    }

    @Test
    fun readerControlLayerExposesModuleAndQuickActionControls() {
        composeRule.setContent {
            ReaderTheme {
                ReaderScreen(runtimeState = ReaderRuntimeFixture.createAppearanceOverlay())
            }
        }

        composeRule.onNodeWithContentDescription("阅读控制底栏").assertExists()
        listOf("目录", "朗读", "界面设置", "阅读行为设置").forEach { label ->
            composeRule.onNodeWithTag("ReaderModule-$label").assertExists()
        }
        listOf("搜索本章", "自动翻页", "内容替换").forEach { label ->
            composeRule.onNodeWithContentDescription(label).assertExists()
        }
    }
}
