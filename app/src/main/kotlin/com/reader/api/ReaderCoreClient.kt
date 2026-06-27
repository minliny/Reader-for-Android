package com.reader.api

import com.reader.core.ReaderCoreRuntime
import com.reader.host.HostRuntime
import com.reader.host.HttpExecuteHandler
import com.reader.host.HttpFetch
import com.reader.host.OkHttpHostTransport
import com.reader.host.ReaderCoreHostTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * App-level singleton wrapping [ReaderCoreRuntime] + [HostRuntime].
 *
 * Uses the polling-based Core protocol: [HostRuntime] owns a daemon poll
 * thread that demultiplexes every Core event off [ReaderCoreHostTransport]:
 *  - `host.request` -> dispatched via [HostAdapter] (e.g. [HttpExecuteHandler]
 *    backed by [OkHttpHostTransport]) and replied with `host.complete` /
 *    `host.error` through the same transport.
 *  - `result` / `error` -> completes the matching [HostRuntime.sendAndAwait]
 *    future.
 *
 * This is the wiring layer, NOT a device proof: JNI `.so` must load on a real
 * device for the poll thread to receive real events. Device proof is Task 12.
 */
class ReaderCoreClient private constructor(
    private val runtime: ReaderCoreRuntime,
    private val hostRuntime: HostRuntime
) {

    /**
     * Send a Core command and await its result/error event, routed by
     * [HostRuntime]'s own poll thread. Blocks the calling coroutine on
     * [Dispatchers.IO] until the matching event arrives or [timeoutMillis]
     * elapses.
     */
    suspend fun sendAndAwait(
        method: String,
        params: JSONObject,
        timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS
    ): JSONObject = withContext(Dispatchers.IO) {
        val result = hostRuntime.sendAndAwait(method, params.toString(), timeoutMillis)
        when {
            result.isSuccess() -> JSONObject(result.dataJson())
            result.isError() -> throw CoreException(result.errorJson())
            result.isTimeout() -> throw CoreTimeoutException(
                "Timeout waiting for Core response (method=$method)"
            )
            else -> throw CoreException(
                "{\"code\":\"INTERNAL\",\"message\":\"" +
                    "unknown result kind: ${result.kind()}\"}"
            )
        }
    }

    fun cancel(requestId: Long) {
        runtime.cancel(requestId)
    }

    fun close() {
        hostRuntime.stop()
        runtime.close()
    }

    companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 60_000L

        @Volatile
        private var INSTANCE: ReaderCoreClient? = null

        fun init(configJson: String = "{}"): ReaderCoreClient {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: run {
                    val runtime = ReaderCoreRuntime(configJson)
                    val transport = ReaderCoreHostTransport(runtime)
                    val hostRuntime = HostRuntime.over(transport)
                        .register(
                            HttpExecuteHandler.CAPABILITY,
                            HttpExecuteHandler(OkHttpHostTransport())
                        )
                        .start()
                    ReaderCoreClient(runtime, hostRuntime).also { INSTANCE = it }
                }
            }
        }

        fun get(): ReaderCoreClient = INSTANCE
            ?: error("ReaderCoreClient not initialized. Call init() first.")

        /**
         * Test-only entry point: injects a custom [HttpFetch] so instrumented
         * tests can route Core's `http.execute` requests through MockWebServer
         * instead of the real network. Mirrors [init] but swaps the host-side
         * HTTP transport. Production code must keep using [init].
         */
        @JvmStatic
        fun initForTest(httpFetch: HttpFetch, configJson: String = "{}"): ReaderCoreClient {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: run {
                    val runtime = ReaderCoreRuntime(configJson)
                    val transport = ReaderCoreHostTransport(runtime)
                    val hostRuntime = HostRuntime.over(transport)
                        .register(HttpExecuteHandler.CAPABILITY, HttpExecuteHandler(httpFetch))
                        .start()
                    ReaderCoreClient(runtime, hostRuntime).also { INSTANCE = it }
                }
            }
        }

        @Synchronized
        fun resetForTest() {
            INSTANCE?.close()
            INSTANCE = null
        }
    }
}

class CoreException(val errorJson: String) : RuntimeException(errorJson)

class CoreTimeoutException(message: String) : RuntimeException(message)
