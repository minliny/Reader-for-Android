package io.reader.android.runtime

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

/** Main-thread Android TextToSpeech adapter. It owns no Core or Reader state. */
internal class AndroidTextToSpeechEngine(context: Context) : ReaderTtsPlaybackEngine, TextToSpeech.OnInitListener {
    private val main = Handler(Looper.getMainLooper())
    private val applicationContext = context.applicationContext
    @Volatile private var speaker: TextToSpeech? = null
    @Volatile override var ready: Boolean = false
        private set
    @Volatile private var listener: ReaderTtsPlaybackEngine.Listener? = null
    private var initializationGeneration = 0L
    private var initializedGeneration = 0L

    override fun setListener(listener: ReaderTtsPlaybackEngine.Listener) { this.listener = listener }

    override fun initialize() {
        main.post {
            if (speaker != null) return@post
            val generation = ++initializationGeneration
            val enginePackage = applicationContext.packageManager
                .queryIntentServices(Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE), PackageManager.MATCH_ALL)
                .firstOrNull()?.serviceInfo?.packageName
            if (enginePackage.isNullOrBlank()) {
                ready = false
                Log.w("ReaderTts", "No Android TextToSpeech service is available")
                listener?.onReady(false)
                return@post
            }
            try {
                speaker = TextToSpeech(applicationContext, this@AndroidTextToSpeechEngine, enginePackage).also {
                    it.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String) { listener?.onStart(utteranceId) }
                        override fun onDone(utteranceId: String) { listener?.onDone(utteranceId) }
                        override fun onError(utteranceId: String) { listener?.onError(utteranceId) }
                        override fun onError(utteranceId: String, errorCode: Int) { listener?.onError(utteranceId) }
                    })
                }
            } catch (error: Throwable) {
                Log.e("ReaderTts", "TextToSpeech initialization failed for $enginePackage", error)
                speaker = null
                ready = false
                listener?.onReady(false)
                return@post
            }
            main.postDelayed({
                if (generation == initializationGeneration && initializedGeneration != generation && speaker != null && !ready) {
                    Log.w("ReaderTts", "TextToSpeech initialization timed out for $enginePackage")
                    listener?.onReady(false)
                }
            }, 8_000)
        }
    }

    override fun onInit(status: Int) {
        // OEM engines may invoke OnInit from a binder thread. Marshal the
        // callback to the same main handler used for construction and speak,
        // otherwise an unexpected callback thread can leave the coordinator in
        // PREPARING without a terminal onReady result.
        main.post {
            val generation = initializationGeneration
            if (generation == 0L || initializedGeneration == generation) return@post
            initializedGeneration = generation
            ready = status == TextToSpeech.SUCCESS && speaker?.setLanguage(Locale.getDefault()) !in
                setOf(TextToSpeech.LANG_MISSING_DATA, TextToSpeech.LANG_NOT_SUPPORTED)
            Log.i("ReaderTts", "TextToSpeech init status=$status ready=$ready")
            listener?.onReady(ready)
        }
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
