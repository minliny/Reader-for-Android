package com.reader.android.data.adapter

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM proof for [TtsSessionController] — P1-4 TTS session closure.
 *
 * Verifies:
 * 1. Paragraph queue: multiple paragraphs spoken sequentially
 * 2. Multi-chapter progression: fetches next chapter via provider
 * 3. Pause/resume replay: re-speaks the same paragraph on resume
 * 4. Stop: halts playback, clears progress
 * 5. AudioFocus: transient loss → auto-pause, gain → auto-resume
 * 6. Progress flow: emits correct chapter/paragraph indices
 *
 * Uses [ControllableTtsAdapter] that blocks on speak() until the test
 * releases it — simulating real TextToSpeech callback semantics.
 */
class TtsSessionControllerJvmTest {

    private fun makeController(
        fake: ControllableTtsAdapter,
        audioFocus: FakeAudioFocusController? = null
    ) = TtsSessionController(
        tts = fake,
        audioFocusController = audioFocus,
        dispatcher = Dispatchers.Unconfined
    )

    @Test
    fun `paragraph queue speaks multiple paragraphs sequentially`() = runBlocking {
        val fake = ControllableTtsAdapter()
        val controller = makeController(fake)
        val text = "First paragraph.\n\nSecond paragraph.\n\nThird paragraph."

        controller.start(TtsChapterRequest(text = text, title = "Chapter 1", index = 0))

        // Release each paragraph and verify order
        fake.waitForSpeak() // paragraph 0
        fake.releaseSpeak()
        fake.waitForSpeak() // paragraph 1
        assertEquals("Second paragraph.", fake.lastSpokenText)
        fake.releaseSpeak()
        fake.waitForSpeak() // paragraph 2
        assertEquals("Third paragraph.", fake.lastSpokenText)
        fake.releaseSpeak()

        // Wait for completion
        waitForIdle(controller)

        assertEquals(3, fake.speakCount)
        assertEquals(TtsPlaybackState.IDLE, controller.stateFlow.value)
    }

    @Test
    fun `progress flow emits correct indices`() = runBlocking {
        val fake = ControllableTtsAdapter()
        val controller = makeController(fake)
        val text = "P1\n\nP2\n\nP3"

        controller.start(TtsChapterRequest(text = text, title = "Ch1", index = 5))

        fake.waitForSpeak()
        var progress = controller.progressFlow.value
        assertNotNull(progress)
        assertEquals(5, progress!!.chapterIndex)
        assertEquals(0, progress.paragraphIndex)
        assertEquals(3, progress.totalParagraphs)
        assertEquals("Ch1", progress.chapterTitle)

        fake.releaseSpeak()
        fake.waitForSpeak()
        progress = controller.progressFlow.value
        assertEquals(1, progress!!.paragraphIndex)

        fake.releaseSpeak()
        fake.waitForSpeak()
        progress = controller.progressFlow.value
        assertEquals(2, progress!!.paragraphIndex)

        fake.releaseSpeak()
        waitForIdle(controller)
    }

    @Test
    fun `multi-chapter progression fetches next chapter via provider`() = runBlocking {
        val fake = ControllableTtsAdapter()
        val controller = makeController(fake)
        val provider = object : TtsChapterProvider {
            private var called = false
            override suspend fun fetchChapter(index: Int): TtsChapterRequest? {
                if (called) return null
                called = true
                return TtsChapterRequest(text = "Chapter 2 text", title = "Chapter 2", index = 1)
            }
        }

        controller.start(
            TtsChapterRequest(text = "Chapter 1 text", title = "Chapter 1", index = 0),
            provider
        )

        // Speak chapter 1
        fake.waitForSpeak()
        assertEquals("Chapter 1 text", fake.lastSpokenText)
        fake.releaseSpeak()

        // Speak chapter 2
        fake.waitForSpeak()
        assertEquals("Chapter 2 text", fake.lastSpokenText)
        assertEquals(1, controller.progressFlow.value!!.chapterIndex)
        assertEquals("Chapter 2", controller.progressFlow.value!!.chapterTitle)
        fake.releaseSpeak()

        // No more chapters — should go IDLE
        waitForIdle(controller)
        assertEquals(TtsPlaybackState.IDLE, controller.stateFlow.value)
    }

    @Test
    fun `pause holds paragraph index and resume re-speaks same paragraph`() = runBlocking {
        val fake = ControllableTtsAdapter()
        val controller = makeController(fake)
        val text = "P1\n\nP2\n\nP3"

        controller.start(TtsChapterRequest(text = text, title = "Ch", index = 0))

        // Speak P1
        fake.waitForSpeak()
        assertEquals("P1", fake.lastSpokenText)
        fake.releaseSpeak()

        // Speak P2 — pause during speak
        fake.waitForSpeak()
        assertEquals("P2", fake.lastSpokenText)
        controller.pause()
        assertEquals(TtsPlaybackState.PAUSED, controller.stateFlow.value)
        // No extra releaseSpeak() here: controller.pause() → tts.pause() already
        // completes the pending speak, so the loop sees `paused` and holds
        // currentParagraphIndex at 1. An extra releaseSpeak() would complete
        // the NEXT pending deferred, causing resume's re-speak to return
        // immediately and the loop to advance to P3 — exactly the bug this
        // test catches.

        // Wait a moment for the loop to process
        delay(100)

        // Resume — should re-speak P2 (index 1, not 2)
        controller.resume()
        fake.waitForSpeak()
        assertEquals("P2", fake.lastSpokenText) // same paragraph
        assertEquals(1, controller.progressFlow.value!!.paragraphIndex)
        fake.releaseSpeak()

        // Continue to P3
        fake.waitForSpeak()
        assertEquals("P3", fake.lastSpokenText)
        fake.releaseSpeak()

        waitForIdle(controller)
    }

    @Test
    fun `stop halts playback and clears progress`() = runBlocking {
        val fake = ControllableTtsAdapter()
        val controller = makeController(fake)
        val text = "P1\n\nP2\n\nP3"

        controller.start(TtsChapterRequest(text = text, title = "Ch", index = 0))

        fake.waitForSpeak()
        controller.stop()
        fake.releaseSpeak() // release the blocked speak

        delay(100)
        assertEquals(TtsPlaybackState.STOPPED, controller.stateFlow.value)
        assertNull(controller.progressFlow.value)
        assertFalse(controller.isStarted())
    }

    @Test
    fun `audio focus transient loss auto-pauses and gain auto-resumes`() = runBlocking {
        val fake = ControllableTtsAdapter()
        val fakeAudioFocus = FakeAudioFocusController()
        val controller = makeController(fake, fakeAudioFocus)

        controller.start(TtsChapterRequest(text = "P1\n\nP2", title = "Ch", index = 0))

        fake.waitForSpeak()
        assertEquals(TtsPlaybackState.PLAYING, controller.stateFlow.value)

        // Simulate transient focus loss
        fakeAudioFocus.simulateLossTransient()
        delay(100)
        assertEquals(TtsPlaybackState.PAUSED, controller.stateFlow.value)

        // Simulate focus gain
        fakeAudioFocus.simulateGain()
        delay(100)
        assertEquals(TtsPlaybackState.PLAYING, controller.stateFlow.value)

        fake.releaseSpeak()
        fake.waitForSpeak()
        fake.releaseSpeak()
        waitForIdle(controller)
    }

    @Test
    fun `audio focus permanent loss auto-stops`() = runBlocking {
        val fake = ControllableTtsAdapter()
        val fakeAudioFocus = FakeAudioFocusController()
        val controller = makeController(fake, fakeAudioFocus)

        controller.start(TtsChapterRequest(text = "P1\n\nP2", title = "Ch", index = 0))

        fake.waitForSpeak()
        assertTrue(controller.isStarted())

        // Simulate permanent focus loss
        fakeAudioFocus.simulateLoss()
        delay(100)

        assertEquals(TtsPlaybackState.STOPPED, controller.stateFlow.value)
        assertFalse(controller.isStarted())
        fake.releaseSpeak()
    }

    // ── Helpers ──

    private suspend fun waitForIdle(controller: TtsSessionController, timeoutMs: Long = 2000L) {
        withTimeoutOrNull(timeoutMs) {
            while (controller.isStarted()) {
                delay(50)
            }
        }
    }

    /**
     * Fake TTS adapter that blocks on [speak] until [releaseSpeak] is called.
     * This simulates the real TextToSpeech callback semantics where speak()
     * suspends until onDone fires.
     */
    private class ControllableTtsAdapter : AndroidTtsAdapter {
        private var pendingSpeak = CompletableDeferred<Unit>()
        @Volatile var speakCount = 0
        @Volatile var lastSpokenText = ""
        @Volatile private var state = TtsPlaybackState.IDLE

        override suspend fun init() = TtsInitResult(success = true)

        override suspend fun speak(utterance: TtsUtterance) {
            speakCount++
            lastSpokenText = utterance.text
            state = TtsPlaybackState.PLAYING
            // Block until the test releases this utterance
            pendingSpeak.await()
            pendingSpeak = CompletableDeferred()
        }

        fun releaseSpeak() {
            pendingSpeak.complete(Unit)
        }

        suspend fun waitForSpeak(timeoutMs: Long = 2000L) {
            withTimeoutOrNull(timeoutMs) {
                // Poll until speak() is called (speakCount increments)
                val target = speakCount + 1
                while (speakCount < target) {
                    delay(10)
                }
            }
        }

        override suspend fun stop() {
            state = TtsPlaybackState.STOPPED
            pendingSpeak.complete(Unit)
            pendingSpeak = CompletableDeferred()
        }

        override suspend fun pause() {
            state = TtsPlaybackState.PAUSED
            pendingSpeak.complete(Unit)
            pendingSpeak = CompletableDeferred()
        }

        override suspend fun resume() {
            state = TtsPlaybackState.PLAYING
        }

        override fun isAvailable() = true

        override fun getState() = state
    }

    /**
     * Fake audio focus controller that records callbacks and lets tests
     * simulate focus changes.
     */
    private class FakeAudioFocusController : AudioFocusController {
        var onLossTransient: (() -> Unit)? = null
        var onGain: (() -> Unit)? = null
        var onLoss: (() -> Unit)? = null
        var focusRequested = false
        var focusAbandoned = false

        override fun requestFocus(
            onLossTransient: () -> Unit,
            onGain: () -> Unit,
            onLoss: () -> Unit
        ): Boolean {
            this.onLossTransient = onLossTransient
            this.onGain = onGain
            this.onLoss = onLoss
            focusRequested = true
            return true
        }

        override fun abandonFocus() {
            focusAbandoned = true
        }

        fun simulateLossTransient() { onLossTransient?.invoke() }
        fun simulateGain() { onGain?.invoke() }
        fun simulateLoss() { onLoss?.invoke() }
    }
}
