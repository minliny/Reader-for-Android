package com.reader.android.ui.reader.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.reader.android.ui.theme.ReaderTheme
import com.reader.android.ui.theme.ReaderVisualMode
import kotlinx.coroutines.delay

@Composable
fun ReaderNightState(
    isNight: Boolean,
    modifier: Modifier = Modifier,
    showToast: Boolean = true,
    content: @Composable () -> Unit
) {
    val visualMode = if (isNight) ReaderVisualMode.Night else ReaderVisualMode.Day

    ReaderTheme(visualMode = visualMode) {
        Box(modifier = modifier.fillMaxSize()) {
            content()

            // Brief toast on mode change
            if (showToast) {
                var visible by remember(isNight) { mutableStateOf(true) }
                LaunchedEffect(isNight) {
                    visible = true
                    delay(2000)
                    visible = false
                }
                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = ReaderTheme.spacing.bottomBarHeight + ReaderTheme.spacing.quickCircleSize + 100.dp)
                ) {
                    val toastText = if (isNight) "已切换夜间模式" else "已切换日间模式"
                    Text(
                        text = toastText,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(ReaderTheme.colors.floatingControlBgAlt)
                            .border(1.dp, ReaderTheme.colors.controlBorder, RoundedCornerShape(999.dp))
                            .semantics { contentDescription = toastText }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        color = ReaderTheme.colors.controlInk,
                        style = ReaderTheme.typography.readerControlLabel
                    )
                }
            }
        }
    }
}
