package com.reader.android.data.adapter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebDavRetryPolicyTest {

    @Test
    fun `backoff increases exponentially`() {
        val policy = RetryPolicy.DEFAULT
        val b0 = policy.backoffForAttempt(0) // 1000ms
        val b1 = policy.backoffForAttempt(1) // 2000ms
        assertTrue(b1 > b0)
    }

    @Test
    fun `backoff capped at max`() {
        val policy = RetryPolicy(maxBackoffMs = 2000)
        val b = policy.backoffForAttempt(10)
        assertEquals(2000, b)
    }

    @Test
    fun `NO_RETRY has zero maxRetries`() {
        assertEquals(0, RetryPolicy.NO_RETRY.maxRetries)
    }

    @Test
    fun `500 errors are retryable`() {
        assertTrue(WebDavErrorMapper.isRetryable(500))
        assertTrue(WebDavErrorMapper.isRetryable(503))
    }

    @Test
    fun `404 is not retryable`() {
        assertFalse(WebDavErrorMapper.isRetryable(404))
    }

    @Test
    fun `statusToMessage maps known codes`() {
        assertEquals("Unauthorized", WebDavErrorMapper.statusToMessage(401))
        assertEquals("Server Error", WebDavErrorMapper.statusToMessage(500))
    }
}
