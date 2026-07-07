package com.reader.ui.shell

import com.reader.host.HostAdapter
import com.reader.host.HostReply
import com.reader.host.HostRequest

/**
 * Slice E — Production consumer of [ReaderUiState.pendingHostRequests].
 *
 * The reducer is pure and can only stamp a [HostRequestDispatch] entry into
 * [ReaderUiState.pendingHostRequests]; it cannot call
 * [HostAdapter.dispatch]. This class is the narrow bridge that turns a
 * queued [HostRequestDispatch] into a real [HostAdapter.dispatch] call and
 * returns a [HostRequestResult] the caller can fold back into the reducer
 * via [ReaderUiIntent.HostRequestComplete] / [HostRequestError].
 *
 * Design:
 *  - Synchronous `dispatch()` wrapper around [HostAdapter.dispatch] so the
 *    caller (a coroutine in the AppShell effect collector) decides threading.
 *  - No Android dependencies — pure JVM, fully testable without a device.
 *  - Maps [HostReply] → [HostRequestResult] including the error
 *    code/message from [HostReply.Error].
 *
 * The caller is responsible for:
 *  - deduplication (skip dispatchIds it has already processed)
 *  - timeout enforcement (e.g. `withTimeoutOrNull`)
 *  - dispatching the returned [HostRequestResult] back into the reducer
 *
 * This keeps the dispatcher single-purpose and easy to test.
 *
 * @property hostAdapter the registered capability set (wired in
 *   [com.reader.api.ReaderCoreClient.init] via `HostRuntime`).
 */
class HostRequestDispatcher(
    private val hostAdapter: HostAdapter
) {

    /**
     * Executes one queued [HostRequestDispatch] against the host adapter.
     *
     * Returns a [HostRequestResult] with `success=true` on
     * [HostReply.Complete] or `success=false` on [HostReply.Error]. Never
     * throws — handler exceptions are caught by [HostAdapter.dispatch] and
     * surfaced as `HostReply.Error(INTERNAL, retryable=true)`.
     */
    fun dispatch(entry: HostRequestDispatch): HostRequestResult {
        val request = HostRequest(
            REQUEST_ID,
            operationId(entry),
            entry.capability,
            entry.paramsJson
        )
        val reply = hostAdapter.dispatch(request)
        return when (reply) {
            is HostReply.Complete -> HostRequestResult(
                dispatchId = entry.dispatchId,
                capability = entry.capability,
                success = true,
                resultJson = reply.resultJson()
            )
            is HostReply.Error -> HostRequestResult(
                dispatchId = entry.dispatchId,
                capability = entry.capability,
                success = false,
                errorCode = reply.code(),
                errorMessage = reply.message()
            )
            else -> HostRequestResult(
                dispatchId = entry.dispatchId,
                capability = entry.capability,
                success = false,
                errorCode = "INTERNAL",
                errorMessage = "unexpected HostReply kind: ${reply?.kind()}"
            )
        }
    }

    private fun operationId(entry: HostRequestDispatch): Long = try {
        // dispatchId is a string requestId from ReaderUiIntent.generateRequestId;
        // derive a stable numeric operationId from its hashCode to keep the
        // HostRequest contract field populated without leaking the string.
        entry.dispatchId.hashCode().toLong() and 0xFFFFFFFFL
    } catch (e: Exception) {
        0L
    }

    companion object {
        /** Stable requestId for dispatcher-originated host requests. */
        private const val REQUEST_ID: Long = 1L
    }
}
