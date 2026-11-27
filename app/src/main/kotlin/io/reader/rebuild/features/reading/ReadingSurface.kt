package io.reader.rebuild.features.reading

import com.reader.core.theme.ReaderDesignMetrics
import io.reader.rebuild.design.ReaderReadingColors
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
internal data class ReadingStyle(
    val fontSize: Double = ReaderDesignMetrics.readerBody.fontSize,
    val lineHeight: Double = ReaderDesignMetrics.readerBody.lineHeight,
    val horizontalInset: Int = ReaderDesignMetrics.screenInset,
    val paragraphSpacing: Int = ReaderDesignMetrics.readerParagraphSpacing,
    val paper: String = ReaderReadingColors.paper,
    val ink: String = ReaderReadingColors.ink,
    val letterSpacing: Double = 0.0,
)

@Serializable
internal data class ReadingPosition(val scalar: Long, val text: String, val loadedImageCount: Int? = null)

@Serializable
internal data class ReadingSelection(val startScalar: Long, val endScalar: Long)

@Serializable
internal data class SurfaceEvent(
    val version: Int, val session: String, val documentId: String,
    val layoutGeneration: Long, val kind: String, val value: JsonElement,
)

internal interface ReadingSurface<T> {
    fun open(chapter: T, anchor: Long, style: ReadingStyle = ReadingStyle())
    fun reflow(style: ReadingStyle, anchor: Long?)
    fun restore(scalar: Long, completion: (ReadingPosition?) -> Unit)
    fun currentPosition(completion: (ReadingPosition?) -> Unit)
    fun select(start: Long, end: Long, completion: (ReadingSelection?) -> Unit)
    fun currentSelection(completion: (ReadingSelection?) -> Unit)
    fun retire()
}
