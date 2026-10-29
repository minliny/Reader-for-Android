package io.reader.android.runtime

/** Core-owned TTS slice identity. Host owns the Android speech engine. */
data class ReaderTtsChapterRef(
    val sourceId: String,
    val bookId: String,
    val chapterIndex: Int,
    val chapterTitle: String? = null,
    val chapterUrl: String? = null,
)

data class ReaderTtsSlice(
    val index: Int,
    val text: String,
    val charStart: Int,
    val charEnd: Int,
    val paragraphIndex: Int,
)

enum class ReaderTtsSlicingStrategy(val wire: String) {
    PARAGRAPH("paragraph"),
    SENTENCE("sentence"),
    PARAGRAPH_THEN_SENTENCE("paragraph-then-sentence"),
    LINE_BREAK("line-break"),
}

data class ReaderTtsSlicePlan(
    val chapter: ReaderTtsChapterRef,
    val strategy: ReaderTtsSlicingStrategy,
    val slices: List<ReaderTtsSlice>,
    val sourceCharCount: Int,
)

enum class ReaderTtsQueueState { IDLE, PLAYING, PAUSED, COMPLETED, STOPPED }
enum class ReaderTtsSliceStatus { PENDING, SPEAKING, DONE, SKIPPED, FAILED }
enum class ReaderTtsFailurePolicy(val wire: String) { SKIP("skip"), STOP("stop") }
enum class ReaderTtsQueueRestartPolicy(val wire: String) { RESET_ON_CORE_RESTART("reset-on-core-restart") }

data class ReaderTtsQueueSnapshot(
    val chapter: ReaderTtsChapterRef,
    val state: ReaderTtsQueueState,
    val currentSliceIndex: Int?,
    val totalSlices: Int,
    val completedSlices: Int,
    val sliceStatuses: List<ReaderTtsSliceStatus>,
    val failurePolicy: ReaderTtsFailurePolicy = ReaderTtsFailurePolicy.STOP,
    val consecutiveFailures: Int = 0,
    val failureLimit: Int = 3,
    val drainBehavior: ReaderTtsDrainBehavior = ReaderTtsDrainBehavior.STOP_ON_BOUNDARY,
    val restartPolicy: ReaderTtsQueueRestartPolicy = ReaderTtsQueueRestartPolicy.RESET_ON_CORE_RESTART,
)

data class ReaderTtsChapterTransition(
    val current: ReaderTtsChapterRef,
    val next: ReaderTtsChapterRef?,
    val drainBehavior: ReaderTtsDrainBehavior,
)

enum class ReaderTtsDrainBehavior(val wire: String) {
    STOP_ON_BOUNDARY("stop-on-boundary"),
    ADVANCE_TO_NEXT("advance-to-next"),
}
