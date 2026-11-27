package io.reader.rebuild

import com.reader.core.protocol.M2HostWebViewDocumentHtml
import com.reader.core.protocol.M2HostWebViewEvaluateJavaScriptRequest
import com.reader.core.protocol.M2HostWebViewEvaluateJavaScriptResponse
import com.reader.core.protocol.ReaderProtocolJson
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.reader.rebuild.core.HostCapabilityRouter
import io.reader.rebuild.host.network.BackgroundWebEvaluator
import io.reader.rebuild.host.network.NetworkAndWebHost
import io.reader.rebuild.host.storage.LibraryRuntime
import io.reader.rebuild.host.system.SystemSurfaceHost
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal const val M2_BOOK = "受控回环书"
internal const val M2_TEXT = "墨纸青松，受控回环正文。中文𠀀与Café都在这里。用于校验在线阅读锚点与换源、脱机缓存。"

internal class Hold {
    val hit = CountDownLatch(1)
    private val released = CountDownLatch(1)
    fun waitForRequest() = check(hit.await(12, TimeUnit.SECONDS)) { "Loopback request never arrived" }
    fun release() { released.countDown() }
    fun await() {
        hit.countDown()
        check(released.await(15, TimeUnit.SECONDS)) { "Loopback response gate was not released" }
    }
}

internal class M2Loopback : Closeable {
    private val executor = Executors.newCachedThreadPool { task -> Thread(task, "reader-m2-http").apply { isDaemon = true } }
    private val server = server()
    private val crossOrigin = server()
    private val holds = ConcurrentHashMap<String, Hold>()
    private val status = ConcurrentHashMap<String, Int>()
    private val calls = ConcurrentHashMap<String, AtomicInteger>()
    @Volatile var subscriptionUpdate = false
    val bytes = ByteArray(192 * 1024) { (it % 251).toByte() }
    val origin = "http://127.0.0.1:${server.address.port}"

    init {
        server.createContext("/") { serve(it) }
        crossOrigin.createContext("/credentials") { exchange ->
            val values = listOf("Authorization", "Cookie", "Proxy-Authorization").mapNotNull { exchange.requestHeaders.getFirst(it) }
            exchange.reply(200, values.joinToString("|").ifEmpty { "no credentials" })
        }
        server.start()
        crossOrigin.start()
    }

    fun count(path: String): Int = calls[path]?.get() ?: 0
    fun hold(path: String): Hold = Hold().also { holds.put(path, it)?.release() }
    fun release(path: String) { holds.remove(path)?.release() }
    fun status(path: String, value: Int?) {
        if (value == null) status.remove(path) else status[path] = value
    }

    fun source(name: String): SourceFixture = SourceFixture(origin, name)

    private fun server(): HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).also {
        it.executor = executor
    }

    private fun serve(exchange: HttpExchange) {
        val path = exchange.requestURI.path
        calls.computeIfAbsent(path) { AtomicInteger() }.incrementAndGet()
        try {
            holds[path]?.await()
            val responseCode = status[path]
            if (responseCode != null) {
                exchange.reply(responseCode, "controlled outage")
                return
            }
            when (path) {
                "/host/echo" -> exchange.reply(200, exchange.requestBody.readBytes().joinToString("") { "%02x".format(it) })
                "/host/503" -> {
                    exchange.sendResponseHeaders(503, 0)
                    repeat(160) { exchange.responseBody.write(ByteArray(65_536)) }
                }
                "/host/redirect" -> {
                    exchange.responseHeaders.set("Location", "http://127.0.0.1:${crossOrigin.address.port}/credentials")
                    exchange.sendResponseHeaders(302, -1)
                }
                "/host/budget" -> {
                    exchange.sendResponseHeaders(200, 0)
                    val segment = ByteArray(65_536)
                    repeat(129) { exchange.responseBody.write(segment) }
                }
                "/host/small" -> exchange.reply(200, "ready")
                "/host/cookies" -> exchange.reply(200, exchange.requestHeaders.getFirst("Cookie") ?: "")
                "/host/media" -> sendMedia(exchange)
                "/rules" -> {
                    val selected = if (subscriptionUpdate) listOf(source("rules"), source("additional")) else listOf(source("rules"))
                    exchange.reply(200, ReaderProtocolJson.encodeToString(selected.map(SourceFixture::rule)), "application/json")
                }
                else -> books(path, exchange)
            }
        } catch (_: IOException) {
            // A cancelled call or a refused oversized response closes the TCP stream.
        } finally {
            exchange.close()
        }
    }

    private fun sendMedia(exchange: HttpExchange) {
        val range = Regex("bytes=(\\d+)-(\\d+)").matchEntire(exchange.requestHeaders.getFirst("Range").orEmpty())
        if (range == null) {
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.write(bytes)
        } else {
            val first = range.groupValues[1].toInt()
            val last = minOf(range.groupValues[2].toInt(), bytes.lastIndex)
            exchange.responseHeaders.set("Content-Range", "bytes $first-$last/${bytes.size}")
            exchange.sendResponseHeaders(206, (last - first + 1).toLong())
            exchange.responseBody.write(bytes, first, last - first + 1)
        }
    }

    private fun books(path: String, exchange: HttpExchange) {
        val match = Regex("/book/([a-z]+)/(search|detail|toc|chapter/[1-4]|login)").matchEntire(path)
        if (match == null) {
            exchange.reply(404, "missing route")
            return
        }
        val source = source(match.groupValues[1])
        val body = when (val route = match.groupValues[2]) {
            "search" -> """<ul class="results"><li><span class="name">$M2_BOOK</span><span class="author">测试作者</span><a class="detail" href="${source.bookId}">详情</a></li></ul>"""
            "detail" -> """<h1>$M2_BOOK</h1><p class="author">测试作者</p><a class="toc" href="${source.toc}">目录</a>"""
            "toc" -> (1..4).joinToString("", "<ol class=\"toc\">", "</ol>") {
                """<li><a href="${source.base}/chapter/$it">第${"一二三四"[it - 1]}章</a></li>"""
            }
            "login" -> "<title>受控书源登录</title>"
            else -> """<article class="content">$M2_TEXT 章 ${route.last()}</article>"""
        }
        exchange.reply(200, "<!doctype html><meta charset=\"utf-8\">$body", "text/html; charset=utf-8")
    }

    override fun close() {
        holds.values.forEach(Hold::release)
        server.stop(0)
        crossOrigin.stop(0)
        executor.shutdownNow()
    }
}

internal data class SourceFixture(val origin: String, val name: String) {
    val id = "$origin/book/$name"
    val base get() = id
    val path get() = "/book/$name"
    val toc get() = "$base/toc"
    val bookId get() = "$base/detail"
    val rule: JsonObject get() = ReaderProtocolJson.parseToJsonElement("""
        {
          "bookSourceName": "M2 $name",
          "bookSourceUrl": "$base",
          "bookSourceGroup": "M2 回环",
          "bookSourceType": 0,
          "loginUrl": "$base/login",
          "enabled": true,
          "searchUrl": "$base/search?q={{key}}",
          "ruleSearch": {"bookList": "ul.results li", "name": "span.name@text", "author": "span.author@text", "bookUrl": "a.detail@href"},
          "ruleBookInfo": {"name": "h1@text", "author": "p.author@text", "tocUrl": "a.toc@href"},
          "ruleToc": {"chapterList": "ol.toc li", "chapterName": "a@text", "chapterUrl": "a@href"},
          "ruleContent": {"content": "article.content@text"}
        }
    """.trimIndent()) as JsonObject
}

private fun HttpExchange.reply(code: Int, text: String, type: String = "text/plain; charset=utf-8") {
    val data = text.encodeToByteArray()
    responseHeaders.set("Content-Type", type)
    sendResponseHeaders(code, data.size.toLong())
    responseBody.use { it.write(data) }
}

internal object FixtureWebEvaluator : BackgroundWebEvaluator {
    override val available = true
    override suspend fun run(request: M2HostWebViewEvaluateJavaScriptRequest,
        cookies: List<Pair<String, String>>): M2HostWebViewEvaluateJavaScriptResponse {
        check(request.document is M2HostWebViewDocumentHtml)
        check(request.javaScript == "document.title")
        return M2HostWebViewEvaluateJavaScriptResponse(value = JsonPrimitive("M2 fixture WebView reply"))
    }
}

internal suspend fun openM2(home: File): Pair<LibraryRuntime, NetworkAndWebHost> {
    val network = NetworkAndWebHost(File(home, "network"), FixtureWebEvaluator)
    val hosted = try {
        LibraryRuntime.open(File(home, "library"), HostCapabilityRouter(SystemSurfaceHost(), network))
    } catch (failure: Throwable) {
        network.close()
        throw failure
    }
    check(hosted.info.buildIdentity.gitCommit == "14fdd8f91549a30a7b7235088d3c524d128201d1")
    return hosted to network
}

internal suspend fun closeM2(library: LibraryRuntime, network: NetworkAndWebHost) {
    try { library.close() } finally { network.close() }
}

internal fun m2Temp(): File = Files.createTempDirectory("reader-native-m2-").toFile()
