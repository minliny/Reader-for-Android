package com.reader.host

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.reader.android.data.adapter.FakeAndroidTtsAdapter
import com.reader.android.data.adapter.FakePermissionRuntimeAdapter
import com.reader.android.data.adapter.WebDavCredentialStore
import com.reader.api.ReaderCoreClient
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/**
 * Automated emulator tier only. This does not claim physical haptic, TTS,
 * brightness, picker-user-flow, or background-lifecycle proof.
 */
@RunWith(AndroidJUnit4::class)
class ReaderUi25HostApi35SmokeTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun facade(): HostFacade = HostFacade(
        context = context,
        tts = FakeAndroidTtsAdapter(),
        permission = FakePermissionRuntimeAdapter(),
        notification = null,
        webDav = null,
        credentials = WebDavCredentialStore(),
        downloadCache = null,
        readerUi25Services = ReaderUi25HostServices(
            activityHostProvider = { null },
            fontRegistration = AndroidReaderFontRegistrationHost(context),
            networkStatus = AndroidReaderNetworkStatusHost(context),
            haptics = AndroidReaderHapticsHost(context),
            backgroundTasks = AndroidWorkManagerBackgroundTaskHost(context)
        )
    )

    @Test
    fun contractManifestIsExact55AndUiCapabilitiesFailClosedWithoutActivity() {
        assertEquals(55, ReaderUi25HostCapabilityManifest.entries.size)
        assertEquals(55, ReaderUi25HostCapabilityManifest.capabilities.size)
        val productionAdapter = ReaderCoreClient.get().hostAdapter()
        assertTrue(ReaderUi25HostCapabilityManifest.missingFrom(productionAdapter).isEmpty())
        assertEquals(
            55,
            productionAdapter.registeredCapabilities()
                .count { it in ReaderUi25HostCapabilityManifest.capabilities }
        )

        val facade = facade()
        val file = FileSelect25Handler(facade).handle(
            HostRequest(1, 1, "file.select", "{}")
        )
        assertTrue(file.isError())
        assertEquals("REQUIRES_UI_CONTEXT", (file as HostReply.Error).code())

        val brightness = BrightnessGet25Handler(facade).handle(
            HostRequest(1, 2, "brightness.get", "{}")
        )
        assertTrue(brightness.isError())
        assertEquals("REQUIRES_UI_CONTEXT", (brightness as HostReply.Error).code())
    }

    @Test
    fun clipboardAndNetworkUseRealAndroidServicesWithHeadlessPrivacyBoundary() {
        val facade = facade()
        val write = ClipboardWrite25Handler(facade).handle(
            HostRequest(1, 3, "clipboard.write", JSONObject().put("text", "api35-smoke").toString())
        )
        assertTrue(write.isComplete())
        val read = ClipboardRead25Handler(facade).handle(
            HostRequest(1, 4, "clipboard.read", "{}")
        )
        assertTrue(read.isComplete())
        val clipboardResult = JSONObject((read as HostReply.Complete).resultJson())
        assertTrue(clipboardResult.has("text"))
        // Android 13+ may hide clipboard contents from a headless
        // instrumentation process. Both nullable text and an owned-clip
        // round-trip are valid real platform results; fabricated text is not.
        assertTrue(
            clipboardResult.isNull("text") ||
                clipboardResult.getString("text") == "api35-smoke"
        )

        val network = NetworkStatus25Handler(facade).handle(
            HostRequest(1, 5, "network.status", "{}")
        )
        assertTrue(network.isComplete())
        val result = JSONObject((network as HostReply.Complete).resultJson())
        assertTrue(result.getString("status") in setOf("online", "limited", "offline"))
        assertTrue(result.has("interface"))
    }

    @Test
    fun systemFontAndWorkManagerHaveConcretePlatformIdentities() {
        val facade = facade()
        val systemFont = sequenceOf(
            File("/system/fonts/Roboto-Regular.ttf"),
            File("/system/fonts/NotoSans-Regular.ttf")
        ).firstOrNull(File::isFile)
        assertNotNull("emulator system font required", systemFont)
        val font = FontRegisterFile25Handler(facade).handle(
            HostRequest(
                1,
                6,
                "font.registerFile",
                JSONObject().put("path", requireNotNull(systemFont).absolutePath).toString()
            )
        )
        assertTrue(font.isComplete())
        assertTrue(JSONObject((font as HostReply.Complete).resultJson()).getBoolean("registered"))

        val task = BackgroundTaskStart25Handler(facade).handle(
            HostRequest(
                1,
                7,
                "background.task.start",
                JSONObject().put("name", "api35-smoke").toString()
            )
        )
        assertTrue(task.isComplete())
        val taskId = JSONObject((task as HostReply.Complete).resultJson()).getString("taskId")
        assertEquals(taskId, UUID.fromString(taskId).toString())
    }

    @Test
    fun hapticsEitherPerformOrReturnExplicitNotAvailable() {
        val reply = Haptics25Handler(facade(), ReaderHapticStyle.LIGHT).handle(
            HostRequest(1, 8, "haptics.light", "{}")
        )
        if (reply.isComplete()) {
            assertTrue(JSONObject((reply as HostReply.Complete).resultJson()).getBoolean("performed"))
        } else {
            assertEquals("NOT_AVAILABLE", (reply as HostReply.Error).code())
        }
    }
}
