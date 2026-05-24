package com.reader.android.ui.reader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class ReaderControlVisualRegressionTest {

    private val readerSource: String by lazy {
        val dir = Paths.get("src/main/kotlin/com/reader/android/ui/reader")
        Files.walk(dir)
            .filter { Files.isRegularFile(it) && it.toString().endsWith(".kt") }
            .map { String(Files.readAllBytes(it)) }
            .toList()
            .joinToString("\n")
    }

    @Test
    fun `reader bottom bar remains reader control labels`() {
        listOf("目录", "朗读", "界面", "设置").forEach { label ->
            assertTrue("Reader control must keep $label", label in readerSource)
        }
    }

    @Test
    fun `reader quick actions remain icon first without text labels`() {
        listOf("搜索", "自动翻页", "内容替换", "夜间模式").forEach { label ->
            assertTrue("Quick action semantic label must remain available: $label", label in readerSource)
        }
        assertFalse("Night mode must not become a dialog", "AlertDialog" in readerSource && "夜间模式" in readerSource)
        assertFalse("Reader page control must not use skip_previous", "skip_previous" in readerSource)
        assertFalse("Reader page control must not use skip_next", "skip_next" in readerSource)
    }

    @Test
    fun `reader settings do not mix global modules`() {
        val settingsSection = readerSource.substringAfter("ReaderSettingsOverlay").take(5000)
        listOf("WebDAV", "RSS", "关于", "备份").forEach { forbidden ->
            assertFalse("Reader settings must not contain $forbidden", forbidden in settingsSection)
        }
    }
}
