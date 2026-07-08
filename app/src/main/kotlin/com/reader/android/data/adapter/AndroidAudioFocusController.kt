package com.reader.android.data.adapter

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build

/**
 * Real [AudioFocusController] backed by Android's [AudioManager].
 *
 * Uses [AudioAttributes] for spoken content (USAGE_MEDIA,
 * CONTENT_TYPE_SPEECH) so the system knows to route through the speaker
 * and apply appropriate ducking. On API 26+ it uses [AudioFocusRequest];
 * on older API levels it falls back to the deprecated
 * [AudioManager.requestAudioFocus] overload.
 *
 * **Callbacks**: all three callbacks are invoked on the AudioManager's
 * binder thread. Callers must marshal back to the main thread if they
 * touch UI state — [TtsSessionController] does this by launching a
 * coroutine on [Dispatchers.Main] inside each callback.
 */
class AndroidAudioFocusController(private val context: Context) : AudioFocusController {

    private val audioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var onLossTransient: (() -> Unit)? = null
    private var onGain: (() -> Unit)? = null
    private var onLoss: (() -> Unit)? = null

    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private val legacyListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> onLossTransient?.invoke()
            AudioManager.AUDIOFOCUS_GAIN -> onGain?.invoke()
            AudioManager.AUDIOFOCUS_LOSS -> onLoss?.invoke()
        }
    }

    private var focusRequest: AudioFocusRequest? = null

    override fun requestFocus(
        onLossTransient: () -> Unit,
        onGain: () -> Unit,
        onLoss: () -> Unit
    ): Boolean {
        this.onLossTransient = onLossTransient
        this.onGain = onGain
        this.onLoss = onLoss

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attributes)
                .setOnAudioFocusChangeListener(legacyListener)
                .build()
            focusRequest = request
            audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                legacyListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    override fun abandonFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            focusRequest = null
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(legacyListener)
        }
        onLossTransient = null
        onGain = null
        onLoss = null
    }
}
