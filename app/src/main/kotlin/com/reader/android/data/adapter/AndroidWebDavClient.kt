package com.reader.android.data.adapter

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * P3: OkHttp-backed [WebDavClient] that integrates with [WebDavCredentialStore].
 *
 * Each [execute] call:
 *  1. Loads the [WebDavCredential] for [credentialIdentifier] from the
 *     keystore-backed [WebDavCredentialStore] (plaintext password never
 *     persisted — see [AndroidKeystoreCredentialStore]).
 *  2. Maps [WebDavMethod] to an OkHttp request (PROPFIND uses Depth: 1 +
 *     application/xml body so the server returns a 207 multi-status listing).
 *  3. Applies the credential's auth header:
 *     - [AuthMethod.Basic] → `Authorization: Basic <base64(user:pass)>`
 *     - [AuthMethod.Bearer] → `Authorization: Bearer <token>`
 *     - [AuthMethod.Digest] → not implemented here (requires
 *       challenge-response flow); execute() returns 401 so the caller can
 *       surface the failure rather than silently degrading.
 *
 * Tests inject [FakeWebDavClient] (no network); on-device the production
 * wiring lives in [com.reader.android.AppProvider].
 */
class AndroidWebDavClient(
    private val credentialStore: WebDavCredentialStore,
    private val credentialIdentifier: String,
    private val client: OkHttpClient = defaultClient()
) : WebDavClient {

    override suspend fun execute(request: WebDavRequest): WebDavResponse {
        val credential = credentialStore.load(credentialIdentifier)
        val builder = Request.Builder().url(request.url)
        // Caller-supplied headers take precedence (e.g. Depth / Overwrite / If).
        for ((k, v) in request.headers) {
            builder.header(k, v)
        }
        applyAuth(builder, credential?.auth)
        when (request.method) {
            WebDavMethod.PROPFIND -> {
                // Default Depth: 1 unless caller overrides via headers.
                if (request.headers["Depth"] == null) builder.header("Depth", "1")
                builder.method(
                    "PROPFIND",
                    (request.body ?: DEFAULT_PROPFIND_BODY)
                        .toRequestBody(XML_MEDIA_TYPE)
                )
            }
            WebDavMethod.GET -> builder.get()
            WebDavMethod.PUT ->
                builder.put(
                    (request.body ?: "")
                        .toRequestBody(OCTET_MEDIA_TYPE)
                )
            WebDavMethod.DELETE -> builder.delete()
            WebDavMethod.MKCOL -> builder.method("MKCOL", null)
        }
        client.newCall(builder.build()).execute().use { resp ->
            val body = resp.body?.string()
            val headers = resp.headers.toMultimap()
                .mapValues { it.value.joinToString(", ") }
            return WebDavResponse(resp.code, body, headers)
        }
    }

    private fun applyAuth(builder: Request.Builder, auth: AuthMethod?) {
        when (auth) {
            is AuthMethod.Basic -> {
                val raw = "${auth.username}:${auth.password}"
                val encoded = android.util.Base64.encodeToString(
                    raw.toByteArray(Charsets.UTF_8),
                    android.util.Base64.NO_WRAP
                )
                builder.header("Authorization", "Basic $encoded")
            }
            is AuthMethod.Bearer -> {
                builder.header("Authorization", "Bearer ${auth.token}")
            }
            is AuthMethod.Digest -> {
                // Digest requires a challenge-response flow (parse WWW-Authenticate,
                // compute HA1/HA2 with nonce). Not implemented in this slice — the
                // server will return 401 and the caller can surface the failure.
            }
            null -> { /* no credential stored — anonymous request */ }
        }
    }

    private companion object {
        val XML_MEDIA_TYPE = "application/xml; charset=utf-8".toMediaTypeOrNull()
        val OCTET_MEDIA_TYPE = "application/octet-stream".toMediaTypeOrNull()
        const val DEFAULT_PROPFIND_BODY =
            """<?xml version="1.0" encoding="utf-8"?>""" +
            """<propfind xmlns="DAV:"><prop><displayname/></prop></propfind>"""

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
