package com.reader.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reader.android.ui.theme.ReaderTheme

// ── Shared style constants ──

private val panelCornerRadius = 4.dp
private val panelBorderWidth = 0.5.dp
private val controlRowHeight = 44.dp
private val iconSize = 20.dp

// ── ReaderPanel ──

@Composable
fun ReaderPanel(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(panelCornerRadius))
            .background(ReaderTheme.colors.floatingControlBg)
            .padding(ReaderTheme.spacing.sm)
    ) {
        content()
    }
}

/**
 * Reader control layer icon button with soft touch target (no Material circle/shape).
 * For general UI icon buttons, see [com.reader.android.ui.components.CommonComponents.ReaderIconButton].
 */
@Composable
fun ReaderIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    iconSize: Dp = 22.dp,
    tint: Color = ReaderTheme.colors.controlInk
) {
    Box(
        modifier = modifier
            .size(size)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
    }
}

// ── ReaderQuickCircle (floating soft circle, distinct from Material FAB) ──

@Composable
fun ReaderQuickCircle(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 52.dp,
    height: Dp = 40.dp,
    iconSize: Dp = 20.dp,
    tint: Color = ReaderTheme.colors.controlInk
) {
    Box(
        modifier = modifier
            .size(width, height)
            .clip(RoundedCornerShape(6.dp))
            .background(ReaderTheme.colors.quickButtonBg)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
    }
}

// ── ReaderActionButton ──

@Composable
fun ReaderActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false
) {
    Box(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (primary) ReaderTheme.colors.primary else ReaderTheme.colors.floatingControlBg)
            .border(0.5.dp, ReaderTheme.colors.controlBorder, RoundedCornerShape(4.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = text }
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (primary) ReaderTheme.colors.paperBg else ReaderTheme.colors.controlInk,
            style = ReaderTheme.typography.readerControlLabel
        )
    }
}

// ── ReaderSwitch ──

@Composable
fun ReaderSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val trackColor = if (checked) ReaderTheme.colors.primary else ReaderTheme.colors.mutedTrack
    val thumbColor = if (checked) ReaderTheme.colors.paperBg else ReaderTheme.colors.controlInk
    Box(
        modifier = modifier
            .size(36.dp, 22.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(trackColor)
            .clickable(role = Role.Switch, onClick = { onCheckedChange(!checked) })
            .semantics { contentDescription = if (checked) "已开启" else "已关闭" },
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .padding(2.dp)
                .clip(CircleShape)
                .background(thumbColor)
        )
    }
}

// ── ReaderSlider ──

@Composable
fun ReaderSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val trackColor = ReaderTheme.colors.mutedTrack
    val activeColor = if (enabled) ReaderTheme.colors.primary else ReaderTheme.colors.mutedTrack
    Box(
        modifier = modifier
            .height(32.dp)
            .semantics { contentDescription = "滑块，${(value * 100).toInt()}%" }
    ) {
        // Thin track
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .align(Alignment.Center)
                .clip(RoundedCornerShape(1.5.dp))
                .background(trackColor)
        )
        // Active fill — proportional to value
        Box(
            modifier = Modifier
                .fillMaxWidth(value.coerceIn(0f, 1f))
                .height(3.dp)
                .align(Alignment.CenterStart)
                .clip(RoundedCornerShape(1.5.dp))
                .background(activeColor)
        )
    }
}

// ── ReaderProgressRail ──

@Composable
fun ReaderProgressRail(
    progress: Float,
    modifier: Modifier = Modifier,
    vertical: Boolean = false
) {
    val width = if (vertical) 4.dp else 40.dp
    val height = if (vertical) 40.dp else 4.dp
    Box(
        modifier = modifier
            .then(if (vertical) Modifier.width(6.dp).height(40.dp) else Modifier.width(40.dp).height(6.dp))
            .clip(RoundedCornerShape(3.dp))
            .background(ReaderTheme.colors.mutedTrack)
    ) {
        Box(
            modifier = Modifier
                .then(
                    if (vertical) Modifier.fillMaxWidth().fillMaxHeight(progress.coerceIn(0f, 1f))
                    else Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight()
                )
                .clip(RoundedCornerShape(3.dp))
                .background(ReaderTheme.colors.primary)
        )
    }
}

/**
 * Reader control layer chip with alpha-selected background and minimal shape.
 * For general UI chips, see [com.reader.android.ui.components.CommonComponents.ReaderChip].
 */
// ── ReaderChip ──

@Composable
fun ReaderChip(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(2.dp))
            .background(if (selected) ReaderTheme.colors.primary.copy(alpha = 0.12f) else ReaderTheme.colors.floatingControlBg)
            .border(0.5.dp, ReaderTheme.colors.controlBorder, RoundedCornerShape(2.dp))
            .semantics { contentDescription = text }
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            color = if (selected) ReaderTheme.colors.primary else ReaderTheme.colors.controlInk,
            style = ReaderTheme.typography.readerControlLabel
        )
    }
}

// ── ReaderSettingRow ──

@Composable
fun ReaderSettingRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = controlRowHeight)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .semantics { contentDescription = "$label，$value" },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = ReaderTheme.colors.controlInk, style = TextStyle(fontSize = 14.sp, lineHeight = 20.sp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(value, color = ReaderTheme.colors.bodyText, style = TextStyle(fontSize = 13.sp, lineHeight = 18.sp))
            if (onClick != null) {
                Spacer(modifier = Modifier.width(4.dp))
                Icon(Icons.Filled.ChevronRight, null, tint = ReaderTheme.colors.controlInk, modifier = Modifier.size(16.dp))
            }
        }
    }
}
