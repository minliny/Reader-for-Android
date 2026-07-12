package com.reader.host

import android.content.Context
import android.content.Intent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.reader.android.data.adapter.AndroidEncryptedCredentialRecord
import com.reader.android.data.adapter.FakeAndroidTtsAdapter
import com.reader.android.data.adapter.FakePermissionRuntimeAdapter
import com.reader.android.data.adapter.WebDavCredentialStore
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

class ReaderUi25HostTestActivity : ComponentActivity()

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class ReaderUi25HostCapabilitiesRobolectricTest {
    private class MemoryCredentialKeystore : WebDavCredentialStore.CredentialKeystore {
        private val values = mutableMapOf<String, String>()
        override fun save(identifier: String, secret: String): AndroidEncryptedCredentialRecord =
            AndroidEncryptedCredentialRecord(identifier, "test", secret, "", "").also {
                values[identifier] = secret
            }
        override fun load(record: AndroidEncryptedCredentialRecord) = values.getValue(record.identifier)
        override fun revoke(record: AndroidEncryptedCredentialRecord) =
            values.remove(record.identifier) != null
    }

    @After
    fun clearBinding() {
        ReaderUiActivityCapabilityBinding.clearForTest()
        runCatching { WorkManagerTestInitHelper.closeWorkDatabase() }
    }

    @Test
    fun `API35 Activity host applies brightness screen flag and launches chooser`() {
        val controller = Robolectric.buildActivity(ReaderUi25HostTestActivity::class.java)
        val activity = controller.get()
        val activityHost = AndroidReaderUiActivityCapabilityHost(activity)
        controller.setup()

        assertEquals(0.35f, activityHost.setBrightness(0.35f), 0.001f)
        assertEquals(0.35f, activityHost.getBrightness(), 0.001f)

        assertTrue(activityHost.setKeepAwake(true))
        assertTrue(
            activity.window.attributes.flags and
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0
        )
        assertTrue(activityHost.setKeepAwake(false))
        assertFalse(
            activity.window.attributes.flags and
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0
        )

        assertTrue(activityHost.shareText("Reader UI 2.5"))
        val chooser = shadowOf(activity).nextStartedActivity
        assertNotNull(chooser)
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        assertEquals(Intent.ACTION_SEND, send?.action)
        assertEquals("Reader UI 2.5", send?.getStringExtra(Intent.EXTRA_TEXT))
    }

    @Test
    fun `API35 clipboard aliases round trip through ClipboardManager`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val facade = HostFacade(
            context = context,
            tts = FakeAndroidTtsAdapter(),
            permission = FakePermissionRuntimeAdapter(),
            notification = null,
            webDav = null,
            credentials = WebDavCredentialStore(MemoryCredentialKeystore()),
            downloadCache = null
        )

        val write = ClipboardWrite25Handler(facade).handle(
            HostRequest(1, 1, "clipboard.write", JSONObject().put("text", "round-trip").toString())
        )
        assertTrue(write.isComplete())
        assertTrue(JSONObject((write as HostReply.Complete).resultJson()).getBoolean("written"))

        val read = ClipboardRead25Handler(facade).handle(
            HostRequest(1, 2, "clipboard.read", "{}")
        )
        assertTrue(read.isComplete())
        assertEquals(
            "round-trip",
            JSONObject((read as HostReply.Complete).resultJson()).getString("text")
        )
    }

    @Test
    fun `API35 network status returns concrete offline snapshot without guessing online`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val snapshot = AndroidReaderNetworkStatusHost(context).snapshot()
        assertFalse(snapshot.connected)
        assertEquals("offline", snapshot.status)
        // Robolectric API 35 exposes a cellular transport without a validated
        // INTERNET capability. Preserve the transport while reporting offline.
        assertEquals("cellular", snapshot.interfaceName)
    }

    @Test
    fun `API35 font registration copies and registers a real readable font`() {
        val systemFont = sequenceOf(
            File("/System/Library/Fonts/SFNSMono.ttf"),
            File("/System/Library/Fonts/Symbol.ttf"),
            File("/System/Library/Fonts/Menlo.ttc")
        ).firstOrNull { it.isFile && it.canRead() }
        assumeTrue("macOS system font required for Robolectric font proof", systemFont != null)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val host = AndroidReaderFontRegistrationHost(context)
        val registered = host.register(requireNotNull(systemFont).absolutePath)

        assertTrue(File(registered.path).isFile)
        assertTrue(registered.fontNames.isNotEmpty())
        assertNotNull(host.resolve(registered.fontNames.single()))
    }

    @Test
    fun `API35 background task start creates a real WorkManager identity`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        val host = AndroidWorkManagerBackgroundTaskHost(context)
        val started = host.start("reader-ui-2.5-proof")
        val info = WorkManager.getInstance(context)
            .getWorkInfoById(java.util.UUID.fromString(started.taskId))
            .get(5, java.util.concurrent.TimeUnit.SECONDS)

        assertNotNull(info)
        assertTrue(requireNotNull(info).tags.contains("reader-ui-host-task:reader-ui-2.5-proof"))
    }

    @Test
    fun `Activity generation prevents destroyed screen from clearing newer binding`() {
        val first = NoOpActivityHost()
        val second = NoOpActivityHost()
        val firstGeneration = ReaderUiActivityCapabilityBinding.bind(first)
        val secondGeneration = ReaderUiActivityCapabilityBinding.bind(second)

        ReaderUiActivityCapabilityBinding.unbind(first, firstGeneration)
        assertTrue(ReaderUiActivityCapabilityBinding.currentHost() === second)

        ReaderUiActivityCapabilityBinding.unbind(second, secondGeneration)
        assertEquals(null, ReaderUiActivityCapabilityBinding.currentHost())
        assertEquals(1, first.closeCalls)
        assertEquals(1, second.closeCalls)
    }

    private class NoOpActivityHost : ReaderUiActivityCapabilityHost {
        var closeCalls = 0
        override fun selectFiles(
            scope: ReaderUiHostRequestScope,
            mimeTypes: List<String>,
            allowsMultiple: Boolean
        ) = ReaderFileSelectionResult(emptyList())
        override fun setBrightness(value: Float) = value
        override fun getBrightness() = 0.5f
        override fun setKeepAwake(keepAwake: Boolean) = true
        override fun shareText(text: String) = true
        override fun shareFile(path: String) = true
        override fun close() { closeCalls++ }
    }
}
