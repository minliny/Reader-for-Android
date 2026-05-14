package com.reader.android.data.adapter

data class RuntimeScope(
    val sourceUrl: String,
    val cookieIsolation: Boolean = true,
    val jsContextIsolation: Boolean = true,
    val keyIsolation: Boolean = true
) {
    companion object {
        fun isolated(sourceUrl: String) = RuntimeScope(sourceUrl)
    }
}
