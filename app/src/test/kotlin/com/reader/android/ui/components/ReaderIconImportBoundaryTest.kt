package com.reader.android.ui.components

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.streams.asSequence

class ReaderIconImportBoundaryTest {

    private val uiSourceRoot: Path = Paths.get("src/main/kotlin/com/reader/android/ui")

    private data class SourceFile(
        val relativePath: String,
        val source: String
    )

    private fun uiSourceFiles(): List<SourceFile> =
        Files.walk(uiSourceRoot).asSequence()
            .filter { Files.isRegularFile(it) }
            .filter { it.toString().endsWith(".kt") }
            .map { path ->
                SourceFile(
                    relativePath = uiSourceRoot.relativize(path).toString(),
                    source = String(Files.readAllBytes(path))
                )
            }
            .toList()

    private fun isAllowedMaterialIconFile(relativePath: String): Boolean =
        relativePath == "components/ReaderIcons.kt" || relativePath.startsWith("stitch/")

    @Test
    fun `material icon imports stay behind semantic reader icon boundary`() {
        val offenders = uiSourceFiles()
            .filterNot { isAllowedMaterialIconFile(it.relativePath) }
            .filter { file ->
                "import androidx.compose.material.icons" in file.source ||
                    "Icons.Filled." in file.source ||
                    "Icons.AutoMirrored.Filled." in file.source
            }
            .map { it.relativePath }

        assertTrue(
            "Only ReaderIcons.kt may map Material Icons for production UI; offenders: $offenders",
            offenders.isEmpty()
        )
    }
}
