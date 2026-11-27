package io.reader.rebuild.features.reading

/** Translates Android UTF-16 boundary indices into a Core chapter scalar window. */
internal class TextScalarMap(private val text: String, val chapterStart: Long = 0L) {
    private val offsets = IntArray(text.codePointCount(0, text.length) + 1)
    val chapterEnd = Math.addExact(chapterStart, offsets.lastIndex.toLong())

    init {
        require(chapterStart >= 0)
        var index = 0
        for (scalar in 0 until offsets.lastIndex) {
            val point = text.codePointAt(index)
            require(point !in Char.MIN_SURROGATE.code..Char.MAX_SURROGATE.code) {
                "Chapter text must contain complete Unicode scalars"
            }
            index += Character.charCount(point)
            offsets[scalar + 1] = index
        }
    }

    fun hasCharacter(scalar: Long): Boolean = scalar in chapterStart until chapterEnd

    fun utf16(scalar: Long): Int? =
        if (scalar in chapterStart..chapterEnd) offsets[(scalar - chapterStart).toInt()] else null

    fun scalar(offset: Int): Long? =
        offsets.binarySearch(offset).takeIf { it >= 0 }?.let { chapterStart + it }

    /** Native hit-testing can land inside a supplementary character's two UTF-16 units. */
    fun scalarAtOrBefore(offset: Int): Long? {
        if (offset !in 0..text.length) return null
        val index = offsets.binarySearch(offset)
        return chapterStart + if (index >= 0) index else -index - 2
    }

    fun substring(start: Long, end: Long): String? {
        if (start > end) return null
        val first = utf16(start) ?: return null
        val last = utf16(end) ?: return null
        return text.substring(first, last)
    }
}
