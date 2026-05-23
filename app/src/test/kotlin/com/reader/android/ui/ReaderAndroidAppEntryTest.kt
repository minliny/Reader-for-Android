package com.reader.android.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class ReaderAndroidAppEntryTest {

    private val appEntrySource: String by lazy {
        String(Files.readAllBytes(Paths.get("src/main/kotlin/com/reader/android/ui/ReaderAndroidApp.kt")))
    }

    private val mainActivitySource: String by lazy {
        String(Files.readAllBytes(Paths.get("src/main/kotlin/com/reader/android/MainActivity.kt")))
    }

    @Test
    fun `main activity enters reader android app`() {
        assertTrue("MainActivity must call ReaderAndroidApp", "ReaderAndroidApp()" in mainActivitySource)
    }

    @Test
    fun `app entry wraps runtime in reader theme`() {
        assertTrue("ReaderAndroidApp must import ReaderTheme", "import com.reader.android.ui.theme.ReaderTheme" in appEntrySource)
        assertTrue("ReaderAndroidApp must call ReaderTheme", "ReaderTheme {" in appEntrySource)
    }

    @Test
    fun `app entry reaches route host through app navigation`() {
        assertTrue("ReaderAndroidApp must call AppNavigation", "AppNavigation()" in appEntrySource)
    }
}
