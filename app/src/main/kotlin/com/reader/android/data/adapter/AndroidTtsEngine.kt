package com.reader.android.data.adapter

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
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
    /**
     * TextToSpeech callbacks may arrive out of order after QUEUE_FLUSH/stop.
     * Keying waiters by the platform utterance id prevents a late callback for
     * one correlation from completing another correlation's suspend call.
     */
    private val pendingUtterances = ConcurrentHashMap<String, UtteranceWaiter>()

    private data class UtteranceWaiter(
        val started: CompletableDeferred<Unit> = CompletableDeferred(),
        val completed: CompletableDeferred<Unit> = CompletableDeferred()
    )

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
                        override fun onStart(utteranceId: String?) {
                            state = TtsPlaybackState.PLAYING
                            utteranceId?.let { pendingUtterances[it]?.started?.complete(Unit) }
                        }
                        override fun onDone(utteranceId: String?) {
                            state = TtsPlaybackState.IDLE
                            utteranceId?.let { id ->
                                pendingUtterances.remove(id)?.let { waiter ->
                                    waiter.started.complete(Unit)
                                    waiter.completed.complete(Unit)
                                }
                            }
                        }
                        override fun onError(utteranceId: String?) {
                            state = TtsPlaybackState.ERROR
                            utteranceId?.let { id ->
                                pendingUtterances.remove(id)?.let { waiter ->
                                    val error = RuntimeException("TTS engine error for $id")
                                    waiter.started.completeExceptionally(error)
                                    waiter.completed.completeExceptionally(error)
                                }
                            }
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
        val handle = beginUtterance(utterance)
        try {
            handle.awaitStarted()
            handle.awaitCompletion()
        } catch (error: Exception) {
            handle.cancel()
            throw error
        }
    }

    /**
     * Starts one exact utterance and exposes distinct platform start and
     * terminal waiters. R8 playback uses this instead of treating enqueue as
     * speech success; the legacy [speak] API remains source-compatible.
     */
    internal fun beginUtterance(utterance: TtsUtterance): AndroidTtsUtteranceHandle {
        val engine = engineRef.get()
            ?: throw IllegalStateException("TTS not initialized — call init() first")
        require(utterance.utteranceId.isNotBlank()) { "TTS utteranceId must not be blank" }
        // QUEUE_FLUSH may not deliver a terminal callback for the displaced
        // utterance. Invalidate those exact-id waiters before enqueueing the
        // replacement so none can time out or complete the new correlation.
        cancelPendingUtterances("TTS superseded by ${utterance.utteranceId}")
        val waiter = UtteranceWaiter()
        check(pendingUtterances.putIfAbsent(utterance.utteranceId, waiter) == null) {
            "TTS duplicate utteranceId=${utterance.utteranceId}"
        }
        val languageResult = engine.setLanguage(Locale.forLanguageTag(utterance.language))
        if (languageResult == TextToSpeech.LANG_MISSING_DATA ||
            languageResult == TextToSpeech.LANG_NOT_SUPPORTED
        ) {
            pendingUtterances.remove(utterance.utteranceId, waiter)
            throw IllegalStateException("TTS language not supported: ${utterance.language}")
        }
        engine.setSpeechRate(utterance.speechRate)
        engine.setPitch(utterance.pitch)
        val result = engine.speak(
            utterance.text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            utterance.utteranceId
        )
        if (result == TextToSpeech.ERROR) {
            pendingUtterances.remove(utterance.utteranceId, waiter)
            val error = IllegalStateException("TTS enqueue failed for ${utterance.utteranceId}")
            waiter.started.completeExceptionally(error)
            waiter.completed.completeExceptionally(error)
            throw error
        }
        return AndroidTtsUtteranceHandle(
            utteranceId = utterance.utteranceId,
            awaitStarted = { withTimeout(10_000L) { waiter.started.await() } },
            awaitCompletion = { withTimeout(60_000L) { waiter.completed.await() } },
            cancel = {
                pendingUtterances.remove(utterance.utteranceId, waiter).also { removed ->
                    if (removed) {
                        val cancelled = CancellationException(
                            "TTS utterance cancelled: ${utterance.utteranceId}"
                        )
                        waiter.started.completeExceptionally(cancelled)
                        waiter.completed.completeExceptionally(cancelled)
                    }
                }
            }
        )
    }

    override suspend fun stop() {
        engineRef.get()?.stop()
        state = TtsPlaybackState.STOPPED
        cancelPendingUtterances("TTS engine stopped")
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
        cancelPendingUtterances("TTS engine shutdown")
        engineRef.getAndSet(null)?.shutdown()
        state = TtsPlaybackState.IDLE
        initResult = null
    }

    private fun cancelPendingUtterances(reason: String) {
        val cancelled = CancellationException(reason)
        pendingUtterances.entries.toList().forEach { (id, waiter) ->
            if (pendingUtterances.remove(id, waiter)) {
                waiter.started.completeExceptionally(cancelled)
                waiter.completed.completeExceptionally(cancelled)
            }
        }
    }
}

internal class AndroidTtsUtteranceHandle(
    val utteranceId: String,
    private val awaitStarted: suspend () -> Unit,
    private val awaitCompletion: suspend () -> Unit,
    private val cancel: () -> Boolean
) {
    suspend fun awaitStarted() = awaitStarted.invoke()
    suspend fun awaitCompletion() = awaitCompletion.invoke()
    fun cancel(): Boolean = cancel.invoke()
}
