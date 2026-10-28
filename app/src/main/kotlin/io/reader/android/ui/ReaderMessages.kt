package io.reader.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Display-only adapters. The caller retains operation identity, receipts and allowed actions. */
@Composable
fun ReaderTransientFeedback(message: String, onRetire: () -> Unit) {
    LaunchedEffect(message) { delay(2_000); onRetire() }
    Surface(color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().testTag("reader_transient_feedback")) {
        Row(Modifier.padding(horizontal = 19.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
fun ReaderRecoveryNotice(message: String, busy: Boolean, onReconcile: (() -> Unit)?, actionLabel: String = "核对并继续",
    actionTag: String = "reader_reconcile_import") {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().testTag("reader_recovery_notice")) {
        Column(Modifier.padding(horizontal = 19.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(message, style = MaterialTheme.typography.bodyMedium)
            if (onReconcile != null) TextButton(onClick = onReconcile, enabled = !busy,
                modifier = Modifier.testTag(actionTag)) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
fun ReaderPageState(title: String, detail: String, action: String, onAction: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(30.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = onAction) { Text(action) }
    }
}

data class ReaderConfirmationPresentation(val identity: String, val title: String, val body: String, val confirmLabel: String)

/** Native confirmation consumes a presentation identity, never a mutable current-book lookup. */
@Composable
fun ReaderNativeConfirmation(
    presentation: ReaderConfirmationPresentation?,
    onConfirm: (String) -> Unit,
    onDismiss: (String) -> Unit,
) {
    if (presentation == null) return
    AlertDialog(
        onDismissRequest = { onDismiss(presentation.identity) },
        title = { Text(presentation.title) }, text = { Text(presentation.body) },
        confirmButton = { TextButton(onClick = { onConfirm(presentation.identity) }) { Text(presentation.confirmLabel) } },
        dismissButton = { TextButton(onClick = { onDismiss(presentation.identity) }) { Text("取消") } },
    )
}
