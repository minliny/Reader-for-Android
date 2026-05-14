package com.reader.android.data.adapter

data class TtsUtterance(
    val text: String,
    val utteranceId: String,
    val language: String = "zh-CN",
    val speechRate: Float = 1.0f,
    val pitch: Float = 1.0f
)

enum class TtsPlaybackState {
    IDLE, PLAYING, PAUSED, STOPPED, ERROR
}

interface TtsEngine {
    suspend fun speak(utterance: TtsUtterance)
    suspend fun stop()
    suspend fun pause()
    suspend fun resume()
    fun getState(): TtsPlaybackState
}

class FakeTtsEngine : TtsEngine {
    @Volatile private var state = TtsPlaybackState.IDLE

    override suspend fun speak(utterance: TtsUtterance) {
        state = TtsPlaybackState.PLAYING
    }

    override suspend fun stop() {
        state = TtsPlaybackState.STOPPED
    }

    override suspend fun pause() {
        state = TtsPlaybackState.PAUSED
    }

    override suspend fun resume() {
        state = TtsPlaybackState.PLAYING
    }

    override fun getState(): TtsPlaybackState = state
}
