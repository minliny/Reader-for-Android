package com.reader.android.data.adapter

import org.jsoup.Jsoup
import org.jsoup.safety.Safelist

/**
 * Clean-room reader-shell rendering host. This is the Android host adapter
 * that turns a fetched [ContentPage]-shaped payload into a renderable result —
 * WITHOUT Readium. TXT/online cleaned content is paginated as plain text;
 * rich HTML is sanitized through a strict jsoup [Safelist] with a
 * Content-Security-Policy that disables scripts. EPUB-CFI navigation is
 * represented as an opaque future slot ([RenderResult.cfi]) left null by this
 * minimal implementation; a Readium navigator can later implement the same
 * contract and populate CFI without changing call sites.
 *
 * Boundary: this host owns text pagination + HTML sanitization + CSP. The
 * actual Compose/WebView surface that displays [RenderResult] lives in the UI
 * layer; the host never touches a View.
 */
interface ReaderShellHost {
    suspend fun render(request: RenderRequest): RenderResult
}

/** Content kind the host knows how to render. */
enum class ReaderContentType { TXT, HTML }

/** Render settings (font size drives page capacity; future slots extensible). */
data class ReaderShellSettings(
    val pageCharBudget: Int = 900,
    val jsEnabled: Boolean = false
)

/** Input to [ReaderShellHost.render]. */
data class RenderRequest(
    val content: String,
    val contentType: ReaderContentType,
    val title: String? = null,
    val settings: ReaderShellSettings = ReaderShellSettings()
)

/**
 * Render output.
 * @param paginatedText TXT content split into display pages (empty for HTML-only).
 * @param sanitizedHtml sanitized, CSP-locked HTML (null for TXT).
 * @param cfi opaque EPUB-CFI slot for a future Readium navigator; null here.
 * @param jsEnabled whether JS is permitted (always false for the clean-room host
 *   unless the caller explicitly opts in via [ReaderShellSettings.jsEnabled] —
 *   and even then the host injects a CSP that blocks remote script execution).
 */
data class RenderResult(
    val paginatedText: List<String>,
    val sanitizedHtml: String?,
    val cfi: String? = null,
    val jsEnabled: Boolean,
    val csp: String
)

/**
 * Minimal clean-room implementation: paginates TXT and sanitizes HTML with a
 * strict allowlist + script-blocking CSP. No Readium, no remote resources.
 */
class ComposeReaderShellHost : ReaderShellHost {

    override suspend fun render(request: RenderRequest): RenderResult {
        val csp = buildCsp(request.settings.jsEnabled)
        return when (request.contentType) {
            ReaderContentType.TXT -> RenderResult(
                paginatedText = paginate(request.content, request.settings.pageCharBudget),
                sanitizedHtml = null,
                jsEnabled = request.settings.jsEnabled,
                csp = csp
            )
            ReaderContentType.HTML -> {
                val clean = sanitize(request.content)
                RenderResult(
                    paginatedText = paginate(Jsoup.parse(clean).text(), request.settings.pageCharBudget),
                    sanitizedHtml = clean,
                    jsEnabled = request.settings.jsEnabled,
                    csp = csp
                )
            }
        }
    }

    /** Split text into pages of roughly [budget] chars, breaking on paragraph
     *  boundaries so pages don't split mid-sentence. */
    private fun paginate(text: String, budget: Int): List<String> {
        if (text.isBlank()) return emptyList()
        val paragraphs = text.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        val pages = mutableListOf<String>()
        val current = StringBuilder()
        for (para in paragraphs) {
            if (current.isNotEmpty() && current.length + para.length + 1 > budget) {
                pages.add(current.toString())
                current.setLength(0)
            }
            if (current.isNotEmpty()) current.append("\n")
            current.append(para)
        }
        if (current.isNotEmpty()) pages.add(current.toString())
        return pages.ifEmpty { listOf(text) }
    }

    /** Sanitize HTML: allow basic formatting/tags, strip scripts/styles/iframe, drop all attributes except href/src. */
    private fun sanitize(html: String): String {
        val safelist = Safelist.relaxed()
            // Remove inline event handlers / style attributes by preserving only relaxed defaults
            .preserveRelativeLinks(false)
        val doc = Jsoup.parse(html)
        doc.select("script,style,iframe,object,embed,link,meta[http-equiv]").remove()
        return Jsoup.clean(doc.body().html(), safelist)
    }

    private fun buildCsp(jsEnabled: Boolean): String {
        // default-src 'none' : no remote anything by default
        val script = if (jsEnabled) "'unsafe-inline'" else "'none'"
        return "default-src 'none'; img-src 'self' data:; style-src 'unsafe-inline'; script-src $script"
    }
}
