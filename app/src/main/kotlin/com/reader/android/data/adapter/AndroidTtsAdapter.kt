package com.reader.android.data.adapter

data class TtsInitResult(
    val success: Boolean,
    val errorMessage: String? = null
)

interface AndroidTtsAdapter {
    suspend fun init(): TtsInitResult
    suspend fun speak(utterance: TtsUtterance)
    suspend fun stop()
    suspend fun pause()
    suspend fun resume()
    fun isAvailable(): Boolean
    fun getState(): TtsPlaybackState
}

class FakeAndroidTtsAdapter(private val engine: TtsEngine = FakeTtsEngine()) : AndroidTtsAdapter {
    override suspend fun init() = TtsInitResult(success = true)
    override suspend fun speak(utterance: TtsUtterance) = engine.speak(utterance)
    override suspend fun stop() = engine.stop()
    override suspend fun pause() = engine.pause()
    override suspend fun resume() = engine.resume()
    override fun isAvailable() = true
    override fun getState() = engine.getState()
}
