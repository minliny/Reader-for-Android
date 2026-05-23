package com.reader.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.reader.android.ui.theme.ReaderTheme

@Composable
fun BookCard(
    title: String,
    modifier: Modifier = Modifier,
    author: String? = null,
    progress: Float? = null,
    onClick: (() -> Unit)? = null
) {
    ReaderCard(
        modifier = modifier,
        onClick = onClick,
        contentDescription = "书籍，$title"
    ) {
        BookCover(title = title, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(ReaderTheme.spacing.sm))
        Text(
            text = title,
            color = ReaderTheme.colors.controlInk,
            style = ReaderTheme.typography.bookTitle,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (author != null) {
            Spacer(modifier = Modifier.height(2.dp))
            BookMetaText(text = author)
        }
        if (progress != null) {
            Spacer(modifier = Modifier.height(ReaderTheme.spacing.xs))
            BookProgressIndicator(progress = progress)
        }
    }
}

@Composable
fun BookListItem(
    title: String,
    modifier: Modifier = Modifier,
    author: String? = null,
    latestChapter: String? = null,
    progress: Float? = null,
    onClick: (() -> Unit)? = null
) {
    ReaderListItem(
        title = title,
        subtitle = listOfNotNull(author, latestChapter).joinToString(" · ").ifBlank { null },
        modifier = modifier,
        contentDescription = "书籍，$title",
        onClick = onClick,
        leading = {
            BookCover(
                title = title,
                modifier = Modifier.size(width = 48.dp, height = 64.dp)
            )
        },
        trailing = {
            if (progress != null) {
                BookProgressIndicator(progress = progress, modifier = Modifier.width(72.dp))
            }
        }
    )
}

@Composable
fun BookCover(
    title: String,
    modifier: Modifier = Modifier,
    contentDescription: String = "书籍封面，$title"
) {
    Box(
        modifier = modifier
            .aspectRatio(0.72f)
            .clip(ReaderTheme.shapes.small)
            .background(ReaderTheme.colors.floatingControlBg)
            .semantics { this.contentDescription = contentDescription }
            .padding(ReaderTheme.spacing.xs),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title.take(4),
            color = ReaderTheme.colors.controlInk,
            style = ReaderTheme.typography.bookTitle,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun BookProgressIndicator(
    progress: Float,
    modifier: Modifier = Modifier,
    contentDescription: String = "阅读进度，${(progress.coerceIn(0f, 1f) * 100).toInt()}%"
) {
    LinearProgressIndicator(
        progress = { progress.coerceIn(0f, 1f) },
        modifier = modifier
            .height(4.dp)
            .clip(ReaderTheme.shapes.chip)
            .semantics { this.contentDescription = contentDescription },
        color = ReaderTheme.colors.primary,
        trackColor = ReaderTheme.colors.mutedTrack,
        drawStopIndicator = {}
    )
}

@Composable
fun BookMetaText(
    text: String,
    modifier: Modifier = Modifier,
    maxLines: Int = 1
) {
    Text(
        text = text,
        modifier = modifier,
        color = ReaderTheme.colors.bodyText,
        style = ReaderTheme.typography.bookMeta,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
fun BookActionSheetItem(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: @Composable (() -> Unit)? = null,
    onClick: () -> Unit
) {
    ReaderListItem(
        title = title,
        subtitle = subtitle,
        modifier = modifier,
        leading = leading,
        contentDescription = title,
        onClick = onClick
    )
}
