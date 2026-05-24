package com.reader.android.ui.reader

import com.reader.android.data.model.TOCItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreTocToReaderTocMapperTest {

    private val sampleTree = listOf(
        TOCItem("第一卷", "", children = listOf(
            TOCItem("第一章 下山", "https://example.com/ch1"),
            TOCItem("第二章 初遇", "https://example.com/ch2")
        )),
        TOCItem("第二卷", "", children = listOf(
            TOCItem("第三章 觉醒", "https://example.com/ch3"),
            TOCItem("", "", children = listOf(
                TOCItem("第四章 试剑", "https://example.com/ch4")
            ))
        ))
    )

    @Test
    fun `maps nested tree to flat list`() {
        val result = CoreTocToReaderTocMapper.map(sampleTree)
        // vol1 + ch1 + ch2 + vol2 + ch3 + nested-header + ch4 = 7
        assertEquals(7, result.size)
    }

    @Test
    fun `computes level from tree depth`() {
        val result = CoreTocToReaderTocMapper.map(sampleTree)
        // Top-level items: level 1
        assertEquals(1, result[0].level) // 第一卷
        assertEquals(2, result[1].level) // 第一章
        assertEquals(2, result[2].level) // 第二章
        assertEquals(1, result[3].level) // 第二卷
        assertEquals(2, result[4].level) // 第三章
        assertEquals(2, result[5].level) // "" (nested empty-title header)
        assertEquals(3, result[6].level) // 第四章
    }

    @Test
    fun `entries start with fixture defaults for local state`() {
        val result = CoreTocToReaderTocMapper.map(sampleTree)
        result.forEach {
            assertEquals(false, it.isCurrent)
            assertEquals(false, it.hasBookmark)
            assertEquals(null, it.progress)
        }
    }

    @Test
    fun `blank title uses fallback`() {
        val tree = listOf(TOCItem("", "url"))
        val result = CoreTocToReaderTocMapper.map(tree)
        assertEquals("未命名章节", result[0].title)
    }

    @Test
    fun `flat list has no children`() {
        val tree = listOf(TOCItem("A", "url1"), TOCItem("B", "url2"))
        val result = CoreTocToReaderTocMapper.map(tree)
        assertEquals(2, result.size)
    }

    @Test
    fun `empty input produces empty output`() {
        val result = CoreTocToReaderTocMapper.map(emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `flatten chapters extracts only url entries`() {
        val chapters = CoreTocToReaderTocMapper.flattenChapters(sampleTree)
        // ch1, ch2, ch3, ch4 = 4 leaf chapters
        assertEquals(4, chapters.size)
        chapters.forEach { assertTrue(it.url.isNotBlank()) }
    }
}
