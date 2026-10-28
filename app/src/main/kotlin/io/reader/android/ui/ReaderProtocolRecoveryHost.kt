package io.reader.android.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Install in the existing shelf/reader message host; it never collects actor.changes. */
@Composable
internal fun ReaderProtocolRecoveryHost(model: ReaderViewModel) {
    val recovery by model.coreProtocolRecoveries.collectAsStateWithLifecycle()
    val host = model.protocolRecoveryHost() // VM UUID + route/generation, captured by the physical callback.
    Column {
        recovery.items.forEach { presentation ->
            ReaderProtocolRecoveryNotice(presentation) { id -> model.recoverCoreProtocol(id, host) }
        }
    }
    // initialized=false with no items is empty, not an instruction to create
    // a runtime or read books. Business page state still belongs to its owner.
}
