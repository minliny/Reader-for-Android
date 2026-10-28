package io.reader.android

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.click
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import io.reader.android.ui.ReaderRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.regex.Pattern

/**
 * Selector: io.reader.android.UiLocalReadingJourneyTest#realTxtPickerReadReturnAndRecreateRetainsPosition
 * Requires the already hash-verified original in Download/Reader; never generates
 * text, substitutes a test Uri, clears app data, or retries a business operation.
 * Activity.recreate is explicitly Activity reconstruction, not process cold start.
 */
@RunWith(AndroidJUnit4::class)
class UiLocalReadingJourneyTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test(timeout = 240_000)
    fun realTxtPickerReadReturnAndRecreateRetainsPosition() {
        val args = InstrumentationRegistry.getArguments()
        val inputName = args.getString("readerInputName")
            ?: throw AssertionError("readerInputName must name the existing real TXT in Download/Reader")
        val expectedHash = args.getString("expectedSHA")
            ?: throw AssertionError("expectedSHA must bind the original pre-transfer SHA256")
        assertTrue("A SHA256 identity is required", expectedHash.matches(Regex("[a-f0-9]{64}")))
        val model = compose.activity.readerViewModel
        compose.waitUntil(20_000) { model.state.value.ready && !model.state.value.busy }
        val originalImportRevision = model.state.value.importRevision
        compose.onNodeWithTag("reader_import").performClick()
        selectRealDocument(inputName)
        compose.waitUntil(120_000) { model.state.value.importRevision > originalImportRevision && !model.state.value.busy }
        assertEquals("Original picker import must report no failure", null, model.state.value.error)
        val book = model.state.value.books.singleOrNull { it.id == model.state.value.lastImportedBookId }
            ?: throw AssertionError("Imported original title absent; actual=${model.state.value.books.map { it.title }}")
        assertEquals("Imported Core identity must bind the exact original bytes", "local:$expectedHash", book.id)
        assertTrue("Real Core catalog must contain chapters", book.chapterCount > 1)
        compose.onNodeWithTag("reader_book_${book.id}").performClick()
        compose.waitUntil(20_000) { model.state.value.route == ReaderRoute.Reading && !model.state.value.busy }
        waitForNativeRestore()
        compose.onNodeWithTag("reader_body").performTouchInput { click(center) }
        compose.onNodeWithTag("reader_open_directory").performClick()
        compose.waitUntil(20_000) { model.state.value.route == ReaderRoute.Directory && !model.state.value.busy }
        val targetChapter = model.state.value.chapters.getOrNull(1)
            ?: throw AssertionError("Real original's second catalog entry is absent")
        compose.onNodeWithTag("reader_chapter_${targetChapter.index}").performClick()
        compose.waitUntil(20_000) {
            model.state.value.route == ReaderRoute.Reading && model.state.value.reading?.chapter?.index == targetChapter.index && !model.state.value.busy
        }
        waitForNativeRestore()
        compose.onNodeWithTag("reader_body").performTouchInput { swipeUp(durationMillis = 550) }
        compose.waitUntil(10_000) { (model.state.value.reading?.visibleScalarOffset ?: 0) > 0 }
        val beforeExit = model.state.value.reading ?: throw AssertionError("Missing actual reading session")
        compose.onNodeWithTag("reader_body").performTouchInput { click(center) }
        compose.onNodeWithTag("reader_controls").assertIsDisplayed()
        compose.onNodeWithTag("reader_font_larger").performClick()
        waitForNativeRestore()
        compose.onNodeWithTag("reader_exit").performClick()
        compose.waitUntil(20_000) { model.state.value.route == ReaderRoute.Bookshelf && !model.state.value.busy }
        val persisted = model.state.value.books.single { it.id == book.id }
        assertEquals(beforeExit.chapter.index, persisted.progressChapter)
        assertTrue("Actual visible text anchor must persist", persisted.progressOffset > 0)
        assertEquals("Reflow must preserve the original scalar anchor", beforeExit.visibleScalarOffset, persisted.progressOffset)

        compose.activityRule.scenario.recreate()
        compose.waitUntil(20_000) { compose.activity.readerViewModel.state.value.ready }
        compose.onNodeWithTag("reader_book_${book.id}").performClick()
        compose.waitUntil(20_000) {
            val state = compose.activity.readerViewModel.state.value
            state.route == ReaderRoute.Reading && !state.busy
        }
        waitForNativeRestore()
        val restored = compose.activity.readerViewModel.state.value.reading ?: throw AssertionError("Missing reopened reader")
        assertEquals(persisted.progressChapter, restored.chapter.index)
        assertEquals(persisted.progressOffset, restored.restoreScalarOffset)
        assertNotEquals("Activity reconstruction must create a fresh reader owner", beforeExit.identity, restored.identity)
        val currentOffset = restored.visibleScalarOffset
        compose.runOnUiThread {
            compose.activity.readerViewModel.visibleAnchor(beforeExit.identity, 0)
        }
        assertEquals("Retired reader callback cannot overwrite current position", currentOffset,
            compose.activity.readerViewModel.state.value.reading?.visibleScalarOffset)
        compose.onNodeWithTag("reader_body").assertExists()
    }

    private fun waitForNativeRestore() {
        compose.waitUntil(8_000) {
            compose.onAllNodes(androidx.compose.ui.test.hasTestTag("reader_restore_loading"))
                .fetchSemanticsNodes().isEmpty()
        }
    }

    private fun selectRealDocument(name: String) {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val provider = Pattern.compile("com\\.(google\\.)?android\\.documentsui")
        assertTrue("System document picker must actually open",
            device.wait(Until.hasObject(By.pkg(provider)), 10_000))
        var file = device.findObject(By.text(name))
        if (file == null) {
            val roots = device.findObject(By.desc(Pattern.compile("Show roots|显示根目录|显示位置|导航抽屉")))
                ?: throw AssertionError("Document picker root selector is unavailable; no coordinate fallback")
            roots.click()
            val downloads = device.wait(Until.findObject(By.text(Pattern.compile("Downloads|下载|下载内容"))), 5_000)
                ?: throw AssertionError("Downloads provider absent")
            downloads.click()
            val folder = device.wait(Until.findObject(By.text("Reader")), 5_000)
                ?: throw AssertionError("Existing Download/Reader folder absent")
            folder.click()
            file = device.wait(Until.findObject(By.text(name)), 5_000)
        }
        (file ?: throw AssertionError("Original input missing: $name")).click()
    }
}
