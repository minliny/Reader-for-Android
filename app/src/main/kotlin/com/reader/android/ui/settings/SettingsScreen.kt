package com.reader.android.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.reader.android.data.storage.ThemePreferences
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val prefs = ThemePreferences(context)
    val scope = rememberCoroutineScope()

    val darkMode by prefs.darkMode.collectAsState(initial = false)
    val fontSize by prefs.fontSize.collectAsState(initial = ThemePreferences.FONT_SIZE_DEFAULT)
    val lineSpacing by prefs.lineSpacing.collectAsState(initial = ThemePreferences.LINE_SPACING_DEFAULT)
    val pageMargin by prefs.pageMargin.collectAsState(initial = ThemePreferences.PAGE_MARGIN_DEFAULT)

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("设置") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text("阅读设置", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(12.dp))

            // Dark mode
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("夜间模式", modifier = Modifier.weight(1f))
                Switch(
                    checked = darkMode,
                    onCheckedChange = { scope.launch { prefs.setDarkMode(it) } }
                )
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Font size
            Text("字号: ${fontSize.toInt()}sp")
            Slider(
                value = fontSize,
                onValueChange = { scope.launch { prefs.setFontSize(it) } },
                valueRange = 12f..32f,
                steps = 0
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Line spacing
            Text("行间距: ${String.format("%.1f", lineSpacing)}x")
            Slider(
                value = lineSpacing,
                onValueChange = { scope.launch { prefs.setLineSpacing(it) } },
                valueRange = 1.0f..3.0f,
                steps = 0
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Page margin
            Text("页边距: ${pageMargin.toInt()}dp")
            Slider(
                value = pageMargin,
                onValueChange = { scope.launch { prefs.setPageMargin(it) } },
                valueRange = 8f..48f,
                steps = 0
            )
        }
    }
}
