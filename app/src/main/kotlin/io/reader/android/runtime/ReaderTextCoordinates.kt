package io.reader.android.runtime

/** Android layout offsets are UTF-16; the Core wire uses Unicode scalar offsets. */
object ReaderTextCoordinates {
    fun scalarOffset(text: String, utf16Offset: Int): Int {
        require(utf16Offset in 0..text.length)
        require(utf16Offset == 0 || utf16Offset == text.length ||
            !(Character.isHighSurrogate(text[utf16Offset - 1]) && Character.isLowSurrogate(text[utf16Offset]))) {
            "UTF16_OFFSET_SPLITS_SCALAR"
        }
        return text.codePointCount(0, utf16Offset)
    }

    fun utf16Offset(text: String, scalarOffset: Int): Int {
        require(scalarOffset in 0..text.codePointCount(0, text.length))
        return text.offsetByCodePoints(0, scalarOffset)
    }
}
