package io.reader.rebuild.host.network

import com.reader.core.protocol.M2HostHttpRequest
import com.reader.core.protocol.M2HostHttpRetryPolicy
import com.reader.core.protocol.M2HostHttpSession
import com.reader.core.protocol.M3HostHttpRequest
import com.reader.core.protocol.M3MangaResourceTransferHostRequest
import com.reader.core.protocol.M3MangaTransferDataUri
import com.reader.core.protocol.M3MangaTransferInput
import com.reader.core.protocol.M3MangaTransferInputCompleteParams
import com.reader.core.protocol.M3MangaTransferInputReceipt
import com.reader.core.protocol.M3MangaTransferInspectInput
import com.reader.core.protocol.M3MangaTransferOutput
import com.reader.core.protocol.M3MangaTransferOutputCompleteParams
import com.reader.core.protocol.M3MangaTransferOutputReceipt
import com.reader.core.protocol.ReaderM3
import com.reader.core.protocol.ReaderProtocolJson
import io.reader.rebuild.core.CoreMangaAssets
import io.reader.rebuild.host.media.MangaImageDecoder
import io.reader.rebuild.host.media.applyMangaGraphicsPlan
import java.io.DataInputStream
import java.io.File
import java.security.MessageDigest
import java.util.Base64
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal class MangaResourceTransfer(
    private val store: MangaTransferStore,
    private val fetch: suspend (M2HostHttpRequest, File, Long) -> Unit,
) {
    suspend fun reply(wire: String, replyId: Long, assets: CoreMangaAssets): String {
        val request = ReaderProtocolJson.decodeFromString<M3MangaResourceTransferHostRequest>(wire)
        check(request.protocolVersion == 1L && request.requestId == assets.request.requestId &&
            request.operationId == assets.operationId)
        val id = request.operationId
        return when (val params = request.params) {
            is M3MangaTransferInput -> ReaderM3.hostCompleteMangaTransferInput.encode(replyId,
                M3MangaTransferInputCompleteParams(id, fetchInput(params, assets)))
            is M3MangaTransferOutput -> ReaderM3.hostCompleteMangaTransferOutput.encode(replyId,
                M3MangaTransferOutputCompleteParams(id, consumeOutput(params, assets)))
            is M3MangaTransferInspectInput -> {
                require(params.bytes in 1..params.maxBytes.wireValue)
                val image = store.inspection(params.transferId, params.bytes, params.sha256)
                check(image.file.sha256() == image.sha256) { "Manga inspection file changed" }
                ReaderM3.hostCompleteMangaTransferInput.encode(replyId,
                    M3MangaTransferInputCompleteParams(id, send(image.file, assets)))
            }
        }
    }

    private suspend fun fetchInput(params: M3MangaTransferInput, assets: CoreMangaAssets): M3MangaTransferInputReceipt {
        val file = store.temporaryInput()
        val limit = params.maxBytes.wireValue
        try {
            when (val request = params.request) {
                is M3HostHttpRequest -> fetch(M2HostHttpRequest(
                    url = request.url, method = request.method, headers = request.headers, body = request.body,
                    charset = request.charset, timeoutMs = request.timeoutMs, followRedirects = request.followRedirects,
                    maxRedirects = request.maxRedirects,
                    retry = request.retry?.let { M2HostHttpRetryPolicy(it.maxAttempts, it.backoffMillis) },
                    usePlatformCookieJar = request.usePlatformCookieJar,
                    session = request.session?.let { M2HostHttpSession(it.id) },
                ), file, limit)
                is M3MangaTransferDataUri -> decodeDataUri(request.dataUri, file, limit)
            }
            check(file.length() in 1..limit)
            val dimensions = if (params.inspectGraphics) MangaImageDecoder.dimensions(file) else null
            return send(file, assets, dimensions)
        } finally { store.discardInput(file) }
    }

    private fun decodeDataUri(uri: String, file: File, limit: Long) {
        val separator = uri.indexOf(',')
        val prefix = uri.take(separator.coerceAtLeast(0)).lowercase()
        require(separator in 1..255 && prefix.startsWith("data:image/") && prefix.endsWith(";base64") &&
            uri.length - separator - 1 <= (limit + 2) / 3 * 4)
        val payload = uri.substring(separator + 1)
        Base64.getDecoder().wrap(payload.byteInputStream(Charsets.US_ASCII)).use { source ->
            file.outputStream().use { output -> source.copyTo(output, 65536) }
        }
    }

    private suspend fun send(file: File, assets: CoreMangaAssets, dimensions: Pair<Int, Int>? = null):
        M3MangaTransferInputReceipt {
        val bytes = file.length()
        val asset = assets.begin(bytes)
        try {
            DataInputStream(file.inputStream()).use { input ->
                var offset = 0L
                while (offset < bytes) {
                    currentCoroutineContext().ensureActive()
                    val limit = minOf(bytes - offset, CoreMangaAssets.CHUNK_LIMIT.toLong()).toInt()
                    val chunk = ByteArray(limit)
                    input.readFully(chunk)
                    offset += chunk.size
                    check(assets.write(asset, chunk) == offset)
                }
                check(input.read() == -1)
            }
            check(assets.commit(asset) == bytes)
            return M3MangaTransferInputReceipt(asset, assets.operationId, bytes,
                dimensions?.first?.toLong(), dimensions?.second?.toLong())
        } catch (failure: Throwable) {
            runCatching { assets.release(asset) }
            throw failure
        }
    }

    private suspend fun consumeOutput(params: M3MangaTransferOutput, assets: CoreMangaAssets):
        M3MangaTransferOutputReceipt {
        check(params.operationId == assets.operationId)
        val transformLimit = 8L * CoreMangaAssets.CHUNK_LIMIT
        val file = store.prepareOutput(params.transferId, params.bytes, if (params.graphicsPlan != null) transformLimit else 0)
        var released = false
        try {
            var offset = 0L
            var chunks = 0
            file.outputStream().use { output ->
                while (offset < params.bytes) {
                    currentCoroutineContext().ensureActive()
                    val limit = minOf(params.bytes - offset, CoreMangaAssets.CHUNK_LIMIT.toLong()).toInt()
                    val chunk = assets.read(params.assetId, offset, limit)
                    check(chunk.size == limit)
                    output.write(chunk)
                    offset += chunk.size
                    chunks++
                }
            }
            check(assets.read(params.assetId, offset, 1).isEmpty())
            assets.release(params.assetId)
            released = true
            params.graphicsPlan?.let { plan -> applyMangaGraphicsPlan(file, plan, transformLimit) }
            val amount = file.length()
            store.publish(params.transferId, amount, file.sha256(), chunks)
            return M3MangaTransferOutputReceipt(true, params.assetId, params.bytes,
                amount.takeIf { params.graphicsPlan != null })
        } catch (error: Throwable) {
            if (!released) runCatching { assets.release(params.assetId) }
            store.discard(params.transferId)
            throw error
        }
    }

    private fun File.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream().use { input ->
            val buffer = ByteArray(65536)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().hex()
    }
}
