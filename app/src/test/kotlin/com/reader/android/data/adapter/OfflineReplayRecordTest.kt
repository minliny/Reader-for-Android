package com.reader.android.data.adapter

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineReplayRecordTest {

    @Test
    fun `valid record passes validation`() {
        val record = OfflineReplayRecord(
            sourceUrl = "http://source.com",
            requestUrl = "http://source.com/search?q=test",
            responseHtml = "<html>results</html>"
        )
        assertTrue(OfflineReplayContract.validate(record))
    }

    @Test
    fun `record with blank sourceUrl fails validation`() {
        val record = OfflineReplayRecord("", "url", "html")
        assertFalse(OfflineReplayContract.validate(record))
    }

    @Test
    fun `record with blank responseHtml fails validation`() {
        val record = OfflineReplayRecord("src", "url", "")
        assertFalse(OfflineReplayContract.validate(record))
    }

    @Test
    fun `fresh record is valid for replay`() {
        val record = OfflineReplayRecord("src", "url", "html")
        assertTrue(OfflineReplayContract.isValidForReplay(record))
    }

    @Test
    fun `isJsResult flag defaults to false`() {
        val record = OfflineReplayRecord("src", "url", "html")
        assertFalse(record.isJsResult)
    }
}
