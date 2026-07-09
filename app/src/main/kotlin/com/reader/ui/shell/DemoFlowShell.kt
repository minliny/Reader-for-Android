package com.reader.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import com.reader.ui.tokens.ReaderSpacingToken
import com.reader.ui.tokens.ReaderTokenAdapter
import com.reader.ui.tokens.ReaderZIndexToken

/**
 * FlowShell — mirrors `renderFlowShell` in `shared-shell-kit/kit.js`.
 *
 * Fixed slot order: stepRegion + comparisonRegion + resultRegion + stateHost.
 *
 * On desktop the flow frame is a wide horizontal window (kit.js `rsk-flow-frame`,
 * `--fd-ds-size-flow-width` 1284dp). On mobile the regions stack vertically inside a
 * full-screen frame so each step stays legible. The frame carries z-index
 * [ReaderZIndexToken.FLOW_WINDOW] so it can float above other surfaces when hosted.
 */
@Composable
fun DemoFlowShell(
    title: String,
    onBack: () -> Unit,
    stepContent: @Composable () -> Unit,
    comparisonContent: (@Composable () -> Unit)? = null,
    resultContent: (@Composable () -> Unit)? = null,
    stateContent: (@Composable () -> Unit)? = null
) {
    val screenPadding = ReaderTokenAdapter.spacing(ReaderSpacingToken.SCREEN_PADDING)
    val flowZ = ReaderTokenAdapter.zIndex(ReaderZIndexToken.FLOW_WINDOW)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .zIndex(flowZ)
    ) {
        Column(Modifier.fillMaxSize()) {
            DemoBackBar(title = title, onBack = onBack)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(screenPadding),
                verticalArrangement = Arrangement.spacedBy(ReaderTokenAdapter.spacing(ReaderSpacingToken.MD))
            ) {
                // stepRegion
                Box(Modifier.fillMaxWidth()) { stepContent() }
                // comparisonRegion
                if (comparisonContent != null) {
                    Box(Modifier.fillMaxWidth()) { comparisonContent() }
                }
                // resultRegion
                if (resultContent != null) {
                    Box(Modifier.fillMaxWidth()) { resultContent() }
                }
            }
        }
        // stateHost — top-aligned transient state (progress / error banners).
        if (stateContent != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(
                        horizontal = screenPadding,
                        vertical = ReaderTokenAdapter.spacing(ReaderSpacingToken.XS)
                    )
                    .zIndex(ReaderTokenAdapter.zIndex(ReaderZIndexToken.OVERLAY))
            ) {
                stateContent()
            }
        }
    }
}
