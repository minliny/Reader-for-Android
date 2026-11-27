package io.reader.rebuild.host.network

import android.content.Context
import android.util.Log
import com.reader.core.protocol.M2CookieGetCompleteParams
import com.reader.core.protocol.M2CookieSetCompleteParams
import com.reader.core.protocol.M2HostCookieGetRequest
import com.reader.core.protocol.M2HostCookieSetRequest
import com.reader.core.protocol.M2HostHttpRequest
import com.reader.core.protocol.M2HostHttpResponse
import com.reader.core.protocol.M2HostMediaDownloadRequest
import com.reader.core.protocol.M2HostWebViewEvaluateJavaScriptRequest
import com.reader.core.protocol.M2HttpExecuteCompleteParams
import com.reader.core.protocol.M2MediaDownloadCompleteParams
import com.reader.core.protocol.M2WebViewEvaluateJavaScriptCompleteParams
import com.reader.core.protocol.ReaderHostCapability
import com.reader.core.protocol.ReaderM2
import com.reader.core.protocol.ReaderProtocolJson
import com.reader.core.protocol.ReaderTypedHostRequest
import io.reader.rebuild.core.HostCapabilities
import io.reader.rebuild.core.CoreMangaAssets
import java.io.File
import java.io.RandomAccessFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonObject

/** The Core owns rules and operation settlement. This slot owns physical I/O and isolated cookies. */
internal class NetworkAndWebHost(
    directory: File,
    private val evaluator: BackgroundWebEvaluator,
    private val logins: AndroidSourceLogins? = null,
) : HostCapabilities {
    private val network = OkHttpNetwork()
    private val cookies = SourceCookieStore(File(directory, "cookies"))
    private val media = MediaTransfer(network, File(directory, "media"))
    private val mangaStore = lazy { MangaTransferStore(File(directory, "manga-scratch")) }
    val mangaTransfers get() = mangaStore.value
    override val extensions = listOf("http.execute.timeoutMs.v1")
    override val available = buildSet {
        add(ReaderHostCapability.HTTP_EXECUTE)
        add(ReaderHostCapability.COOKIE_GET)
        add(ReaderHostCapability.COOKIE_SET)
        add(ReaderHostCapability.MEDIA_DOWNLOAD)
        add(ReaderHostCapability.MANGA_RESOURCE_TRANSFER)
        if (evaluator.available) add(ReaderHostCapability.WEBVIEW_EVALUATE_JAVA_SCRIPT)
    }

    override suspend fun reply(capability: ReaderHostCapability, request: String, replyId: Long): String {
        val operationId = ReaderProtocolJson.decodeFromString<ReaderTypedHostRequest<kotlinx.serialization.json.JsonElement>>(request)
            .also { check(it.capability == capability && it.type == "host.request" && it.protocolVersion == 1) }.operationId
        try {
            return when (capability) {
                ReaderHostCapability.HTTP_EXECUTE -> ReaderM2.hostCompleteHttpExecute.encode(replyId,
                    M2HttpExecuteCompleteParams(operationId, fetch(params(request))))
                ReaderHostCapability.COOKIE_GET -> {
                    val model = params<M2HostCookieGetRequest>(request)
                    logins?.pull(model.sessionId)
                    ReaderM2.hostCompleteCookieGet.encode(replyId,
                        M2CookieGetCompleteParams(operationId, cookies.get(model)))
                }
                ReaderHostCapability.COOKIE_SET -> {
                    val model = params<M2HostCookieSetRequest>(request)
                    logins?.pull(model.sessionId)
                    val result = cookies.set(model)
                    logins?.push(model.sessionId, model.sessionId?.let { cookies.profile(it) })
                    ReaderM2.hostCompleteCookieSet.encode(replyId, M2CookieSetCompleteParams(operationId, result))
                }
                ReaderHostCapability.WEBVIEW_EVALUATE_JAVA_SCRIPT -> {
                    val model = params<M2HostWebViewEvaluateJavaScriptRequest>(request)
                    logins?.pull(model.profileId)
                    val result = evaluator.run(model, model.profileId?.let { cookies.profile(it).entriesForWeb() }.orEmpty())
                    ReaderM2.hostCompleteWebViewEvaluateJavaScript.encode(replyId,
                        M2WebViewEvaluateJavaScriptCompleteParams(operationId, result))
                }
                ReaderHostCapability.MEDIA_DOWNLOAD -> {
                    val model = params<M2HostMediaDownloadRequest>(request)
                    logins?.pull(model.sessionId)
                    val jar = model.sessionId?.let { cookies.profile(it) }
                    val result = media.download(model, jar)
                    try {
                        logins?.push(model.sessionId, jar)
                        ReaderM2.hostCompleteMediaDownload.encode(replyId, M2MediaDownloadCompleteParams(operationId, result))
                    } catch (error: Exception) {
                        media.discard(result)
                        throw error
                    }
                }
                else -> error("Unsupported NetworkAndWebHost capability")
            }
        } catch (failure: Exception) {
            if (failure is CancellationException && failure !is TimeoutCancellationException) throw failure
            return ReaderM2.hostError.encode(replyId, NetworkHostFailure.classified(failure).reply(operationId))
        }
    }

    override fun discardUnsent(capability: ReaderHostCapability, reply: String) {
        if (capability == ReaderHostCapability.MEDIA_DOWNLOAD) {
            val value = runCatching {
                val envelope = ReaderProtocolJson.parseToJsonElement(reply).jsonObject
                envelope["params"]?.let { ReaderProtocolJson.decodeFromJsonElement(M2MediaDownloadCompleteParams.serializer(), it) }
            }.getOrNull()
            value?.result?.let(media::discard)
        }
    }

    override suspend fun replyWithAssets(capability: ReaderHostCapability, request: String, replyId: Long,
        assets: CoreMangaAssets): String {
        check(capability == ReaderHostCapability.MANGA_RESOURCE_TRANSFER)
        val reply = MangaResourceTransfer(mangaTransfers, ::fetchManga)
        return try {
            reply.reply(request, replyId, assets)
        } catch (failure: Exception) {
            Log.e("ReaderM3", "manga resource host operation failed", failure)
            if (failure is CancellationException && failure !is TimeoutCancellationException) throw failure
            ReaderM2.hostError.encode(replyId, NetworkHostFailure.classified(failure).reply(assets.operationId))
        }
    }

    private suspend fun fetchManga(params: M2HostHttpRequest, file: File, limit: Long) {
        val session = params.session?.id
        val useCookies = params.usePlatformCookieJar ?: (session != null)
        if (useCookies) logins?.pull(session)
        val jar = if (useCookies) cookies.profile(session, httpUrl(params.url)) else null
        RandomAccessFile(file, "rw").use { output ->
            network.capture(params, jar) { response, _, _ ->
                if (!response.isSuccessful) throw NetworkHostFailure.http(response.code)
                output.setLength(0)
                output.seek(0)
                response.body.forEachSegment(limit) { segment, size -> output.write(segment, 0, size) }
            }
        }
        if (useCookies) logins?.push(session, jar)
    }

    suspend fun fetch(params: M2HostHttpRequest): M2HostHttpResponse {
        val session = params.session?.id
        val useCookies = params.usePlatformCookieJar ?: (session != null)
        if (useCookies) logins?.pull(session)
        val jar = if (useCookies) cookies.profile(session, httpUrl(params.url)) else null
        return network.fetch(params, jar).also { if (useCookies) logins?.push(session, jar) }
    }

    suspend fun beginLogin(sourceId: String, url: String): SourceLoginSession =
        requireNotNull(logins).begin(sourceId, httpUrl(url), withContext(Dispatchers.IO) { cookies.profile(sourceId) })

    suspend fun finishLogin(session: SourceLoginSession) { logins?.finish(session) }
    suspend fun revokeLogin(sourceId: String) { logins?.revoke(sourceId) }

    suspend fun revokeLogin(session: SourceLoginSession) { logins?.revoke(session) }

    suspend fun clearLogin(sourceId: String) {
        revokeLogin(sourceId)
        withContext(Dispatchers.IO) { cookies.clear(sourceId) }
    }

    suspend fun close() {
        try { logins?.close() } finally {
            network.close()
            if (mangaStore.isInitialized()) mangaTransfers.close()
        }
    }

    private inline fun <reified P> params(wire: String): P =
        ReaderProtocolJson.decodeFromString<ReaderTypedHostRequest<P>>(wire).params

    companion object {
        fun android(context: Context, directory: File): NetworkAndWebHost {
            val app = context.applicationContext
            val supported = supportsIsolatedWebProfiles()
            return NetworkAndWebHost(directory, AndroidBackgroundWebEvaluator(app, supported), AndroidSourceLogins(app))
        }
    }
}
