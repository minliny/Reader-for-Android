package com.reader.android.ui.reader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class ReaderNativeComponentContractTest {

    // ── Custom components exist ──

    @Test
    fun `reader native components file exists with required composables`() {
        val source = String(Files.readAllBytes(Paths.get(
            "src/main/kotlin/com/reader/android/ui/components/ReaderNativeComponents.kt"
        )))
        listOf("ReaderPanel", "ReaderIconButton", "ReaderActionButton",
            "ReaderSwitch", "ReaderSlider", "ReaderProgressRail",
            "ReaderChip", "ReaderSettingRow"
        ).forEach { component ->
            assertTrue("Must have $component", "fun $component" in source)
        }
    }

    // ── ReaderControlBase uses custom components ──

    @Test
    fun `reader control base uses ReaderIconButton not IconButton`() {
        val source = String(Files.readAllBytes(Paths.get(
            "src/main/kotlin/com/reader/android/ui/reader/components/ReaderControlBase.kt"
        )))
        assertTrue("Must use ReaderIconButton", "ReaderIconButton" in source)
    }

    @Test
    fun `reader control base uses ReaderProgressRail not LinearProgressIndicator`() {
        val source = String(Files.readAllBytes(Paths.get(
            "src/main/kotlin/com/reader/android/ui/reader/components/ReaderControlBase.kt"
        )))
        assertTrue("Must use ReaderProgressRail", "ReaderProgressRail" in source)
        assertFalse("Must not use Material3 LinearProgressIndicator",
            "import androidx.compose.material3.LinearProgressIndicator" in source)
    }

    // ── Regression guards ──

    @Test
    fun `quick buttons still no text labels`() {
        val source = String(Files.readAllBytes(Paths.get(
            "src/main/kotlin/com/reader/android/ui/reader/components/ReaderControlBase.kt"
        )))
        assertTrue("Must have Search icon token", "ReaderIconToken.Search" in source)
        assertTrue("Must have NightMode icon token", "ReaderIconToken.NightMode" in source)
        assertFalse("Must not import Material Icons directly", "import androidx.compose.material.icons" in source)
    }

    @Test
    fun `bottom bar labels unchanged`() {
        val source = String(Files.readAllBytes(Paths.get(
            "src/main/kotlin/com/reader/android/ui/reader/components/ReaderControlBase.kt"
        )))
        assertTrue("Must have 目录", "目录" in source)
        assertTrue("Must have 朗读", "朗读" in source)
        assertTrue("Must have 界面设置", "界面设置" in source)
        assertTrue("Must have 阅读行为设置", "阅读行为设置" in source)
    }

    @Test
    fun `overlay zone not full screen`() {
        val source = String(Files.readAllBytes(Paths.get(
            "src/main/kotlin/com/reader/android/ui/reader/components/ReaderControlBase.kt"
        )))
        assertTrue("Must use zone-based overlay", "quickActionsBottomInset" in source)
    }

    @Test
    fun `night mode not dialog`() {
        val source = String(Files.readAllBytes(Paths.get(
            "src/main/kotlin/com/reader/android/ui/reader/components/ReaderControlBase.kt"
        )))
        assertFalse("Must not be AlertDialog", "AlertDialog" in source)
    }
}
