package com.reader.android.data.adapter

import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * OkHttp-backed [WebDavClient] that integrates with [WebDavCredentialStore]
 * and applies [RetryPolicy] + [WebDavErrorMapper] for transient failures.
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
 *  4. Retries retryable failures (408/429/5xx per [WebDavErrorMapper.isRetryable])
 *     with exponential backoff per [retryPolicy]. Non-retryable statuses and
 *     IOExceptions on the final attempt are returned as-is so the caller
 *     (e.g. [BackupRestoreManager]) can surface a meaningful error.
 *
 * Tests inject [FakeWebDavClient] (no network); on-device the production
 * wiring lives in [com.reader.android.AppProvider].
 */
class AndroidWebDavClient(
    private val credentialStore: WebDavCredentialStore,
    private val credentialIdentifier: String,
    private val client: OkHttpClient = defaultClient(),
    /**
     * P1-6: Retry policy for transient failures. Defaults to [RetryPolicy.DEFAULT]
     * (3 retries, 1s → 2s → 4s backoff). Pass [RetryPolicy.NO_RETRY] to
     * disable. JVM tests inject a no-retry policy or use [FakeWebDavClient].
     */
    private val retryPolicy: RetryPolicy = RetryPolicy.DEFAULT
) : WebDavClient {

    override suspend fun execute(request: WebDavRequest): WebDavResponse {
        var lastResponse: WebDavResponse? = null
        var lastError: Exception? = null
        for (attempt in 0..retryPolicy.maxRetries) {
            try {
                val response = executeOnce(request)
                lastResponse = response
                // Success or non-retryable → return immediately.
                if (!WebDavErrorMapper.isRetryable(response.statusCode)) {
                    return response
                }
                // 401/403/404 etc. are non-retryable; only 408/429/5xx reach here.
            } catch (e: Exception) {
                lastError = e
                // IOException is retryable (network blip); other exceptions are not.
                if (attempt >= retryPolicy.maxRetries) throw e
            }
            // Backoff before next attempt (skip on the last iteration).
            if (attempt < retryPolicy.maxRetries) {
                delay(retryPolicy.backoffForAttempt(attempt))
            }
        }
        // All retries exhausted — return the last response or throw the last error.
        if (lastResponse != null) return lastResponse
        if (lastError != null) throw lastError
        return WebDavResponse(599, "retry exhausted")
    }

    private suspend fun executeOnce(request: WebDavRequest): WebDavResponse {
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
