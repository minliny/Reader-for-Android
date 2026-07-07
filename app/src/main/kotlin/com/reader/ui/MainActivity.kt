package com.reader.ui

import android.os.Bundle
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.reader.api.ReaderCoreClient
import com.reader.ui.motion.SystemAnimationScaleReducedMotionResolver
import com.reader.ui.theme.ReaderTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Slice A — WebView real binding: construct the host WebView and bind
        // it to the Core client so `webview.evaluateJavaScript` dispatches
        // actually execute against an Activity-attached WebView. Until this
        // runs, dispatch returns REQUIRES_UI_CONTEXT (fail-closed).
        //
        // The WebView is kept off-screen (it is created but not added to the
        // view hierarchy) — its sole purpose is to provide a renderer handle
        // for `WebView.evaluateJavascript`. Core never paints UI through it;
        // user-visible book content is rendered by the Compose reader
        // (`ImmersiveReadingScreen`).
        val webView = WebView(this)
        runCatching { ReaderCoreClient.get().bindWebViewExecutor(webView) }

        setContent {
            val reducedMotionResolver = remember {
                SystemAnimationScaleReducedMotionResolver(this@MainActivity)
            }
            ReaderTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    ReaderApp(reducedMotionResolver = reducedMotionResolver)
                }
            }
        }
    }

    override fun onDestroy() {
        // Revert to fail-closed so any post-Activity dispatch (e.g. from a
        // background work coroutine that outlives the Activity) cannot
        // reference a destroyed WebView. Real users re-bind on the next
        // Activity launch via `onCreate`.
        runCatching { ReaderCoreClient.get().bindWebViewExecutor(null) }
        super.onDestroy()
    }
}
