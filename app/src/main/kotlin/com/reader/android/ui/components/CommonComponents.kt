package com.reader.android.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.reader.android.ui.theme.ReaderTheme

@Immutable
data class ReaderMainTab(
    val label: String,
    val contentDescription: String,
    val icon: ImageVector
)

@Composable
fun ReaderMainTabShell(
    tabs: List<ReaderMainTab>,
    selectedIndex: Int,
    showMainNav: Boolean,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        modifier = modifier,
        bottomBar = {
            if (showMainNav) {
                ReaderMainTabBar(
                    tabs = tabs,
                    selectedIndex = selectedIndex,
                    onTabSelected = onTabSelected
                )
            }
        },
        content = content
    )
}

@Composable
fun ReaderAppTopBar(
    title: String,
    modifier: Modifier = Modifier,
    navigationContentDescription: String = "返回",
    onNavigateBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(ReaderTheme.colors.paperBg)
            .padding(horizontal = ReaderTheme.spacing.sm, vertical = ReaderTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onNavigateBack != null) {
            ReaderIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = navigationContentDescription,
                onClick = onNavigateBack
            )
            Spacer(modifier = Modifier.width(ReaderTheme.spacing.xs))
        }
        Text(
            text = title,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
            color = ReaderTheme.colors.controlInk,
            style = ReaderTheme.typography.pageTitle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Row(horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.xs), content = actions)
    }
}

@Composable
fun ReaderMainTabBar(
    tabs: List<ReaderMainTab>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(ReaderTheme.colors.bottomBarBg)
            .padding(vertical = ReaderTheme.spacing.xs)
            .semantics { contentDescription = "主导航" },
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        tabs.forEachIndexed { index, tab ->
            val selected = index == selectedIndex
            val iconContainerModifier = Modifier
                .size(28.dp)
                .clip(ReaderTheme.shapes.medium)
                .let { base ->
                    if (selected) base.background(ReaderTheme.colors.primary) else base
                }

            Column(
                modifier = Modifier
                    .width(64.dp)
                    .height(44.dp)
                    .clickable(
                        role = Role.Tab,
                        onClick = { onTabSelected(index) }
                    ),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(iconContainerModifier, contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.contentDescription,
                        tint = if (selected) ReaderTheme.colors.paperBg else ReaderTheme.colors.controlInk,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    text = tab.label,
                    style = ReaderTheme.typography.readerControlLabel,
                    color = if (selected) ReaderTheme.colors.primary else ReaderTheme.colors.controlInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun ReaderSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ReaderTheme.spacing.screenPadding, vertical = ReaderTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
            color = ReaderTheme.colors.controlInk,
            style = ReaderTheme.typography.sectionTitle
        )
        action?.invoke()
    }
}

@Composable
fun ReaderCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentDescription: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val cardModifier = modifier.then(
        if (contentDescription != null) {
            Modifier.semantics { this.contentDescription = contentDescription }
        } else {
            Modifier
        }
    )
    Card(
        modifier = cardModifier,
        shape = ReaderTheme.shapes.card,
        colors = CardDefaults.cardColors(containerColor = ReaderTheme.colors.metaBg),
        elevation = CardDefaults.cardElevation(defaultElevation = ReaderTheme.elevation.card),
        border = BorderStroke(1.dp, ReaderTheme.colors.controlBorder),
        onClick = onClick ?: {},
        enabled = onClick != null
    ) {
        Column(modifier = Modifier.padding(ReaderTheme.spacing.cardPadding), content = content)
    }
}

@Composable
fun ReaderPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String = text
) {
    Button(
        onClick = onClick,
        modifier = modifier.semantics { this.contentDescription = contentDescription },
        enabled = enabled,
        shape = ReaderTheme.shapes.chip,
        colors = ButtonDefaults.buttonColors(
            containerColor = ReaderTheme.colors.primary,
            contentColor = ReaderTheme.colors.paperBg,
            disabledContainerColor = ReaderTheme.colors.mutedTrack,
            disabledContentColor = ReaderTheme.colors.controlInk
        ),
        contentPadding = PaddingValues(horizontal = ReaderTheme.spacing.md, vertical = ReaderTheme.spacing.sm),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = ReaderTheme.elevation.none)
    ) {
        Text(text = text, style = ReaderTheme.typography.readerControlLabel)
    }
}

@Composable
fun ReaderSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String = text
) {
    Surface(
        modifier = modifier
            .clip(ReaderTheme.shapes.chip)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        shape = ReaderTheme.shapes.chip,
        color = ReaderTheme.colors.quickButtonBg,
        contentColor = ReaderTheme.colors.controlInk,
        border = BorderStroke(1.dp, ReaderTheme.colors.controlBorder),
        tonalElevation = ReaderTheme.elevation.none,
        shadowElevation = ReaderTheme.elevation.none
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = ReaderTheme.spacing.md, vertical = ReaderTheme.spacing.sm),
            style = ReaderTheme.typography.readerControlLabel
        )
    }
}

/**
 * General-purpose icon button with Material3 IconButton styling.
 * For reader control layer icon buttons, see [com.reader.android.ui.components.ReaderNativeComponents.ReaderIconButton].
 */
@Composable
fun ReaderIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(ReaderTheme.spacing.quickCircleSize)
            .background(ReaderTheme.colors.quickButtonBg, ReaderTheme.shapes.quickCircle)
            .border(1.dp, ReaderTheme.colors.controlBorder, ReaderTheme.shapes.quickCircle),
        enabled = enabled
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = ReaderTheme.colors.controlInk
        )
    }
}

/**
 * General-purpose chip with solid background selection state.
 * For reader control layer chips, see [com.reader.android.ui.components.ReaderNativeComponents.ReaderChip].
 */
@Composable
fun ReaderChip(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    contentDescription: String = text,
    onClick: (() -> Unit)? = null
) {
    val background = if (selected) ReaderTheme.colors.primary else ReaderTheme.colors.floatingControlBg
    val foreground = if (selected) ReaderTheme.colors.paperBg else ReaderTheme.colors.controlInk
    Box(
        modifier = modifier
            .clip(ReaderTheme.shapes.chip)
            .background(background)
            .border(1.dp, ReaderTheme.colors.controlBorder, ReaderTheme.shapes.chip)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .semantics { this.contentDescription = contentDescription }
            .padding(horizontal = ReaderTheme.spacing.sm, vertical = ReaderTheme.spacing.xs),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = foreground, style = ReaderTheme.typography.readerControlLabel)
    }
}

@Composable
fun ReaderDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier,
        thickness = DividerDefaults.Thickness,
        color = ReaderTheme.colors.controlBorder
    )
}

@Composable
fun ReaderListItem(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    contentDescription: String = title,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .semantics { this.contentDescription = contentDescription }
            .padding(horizontal = ReaderTheme.spacing.screenPadding, vertical = ReaderTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        leading?.invoke()
        if (leading != null) Spacer(modifier = Modifier.width(ReaderTheme.spacing.sm))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = ReaderTheme.colors.controlInk,
                style = ReaderTheme.typography.bookTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = ReaderTheme.colors.bodyText,
                    style = ReaderTheme.typography.bookMeta,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (trailing != null) {
            Spacer(modifier = Modifier.width(ReaderTheme.spacing.sm))
            trailing()
        }
    }
}
