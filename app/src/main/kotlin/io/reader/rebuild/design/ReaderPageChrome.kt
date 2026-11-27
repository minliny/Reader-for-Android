package io.reader.rebuild.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.reader.core.theme.ReaderDesignMetrics
import com.reader.core.theme.ReaderIconMapping
import com.reader.core.theme.ReaderIconRegistry

@Composable
internal fun ReaderTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
) {
    val colors = LocalReaderColors.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).background(colors.background)
            .padding(end = ReaderDesignMetrics.spaceSmall.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) ReaderAction(ReaderIconRegistry.back, "返回", onBack)
        else Spacer(Modifier.width(ReaderDesignMetrics.screenInset.dp))
        Box(Modifier.weight(1f).padding(vertical = ReaderDesignMetrics.spaceExtraSmall.dp)) {
            Text(title, color = colors.primaryText, maxLines = 2, overflow = TextOverflow.Ellipsis,
                style = readerTextStyle(if (onBack == null) ReaderDesignMetrics.appTitle else ReaderDesignMetrics.pageTitle))
        }
        actions()
    }
}

@Composable
internal fun ReaderAction(icon: ReaderIconMapping, label: String, onClick: () -> Unit, enabled: Boolean = true) {
    IconButton(onClick, enabled = enabled) {
        ReaderIcon(icon, label)
    }
}
