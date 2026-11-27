package io.reader.rebuild.host.media

import com.reader.core.protocol.M2HostHttpRequest
import com.reader.core.protocol.M2HostHttpResponse
import com.reader.core.protocol.M3HttpTtsBuildRequestData
import com.reader.core.protocol.M3HttpTtsPlayback
import com.reader.core.protocol.M3HttpTtsPlaybackFormat
import java.io.File
import java.net.URI
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal class SpeechAudioRequest(
    private val directory: File,
    private val fetch: suspend (M2HostHttpRequest) -> M2HostHttpResponse,
    private val credential: suspend (String) -> String?,
) {
    suspend fun load(plan: M3HttpTtsBuildRequestData): File {
        val destination = URI(plan.url)
        require(destination.scheme.equals("https", ignoreCase = true) && destination.host != null &&
            destination.userInfo == null) { "Unusable speech audio endpoint" }
        val playback = plan.playback
        val headers = plan.headers.mapValuesTo(mutableMapOf()) { JsonPrimitive(it.value) }
        val contentType = plan.contentType ?: plan.headers.entries.firstOrNull {
            it.key.equals("Content-Type", ignoreCase = true)
        }?.value
        if (contentType != null && headers.keys.none { it.equals("Content-Type", ignoreCase = true) }) {
            headers["Content-Type"] = JsonPrimitive(contentType)
        }
        playback?.credentialRef?.let { reference ->
            val key = requireNotNull(playback.credentialHeader) { "Speech credential header unavailable" }
            val secret = credential(reference)?.takeIf(String::isNotEmpty)
                ?: error("Speech credential unavailable")
            headers.keys.removeAll { it.equals(key, ignoreCase = true) }
            headers[key] = JsonPrimitive(playback.credentialPrefix + secret)
        }
        val charset = contentType?.split(';')?.asSequence()?.drop(1)?.map(String::trim)?.firstOrNull {
            it.startsWith("charset=", ignoreCase = true)
        }?.substringAfter('=')?.trim('"', '\'')
        val response = fetch(M2HostHttpRequest(url = plan.url, method = plan.method, headers = JsonObject(headers),
            body = plan.body, charset = charset, followRedirects = false,
            timeoutMs = 15_000, usePlatformCookieJar = false, responseAsset = true))
        require(response.status?.let { it in 200L..299L } == true &&
            response.finalUrl?.let { URI(it).scheme.equals("https", ignoreCase = true) } == true) {
            "Unusable speech audio response"
        }
        val encoded = requireNotNull(response.bodyBase64)
        require(encoded.isNotEmpty() && encoded.length <= (MAX_AUDIO_BYTES + 2) / 3 * 4) { "Speech audio byte budget exceeded" }
        var output: File? = null
        try {
            return withContext(Dispatchers.IO) {
                val audio = Base64.getDecoder().decode(encoded)
                require(audio.isNotEmpty() && audio.size <= MAX_AUDIO_BYTES) { "Speech audio byte budget exceeded" }
                check(directory.isDirectory || directory.mkdirs())
                val file = File.createTempFile("slice-", if (playback?.format == M3HttpTtsPlaybackFormat.MP3) ".mp3" else ".wav", directory)
                output = file
                file.outputStream().buffered().use { sink ->
                    if (playback?.format == M3HttpTtsPlaybackFormat.PCM) sink.write(pcmHeader(audio.size, playback))
                    sink.write(audio)
                }
                ensureActive()
                file
            }
        } catch (failure: Throwable) {
            output?.delete()
            throw failure
        }
    }

    private fun pcmHeader(size: Int, playback: M3HttpTtsPlayback): ByteArray {
        val rate = requireNotNull(playback.sampleRate).toInt()
        val channels = requireNotNull(playback.channels).wireValue.toInt()
        val frameBytes = channels * Short.SIZE_BYTES
        require(rate in 8_000..192_000 && channels in 1..2 && size % frameBytes == 0)
        return ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            .put("RIFF".toByteArray(Charsets.US_ASCII)).putInt(size + 36)
            .put("WAVEfmt ".toByteArray(Charsets.US_ASCII)).putInt(16)
            .putShort(1).putShort(channels.toShort()).putInt(rate).putInt(rate * frameBytes)
            .putShort(frameBytes.toShort()).putShort(16)
            .put("data".toByteArray(Charsets.US_ASCII)).putInt(size).array()
    }

    companion object {
        private const val MAX_AUDIO_BYTES = 8 * 1024 * 1024
    }
}
