package io.reader.android.runtime

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.json.JSONException

/** Protocol-only TTS DTO admission. No Android speech engine, Core or device is used. */
class ReaderTtsPolicyTest {
    private val chapter = ReaderTtsChapterRef("src-a", "book-a", 2, "第三章", "https://example.test/3")

    private fun chapterJson(value: ReaderTtsChapterRef = chapter) = JSONObject()
        .put("sourceId", value.sourceId).put("bookId", value.bookId).put("chapterIndex", value.chapterIndex)
        .put("chapterTitle", value.chapterTitle).put("chapterUrl", value.chapterUrl)

    @Test
    fun slicePlanPreservesCoreTextAndScalarRanges() {
        val result = ReaderTtsPolicy.plan(JSONObject().put("plan", JSONObject()
            .put("chapter", chapterJson())
            .put("strategy", "paragraph-then-sentence")
            .put("sourceCharCount", 13)
            .put("slices", JSONArray()
                .put(JSONObject().put("index", 0).put("text", "第一段。").put("charStart", 0).put("charEnd", 4).put("paragraphIndex", 0))
                .put(JSONObject().put("index", 1).put("text", "第二段。").put("charStart", 4).put("charEnd", 8).put("paragraphIndex", 1)))), chapter, 13)
        assertEquals(ReaderTtsSlicingStrategy.PARAGRAPH_THEN_SENTENCE, result.strategy)
        assertEquals(listOf("第一段。", "第二段。"), result.slices.map { it.text })
        assertEquals(4, result.slices[1].charStart)
        assertEquals(13, result.sourceCharCount)
    }

    @Test
    fun slicePlanRejectsForeignChapterAndInvalidRanges() {
        val foreign = chapter.copy(bookId = "other")
        val value = JSONObject().put("plan", JSONObject()
            .put("chapter", chapterJson(foreign)).put("sourceCharCount", 4)
            .put("slices", JSONArray().put(JSONObject().put("index", 0).put("text", "text")
                .put("charStart", 0).put("charEnd", 4).put("paragraphIndex", 0))))
        assertThrows(IllegalStateException::class.java) { ReaderTtsPolicy.plan(value, chapter, 4) }

        val badRange = JSONObject().put("plan", JSONObject()
            .put("chapter", chapterJson()).put("sourceCharCount", 4)
            .put("slices", JSONArray().put(JSONObject().put("index", 0).put("text", "text")
                .put("charStart", 2).put("charEnd", 2).put("paragraphIndex", 0))))
        assertThrows(IllegalStateException::class.java) { ReaderTtsPolicy.plan(badRange, chapter, 4) }
    }

    @Test
    fun queueSnapshotParsesStateCursorAndStatuses() {
        val value = JSONObject().put("snapshot", JSONObject()
            .put("chapter", chapterJson()).put("state", "playing").put("currentSliceIndex", 1)
            .put("totalSlices", 3).put("completedSlices", 1)
            .put("failurePolicy", "skip").put("consecutiveFailures", 1).put("failureLimit", 3)
            .put("drainBehavior", "advance-to-next").put("restartPolicy", "reset-on-core-restart")
            .put("sliceStatuses", JSONArray().put("done").put("speaking").put("pending")))
        val snapshot = ReaderTtsPolicy.queueState(value, chapter)
        assertEquals(ReaderTtsQueueState.PLAYING, snapshot.state)
        assertEquals(1, snapshot.currentSliceIndex)
        assertEquals(ReaderTtsFailurePolicy.SKIP, snapshot.failurePolicy)
        assertEquals(ReaderTtsDrainBehavior.ADVANCE_TO_NEXT, snapshot.drainBehavior)
        assertEquals(listOf(ReaderTtsSliceStatus.DONE, ReaderTtsSliceStatus.SPEAKING, ReaderTtsSliceStatus.PENDING), snapshot.sliceStatuses)

        val idle = JSONObject().put("snapshot", JSONObject().put("chapter", chapterJson())
            .put("state", "idle").put("totalSlices", 0).put("completedSlices", 0))
        assertNull(ReaderTtsPolicy.queueState(idle, chapter).currentSliceIndex)
    }

    @Test
    fun queueSnapshotRejectsCursorAndStatusCountDrift() {
        val badCursor = JSONObject().put("snapshot", JSONObject().put("chapter", chapterJson())
            .put("state", "playing").put("currentSliceIndex", 3).put("totalSlices", 3).put("completedSlices", 0))
        assertThrows(IllegalStateException::class.java) { ReaderTtsPolicy.queueState(badCursor, chapter) }
        val badCount = JSONObject().put("snapshot", JSONObject().put("chapter", chapterJson())
            .put("state", "playing").put("currentSliceIndex", 0).put("totalSlices", 3).put("completedSlices", 0)
            .put("sliceStatuses", JSONArray().put("speaking")))
        assertThrows(IllegalStateException::class.java) { ReaderTtsPolicy.queueState(badCount, chapter) }
        val badFailurePolicy = JSONObject().put("snapshot", JSONObject().put("chapter", chapterJson())
            .put("state", "idle").put("totalSlices", 0).put("completedSlices", 0).put("failurePolicy", "other"))
        assertThrows(IllegalStateException::class.java) { ReaderTtsPolicy.queueState(badFailurePolicy, chapter) }
    }

    @Test
    fun chapterRejectsExplicitNullOptionalIdentityFields() {
        val malformed = JSONObject().put("sourceId", chapter.sourceId).put("bookId", chapter.bookId)
            .put("chapterIndex", chapter.chapterIndex).put("chapterTitle", JSONObject.NULL)
        assertThrows(IllegalStateException::class.java) { ReaderTtsPolicy.chapter(malformed) }
    }

    @Test
    fun chapterTransitionAdmitsOnlySameBookForwardNext() {
        val next = chapter.copy(chapterIndex = 3, chapterTitle = "第四章")
        val value = JSONObject().put("transition", JSONObject().put("current", chapterJson())
            .put("next", chapterJson(next)).put("drainBehavior", "advance-to-next"))
        val transition = ReaderTtsPolicy.transition(value, chapter)
        assertEquals(ReaderTtsDrainBehavior.ADVANCE_TO_NEXT, transition.drainBehavior)
        assertEquals(3, transition.next?.chapterIndex)

        val terminal = JSONObject().put("transition", JSONObject().put("current", chapterJson()).put("next", JSONObject.NULL))
        assertNull(ReaderTtsPolicy.transition(terminal, chapter).next)

        val backward = chapter.copy(chapterIndex = 1)
        val bad = JSONObject().put("transition", JSONObject().put("current", chapterJson())
            .put("next", chapterJson(backward)))
        assertThrows(IllegalStateException::class.java) { ReaderTtsPolicy.transition(bad, chapter) }
    }

    @Test
    fun serviceBindsCoreMethodsToExactChapterAndDoesNotCreateSpeechSideEffects() {
        val methods = mutableListOf<String>()
        val port = object : CoreTransport {
            override val session = "tts-runtime"
            override fun request(method: String, parameters: JSONObject, timeoutMs: Long, beforeSend: (Long) -> Unit,
                terminal: ((JSONObject) -> Unit)?): JSONObject {
                methods += method
                when (method) {
                    "tts.slice" -> {
                        assertEquals("local", parameters.getJSONObject("chapter").getString("sourceId"))
                        assertEquals(3, parameters.getJSONObject("chapter").getInt("chapterIndex"))
                        return JSONObject().put("plan", JSONObject().put("chapter", chapterJson(ReaderTtsChapterRef("local", "book-a", 3)))
                            .put("sourceCharCount", 2).put("slices", JSONArray().put(JSONObject()
                                .put("index", 0).put("text", "正文").put("charStart", 0).put("charEnd", 2).put("paragraphIndex", 0))))
                    }
                    "tts.queue.status" -> return JSONObject().put("snapshot", JSONObject().put("chapter", chapterJson())
                        .put("state", "idle").put("totalSlices", 0).put("completedSlices", 0))
                    "tts.chapter.plan" -> return JSONObject().put("transition", JSONObject().put("current", chapterJson())
                        .put("next", JSONObject.NULL).put("drainBehavior", "stop-on-boundary"))
                    else -> throw JSONException("unexpected method")
                }
            }
            override fun drainOriginalTerminal(requestId: Long, timeoutMs: Long) = error("NO_DRAIN")
            override fun close() = Unit
        }
        val service = ReaderTtsService(port)
        val local = ReaderChapter("book-a", 3, "第四章", "正文", "body", "processing")
        assertEquals(1, service.slice(local).slices.size)
        assertEquals(ReaderTtsQueueState.IDLE, service.queueStatus(chapter).state)
        assertNull(service.chapterPlan(chapter).next)
        assertEquals(listOf("tts.slice", "tts.queue.status", "tts.chapter.plan"), methods)
    }
}
