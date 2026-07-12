package com.reader.ui.shell

import io.reader.ui.runtime.ReaderUIRuntime
import io.reader.ui.runtime.ReaderUIState
import io.reader.ui.runtime.ReaderUITransition

/**
 * Thin Android seam for the shared executable UI runtime while the native
 * reducer remains the production owner. It deliberately delegates canonical
 * event lookup and action execution to [ReaderUIRuntime]; Android does not
 * copy or reinterpret `GeneratedRuntimeActions`.
 *
 * The adapter is currently used by projection-parity tests. Route-by-route
 * production cutover can reuse the same seam once the explicit `book.open`
 * route/loading migration gap is resolved.
 */
class ReaderUiRuntimeShadowAdapter(
    initialState: ReaderUIState = ReaderUIState()
) {
    private val runtime = ReaderUIRuntime(initialState)

    val state: ReaderUIState
        get() = runtime.state

    fun dispatch(
        event: String,
        payload: Map<String, String> = emptyMap(),
        correlationId: String? = null
    ): ReaderUITransition = runtime.dispatch(event, payload, correlationId)

    fun completeAsync(error: String? = null): ReaderUIState = runtime.completeAsync(error)
}
