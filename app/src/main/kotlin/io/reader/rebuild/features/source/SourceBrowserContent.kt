package io.reader.rebuild.features.source

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.reader.core.theme.ReaderDesignMetrics as Metrics
import com.reader.core.theme.ReaderIconRegistry as Icons
import com.reader.core.theme.ReaderIconMapping
import io.reader.rebuild.design.LocalReaderColors
import io.reader.rebuild.design.ReaderIcon
import io.reader.rebuild.design.readerTextStyle

internal data class SourceBrowserState(
    val address: String,
    val secure: Boolean,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val loading: Boolean = false,
    val progress: Float = 0f,
    val canRefresh: Boolean = true,
)

internal enum class SourceBrowserAction { BACK, FORWARD, REFRESH, DONE }

@Composable
internal fun SourceBrowserContent(
    title: String,
    state: SourceBrowserState,
    onAction: (SourceBrowserAction) -> Unit = {},
    content: @Composable () -> Unit,
) {
    val colors = LocalReaderColors.current
    Column(Modifier.fillMaxSize().background(colors.background).testTag("source.browser")) {
        Surface(color = colors.surface) {
            Column(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = Metrics.sourceLoginToolbarMinHeight.dp)
                        .padding(horizontal = Metrics.screenInset.dp, vertical = Metrics.spaceExtraSmall.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Metrics.rowBlockSpacing.dp)) {
                        Text(title, color = colors.primaryText, style = readerTextStyle(Metrics.sourceLoginTitle),
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Row(
                            Modifier.fillMaxWidth().background(colors.field, RoundedCornerShape(Metrics.pillRadius.dp))
                                .padding(horizontal = Metrics.spaceExtraSmall.dp, vertical = Metrics.rowBlockSpacing.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ReaderIcon(
                                if (state.secure) Icons.sourceLoginSecure else Icons.sourceLoginUnknown,
                                if (state.secure) "安全连接" else "连接未加密",
                                size = Metrics.sourceLoginLockSize.dp,
                            )
                            Text(
                                state.address, Modifier.padding(start = Metrics.rowBlockSpacing.dp).testTag("source.browser.address"),
                                style = readerTextStyle(Metrics.sourceLoginAddress),
                                color = colors.secondaryText, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().height(Metrics.sourceLoginProgressHeight.dp)) {
                    if (state.loading) LinearProgressIndicator(
                        progress = { state.progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxSize(), color = colors.accentFill, trackColor = colors.progressTrack,
                    )
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) { content() }
        Surface(color = colors.surface) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = Metrics.sourceLoginToolbarMinHeight.dp)
                    .padding(horizontal = Metrics.spaceExtraSmall.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BrowserButton(Icons.previousChapter, "后退", state.canGoBack) { onAction(SourceBrowserAction.BACK) }
                BrowserButton(Icons.nextChapter, "前进", state.canGoForward) { onAction(SourceBrowserAction.FORWARD) }
                BrowserButton(Icons.refresh, "刷新", !state.loading && state.canRefresh) { onAction(SourceBrowserAction.REFRESH) }
                Box(Modifier.weight(1f))
                SourceTextAction("完成", { onAction(SourceBrowserAction.DONE) })
            }
        }
    }
}

@Composable
private fun BrowserButton(icon: ReaderIconMapping, label: String, enabled: Boolean, action: () -> Unit) {
    val colors = LocalReaderColors.current
    IconButton(onClick = action, enabled = enabled, modifier = Modifier.testTag("source.browser.$label")) {
        ReaderIcon(icon, label, size = Metrics.sourceLoginActionIconSize.dp,
            tint = if (enabled) colors.accent else colors.mutedText)
    }
}
