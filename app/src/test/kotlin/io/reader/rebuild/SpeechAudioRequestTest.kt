package io.reader.rebuild

import com.reader.core.protocol.M2HostHttpRequest
import com.reader.core.protocol.M2HostHttpResponse
import com.reader.core.protocol.M3HttpTtsBuildRequestData
import com.reader.core.protocol.M3HttpTtsPlayback
import com.reader.core.protocol.M3HttpTtsPlaybackChannels
import com.reader.core.protocol.M3HttpTtsPlaybackFormat
import io.reader.rebuild.host.media.SpeechAudioRequest
import java.util.Base64
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SpeechAudioRequestTest {
    @get:Rule val files = TemporaryFolder()

    @Test fun corePCMProfileBecomesAPrivateWavWithoutForwardingCredentials() = runBlocking {
        val url = "https://127.0.0.1:18767/fixture"
        val pcm = byteArrayOf(12, 0, -12, -1)
        val expectedHeader = byteArrayOf(
            82, 73, 70, 70, 40, 0, 0, 0, 87, 65, 86, 69, 102, 109, 116, 32,
            16, 0, 0, 0, 1, 0, 1, 0, -128, 62, 0, 0, 0, 125, 0, 0, 2, 0, 16, 0,
            100, 97, 116, 97, 4, 0, 0, 0,
        )
        var request: M2HostHttpRequest? = null
        val staging = files.newFolder()
        val transport = SpeechAudioRequest(staging, fetch = { value ->
            request = value
            M2HostHttpResponse("", 200, finalUrl = url, bodyBase64 = Base64.getEncoder().encodeToString(pcm))
        }, credential = { reference -> "secret".takeIf { reference == "fixture-key" } })
        val audio = transport.load(M3HttpTtsBuildRequestData("POST", url, emptyMap(), body = "中文",
            contentType = "text/plain; charset=GBK", playback = pcmPlan()))
        check(audio.parentFile == staging && audio.extension == "wav")
        check(audio.readBytes().contentEquals(expectedHeader + pcm))
        val actual = checkNotNull(request)
        check(actual.responseAsset == true && actual.usePlatformCookieJar == false && actual.followRedirects == false)
        check(actual.charset == "GBK" && actual.headers?.get("Authorization").toString() == "\"Bearer secret\"")
    }

    @Test fun missingCredentialAndPlainHttpNeverReachTheNetwork() = runBlocking {
        var calls = 0
        val staging = files.newFolder()
        val request = SpeechAudioRequest(staging, fetch = {
            calls++
            error("Unexpected request")
        }, credential = { null })
        val template = M3HttpTtsBuildRequestData("GET", "https://127.0.0.1:18767/fixture", emptyMap(), playback = pcmPlan())
        check(runCatching { request.load(template) }.isFailure)
        check(runCatching { request.load(template.copy(url = "http://127.0.0.1:18767/fixture")) }.isFailure)
        check(calls == 0 && staging.list().orEmpty().isEmpty())
    }

    private fun pcmPlan() = M3HttpTtsPlayback(M3HttpTtsPlaybackFormat.PCM, 16000, M3HttpTtsPlaybackChannels.V1,
        credentialRef = "fixture-key", credentialHeader = "Authorization", credentialPrefix = "Bearer ",
        rateApplied = true, ratePercent = 100)
}
