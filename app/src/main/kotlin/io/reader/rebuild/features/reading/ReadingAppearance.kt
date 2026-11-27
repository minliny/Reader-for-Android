package io.reader.rebuild.features.reading

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.reader.core.theme.ReaderDesignMetrics
import com.reader.core.theme.ReaderPaperThemes

internal data class ReadingAppearance(
    val paperId: String = ReaderPaperThemes.all.first().id,
    val font: Double = ReaderDesignMetrics.readerBody.fontSize,
    val line: Double = 1.96,
    val scrolling: Boolean = false,
    val spacing: Double = 0.0,
) {
    val paper get() = ReaderPaperThemes.byId(paperId) ?: ReaderPaperThemes.all.first()
    val style get() = ReadingStyle(fontSize = font, lineHeight = font * line,
        paper = paper.paperStart.toCss(), ink = paper.ink.toCss(), letterSpacing = spacing)
}

private fun Long.toCss() = "#" + (this and 0xffffff).toString(16).padStart(6, '0')

@Composable
internal fun ReaderPaperFrame(
    appearance: ReadingAppearance,
    modifier: Modifier = Modifier,
    statusInset: Dp = 0.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    Column(modifier.fillMaxSize().background(Color(appearance.paper.paperStart.toInt()))) {
        if (statusInset > 0.dp) Box(Modifier.fillMaxWidth().height(statusInset)
            .background(Color(appearance.paper.statusBackground.toInt())))
        Box(Modifier.weight(1f).fillMaxWidth(), content = content)
    }
}
