package io.reader.android.runtime

import org.json.JSONObject

/**
 * Source-only Core TTS adapter. It owns no Android speech engine and no
 * playback lifecycle; those remain platform-host responsibilities.
 */
internal class ReaderTtsService(private val transport: CoreTransport) : ReaderTtsPlaybackCommands {
    fun slice(chapter: ReaderTtsChapterRef, content: String,
        strategy: ReaderTtsSlicingStrategy = ReaderTtsSlicingStrategy.PARAGRAPH): ReaderTtsSlicePlan {
        val data = transport.request("tts.slice", JSONObject()
            .put("chapter", chapter.toJson())
            .put("content", content)
            .put("strategy", strategy.wire))
        return ReaderTtsPolicy.plan(data, chapter, content.codePointCount(0, content.length))
    }

    fun slice(chapter: ReaderChapter, strategy: ReaderTtsSlicingStrategy = ReaderTtsSlicingStrategy.PARAGRAPH): ReaderTtsSlicePlan {
        return slice(chapter.reference(), chapter.text, strategy)
    }

    fun queueStatus(chapter: ReaderTtsChapterRef): ReaderTtsQueueSnapshot {
        val data = transport.request("tts.queue.status", JSONObject().put("chapter", chapter.toJson()))
        return ReaderTtsPolicy.queueState(data, chapter)
    }

    override fun queuePlay(plan: ReaderTtsSlicePlan, startSliceIndex: Int): ReaderTtsQueueSnapshot {
        check(plan.slices.isNotEmpty()) { "TTS_QUEUE_PLAN_EMPTY" }
        check(startSliceIndex in plan.slices.indices) { "TTS_QUEUE_START_INDEX" }
        val data = transport.request("tts.queue.play", JSONObject()
            .put("plan", plan.toJson()).put("startSliceIndex", startSliceIndex))
        return ReaderTtsPolicy.queueState(data, plan.chapter, plan.slices.size)
    }

    override fun queuePause(chapter: ReaderTtsChapterRef): ReaderTtsQueueSnapshot =
        queueControl("tts.queue.pause", chapter)

    override fun queueResume(chapter: ReaderTtsChapterRef): ReaderTtsQueueSnapshot =
        queueControl("tts.queue.resume", chapter)

    override fun queueStop(chapter: ReaderTtsChapterRef): ReaderTtsQueueSnapshot =
        queueControl("tts.queue.stop", chapter)

    fun queueNext(chapter: ReaderTtsChapterRef): ReaderTtsQueueSnapshot =
        queueControl("tts.queue.next", chapter)

    fun queuePrev(chapter: ReaderTtsChapterRef): ReaderTtsQueueSnapshot =
        queueControl("tts.queue.prev", chapter)

    fun queueSeek(chapter: ReaderTtsChapterRef, sliceIndex: Int): ReaderTtsQueueSnapshot {
        check(sliceIndex >= 0) { "TTS_QUEUE_SLICE_INDEX" }
        val data = transport.request("tts.queue.seek", chapterParams(chapter).put("sliceIndex", sliceIndex))
        return ReaderTtsPolicy.queueState(data, chapter).also { requireSlice(it, sliceIndex) }
    }

    fun queueSkip(chapter: ReaderTtsChapterRef): ReaderTtsQueueSnapshot =
        queueControl("tts.queue.skip", chapter)

    override fun queueSetRate(chapter: ReaderTtsChapterRef, rate: Int): ReaderTtsQueueSetRateResult {
        check(rate in 1..10) { "TTS_QUEUE_RATE" }
        val data = transport.request("tts.queue.set-rate", chapterParams(chapter).put("rate", rate))
        val snapshot = ReaderTtsPolicy.queueState(data, chapter)
        val returnedRate = data.getInt("rate").also { check(it == rate && it in 1..10) { "TTS_QUEUE_RATE_RESULT" } }
        return ReaderTtsQueueSetRateResult(snapshot, returnedRate)
    }

    fun queueReportStatus(chapter: ReaderTtsChapterRef, sliceIndex: Int,
        status: ReaderTtsSliceStatus): ReaderTtsQueueSnapshot {
        check(sliceIndex >= 0) { "TTS_QUEUE_SLICE_INDEX" }
        check(status in setOf(ReaderTtsSliceStatus.SPEAKING, ReaderTtsSliceStatus.DONE, ReaderTtsSliceStatus.FAILED)) {
            "TTS_QUEUE_REPORT_STATUS"
        }
        val data = transport.request("tts.queue.report-status", chapterParams(chapter)
            .put("sliceIndex", sliceIndex).put("status", status.wire))
        return ReaderTtsPolicy.queueState(data, chapter).also { requireSlice(it, sliceIndex) }
    }

    override fun queueReportCallback(chapter: ReaderTtsChapterRef, sliceIndex: Int,
        status: ReaderTtsSliceStatus, callbackId: String,
        failurePolicy: ReaderTtsFailurePolicy,
        failureLimit: Int): ReaderTtsQueueCallbackResult {
        check(sliceIndex >= 0) { "TTS_QUEUE_SLICE_INDEX" }
        check(status in setOf(ReaderTtsSliceStatus.SPEAKING, ReaderTtsSliceStatus.DONE, ReaderTtsSliceStatus.FAILED)) {
            "TTS_QUEUE_REPORT_CALLBACK"
        }
        check(callbackId.isNotBlank()) { "TTS_CALLBACK_ID" }
        check(failureLimit > 0) { "TTS_FAILURE_LIMIT" }
        val data = transport.request("tts.queue.report-callback", chapterParams(chapter)
            .put("sliceIndex", sliceIndex).put("status", status.wire).put("callbackId", callbackId)
            .put("failurePolicy", failurePolicy.wire).put("failureLimit", failureLimit))
        return ReaderTtsPolicy.callback(data, chapter).also { requireSlice(it.snapshot, sliceIndex) }
    }

    fun chapterPlan(
        chapter: ReaderTtsChapterRef,
        drainBehavior: ReaderTtsDrainBehavior = ReaderTtsDrainBehavior.STOP_ON_BOUNDARY,
        nextChapter: ReaderTtsChapterRef? = null,
    ): ReaderTtsChapterTransition {
        val params = JSONObject().put("chapter", chapter.toJson()).put("drainBehavior", drainBehavior.wire)
        nextChapter?.let { params.put("nextChapter", it.toJson()) }
        return ReaderTtsPolicy.transition(transport.request("tts.chapter.plan", params), chapter)
    }

    fun chapterPlan(chapter: ReaderTtsChapterRef, next: ReaderTtsChapterRef?,
        drainBehavior: ReaderTtsDrainBehavior): ReaderTtsChapterTransition =
        chapterPlan(chapter, drainBehavior, next)

    private fun ReaderChapter.reference() = ReaderTtsChapterRef(
        sourceId = sourceId,
        bookId = bookId,
        chapterIndex = index,
        chapterTitle = title,
    )

    private fun ReaderTtsChapterRef.toJson() = JSONObject()
        .put("sourceId", sourceId)
        .put("bookId", bookId)
        .put("chapterIndex", chapterIndex)
        .apply {
            chapterTitle?.let { put("chapterTitle", it) }
            chapterUrl?.let { put("chapterUrl", it) }
        }

    private fun chapterParams(chapter: ReaderTtsChapterRef) = JSONObject().put("chapter", chapter.toJson())

    private fun queueControl(method: String, chapter: ReaderTtsChapterRef): ReaderTtsQueueSnapshot =
        ReaderTtsPolicy.queueState(transport.request(method, chapterParams(chapter)), chapter)

    private fun requireSlice(snapshot: ReaderTtsQueueSnapshot, sliceIndex: Int) {
        check(sliceIndex in 0 until snapshot.totalSlices) { "TTS_QUEUE_SLICE_INDEX" }
    }

    private fun ReaderTtsSlicePlan.toJson() = JSONObject()
        .put("chapter", chapter.toJson())
        .put("strategy", strategy.wire)
        .put("sourceCharCount", sourceCharCount)
        .put("slices", org.json.JSONArray().also { rows ->
            slices.forEach { slice -> rows.put(JSONObject()
                .put("index", slice.index).put("text", slice.text)
                .put("charStart", slice.charStart).put("charEnd", slice.charEnd)
                .put("paragraphIndex", slice.paragraphIndex)) }
        })

    private val ReaderTtsSliceStatus.wire: String
        get() = name.lowercase()
}
