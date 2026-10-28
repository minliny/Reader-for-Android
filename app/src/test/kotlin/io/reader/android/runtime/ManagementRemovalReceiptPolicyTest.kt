package io.reader.android.runtime

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * UNWIRED policy DTO cases only. No transport, real book, Core mutation, file,
 * database or device is created/deleted. Keys reuse fixed Core's local/other
 * composite-key fixture shape. Hex strings check receipt shape, not Core SHA.
 * Requires the replacement ReaderBookshelfManagement.kt in this same draft.
 */
class ManagementRemovalReceiptPolicyTest {
    private val uuid = "12345678-1234-4234-8123-123456789abc"
    private val a = ShelfBookKey("local", "book")
    private val b = ShelfBookKey("other", "book") // Same bookId, distinct source.
    private val c = ShelfBookKey("local", "second-book")
    private val literal = listOf(a, b, c, a, b, a)

    private fun params(method: String, keys: List<ShelfBookKey> = literal): JSONObject = when (method) {
        "bookshelf.remove" -> JSONObject().put("sourceId", keys.single().sourceId).put("bookId", keys.single().bookId)
        "bookshelf.removeBatch" -> JSONObject().put("targets", JSONArray(ManagementReceiptPolicy.targetsJson(keys)))
        else -> error("Removal fixture only")
    }
    private fun record(method: String, keys: List<ShelfBookKey> = literal): JSONObject =
        JSONObject().put("kind", ManagementReceiptPolicy.KIND).put("id", "android-management-$uuid")
            .put("method", method).put("completed", false).put("requestId", 1001L).put("runtimeSession", "original-runtime")
            .put("parametersJson", JSONObject().put("operationId", "$uuid/1").put("sequence", 1)
                .put("mutation", JSONObject().put("method", method).put("params", params(method, keys))).toString())
    private fun binding(sequence: Long = 1): JSONObject = JSONObject().put("operationId", "$uuid/$sequence")
        .put("sequence", sequence).put("payloadFingerprint", "a".repeat(64))
    private fun receipt(method: String, data: JSONObject, outcome: String = "applied"): JSONObject =
        JSONObject().put("binding", binding()).put("method", method).put("outcome", outcome)
            .put("data", data).put("ackFingerprint", "b".repeat(64))
    private fun targets(keys: List<ShelfBookKey>) = JSONArray(ManagementReceiptPolicy.targetsJson(keys))
    private fun batch(): JSONObject = JSONObject().put("requestedCount", 6L).put("uniqueCount", 3L)
        .put("removedTargets", targets(listOf(a, c))).put("missingTargets", targets(listOf(b)))
        .put("duplicateTargets", targets(listOf(a, b, a)))
    private fun query(found: JSONObject? = null, head: JSONObject? = found?.getJSONObject("binding"), allowed: Boolean = false) =
        JSONObject().put("head", head ?: JSONObject.NULL).put("receipt", found ?: JSONObject.NULL).put("canApplySameOriginal", allowed)

    @Test fun literalCompositeKeysOrderAndDuplicatesRemainFrozenWithoutHostNormalization() {
        val originalRecord = record("bookshelf.removeBatch")
        val original = originalRecord.getString("parametersJson")
        val decoded = ManagementReceiptPolicy.parameters(originalRecord)
        val requested = ManagementReceiptPolicy.removalTargets("bookshelf.removeBatch", decoded.getJSONObject("mutation").getJSONObject("params"))
        assertEquals(literal, requested)
        assertEquals(6, requested.size)
        assertFalse(requested[0] == requested[1])
        decoded.getJSONObject("mutation").getJSONObject("params").getJSONArray("targets").getJSONObject(0).put("sourceId", "changed")
        assertEquals(original, originalRecord.getString("parametersJson"))
        assertEquals(literal, ManagementReceiptPolicy.removalTargets("bookshelf.removeBatch",
            ManagementReceiptPolicy.parameters(originalRecord).getJSONObject("mutation").getJSONObject("params")))
        assertThrows(UnsupportedOperationException::class.java) { (requested as MutableList<ShelfBookKey>).clear() }
        val retainedLiteral = ShelfBookKey(" local ", " book ")
        assertEquals(listOf(retainedLiteral), ManagementReceiptPolicy.removalTargets("bookshelf.remove", params("bookshelf.remove", listOf(retainedLiteral))))
        assertThrows(IllegalStateException::class.java) {
            ManagementReceiptPolicy.parameters(record("bookshelf.removeBatch", emptyList()))
        }
    }

    @Test fun originalRemovedFalseMissingPartitionAndRejectedReceiptNeverBecomeNewFacts() {
        val single = record("bookshelf.remove", listOf(a))
        val noOp = receipt("bookshelf.remove", JSONObject().put("removed", false))
        val first = ManagementReceiptPolicy.receipt(single, noOp)
        assertEquals(ManagementConfirmation.APPLIED, first.confirmation)
        assertEquals(ShelfSingleRemoval(a, false), first.removal)
        assertNull(first.coreRemovalReceipt) // A policy decode is not proof of Host durable persistence.
        single.put("receiptJson", noOp.toString())
        assertThrows(IllegalStateException::class.java) {
            ManagementReceiptPolicy.receipt(single, receipt("bookshelf.remove", JSONObject().put("removed", true)))
        }
        val batchRecord = record("bookshelf.removeBatch")
        val originalReceipt = receipt("bookshelf.removeBatch", batch())
        val original = ManagementReceiptPolicy.receipt(batchRecord, originalReceipt).batchRemoval!!
        assertEquals(6L, original.requestedCount)
        assertEquals(3L, original.uniqueCount)
        assertEquals(listOf(a, c), original.removedTargets)
        assertEquals(listOf(b), original.missingTargets)
        assertEquals(listOf(a, b, a), original.duplicateTargets)
        assertThrows(UnsupportedOperationException::class.java) { (original.removedTargets as MutableList<ShelfBookKey>).clear() }
        batchRecord.put("receiptJson", originalReceipt.toString())
        val changedPartition = batch().put("removedTargets", targets(listOf(b, c))).put("missingTargets", targets(listOf(a)))
        assertThrows(IllegalStateException::class.java) {
            ManagementReceiptPolicy.receipt(batchRecord, receipt("bookshelf.removeBatch", changedPartition))
        }
        val rejected = receipt("bookshelf.removeBatch", JSONObject().put("error", JSONObject().put("code", "INVALID_PARAMS")
            .put("message", "Original request rejected").put("retryable", false)), "rejected")
        val known = ManagementReceiptPolicy.receipt(record("bookshelf.removeBatch"), rejected)
        assertEquals(ManagementConfirmation.REJECTED, known.confirmation)
        assertEquals("INVALID_PARAMS", known.rejection!!.code)
        assertNull(known.removal)
        assertNull(known.batchRemoval)
        assertNull(known.coreRemovalReceipt)
        val unknown = record("bookshelf.remove", listOf(a))
        val wireError = JSONObject().put("type", "error").put("protocolVersion", 1).put("requestId", 1001L)
            .put("error", JSONObject().put("code", "INVALID_PARAMS").put("message", "No durable receipt").put("retryable", false))
        assertThrows(IllegalStateException::class.java) { ManagementReceiptPolicy.wireReceipt(unknown, wireError) }
        assertTrue(ManagementReceiptPolicy.isActive(unknown))
        assertFalse(unknown.has("receiptJson"))
    }

    @Test fun batchReceiptMustPartitionExactOriginalScopeAndKeepCoreRequestOrderWithoutNumericCoercion() {
        val originalRecord = record("bookshelf.removeBatch")
        val invalid = listOf(
            batch().put("removedTargets", targets(listOf(c, a))), // Reordered first-seen results.
            batch().put("removedTargets", targets(listOf(a, a, c))), // Repeated removed key.
            batch().put("missingTargets", targets(listOf(a))), // Overlap / missing original b.
            batch().put("removedTargets", targets(listOf(a, ShelfBookKey("foreign", "second-book")))),
            batch().put("duplicateTargets", targets(listOf(b, a, a))),
            batch().put("requestedCount", 6.0),
            batch().put("uniqueCount", "3"),
        )
        for (data in invalid) assertThrows(IllegalStateException::class.java) {
            ManagementReceiptPolicy.receipt(originalRecord, receipt("bookshelf.removeBatch", data))
        }
        val sameBookOtherSource = record("bookshelf.removeBatch", listOf(a, b))
        val valid = JSONObject().put("requestedCount", 2).put("uniqueCount", 2).put("removedTargets", targets(listOf(a)))
            .put("missingTargets", targets(listOf(b))).put("duplicateTargets", JSONArray())
        assertEquals(listOf(a), ManagementReceiptPolicy.receipt(sameBookOtherSource, receipt("bookshelf.removeBatch", valid)).batchRemoval!!.removedTargets)
        val single = record("bookshelf.remove", listOf(a))
        val wire = JSONObject().put("protocolVersion", 1).put("requestId", 1002L).put("type", "result")
            .put("data", receipt("bookshelf.remove", JSONObject().put("removed", false)))
        assertThrows(IllegalStateException::class.java) { ManagementReceiptPolicy.wireReceipt(single, wire) }
    }

    @Test fun exactQueryAndKnownOriginalReceiptFenceReplayAcrossTheSharedLatestManagementHead() {
        val originalRecord = record("bookshelf.removeBatch")
        val originalParameters = originalRecord.getString("parametersJson")
        assertFalse(ManagementReceiptPolicy.canReplay(originalRecord, query()))
        assertTrue(ManagementReceiptPolicy.canReplay(originalRecord, query(allowed = true)))
        assertFalse(ManagementReceiptPolicy.canReplay(originalRecord, query(head = binding(2))))
        assertThrows(IllegalStateException::class.java) {
            ManagementReceiptPolicy.canReplay(originalRecord, query(head = binding(2), allowed = true))
        }
        val original = receipt("bookshelf.removeBatch", batch())
        originalRecord.put("receiptJson", original.toString())
        assertFalse(ManagementReceiptPolicy.canReplay(originalRecord, query(allowed = true)))
        assertEquals(3L, ManagementReceiptPolicy.nextSequence(query(head = binding(2)))) // Same group/assign domain head.
        assertEquals(original.toString(), ManagementReceiptPolicy.query(originalRecord, query(original, allowed = true))!!.toString())
        assertTrue(ManagementReceiptPolicy.isActive(originalRecord)) // Receipt observed but not yet query-qualified and persisted by service.
        assertThrows(Exception::class.java) { ManagementReceiptPolicy.query(originalRecord, JSONObject().put("books", JSONArray())) }
        val wrongMethod = JSONObject(original.toString()).put("method", "bookshelf.group.assign")
        assertThrows(IllegalStateException::class.java) { ManagementReceiptPolicy.query(originalRecord, query(wrongMethod)) }
        assertEquals(originalParameters, originalRecord.getString("parametersJson"))
        // Neither a current list nor a missing/superseded latest receipt clears the original intent.
        assertFalse(originalRecord.getBoolean("completed"))
        assertNull(ManagementReceiptPolicy.query(originalRecord, query(head = binding(2))))
        assertFalse(ManagementReceiptPolicy.canReplay(originalRecord, query(head = binding(2))))
    }
}
