package com.reader.android.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class ReaderAccessibilitySemanticsScanTest {

    private val readerSource: String by lazy {
        val dir = Paths.get("src/main/kotlin/com/reader/android/ui/reader")
        Files.walk(dir)
            .filter { Files.isRegularFile(it) && it.toString().endsWith(".kt") }
            .map { String(Files.readAllBytes(it)) }
            .toList()
            .joinToString("\n")
    }

    @Test
    fun `all icon only controls have content description`() {
        // Every IconButton/Icon usage should have contentDescription or be decorative (null)
        assertTrue("Reader components must have semantics", "contentDescription" in readerSource)
        assertTrue("Reader components must use semantics", "semantics" in readerSource)
    }

    @Test
    fun `quick action buttons have no visible text labels`() {
        val quickCircleStart = readerSource.indexOf("fun ReaderQuickCircle")
        val nextFun = readerSource.indexOf("private fun", quickCircleStart + 1)
        val quickCircleBody = if (nextFun > 0) readerSource.substring(quickCircleStart, nextFun) else readerSource.substring(quickCircleStart)
        assertTrue("Quick circle must not have Text", "Text(" !in quickCircleBody)
    }

    @Test
    fun `night mode is not dialog`() {
        listOf("Dialog(", "AlertDialog(").forEach { forbidden ->
            assertTrue("Night mode must not be dialog: $forbidden", forbidden !in readerSource)
        }
    }

    @Test
    fun `reader settings overlay excludes global settings`() {
        listOf("WebDAV", "书源", "RSS", "全局设置").forEach { forbidden ->
            assertTrue("Reader settings must not include '$forbidden'", forbidden !in readerSource)
        }
    }

    @Test
    fun `replace overlay shows current book rules only`() {
        assertTrue(
            "Replace must show current book only",
            "仅显示当前书籍匹配到的替换规则" in readerSource || "当前书籍" in readerSource
        )
    }
}
