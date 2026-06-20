package com.reader.android.ui.navigation

import com.reader.android.ui.appScreens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AppMainNavVisualRegressionTest {

    @Test
    fun `main nav stays four balanced modules`() {
        assertEquals(4, appScreens.size)
        assertEquals(listOf("书架", "发现", "RSS", "设置"), appScreens.map { it.label })
    }

    @Test
    fun `search reader source and mine stay out of main nav`() {
        val labels = appScreens.map { it.label }

        listOf("搜索", "阅读", "书源", "我的").forEach { forbidden ->
            assertFalse("$forbidden must not be a primary visual tab", forbidden in labels)
        }
    }
}
