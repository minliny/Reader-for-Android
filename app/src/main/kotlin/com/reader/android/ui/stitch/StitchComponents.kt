package com.reader.android.ui.stitch

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reader.android.ui.theme.ReaderTheme

// ── StitchPanel ──
@Composable
fun StitchPanel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(ReaderTheme.colors.floatingControlBg)
            .padding(12.dp)
    ) { content() }
}

// ── StitchListItem ──
@Composable
fun StitchListItem(
    title: String,
    subtitle: String = "",
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null, role = Role.Button, onClick = onClick
            ) else Modifier)
            .semantics { contentDescription = title }
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = ReaderTheme.colors.controlInk,
                fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) Text(subtitle, color = ReaderTheme.colors.bodyText,
                fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (onClick != null) {
            Spacer(modifier = Modifier.width(4.dp))
            Icon(Icons.Filled.ChevronRight, null, tint = ReaderTheme.colors.controlInk, modifier = Modifier.size(16.dp))
        }
    }
}

// ── StitchSearchField ──
@Composable
fun StitchSearchField(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(ReaderTheme.colors.metaBg)
            .semantics { contentDescription = "搜索" }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Search, null, tint = ReaderTheme.colors.controlInk, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            query.ifBlank { "搜索书籍" },
            color = if (query.isBlank()) ReaderTheme.colors.bodyText else ReaderTheme.colors.controlInk,
            fontSize = 14.sp
        )
    }
}

// ── StitchErrorState ──
@Composable
fun StitchErrorState(
    title: String,
    message: String = "",
    code: String = "",
    onRetry: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("!", color = ReaderTheme.colors.primary, fontSize = 48.sp, fontWeight = FontWeight.Light,
            modifier = Modifier.semantics { heading() })
        Spacer(modifier = Modifier.height(12.dp))
        Text(title, color = ReaderTheme.colors.controlInk, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        if (message.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(message, color = ReaderTheme.colors.bodyText, fontSize = 13.sp)
        }
        if (code.isNotBlank()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text("[$code]", color = ReaderTheme.colors.bodyText.copy(alpha = 0.6f), fontSize = 11.sp)
        }
        Spacer(modifier = Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            onBack?.let {
                StitchActionButton("返回", it, modifier = Modifier)
            }
            onRetry?.let {
                StitchActionButton("重试", it, modifier = Modifier, primary = true)
            }
        }
    }
}

// ── StitchActionButton ──
@Composable
fun StitchActionButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = false) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (primary) ReaderTheme.colors.primary else ReaderTheme.colors.floatingControlBg)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null, role = Role.Button, onClick = onClick
            )
            .semantics { contentDescription = text }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(text, color = if (primary) Color.White else ReaderTheme.colors.controlInk, fontSize = 13.sp)
    }
}
