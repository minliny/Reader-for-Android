package com.reader.android.ui.prototype

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class ReaderPrototypeVisualPolishTest {

    private val gallerySource: String by lazy {
        String(Files.readAllBytes(Paths.get("src/main/kotlin/com/reader/android/ui/prototype/ReaderPrototypeGallery.kt")))
    }

    private val catalogSource: String by lazy {
        String(Files.readAllBytes(Paths.get("src/main/kotlin/com/reader/android/ui/prototype/ReaderPrototypeCatalog.kt")))
    }

    @Test
    fun `prototype catalog keeps required coverage and polish copy`() {
        assertTrue(ReaderPrototypeCatalog.entries.size >= 38)
        assertEquals(38, ReaderPrototypeCatalog.entries.size)
        assertTrue(ReaderPrototypeCatalog.entries.all { it.description.isNotBlank() })
        assertTrue(ReaderPrototypeCatalog.entries.any { it.description.contains("UI fixture") })
    }

    @Test
    fun `prototype gallery exposes readable selected state`() {
        listOf("UI 原型预览", "原型目录", "当前：", "当前预览", "已选").forEach { token ->
            assertTrue("Gallery must expose $token", token in gallerySource)
        }
        assertFalse("Gallery must not keep English debug placeholder", "Prototype fixture only" in catalogSource + gallerySource)
    }

    @Test
    fun `state prototypes keep clear actions`() {
        listOf("去搜索", "重试", "授权后才能继续读取本地文件").forEach { token ->
            assertTrue("State prototype must keep $token", token in gallerySource)
        }
    }
}
