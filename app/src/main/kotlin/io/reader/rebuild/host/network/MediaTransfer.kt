package io.reader.rebuild.host.network

import com.reader.core.protocol.M2HostMediaDownloadRequest
import com.reader.core.protocol.M2HostMediaDownloadResponse
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.withTimeout
import okhttp3.Request
import okhttp3.Response

internal class MediaTransfer(
    private val transport: OkHttpNetwork,
    private val directory: File,
) {
    suspend fun download(model: M2HostMediaDownloadRequest, jar: PersistentCookieJar?): M2HostMediaDownloadResponse {
        val request = Request.Builder().url(httpUrl(model.url)).method(model.method?.wireValue ?: "GET", null)
        for ((name, value) in model.headers.orEmpty()) request.header(name, value)
        model.ifNoneMatch?.let { request.header("If-None-Match", it) }
        model.ifModifiedSince?.let { request.header("If-Modified-Since", it) }
        val start = model.rangeStart
        val end = model.rangeEnd
        if (start != null) {
            if (start < 0 || end != null && end < start) throw NetworkHostFailure.invalid("Invalid media range")
            request.header("Range", "bytes=$start-${end ?: ""}").header("Accept-Encoding", "identity")
        } else if (end != null) throw NetworkHostFailure.invalid("Missing media range start")
        val limit = minOf(model.maxBytes ?: MAX_BYTES, MAX_BYTES)
        if (limit <= 0) throw NetworkHostFailure.invalid("Invalid media byte budget")
        return withTimeout(timeoutMillis(model.timeoutMillis)) {
            transport.exchange(request.build(), jar, requestedTimeout = model.timeoutMillis, discard = ::discard) { reply, _, _ ->
                if (reply.code !in 200..299 && reply.code != 304) throw NetworkHostFailure.http(reply.code)
                val key = model.cacheKey ?: UUID.randomUUID().toString()
                if (reply.code == 304 || reply.request.method == "HEAD") receipt(reply, key, null, 0, null)
                else capture(reply, key, model.savePath, limit, rangeLength(reply, start, end))
            }
        }
    }

    fun discard(reply: M2HostMediaDownloadResponse) {
        reply.tempPath?.let(::discardPath)
    }

    fun discardPath(path: String) {
        val candidate = File(path)
        if (candidate.absoluteFile.parentFile == directory.canonicalFile) candidate.delete()
    }

    private fun capture(response: Response, resourceId: String, savePath: String?, limit: Long,
        expected: Long?): M2HostMediaDownloadResponse {
        if ((expected ?: 0L) > limit) throw NetworkHostFailure.invalid("Media range exceeds byte budget")
        directory.mkdirs()
        val root = directory.canonicalFile
        val destination = if (savePath == null) File(root, "${UUID.randomUUID()}.media")
        else File(savePath).let { if (it.isAbsolute) it else File(root, savePath) }.absoluteFile
        if (destination.parentFile != root || destination.name.startsWith(".")) {
            throw NetworkHostFailure.denied("Media destination is outside Host storage")
        }
        val temp = File.createTempFile(".reader-", ".partial", root)
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            val count = FileOutputStream(temp).use { output ->
                response.body.forEachSegment(limit) { segment, bytes ->
                    output.write(segment, 0, bytes)
                    digest.update(segment, 0, bytes)
                }.also { output.fd.sync() }
            }
            if (expected != null && count != expected) throw NetworkHostFailure.invalid("Media range length does not match body")
            // Moving without REPLACE_EXISTING prevents overwriting another transfer.
            Files.move(temp.toPath(), destination.toPath())
            return receipt(response, resourceId, destination.path, count, digest.digest().hex())
        } finally {
            temp.delete()
        }
    }

    private fun receipt(response: Response, resourceId: String, path: String?, count: Long, hash: String?) =
        M2HostMediaDownloadResponse(resourceId, path, response.code.toLong(),
            response.header("Content-Type"), response.body.contentLength().takeIf { it >= 0 },
            response.header("ETag"), count, hash, false, response.request.url.toString())

    private fun rangeLength(response: Response, start: Long?, requestedLast: Long?): Long? {
        if (start == null) return null
        val values = response.header("Content-Range")?.let(RANGE::matchEntire)?.groupValues
        if (response.code != 206 || values == null) throw NetworkHostFailure.invalid("Server did not honor media range")
        val first = values[1].toLongOrNull()
        val last = values[2].toLongOrNull()
        val total = values[3].toLongOrNull()
        if (first != start || last == null || last < start || last == Long.MAX_VALUE ||
            requestedLast != null && last > requestedLast ||
            total != null && (last >= total || last != minOf(requestedLast ?: last, total - 1)) ||
            total == null && (values[3] != "*" || requestedLast != null && last != requestedLast)) {
            throw NetworkHostFailure.invalid("Returned media range does not match request")
        }
        return last - start + 1
    }

    companion object {
        private const val MAX_BYTES = 128L * 1024 * 1024
        private val RANGE = Regex("bytes ([0-9]+)-([0-9]+)/([0-9]+|\\*)")
    }
}

internal fun ByteArray.hex(): String = joinToString("") { "%02x".format(it) }
