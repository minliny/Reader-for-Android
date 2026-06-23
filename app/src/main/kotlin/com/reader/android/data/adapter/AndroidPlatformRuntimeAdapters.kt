package com.reader.android.data.adapter

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.webkit.CookieManager
import android.webkit.WebView
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class AndroidContentUriAccessEvidence(
    val redactedUri: String,
    val persistableReadPermissionGranted: Boolean,
    val permissionDeniedMapped: Boolean,
    val bytePrefixLength: Int,
    val encoding: EncodingResult? = null
)

class AndroidContentUriLocalBookAdapter(
    private val contentResolver: ContentResolver
) {
    fun takePersistableReadPermission(uri: Uri, grantFlags: Int): AndroidContentUriAccessEvidence {
        return try {
            val readFlag = grantFlags and Intent.FLAG_GRANT_READ_URI_PERMISSION
            if (readFlag == 0) {
                return AndroidContentUriAccessEvidence(uri.toString().redactedContentUri(), false, true, 0)
            }
            contentResolver.takePersistableUriPermission(uri, readFlag)
            AndroidContentUriAccessEvidence(uri.toString().redactedContentUri(), true, false, 0)
        } catch (_: SecurityException) {
            AndroidContentUriAccessEvidence(uri.toString().redactedContentUri(), false, true, 0)
        }
    }

    fun readPrefixAndDetectEncoding(uri: Uri, maxBytes: Int = 4096): AndroidContentUriAccessEvidence {
        return try {
            val bytes = contentResolver.openInputStream(uri)?.use { input ->
                input.readBytes(maxBytes.coerceIn(1, 64 * 1024))
            } ?: ByteArray(0)
            AndroidContentUriAccessEvidence(
                redactedUri = uri.toString().redactedContentUri(),
                persistableReadPermissionGranted = true,
                permissionDeniedMapped = false,
                bytePrefixLength = bytes.size,
                encoding = EncodingDetector.detect(bytes)
            )
        } catch (_: SecurityException) {
            AndroidContentUriAccessEvidence(uri.toString().redactedContentUri(), false, true, 0)
        }
    }

    private fun java.io.InputStream.readBytes(maxBytes: Int): ByteArray {
        val buffer = ByteArray(maxBytes)
        val count = read(buffer)
        return if (count <= 0) ByteArray(0) else buffer.copyOf(count)
    }
}

class AndroidCookieManagerStore(
    private val cookieManager: CookieManager = CookieManager.getInstance()
) : CookieStore {

    override suspend fun get(sourceUrl: String): CookieScope {
        val header = cookieManager.getCookie(sourceUrl).orEmpty()
        val records = header.split(";")
            .mapNotNull { raw ->
                val parts = raw.trim().split("=", limit = 2)
                if (parts.size == 2 && parts[0].isNotBlank()) {
                    CookieRecord(
                        name = parts[0],
                        value = parts[1],
                        domain = Uri.parse(sourceUrl).host.orEmpty()
                    )
                } else {
                    null
                }
            }
        return CookieScope(sourceUrl, records)
    }

    override suspend fun save(sourceUrl: String, cookies: List<CookieRecord>) {
        cookies.forEach { cookie ->
            cookieManager.setCookie(sourceUrl, cookie.toHeaderValue())
        }
        cookieManager.flush()
    }

    override suspend fun clear(sourceUrl: String) {
        get(sourceUrl).cookies.forEach { cookie ->
            cookieManager.setCookie(sourceUrl, "${cookie.name}=; Max-Age=0; Path=${cookie.path}")
        }
        cookieManager.flush()
    }

    override suspend fun clearAll() {
        cookieManager.removeAllCookies(null)
        cookieManager.flush()
    }

    fun redactedMirrorEvidence(sourceUrl: String): String {
        val cookieCount = cookieManager.getCookie(sourceUrl).orEmpty()
            .split(";")
            .count { it.contains("=") }
        return "cookie_mirror source:${sourceUrl.redactedUrl()} count:$cookieCount values:REDACTED"
    }

    private fun CookieRecord.toHeaderValue(): String = buildString {
        append(name)
        append("=")
        append(value)
        append("; Path=")
        append(path)
        if (secure) append("; Secure")
        if (httpOnly) append("; HttpOnly")
    }
}

data class AndroidEncryptedCredentialRecord(
    val identifier: String,
    val keyAlias: String,
    val cipherTextBase64: String,
    val ivBase64: String,
    val valueChecksum: String
)

class AndroidKeystoreCredentialStore(
    private val namespace: String = "reader_android_runtime"
) {
    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    fun save(identifier: String, secret: String): AndroidEncryptedCredentialRecord {
        val alias = aliasFor(identifier)
        val key = getOrCreateKey(alias)
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val cipherText = cipher.doFinal(secret.toByteArray(Charsets.UTF_8))
        return AndroidEncryptedCredentialRecord(
            identifier = identifier,
            keyAlias = alias,
            cipherTextBase64 = Base64.encodeToString(cipherText, Base64.NO_WRAP),
            ivBase64 = Base64.encodeToString(cipher.iv, Base64.NO_WRAP),
            valueChecksum = secret.hashCode().toString()
        )
    }

    fun load(record: AndroidEncryptedCredentialRecord): String {
        val key = keyStore.getKey(record.keyAlias, null) as SecretKey
        val cipher = Cipher.getInstance(AES_GCM)
        val iv = Base64.decode(record.ivBase64, Base64.NO_WRAP)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        val bytes = cipher.doFinal(Base64.decode(record.cipherTextBase64, Base64.NO_WRAP))
        return bytes.toString(Charsets.UTF_8)
    }

    fun revoke(record: AndroidEncryptedCredentialRecord): Boolean {
        if (keyStore.containsAlias(record.keyAlias)) {
            keyStore.deleteEntry(record.keyAlias)
        }
        return !keyStore.containsAlias(record.keyAlias)
    }

    private fun getOrCreateKey(alias: String): SecretKey {
        keyStore.getKey(alias, null)?.let { return it as SecretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    private fun aliasFor(identifier: String): String =
        "$namespace.${identifier.replace(Regex("[^A-Za-z0-9_.-]"), "_")}"

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val AES_GCM = "AES/GCM/NoPadding"
    }
}

class AndroidWebViewRuntimeHost(
    private val webView: WebView,
    private val cookieManager: CookieManager = CookieManager.getInstance()
) {
    fun prepareForRuntime(allowJavaScript: Boolean = true, allowDomStorage: Boolean = true) {
        webView.settings.javaScriptEnabled = allowJavaScript
        webView.settings.domStorageEnabled = allowDomStorage
        cookieManager.setAcceptCookie(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.setAcceptThirdPartyCookies(webView, true)
        }
    }

    fun loadHtmlAndEvaluate(
        request: WebRuntimeRequest,
        callback: (WebRuntimeResponse) -> Unit
    ) {
        webView.post {
            val baseUrl = request.sourceUrl.ifBlank { request.url }
            webView.loadDataWithBaseURL(baseUrl, request.html.orEmpty(), "text/html", "UTF-8", request.url)
            val js = request.jsCode
            if (js.isNullOrBlank() || Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
                callback(WebRuntimeResponse(html = request.html.orEmpty(), url = request.url, success = true))
            } else {
                webView.evaluateJavascript(js) { value ->
                    callback(WebRuntimeResponse(html = value.orEmpty(), url = request.url, success = true))
                }
            }
        }
    }

    fun redactedCookieMirrorEvidence(url: String): String {
        val count = cookieManager.getCookie(url).orEmpty()
            .split(";")
            .count { it.contains("=") }
        return "webview_cookie_mirror url:${url.redactedUrl()} count:$count values:REDACTED"
    }
}

private fun String.redactedContentUri(): String {
    val uri = runCatching { Uri.parse(this) }.getOrNull()
    if (uri?.scheme != "content") {
        return "content://provider.redacted"
    }

    val redactedPath = uri.pathSegments.joinToString(separator = "", prefix = "") { "/segment" }
    return buildString {
        append("content://provider.redacted")
        append(redactedPath)
        if (!uri.query.isNullOrBlank()) {
            append("?query=REDACTED")
        }
        if (!uri.fragment.isNullOrBlank()) {
            append("#fragment")
        }
    }
}

private fun String.redactedUrl(): String =
    replace(Regex("https?://[^\\s|]+"), "url:REDACTED")
