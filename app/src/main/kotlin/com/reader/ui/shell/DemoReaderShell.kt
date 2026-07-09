package com.reader.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.reader.ui.motion.ViewportClass
import com.reader.ui.tokens.ReaderSizeToken
import com.reader.ui.tokens.ReaderSpacingToken
import com.reader.ui.tokens.ReaderTokenAdapter
import com.reader.ui.tokens.ReaderZIndexToken

/**
 * ReaderShell — mirrors `renderReaderShell` in `shared-shell-kit/kit.js`.
 *
 * Fixed slot order: readingSurface + readerOverlayHost (wraps bottomSheetHost +
 * readerModuleNav) + readerStateHost.
 *
 * Unlike the phone shells, the reader frame is fully immersive: [readingContent] owns
 * the whole surface (no status-bar inset padding by default, mirroring the kit.js
 * `rsk-reading-surface` article). Overlay layers stack above it with increasing
 * z-index so that the module nav always sits above the bottom sheet, which sits
 * above generic overlays, which sits above the reading surface.
 *
 * z-index ladder (sourced from [ReaderTokenAdapter.zIndex]):
 *  - CONTENT          reading surface
 *  - OVERLAY          generic reader overlay (info layer, gesture hints, …)
 *  - BOTTOM_SHEET     bottom sheet host
 *  - READER_MODULE_NAV reader module nav
 *
 * 自适应：COMPACT_LANDSCAPE 时阅读正文区加水平 padding 减少 stretch（基本保持全屏）。
 */
@Composable
fun DemoReaderShell(
    readingContent: @Composable () -> Unit,
    overlayContent: (@Composable () -> Unit)? = null,
    bottomSheetContent: (@Composable () -> Unit)? = null,
    moduleNavContent: (@Composable () -> Unit)? = null,
    stateContent: (@Composable () -> Unit)? = null,
    viewportClass: ViewportClass = ViewportClass.PORTRAIT
) {
    val contentZ = ReaderTokenAdapter.zIndex(ReaderZIndexToken.CONTENT)
    val overlayZ = ReaderTokenAdapter.zIndex(ReaderZIndexToken.OVERLAY)
    val sheetZ = ReaderTokenAdapter.zIndex(ReaderZIndexToken.BOTTOM_SHEET)
    val moduleNavZ = ReaderTokenAdapter.zIndex(ReaderZIndexToken.READER_MODULE_NAV)
    // COMPACT_LANDSCAPE（手机横屏）时给正文加水平 padding，减少过宽拉伸
    val isCompactLandscape = viewportClass == ViewportClass.COMPACT_LANDSCAPE
    val readingHorizontalPadding = if (isCompactLandscape) 24.dp else 0.dp
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // readingSurface — full-bleed article. zIndex CONTENT keeps it beneath overlays.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = readingHorizontalPadding)
                .zIndex(contentZ)
        ) {
            readingContent()
        }

        // readerOverlayHost — floats above the surface (info bar, gesture hints, …).
        if (overlayContent != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(overlayZ)
            ) {
                overlayContent()
            }
        }

        // bottomSheetHost — anchored to the bottom, above generic overlays.
        if (bottomSheetContent != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = ReaderTokenAdapter.size(ReaderSizeToken.READER_BOTTOM_SHEET_MIN_HEIGHT))
                    .zIndex(sheetZ)
            ) {
                bottomSheetContent()
            }
        }

        // readerModuleNav — module navigation dock, highest in-reader layer.
        if (moduleNavContent != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = ReaderTokenAdapter.size(ReaderSizeToken.READER_MODULE_NAV_HEIGHT))
                    .zIndex(moduleNavZ)
            ) {
                moduleNavContent()
            }
        }

        // readerStateHost — top-aligned state slot (loading / error / empty banners).
        if (stateContent != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(
                        horizontal = ReaderTokenAdapter.spacing(ReaderSpacingToken.SCREEN_PADDING),
                        vertical = ReaderTokenAdapter.spacing(ReaderSpacingToken.XS)
                    )
                    .zIndex(overlayZ)
            ) {
                stateContent()
            }
        }
    }
}

/**
 * Convenience wrapper that places a [Column] of overlay rows at the top of the reader
 * frame. Mirrors the kit.js `readerOverlayHost` default content (info layer + progress).
 */
@Composable
fun DemoReaderOverlayHost(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = ReaderTokenAdapter.spacing(ReaderSpacingToken.SCREEN_PADDING),
                vertical = ReaderTokenAdapter.spacing(ReaderSpacingToken.XS)
            ),
        verticalArrangement = Arrangement.spacedBy(ReaderTokenAdapter.spacing(ReaderSpacingToken.XS)),
        content = { content() }
    )
}
