package io.reader.rebuild.features.source

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.reader.core.theme.ReaderDesignMetrics as Metrics
import io.reader.rebuild.design.LocalReaderColors
import io.reader.rebuild.design.ReaderTopBar
import io.reader.rebuild.design.readerTextStyle
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

internal fun JsonObject.sourceText(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

@Composable
internal fun SourcePage(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    val colors = LocalReaderColors.current
    Column(Modifier.fillMaxSize().background(colors.background)) {
        ReaderTopBar(title, onBack)
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) { content() }
    }
}

@Composable
internal fun SourceCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = LocalReaderColors.current
    Surface(
        modifier.fillMaxWidth(),
        color = colors.surface,
        shape = RoundedCornerShape(Metrics.cardRadius.dp),
    ) {
        Column(
            Modifier.padding(Metrics.cardPadding.dp),
            verticalArrangement = Arrangement.spacedBy(Metrics.rowBlockSpacing.dp),
        ) { content() }
    }
}

@Composable
internal fun SourceStatusBadge(label: String, color: Color, modifier: Modifier = Modifier) {
    val colors = LocalReaderColors.current
    Box(
        modifier.background(color.copy(alpha = 0.12f).compositeOver(colors.surface),
            RoundedCornerShape(Metrics.sourceTransferBadgeRadius.dp))
            .padding(
                horizontal = Metrics.sourceTransferBadgeInlinePadding.dp,
                vertical = Metrics.sourceTransferBadgeBlockPadding.dp,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = readerTextStyle(Metrics.sourceTransferBadgeText), color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun SourceTextAction(label: String, onClick: () -> Unit, enabled: Boolean = true) {
    TextButton(onClick = onClick, enabled = enabled) {
        Text(label, style = readerTextStyle(Metrics.sourceFilterFieldText), maxLines = 1)
    }
}

internal fun Modifier.sourcePageWidth(): Modifier = widthIn(max = 650.dp).fillMaxWidth()

@Composable
internal fun SourceConfirmationVisibility(visible: Boolean, onVisibility: (Boolean) -> Unit) {
    val notify = rememberUpdatedState(onVisibility)
    DisposableEffect(visible) {
        notify.value(visible)
        onDispose { if (visible) notify.value(false) }
    }
}
