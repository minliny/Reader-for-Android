package io.reader.rebuild.host.media

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout

internal sealed interface SpeechPhysicalEvent {
    data class Range(val start: Int, val end: Int) : SpeechPhysicalEvent
    data object Started : SpeechPhysicalEvent
    data object Finished : SpeechPhysicalEvent
    data object Failed : SpeechPhysicalEvent
}

internal class AndroidSystemSpeech(private val context: Context) {
    private data class Utterance(
        val text: String,
        val locale: Locale?,
        val receive: (SpeechPhysicalEvent) -> Unit,
        var rate: Float,
        var platformId: String? = null,
        var submissionOffset: Int = 0,
        var lastStart: Int = 0,
    )

    private val main = Handler(Looper.getMainLooper())
    private var engine: TextToSpeech? = null
    private var initialized: CompletableDeferred<Int>? = null
    private var utterance: Utterance? = null

    suspend fun prepare() {
        if (engine == null) {
            val ready = CompletableDeferred<Int>()
            initialized = ready
            engine = TextToSpeech(context) { ready.complete(it) }
        }
        val ready = checkNotNull(initialized)
        try {
            check(withTimeout(10_000) { ready.await() } == TextToSpeech.SUCCESS) { "System speech engine unavailable" }
            check(checkNotNull(engine).setOnUtteranceProgressListener(listener) == TextToSpeech.SUCCESS)
        } catch (failure: Throwable) {
            close()
            throw failure
        }
    }

    fun speak(text: String, locale: Locale?, rate: Float, receive: (SpeechPhysicalEvent) -> Unit) {
        require(text.isNotEmpty() && text.length <= TextToSpeech.getMaxSpeechInputLength())
        utterance = Utterance(text, locale, receive, rate)
        resume()
    }

    fun pause() {
        val active = utterance ?: return
        active.platformId = null
        engine?.stop()
    }

    fun resume() {
        val active = checkNotNull(utterance)
        val tts = checkNotNull(engine)
        val ident = UUID.randomUUID().toString()
        val locale = active.locale ?: tts.voice?.locale ?: Locale.getDefault()
        check(tts.setLanguage(locale) >= TextToSpeech.LANG_AVAILABLE) { "System speech voice unavailable" }
        check(tts.setSpeechRate(active.rate) == TextToSpeech.SUCCESS)
        active.platformId = ident
        active.submissionOffset = active.lastStart
        check(tts.speak(active.text.substring(active.submissionOffset), TextToSpeech.QUEUE_FLUSH, Bundle.EMPTY, ident) ==
            TextToSpeech.SUCCESS) { "System speech request rejected" }
    }

    fun hasUtterance(): Boolean = utterance != null

    fun setRate(rate: Float) {
        utterance?.rate = rate
        check(engine?.setSpeechRate(rate) == TextToSpeech.SUCCESS)
    }

    fun stop() {
        utterance = null
        engine?.stop()
    }

    fun close() {
        stop()
        engine?.shutdown()
        engine = null
        initialized = null
    }

    private val listener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String) {
            main.post {
                utterance?.takeIf { it.platformId == utteranceId }?.receive?.invoke(SpeechPhysicalEvent.Started)
            }
        }
        override fun onDone(utteranceId: String) = terminal(utteranceId, SpeechPhysicalEvent.Finished)
        @Deprecated("Android calls this overload on some installed engines")
        override fun onError(utteranceId: String) = terminal(utteranceId, SpeechPhysicalEvent.Failed)
        override fun onError(utteranceId: String, errorCode: Int) = terminal(utteranceId, SpeechPhysicalEvent.Failed)

        override fun onRangeStart(utteranceId: String, start: Int, end: Int, frame: Int) {
            main.post {
                val active = utterance?.takeIf { it.platformId == utteranceId } ?: return@post
                val low = active.submissionOffset + start
                val high = active.submissionOffset + end
                if (start < 0 || low < 0 || low >= high || high > active.text.length ||
                    Character.isLowSurrogate(active.text[low])) return@post
                active.lastStart = low
                active.receive(SpeechPhysicalEvent.Range(low, high))
            }
        }
    }

    private fun terminal(id: String, event: SpeechPhysicalEvent) {
        main.post {
            val active = utterance?.takeIf { it.platformId == id } ?: return@post
            utterance = null
            active.receive(event)
        }
    }
}

@OptIn(UnstableApi::class)
internal class AndroidRemoteSpeech(private val context: Context) {
    private var player: ExoPlayer? = null
    private var listener: Player.Listener? = null
    private var media: File? = null

    fun play(file: File, rate: Float, receive: (SpeechPhysicalEvent) -> Unit) {
        stop()
        media = file
        val driver = player ?: ExoPlayer.Builder(context).build().also {
            player = it
            it.setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(), false)
        }
        val observer = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (listener === this && isPlaying) receive(SpeechPhysicalEvent.Started)
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (listener === this && playbackState == Player.STATE_ENDED) receive(SpeechPhysicalEvent.Finished)
            }
            override fun onPlayerError(error: PlaybackException) {
                if (listener === this) receive(SpeechPhysicalEvent.Failed)
            }
        }
        listener = observer
        driver.addListener(observer)
        driver.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
        driver.setPlaybackSpeed(rate)
        driver.prepare()
        driver.play()
    }

    fun pause() { player?.pause() }
    fun resume() { checkNotNull(player).play() }
    fun setRate(rate: Float) { player?.setPlaybackSpeed(rate) }

    fun stop() {
        listener?.let { player?.removeListener(it) }
        listener = null
        player?.stop()
        player?.clearMediaItems()
        media?.delete()
        media = null
    }

    fun close() {
        stop()
        player?.release()
        player = null
    }
}
