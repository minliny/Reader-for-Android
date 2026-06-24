package com.reader.android.data.adapter

import com.reader.android.data.model.BookSourceRule
import com.reader.android.data.model.RuleExtraction
import com.reader.android.data.model.RuleField
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
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

    /**
     * Select repeated row elements by [listSelector] and return their text.
     * Used by list stages (search results, TOC chapters) when a [BookSourceRule]
     * carries a `listSelector`.
     */
    fun selectHtmlTextList(html: String, listSelector: String): List<Element> {
        return Jsoup.parse(html).select(listSelector).toList()
    }

    /**
     * Evaluate a [BookSourceRule] against [html]. Returns one field map per row
     * when the rule has a [BookSourceRule.listSelector] (search/TOC), or a single
     * field map evaluated against the whole document otherwise (bookInfo/content).
     * Empty results are returned for selectors that fail to parse — the caller
     * (parser) falls back to the regex path.
     */
    fun evaluateRule(html: String, rule: BookSourceRule): List<Map<String, String>> {
        val document = Jsoup.parse(html)
        val rows: List<Element> = rule.listSelector
            ?.let { document.select(it).toList() }
            ?: listOf(document)
        return rows.map { row -> rule.fields.associate { field -> field.name to extractField(row, field) } }
    }

    private fun extractField(scope: Element, field: RuleField): String {
        val elements = scope.select(field.selector)
        return when (field.extraction) {
            RuleExtraction.ATTRIBUTE -> elements.firstOrNull()?.attr(field.attribute ?: "href")?.trim().orEmpty()
            RuleExtraction.HTML -> elements.firstOrNull()?.html()?.trim().orEmpty()
            RuleExtraction.TEXT -> elements.joinToString("\n") { it.text().trim() }.trim()
        }
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
