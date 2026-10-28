package io.reader.android.runtime

/** Progress's outer business completion still runs after the exact terminal sink.
 * Only a real typed protocol failure keeps priority over that completion; all
 * other outcomes retain the previous finally-block exception precedence.
 */
internal fun <T> withProgressTerminalCompletion(request: () -> T, complete: () -> Unit): T {
    var requestFailure: Throwable? = null
    try {
        return request()
    } catch (failure: Throwable) {
        requestFailure = failure
        throw failure
    } finally {
        try {
            complete()
        } catch (completionFailure: Throwable) {
            val primary = requestFailure
            if (primary == null || primary.coreProtocolPending() == null) throw completionFailure
            if (completionFailure !== primary) primary.addSuppressed(completionFailure)
        }
    }
}
