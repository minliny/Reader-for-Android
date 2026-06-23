package com.reader.android.data.adapter

import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidPlatformRuntimeInstrumentedSmokeTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun webViewLoadsLocalHtmlAndExportsRedactedDomSmoke() {
        val result = StringBuilder()
        val finished = CountDownLatch(1)
        lateinit var webView: WebView

        instrumentation.runOnMainSync {
            webView = WebView(context)
            AndroidWebViewRuntimeHost(webView).prepareForRuntime(
                allowJavaScript = true,
                allowDomStorage = false
            )
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    view.evaluateJavascript(
                        "document.querySelector('article.chapter p').textContent"
                    ) { value ->
                        result.append(value.orEmpty())
                        finished.countDown()
                    }
                }
            }
            webView.loadDataWithBaseURL(
                "https://fixture.invalid/book/1",
                "<html><body><article class='chapter'><p>fixture-dom-ok</p></article></body></html>",
                "text/html",
                "UTF-8",
                null
            )
        }

        assertTrue("WebView DOM callback timed out", finished.await(5, TimeUnit.SECONDS))
        assertTrue(result.toString().contains("fixture-dom-ok"))

        instrumentation.runOnMainSync {
            webView.destroy()
        }
    }

    @Test
    fun cookieManagerMirrorRedactsCookieValues() {
        val sourceUrl = "https://fixture.invalid/session"
        val cookieManager = CookieManager.getInstance()
        cookieManager.removeAllCookies(null)
        cookieManager.flush()
        cookieManager.setCookie(sourceUrl, "sid=synthetic-cookie; Path=/; HttpOnly")
        cookieManager.flush()

        val evidence = AndroidCookieManagerStore(cookieManager).redactedMirrorEvidence(sourceUrl)

        assertTrue(evidence.contains("count:1"))
        assertTrue(evidence.contains("values:REDACTED"))
        assertFalse(evidence.contains("synthetic-cookie"))
        assertFalse(evidence.contains(sourceUrl))
    }

    @Test
    fun androidKeystoreCanRevokeSyntheticCredential() {
        val store = AndroidKeystoreCredentialStore(namespace = "reader_android_runtime_smoke")
        val record = store.save("instrumented.synthetic", "synthetic-secret-value")

        assertTrue(store.load(record) == "synthetic-secret-value")
        assertTrue(store.revoke(record))
        assertThrows(Exception::class.java) {
            store.load(record)
        }
    }

    @Test
    fun safPermissionDeniedMapsToRedactedFailClosedEvidence() {
        val adapter = AndroidContentUriLocalBookAdapter(context.contentResolver)
        val evidence = adapter.takePersistableReadPermission(
            Uri.parse("content://com.reader.android.instrumented/document/book.txt"),
            grantFlags = 0
        )

        assertFalse(evidence.persistableReadPermissionGranted)
        assertTrue(evidence.permissionDeniedMapped)
        assertTrue(evidence.redactedUri.startsWith("content://provider.redacted"))
        assertFalse(evidence.redactedUri.contains("com.reader.android.instrumented"))
        assertFalse(evidence.redactedUri.contains("book.txt"))
    }
}
