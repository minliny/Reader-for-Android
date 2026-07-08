package com.reader.android.data.adapter

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

/**
 * Progress update emitted by [TtsSessionController] on each paragraph boundary.
 * The AppShellViewModel collects this and dispatches [UpdateTtsProgress] so
 * the reducer's `activeSession.ttsSentenceIndex` / `ttsChapterIndex` reflect
 * the real playback position — closing the "UI action → Host/Core writeback"
 * loop required by P1-4.
 */
data class TtsProgressUpdate(
    val chapterIndex: Int,
    val paragraphIndex: Int,
    val totalParagraphs: Int,
    val chapterTitle: String
)

/**
 * A single chapter to be spoken by the TTS session.
 */
data class TtsChapterRequest(
    val text: String,
    val title: String,
    val index: Int
)

/**
 * Provides the next chapter for multi-chapter TTS progression.
 * Implementations fetch chapter text from Core (bookApi.content) or the
 * local chapter cache. Returns null when there are no more chapters.
 */
interface TtsChapterProvider {
    suspend fun fetchChapter(index: Int): TtsChapterRequest?
}

/**
 * Pluggable audio focus controller so JVM tests can inject a fake.
 * The real implementation ([AndroidAudioFocusController]) uses
 * [AudioManager] + [android.media.AudioFocusRequest].
 */
interface AudioFocusController {
    /**
     * Requests audio focus for spoken content.
     * Returns true if focus was granted.
     * - [onLossTransient]: focus lost transiently (e.g. notification sound) → pause
     * - [onGain]: focus regained → resume (if paused by transient loss)
     * - [onLoss]: focus permanently lost (e.g. another media app) → stop
     */
    fun requestFocus(
        onLossTransient: () -> Unit,
        onGain: () -> Unit,
        onLoss: () -> Unit
    ): Boolean

    /** Releases audio focus. */
    fun abandonFocus()
}

/**
 * Session-level TTS orchestrator sitting above [AndroidTtsAdapter].
 *
 * **Why this exists (P1-4)**: The raw [AndroidTtsEngine] speaks a single
 * utterance per `speak()` call with `QUEUE_FLUSH` — no paragraph queue,
 * no multi-chapter progression, no pause/resume replay, no audio focus
 * / becoming-noisy recovery, and no progress writeback to the reducer.
 * [TtsSessionController] closes all five gaps:
 *
 * 1. **Playback queue**: splits chapter text into paragraphs and speaks
 *    them sequentially via [ChapterTextFeeder] semantics.
 * 2. **Multi-chapter progression**: when the current chapter's paragraphs
 *    are exhausted, fetches the next chapter via [TtsChapterProvider]
 *    (if registered) and continues.
 * 3. **Real pause/resume**: on pause, the engine is stopped but the
 *    current paragraph index is held; on resume, the same paragraph is
 *    re-spoken (TextToSpeech has no native mid-utterance resume).
 * 4. **Audio focus / becoming-noisy recovery**: transient focus loss
 *    auto-pauses, focus regain auto-resumes, permanent loss stops;
 *    headphone unplug auto-pauses.
 * 5. **Progress writeback**: [progressFlow] emits on each paragraph
 *    boundary so the AppShellViewModel can dispatch `UpdateTtsProgress`.
 *
 * **Thread safety**: the playback loop runs on [Dispatchers.Main] (TTS
 * engine requires main-thread access). State transitions are guarded by
 * `@Volatile` flags. The controller is a singleton held by [AppProvider].
 */
class TtsSessionController(
    private val tts: AndroidTtsAdapter,
    private val audioFocusController: AudioFocusController? = null,
    private val context: Context? = null,
    /**
     * Coroutine context for the playback loop. Defaults to [Dispatchers.Main]
     * because TextToSpeech requires main-thread access. JVM tests inject
     * [Dispatchers.Unconfined] (or any test dispatcher) since the fake TTS
     * adapter doesn't touch Android APIs.
     */
    private val dispatcher: CoroutineContext = Dispatchers.Main
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private var sessionJob: Job? = null

    @Volatile private var paragraphs: List<String> = emptyList()
    @Volatile private var currentParagraphIndex = 0
    @Volatile private var currentChapterIndex = 0
    @Volatile private var currentChapterTitle = ""
    @Volatile private var paused = false
    @Volatile private var stopped = false
    @Volatile private var chapterProvider: TtsChapterProvider? = null
    @Volatile private var started = false

    private val _progressFlow = MutableStateFlow<TtsProgressUpdate?>(null)
    val progressFlow: StateFlow<TtsProgressUpdate?> = _progressFlow.asStateFlow()

    private val _stateFlow = MutableStateFlow(TtsPlaybackState.IDLE)
    val stateFlow: StateFlow<TtsPlaybackState> = _stateFlow.asStateFlow()

    private var becomingNoisyReceiver: BroadcastReceiver? = null

    /**
     * Sets the chapter provider for multi-chapter progression.
     * Call this before [start] if the session should advance to the next
     * chapter automatically.
     */
    fun setChapterProvider(provider: TtsChapterProvider?) {
        this.chapterProvider = provider
    }

    /**
     * Starts a TTS session with the given initial chapter.
     * If a [TtsChapterProvider] is registered (via [setChapterProvider]),
     * playback continues into the next chapter when the current one ends.
     *
     * Returns true if the engine initialized and playback started.
     */
    suspend fun start(
        initialChapter: TtsChapterRequest,
        provider: TtsChapterProvider? = null
    ): Boolean {
        if (started) {
            stop()
        }
        val initResult = tts.init()
        if (!initResult.success) return false

        this.chapterProvider = provider ?: this.chapterProvider
        this.paused = false
        this.stopped = false
        this.started = true
        this.currentChapterIndex = initialChapter.index
        this.currentChapterTitle = initialChapter.title
        this.paragraphs = splitParagraphs(initialChapter.text)
        this.currentParagraphIndex = 0

        audioFocusController?.requestFocus(
            onLossTransient = { scope.launch { pause() } },
            onGain = { scope.launch { if (paused) resume() } },
            onLoss = { scope.launch { stop() } }
        )

        registerBecomingNoisy()

        sessionJob?.cancel()
        sessionJob = scope.launch { playbackLoop() }
        return true
    }

    private suspend fun playbackLoop() {
        while (!stopped) {
            if (paused) {
                delay(50)
                continue
            }
            if (currentParagraphIndex >= paragraphs.size) {
                // Try to fetch next chapter
                val nextIndex = currentChapterIndex + 1
                val nextChapter = chapterProvider?.fetchChapter(nextIndex)
                if (nextChapter == null) {
                    _stateFlow.value = TtsPlaybackState.IDLE
                    started = false
                    return
                }
                currentChapterIndex = nextChapter.index
                currentChapterTitle = nextChapter.title
                paragraphs = splitParagraphs(nextChapter.text)
                currentParagraphIndex = 0
                continue
            }

            val text = paragraphs[currentParagraphIndex]
            val utteranceId = "tts-$currentChapterIndex-$currentParagraphIndex"

            _progressFlow.value = TtsProgressUpdate(
                chapterIndex = currentChapterIndex,
                paragraphIndex = currentParagraphIndex,
                totalParagraphs = paragraphs.size,
                chapterTitle = currentChapterTitle
            )
            _stateFlow.value = TtsPlaybackState.PLAYING

            try {
                tts.speak(TtsUtterance(text = text, utteranceId = utteranceId))
            } catch (e: CancellationException) {
                // sessionJob.cancel() propagates as CancellationException through
                // the suspended speak() call — re-throw so stop() owns the final
                // state (STOPPED), not the generic error path.
                throw e
            } catch (e: Exception) {
                _stateFlow.value = TtsPlaybackState.ERROR
                started = false
                return
            }

            // Only advance if not paused during speak() — on pause the
            // engine was stopped mid-utterance, so we re-speak the same
            // paragraph on resume.
            if (!paused && !stopped) {
                currentParagraphIndex++
            }
        }
        started = false
    }

    suspend fun pause() {
        if (paused || stopped || !started) return
        paused = true
        tts.pause()
        _stateFlow.value = TtsPlaybackState.PAUSED
    }

    suspend fun resume() {
        if (!paused || stopped || !started) return
        paused = false
        _stateFlow.value = TtsPlaybackState.PLAYING
        // The playback loop will re-speak currentParagraphIndex (unchanged
        // during pause) — closing the "real pause/resume" gap.
    }

    suspend fun stop() {
        stopped = true
        paused = false
        started = false
        tts.stop()
        audioFocusController?.abandonFocus()
        unregisterBecomingNoisy()
        sessionJob?.cancel()
        _stateFlow.value = TtsPlaybackState.STOPPED
        _progressFlow.value = null
    }

    /**
     * Permanent teardown — releases the underlying TTS engine.
     * Call when the reader exits so the engine's binder thread doesn't leak.
     */
    fun shutdown() {
        scope.launch { stop() }
        if (tts is AndroidTtsEngine) {
            tts.shutdown()
        }
    }

    fun isStarted(): Boolean = started
    fun isPaused(): Boolean = paused
    fun getCurrentProgress(): TtsProgressUpdate? = _progressFlow.value

    private fun splitParagraphs(text: String): List<String> =
        text.split(Regex("\n\n+")).filter { it.isNotBlank() }

    private fun registerBecomingNoisy() {
        val ctx = context ?: return
        becomingNoisyReceiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                scope.launch { pause() }
            }
        }
        runCatching {
            ctx.registerReceiver(
                becomingNoisyReceiver,
                IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            )
        }
    }

    private fun unregisterBecomingNoisy() {
        val ctx = context ?: return
        becomingNoisyReceiver?.let {
            runCatching { ctx.unregisterReceiver(it) }
        }
        becomingNoisyReceiver = null
    }
}
