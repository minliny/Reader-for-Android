package io.reader.android.runtime

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Core transport boundary only; no Native, Android TTS or device is used. */
class ReaderTtsServiceTest {
    private val chapter = ReaderTtsChapterRef("src-a", "book-a", 2)

    @Test
    fun sliceDispatchesExactCoreMethodAndAdmitsPlan() {
        val calls = mutableListOf<Pair<String, JSONObject>>()
        val service = ReaderTtsService(fakeTransport { method, params ->
            calls += method to params
            JSONObject().put("plan", JSONObject().put("chapter", chapterJson())
                .put("strategy", "sentence").put("sourceCharCount", 8)
                .put("slices", JSONArray().put(JSONObject().put("index", 0).put("text", "第一句。")
                    .put("charStart", 0).put("charEnd", 4).put("paragraphIndex", 0))
                    .put(JSONObject().put("index", 1).put("text", "第二句。")
                        .put("charStart", 4).put("charEnd", 8).put("paragraphIndex", 0))))
        })
        val result = service.slice(chapter, "第一句。第二句。", ReaderTtsSlicingStrategy.SENTENCE)
        assertEquals(ReaderTtsSlicingStrategy.SENTENCE, result.strategy)
        assertEquals("tts.slice", calls.single().first)
        assertEquals("第一句。第二句。", calls.single().second.getString("content"))
        assertEquals("sentence", calls.single().second.getString("strategy"))
        assertEquals("src-a", calls.single().second.getJSONObject("chapter").getString("sourceId"))
    }

    @Test
    fun queueStatusAndChapterPlanUseDedicatedReadMethods() {
        val methods = mutableListOf<String>()
        val service = ReaderTtsService(fakeTransport { method, _ ->
            methods += method
            when (method) {
                "tts.queue.status" -> JSONObject().put("snapshot", JSONObject().put("chapter", chapterJson())
                    .put("state", "idle").put("totalSlices", 0).put("completedSlices", 0))
                else -> JSONObject().put("transition", JSONObject().put("current", chapterJson())
                    .put("next", JSONObject.NULL).put("drainBehavior", "stop-on-boundary"))
            }
        })
        assertEquals(ReaderTtsQueueState.IDLE, service.queueStatus(chapter).state)
        assertTrue(service.chapterPlan(chapter).next == null)
        assertEquals(listOf("tts.queue.status", "tts.chapter.plan"), methods)
    }

    private fun chapterJson() = JSONObject().put("sourceId", chapter.sourceId)
        .put("bookId", chapter.bookId).put("chapterIndex", chapter.chapterIndex)

    private fun fakeTransport(response: (String, JSONObject) -> JSONObject) = object : CoreTransport {
        override val session = "tts-test"
        override fun request(method: String, params: JSONObject, timeoutMs: Long, beforeSend: (Long) -> Unit,
            terminal: ((JSONObject) -> Unit)?): JSONObject = response(method, params)
        override fun drainOriginalTerminal(requestId: Long, timeoutMs: Long) = error("NO_DRAIN")
        override fun close() = error("NO_CLOSE")
    }
}
