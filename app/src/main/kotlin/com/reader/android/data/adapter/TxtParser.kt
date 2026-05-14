package com.reader.android.data.adapter

data class TxtParseResult(
    val title: String?,
    val chapters: List<TxtChapter>,
    val estimatedEncoding: String = "UTF-8"
)

data class TxtChapter(
    val index: Int,
    val title: String,
    val startLine: Int,
    val endLine: Int
)

class TxtParser {
    private val chapterPattern = Regex(
        """^(第[零一二三四五六七八九十百千万0-9]+[章节回卷集部篇].*|[Cc]hapter\s+\d+.*|序言|前言|楔子|尾声|后记|番外)"""
    )

    fun parse(lines: List<String>, encoding: String = "UTF-8"): TxtParseResult {
        val title = lines.firstOrNull { it.isNotBlank() }?.trim()?.take(100)
        val chapters = mutableListOf<TxtChapter>()
        var currentChapter: TxtChapter? = null

        lines.forEachIndexed { idx, line ->
            if (chapterPattern.matches(line.trim())) {
                currentChapter?.let { chapters.add(it.copy(endLine = idx - 1)) }
                currentChapter = TxtChapter(
                    index = chapters.size,
                    title = line.trim(),
                    startLine = idx,
                    endLine = idx
                )
            }
        }
        currentChapter?.let { chapters.add(it.copy(endLine = lines.size - 1)) }

        return TxtParseResult(title = title, chapters = chapters, estimatedEncoding = encoding)
    }

    fun detectEncoding(bytes: ByteArray): String {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return "UTF-8-BOM"
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) return "UTF-16LE"
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) return "UTF-16BE"
        return "UTF-8"
    }
}
