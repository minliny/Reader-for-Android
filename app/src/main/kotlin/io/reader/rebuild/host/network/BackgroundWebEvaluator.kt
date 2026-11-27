package io.reader.rebuild.host.network

import android.content.Context
import com.reader.core.protocol.M2HostWebViewDocumentHtml
import com.reader.core.protocol.M2HostWebViewDocumentUrl
import com.reader.core.protocol.M2HostWebViewEvaluateJavaScriptRequest
import com.reader.core.protocol.M2HostWebViewEvaluateJavaScriptResponse
import com.reader.core.protocol.ReaderProtocolJson
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

internal interface BackgroundWebEvaluator {
    val available: Boolean
    suspend fun run(request: M2HostWebViewEvaluateJavaScriptRequest, cookies: List<Pair<String, String>>):
        M2HostWebViewEvaluateJavaScriptResponse
}

internal class AndroidBackgroundWebEvaluator(private val context: Context, override val available: Boolean) : BackgroundWebEvaluator {
    override suspend fun run(request: M2HostWebViewEvaluateJavaScriptRequest, cookies: List<Pair<String, String>>):
        M2HostWebViewEvaluateJavaScriptResponse = withTimeout(timeoutMillis(request.timeoutMillis)) {
        withContext(Dispatchers.Main.immediate) {
            val profile = IsolatedWebProfile(context, "background")
            val view = profile.view
            val navigation = PendingWebNavigation(view)
            try {
                profile.seed(cookies)
                when (val document = request.document) {
                    is M2HostWebViewDocumentHtml -> view.loadDataWithBaseURL(document.baseUrl?.let { httpUrl(it).toString() },
                        document.body, "text/html", "UTF-8", null)
                    is M2HostWebViewDocumentUrl -> view.loadUrl(httpUrl(document.url).toString(),
                        request.headers?.mapValues { it.value.headerValue() }.orEmpty())
                }
                navigation.await()
                delay((request.settleDelayMillis ?: 0).coerceIn(0, 120_000))
                val slot = JsonPrimitive("__reader_" + UUID.randomUUID().toString()).toString()
                view.evaluate(script(slot, request))
                val result = awaitResult { view.evaluate("JSON.stringify(window[$slot])") }
                val value = result["value"] ?: JsonNull
                val resource = if (request.resourceUrlMatcherJavaScript == null) null
                else (value as? JsonPrimitive)?.contentOrNull?.let { httpUrl(it).toString() }
                    ?: throw NetworkHostFailure.invalid("WebView resource observation unavailable")
                M2HostWebViewEvaluateJavaScriptResponse(resource, value, view.url, view.title)
            } finally {
                navigation.retire()
                profile.close()
            }
        }
    }

    private suspend fun awaitResult(read: suspend () -> String): JsonObject {
        while (true) {
            val wire = read()
            if (wire.length > 8 * 1024 * 1024) throw NetworkHostFailure.invalid("WebView response exceeds byte budget")
            val value = ReaderProtocolJson.parseToJsonElement(wire)
            val box = (value as? JsonPrimitive)?.contentOrNull
                ?.let { ReaderProtocolJson.parseToJsonElement(it) as? JsonObject }
            if (box != null) {
                if (box["ok"]?.jsonPrimitive?.booleanOrNull != true) {
                    throw NetworkHostFailure.invalid("WebView JavaScript evaluation failed")
                }
                return box
            }
            delay(40)
        }
    }

    private fun script(slot: String, request: M2HostWebViewEvaluateJavaScriptRequest): String {
        val program = JsonPrimitive(request.javaScript).toString()
        val action = request.resourceUrlMatcherJavaScript?.let { matcher ->
            """
                const matches = (0, eval)(${JsonPrimitive("($matcher)")});
                const existing = performance.getEntriesByType('resource').find(entry => matches(entry.name));
                if (existing) return existing.name;
                const observed = new Promise(resolve => {
                    const observer = new PerformanceObserver(list => {
                        const item = list.getEntries().find(entry => matches(entry.name));
                        if (item) {
                            observer.disconnect();
                            resolve(item.name);
                        }
                    });
                    observer.observe({entryTypes: ['resource']});
                });
                const failed = Promise.resolve().then(() => (0, eval)($program)).then(() => new Promise(() => {}));
                return await Promise.race([observed, failed]);
            """.trimIndent()
        } ?: "return await (0, eval)($program);"
        return """
            (async () => { $action })().then(value => {
                const result = {ok: true, value: value === undefined ? null : value};
                JSON.stringify(result);
                window[$slot] = result;
            }).catch(() => { window[$slot] = {ok: false}; });
        """.trimIndent()
    }
}
