package com.reader.android.data.adapter

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.parser.Parser

data class MarkupExtractionResult(
    val values: List<String>,
    val selector: String,
    val mode: String,
    val redactedPreview: String
) {
    val passed: Boolean get() = values.isNotEmpty()
}

class JsoupMarkupParserAdapter {

    fun selectHtmlText(html: String, cssSelector: String): MarkupExtractionResult {
        val document = Jsoup.parse(html)
        val values = document.select(cssSelector)
            .map { it.text().trim() }
            .filter { it.isNotBlank() }
        return result(values, cssSelector, "html.css")
    }

    fun selectHtmlAttribute(html: String, cssSelector: String, attribute: String): MarkupExtractionResult {
        val document = Jsoup.parse(html)
        val values = document.select(cssSelector)
            .map { it.attr(attribute).trim() }
            .filter { it.isNotBlank() }
        return result(values, "$cssSelector@$attribute", "attribute-extraction")
    }

    fun selectHtmlXPathText(html: String, boundedXPath: String): MarkupExtractionResult {
        val selector = boundedXPath.toBoundedCssSelector()
        val values = selector?.let { Jsoup.parse(html).select(it).map { node -> node.text().trim() } }
            ?: emptyList()
        return result(values.filter { it.isNotBlank() }, boundedXPath, "html.xpath")
    }

    fun selectXmlXPathText(xml: String, boundedXPath: String): MarkupExtractionResult {
        val selector = boundedXPath.toBoundedCssSelector()
        val document: Document = Jsoup.parse(xml, "", Parser.xmlParser())
        val values = selector?.let { document.select(it).map { node -> node.text().trim() } }
            ?: emptyList()
        return result(values.filter { it.isNotBlank() }, boundedXPath, "xml.xpath")
    }

    fun textWithoutScriptOrStyle(html: String): String {
        val document = Jsoup.parse(html)
        document.select("script,style").remove()
        return document.text().trim()
    }

    private fun result(values: List<String>, selector: String, mode: String): MarkupExtractionResult =
        MarkupExtractionResult(
            values = values,
            selector = selector,
            mode = mode,
            redactedPreview = values.joinToString("|").boundedPreview()
        )

    private fun String.toBoundedCssSelector(): String? {
        val trimmed = trim()
            .removeSuffix("/text()")
            .removeSuffix("/@href")
            .removeSuffix("/@src")
        if (!trimmed.startsWith("//")) return null
        if (trimmed.contains("[") || trimmed.contains("..") || trimmed.contains("*")) return null
        val parts = trimmed.removePrefix("//")
            .split("/")
            .map { it.trim() }
            .filter { it.isNotBlank() }
        if (parts.isEmpty()) return null
        if (parts.any { !it.matches(Regex("[A-Za-z][A-Za-z0-9_-]*")) }) return null
        return parts.joinToString(" ")
    }

    private fun String.boundedPreview(): String =
        replace(Regex("https?://[^\\s|]+"), "url:REDACTED")
            .replace(Regex("(?i)(cookie|token|password|authorization)=[^\\s|]+"), "$1=REDACTED")
            .take(160)
}
