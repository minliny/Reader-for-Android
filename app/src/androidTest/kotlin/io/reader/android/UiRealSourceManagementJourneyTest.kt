package io.reader.android

import android.app.KeyguardManager
import android.os.Bundle
import android.os.Process as AndroidProcess
import android.os.SystemClock
import android.webkit.MimeTypeMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import io.reader.android.runtime.*
import io.reader.android.ui.ReaderRoute
import io.reader.android.ui.ReaderViewModel
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** One intact existing SAF document; only a new source import/toggle/delete, never a search or Add. */
@RunWith(AndroidJUnit4::class)
class UiRealSourceManagementJourneyTest {
    private val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private var scenario: ActivityScenario<MainActivity>? = null
    private lateinit var current: MainActivity
    private var failureDiagnosticCaptured = false
    @get:Rule val rules: TestRule = RuleChain.outerRule(
        Timeout.builder().withTimeout(180, TimeUnit.SECONDS).withLookingForStuckThread(true).build()).around(compose)

    @Test fun intactRealSourceDocumentImportsIdejianTogglesCancelsThenDeletesWithOriginalReceipts() {
        marker("method-enter")
        var primary: Throwable? = null
        var phase = "precondition"
        try {
            val args = InstrumentationRegistry.getArguments()
            val baseline = checkNotNull(args.getString("baselineProofSha256"))
            assertTrue(baseline.matches(Regex("[a-f0-9]{64}")))
            assertEquals(FILE_NAME, args.getString("displayName")); assertEquals(FILE_SHA, args.getString("expectedSHA"))
            assertEquals(FILE_BYTES, checkNotNull(args.getString("expectedBytes")?.toIntOrNull()))
            assertEquals(SOURCE_ID, args.getString("sourceId")); assertEquals(0, checkNotNull(args.getString("sourceIndex")?.toIntOrNull()))
            scenario = ActivityScenario.launch(MainActivity::class.java); scenario!!.onActivity { current = it }
            val model = current.readerViewModel
            compose.waitUntil(20_000) { !model.state.value.busy && (model.state.value.ready || model.state.value.error != null) }
            healthy(model); assertEquals(ReaderRoute.Bookshelf, model.state.value.route)
            val localBefore = model.state.value.books.toList(); val remoteBefore = model.state.value.remoteBooks.toList()
            val completedBefore = completedNames(); val unknownBefore = unknown()
            val app = current.application as ReaderApplication
            val appDelegate = field(app, "coreOwner\$delegate") as Lazy<*>
            assertTrue(appDelegate.isInitialized()); val appOwner = app.coreOwner
            val runtime = initializedActor(appOwner).runtimeSession
            compose.waitUntil(5_000) { compose.runOnUiThread { current.window.decorView.hasWindowFocus() } }
            assertTrue(UiDevice.getInstance(instrumentation).isScreenOn)
            assertFalse(checkNotNull(current.getSystemService(KeyguardManager::class.java)).isKeyguardLocked)
            marker("source-management-input-bound", Bundle().apply {
                putString("baselineProofSha256", baseline); putString("displayName", FILE_NAME)
                putString("sourceDocumentSha256", FILE_SHA); putInt("sourceDocumentBytes", FILE_BYTES)
                putString("sourceId", SOURCE_ID); putInt("selectedIndex", 0); putString("runtimeSession", runtime)
                putBoolean("screenOn", true); putBoolean("keyguardLocked", false)
            })
            compose.onNodeWithTag("reader_online_sources").assertIsEnabled().performClick()
            idle(model, 20_000); val workspace = model.onlineSources
            assertEquals(ReaderRoute.Online, model.state.value.route); assertTrue(workspace.state.value.loaded)
            val sourcesBefore = workspace.state.value.sources.toList()
            assertTrue("Target source must be absent; do not replace or recover another intent", sourcesBefore.none { it.sourceId == SOURCE_ID })
            assertTrue("Protected Baidu source must already exist", sourcesBefore.any { it.sourceId == PROTECTED_SOURCE_ID })
            phase = "one-standard-saf-document"
            clickWorkspace("reader_source_import_file")
            selectRealDocument(FILE_NAME)
            compose.waitUntil(15_000) { !workspace.state.value.busy && (workspace.state.value.sourceDocument != null || workspace.state.value.error != null) }
            idle(model, 5_000)
            val document = checkNotNull(workspace.state.value.sourceDocument)
            assertEquals(FILE_BYTES, document.documentBytes); assertEquals(FILE_SHA, document.documentSha256)
            assertEquals(FILE_SHA, sha(document.originalDocumentBytes())); assertEquals(2, document.sourceCount)
            assertEquals(SOURCE_ID, document.entries[0].sourceId); assertEquals("得间小说", document.entries[0].name)
            assertEquals(PROTECTED_SOURCE_ID, document.entries[1].sourceId)
            val selected = document.select(0) // Read-only immutable preview, never a business call.
            val selectedJson = JSONObject(selected.selectedDefinitionJson)
            assertEquals(SOURCE_ID, selectedJson.getString("bookSourceUrl")); assertTrue(selectedJson.getBoolean("enabled"))
            assertEquals(completedBefore, completedNames())
            marker("source-management-document-preview-original", Bundle().apply {
                putString("documentId", document.id); putLong("documentEpoch", workspace.state.value.sourceDocumentEpoch)
                putInt("documentBytes", document.documentBytes); putString("documentSha256", document.documentSha256)
                putInt("sourceCount", document.sourceCount); putInt("selectedIndex", 0); putString("sourceId", SOURCE_ID)
                putString("selectedDefinitionSha256", sha(selected.selectedDefinitionJson.toByteArray(Charsets.UTF_8)))
                putString("definitionClaim", selected.definitionClaim); putBoolean("completedRecordsUnchangedBeforeChoice", true)
            })
            phase = "explicit-import-target-only"
            clickWorkspace("reader_source_document_choose_0"); idle(model, 65_000)
            assertNull(workspace.state.value.sourceDocument); assertEquals(selected, workspace.state.value.sourceDocumentSelection)
            val imported = originalAfter(completedBefore, "source.import", runtime)
            assertEquals(selected.selectedDefinitionJson, imported.second.getString("originalBookSourceJson"))
            val importedRow = workspace.state.value.sources.single { it.sourceId == SOURCE_ID }
            assertTrue(importedRow.enabled)
            assertEquals(sourcesBefore, workspace.state.value.sources.filter { it.sourceId != SOURCE_ID })
            marker("source-management-import-original-ACK", managementBundle(imported).apply {
                putString("sourceVersion", importedRow.sourceVersion); putInt("selectedIndex", 0); putString("documentSha256", FILE_SHA)
            })

            phase = "disable-new-source-once"
            val beforeDisable = completedNames()
            val disableIndex = workspace.state.value.sources.indexOfFirst { it.sourceId == SOURCE_ID }; assertTrue(disableIndex >= 0)
            clickWorkspace("reader_source_toggle_$disableIndex"); idle(model, 65_000)
            val disabled = originalAfter(beforeDisable, "source.update", runtime, false, true)
            val disabledRow = workspace.state.value.sources.single { it.sourceId == SOURCE_ID }; assertFalse(disabledRow.enabled)
            assertEquals(importedRow.sourceVersion, disabled.second.getString("observedSourceVersion"))
            assertNextOriginal(imported, disabled)
            marker("source-management-disable-original-ACK", managementBundle(disabled).apply { putString("sourceVersion", disabledRow.sourceVersion) })

            phase = "enable-new-source-once"
            val beforeEnable = completedNames()
            val enableIndex = workspace.state.value.sources.indexOfFirst { it.sourceId == SOURCE_ID }; assertTrue(enableIndex >= 0)
            clickWorkspace("reader_source_toggle_$enableIndex"); idle(model, 65_000)
            val enabled = originalAfter(beforeEnable, "source.update", runtime, true, false)
            val enabledRow = workspace.state.value.sources.single { it.sourceId == SOURCE_ID }; assertTrue(enabledRow.enabled)
            assertEquals(disabledRow.sourceVersion, enabled.second.getString("observedSourceVersion"))
            assertNextOriginal(disabled, enabled)
            marker("source-management-enable-original-ACK", managementBundle(enabled).apply { putString("sourceVersion", enabledRow.sourceVersion) })

            phase = "cancel-first-delete-confirmation"
            val beforeCancel = completedNames(); val rowsBeforeCancel = workspace.state.value.sources.toList()
            val deleteIndex = workspace.state.value.sources.indexOfFirst { it.sourceId == SOURCE_ID }; assertTrue(deleteIndex >= 0)
            clickWorkspace("reader_source_delete_$deleteIndex"); compose.waitForIdle()
            val cancelledPresentation = checkNotNull(workspace.state.value.sourceDeletion)
            assertEquals("删除书源“得间小说”？", cancelledPresentation.title)
            assertEquals(beforeCancel, completedNames())
            compose.onNodeWithTag("reader_confirmation_dismiss").assertIsDisplayed().assertIsEnabled().performClick()
            compose.waitUntil(5_000) { workspace.state.value.sourceDeletion == null }; compose.waitForIdle(); idle(model, 5_000)
            assertEquals(beforeCancel, completedNames()); assertEquals(rowsBeforeCancel, workspace.state.value.sources)
            marker("source-management-delete-cancelled-no-original", Bundle().apply {
                putString("sourceId", SOURCE_ID); putString("confirmationId", cancelledPresentation.identity)
                putBoolean("completedRecordsUnchanged", true); putBoolean("sourceStillEnabled", true); putInt("newOriginalCount", 0)
            })

            phase = "new-explicit-delete-confirmation"
            val beforeDelete = completedNames()
            val finalIndex = workspace.state.value.sources.indexOfFirst { it.sourceId == SOURCE_ID }; assertTrue(finalIndex >= 0)
            clickWorkspace("reader_source_delete_$finalIndex"); compose.waitForIdle()
            val confirmedPresentation = checkNotNull(workspace.state.value.sourceDeletion)
            assertNotEquals(cancelledPresentation.identity, confirmedPresentation.identity)
            assertEquals("删除书源“得间小说”？", confirmedPresentation.title); assertEquals(beforeDelete, completedNames())
            compose.onNodeWithTag("reader_confirmation_confirm").assertIsDisplayed().assertIsEnabled().performClick()
            idle(model, 65_000)
            val deleted = originalAfter(beforeDelete, "source.delete", runtime, cleanupRequired = true)
            assertEquals(enabledRow.sourceVersion, deleted.second.getString("observedSourceVersion"))
            assertNextOriginal(enabled, deleted)
            assertEquals(sourcesBefore, workspace.state.value.sources); assertNull(workspace.state.value.sourceDeletion)
            marker("source-management-delete-original-ACK", managementBundle(deleted).apply {
                putString("confirmationId", confirmedPresentation.identity); putString("cancelledConfirmationId", cancelledPresentation.identity)
                putBoolean("sourceAbsent", true)
            })

            phase = "return-protected-bookshelf"
            compose.onNodeWithTag("reader_tab_library").assertIsEnabled().performClick()
            compose.waitUntil(20_000) { !model.state.value.busy && model.state.value.route == ReaderRoute.Bookshelf }
            compose.waitForIdle(); healthy(model)
            assertEquals(localBefore, model.state.value.books); assertEquals(remoteBefore, model.state.value.remoteBooks)
            assertSame(appOwner, (current.application as ReaderApplication).coreOwner)
            assertEquals(runtime, initializedActor(appOwner).runtimeSession); assertArrayEquals(unknownBefore, unknown())
            val newOriginals = freshRecords(completedBefore)
            assertEquals(4, newOriginals.size); assertTrue(newOriginals.all { it.second.getString("kind") == "sourceRules" && it.second.getString("sourceId") == SOURCE_ID })
            assertEquals(4, newOriginals.map { it.second.getLong("requestId") }.toSet().size)
            val active = File(current.filesDir, "Reader/Operations").listFiles().orEmpty()
            assertEquals(setOf("$UNKNOWN_ID.json"), active.filter { it.name.endsWith(".json") }.map { it.name }.toSet())
            assertTrue(active.none { it.name.endsWith(".new") || it.name.endsWith(".bak") })
            marker("source-management-protected-bookshelf-returned", Bundle().apply {
                putString("sourceId", SOURCE_ID); putString("runtimeSession", runtime); putInt("newSourceOriginalCount", 4)
                putBoolean("protectedLocalBooksUnchanged", true); putBoolean("protectedRemoteBooksUnchanged", true)
                putBoolean("searchDispatched", false); putBoolean("addDispatched", false); putInt("newProgressOriginalCount", 0)
            })
        } catch (failure: Throwable) {
            primary = failure
            if (::current.isInitialized) {
                if (phase == "one-standard-saf-document") try { capturePickerFailure(phase) } catch (diagnostic: Throwable) { failure.addSuppressed(diagnostic) }
                marker("method-failed", Bundle().apply { putString("failurePhase", phase); putString("failureClass", failure.javaClass.name) })
            }
            throw failure
        } finally {
            try { finishOnlyBookshelf() } catch (cleanup: Throwable) { if (primary == null) throw cleanup else primary.addSuppressed(cleanup) }
        }
        marker("method-exit")
    }

    private fun originalAfter(before: Set<String>, method: String, runtime: String, enabled: Boolean? = null,
        cleanupRequired: Boolean? = null): Triple<File, JSONObject, ByteArray> {
        val row = freshRecords(before).single(); val record = row.second
        assertEquals("sourceRules", record.getString("kind")); assertEquals(method, record.getString("method"))
        assertEquals(SOURCE_ID, record.getString("sourceId")); assertEquals(runtime, record.getString("runtimeSession"))
        assertTrue(record.getBoolean("completed")); assertTrue(record.getBoolean("queryQualified"))
        val params = SourceRulesReceiptPolicy.parameters(record)
        assertEquals(method, params.getJSONObject("mutation").getString("method"))
        assertEquals(SourceImportConfirmation.APPLIED, SourceRulesReceiptPolicy.receipt(record,
            SourceRulesReceiptPolicy.wireReceipt(record, record.getJSONObject("wireTerminal"))))
        val business = params.getJSONObject("mutation").getJSONObject("params")
        if (method == "source.delete") {
            assertEquals(1, business.getJSONArray("sourceIds").length()); assertEquals(SOURCE_ID, business.getJSONArray("sourceIds").getString(0))
            assertEquals(1, JSONObject(record.getString("receiptJson")).getJSONObject("data").getInt("deleted"))
        } else assertEquals(SOURCE_ID, business.getString("sourceId"))
        if (enabled != null) assertEquals(enabled, business.get("enabled"))
        if (cleanupRequired != null) {
            assertEquals(cleanupRequired, record.get("hostCleanupRequired")); assertEquals(cleanupRequired, record.getBoolean("hostCleanupConfirmed"))
            if (cleanupRequired) {
                assertEquals(runtime, record.getString("hostCleanupRuntimeSession"))
                val fact = JSONObject(record.getString("lastHostCleanupObservationJson"))
                assertEquals(SOURCE_ID, fact.getString("sourceId")); assertEquals(runtime, fact.getString("runtimeSession"))
                assertEquals("CONFIRMED", fact.getString("reason")); assertTrue(fact.getBoolean("confirmed"))
            }
        }
        val operations = File(current.filesDir, "Reader/Operations")
        for (suffix in listOf(".json", ".json.new", ".json.bak")) assertFalse(File(operations, record.getString("id") + suffix).exists())
        return row
    }
    private fun assertNextOriginal(previous: Triple<File, JSONObject, ByteArray>, next: Triple<File, JSONObject, ByteArray>) {
        val old = SourceRulesReceiptPolicy.parameters(previous.second); val current = SourceRulesReceiptPolicy.parameters(next.second)
        assertEquals(old.getLong("sequence") + 1, current.getLong("sequence")); assertNotEquals(old.getString("operationId"), current.getString("operationId"))
        assertNotEquals(previous.second.getLong("requestId"), next.second.getLong("requestId"))
    }
    private fun managementBundle(row: Triple<File, JSONObject, ByteArray>) = recordBundle(row).apply {
        val record = row.second; val params = SourceRulesReceiptPolicy.parameters(record)
        putString("sourceId", SOURCE_ID); putString("method", record.getString("method")); putString("coreOperationId", params.getString("operationId"))
        putLong("sequence", params.getLong("sequence")); putString("originalParametersJson", record.getString("parametersJson"))
        putString("parsedTerminalJson", record.getJSONObject("wireTerminal").toString()); putString("receiptJson", record.getString("receiptJson"))
        putBoolean("queryQualified", true); putBoolean("completed", true)
        putBoolean("hostCleanupRequired", record.optBoolean("hostCleanupRequired")); putBoolean("hostCleanupConfirmed", record.optBoolean("hostCleanupConfirmed"))
        if (record.has("lastHostCleanupObservationJson")) putString("hostCleanupObservationJson", record.getString("lastHostCleanupObservationJson"))
    }

    private fun idle(model: ReaderViewModel, timeout: Long) {
        compose.waitUntil(timeout) { !model.state.value.busy && !model.onlineSources.state.value.busy }
        compose.waitForIdle(); healthy(model)
        val online = model.onlineSources.state.value
        assertNull(online.error); assertFalse(online.pending)
    }
    private fun healthy(model: ReaderViewModel) {
        val state = model.state.value
        assertTrue(state.ready && state.pendingReady); assertFalse(state.busy); assertNull(state.reading)
        assertNull(state.error); assertNull(state.protocolRecoveryReference)
        assertTrue(state.pendingImports.isEmpty() && state.pendingBookmarks.isEmpty()); assertNull(state.pendingSearchClose)
        assertEquals(listOf(UNKNOWN_ID), state.pendingPositions.map { it.id }); assertEquals("local", state.pendingPositions.single().sourceId)
        assertEquals(0L, state.importRevision); unknown()
        val status = (current.application as ReaderApplication).coreProtocolRecovery.view.value
        assertTrue(status.initialized && status.admissionOpen && status.items.isEmpty())
    }
    private fun clickWorkspace(tag: String) {
        compose.onNode(hasScrollAction() and hasAnyAncestor(hasTestTag("reader_online_workspace")))
            .performScrollToNode(hasTestTag(tag))
        compose.onNodeWithTag(tag).assertIsDisplayed().assertIsEnabled().performClick()
    }
    /** Read only the already-initialized singleton. It never calls a transport or initializes a delegate. */
    private fun initializedActor(owner: AppCoreOwner): ReaderCoreEventActor {
        val delegate = field(owner, "actorDelegate") as Lazy<*>
        assertTrue(delegate.isInitialized()); return delegate.value as ReaderCoreEventActor
    }
    private fun field(owner: Any, name: String): Any? = owner.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(owner)
    private fun completedNames() = File(current.filesDir, "Reader/Operations/Completed").listFiles().orEmpty()
        .filter { it.name.endsWith(".json") }.mapTo(linkedSetOf()) { it.name }
    private fun freshRecords(before: Set<String>) = (completedNames() - before).map { name ->
        val file = File(current.filesDir, "Reader/Operations/Completed/$name")
        assertTrue(file.length() in 1..8L * 1024 * 1024)
        assertFalse(File(file.path + ".new").exists()); assertFalse(File(file.path + ".bak").exists())
        val raw = file.readBytes(); Triple(file, JSONObject(raw.toString(Charsets.UTF_8)), raw)
    }
    private fun recordBundle(row: Triple<File, JSONObject, ByteArray>) = Bundle().apply {
        putString("originalId", row.second.getString("id")); putString("originalRecordSha256", sha(row.third)); putInt("originalRecordBytes", row.third.size)
        putLong("requestId", row.second.getLong("requestId")); putString("runtimeSession", row.second.getString("runtimeSession"))
    }
    private fun unknown(): ByteArray {
        val operations = File(current.filesDir, "Reader/Operations"); val base = File(operations, "$UNKNOWN_ID.json")
        assertEquals(739L, base.length()); val raw = base.readBytes(); assertEquals(UNKNOWN_SHA, sha(raw))
        assertFalse(JSONObject(raw.toString(Charsets.UTF_8)).has("wireTerminal"))
        assertFalse(File(base.path + ".new").exists()); assertFalse(File(base.path + ".bak").exists())
        assertFalse(File(operations, "Completed/$UNKNOWN_ID.json").exists())
        assertFalse(File(operations, "Completed/$UNKNOWN_ID.json.new").exists()); assertFalse(File(operations, "Completed/$UNKNOWN_ID.json.bak").exists())
        return raw
    }
    private fun finishOnlyBookshelf() {
        val owned = scenario ?: return
        if (owned.state != Lifecycle.State.DESTROYED) {
            val model = current.readerViewModel
            if (model.state.value.reading != null || model.state.value.busy || model.state.value.route != ReaderRoute.Bookshelf || model.state.value.error != null || model.state.value.protocolRecoveryReference != null) {
                marker("activity-retained-for-controller"); throw AssertionError("Retain failed original; no automatic navigation, reconcile or second click")
            }
            val destroyed = CountDownLatch(1)
            val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_DESTROY) destroyed.countDown() }
            try {
                instrumentation.runOnMainSync { current.lifecycle.addObserver(observer); current.finish() }
                assertTrue(destroyed.await(5, TimeUnit.SECONDS)); compose.waitUntil(5_000) { owned.state == Lifecycle.State.DESTROYED }
            } finally { instrumentation.runOnMainSync { current.lifecycle.removeObserver(observer) } }
        }
        assertEquals(Lifecycle.State.DESTROYED, owned.state); owned.close(); marker("activity-destroyed")
    }
    private fun marker(step: String, details: Bundle = Bundle()) {
        details.putString("readerStep", step); details.putString("readerMethod", METHOD)
        details.putInt("readerProcessPid", AndroidProcess.myPid()); details.putLong("readerProcessStartElapsedMs", AndroidProcess.getStartElapsedRealtime())
        details.putLong("readerElapsedMs", SystemClock.elapsedRealtime()); instrumentation.sendStatus(2, details)
    }
    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun shaFile(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream -> val b = ByteArray(64 * 1024); while (true) { val n = stream.read(b); if (n < 0) break; if (n > 0) digest.update(b, 0, n) } }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    // Source-bound SAF document rows, one optional navigation and at most two observed container scrolls.
    private fun selectRealDocument(name: String) {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val watcherName = "reader-original-picker-open-consent"
        val consentPackage = "com.oplus.securitypermission"
        val consentTitle = "“Reader”想要打开“io.reader.minliny.test”"
        var consentClickAttempted = false
        device.registerWatcher(watcherName, UiWatcher {
            val title = By.pkg(consentPackage).res("$consentPackage:id/alertTitle").text(consentTitle)
            val checkbox = By.pkg(consentPackage).res("$consentPackage:id/coui_security_alert_dialog_checkbox")
                .text("始终允许打开").checkable(true).checked(false).enabled(true)
            val open = By.pkg(consentPackage).res("android:id/button1").text("打开").enabled(true)
            if (consentClickAttempted || device.findObjects(title).size != 1 ||
                device.findObjects(checkbox).size != 1 || device.findObjects(open).size != 1) {
                false
            } else {
                val titleSelector = UiSelector().packageName(consentPackage)
                    .resourceId("$consentPackage:id/alertTitle").text(consentTitle)
                val checkboxSelector = UiSelector().packageName(consentPackage)
                    .resourceId("$consentPackage:id/coui_security_alert_dialog_checkbox").text("始终允许打开")
                val openSelector = UiSelector().packageName(consentPackage)
                    .resourceId("android:id/button1").text("打开").enabled(true)
                if (!device.findObject(titleSelector).exists() || device.findObject(checkboxSelector).isChecked ||
                    !device.findObject(openSelector).isEnabled) false else {
                    consentClickAttempted = true // At most one dispatch, including a failed click.
                    marker("oem-open-observed", Bundle().apply { putString("dialogTitle", consentTitle); putBoolean("checkboxChecked", false) })
                    val clicked = device.findObject(openSelector).click()
                    val gone = device.findObject(titleSelector).waitUntilGone(2_000)
                    marker("oem-open-ACK", Bundle().apply { putBoolean("clickDispatched", clicked); putBoolean("dialogGone", gone) })
                    clicked
                }
            }
        })
        try {
            val provider = "com\\.(google\\.)?android\\.documentsui"
            val providerSelector = UiSelector().packageNameMatches(provider)
            assertTrue("System document picker must actually open",
                device.findObject(providerSelector).waitForExists(10_000))
            marker("picker-visible")
            // android:id/title is a real document-row title. Toolbar and breadcrumb
            // labels can name Reader too; they never authorize folder navigation.
            val file = providerSelector.resourceId("android:id/title").text(name)
            val folder = providerSelector.resourceId("android:id/title").text("Reader")
            val drawer = providerSelector.resourceIdMatches(".*:id/roots_list")
            val downloads = drawer.childSelector(UiSelector().packageNameMatches(provider).textMatches("Downloads|下载|下载内容"))
            val readerBreadcrumb = providerSelector.resourceIdMatches(".*:id/breadcrumb_text").text("Reader")
            val downloadsBreadcrumb = providerSelector.resourceIdMatches(".*:id/breadcrumb_text").textMatches("Downloads|下载|下载内容")
            val exactPreview = providerSelector.resourceIdMatches(".*:id/preview_icon").description("预览“$name”文件")
            if (!device.findObject(file).exists()) {
                // The observed exact preview plus both breadcrumb identities proves
                // this retained Reader folder; the preview itself is never clicked.
                val alreadyInReader = device.findObject(readerBreadcrumb).exists() &&
                    device.findObject(downloadsBreadcrumb).exists() && device.findObject(exactPreview).exists()
                if (!alreadyInReader) {
                    // One roots -> Downloads -> Reader document-row navigation only.
                    if (!device.findObject(drawer).exists()) {
                        val roots = providerSelector.descriptionMatches("Show roots|显示根目录|显示位置|导航抽屉")
                        clickPickerOnce(device, roots, "picker-roots", drawer)
                    }
                    marker("picker-drawer-ready")
                    clickPickerOnce(device, downloads, "picker-downloads", folder, gone = drawer)
                    clickPickerOnce(device, folder, "picker-folder", readerBreadcrumb)
                }
                assertTrue("Current Reader breadcrumb absent", device.findObject(readerBreadcrumb).waitForExists(5_000))
                assertTrue("Downloads ancestor breadcrumb absent", device.findObject(downloadsBreadcrumb).waitForExists(5_000))
                exposeDocumentTitle(device, provider, file)
            }
            assertTrue("Original input missing: $name", device.findObject(file).waitForExists(5_000))
            marker("picker-file-click")
            // UiObject stores a selector and resolves its live node during this one click.
            val clickReturned = device.findObject(file).click()
            marker("picker-file-click-observed", Bundle().apply { putBoolean("clickReturned", clickReturned) })
            val pickerGone = device.findObject(providerSelector).waitUntilGone(10_000)
            assertTrue("System picker disappearance was not observed within 10 seconds", pickerGone)
            val readerWindowObserved = device.findObject(UiSelector().packageName("io.reader.minliny")).waitForExists(10_000)
            assertTrue("Reader's actual window was not observed after file selection", readerWindowObserved)
            marker("picker-returned", Bundle().apply {
                putBoolean("clickReturned", clickReturned); putBoolean("pickerGone", pickerGone)
                putBoolean("readerWindowObserved", readerWindowObserved)
            })
        } finally {
            device.removeWatcher(watcherName)
        }
    }

    /** At most two real scrolls in the unique actual DocumentsUI scroll container. */
    private fun exposeDocumentTitle(device: UiDevice, provider: String, file: UiSelector) {
        val container = UiSelector().packageNameMatches(provider).className("android.widget.ScrollView").scrollable(true)
        for (attempt in 1..2) {
            val beforeVisible = device.findObject(file).waitForExists(750)
            if (beforeVisible) return
            assertTrue("Unique DocumentsUI scroll container absent", device.findObject(container.instance(0)).exists())
            assertFalse("DocumentsUI scroll container ambiguous", device.findObject(container.instance(1)).exists())
            val returned = UiScrollable(container).scrollForward(35)
            val afterVisible = device.findObject(file).waitForExists(1_000)
            marker("picker-document-scroll-$attempt", Bundle().apply {
                putInt("attempt", attempt); putInt("maxScrolls", 2); putInt("scrollSteps", 35)
                putString("containerClass", "android.widget.ScrollView")
                putBoolean("beforeVisible", beforeVisible); putBoolean("scrollReturned", returned); putBoolean("afterVisible", afterVisible)
            })
            if (afterVisible) return // The Boolean alone does not prove the original file became visible.
        }
        throw AssertionError("Exact document-row title absent after two bounded DocumentsUI scrolls")
    }

    /** One read-only failure observation; no picker action, URI injection or provider query. */
    private fun capturePickerFailure(phase: String) {
        if (failureDiagnosticCaptured) return
        failureDiagnosticCaptured = true // Reserve the sole attempt even if its dump/marker fails.
        val details = Bundle().apply {
            putString("failurePhase", phase); putString("mimeExtension", "json")
            val mapped = MimeTypeMap.getSingleton().getMimeTypeFromExtension("json")
            putBoolean("mappedMimePresent", mapped != null)
            if (mapped != null) putString("mappedMime", mapped)
            putString("mimeClaim", "MIMETYPEMAP_LOOKUP_ONLY_NOT_DOCUMENT_PROVIDER_OR_RETURNED_URI")
        }
        val file = File(instrumentation.targetContext.cacheDir, "reader-a4-source-picker-failure-${UUID.randomUUID()}.xml")
        details.putString("cacheFile", file.name)
        try {
            UiDevice.getInstance(instrumentation).dumpWindowHierarchy(file)
            val bytes = file.length(); details.putLong("hierarchyBytes", bytes)
            val bounded = file.isFile && bytes in 1..2L * 1024 * 1024
            details.putBoolean("hierarchySaved", bounded)
            if (bounded) details.putString("hierarchySha256", shaFile(file))
        } catch (error: Exception) {
            details.putBoolean("hierarchySaved", false); details.putString("hierarchyFailureClass", error.javaClass.name)
        }
        marker("online-source-picker-failure-diagnostic", details)
    }

    private fun clickPickerOnce(device: UiDevice, selector: UiSelector, stage: String, next: UiSelector, gone: UiSelector? = null) {
        assertTrue("$stage target absent", device.findObject(selector).waitForExists(5_000))
        marker("$stage-click")
        // Reacquire from the exact selector immediately before the only click.
        val clickReturned = device.findObject(selector).click()
        marker("$stage-click-observed", Bundle().apply { putBoolean("clickReturned", clickReturned) })
        if (gone != null) assertTrue("$stage did not retire its drawer", device.findObject(gone).waitUntilGone(5_000))
        assertTrue("$stage did not expose the next state", device.findObject(next).waitForExists(5_000))
        marker("$stage-ready")
    }


    companion object {
        private const val METHOD = "intactRealSourceDocumentImportsIdejianTogglesCancelsThenDeletesWithOriginalReceipts"
        private const val FILE_NAME = "Reader-real-sources.json"
        private const val FILE_SHA = "f26fa3bd2c9928f8a40a374e5af09dd747a484ff5094f8baf19b880c6f8ea322"
        private const val FILE_BYTES = 5606
        private const val SOURCE_ID = "https://m.idejian.com"
        private const val PROTECTED_SOURCE_ID = "https://dushu.baidu.com/"
        private const val UNKNOWN_ID = "android-progress-34f21d9a-6dc6-478e-a551-f85219609563"
        private const val UNKNOWN_SHA = "c4a496c111870155bd93435b76aae2dd70402fbd65e834bf4ebb91297df1ec82"
    }
}
