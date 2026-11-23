package io.reader.android.runtime

import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Protocol/lineage DTOs only. These inputs never call Core or claim novel decoding/Native Ready. */
class ReaderProgressNewIntentPolicyTest {
    @Test fun completeColdOriginalRetainsRawAndSameRuntimeNeverQualifies() {
        val raw = original().toString().toByteArray(Charsets.UTF_8)
        val snapshot = ReaderProgressOriginalSnapshot.from(raw)
        ReaderProgressNewIntentPolicy.qualify(snapshot, currentRuntime, chapter())
        assertArrayEquals(raw, snapshot.rawCopy())
        assertEquals(original().getString("id"), snapshot.identity().id)
        assertEquals(ProgressJournalCandidatePolicy.hash(raw), snapshot.identity().recordSha256)
        assertEquals(raw.size, snapshot.identity().recordBytes)
        assertFalse(ProgressJournalCandidatePolicy.parse(snapshot.rawCopy()).getBoolean("completed"))
        assertFalse(ProgressJournalCandidatePolicy.parse(snapshot.rawCopy()).has("wireTerminal"))
        val denied = assertThrows(IllegalStateException::class.java) {
            ReaderProgressNewIntentPolicy.qualify(snapshot, oldRuntime, chapter())
        }
        assertEquals("PROGRESS_INTENT_NOT_COLD_ORIGINAL", denied.message)
        assertArrayEquals(raw, snapshot.rawCopy())
        assertTrue(ReaderProgressNewIntentPolicy.sameOriginals(listOf(snapshot), listOf(ReaderProgressOriginalSnapshot.from(raw))))
        val changed = original().put("requestId", 1012L).toString().toByteArray(Charsets.UTF_8)
        assertFalse(ReaderProgressNewIntentPolicy.sameOriginals(listOf(snapshot), listOf(ReaderProgressOriginalSnapshot.from(changed))))
    }

    @Test fun oldReceiptFieldsWrongScopeAndNonIntegralDispatchCannotBecomeColdAuthority() {
        val untouched = original().toString().toByteArray(Charsets.UTF_8)
        val mutations: List<(JSONObject) -> Unit> = listOf(
            { it.put("wireTerminal", JSONObject()) },
            { it.put("completed", true) },
            { it.put("journalSchema", 1.5) },
            { it.put("requestId", 1011.5) },
            { it.getJSONObject("parameters").put("expectedBodyVersion", "c".repeat(64)) },
            { it.getJSONObject("parameters").put("chapterIndex", 4) },
            { it.getJSONObject("parameters").put("chapterOffset", 3) },
            { it.getJSONObject("parameters").put("chapterOffset", 1) }, // Same range, producer ratio no longer matches.
        )
        for (mutate in mutations) {
            val record = JSONObject(untouched.toString(Charsets.UTF_8)); mutate(record)
            val candidate = ReaderProgressOriginalSnapshot.from(record.toString().toByteArray(Charsets.UTF_8))
            assertThrows(IllegalStateException::class.java) { ReaderProgressNewIntentPolicy.qualify(candidate, currentRuntime, chapter()) }
        }
        val valid = ReaderProgressOriginalSnapshot.from(untouched)
        assertThrows(IllegalStateException::class.java) {
            ReaderProgressNewIntentPolicy.qualify(valid, currentRuntime, chapter().copy(processingVersion = "d".repeat(64)))
        }
        assertArrayEquals(untouched, valid.rawCopy())
    }

    private fun chapter() = ReaderChapter(book, 3, "protocol scope", "标量", body, processing,
        "22222222-2222-4222-8222-222222222222")
    private fun original() = JSONObject().put("journalSchema", 1).put("kind", "progress")
        .put("id", "android-progress-11111111-1111-4111-8111-111111111111").put("bookId", book)
        .put("completed", false).put("requestId", 1011L).put("runtimeSession", oldRuntime)
        .put("parameters", JSONObject().put("sourceId", "local").put("bookId", book).put("chapterIndex", 3)
            .put("chapterOffset", 0).put("chapterProgress", 0).put("expectedBodyVersion", body)
            .put("expectedProcessingVersion", processing).put("expectedProgressRevision", "reader-progress-v1:" + "e".repeat(64)))
    private val book = "local:" + "a".repeat(64)
    private val body = "a".repeat(64)
    private val processing = "b".repeat(64)
    private val oldRuntime = "33333333-3333-4333-8333-333333333333"
    private val currentRuntime = "44444444-4444-4444-8444-444444444444"
}
