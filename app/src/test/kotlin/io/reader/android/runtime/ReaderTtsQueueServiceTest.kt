package io.reader.android.runtime

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Assert.assertThrows

/** Queue-control transport only; no Android speech, Native device or audio side effect. */
class ReaderTtsQueueServiceTest {
    private val chapter = ReaderTtsChapterRef("src-a", "book-a", 2)
    private val plan = ReaderTtsSlicePlan(chapter, ReaderTtsSlicingStrategy.PARAGRAPH, listOf(
        ReaderTtsSlice(0, "第一段", 0, 3, 0), ReaderTtsSlice(1, "第二段", 3, 6, 1),
    ), 6)

    @Test
    fun queueControlsDispatchExactCoreMethodsAndBindPlan() {
        val methods = mutableListOf<String>()
        val service = ReaderTtsService(fakeTransport { method, params ->
            methods += method
            if (method == "tts.queue.play") {
                assertEquals(1, params.getInt("startSliceIndex"))
                assertEquals(2, params.getJSONObject("plan").getJSONArray("slices").length())
            }
            val value = queueResult()
            when (method) {
                "tts.queue.set-rate" -> value.put("rate", params.getInt("rate"))
                "tts.queue.report-callback" -> value.put("callbackDisposition", "applied").put("failureAction", "skip")
            }
            value
        })
        assertEquals(ReaderTtsQueueState.PLAYING, service.queuePlay(plan, 1).state)
        service.queuePause(chapter)
        service.queueResume(chapter)
        service.queueStop(chapter)
        service.queueNext(chapter)
        service.queuePrev(chapter)
        service.queueSeek(chapter, 1)
        service.queueSkip(chapter)
        assertEquals(7, service.queueSetRate(chapter, 7).rate)
        service.queueReportStatus(chapter, 1, ReaderTtsSliceStatus.DONE)
        val callback = service.queueReportCallback(chapter, 1, ReaderTtsSliceStatus.FAILED, "callback-1",
            ReaderTtsFailurePolicy.SKIP, 2)
        assertEquals(ReaderTtsCallbackDisposition.APPLIED, callback.callbackDisposition)
        assertEquals(ReaderTtsFailureAction.SKIP, callback.failureAction)
        assertEquals(listOf("tts.queue.play", "tts.queue.pause", "tts.queue.resume", "tts.queue.stop",
            "tts.queue.next", "tts.queue.prev", "tts.queue.seek", "tts.queue.skip", "tts.queue.set-rate",
            "tts.queue.report-status", "tts.queue.report-callback"), methods)
    }

    @Test
    fun queueControlsRejectInvalidHostInputsBeforeTransport() {
        val methods = mutableListOf<String>()
        val service = ReaderTtsService(fakeTransport { method, _ -> methods += method; queueResult() })
        assertThrows(IllegalStateException::class.java) { service.queuePlay(plan, 2) }
        assertThrows(IllegalStateException::class.java) { service.queueSeek(chapter, -1) }
        assertThrows(IllegalStateException::class.java) { service.queueSetRate(chapter, 0) }
        assertThrows(IllegalStateException::class.java) { service.queueReportStatus(chapter, 0, ReaderTtsSliceStatus.PENDING) }
        assertThrows(IllegalStateException::class.java) { service.queueReportCallback(chapter, 0, ReaderTtsSliceStatus.SKIPPED, "") }
        assertTrue(methods.isEmpty())
    }

    private fun queueResult() = JSONObject().put("snapshot", JSONObject()
        .put("chapter", JSONObject().put("sourceId", chapter.sourceId).put("bookId", chapter.bookId).put("chapterIndex", chapter.chapterIndex))
        .put("state", "playing").put("currentSliceIndex", 0).put("totalSlices", 2).put("completedSlices", 0)
        .put("sliceStatuses", JSONArray().put("speaking").put("pending")))

    private fun fakeTransport(response: (String, JSONObject) -> JSONObject) = object : CoreTransport {
        override val session = "tts-queue-test"
        override fun request(method: String, params: JSONObject, timeoutMs: Long, beforeSend: (Long) -> Unit,
            terminal: ((JSONObject) -> Unit)?): JSONObject = response(method, params)
        override fun drainOriginalTerminal(requestId: Long, timeoutMs: Long) = error("NO_DRAIN")
        override fun close() = error("NO_CLOSE")
    }
}
