package com.reader.android.data.network

data class RequestHeaders(
    val customHeaders: Map<String, String> = emptyMap(),
    val userAgent: String = DEFAULT_USER_AGENT,
    val cookieHeader: String? = null
) {
    fun toMap(): Map<String, String> {
        val result = mutableMapOf<String, String>()
        result["User-Agent"] = userAgent
        cookieHeader?.let { result["Cookie"] = it }
        result.putAll(customHeaders)
        return result
    }

    companion object {
        const val DEFAULT_USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

        fun withCookie(cookieString: String) = RequestHeaders(cookieHeader = cookieString)
        fun withUA(ua: String) = RequestHeaders(userAgent = ua)
    }
}
