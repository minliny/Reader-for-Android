package io.reader.android.runtime

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BookmarkReceiptPolicyTest {
    private val bookId = "local:" + "a".repeat(64)
    private val operationId = "12345678-1234-1234-1234-123456789abc/8"

    private fun binding(sequence: Long = 8) = JSONObject().put("operationId", operationId.substringBefore('/') + "/$sequence")
        .put("sequence", sequence).put("payloadFingerprint", "b".repeat(64))

    private fun scope() = JSONObject().put("sourceId", "local").put("bookId", bookId).put("chapterIndex", 2)
        .put("bodyVersion", "body-original").put("processingVersion", "processing-original")

    private fun parameters() = JSONObject().put("operationId", operationId).put("sequence", 8)
        .put("positionScope", scope()).put("bookName", "Original").put("bookAuthor", "Author")
        .put("chapterIndex", 2).put("chapterPos", 17).put("chapterName", "Chapter")
        .put("bookText", "Selected original").put("content", "Original note")

    private fun record() = JSONObject().put("action", "create").put("parameters", parameters())

    private fun row() = JSONObject().put("positionScope", scope()).put("time", 1_800_000_000_000L)
        .put("bookName", "Original").put("bookAuthor", "Author").put("chapterIndex", 2).put("chapterPos", 17)
        .put("chapterName", "Chapter").put("bookText", "Selected original").put("content", "Original note")

    private fun receipt() = JSONObject().put("binding", binding()).put("phase", "committed")
        .put("outcome", JSONObject().put("action", "create").put("result", JSONObject().put("bookmark", row())))

    @Test fun sequenceComesOnlyFromValidatedPermanentHead() {
        assertEquals(1L, BookmarkReceiptPolicy.nextSequence(JSONObject().put("head", JSONObject.NULL)))
        assertEquals(9L, BookmarkReceiptPolicy.nextSequence(JSONObject().put("head", binding())))
        assertThrows(IllegalStateException::class.java) { BookmarkReceiptPolicy.nextSequence(JSONObject()) }
        assertThrows(IllegalStateException::class.java) {
            BookmarkReceiptPolicy.nextSequence(JSONObject().put("head", binding().put("sequence", 7)))
        }
        assertThrows(IllegalStateException::class.java) {
            BookmarkReceiptPolicy.nextSequence(JSONObject().put("head", binding(9_007_199_254_740_991L)))
        }
    }

    @Test fun onlyExactOperationReceiptCanConfirmOriginal() {
        BookmarkReceiptPolicy.receipt(record(), receipt())
        for (field in listOf("operationId", "sequence", "payloadFingerprint")) {
            val changed = receipt()
            changed.getJSONObject("binding").put(field, when (field) {
                "operationId" -> "87654321-1234-1234-1234-123456789abc/8"
                "sequence" -> 9
                else -> "INVALID"
            })
            assertThrows(IllegalStateException::class.java) { BookmarkReceiptPolicy.receipt(record(), changed) }
        }
        val differentAction = receipt().also { it.getJSONObject("outcome").put("action", "delete") }
        assertThrows(IllegalStateException::class.java) { BookmarkReceiptPolicy.receipt(record(), differentAction) }
    }

    @Test fun createRequiresExactFullScopeAndScalar() {
        for (field in listOf("sourceId", "bookId", "chapterIndex", "bodyVersion", "processingVersion")) {
            val changed = receipt()
            changed.getJSONObject("outcome").getJSONObject("result").getJSONObject("bookmark").getJSONObject("positionScope")
                .put(field, if (field == "chapterIndex") 3 else "different")
            assertThrows(Exception::class.java) { BookmarkReceiptPolicy.receipt(record(), changed) }
        }
        for (field in listOf("chapterPos", "bookName", "bookAuthor", "chapterName", "bookText", "content")) {
            val changed = receipt()
            changed.getJSONObject("outcome").getJSONObject("result").getJSONObject("bookmark")
                .put(field, if (field == "chapterPos") 18 else "different")
            assertThrows(IllegalStateException::class.java) { BookmarkReceiptPolicy.receipt(record(), changed) }
        }
    }

    @Test fun deleteRetainsOriginalFalseInsteadOfFreshListState() {
        val original = JSONObject().put("time", 9).put("deleted", false)
        val record = JSONObject().put("action", "delete").put("parameters", JSONObject().put("operationId", operationId)
            .put("sequence", 8).put("time", 9)).put("mutationResult", original)
        val receipt = JSONObject().put("binding", binding()).put("phase", "committed")
            .put("outcome", JSONObject().put("action", "delete").put("result", original))
        assertFalse(BookmarkReceiptPolicy.receipt(record, receipt).getBoolean("deleted"))
        val changed = JSONObject(receipt.toString()).also { it.getJSONObject("outcome").getJSONObject("result").put("deleted", true) }
        assertThrows(IllegalStateException::class.java) { BookmarkReceiptPolicy.receipt(record, changed) }
        assertThrows(Exception::class.java) {
            BookmarkReceiptPolicy.receipt(record, JSONObject().put("bookmarks", org.json.JSONArray()))
        }
    }

    @Test fun settlementRequiresOriginalFingerprintAndSettledPhase() {
        val record = record().put("receipt", receipt())
            .put("mutationResult", receipt().getJSONObject("outcome").getJSONObject("result"))
        val settled = receipt().put("phase", "settled")
        BookmarkReceiptPolicy.receipt(record, settled, settled = true)
        assertThrows(IllegalStateException::class.java) { BookmarkReceiptPolicy.receipt(record, receipt(), settled = true) }
        val changed = JSONObject(settled.toString()).also { it.getJSONObject("binding").put("payloadFingerprint", "c".repeat(64)) }
        assertThrows(IllegalStateException::class.java) { BookmarkReceiptPolicy.receipt(record, changed, settled = true) }
        val differentTime = JSONObject(settled.toString()).also {
            it.getJSONObject("outcome").getJSONObject("result").getJSONObject("bookmark").put("time", 1_800_000_000_001L)
        }
        assertThrows(IllegalStateException::class.java) { BookmarkReceiptPolicy.receipt(record, differentTime, settled = true) }
    }

    @Test fun onlyExactCreatePositionStaleAckIsKnownPrewriteRejection() {
        val record = record().put("requestId", 1001L).put("stage", "mutation")
        fun error(code: String = "INVALID_PARAMS", reason: String = "POSITION_CONTEXT_STALE", originalPending: Boolean = false) =
            JSONObject().put("requestId", 1001L).put("type", "error").put("error", JSONObject().put("code", code)
                .put("details", JSONObject().put("reason", reason).put("originalPending", originalPending)))
        assertTrue(BookmarkReceiptPolicy.definiteRejection(record, error()))
        assertFalse(BookmarkReceiptPolicy.definiteRejection(JSONObject(record.toString()).put("action", "delete"), error()))
        assertFalse(BookmarkReceiptPolicy.definiteRejection(JSONObject(record.toString()).put("stage", "settle"), error()))
        assertFalse(BookmarkReceiptPolicy.definiteRejection(record, error(originalPending = true)))
        assertFalse(BookmarkReceiptPolicy.definiteRejection(record, error(code = "INTERNAL")))
        assertFalse(BookmarkReceiptPolicy.definiteRejection(record, error(reason = "UNKNOWN_REASON")))
        assertFalse(BookmarkReceiptPolicy.definiteRejection(record, error(reason = "PROCESSING_CONTEXT_STALE")))
        assertFalse(BookmarkReceiptPolicy.definiteRejection(record,
            error().also { it.getJSONObject("error").remove("details") }))
        assertThrows(IllegalStateException::class.java) {
            BookmarkReceiptPolicy.definiteRejection(record, error().put("requestId", 1002L))
        }
    }
}
