package com.reader.android.data.adapter

enum class BoundaryEvent {
    CHAPTER_START,
    CHAPTER_END,
    PARAGRAPH_BOUNDARY
}

data class ChapterTextSegment(
    val text: String,
    val chapterTitle: String,
    val chapterIndex: Int,
    val boundary: BoundaryEvent
)

class ChapterTextFeeder(private val chapterContent: String, private val chapterTitle: String = "", private val chapterIndex: Int = 0) {
    private val paragraphs = chapterContent.split(Regex("\n\n+")).filter { it.isNotBlank() }
    private var currentParagraph = 0

    fun hasNext(): Boolean = currentParagraph < paragraphs.size

    fun next(): ChapterTextSegment? {
        if (!hasNext()) return null
        val boundary = when (currentParagraph) {
            0 -> BoundaryEvent.CHAPTER_START
            else -> BoundaryEvent.PARAGRAPH_BOUNDARY
        }
        return ChapterTextSegment(
            text = paragraphs[currentParagraph++],
            chapterTitle = chapterTitle,
            chapterIndex = chapterIndex,
            boundary = boundary
        )
    }

    fun nextChapterSignal(): ChapterTextSegment {
        return ChapterTextSegment(
            text = "",
            chapterTitle = chapterTitle,
            chapterIndex = chapterIndex,
            boundary = BoundaryEvent.CHAPTER_END
        )
    }

    fun reset() { currentParagraph = 0 }
    fun remaining(): Int = paragraphs.size - currentParagraph
}
