package io.reader.android

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.reader.android.ui.ReaderRoute
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

/** Current-device recovery boundary: no receipt means the original stays pending. */
@RunWith(AndroidJUnit4::class)
class UiPendingPositionNoCandidateTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun noCandidateRecoveryKeepsBothPendingJournalsAndDoesNotComplete() {
        val model = compose.activity.readerViewModel
        compose.waitUntil(20_000) { !model.state.value.busy && model.state.value.ready }
        val initial = model.state.value
        assertEquals(ReaderRoute.Bookshelf, initial.route)
        assertTrue(initial.pendingReady && initial.pendingImports.isEmpty())
        assertEquals(setOf(MOBI_PENDING_ID, TXT_PENDING_ID), initial.pendingPositions.map { it.id }.toSet())
        assertEquals(0L, initial.importRevision)

        val operations = File(compose.activity.filesDir, "Reader/Operations")
        val mobi = File(operations, "$MOBI_PENDING_ID.json")
        val txt = File(operations, "$TXT_PENDING_ID.json")
        val mobiRaw = mobi.readBytes(); val txtRaw = txt.readBytes()
        assertEquals(MOBI_ACTIVE_SHA, sha(mobiRaw)); assertEquals(TXT_ACTIVE_SHA, sha(txtRaw))
        assertFalse(JSONObject(mobiRaw.toString(Charsets.UTF_8)).has("wireTerminal"))
        assertFalse(JSONObject(txtRaw.toString(Charsets.UTF_8)).has("wireTerminal"))
        val completedBefore = completedNames(operations)

        compose.onNodeWithTag("reader_reconcile_position").assertIsDisplayed().assertIsEnabled().performClick()
        compose.waitUntil(15_000) { !model.state.value.busy }

        val after = model.state.value
        // The owner may surface this as a transient message or a retained
        // protocol reference; the durable contract is that no receipt means
        // the original remains pending and no completion is fabricated.
        assertEquals(setOf(MOBI_PENDING_ID, TXT_PENDING_ID), after.pendingPositions.map { it.id }.toSet())
        assertEquals(0L, after.importRevision)
        assertEquals(MOBI_ACTIVE_SHA, sha(mobi.readBytes())); assertEquals(TXT_ACTIVE_SHA, sha(txt.readBytes()))
        assertEquals(completedBefore, completedNames(operations))
        assertFalse(File(operations, "$MOBI_PENDING_ID.json.new").exists())
        assertFalse(File(operations, "$TXT_PENDING_ID.json.new").exists())
    }

    private fun completedNames(operations: File): Set<String> =
        File(operations, "Completed").listFiles().orEmpty().filter { it.name.endsWith(".json") }.map { it.name }.toSet()

    private fun sha(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }

    companion object {
        private const val MOBI_PENDING_ID = "android-progress-34f21d9a-6dc6-478e-a551-f85219609563"
        private const val MOBI_ACTIVE_SHA = "c4a496c111870155bd93435b76aae2dd70402fbd65e834bf4ebb91297df1ec82"
        private const val TXT_PENDING_ID = "android-progress-20565269-a395-4ac4-be55-bdc9157d994e"
        private const val TXT_ACTIVE_SHA = "a26c7e82d3cec2ae66a7452df0247ca8589469eef9a4a936cfc505f48ebee063"
    }
}
