package io.reader.android.runtime

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Protocol/scalar DTO fixtures, not novels, Core execution, AtomicFile commits or network evidence. */
class ReaderOnlineReadingPolicyTest {
    private val body = "b".repeat(64); private val processing = "c".repeat(64)
    private val revision = "reader-progress-v1:" + "d".repeat(64)
    private fun params(source: String) = JSONObject().put("sourceId", source).put("bookId", "same-book-id")
        .put("chapterIndex", 2).put("chapterOffset", 1).put("chapterProgress", 0.25)
        .put("expectedBodyVersion", body).put("expectedProcessingVersion", processing).put("expectedProgressRevision", revision)
    private fun record(source: String) = JSONObject().put("journalSchema", 1).put("kind", "progress")
        .put("id", "android-progress-12345678-1234-1234-1234-123456789abc").put("bookId", "same-book-id")
        .put("parameters", params(source)).put("completed", false).put("requestId", 17).put("runtimeSession", "runtime-original")
    private fun receipt(source: String) = JSONObject().put("protocolVersion", 1).put("requestId", 17).put("deliveryId", 9)
        .put("type", "result").put("data", JSONObject().put("stored", true).put("sourceId", source).put("bookId", "same-book-id")
            .put("chapterIndex", 2).put("chapterOffset", 1).put("chapterProgress", 0.25).put("bodyVersion", body)
            .put("processingVersion", processing).put("progressRevision", revision).put("updatedAt", 123))

    @Test fun differentSourcesWithSameBookIdCannotConfirmOrQualifyAnotherOriginalCandidate() {
        val original = record("source-a"); val event = receipt("source-a")
        assertEquals(revision, ProgressReceiptPolicy.revision(original, event))
        assertThrows(IllegalStateException::class.java) { ProgressReceiptPolicy.revision(original, receipt("source-b")) }
        val candidate = JSONObject(original.toString()).put("wireTerminal", event)
        val qualified = ProgressJournalCandidatePolicy.qualify(original.toString().toByteArray(), candidate.toString().toByteArray())
        assertEquals("source-a", qualified.sourceId)
        assertThrows(IllegalStateException::class.java) {
            ProgressJournalCandidatePolicy.qualify(record("source-b").toString().toByteArray(), candidate.toString().toByteArray())
        }
        val numericSource = record("7"); numericSource.getJSONObject("parameters").put("sourceId", 7)
        assertThrows(IllegalStateException::class.java) { ProgressReceiptPolicy.revision(numericSource, receipt("7")) }
        val numericCandidate = JSONObject(numericSource.toString()).put("wireTerminal", receipt("7"))
        assertThrows(IllegalStateException::class.java) {
            ProgressJournalCandidatePolicy.qualify(numericSource.toString().toByteArray(), numericCandidate.toString().toByteArray())
        }
        val complete = JSONObject(candidate.toString()).put("completed", true)
        assertEquals("source-a", ReaderProgressNewIntentPolicy.confirmed(complete).sourceId)
    }

    @Test fun canonicalSnapshotAndCasObservationMustAgreeBeforePublishingUnicodeRestore() {
        val key = ShelfBookKey("source-a", "same-book-id")
        val scope = JSONObject().put("sourceId", key.sourceId).put("bookId", key.bookId).put("chapterIndex", 2)
            .put("bodyVersion", body).put("processingVersion", processing)
        val stored = JSONObject(scope.toString()).put("chapterOffset", 1).put("updatedAt", 123)
        val entry = JSONObject().put("kind", "ready").put("sourceId", key.sourceId).put("bookId", key.bookId)
            .put("chapterIndex", 2).put("chapterTitle", "protocol metadata").put("positionScope", scope).put("progress", stored)
            .put("content", "A😀B").put("blocks", JSONArray().put(JSONObject().put("kind", "text"))).put("baseUrl", JSONObject.NULL)
        val progress = JSONObject().put("found", true).put("progressRevision", revision).put("progress", JSONObject(stored.toString()))
        val opened = ReaderOnlineReadingPolicy.chapter(key, "source-version", entry, progress, null)
        assertEquals(key, opened.chapter.key); assertEquals(3, opened.chapter.scalarCount); assertEquals(1, opened.restoreScalarOffset)
        assertEquals(ReaderTextPresentationKind.ONLINE_TEXT, opened.chapter.presentationKind)
        progress.getJSONObject("progress").put("chapterOffset", 2)
        assertThrows(IllegalStateException::class.java) { ReaderOnlineReadingPolicy.chapter(key, "source-version", entry, progress, null) }
        progress.getJSONObject("progress").put("chapterOffset", 1).put("sourceId", "source-b")
        assertThrows(IllegalStateException::class.java) { ReaderOnlineReadingPolicy.chapter(key, "source-version", entry, progress, null) }
    }
}
