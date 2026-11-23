package io.reader.android.runtime

/** Serialized parsed JSON from the same admitted close call; NOT byte-exact raw transport. */
data class ReaderContentSearchCloseObservation(
    val session: ReaderContentSearchSession,
    val cursor: String,
    val requestId: Long,
    val runtimeSession: String,
    val parametersJson: String,
    val parsedTerminalJson: String,
)

data class ReaderSearchCloseScope(val readingOwner: Long, val searchGeneration: Long, val session: ReaderContentSearchSession)
data class ReaderSearchCloseConfirmation(val scope: ReaderSearchCloseScope, val observation: ReaderContentSearchCloseObservation)

/** UI publication identity only; never dispatches, retries, ACKs or owns cleanup. */
internal object ReaderSearchClosePublicationPolicy {
    fun confirmed(scope: ReaderSearchCloseScope, currentOwner: Long?, currentGeneration: Long,
        inFlight: ReaderSearchCloseScope?, observation: ReaderContentSearchCloseObservation?): ReaderSearchCloseConfirmation? {
        if (inFlight != scope || currentOwner != scope.readingOwner || currentGeneration != scope.searchGeneration ||
            observation == null || observation.session != scope.session || observation.runtimeSession != scope.session.runtimeSession) return null
        return ReaderSearchCloseConfirmation(scope, observation)
    }
}
