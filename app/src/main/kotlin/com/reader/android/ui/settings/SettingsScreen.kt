package com.reader.android.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.reader.android.BuildConfig
import com.reader.android.data.storage.ThemePreferences
import com.reader.android.ui.components.ReaderAppTopBar
import com.reader.android.ui.components.ReaderSettingsGroup
import com.reader.android.ui.components.ReaderSettingsRow
import com.reader.android.ui.components.ReaderSettingsSwitchRow
import com.reader.android.ui.theme.ReaderTheme
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    onPrototypeGalleryClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val prefs = ThemePreferences(context)
    val scope = rememberCoroutineScope()

    val darkMode by prefs.darkMode.collectAsState(initial = false)
    val fontSize by prefs.fontSize.collectAsState(initial = ThemePreferences.FONT_SIZE_DEFAULT)
    val lineSpacing by prefs.lineSpacing.collectAsState(initial = ThemePreferences.LINE_SPACING_DEFAULT)
    val pageMargin by prefs.pageMargin.collectAsState(initial = ThemePreferences.PAGE_MARGIN_DEFAULT)

    ReaderTheme {
        Column(modifier = Modifier.fillMaxSize()) {
            ReaderAppTopBar(title = "设置")

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(ReaderTheme.spacing.screenPadding)
            ) {
                ReaderSettingsGroup(title = "阅读设置") {
                    ReaderSettingsSwitchRow(
                        title = "夜间模式",
                        checked = darkMode,
                        onCheckedChange = { scope.launch { prefs.setDarkMode(it) } }
                    )

                    // Font size
                    Text(
                        "字号: ${fontSize.toInt()}sp",
                        style = ReaderTheme.typography.bookMeta,
                        color = ReaderTheme.colors.controlInk
                    )
                    Slider(
                        value = fontSize,
                        onValueChange = { scope.launch { prefs.setFontSize(it) } },
                        valueRange = 12f..32f,
                        steps = 0,
                        colors = SliderDefaults.colors(
                            thumbColor = ReaderTheme.colors.primary,
                            activeTrackColor = ReaderTheme.colors.primary,
                            inactiveTrackColor = ReaderTheme.colors.mutedTrack
                        )
                    )
                    Spacer(modifier = Modifier.height(ReaderTheme.spacing.xs))

                    // Line spacing
                    Text(
                        "行间距: ${String.format("%.1f", lineSpacing)}x",
                        style = ReaderTheme.typography.bookMeta,
                        color = ReaderTheme.colors.controlInk
                    )
                    Slider(
                        value = lineSpacing,
                        onValueChange = { scope.launch { prefs.setLineSpacing(it) } },
                        valueRange = 1.0f..3.0f,
                        steps = 0,
                        colors = SliderDefaults.colors(
                            thumbColor = ReaderTheme.colors.primary,
                            activeTrackColor = ReaderTheme.colors.primary,
                            inactiveTrackColor = ReaderTheme.colors.mutedTrack
                        )
                    )
                    Spacer(modifier = Modifier.height(ReaderTheme.spacing.xs))

                    // Page margin
                    Text(
                        "页边距: ${pageMargin.toInt()}dp",
                        style = ReaderTheme.typography.bookMeta,
                        color = ReaderTheme.colors.controlInk
                    )
                    Slider(
                        value = pageMargin,
                        onValueChange = { scope.launch { prefs.setPageMargin(it) } },
                        valueRange = 8f..48f,
                        steps = 0,
                        colors = SliderDefaults.colors(
                            thumbColor = ReaderTheme.colors.primary,
                            activeTrackColor = ReaderTheme.colors.primary,
                            inactiveTrackColor = ReaderTheme.colors.mutedTrack
                        )
                    )
                }

                if (BuildConfig.DEBUG && onPrototypeGalleryClick != null) {
                    ReaderSettingsGroup(title = "调试") {
                        ReaderSettingsRow(
                            title = "UI 原型预览",
                            subtitle = "打开 Reader UI Prototype Gallery",
                            onClick = onPrototypeGalleryClick
                        )
                    }
                }
            }
        }
    }
}
