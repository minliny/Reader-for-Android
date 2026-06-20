package com.reader.android.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.reader.android.ui.theme.ReaderTheme

@Composable
fun ReaderSearchBox(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "输入书名或作者",
    contentDescription: String = "搜索输入框"
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .fillMaxWidth()
            .semantics { this.contentDescription = contentDescription },
        textStyle = ReaderTheme.typography.bookTitle,
        placeholder = {
            Text(text = placeholder, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.bookMeta)
        },
        leadingIcon = {
            Icon(
                imageVector = ReaderIconToken.Search.asImageVector(),
                contentDescription = "搜索",
                tint = ReaderTheme.colors.controlInk
            )
        },
        singleLine = true,
        shape = ReaderTheme.shapes.chip,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = ReaderTheme.colors.controlInk,
            unfocusedTextColor = ReaderTheme.colors.controlInk,
            focusedContainerColor = ReaderTheme.colors.metaBg,
            unfocusedContainerColor = ReaderTheme.colors.metaBg,
            focusedBorderColor = ReaderTheme.colors.primary,
            unfocusedBorderColor = ReaderTheme.colors.controlBorder,
            cursorColor = ReaderTheme.colors.primary
        )
    )
}

@Composable
fun SearchResultItem(
    title: String,
    sourceName: String,
    modifier: Modifier = Modifier,
    author: String? = null,
    latestChapter: String? = null,
    intro: String? = null,
    onClick: (() -> Unit)? = null
) {
    ReaderCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ReaderTheme.spacing.screenPadding, vertical = 4.dp),
        onClick = onClick,
        contentDescription = "搜索结果，$title"
    ) {
        Row {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = ReaderTheme.colors.controlInk,
                    style = ReaderTheme.typography.bookTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(ReaderTheme.spacing.xs))
                Row {
                    SearchResultSourceChip(sourceName = sourceName)
                    if (author != null) {
                        BookMetaText(
                            text = author,
                            modifier = Modifier.padding(start = ReaderTheme.spacing.xs)
                        )
                    }
                }
                if (latestChapter != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    BookMetaText(text = latestChapter)
                }
                if (intro != null) {
                    Spacer(modifier = Modifier.height(ReaderTheme.spacing.xs))
                    BookMetaText(text = intro, maxLines = 2)
                }
            }
        }
    }
}

@Composable
fun SearchResultSourceChip(
    sourceName: String,
    modifier: Modifier = Modifier
) {
    ReaderChip(
        text = sourceName,
        modifier = modifier,
        contentDescription = "来源，$sourceName"
    )
}
