package com.reader.ui.shell

/**
 * Slice D — HostRequest dispatch envelope.
 *
 * The UI Reducer is a pure function: it cannot perform side-effects (like
 * calling [com.reader.host.HostAdapter.dispatch]). Instead, when the user
 * taps a TTS / permission / notification / share / clipboard / device
 * button, the UI dispatches a [ReaderUiIntent.DispatchHostRequest] into
 * the reducer; the reducer stamps a [HostRequestDispatch] into
 * [ReaderUiState.pendingHostRequests].
 *
 * The UI layer (the `ReaderApp` composable or its ViewModel) observes
 * `pendingHostRequests` and actually calls
 * [com.reader.host.HostAdapter.dispatch] for each entry. When the host
 * replies, the UI dispatches [ReaderUiIntent.HostRequestComplete] or
 * [ReaderUiIntent.HostRequestError] back into the reducer, which removes
 * the entry from the queue.
 *
 * This effect-saga pattern keeps the reducer pure while giving the UI a
 * single addressable queue for all host-bound requests.
 *
 * @property dispatchId Unique id (same as the intent's [requestId]) so the
 *   completion intent can find and remove the right entry.
 * @property capability The host capability name, e.g. `tts.system.start`,
 *   `permission.check`, `notification.show`, `share.invoke`,
 *   `clipboard.copy`, `device.vibrate`.
 * @property paramsJson JSON-encoded parameters for the capability handler.
 * @property createdAtMillis For diagnostics / timeout tracking.
 */
data class HostRequestDispatch(
    val dispatchId: String,
    val capability: String,
    val paramsJson: String,
    val createdAtMillis: Long = System.currentTimeMillis()
)
