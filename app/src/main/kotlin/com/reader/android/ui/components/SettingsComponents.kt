package com.reader.android.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.reader.android.ui.theme.ReaderTheme

@Composable
fun ReaderSettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    contentDescription: String = title,
    trailing: @Composable () -> Unit = {},
    onClick: (() -> Unit)? = null
) {
    ReaderListItem(
        title = title,
        subtitle = subtitle,
        modifier = modifier,
        trailing = trailing,
        contentDescription = contentDescription,
        onClick = onClick
    )
}

@Composable
fun ReaderSettingsSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    contentDescription: String = title
) {
    ReaderSettingsRow(
        title = title,
        subtitle = subtitle,
        modifier = modifier.semantics { this.contentDescription = "$contentDescription，${if (checked) "已开启" else "已关闭"}" },
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = ReaderTheme.colors.paperBg,
                    checkedTrackColor = ReaderTheme.colors.primary,
                    uncheckedThumbColor = ReaderTheme.colors.controlInk,
                    uncheckedTrackColor = ReaderTheme.colors.mutedTrack
                )
            )
        },
        onClick = { onCheckedChange(!checked) }
    )
}

@Composable
fun ReaderSettingsDropdownRow(
    title: String,
    selectedText: String,
    options: List<String>,
    onOptionSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    contentDescription: String = title
) {
    var expanded by remember { mutableStateOf(false) }
    ReaderSettingsRow(
        title = title,
        subtitle = subtitle ?: selectedText,
        modifier = modifier,
        contentDescription = "$contentDescription，当前为$selectedText",
        trailing = {
            Icon(
                imageVector = ReaderIconToken.ChevronDown.asImageVector(),
                contentDescription = "展开$title",
                tint = ReaderTheme.colors.controlInk
            )
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(text = option, color = ReaderTheme.colors.controlInk) },
                        onClick = {
                            onOptionSelected(option)
                            expanded = false
                        }
                    )
                }
            }
        },
        onClick = { expanded = true }
    )
}

@Composable
fun ReaderSettingsGroup(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = "设置分组，$title" }
    ) {
        ReaderSectionHeader(title = title)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = ReaderTheme.spacing.sm),
            content = content
        )
    }
}
