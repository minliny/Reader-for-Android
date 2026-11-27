package io.reader.rebuild

import com.reader.core.protocol.M2CookieGetSmokeParams
import com.reader.core.protocol.M2CookieSetSmokeParams
import com.reader.core.protocol.M2HostCookieGetRequest
import com.reader.core.protocol.M2HostCookieRecord
import com.reader.core.protocol.M2HostCookieSetRequest
import com.reader.core.protocol.M2HostHttpRequest
import com.reader.core.protocol.M2HostHttpSession
import com.reader.core.protocol.M2HostMediaDownloadRequest
import com.reader.core.protocol.M2HostWebViewDocumentHtml
import com.reader.core.protocol.M2HostWebViewEvaluateJavaScriptRequest
import com.reader.core.protocol.M2HttpExecuteSmokeParams
import com.reader.core.protocol.M2MediaDownloadSmokeParams
import com.reader.core.protocol.M2WebViewEvaluateJavaScriptSmokeParams
import com.reader.core.protocol.ReaderErrorCode
import com.reader.core.protocol.ReaderM2
import io.reader.rebuild.core.CoreRejected
import io.reader.rebuild.core.CoreRuntimeActor
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Test

class M2HostNetworkTest {
    @Test fun chunkBudgetRedirectAndStatusThroughNativeCore() = runBlocking {
        val home = m2Temp()
        M2Loopback().use { fixture ->
            val (library, network) = openM2(home)
            try {
                val core = library.core
                val encoded = core.http(M2HostHttpRequest(url = fixture.origin + "/host/echo", method = "POST", body = "中文", charset = "GBK"))
                check(encoded.body == "d6d0cec4")
                val status = expectCoreFailure { core.http(M2HostHttpRequest(url = fixture.origin + "/host/503")) }
                check(status.detail.details?.host?.diagnostics?.code?.wireValue == "HTTP_ERROR")
                check(status.detail.details?.host?.diagnostics?.details?.get("status") == JsonPrimitive(503))
                val credentials = JsonObject(listOf("Authorization", "Cookie", "Proxy-Authorization")
                    .associateWith { JsonPrimitive("fixture-only") })
                val redirect = core.http(M2HostHttpRequest(url = fixture.origin + "/host/redirect", headers = credentials))
                check(redirect.body == "no credentials" && redirect.redirects?.single()?.status == 302L)
                val oversized = expectCoreFailure { core.http(M2HostHttpRequest(url = fixture.origin + "/host/budget")) }
                check(oversized.detail.details?.host?.diagnostics?.code?.wireValue == "INVALID_RESPONSE")
                println("ANDROID_M2_HTTP GBK=${encoded.body} refused503=${status.detail.code} crossOriginCredentials=0 oversize=INVALID_RESPONSE")
            } finally { closeM2(library, network); home.deleteRecursively() }
        }
    }

    @Test fun sourceScopedCookiePersistenceAndWebHostRoute() = runBlocking {
        val home = m2Temp()
        M2Loopback().use { fixture ->
            val url = fixture.origin + "/host/cookies"
            val (first, _) = openM2(home)
            try {
                val core = first.core
                val saved = core.call(ReaderM2.runtimeHostSmokeCookieSet,
                    M2CookieSetSmokeParams(params = M2HostCookieSetRequest(M2HostCookieRecord("m2", "first", path = "/"), url, "A")))
                check(saved.stored)
                check(core.http(M2HostHttpRequest(url = url, session = M2HostHttpSession("A"))).body == "m2=first")
                check(core.http(M2HostHttpRequest(url = url, session = M2HostHttpSession("B"))).body.isEmpty())
                val web = core.call(ReaderM2.runtimeHostSmokeWebViewEvaluateJavaScript,
                    M2WebViewEvaluateJavaScriptSmokeParams(params = M2HostWebViewEvaluateJavaScriptRequest(
                        document = M2HostWebViewDocumentHtml(body = "<title>M2 fixture WebView reply</title>"),
                        javaScript = "document.title", profileId = "A")))
                check(web.value == JsonPrimitive("M2 fixture WebView reply"))
                println("ANDROID_M2_COOKIES scopeA=independent scopeB=empty webRoute=fixture")
            } finally { first.close() }
            val (second, network) = openM2(home)
            try {
                val params = M2CookieGetSmokeParams(params = M2HostCookieGetRequest(url = url, sessionId = "A"))
                val retrieved = second.core.call(ReaderM2.runtimeHostSmokeCookieGet, params).cookies
                check(retrieved.single().value == "first")
                network.clearLogin("A")
                check(second.core.call(ReaderM2.runtimeHostSmokeCookieGet, params).cookies.isEmpty())
            } finally { second.close(); home.deleteRecursively() }
        }
    }

    @Test fun originalCancellationAndSegmentedMedia() = runBlocking {
        val home = m2Temp()
        M2Loopback().use { fixture ->
            val (library, network) = openM2(home)
            try {
                val core = library.core
                val path = "/host/small"
                val held = fixture.hold(path)
                try {
                    val original = core.submit(ReaderM2.runtimeHostSmokeHttpExecute,
                        M2HttpExecuteSmokeParams(params = M2HostHttpRequest(url = fixture.origin + path)))
                    held.waitForRequest()
                    core.cancelRead(original.identity)
                    val cancelled = expectCoreFailure { withTimeout(4_000) { original.await() } }
                    check(cancelled.detail.code == ReaderErrorCode.CANCELLED)
                    println("ANDROID_M2_HOST_CANCEL id=${original.identity.requestId} terminal=CANCELLED beforeRelease=true")
                } finally { fixture.release(path) }
                check(core.http(M2HostHttpRequest(url = fixture.origin + path)).body == "ready")
                val media = core.call(ReaderM2.runtimeHostSmokeMediaDownload, M2MediaDownloadSmokeParams(params =
                    M2HostMediaDownloadRequest(url = fixture.origin + "/host/media", rangeStart = 7, rangeEnd = 131086,
                        maxBytes = 132_000)))
                val saved = File(requireNotNull(media.tempPath))
                check(saved.canonicalFile.parentFile == File(home, "network/media").canonicalFile)
                check(media.statusCode == 206L && media.byteLength == 131080L &&
                    saved.readBytes().contentEquals(fixture.bytes.copyOfRange(7, 131087)))
                val refused = expectCoreFailure {
                    core.call(ReaderM2.runtimeHostSmokeMediaDownload, M2MediaDownloadSmokeParams(params =
                        M2HostMediaDownloadRequest(url = fixture.origin + "/host/media", maxBytes = 99)))
                }
                check(refused.detail.details?.host?.diagnostics?.code?.wireValue == "INVALID_RESPONSE")
                check(checkNotNull(saved.parentFile).listFiles()?.toList() == listOf(saved))
                println("ANDROID_M2_MEDIA status=${media.statusCode} byteLength=${media.byteLength} digest=${media.sha256} budget=INVALID_RESPONSE")
            } finally { closeM2(library, network); home.deleteRecursively() }
        }
    }

    private suspend fun CoreRuntimeActor.http(params: M2HostHttpRequest) =
        call(ReaderM2.runtimeHostSmokeHttpExecute, M2HttpExecuteSmokeParams(params = params))
}

internal suspend fun expectCoreFailure(block: suspend () -> Unit): CoreRejected {
    try { block() } catch (error: CoreRejected) { return error }
    error("Expected a rejected original Core operation")
}
