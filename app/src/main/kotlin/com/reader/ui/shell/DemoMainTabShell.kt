package com.reader.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.reader.android.R
import com.reader.ui.demo.DemoIcon
import com.reader.ui.motion.ViewportClass
import com.reader.ui.theme.ReaderShapes
import com.reader.ui.theme.ReaderTextStyles
import com.reader.ui.theme.readerExtraColors
import com.reader.ui.tokens.ReaderSizeToken
import com.reader.ui.tokens.ReaderSpacingToken
import com.reader.ui.tokens.ReaderTokenAdapter
import com.reader.ui.tokens.ReaderZIndexToken

/**
 * MainTabShell — mirrors `renderMainTabShell` in `shared-shell-kit/kit.js`.
 *
 * Fixed slot order: statusBar + appTopBar + contentRegion + stateHost + mainNav.
 *
 * The system status bar is honored through `WindowInsets.statusBars`; the floating
 * 4-tab bottom nav is layered above content with z-index [ReaderZIndexToken.MAIN_NAV]
 * and sized via [ReaderSizeToken.MAIN_NAV_HEIGHT]. Horizontal content padding consumes
 * [ReaderSpacingToken.SCREEN_PADDING] so the inset stays aligned with the contract.
 *
 * 自适应：TABLET_EXPANDED / EXPANDED_WIDTH 时内容区 maxWidth 约束为 720.dp（居中），
 * 避免宽屏拉伸过宽；其他 viewport class 保持现有行为。
 */
@Composable
fun DemoMainTabShell(
    title: String,
    activeRoute: String,
    onNavigate: (String) -> Unit,
    viewportClass: ViewportClass = ViewportClass.PORTRAIT,
    content: LazyListScope.() -> Unit
) {
    val screenPadding = ReaderTokenAdapter.spacing(ReaderSpacingToken.SCREEN_PADDING)
    val navHeight = ReaderTokenAdapter.size(ReaderSizeToken.MAIN_NAV_HEIGHT)
    val navZIndex = ReaderTokenAdapter.zIndex(ReaderZIndexToken.MAIN_NAV)
    val isWideScreen = viewportClass == ViewportClass.TABLET_EXPANDED ||
        viewportClass == ViewportClass.EXPANDED_WIDTH
    val contentMaxWidth = if (isWideScreen) 720.dp else Dp.Unspecified
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            Modifier
                .widthIn(max = contentMaxWidth)
                .fillMaxSize()
        ) {
            DemoTopBar(title = title)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = screenPadding,
                    end = screenPadding,
                    top = ReaderTokenAdapter.spacing(ReaderSpacingToken.XS),
                    // Reserve room for the floating main nav (height + clearance).
                    bottom = navHeight + ReaderTokenAdapter.spacing(ReaderSpacingToken.LG)
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = content
            )
        }
        DemoBottomNav(
            activeRoute = activeRoute,
            onNavigate = onNavigate,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .zIndex(navZIndex)
        )
    }
}

/**
 * Floating 4-tab bottom navigation (书架 / 发现 / RSS / 设置).
 *
 * Extracted from `DemoRouteScreen` and made public so that MainTabShell and any future
 * host can reuse the exact same nav chrome. The nav container height honors
 * [ReaderSizeToken.MAIN_NAV_HEIGHT]; the floating layer sits at z-index
 * [ReaderZIndexToken.MAIN_NAV] per the kit.js `mainNav` helper.
 */
@Composable
fun DemoBottomNav(
    activeRoute: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val items = listOf(
        Triple("书架", "bookshelf", R.drawable.reader_ic_bookshelf),
        Triple("发现", "discover", R.drawable.reader_ic_discover),
        Triple("RSS", "rss", R.drawable.reader_ic_rss),
        Triple("设置", "settings", R.drawable.reader_ic_gear)
    )
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Row(
        modifier = modifier
            .padding(horizontal = 14.dp, vertical = 14.dp)
            .fillMaxWidth()
            .defaultMinSize(minHeight = ReaderTokenAdapter.size(ReaderSizeToken.MAIN_NAV_HEIGHT))
            .background(extra.navBackground, ReaderShapes.xl)
            .border(1.dp, colors.outline, ReaderShapes.xl)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items.forEach { (label, route, icon) ->
            val active = activeRoute == route
            Column(
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 56.dp)
                    .background(if (active) extra.primaryDark else Color.Transparent, ReaderShapes.xl)
                    .clickable { onNavigate(route) }
                    .padding(vertical = 5.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                DemoIcon(icon, tint = if (active) colors.onPrimary else extra.navInactive)
                Text(label, style = ReaderTextStyles.tabLabel, color = if (active) colors.onPrimary else extra.navInactive)
            }
        }
    }
}
