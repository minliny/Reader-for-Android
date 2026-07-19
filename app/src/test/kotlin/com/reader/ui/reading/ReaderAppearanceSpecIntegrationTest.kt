package com.reader.ui.reading

import com.reader.android.data.storage.FontFamily
import com.reader.ui.theme.ReaderThemeResolver
import io.reader.ui.contract.ReaderAppearanceSpecRegistry
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderAppearanceSpecIntegrationTest {
    @Test
    fun `Reader 2 appearance is generated from one canonical spec`() {
        assertEquals("Reader 2/Full/AppearanceContent", ReaderAppearanceSpecRegistry.source.path)
        assertEquals(
            listOf("日间", "暖白", "夜间", "暖夜", "纸纹", "青叶纹", "夜纹", "林夜纹"),
            ReaderThemeResolver.FULL_APPEARANCE_THEMES.map { it.label }
        )
        assertEquals(
            listOf("#FFFFFF", "#FBF0DF", "#26231F", "#302922", "#F5EAD8", "#E7F0E2", "#34302B", "#263129"),
            ReaderAppearanceSpecRegistry.themes.map { it.swatchHex }
        )
        assertEquals(
            listOf("系统", "宋体", "黑体", "楷体", "仿宋", "等宽", "思源宋体", "霞鹜文楷", "+ 导入"),
            ReaderAppearanceSpecRegistry.fonts.map { it.label }
        )
        assertEquals(listOf(18.0, 1.96, 16.0, 0.0), ReaderAppearanceSpecRegistry.steppers.map { it.defaultValue })
    }

    @Test
    fun `legacy full settings pages consume the generated appearance projection`() {
        assertEquals(
            listOf("系统", "宋体", "黑体", "楷体", "仿宋", "等宽", "思源宋体", "霞鹜文楷"),
            readerFullAppearanceFonts.map { it.label }
        )
        assertEquals(
            listOf("日间", "暖白", "夜间", "暖夜", "纸纹", "青叶纹", "夜纹", "林夜纹"),
            readerFullAppearanceThemes.map { it.label }
        )
        assertEquals(2.04f, nextReaderAppearanceValue(1.96f, readerFullAppearanceStepper("lineHeight")), 0.0001f)
        assertEquals(0.2f, nextReaderAppearanceValue(0f, readerFullAppearanceStepper("letterSpacing")), 0.0001f)
        assertEquals("系统", FontFamily.SYSTEM.displayName)
        assertEquals("宋体", FontFamily.SERIF.displayName)
        assertEquals("黑体", FontFamily.SANS_SERIF.displayName)
    }
}
