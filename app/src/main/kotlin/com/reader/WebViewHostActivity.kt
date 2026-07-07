package com.reader

import android.app.Activity
import android.os.Bundle

/**
 * Minimal no-UI Activity used as a host for [android.webkit.WebView] in
 * instrumented rebind tests. It does not call `setContentView`; the test
 * only needs a real Activity-backed `Context` so `WebView(context)` can
 * initialize its renderer thread.
 *
 * No Compose, no theme — the smallest possible Activity on the
 * `android.app.Activity` base class so the test does not pull in
 * `androidx.activity` / Material dependencies.
 *
 * Declared in main (not androidTest) so `ActivityScenario.launch` resolves
 * it to the target process (`com.reader.android`), not the test process.
 * `exported=false` and no intent-filter — no production code launches it.
 */
class WebViewHostActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Intentionally no setContentView — see class kdoc.
    }
}
