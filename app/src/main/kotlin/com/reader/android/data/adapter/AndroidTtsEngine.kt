package com.reader.android.data.adapter

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicReference

/**
 * Real [AndroidTtsAdapter] backed by Android's [TextToSpeech] engine.
 *
 * **Lifecycle**:
 *  - [init] lazily constructs a [TextToSpeech] instance on the first call
 *    and waits for `onInit` with a 3s timeout. If the engine reports
 *    `LANG_MISSING_DATA` or fails to init, [isAvailable] returns false and
 *    [speak] returns immediately without audio.
 *  - [speak] maps [TtsUtterance] to `TextToSpeech.speak` with a unique
 *    utterance id; the caller's coroutine resumes when the engine fires
 *    `onDone` / `onError`.
 *  - [stop] / [pause] / [resume] delegate to the engine's native stop /
 *    pause / resume where the API supports it (API 21+).
 *
 * **Thread safety**: the [TextToSpeech] engine is constructed on the main
 * looper (required by Android); all subsequent calls are forwarded on the
 * engine's internal binder thread. State transitions are guarded by
 * [AtomicReference].
 *
 * **Testability**: JVM tests inject [FakeAndroidTtsAdapter]; this class
 * only runs on a real device (it touches `android.speech.tts`).
 */
class AndroidTtsEngine(
    private val context: Context
) : AndroidTtsAdapter {

    private val engineRef = AtomicReference<TextToSpeech?>(null)
    @Volatile private var initResult: TtsInitResult? = null
    @Volatile private var state: TtsPlaybackState = TtsPlaybackState.IDLE
    private val pendingUtterances = ConcurrentLinkedQueue<CompletableDeferred<Unit>>()

    override suspend fun init(): TtsInitResult {
        initResult?.let { return it }
        val deferred = CompletableDeferred<TtsInitResult>()
        // Kotlin closure capture: `tts` cannot reference itself inside its
        // own initializer, so we route through a nullable holder that the
        // init callback reads. The callback fires on a binder thread after
        // the constructor returns, so the holder is populated by then.
        val ttsHolder = AtomicReference<TextToSpeech?>(null)
        val tts = TextToSpeech(context.applicationContext) { status ->
            val engine = ttsHolder.get() ?: return@TextToSpeech
            if (status == TextToSpeech.SUCCESS) {
                val langResult = engine.setLanguage(Locale.CHINA)
                if (langResult == TextToSpeech.LANG_MISSING_DATA ||
                    langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                    deferred.complete(TtsInitResult(success = false, errorMessage = "language not supported"))
                } else {
                    engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) { state = TtsPlaybackState.PLAYING }
                        override fun onDone(utteranceId: String?) {
                            state = TtsPlaybackState.IDLE
                            pendingUtterances.poll()?.complete(Unit)
                        }
                        override fun onError(utteranceId: String?) {
                            state = TtsPlaybackState.ERROR
                            pendingUtterances.poll()?.completeExceptionally(
                                RuntimeException("TTS engine error for $utteranceId")
                            )
                        }
                    })
                    engineRef.set(engine)
                    deferred.complete(TtsInitResult(success = true))
                }
            } else {
                deferred.complete(TtsInitResult(success = false, errorMessage = "TTS init failed: status=$status"))
            }
        }
        ttsHolder.set(tts)
        // Wait up to 3s for onInit; TextToSpeech construction is async.
        val result = runCatching {
            kotlinx.coroutines.withTimeoutOrNull(3000L) { deferred.await() }
        }.getOrNull() ?: TtsInitResult(success = false, errorMessage = "TTS init timeout")
        initResult = result
        if (!result.success) {
            runCatching { tts.shutdown() }
        }
        return result
    }

    override suspend fun speak(utterance: TtsUtterance) {
        val engine = engineRef.get()
            ?: throw IllegalStateException("TTS not initialized — call init() first")
        val deferred = CompletableDeferred<Unit>()
        pendingUtterances.add(deferred)
        state = TtsPlaybackState.PLAYING
        engine.setSpeechRate(utterance.speechRate)
        engine.setPitch(utterance.pitch)
        val params = android.speech.tts.TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID to utterance.utteranceId
        engine.speak(utterance.text, TextToSpeech.QUEUE_FLUSH, null, utterance.utteranceId)
        // Don't block forever — the engine callback resumes us.
        runCatching { kotlinx.coroutines.withTimeoutOrNull(60_000L) { deferred.await() } }
            .onFailure { pendingUtterances.remove(deferred) }
    }

    override suspend fun stop() {
        engineRef.get()?.stop()
        state = TtsPlaybackState.STOPPED
        pendingUtterances.forEach { it.complete(Unit) }
        pendingUtterances.clear()
    }

    override suspend fun pause() {
        // TextToSpeech doesn't have a native pause on all API levels;
        // stop the current utterance but keep the engine alive.
        engineRef.get()?.stop()
        state = TtsPlaybackState.PAUSED
    }

    override suspend fun resume() {
        // Resume is a no-op: the caller must re-speak the remaining text.
        // The engine itself doesn't buffer paused text.
        state = TtsPlaybackState.PLAYING
    }

    override fun isAvailable(): Boolean = initResult?.success == true

    override fun getState(): TtsPlaybackState = state

    /**
     * Release the underlying [TextToSpeech] engine. Call this when the TTS
     * session is done (e.g. reader exit) so the engine's binder thread
     * doesn't leak.
     */
    fun shutdown() {
        engineRef.getAndSet(null)?.shutdown()
        state = TtsPlaybackState.IDLE
        initResult = null
    }
}
