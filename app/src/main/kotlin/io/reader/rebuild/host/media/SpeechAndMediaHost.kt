package io.reader.rebuild.host.media

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import com.reader.core.protocol.M2HostHttpRequest
import com.reader.core.protocol.M2HostHttpResponse
import com.reader.core.protocol.M3HttpTtsBuildRequestData
import java.io.File
import java.util.Locale
import java.util.UUID

/** Physical speech, focus and Media3 state for the reader owner on Main. */
internal class SpeechAndMediaHost(
    context: Context,
    directory: File,
    fetch: suspend (M2HostHttpRequest) -> M2HostHttpResponse,
    credential: suspend (String) -> String? = { null },
) {
    private enum class Driver { SYSTEM, REMOTE }

    private val app = context.applicationContext
    private val audio = app.getSystemService(AudioManager::class.java)
    private val commands = SpeechMediaSession(app)
    private val system = AndroidSystemSpeech(app)
    private val remote = AndroidRemoteSpeech(app)
    private val audioRequest = SpeechAudioRequest(directory, fetch, credential)
    private var metadata: SpeechMetadata? = null
    private var handle: ((SpeechAction) -> Unit)? = null
    private var focus: AudioFocusRequest? = null
    private var driver: Driver? = null
    private var ticket: UUID? = null
    private var remoteRateApplied = false

    suspend fun begin(owner: UUID, title: String, chapter: String?, ratePercent: Long, action: (SpeechAction) -> Unit) {
        mainThread()
        require(ratePercent in 50..200)
        check(metadata == null)
        val current = SpeechMetadata(owner, title, chapter, ratePercent = ratePercent)
        metadata = current
        handle = action
        try {
            commands.connect(current, action)
            requireOwner(owner)
        } catch (failure: Throwable) {
            retire(owner)
            throw failure
        }
    }

    suspend fun systemSlice(owner: UUID, token: UUID, text: String, locale: Locale?,
                            receive: (UUID, SpeechPhysicalEvent) -> Unit) {
        requireOwner(owner)
        stopSlice(owner)
        system.prepare()
        val current = requireOwner(owner)
        obtainFocus(owner)
        ticket = token
        driver = Driver.SYSTEM
        system.speak(text, locale, current.ratePercent / 100f, observer(owner, token, receive))
    }

    suspend fun httpSlice(owner: UUID, token: UUID, plan: M3HttpTtsBuildRequestData, receive: (UUID, SpeechPhysicalEvent) -> Unit) {
        requireOwner(owner)
        stopSlice(owner)
        val file = audioRequest.load(plan)
        try {
            val current = requireOwner(owner)
            obtainFocus(owner)
            ticket = token
            driver = Driver.REMOTE
            remoteRateApplied = plan.playback?.rateApplied == true
            remote.play(file, if (remoteRateApplied) 1f else current.ratePercent / 100f, observer(owner, token, receive))
        } catch (failure: Throwable) {
            file.delete()
            throw failure
        }
    }

    fun pause(owner: UUID) {
        requireOwner(owner)
        when (driver) {
            Driver.SYSTEM -> system.pause()
            Driver.REMOTE -> remote.pause()
            null -> Unit
        }
        projectPlaying(false)
        abandonFocus()
    }

    fun resume(owner: UUID): Boolean {
        requireOwner(owner)
        val active = driver ?: return false
        if (active == Driver.SYSTEM && !system.hasUtterance()) return false
        obtainFocus(owner)
        when (active) {
            Driver.SYSTEM -> system.resume()
            Driver.REMOTE -> remote.resume()
        }
        return true
    }

    fun setRate(owner: UUID, percent: Long) {
        require(percent in 50..200)
        val current = requireOwner(owner)
        metadata = current.copy(ratePercent = percent)
        when (driver) {
            Driver.SYSTEM -> system.setRate(percent / 100f)
            Driver.REMOTE -> if (!remoteRateApplied) remote.setRate(percent / 100f) else Unit
            null -> Unit
        }
        refreshProjection()
    }

    fun showQueue(owner: UUID, index: Long, count: Long) {
        require(index in 0 until count)
        val current = requireOwner(owner)
        if (current.sliceIndex == index && current.sliceCount == count) return
        metadata = current.copy(sliceIndex = index, sliceCount = count)
        refreshProjection()
    }

    fun stopSlice(owner: UUID) {
        requireOwner(owner)
        ticket = null
        when (driver) {
            Driver.SYSTEM -> system.stop()
            Driver.REMOTE -> remote.stop()
            null -> Unit
        }
        driver = null
        remoteRateApplied = false
        projectPlaying(false)
        abandonFocus()
    }

    fun retire(owner: UUID) {
        mainThread()
        if (metadata?.owner != owner) return
        stopSlice(owner)
        commands.disconnect(owner)
        system.close()
        remote.close()
        metadata = null
        handle = null
    }

    private fun observer(owner: UUID, token: UUID, receive: (UUID, SpeechPhysicalEvent) -> Unit):
        (SpeechPhysicalEvent) -> Unit = { event ->
        if (metadata?.owner == owner && ticket == token) {
            when (event) {
                SpeechPhysicalEvent.Started -> projectPlaying(true)
                SpeechPhysicalEvent.Finished, SpeechPhysicalEvent.Failed -> {
                    projectPlaying(false)
                    abandonFocus()
                }
                is SpeechPhysicalEvent.Range -> Unit
            }
            receive(token, event)
        }
    }

    private fun obtainFocus(owner: UUID) {
        if (focus != null) return
        val manager = requireNotNull(audio)
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setWillPauseWhenDucked(true)
            .setOnAudioFocusChangeListener({ change ->
                if (metadata?.owner == owner && focus != null && change in
                    setOf(AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK)) {
                    pause(owner)
                    handle?.invoke(SpeechAction.Pause)
                }
            }, Handler(Looper.getMainLooper())).build()
        check(manager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) { "Speech audio focus unavailable" }
        focus = request
    }

    private fun abandonFocus() {
        val current = focus ?: return
        focus = null
        audio?.abandonAudioFocusRequest(current)
    }

    private fun projectPlaying(playing: Boolean) {
        val current = metadata ?: return
        if (current.playing == playing) return
        metadata = current.copy(playing = playing)
        refreshProjection()
    }

    private fun refreshProjection() {
        val current = metadata ?: return
        val onAction = handle ?: return
        commands.update(current, onAction)
    }

    private fun requireOwner(owner: UUID): SpeechMetadata {
        mainThread()
        return checkNotNull(metadata?.takeIf { it.owner == owner }) { "Speech session retired" }
    }

    private fun mainThread() = check(Looper.myLooper() == Looper.getMainLooper())
}
