package com.reader.android.data.bridge

// ── Error taxonomy matching Reader-Core MappedReaderError ──
// Types retained (ReaderErrorCode / ReaderFailureStage / ReaderError / BridgeResult)
// are referenced by TtsErrorMapper / WebRuntimeErrorMapper and BridgeContractTest.
// The unused ReaderCoreBridge interface was removed; the real Rust Core bridge is
// com.reader.core.NativeCoreBridge (JNI) consumed via ReaderCoreRuntime.

enum class ReaderErrorCode {
    NETWORK,
    PARSE,
    TIMEOUT,
    NOT_FOUND,
    UNAUTHORIZED,
    FORBIDDEN,
    UNKNOWN
}

enum class ReaderFailureStage {
    SEARCH,
    TOC,
    CONTENT,
    BOOK_INFO
}

data class ReaderError(
    val code: ReaderErrorCode,
    val stage: ReaderFailureStage,
    val message: String? = null,
    val sourceName: String? = null
)

// ── Bridge result types ──

sealed class BridgeResult<out T> {
    data class Success<T>(val data: T) : BridgeResult<T>()
    data class Failure(val error: ReaderError) : BridgeResult<Nothing>()
}
