package io.reader.android.ui.ebookdraft

import android.content.Context
import android.net.Uri
import android.util.JsonWriter
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.webkit.WebViewAssetLoader
import io.reader.android.runtime.ReaderEbookFontFallback
import io.reader.android.runtime.ebookdraft.ReaderEbookAssetProvider
import io.reader.android.runtime.ebookdraft.ReaderEbookWebViewBoundary
import io.reader.android.ui.background
import io.reader.android.ui.ink
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets.UTF_8
import java.util.Locale
import kotlin.math.ceil

internal data class ReaderEbookLayoutSnapshot(val epoch: Long, val token: String, val input: ReaderEbookRenderInput,
    val anchor: Int, val widthPx: Int, val heightPx: Int, val json: ByteArray)

/** Exact private capability only. Must use the provider-owner patch; this file has no Native bridge. */
internal class ReaderEbookLocalResources(context: Context, private val original: ReaderEbookRenderInput,
    private val current: () -> ReaderEbookLayoutSnapshot?, private val requireCurrent: () -> Unit,
    private val failed: (Exception) -> Unit) {
    private val assets = context.assets
    private val session = original.prepared.lease.renderId.toString()
    private val missingFonts = ReaderEbookFontFallback()
    val entryUrl: String = Uri.parse(ReaderEbookAssetProvider.ORIGIN).buildUpon().appendPath("publication").appendPath(session)
        .apply { original.prepared.presentation.entryPath.split('/').forEach { appendPath(it) } }.build().toString()
    private val bootstrap by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { bootstrap() } // Encoded only on interception IO, not UI.
    private val loader = WebViewAssetLoader.Builder().setDomain(ReaderEbookAssetProvider.ORIGIN_HOST).setHttpAllowed(false)
        .addPathHandler("/__reader/", WebViewAssetLoader.PathHandler { suffix -> trusted(suffix) }).build()

    fun intercept(request: WebResourceRequest): WebResourceResponse {
        var canonicalEntry: String? = null
        return try {
            requireCurrent()
            val uri = request.url
            require(request.method == "GET" && uri.scheme == "https" && uri.encodedAuthority == ReaderEbookAssetProvider.ORIGIN_HOST
                && uri.encodedQuery == null && uri.encodedFragment == null) { "NON_LOCAL_RESOURCE_DENIED" }
            val path = uri.encodedPath ?: error("RESOURCE_PATH_REQUIRED")
            if (request.isForMainFrame) require(path == "/__reader/shell.html") { "MAIN_DOCUMENT_DENIED" }
            when {
                path.startsWith("/__reader/") -> {
                    require('%' !in path) { "ENCODED_CONTROL_PATH_DENIED" }
                    loader.shouldInterceptRequest(uri) ?: ReaderEbookWebViewBoundary.denied()
                }
                path.startsWith("/publication/$session/") && !request.isForMainFrame -> {
                    val provider = original.prepared.provider
                    val entry = provider.canonicalEntry(uri, request.method)
                    canonicalEntry = entry
                    val resource = provider.readCanonicalEntry(entry)
                    requireCurrent()
                    // Archive bytes retain their original encoding. Chromium handles BOM/XML/charset detection.
                    response(resource.mime, null, resource.bytes, ReaderEbookWebViewBoundary.PUBLICATION_CSP)
                }
                else -> ReaderEbookWebViewBoundary.denied()
            }
        } catch (error: Exception) {
            val entry = canonicalEntry
            if (entry != null) {
                try {
                    requireCurrent()
                    if (missingFonts.recordMissingFont(request.url.toString(), entry, error))
                        return ReaderEbookWebViewBoundary.denied(404)
                } catch (retired: Exception) {
                    failed(retired)
                    return ReaderEbookWebViewBoundary.denied(404)
                }
            }
            failed(error)
            ReaderEbookWebViewBoundary.denied(404)
        }
    }

    fun missingEmbeddedFontResponse(uri: Uri, status: Int): Boolean = missingFonts.acceptsHttpFailure(uri.toString(), status)

    fun activeResource(uri: Uri): Boolean {
        if (uri.scheme != "https" || uri.encodedAuthority != ReaderEbookAssetProvider.ORIGIN_HOST) return false
        val path = uri.encodedPath ?: return false
        if (path.startsWith("/publication/$session/")) return true
        if (path in setOf("/__reader/shell.html", "/__reader/runtime.mjs", "/__reader/reader-ebook-dom.mjs", "/__reader/config/$session.json",
                "/__reader/layout-current/$session.json")) return true
        return current()?.let { path == "/__reader/layout/$session/${it.epoch}/${it.token}.json" } == true
    }

    private fun trusted(suffix: String): WebResourceResponse? {
        requireCurrent()
        val snapshot = current() ?: return null
        val bytes: ByteArray
        val mime: String
        when (suffix) {
            "shell.html" -> {
                mime = "text/html"
                bytes = assets.open("reader_ebook_runtime/shell.html").use { it.readBytes() }.toString(UTF_8)
                    .replace("__RENDER_UUID__", session).replace("__LAYOUT_EPOCH__", snapshot.epoch.toString())
                    .replace("__LAYOUT_TOKEN__", snapshot.token).toByteArray(UTF_8)
            }
            "runtime.mjs", "reader-ebook-dom.mjs" -> {
                mime = "text/javascript"; bytes = assets.open("reader_ebook_runtime/$suffix").use { it.readBytes() }
            }
            "config/$session.json" -> { mime = "application/json"; bytes = bootstrap }
            "layout-current/$session.json" -> { mime = "application/json"; bytes = snapshot.json }
            "layout/$session/${snapshot.epoch}/${snapshot.token}.json" -> { mime = "application/json"; bytes = snapshot.json }
            else -> return null
        }
        requireCurrent()
        return response(mime, "UTF-8", bytes, SHELL_CSP)
    }

    private fun bootstrap(): ByteArray {
        val p = original.prepared.presentation
        val dom = p.domConfiguration(original.chapter, original.currentAnchor, 0) // Existing authoritative admission.
        val out = object : ByteArrayOutputStream() {
            private val maximum = 40 * 1024 * 1024
            override fun write(value: Int) { requireCurrent(); check(count < maximum) { "BOOTSTRAP_BYTE_LIMIT" }; super.write(value) }
            override fun write(bytes: ByteArray, offset: Int, length: Int) {
                requireCurrent()
                check(length <= maximum - count) { "BOOTSTRAP_BYTE_LIMIT" }; super.write(bytes, offset, length)
            }
        }
        JsonWriter(OutputStreamWriter(out, UTF_8)).use { writer ->
            writer.beginObject().name("protocolVersion").value(1).name("session").value(session).name("entryUrl").value(entryUrl)
            writer.name("dom").beginObject().name("canonical").value(dom.getString("canonical"))
                .name("chapterText").value(dom.getString("content")).name("start").value(dom.getInt("start"))
                .name("end").value(dom.getInt("end")).name("scalarCount").value(dom.getInt("scalarCount"))
                .name("offset").value(original.currentAnchor).name("topInset").value(0).endObject().endObject()
        }
        return out.toByteArray()
    }

    companion object {
        const val SHELL_URL = ReaderEbookAssetProvider.ORIGIN + "/__reader/shell.html"
        private const val SHELL_CSP = "default-src 'none'; script-src 'self'; style-src 'self' 'unsafe-inline'; " +
            "connect-src 'self'; frame-src 'self'; img-src 'none'; font-src 'none'; object-src 'none'; " +
            "base-uri 'none'; form-action 'none'; worker-src 'none'"
        private fun response(mime: String, encoding: String?, bytes: ByteArray, csp: String) = WebResourceResponse(
            mime, encoding, 200, "OK", mapOf("Content-Security-Policy" to csp, "Cache-Control" to "no-store",
                "X-Content-Type-Options" to "nosniff", "Referrer-Policy" to "no-referrer"), ByteArrayInputStream(bytes))

        fun snapshot(input: ReaderEbookRenderInput, epoch: Long, token: String, anchor: Int, width: Int, height: Int): ReaderEbookLayoutSnapshot {
            val g = input.geometry
            require(width > g.leftPx + g.rightPx && height > g.topPx + g.bottomPx)
            fun css(px: Int) = ceil(px / g.density.toDouble()).toInt()
            fun color(argb: Int): String { require((argb ushr 24) == 255); return String.format(Locale.ROOT, "#%06x", argb and 0xffffff) }
            val font = input.fontSizeSp * g.fontScale
            require(font.isFinite() && font in 1f..512f)
            val options = JSONObject().put("layoutToken", token).put("fontSize", font.toDouble()).put("lineHeight", 1.8)
                .put("paper", color(input.paper.background().toArgb())).put("ink", color(input.paper.ink().toArgb()))
                .put("meta", color(input.paper.ink().toArgb())).put("selection", "rgba(150,180,170,0.35)")
                .put("dark", input.paper.background().luminance() <= 0.5f).put("topInset", css(g.topPx)).put("bottomInset", css(g.bottomPx))
                .put("leftInset", css(g.leftPx)).put("rightInset", css(g.rightPx)).put("horizontalInset", css(g.leftPx))
                .put("fontURL", JSONObject.NULL).put("systemFont", false).put("fontVariations", JSONObject())
                .put("longPressSelectText", true).put("letterSpacing", 0).put("alignment", "start")
                .put("paragraphSpacing", 0).put("firstLineIndentEm", 0).put("reduceMotion", true)
            val json = JSONObject().put("session", input.prepared.lease.renderId.toString()).put("layoutEpoch", epoch).put("layoutToken", token)
                .put("anchor", anchor).put("userScrollEnabled", input.userScrollEnabled).put("options", options).toString().toByteArray(UTF_8)
            return ReaderEbookLayoutSnapshot(epoch, token, input, anchor, width, height, json)
        }
    }
}
