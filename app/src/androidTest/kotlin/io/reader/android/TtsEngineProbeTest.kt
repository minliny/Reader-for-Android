package io.reader.android

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.reader.android.runtime.AndroidTextToSpeechEngine
import io.reader.android.runtime.ReaderTtsPlaybackEngine
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * System boundary probe for A5. It does not open Reader UI or touch production
 * data; it only proves whether Android's TextToSpeech adapter emits onReady.
 */
@RunWith(AndroidJUnit4::class)
class TtsEngineProbeTest {
    @Test(timeout = 15_000)
    fun systemTtsDeliversInitializationCallback() {
        val ready = CountDownLatch(1)
        val success = AtomicBoolean(false)
        val engine = AndroidTextToSpeechEngine(ApplicationProvider.getApplicationContext<Context>())
        engine.setListener(object : ReaderTtsPlaybackEngine.Listener {
            override fun onReady(value: Boolean) {
                success.set(value)
                Log.i("ReaderTtsProbe", "onReady success=$value")
                ready.countDown()
            }

            override fun onStart(utteranceId: String) = Unit
            override fun onDone(utteranceId: String) = Unit
            override fun onError(utteranceId: String) = Unit
        })
        try {
            engine.initialize()
            assertTrue(
                "Android TextToSpeech did not deliver onReady within 10s; inspect ReaderTts logs for service and callback stages",
                ready.await(10, TimeUnit.SECONDS),
            )
            assertTrue(
                "Android TextToSpeech delivered onReady(false); the platform engine or locale is unavailable",
                success.get(),
            )
        } finally {
            engine.shutdown()
        }
    }
}
