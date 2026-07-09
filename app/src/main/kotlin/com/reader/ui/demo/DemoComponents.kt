package com.reader.ui.demo

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.reader.android.R
import com.reader.ui.shell.DemoBottomActions
import com.reader.ui.theme.ReaderTextStyles
import com.reader.ui.theme.readerExtraColors
import com.reader.ui.tokens.ReaderColorToken
import com.reader.ui.tokens.ReaderRadiusToken
import com.reader.ui.tokens.ReaderSpacingToken
import com.reader.ui.tokens.ReaderTokenAdapter
import com.reader.ui.tokens.ReaderTypeToken
import com.reader.ui.tokens.ReaderZIndexToken

// ═══════════════════════════════════════════════════════════════════════════════
// Data classes
// ═══════════════════════════════════════════════════════════════════════════════

// Note: DemoBookInfo is the public component-library book model.
// DemoRouteScreen.kt has its own private DemoBook with a different signature;
// Phase 4.4 will unify them. Until then DemoBookInfo avoids a redeclaration conflict.
data class DemoBookInfo(
    val title: String,
    val author: String,
    val category: String,
    val status: String,
    val latest: String,
    val progress: Float = 0f,
    val coverLabel: String = title.take(1)
)

data class DemoChip(
    val label: String,
    val routeId: String? = null,
    val active: Boolean = false
)

data class DemoModuleNavItem(
    val label: String,
    @DrawableRes val iconRes: Int
)

data class DemoFlowStep(
    val label: String,
    val completed: Boolean = false
)

// ═══════════════════════════════════════════════════════════════════════════════
// A. Basic display components
// ═══════════════════════════════════════════════════════════════════════════════

/** Section card container — mirrors `.fd-section-card`. */
@Composable
fun DemoSectionCard(
    title: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
                RoundedCornerShape(ReaderTokenAdapter.radius(ReaderRadiusToken.CARD))
            )
            .border(1.dp, readerExtraColors().hairline, RoundedCornerShape(ReaderTokenAdapter.radius(ReaderRadiusToken.CARD)))
            .padding(ReaderTokenAdapter.spacing(ReaderSpacingToken.CARD_PADDING)),
        verticalArrangement = Arrangement.spacedBy(ReaderTokenAdapter.spacing(ReaderSpacingToken.XS))
    ) {
        if (title != null) {
            Text(title, style = ReaderTextStyles.sectionTitle, color = MaterialTheme.colorScheme.onBackground)
        }
        content()
    }
}

/** Empty state — mirrors `.fd-empty-state`. */
@Composable
fun DemoEmptyState(
    @DrawableRes iconRes: Int,
    title: String,
    body: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(ReaderTokenAdapter.spacing(ReaderSpacingToken.LG)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ReaderTokenAdapter.spacing(ReaderSpacingToken.SM))
    ) {
        DemoIcon(iconRes, size = 42.dp, tint = MaterialTheme.colorScheme.primary)
        Text(title, style = ReaderTextStyles.emptyHeading, color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center)
        if (body != null) {
            Text(body, style = ReaderTextStyles.emptyBody, color = readerExtraColors().muted, textAlign = TextAlign.Center)
        }
        if (actionLabel != null && onAction != null) {
            DemoButton(label = actionLabel, primary = true, onClick = onAction)
        }
    }
}

/** Error state — mirrors `.fd-error-state`. */
@Composable
fun DemoErrorState(
    title: String,
    body: String? = null,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(ReaderTokenAdapter.spacing(ReaderSpacingToken.LG)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ReaderTokenAdapter.spacing(ReaderSpacingToken.SM))
    ) {
        val warnColor = ReaderTokenAdapter.color(ReaderColorToken.STATUS_WARN)
        DemoIcon(R.drawable.reader_ic_warning, size = 42.dp, tint = warnColor)
        Text(title, style = ReaderTextStyles.emptyHeading, color = warnColor, textAlign = TextAlign.Center)
        if (body != null) {
            Text(body, style = ReaderTextStyles.emptyBody, color = readerExtraColors().muted, textAlign = TextAlign.Center)
        }
        if (onRetry != null) {
            DemoButton(label = "重试", primary = true, onClick = onRetry)
        }
    }
}

/** Loading skeleton — mirrors `.fd-skeleton`. */
@Composable
fun DemoLoadingSkeleton(
    lines: Int = 3,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(ReaderTokenAdapter.spacing(ReaderSpacingToken.CARD_PADDING)),
        verticalArrangement = Arrangement.spacedBy(ReaderTokenAdapter.spacing(ReaderSpacingToken.XS))
    ) {
        repeat(lines) { index ->
            val widthFraction = when (index % 3) { 0 -> 0.72f; 1 -> 0.50f; else -> 0.88f }
            Box(
                Modifier
                    .fillMaxWidth(widthFraction)
                    .height(if (index == 0) 14.dp else 11.dp)
                    .background(readerExtraColors().hairline, RoundedCornerShape(ReaderTokenAdapter.radius(ReaderRadiusToken.CONTROL)))
            )
        }
    }
}

/** Generic status card — variant of DemoStateCard with modifier param. */
@Composable
fun DemoStateBlock(
    @DrawableRes iconRes: Int,
    title: String,
    body: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
                RoundedCornerShape(ReaderTokenAdapter.radius(ReaderRadiusToken.CARD))
            )
            .border(1.dp, readerExtraColors().hairline, RoundedCornerShape(ReaderTokenAdapter.radius(ReaderRadiusToken.CARD)))
            .padding(ReaderTokenAdapter.spacing(ReaderSpacingToken.CARD_PADDING)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ReaderTokenAdapter.spacing(ReaderSpacingToken.SM))
    ) {
        DemoIcon(iconRes, size = 42.dp, tint = MaterialTheme.colorScheme.primary)
        Text(title, style = ReaderTextStyles.emptyHeading, color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center)
        Text(body, style = ReaderTextStyles.emptyBody, color = readerExtraColors().muted, textAlign = TextAlign.Center)
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// B. Book components
// ═══════════════════════════════════════════════════════════════════════════════

/** Bookshelf list item — mirrors `.fd-book-card`. */
@Composable
fun DemoBookCard(
    book: DemoBookInfo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(ReaderTokenAdapter.spacing(ReaderSpacingToken.CARD_PADDING)),
        horizontalArrangement = Arrangement.spacedBy(ReaderTokenAdapter.spacing(ReaderSpacingToken.SM)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DemoBookCover(book.coverLabel)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(book.title, style = ReaderTextStyles.bookTitle, color = MaterialTheme.colorScheme.onBackground, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("${book.author} · ${book.category} · ${book.status}", style = ReaderTextStyles.bookAuthor, color = readerExtraColors().muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("最新：${book.latest}", style = ReaderTextStyles.bookAuthor, color = readerExtraColors().muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (book.progress > 0f) {
                DemoReaderProgressBar(progress = book.progress, chapter = "", modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

/** Bookshelf grid — mirrors `.fd-book-grid`. */
@Composable
fun DemoBookGrid(
    books: List<DemoBookInfo>,
    onBookClick: (String) -> Unit,
    columns: Int = 3,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ReaderTokenAdapter.spacing(ReaderSpacingToken.SM))) {
        books.chunked(columns).forEach { rowBooks ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ReaderTokenAdapter.spacing(ReaderSpacingToken.SM))) {
                rowBooks.forEach { book ->
                    Column(
                        modifier = Modifier.weight(1f).clickable { onBookClick(book.title) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(ReaderTokenAdapter.spacing(ReaderSpacingToken.XS))
                    ) {
                        DemoBookCover(book.coverLabel)
                        Text(book.title, style = ReaderTextStyles.bookTitle, color = MaterialTheme.colorScheme.onBackground, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                    }
                }
                repeat(columns - rowBooks.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Single book cover — mirrors `.fd-book-cover`. */
@Composable
fun DemoBookCover(
    label: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(60.dp)
            .aspectRatio(2f / 3f)
            .background(
                ReaderTokenAdapter.color(ReaderColorToken.PRIMARY).copy(alpha = 0.16f),
                RoundedCornerShape(ReaderTokenAdapter.radius(ReaderRadiusToken.MEDIUM))
            )
            .border(1.dp, readerExtraColors().hairline, RoundedCornerShape(ReaderTokenAdapter.radius(ReaderRadiusToken.MEDIUM)))
            .padding(6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label.take(4),
            style = TextStyle(fontFamily = FontFamily.Serif, fontSize = 15.sp, fontWeight = FontWeight(700)),
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )
    }
}

/** Continue reading card — mirrors `.fd-continue-card`. */
@Composable
fun DemoContinueReadingCard(
    title: String,
    chapter: String,
    progress: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(
                MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
                RoundedCornerShape(ReaderTokenAdapter.radius(ReaderRadiusToken.CARD))
            )
            .border(1.dp, readerExtraColors().hairline, RoundedCornerShape(ReaderTokenAdapter.radius(ReaderRadiusToken.CARD)))
            .padding(ReaderTokenAdapter.spacing(ReaderSpacingToken.CARD_PADDING)),
        verticalArrangement = Arrangement.spacedBy(ReaderTokenAdapter.spacing(ReaderSpacingToken.XS))
    ) {
        Text("继续阅读", style = ReaderTextStyles.continueLabel, color = MaterialTheme.colorScheme.primary)
        Text(title, style = ReaderTextStyles.continueTitle, color = MaterialTheme.colorScheme.onBackground, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(chapter, style = ReaderTextStyles.bookAuthor, color = readerExtraColors().muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        DemoReaderProgressBar(progress = progress, chapter = "")
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// C. Chip / tag components
// ═══════════════════════════════════════════════════════════════════════════════

/** Horizontal scrolling chip row — mirrors `.fd-chip-row`. */
@Composable
fun DemoChipRow(
    chips: List<DemoChip>,
    onChipClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        chips.forEach { chip ->
            DemoChipItem(label = chip.label, active = chip.active) {
                onChipClick(chip.routeId ?: chip.label)
            }
        }
    }
}

/** Single chip — mirrors `.fd-chip`. Named DemoChipItem to avoid conflict with DemoChip data class. */
@Composable
fun DemoChipItem(
    label: String,
    active: Boolean = false,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Text(
        text = label,
        style = TextStyle(fontFamily = FontFamily.Default, fontSize = 11.sp, fontWeight = FontWeight(800)),
        color = if (active) colors.onPrimary else readerExtraColors().controlInk,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .defaultMinSize(minHeight = 32.dp)
            .background(
                if (active) ReaderTokenAdapter.color(ReaderColorToken.PRIMARY_DARK) else colors.surface.copy(alpha = 0.74f),
                RoundedCornerShape(ReaderTokenAdapter.radius(ReaderRadiusToken.CONTROL))
            )
            .border(1.dp, if (active) Color.Transparent else readerExtraColors().hairline, RoundedCornerShape(ReaderTokenAdapter.radius(ReaderRadiusToken.CONTROL)))
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 8.dp)
    )
}

/** Filter popover — mirrors `.fd-filter-popover`. */
@Composable
fun DemoFilterPopover(
    title: String,
    options: List<String>,
    selected: String?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.32f))
            .zIndex(ReaderTokenAdapter.zIndex(ReaderZIndexToken.OVERLAY))
            .clickable(onClick = onDismiss)
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(ReaderTokenAdapter.spacing(ReaderSpacingToken.SCREEN_PADDING))
                .background(
                    MaterialTheme.colorScheme.surface,
                    RoundedCornerShape(ReaderTokenAdapter.radius(ReaderRadiusToken.CARD))
                )
                .border(1.dp, readerExtraColors().hairline, RoundedCornerShape(ReaderTokenAdapter.radius(ReaderRadiusToken.CARD)))
                .padding(ReaderTokenAdapter.spacing(ReaderSpacingToken.CARD_PADDING)),
            verticalArrangement = Arrangement.spacedBy(ReaderTokenAdapter.spacing(ReaderSpacingToken.XS))
        ) {
            Text(title, style = ReaderTextStyles.sectionTitle, color = MaterialTheme.colorScheme.onBackground)
            options.forEach { option ->
                Row(
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 44.dp).clickable { onSelect(option) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ReaderTokenAdapter.spacing(ReaderSpacingToken.XS))
                ) {
                    Text(option, modifier = Modifier.weight(1f), style = ReaderTextStyles.bookAuthor, color = MaterialTheme.colorScheme.onBackground)
                    if (option == selected) {
                        DemoIcon(R.drawable.reader_ic_check, size = 18.dp, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// D. List item components
// ═══════════════════════════════════════════════════════════════════════════════

/** Generic list item — mirrors `.fd-list-item`. */
@Composable
fun DemoListItem(
    title: String,
    subtitle: String? = null,
    @DrawableRes leadingIcon: Int? = null,
    trailingText: String? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val clickable = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Row(
        modifier = modifier.fillMaxWidth().defaultMinSize(minHeight = 44.dp).then(clickable),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ReaderTokenAdapter.spacing(ReaderSpacingToken.SM))
    ) {
        if (leadingIcon != null) {
            DemoIcon(leadingIcon, tint = MaterialTheme.colorScheme.primary)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = TextStyle(fontFamily = FontFamily.Default, fontSize = 15.sp, fontWeight = FontWeight(800)), color = MaterialTheme.colorScheme.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = ReaderTextStyles.bookAuthor, color = readerExtraColors().muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (trailingText != null) {
            Text(trailingText, style = TextStyle(fontFamily = FontFamily.Default, fontSize = 11.sp, fontWeight = FontWeight(800)), color = MaterialTheme.colorScheme.primary)
        }
    }
}

/** Source list item — mirrors `.fd-source-item`. */
@Composable
fun DemoSourceListItem(
    name: String,
    url: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth().defaultMinSize(minHeight = 44.dp).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ReaderTokenAdapter.spacing(ReaderSpacingToken.SM))
    ) {
        DemoIcon(R.drawable.reader_ic_source_stack, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(name, style = TextStyle(fontFamily = FontFamily.Default, fontSize = 15.sp, fontWeight = FontWeight(800)), color = MaterialTheme.colorScheme.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(url, style = ReaderTextStyles.bookAuthor, color = readerExtraColors().muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(if (enabled) "启用" else "禁用", style = TextStyle(fontFamily = FontFamily.Default, fontSize = 11.sp, fontWeight = FontWeight(800)), color = if (enabled) ReaderTokenAdapter.color(ReaderColorToken.STATUS_GOOD) else readerExtraColors().muted)
    }
}

/** Settings row — mirrors `.fd-setting-row`. */
@Composable
fun DemoSettingsRow(
    title: String,
    value: String? = null,
    @DrawableRes iconRes: Int? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val clickable = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Row(
        modifier = modifier.fillMaxWidth().defaultMinSize(minHeight = 44.dp).then(clickable),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ReaderTokenAdapter.spacing(ReaderSpacingToken.SM))
    ) {
        if (iconRes != null) {
            DemoIcon(iconRes, tint = MaterialTheme.colorScheme.primary)
        }
        Text(title, modifier = Modifier.weight(1f), style = TextStyle(fontFamily = FontFamily.Default, fontSize = 14.sp, fontWeight = FontWeight(500)), color = MaterialTheme.colorScheme.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (value != null) {
            Text(value, style = ReaderTextStyles.bookAuthor, color = readerExtraColors().muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        } else if (onClick != null) {
            DemoIcon(R.drawable.reader_ic_chevron, size = 18.dp, tint = readerExtraColors().muted)
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// E. Reader components
// ═══════════════════════════════════════════════════════════════════════════════

/** Reader control panel — mirrors `.fd-reader-panel-*`. */
@Composable
fun DemoReaderControlPanel(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.32f))
            .zIndex(ReaderTokenAdapter.zIndex(ReaderZIndexToken.BOTTOM_SHEET))
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.surface,
                    RoundedCornerShape(topStart = ReaderTokenAdapter.radius(ReaderRadiusToken.BOTTOM_SHEET), topEnd = ReaderTokenAdapter.radius(ReaderRadiusToken.BOTTOM_SHEET))
                )
                .border(1.dp, readerExtraColors().hairline, RoundedCornerShape(topStart = ReaderTokenAdapter.radius(ReaderRadiusToken.BOTTOM_SHEET), topEnd = ReaderTokenAdapter.radius(ReaderRadiusToken.BOTTOM_SHEET)))
                .padding(ReaderTokenAdapter.spacing(ReaderSpacingToken.CARD_PADDING)),
            verticalArrangement = Arrangement.spacedBy(ReaderTokenAdapter.spacing(ReaderSpacingToken.SM))
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = ReaderTextStyles.sectionTitle, color = MaterialTheme.colorScheme.onBackground)
                Box(Modifier.size(36.dp).clickable(onClick = onDismiss), contentAlignment = Alignment.Center) {
                    DemoIcon(R.drawable.reader_ic_close, size = 20.dp, tint = readerExtraColors().muted)
                }
            }
            content()
        }
    }
}

/** Reader module dock — mirrors `.fd-reader-module-nav`. */
@Composable
fun DemoReaderModuleNav(
    items: List<DemoModuleNavItem>,
    activeIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(readerExtraColors().bottomBarBackground)
            .padding(vertical = ReaderTokenAdapter.spacing(ReaderSpacingToken.XS))
            .zIndex(ReaderTokenAdapter.zIndex(ReaderZIndexToken.READER_MODULE_NAV)),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEachIndexed { index, item ->
            val active = index == activeIndex
            Column(
                modifier = Modifier.clickable { onSelect(index) }.padding(horizontal = ReaderTokenAdapter.spacing(ReaderSpacingToken.XS), vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                DemoIcon(item.iconRes, size = 22.dp, tint = if (active) MaterialTheme.colorScheme.primary else readerExtraColors().muted)
                Text(item.label, style = TextStyle(fontFamily = FontFamily.Default, fontSize = 11.sp, fontWeight = FontWeight(800)), color = if (active) MaterialTheme.colorScheme.primary else readerExtraColors().muted, maxLines = 1)
            }
        }
    }
}

/** Reader progress bar. */
@Composable
fun DemoReaderProgressBar(
    progress: Float,
    chapter: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (chapter.isNotEmpty()) {
            Text(chapter, style = ReaderTextStyles.infoLayer, color = readerExtraColors().muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(readerExtraColors().hairline, RoundedCornerShape(1.dp))
        ) {
            Box(
                Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .height(2.dp)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

/** Reader text content — mirrors `.rsk-reading-surface`. */
@Composable
fun DemoReaderTextContent(
    lines: List<String>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(ReaderTokenAdapter.spacing(ReaderSpacingToken.CARD_PADDING)),
        verticalArrangement = Arrangement.spacedBy(ReaderTokenAdapter.spacing(ReaderSpacingToken.XS))
    ) {
        lines.forEach { line ->
            Text(
                text = line,
                style = TextStyle(
                    fontFamily = FontFamily.Serif,
                    fontSize = ReaderTokenAdapter.type(ReaderTypeToken.READER_BODY),
                    lineHeight = (ReaderTokenAdapter.type(ReaderTypeToken.READER_BODY).value * 1.96f).sp
                ),
                color = readerExtraColors().readerInk
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// F. Settings components
// ═══════════════════════════════════════════════════════════════════════════════

/** Settings block — mirrors `.fd-setting-block`. */
@Composable
fun DemoSettingsBlock(
    title: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ReaderTokenAdapter.spacing(ReaderSpacingToken.XS))
    ) {
        if (title != null) {
            Text(title, style = ReaderTextStyles.sectionTitle, color = MaterialTheme.colorScheme.onBackground)
        }
        content()
    }
}

/** Value strip — mirrors `.fd-value-strip`. */
@Composable
fun DemoValueStrip(
    label: String,
    value: String,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val clickable = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Row(
        modifier = modifier.fillMaxWidth().defaultMinSize(minHeight = 44.dp).then(clickable),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ReaderTokenAdapter.spacing(ReaderSpacingToken.SM))
    ) {
        Text(label, modifier = Modifier.weight(1f), style = TextStyle(fontFamily = FontFamily.Default, fontSize = 14.sp, fontWeight = FontWeight(500)), color = MaterialTheme.colorScheme.onBackground)
        Text(value, style = ReaderTextStyles.bookAuthor, color = readerExtraColors().muted)
        if (onClick != null) {
            DemoIcon(R.drawable.reader_ic_chevron, size = 18.dp, tint = readerExtraColors().muted)
        }
    }
}

/** Toggle row. */
@Composable
fun DemoToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth().defaultMinSize(minHeight = 44.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, modifier = Modifier.weight(1f), style = TextStyle(fontFamily = FontFamily.Default, fontSize = 14.sp, fontWeight = FontWeight(500)), color = MaterialTheme.colorScheme.onBackground)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** Slider row. */
@Composable
fun DemoSliderRow(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ReaderTokenAdapter.spacing(ReaderSpacingToken.XS))) {
        Text(title, style = TextStyle(fontFamily = FontFamily.Default, fontSize = 14.sp, fontWeight = FontWeight(500)), color = MaterialTheme.colorScheme.onBackground)
        Slider(value = value, onValueChange = onValueChange, valueRange = range)
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// G. State / feedback components
// ═══════════════════════════════════════════════════════════════════════════════

// Note: DemoBottomActions is imported from com.reader.ui.shell.DemoLibraryShell.
// Signature: DemoBottomActions(actions: List<DemoRouteAction>, onNavigate: (String) -> Unit, modifier: Modifier = Modifier)

/** Toast — mirrors `.fd-toast`. */
@Composable
fun DemoToast(
    message: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(
                MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                RoundedCornerShape(ReaderTokenAdapter.radius(ReaderRadiusToken.CONTROL))
            )
            .zIndex(ReaderTokenAdapter.zIndex(ReaderZIndexToken.OVERLAY))
            .padding(horizontal = ReaderTokenAdapter.spacing(ReaderSpacingToken.MD), vertical = ReaderTokenAdapter.spacing(ReaderSpacingToken.SM))
    ) {
        Text(message, style = ReaderTextStyles.continueAction, color = MaterialTheme.colorScheme.onBackground)
    }
}

/** Dialog — mirrors `.fd-dialog`. */
@Composable
fun DemoDialog(
    title: String,
    body: String,
    confirmLabel: String,
    dismissLabel: String? = null,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = ReaderTextStyles.sectionTitle, color = MaterialTheme.colorScheme.onBackground) },
        text = { Text(body, style = ReaderTextStyles.emptyBody, color = readerExtraColors().muted) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel, color = MaterialTheme.colorScheme.primary) } },
        dismissButton = if (dismissLabel != null) {
            { TextButton(onClick = onDismiss) { Text(dismissLabel, color = readerExtraColors().muted) } }
        } else null
    )
}

// ═══════════════════════════════════════════════════════════════════════════════
// H. Search components
// ═══════════════════════════════════════════════════════════════════════════════

/** Search bar — mirrors `.fd-search-bar`. */
@Composable
fun DemoSearchBar(
    placeholder: String,
    value: String,
    onValueChange: (String) -> Unit,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surface.copy(alpha = 0.62f),
                RoundedCornerShape(ReaderTokenAdapter.radius(ReaderRadiusToken.CONTROL))
            )
            .border(1.dp, readerExtraColors().hairline, RoundedCornerShape(ReaderTokenAdapter.radius(ReaderRadiusToken.CONTROL)))
            .padding(horizontal = ReaderTokenAdapter.spacing(ReaderSpacingToken.SM), vertical = ReaderTokenAdapter.spacing(ReaderSpacingToken.XS)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ReaderTokenAdapter.spacing(ReaderSpacingToken.XS))
    ) {
        if (onBack != null) {
            Box(Modifier.size(36.dp).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                DemoIcon(R.drawable.reader_ic_chevron_left, size = 20.dp, tint = readerExtraColors().muted)
            }
        }
        DemoIcon(R.drawable.reader_ic_search, size = 18.dp, tint = readerExtraColors().muted)
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(placeholder, style = ReaderTextStyles.bookAuthor, color = readerExtraColors().muted)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = TextStyle(fontFamily = FontFamily.Default, fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (value.isNotEmpty()) {
            Box(Modifier.size(20.dp).clickable { onValueChange("") }, contentAlignment = Alignment.Center) {
                DemoIcon(R.drawable.reader_ic_clear, size = 16.dp, tint = readerExtraColors().muted)
            }
        }
    }
}

/** Search result item. */
@Composable
fun DemoSearchResultItem(
    title: String,
    subtitle: String,
    highlight: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = ReaderTokenAdapter.spacing(ReaderSpacingToken.SM)),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(title, style = TextStyle(fontFamily = FontFamily.Default, fontSize = 15.sp, fontWeight = FontWeight(800)), color = MaterialTheme.colorScheme.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(subtitle, style = ReaderTextStyles.bookAuthor, color = readerExtraColors().muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (highlight != null) {
            Text(highlight, style = ReaderTextStyles.bookAuthor, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// I. Flow components
// ═══════════════════════════════════════════════════════════════════════════════

/** Flow window — mirrors `.fd-flow-window`. */
@Composable
fun DemoFlowWindow(
    steps: List<DemoFlowStep>,
    activeStep: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(ReaderTokenAdapter.spacing(ReaderSpacingToken.CARD_PADDING)),
        horizontalArrangement = Arrangement.spacedBy(ReaderTokenAdapter.spacing(ReaderSpacingToken.SM))
    ) {
        steps.forEachIndexed { index, step ->
            DemoFlowStepComposable(label = step.label, active = index == activeStep, completed = step.completed)
        }
    }
}

/** Flow step composable — renders a single step in DemoFlowWindow. */
@Composable
fun DemoFlowStepComposable(
    label: String,
    active: Boolean,
    completed: Boolean
) {
    Row(
        modifier = Modifier
            .defaultMinSize(minHeight = 36.dp)
            .background(
                if (active) ReaderTokenAdapter.color(ReaderColorToken.PRIMARY_DARK) else MaterialTheme.colorScheme.surface.copy(alpha = 0.74f),
                RoundedCornerShape(ReaderTokenAdapter.radius(ReaderRadiusToken.CONTROL))
            )
            .border(1.dp, if (active) Color.Transparent else readerExtraColors().hairline, RoundedCornerShape(ReaderTokenAdapter.radius(ReaderRadiusToken.CONTROL)))
            .padding(horizontal = ReaderTokenAdapter.spacing(ReaderSpacingToken.SM), vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ReaderTokenAdapter.spacing(ReaderSpacingToken.XS))
    ) {
        if (completed) {
            DemoIcon(R.drawable.reader_ic_check, size = 14.dp, tint = if (active) MaterialTheme.colorScheme.onPrimary else ReaderTokenAdapter.color(ReaderColorToken.STATUS_GOOD))
        }
        Text(label, style = TextStyle(fontFamily = FontFamily.Default, fontSize = 11.sp, fontWeight = FontWeight(800)), color = if (active) MaterialTheme.colorScheme.onPrimary else readerExtraColors().controlInk, maxLines = 1)
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Extra utility components
// ═══════════════════════════════════════════════════════════════════════════════

/** Metric badge — small label + value block for stats. */
@Composable
fun DemoMetricBadge(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(readerExtraColors().metaBackground, RoundedCornerShape(ReaderTokenAdapter.radius(ReaderRadiusToken.CARD)))
            .padding(horizontal = ReaderTokenAdapter.spacing(ReaderSpacingToken.SM), vertical = ReaderTokenAdapter.spacing(ReaderSpacingToken.XS))
    ) {
        Text(label, style = ReaderTextStyles.bookAuthor, color = readerExtraColors().muted)
        Text(value, style = ReaderTextStyles.bookTitle, color = MaterialTheme.colorScheme.onBackground)
    }
}
