@file:Suppress("DEPRECATION") // The production host is a platform Activity.

package io.reader.rebuild

import android.app.Activity
import android.app.Fragment
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import android.os.Process
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.reader.core.protocol.BookshelfGetParams
import com.reader.core.protocol.ReaderM1
import io.reader.rebuild.features.bookshelf.LocalImportCoordinator
import io.reader.rebuild.features.shell.ReaderActivity
import io.reader.rebuild.host.storage.FileAndAssetHost
import io.reader.rebuild.host.storage.LibraryRuntime
import io.reader.rebuild.host.storage.SystemDocumentPicker
import io.reader.rebuild.host.system.SystemSurfaceHost
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class SystemDocumentPickerTest {
    private val runner = InstrumentationRegistry.getInstrumentation()
    private val context = runner.targetContext
    private val resolver = context.contentResolver

    @Test fun authorizedPickerImportRetainsOriginal() = runBlocking {
        check(context.packageName == "io.reader.minliny.rebuild.android.dev")
        val run = requireNotNull(InstrumentationRegistry.getArguments().getString("readerM1Run"))
        require(run.matches(Regex("[A-Za-z0-9][A-Za-z0-9_-]{0,63}")))
        val dir = File(context.filesDir, "instrumentation-saf/$run")
        check(dir.mkdirs()) { "Use a new readerM1Run for the system picker test" }
        val name = "m1-saf-${UUID.randomUUID().toString().take(8)}.epub"
        val seeded = createDownload(name)
        var removed = false
        var activity: Activity? = null
        var opened: LibraryRuntime? = null
        try {
            val host = runner.startActivitySync(
                Intent(context, ReaderActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            activity = host
            val beforeSelection = externalDocument("${Environment.DIRECTORY_DOWNLOADS}/$name")
            check(uriPermission(beforeSelection) == PackageManager.PERMISSION_DENIED)
            val receiver = PickerResultFragment()
            launchPicker(host, receiver)
            val picker = selectDownload(name)
            val result = withTimeout(10_000) { receiver.result.await() }
            val grantedUri = requireNotNull(result.data)
            check(grantedUri != seeded && DocumentsContract.isDocumentUri(context, grantedUri))
            check(result.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            check(uriPermission(grantedUri) == PackageManager.PERMISSION_GRANTED)

            val errors = ConcurrentLinkedQueue<Throwable>()
            val runtime = LibraryRuntime.open(File(dir, "library"), SystemSurfaceHost(), errors::add)
            opened = runtime
            val files = FileAndAssetHost(dir)
            val document = SystemDocumentPicker.granted(resolver, grantedUri)
            check(document.name == name)
            val shelf = LocalImportCoordinator(runtime.core, files).import(document)
            val sha = NativeM1CoreProof.EPUB_SHA
            check(shelf.created && shelf.sourceId == "local" && shelf.bookId == "local:$sha")
            check(resolver.delete(seeded, null, null) == 1)
            removed = true
            val record = runtime.core.call(ReaderM1.bookshelfGet, BookshelfGetParams("local", shelf.bookId))
            val retained = files.authorize(sha).file
            check(record.book?.bookId == shelf.bookId && record.original?.sha256 == sha)
            check(retained.canonicalFile.toPath().startsWith(dir.canonicalFile.toPath()))
            check(retained.name == "$sha.source" && FileAndAssetHost.sha256(retained) == sha)
            check(errors.isEmpty()) { errors.toString() }
            println("M1_SAF pid=${Process.myPid()} route=$run picker=$picker action=${Intent.ACTION_OPEN_DOCUMENT}" +
                " documentProvider=${grantedUri.authority} readGrant=true retainedPrivateSource=$sha seedRemoved=true")
        } finally {
            try { opened?.close() }
            finally {
                runner.runOnMainSync { activity?.finish() }
                if (!removed) resolver.delete(seeded, null, null)
            }
        }
    }

    private fun createDownload(name: String): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "application/epub+zip")
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = requireNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values))
        try {
            runner.context.assets.open("fixtures/m1-window.epub").use { original ->
                requireNotNull(resolver.openOutputStream(uri)).use { original.copyTo(it) }
            }
            val publish = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
            check(resolver.update(uri, publish, null, null) == 1)
        } catch (failure: Exception) {
            resolver.delete(uri, null, null)
            throw failure
        }
        return uri
    }

    private fun launchPicker(activity: Activity, receiver: PickerResultFragment) {
        val intent = SystemDocumentPicker.request().apply {
            putExtra(DocumentsContract.EXTRA_INITIAL_URI, externalDocument(Environment.DIRECTORY_DOWNLOADS))
        }
        runner.runOnMainSync {
            activity.fragmentManager.beginTransaction().add(receiver, "m1-picker-result").commitNow()
            receiver.open(intent)
        }
    }

    private suspend fun selectDownload(name: String): String = withTimeout(20_000) {
        var picker: String? = null
        while (picker == null) {
            val window = runner.uiAutomation.rootInActiveWindow
            val pkg = window?.packageName?.toString()
            if (pkg in setOf("com.android.documentsui", "com.google.android.documentsui")) {
                val row = window?.findAccessibilityNodeInfosByText(name.substringBeforeLast('.'))?.firstOrNull {
                    it.isVisibleToUser && it.text?.toString() in setOf(name, name.substringBeforeLast('.'))
                }
                if (row != null && clickRow(row)) picker = pkg
            }
            if (picker == null) delay(100)
        }
        picker
    }

    private fun clickRow(row: AccessibilityNodeInfo): Boolean {
        var node: AccessibilityNodeInfo? = row
        while (node != null && !node.isClickable) node = node.parent
        return node?.isEnabled == true && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    private fun externalDocument(path: String) =
        DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:$path")

    private fun uriPermission(uri: Uri) =
        context.checkUriPermission(uri, Process.myPid(), Process.myUid(), Intent.FLAG_GRANT_READ_URI_PERMISSION)
}

class PickerResultFragment : Fragment() {
    val result = CompletableDeferred<Intent>()

    fun open(request: Intent) = startActivityForResult(request, PICK_DOCUMENT)

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != PICK_DOCUMENT) return
        if (resultCode == Activity.RESULT_OK && data != null) result.complete(data)
        else result.completeExceptionally(IllegalStateException("System picker returned result $resultCode"))
    }

    private companion object { const val PICK_DOCUMENT = 16 }
}
