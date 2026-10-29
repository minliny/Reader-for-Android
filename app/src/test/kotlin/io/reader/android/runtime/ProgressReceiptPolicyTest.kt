package io.reader.android.runtime

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ProgressReceiptPolicyTest {
    private fun record() = JSONObject().put("requestId", 1001L).put("bookId", "local:original")
        .put("parameters", JSONObject().put("sourceId", "local").put("bookId", "local:original").put("chapterIndex", 2).put("chapterOffset", 17)
            .put("expectedBodyVersion", "body-original").put("expectedProcessingVersion", "processing-original"))

    private fun ack() = JSONObject().put("type", "result").put("requestId", 1001L)
        .put("data", JSONObject().put("stored", true).put("sourceId", "local").put("bookId", "local:original")
            .put("chapterIndex", 2).put("chapterOffset", 17).put("bodyVersion", "body-original")
            .put("processingVersion", "processing-original").put("progressRevision", "original-ack-revision"))

    @Test fun differentRequestCannotConfirmOriginalPosition() {
        assertEquals("original-ack-revision", ProgressReceiptPolicy.revision(record(), ack()))
        assertThrows(IllegalStateException::class.java) {
            ProgressReceiptPolicy.revision(record(), ack().put("requestId", 1002L))
        }
    }

    @Test fun successfulFreshReadCannotConfirmAnOriginalWrite() {
        val read = JSONObject().put("type", "result").put("requestId", 1001L).put("data", JSONObject()
            .put("found", true).put("progressRevision", "newer-read-revision")
            .put("progress", ack().getJSONObject("data")))
        assertThrows(Exception::class.java) { ProgressReceiptPolicy.revision(record(), read) }
    }
}
