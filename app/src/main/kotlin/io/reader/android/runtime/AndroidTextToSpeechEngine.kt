package io.reader.android.runtime

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/** Main-thread Android TextToSpeech adapter. It owns no Core or Reader state. */
internal class AndroidTextToSpeechEngine(context: Context) : ReaderTtsPlaybackEngine, TextToSpeech.OnInitListener {
    private val main = Handler(Looper.getMainLooper())
    private val applicationContext = context.applicationContext
    @Volatile private var speaker: TextToSpeech? = null
    @Volatile override var ready: Boolean = false
        private set
    @Volatile private var listener: ReaderTtsPlaybackEngine.Listener? = null

    override fun setListener(listener: ReaderTtsPlaybackEngine.Listener) { this.listener = listener }

    override fun initialize() {
        main.post {
            if (speaker != null) return@post
            val enginePackage = applicationContext.packageManager
                .queryIntentServices(Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE), PackageManager.MATCH_ALL)
                .firstOrNull()?.serviceInfo?.packageName
            if (enginePackage.isNullOrBlank()) {
                ready = false
                listener?.onReady(false)
                return@post
            }
            speaker = TextToSpeech(applicationContext, this@AndroidTextToSpeechEngine, enginePackage).also {
                it.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String) { listener?.onStart(utteranceId) }
                    override fun onDone(utteranceId: String) { listener?.onDone(utteranceId) }
                    override fun onError(utteranceId: String) { listener?.onError(utteranceId) }
                    override fun onError(utteranceId: String, errorCode: Int) { listener?.onError(utteranceId) }
                })
            }
        }
    }

    override fun onInit(status: Int) {
        check(Looper.myLooper() == Looper.getMainLooper()) { "TTS_INIT_MAIN_THREAD_REQUIRED" }
        ready = status == TextToSpeech.SUCCESS && speaker?.setLanguage(Locale.getDefault()) !in
            setOf(TextToSpeech.LANG_MISSING_DATA, TextToSpeech.LANG_NOT_SUPPORTED)
        listener?.onReady(ready)
    }

    override fun speak(utteranceId: String, text: String) {
        main.post {
            val value = speaker
            if (value == null || !ready) { listener?.onError(utteranceId); return@post }
            val params = Bundle().apply { putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId) }
            if (value.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId) != TextToSpeech.SUCCESS) listener?.onError(utteranceId)
        }
    }

    override fun setRate(rate: Int) {
        main.post { speaker?.setSpeechRate(rate.coerceIn(1, 10) / 5f) }
    }

    override fun stop() { main.post { speaker?.stop() } }

    override fun shutdown() {
        main.post {
            ready = false
            speaker?.shutdown()
            speaker = null
        }
    }
}
