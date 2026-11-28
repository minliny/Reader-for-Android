package io.reader.rebuild.features.shell

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import com.reader.core.theme.ReaderDesignMetrics
import com.reader.core.theme.ReaderIconRegistry
import io.reader.rebuild.design.LocalReaderColors
import io.reader.rebuild.design.ReaderIcon
import io.reader.rebuild.design.readerTextStyle

/** The two top-level destinations. Secondary tools stay inside their owner page. */
@Composable
internal fun ReaderBottomNavigation(
    selected: MainDestination,
    wide: Boolean,
    onSelect: (MainDestination) -> Unit,
    onSelectedTap: (MainDestination) -> Unit,
) {
    val colors = LocalReaderColors.current
    val items = listOf(
        MainDestination.Shelf to (ReaderIconRegistry.shelf to "书架"),
        MainDestination.Settings to (ReaderIconRegistry.settings to "设置"),
    )
    val content: @Composable () -> Unit = {
        items.forEach { (destination, iconAndLabel) ->
            val selectedItem = selected == destination
            val (icon, label) = iconAndLabel
            Column(
                Modifier
                    .padding(4.dp)
                .then(Modifier.width(if (wide) 66.dp else 78.dp))
                    .background(
                        if (selectedItem) colors.accentFill else androidx.compose.ui.graphics.Color.Transparent,
                        RoundedCornerShape(ReaderDesignMetrics.pillRadius.dp),
                    )
                    .padding(horizontal = 8.dp, vertical = 7.dp)
                    .size(width = if (wide) 50.dp else 78.dp, height = 50.dp)
                    .then(androidx.compose.ui.Modifier.clickableWithoutRipple {
                        if (selectedItem) onSelectedTap(destination) else onSelect(destination)
                    }),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                ReaderIcon(icon, label, tint = if (selectedItem) colors.onAccent else colors.secondaryText)
                Text(label, color = if (selectedItem) colors.onAccent else colors.secondaryText,
                    style = readerTextStyle(ReaderDesignMetrics.mainTabLabel), maxLines = 1)
            }
        }
    }
    if (wide) {
        Surface(
            Modifier
                .width(82.dp)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(start = 12.dp, top = 12.dp, bottom = 12.dp)
                .shadow(6.dp, RoundedCornerShape(ReaderDesignMetrics.pillRadius.dp)),
            shape = RoundedCornerShape(ReaderDesignMetrics.pillRadius.dp),
            color = colors.elevated,
            border = BorderStroke(1.dp, colors.outline),
        ) {
            Column(Modifier.padding(7.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
        }
    } else {
        Surface(
            Modifier
                .navigationBarsPadding()
                .padding(horizontal = 32.dp, vertical = 15.dp)
                .shadow(6.dp, RoundedCornerShape(ReaderDesignMetrics.pillRadius.dp)),
            shape = RoundedCornerShape(ReaderDesignMetrics.pillRadius.dp),
            color = colors.elevated,
            border = BorderStroke(1.dp, colors.outline),
        ) {
            Row(Modifier.padding(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) { content() }
        }
    }
}

internal enum class MainDestination { Shelf, Settings }

private fun Modifier.clickableWithoutRipple(onClick: () -> Unit): Modifier =
    clickable(onClick = onClick)
