package com.reader.android.data.network

import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class NetworkErrorMapperTest {

    @Test
    fun `socket timeout maps to TIMEOUT code`() {
        val snap = NetworkErrorMapper.map(
            SocketTimeoutException("connect timed out"),
            sourceName = "test-source"
        )
        assertEquals("TIMEOUT", snap.codeName)
        assertEquals("test-source", snap.sourceName)
    }

    @Test
    fun `unknown host maps to NETWORK code`() {
        val snap = NetworkErrorMapper.map(
            UnknownHostException("source.example"),
            sourceName = null
        )
        assertEquals("NETWORK", snap.codeName)
    }

    @Test
    fun `generic io exception maps to NETWORK code`() {
        val snap = NetworkErrorMapper.map(
            java.io.IOException("broken pipe"),
            sourceName = "s"
        )
        assertEquals("NETWORK", snap.codeName)
    }

    @Test
    fun `non-io throwable maps to UNKNOWN code`() {
        val snap = NetworkErrorMapper.map(
            IllegalStateException("boom"),
            sourceName = null
        )
        assertEquals("UNKNOWN", snap.codeName)
    }
}
