package com.reader.android.data.adapter

import com.reader.android.data.model.BookSourceRule
import com.reader.android.data.model.RuleExtraction
import com.reader.android.data.model.RuleField

/**
 * Parses a clean-room Legado-style rule grammar into a [BookSourceRule].
 *
 * Accepted shapes (one field per line, `name<sep>selector`):
 * - `name@css`            → text extraction via CSS selector
 * - `name@css||attr`      → attribute extraction via CSS selector
 * - `name@css||html`      → raw HTML extraction via CSS selector
 * - `name@xpath`          → bounded XPath mapped to a CSS selector by the adapter
 *
 * The first field line may start with `list@` to declare the row selector for
 * list stages (search results / TOC chapters), e.g. `list@div.book-item`.
 *
 * This grammar is a clean-room subset. No Legado source is referenced.
 */
object RuleExpression {

    private const val LIST_KEY = "list"

    fun parse(ruleText: String): BookSourceRule {
        val fields = mutableListOf<RuleField>()
        var listSelector: String? = null
        ruleText.lines().map { it.trim() }.filter { it.isNotBlank() && !it.startsWith("#") }.forEach { line ->
            val sepIndex = line.indexOf('@')
            if (sepIndex <= 0) return@forEach
            val name = line.substring(0, sepIndex).trim()
            val rest = line.substring(sepIndex + 1).trim()
            if (name == LIST_KEY) {
                listSelector = rest
                return@forEach
            }
            val (selector, extraction, attribute) = parseSelector(rest)
            fields.add(RuleField(name = name, selector = selector, extraction = extraction, attribute = attribute))
        }
        return BookSourceRule(listSelector = listSelector, fields = fields.toList())
    }

    private fun parseSelector(rest: String): Triple<String, RuleExtraction, String?> {
        // css||attr  → attribute extraction
        // css||html  → html extraction
        // css        → text extraction
        val parts = rest.split("||", limit = 2).map { it.trim() }
        val selector = parts[0]
        return when {
            parts.size < 2 -> Triple(selector, RuleExtraction.TEXT, null)
            parts[1].equals("html", ignoreCase = true) -> Triple(selector, RuleExtraction.HTML, null)
            parts[1].equals("text", ignoreCase = true) -> Triple(selector, RuleExtraction.TEXT, null)
            else -> Triple(selector, RuleExtraction.ATTRIBUTE, parts[1])
        }
    }
}
