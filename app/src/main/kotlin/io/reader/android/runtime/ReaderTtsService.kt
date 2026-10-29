package io.reader.android.runtime

import org.json.JSONObject

/**
 * Source-only Core TTS adapter. It owns no Android speech engine and no
 * playback lifecycle; those remain platform-host responsibilities.
 */
internal class ReaderTtsService(private val transport: CoreTransport) {
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
}
