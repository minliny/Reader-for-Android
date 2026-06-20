package com.reader.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.reader.android.ui.theme.ReaderTheme

@Composable
fun ReaderLoadingState(
    modifier: Modifier = Modifier,
    message: String = "加载中"
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .semantics { contentDescription = message },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(color = ReaderTheme.colors.primary, trackColor = ReaderTheme.colors.mutedTrack)
        Spacer(modifier = Modifier.height(ReaderTheme.spacing.sm))
        Text(text = message, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.stateMessage)
    }
}

@Composable
fun ReaderEmptyState(
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    ReaderStateMessage(
        icon = ReaderIconToken.FolderOff.asImageVector(),
        title = title,
        message = message,
        actionText = actionText,
        onActionClick = onActionClick,
        modifier = modifier,
        contentDescription = "空状态，$title"
    )
}

@Composable
fun ReaderErrorState(
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    retryText: String = "重试",
    onRetryClick: (() -> Unit)? = null
) {
    ReaderStateMessage(
        icon = ReaderIconToken.Warning.asImageVector(),
        title = title,
        message = message,
        actionText = if (onRetryClick != null) retryText else null,
        onActionClick = onRetryClick,
        modifier = modifier,
        contentDescription = "错误状态，$title"
    )
}

@Composable
fun ReaderOfflineState(
    modifier: Modifier = Modifier,
    title: String = "当前离线",
    message: String? = "请检查网络后重试",
    retryText: String = "重试",
    onRetryClick: (() -> Unit)? = null
) {
    ReaderStateMessage(
        icon = ReaderIconToken.Offline.asImageVector(),
        title = title,
        message = message,
        actionText = if (onRetryClick != null) retryText else null,
        onActionClick = onRetryClick,
        modifier = modifier,
        contentDescription = "离线状态，$title"
    )
}

@Composable
fun ReaderPermissionRequiredState(
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    actionText: String = "授权",
    onActionClick: (() -> Unit)? = null
) {
    ReaderStateMessage(
        icon = ReaderIconToken.Permission.asImageVector(),
        title = title,
        message = message,
        actionText = if (onActionClick != null) actionText else null,
        onActionClick = onActionClick,
        modifier = modifier,
        contentDescription = "需要权限，$title"
    )
}

@Composable
private fun ReaderStateMessage(
    icon: ImageVector,
    title: String,
    message: String?,
    actionText: String?,
    onActionClick: (() -> Unit)?,
    modifier: Modifier,
    contentDescription: String
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(ReaderTheme.spacing.lg)
            .semantics { this.contentDescription = contentDescription },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.height(48.dp),
            tint = ReaderTheme.colors.primary
        )
        Spacer(modifier = Modifier.height(ReaderTheme.spacing.md))
        Text(
            text = title,
            modifier = Modifier.semantics { heading() },
            color = ReaderTheme.colors.controlInk,
            style = ReaderTheme.typography.stateTitle,
            textAlign = TextAlign.Center
        )
        if (message != null) {
            Spacer(modifier = Modifier.height(ReaderTheme.spacing.xs))
            Text(
                text = message,
                color = ReaderTheme.colors.bodyText,
                style = ReaderTheme.typography.stateMessage,
                textAlign = TextAlign.Center
            )
        }
        if (actionText != null && onActionClick != null) {
            Spacer(modifier = Modifier.height(ReaderTheme.spacing.md))
            ReaderPrimaryButton(
                text = actionText,
                onClick = onActionClick,
                contentDescription = actionText
            )
        }
    }
}
