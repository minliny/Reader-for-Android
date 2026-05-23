package com.reader.android.ui.settings

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class SettingsScreenStructureTest {

    private val screenSource: String by lazy {
        String(
            Files.readAllBytes(
                Paths.get("src/main/kotlin/com/reader/android/ui/settings/SettingsScreen.kt")
            )
        )
    }

    @Test
    fun `settings screen uses reader theme and components`() {
        listOf(
            "ReaderTheme",
            "ReaderAppTopBar",
            "ReaderSettingsGroup",
            "ReaderSettingsSwitchRow"
        ).forEach { token ->
            assertTrue("SettingsScreen must use $token", token in screenSource)
        }
    }

    @Test
    fun `settings screen preserves themepreferences data binding`() {
        listOf(
            "ThemePreferences",
            "darkMode",
            "fontSize",
            "lineSpacing",
            "pageMargin",
            "collectAsState"
        ).forEach { token ->
            assertTrue("SettingsScreen must preserve $token", token in screenSource)
        }
    }

    @Test
    fun `settings screen exposes reader settings section`() {
        listOf("阅读设置", "夜间模式", "字号", "行间距", "页边距").forEach { text ->
            assertTrue("SettingsScreen must display '$text'", text in screenSource)
        }
    }

    @Test
    fun `settings screen configures slider colors with reader theme`() {
        listOf("SliderDefaults.colors", "ReaderTheme.colors.primary", "ReaderTheme.colors.mutedTrack").forEach { token ->
            assertTrue("SettingsScreen sliders must use $token", token in screenSource)
        }
    }

    @Test
    fun `settings screen does not reintroduce stitch old tokens`() {
        listOf(
            "bg-" + "surface-container",
            "bg-" + "surface-container-high",
            "bg-" + "surface-container-highest",
            "text-" + "on-surface",
            "text-" + "on-surface-variant",
            "shadow-" + "lg",
            "shadow-" + "md",
            "#" + "fdf6ec", "#" + "eae1da", "#" + "f5ece6", "#" + "efe7e0", "#" + "8b5000",
            "MaterialTheme",
            "Scaffold",
            "TopAppBar",
            "ExperimentalMaterial3Api",
            "HorizontalDivider",
            "Web" + "View",
            "normalized-" + "html"
        ).forEach { forbidden ->
            assertTrue(
                "SettingsScreen must not reintroduce $forbidden",
                forbidden !in screenSource
            )
        }
    }
}
