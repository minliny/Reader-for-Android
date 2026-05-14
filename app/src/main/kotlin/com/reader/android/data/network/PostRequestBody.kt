package com.reader.android.data.network

import java.net.URLEncoder

enum class PostContentType(val mimeType: String) {
    FORM_URLENCODED("application/x-www-form-urlencoded"),
    JSON("application/json"),
    TEXT_PLAIN("text/plain")
}

data class PostRequestBody(
    val contentType: PostContentType = PostContentType.FORM_URLENCODED,
    val fields: Map<String, String> = emptyMap(),
    val rawBody: String? = null
) {
    fun toBodyString(): String {
        return rawBody ?: when (contentType) {
            PostContentType.FORM_URLENCODED -> fields.entries.joinToString("&") { (k, v) ->
                "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
            }
            PostContentType.JSON -> {
                val pairs = fields.entries.joinToString(",") { (k, v) ->
                    "\"${k}\":\"${v}\""
                }
                "{$pairs}"
            }
            PostContentType.TEXT_PLAIN -> fields.entries.joinToString("\n") { (k, v) -> "$k=$v" }
        }
    }

    companion object {
        fun formUrlEncoded(fields: Map<String, String>) = PostRequestBody(
            contentType = PostContentType.FORM_URLENCODED, fields = fields
        )
        fun json(fields: Map<String, String>) = PostRequestBody(
            contentType = PostContentType.JSON, fields = fields
        )
        fun raw(contentType: PostContentType, body: String) = PostRequestBody(
            contentType = contentType, rawBody = body
        )
    }
}
