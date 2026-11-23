package io.reader.android.runtime

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest

/** Mature decoder/selection with protocol metadata. No UI mirror, real source, HTTP or fsync claim. */
class ReaderSourceJsonDocumentTest {
    @Test fun arrayChoiceRetainsWholeDocumentFingerprintAndCompleteSelectedObject() {
        val first = """{"bookSourceUrl":"protocol-one","bookSourceName":"来源一","enabled":false,"unknown":{"keep":[1,true,"原文"]}}"""
        val second = """{"bookSourceUrl":"protocol-two","bookSourceName":"来源二","ruleSearch":{"bookList":"unchanged"},"extra":[null,{"retain":"全字段"}]}"""
        val raw = ("\uFEFF[\n" + first + ",\n" + second + "\n]").toByteArray(Charsets.UTF_8)
        val preview = ReaderSourceJsonDocument.decode(raw)
        assertEquals(raw.size, preview.documentBytes)
        assertEquals(MessageDigest.getInstance("SHA-256").digest(raw).joinToString("") { "%02x".format(it) }, preview.documentSha256)
        assertArrayEquals(raw, preview.originalDocumentBytes()); assertEquals(2, preview.sourceCount)
        assertEquals(listOf("来源一", "来源二"), preview.entries.map { it.name })
        val selected = preview.select(1)
        assertEquals("protocol-two", selected.sourceId); assertEquals(1, selected.selectedIndex)
        assertEquals(preview.id, selected.documentId); assertEquals(preview.documentSha256, selected.documentSha256)
        assertEquals(raw.size, selected.documentBytes); assertEquals(2, selected.sourceCount)
        assertEquals(JSONObject(second).toString(), selected.selectedDefinitionJson)
        assertEquals("全字段", JSONObject(selected.selectedDefinitionJson).getJSONArray("extra").getJSONObject(1).getString("retain"))
        assertEquals(selected, preview.select(1))
        assertEquals("SELECTED_COMPLETE_OBJECT_SERIALIZATION_NOT_ORIGINAL_DOCUMENT_BYTES", selected.definitionClaim)
        assertThrows(IllegalArgumentException::class.java) { preview.select(-1) }
        assertThrows(IllegalArgumentException::class.java) { preview.select(2) }
        raw[0] = 0; val exposed = preview.originalDocumentBytes(); exposed[0] = 0
        assertNotEquals(0.toByte(), preview.originalDocumentBytes()[0])
        val single = ReaderSourceJsonDocument.decode(first.toByteArray(Charsets.UTF_8))
        assertEquals(1, single.sourceCount); assertEquals(JSONObject(first).toString(), single.select(0).selectedDefinitionJson)
        assertThrows(ReaderSourceDocumentException::class.java) { ReaderSourceJsonDocument.decode("[]".toByteArray()) }
        assertThrows(ReaderSourceDocumentException::class.java) { ReaderSourceJsonDocument.decode("[$first,3]".toByteArray()) }
        assertThrows(ReaderSourceDocumentException::class.java) { ReaderSourceJsonDocument.decode("$first $second".toByteArray()) }
    }
}
