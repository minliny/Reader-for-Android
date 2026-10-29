package io.reader.android.runtime

import org.json.JSONObject
import java.util.Collections

/** Core TTS DTO admission. It does not invoke Android TextToSpeech or rewrite slice text. */
internal object ReaderTtsPolicy {
    private val strategies = ReaderTtsSlicingStrategy.entries.associateBy { it.wire }
    private val drainBehaviors = ReaderTtsDrainBehavior.entries.associateBy { it.wire }

    fun chapter(value: JSONObject): ReaderTtsChapterRef {
        val sourceId = value.getString("sourceId").also { check(it.isNotBlank()) { "TTS_SOURCE_ID" } }
        val bookId = value.getString("bookId").also { check(it.isNotBlank()) { "TTS_BOOK_ID" } }
        val index = value.getInt("chapterIndex").also { check(it >= 0) { "TTS_CHAPTER_INDEX" } }
        fun optional(name: String): String? = when {
            !value.has(name) || value.isNull(name) -> null
            else -> value.getString(name).also { check(it.isNotBlank()) { "TTS_$name" } }
        }
        return ReaderTtsChapterRef(sourceId, bookId, index, optional("chapterTitle"), optional("chapterUrl"))
    }

    fun plan(value: JSONObject, expected: ReaderTtsChapterRef, sourceCharCount: Int): ReaderTtsSlicePlan {
        val plan = value.getJSONObject("plan")
        val actualChapter = chapter(plan.getJSONObject("chapter"))
        check(actualChapter.sourceId == expected.sourceId && actualChapter.bookId == expected.bookId &&
            actualChapter.chapterIndex == expected.chapterIndex) { "TTS_CHAPTER_SCOPE" }
        val strategy = strategies[plan.optString("strategy", ReaderTtsSlicingStrategy.PARAGRAPH.wire)]
            ?: error("TTS_STRATEGY")
        val sourceCount = plan.getInt("sourceCharCount").also {
            check(it >= 0 && it == sourceCharCount) { "TTS_SOURCE_CHAR_COUNT" }
        }
        val rows = plan.getJSONArray("slices")
        val slices = ArrayList<ReaderTtsSlice>(rows.length())
        var previousEnd = 0
        for (position in 0 until rows.length()) {
            val row = rows.getJSONObject(position)
            val index = row.getInt("index").also { check(it == position) { "TTS_SLICE_INDEX" } }
            val text = row.getString("text").also { check(it.isNotBlank()) { "TTS_SLICE_TEXT" } }
            val start = row.getInt("charStart")
            val end = row.getInt("charEnd")
            val paragraph = row.getInt("paragraphIndex")
            check(start >= previousEnd && end > start && end <= sourceCount && paragraph >= 0) { "TTS_SLICE_RANGE" }
            previousEnd = end
            slices += ReaderTtsSlice(index, text, start, end, paragraph)
        }
        check(sourceCount == 0 || slices.isNotEmpty()) { "TTS_EMPTY_PLAN" }
        return ReaderTtsSlicePlan(actualChapter, strategy, Collections.unmodifiableList(slices), sourceCount)
    }

    fun queueState(value: JSONObject, expected: ReaderTtsChapterRef): ReaderTtsQueueSnapshot {
        val snapshot = value.getJSONObject("snapshot")
        val chapter = chapter(snapshot.getJSONObject("chapter"))
        check(chapter.sourceId == expected.sourceId && chapter.bookId == expected.bookId &&
            chapter.chapterIndex == expected.chapterIndex) { "TTS_QUEUE_SCOPE" }
        val state = when (snapshot.getString("state")) {
            "idle" -> ReaderTtsQueueState.IDLE
            "playing" -> ReaderTtsQueueState.PLAYING
            "paused" -> ReaderTtsQueueState.PAUSED
            "completed" -> ReaderTtsQueueState.COMPLETED
            "stopped" -> ReaderTtsQueueState.STOPPED
            else -> error("TTS_QUEUE_STATE")
        }
        val total = snapshot.getInt("totalSlices").also { check(it >= 0) { "TTS_QUEUE_TOTAL" } }
        val completed = snapshot.getInt("completedSlices").also { check(it in 0..total) { "TTS_QUEUE_COMPLETED" } }
        val current = if (!snapshot.has("currentSliceIndex") || snapshot.isNull("currentSliceIndex")) null
            else snapshot.getInt("currentSliceIndex").also { check(it in 0 until total) { "TTS_QUEUE_CURSOR" } }
        val statuses = snapshot.optJSONArray("sliceStatuses")?.let { rows ->
            check(rows.length() == 0 || rows.length() == total) { "TTS_QUEUE_STATUS_COUNT" }
            (0 until rows.length()).map { index ->
                when (rows.getString(index)) {
                    "pending" -> ReaderTtsSliceStatus.PENDING
                    "speaking" -> ReaderTtsSliceStatus.SPEAKING
                    "done" -> ReaderTtsSliceStatus.DONE
                    "skipped" -> ReaderTtsSliceStatus.SKIPPED
                    "failed" -> ReaderTtsSliceStatus.FAILED
                    else -> error("TTS_SLICE_STATUS:$index")
                }
            }
        } ?: emptyList()
        return ReaderTtsQueueSnapshot(chapter, state, current, total, completed, Collections.unmodifiableList(statuses))
    }

    fun transition(value: JSONObject, expected: ReaderTtsChapterRef): ReaderTtsChapterTransition {
        val transition = value.getJSONObject("transition")
        val current = chapter(transition.getJSONObject("current"))
        check(current.sourceId == expected.sourceId && current.bookId == expected.bookId &&
            current.chapterIndex == expected.chapterIndex) { "TTS_TRANSITION_SCOPE" }
        val next = if (!transition.has("next") || transition.isNull("next")) null else chapter(transition.getJSONObject("next"))
        next?.let {
            check(it.sourceId == current.sourceId && it.bookId == current.bookId && it.chapterIndex > current.chapterIndex) {
                "TTS_TRANSITION_NEXT"
            }
        }
        val drain = drainBehaviors[transition.optString("drainBehavior", ReaderTtsDrainBehavior.STOP_ON_BOUNDARY.wire)]
            ?: error("TTS_DRAIN_BEHAVIOR")
        return ReaderTtsChapterTransition(current, next, drain)
    }
}
