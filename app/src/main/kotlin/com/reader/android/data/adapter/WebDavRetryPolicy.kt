package com.reader.android.data.adapter

data class RetryPolicy(
    val maxRetries: Int = 3,
    val initialBackoffMs: Long = 1000,
    val backoffMultiplier: Double = 2.0,
    val maxBackoffMs: Long = 30000
) {
    fun backoffForAttempt(attempt: Int): Long {
        val delay = (initialBackoffMs * Math.pow(backoffMultiplier, attempt.toDouble())).toLong()
        return delay.coerceAtMost(maxBackoffMs)
    }

    companion object {
        val DEFAULT = RetryPolicy()
        val AGGRESSIVE = RetryPolicy(maxRetries = 5, initialBackoffMs = 500)
        val NO_RETRY = RetryPolicy(maxRetries = 0)
    }
}

object WebDavErrorMapper {
    fun statusToMessage(statusCode: Int): String = when (statusCode) {
        401 -> "Unauthorized"
        403 -> "Forbidden"
        404 -> "Not Found"
        409 -> "Conflict"
        423 -> "Locked"
        507 -> "Insufficient Storage"
        in 500..599 -> "Server Error"
        else -> "Unknown Error ($statusCode)"
    }

    fun isRetryable(statusCode: Int): Boolean = statusCode in listOf(408, 429) || statusCode in 500..599
}
