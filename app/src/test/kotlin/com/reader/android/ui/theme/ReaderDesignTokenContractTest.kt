package com.reader.android.ui.theme

import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class ReaderDesignTokenContractTest {

    private val workspaceRoot: Path = Paths.get("..").toAbsolutePath().normalize()
    private val contract: JSONObject by lazy {
        JSONObject(readWorkspace("docs/ui-design/frontend-input/design-tokens.json"))
    }
    private val cssSource: String by lazy {
        readWorkspace(contract.getString("cssSource"))
    }

    @Test
    fun `design token contract is the shared source for frontend css`() {
        val entries = contractTokenEntries()
        assertTrue("design token contract must define enough landed tokens", entries.size >= 65)
        listOf("colors", "spacing", "frame", "zIndex", "textLimits").forEach { group ->
            assertTrue("design token contract must include $group tokens", contract.getJSONObject("tokens").has(group))
        }

        entries.forEach { entry ->
            assertTrue(
                "tokens.css must declare ${entry.css}",
                Regex("""${Regex.escape(entry.css)}\s*:\s*${Regex.escape(entry.cssValue)}\s*;""")
                    .containsMatchIn(cssSource)
            )
        }
    }

    @Test
    fun `design token contract traces compose theme token values`() {
        val composeSources = contract.getJSONObject("composeSources")
        val sourceByGroup = mapOf(
            "colors" to readWorkspace(composeSources.getString("colors")),
            "spacing" to readWorkspace(composeSources.getString("spacing")),
            "typography" to readWorkspace(composeSources.getString("typography")),
            "radii" to readWorkspace(composeSources.getString("shapes")),
            "elevation" to readWorkspace(composeSources.getString("elevation"))
        )

        contractTokenEntries()
            .filter { it.composeField != null && it.composeValue != null }
            .forEach { entry ->
                val source = sourceByGroup.getValue(entry.group)
                val field = Regex.escape(entry.composeField!!)
                val value = Regex.escape(entry.composeValue!!)
                val fieldValuePattern = Regex("""$field\s*=\s*(?:Color\(|RoundedCornerShape\(|TextStyle\(fontSize\s*=\s*)?$value""")
                assertTrue(
                    "${entry.group}.${entry.name} must trace ${entry.composeField}=${entry.composeValue}",
                    fieldValuePattern.containsMatchIn(source)
                )
            }
    }

    private fun contractTokenEntries(): List<TokenEntry> {
        val tokens = contract.getJSONObject("tokens")
        val entries = mutableListOf<TokenEntry>()
        tokens.keys().forEachRemaining { group ->
            collectEntries(group, tokens.getJSONObject(group), entries)
        }
        return entries
    }

    private fun collectEntries(group: String, node: JSONObject, entries: MutableList<TokenEntry>) {
        node.keys().forEachRemaining { name ->
            val child = node.getJSONObject(name)
            if (child.has("css") && child.has("cssValue")) {
                entries += TokenEntry(
                    group = group,
                    name = name,
                    css = child.getString("css"),
                    cssValue = child.getString("cssValue"),
                    composeField = child.optString("composeField").ifBlank { null },
                    composeValue = child.optString("composeValue").ifBlank { null }
                )
            } else {
                collectEntries(group, child, entries)
            }
        }
    }

    private fun readWorkspace(path: String): String =
        String(Files.readAllBytes(workspaceRoot.resolve(path)))

    private data class TokenEntry(
        val group: String,
        val name: String,
        val css: String,
        val cssValue: String,
        val composeField: String?,
        val composeValue: String?
    )
}
