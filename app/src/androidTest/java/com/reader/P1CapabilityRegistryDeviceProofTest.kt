package com.reader

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.reader.android.AppProvider
import com.reader.api.ReaderCoreClient
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * P1 device-level proof: all P1 capabilities (source/RSS, WebDAV/backup) are
 * registered on the [com.reader.host.HostAdapter] after
 * [ReaderCoreClient.init] runs with a real Android [Context].
 *
 * **What this proves**: the full capability registration code path in
 * [ReaderCoreClient.buildHostRuntime] (which wires [SourceRssContext] +
 * [com.reader.host.WebDavContext] + [com.reader.host.HostFacade]) runs on
 * the Dalvik/ART class loader — not just JVM. Every P1 capability is
 * resolvable via [com.reader.host.HostAdapter.isRegistered], proving the
 * `registerHandlers` extension functions executed on device.
 *
 * **Pattern**: uses the same `@Before` singleton guard as
 * [Stage6AsyncResultOverlayScreenshotTest] — if a prior test called
 * [ReaderCoreClient.resetForTest], we re-init with the target context so
 * the full capability set (including HostFacade) is registered.
 */
@RunWith(AndroidJUnit4::class)
class P1CapabilityRegistryDeviceProofTest {

    private val sourceRssCapabilities = listOf(
        "source.list",
        "source.add",
        "source.remove",
        "source.set_enabled",
        "source.import",
        "source.export",
        "source.debug.run",
        "source.debug.detect",
        "rss.subscription.list",
        "rss.subscription.add",
        "rss.subscription.delete",
        "rss.refresh"
    )

    private val webDavBackupCapabilities = listOf(
        "webdav.connect",
        "webdav.upload",
        "webdav.download",
        "webdav.list",
        "webdav.delete",
        "webdav.mkdir",
        "backup.create",
        "backup.restore"
    )

    @Before
    fun ensureCoreClientInitialized() {
        // AppProvider MUST be initialized before ReaderCoreClient so that
        // Room DB, DataStore, TTS engine, and WebDAV client are backed by
        // real storage. ReaderApplication.onCreate normally does this, but
        // a prior test might have closed it.
        if (!AppProvider.isInitialized) {
            AppProvider.init(InstrumentationRegistry.getInstrumentation().targetContext)
        }
        try {
            ReaderCoreClient.get()
        } catch (e: IllegalStateException) {
            ReaderCoreClient.init(InstrumentationRegistry.getInstrumentation().targetContext)
        }
    }

    /**
     * All 12 source/RSS capabilities must be registered on the device-level
     * [com.reader.host.HostAdapter] after [ReaderCoreClient.init] with a
     * real [Context]. This proves `SourceRssContext.registerHandlers` ran
     * on the Dalvik/ART class loader.
     */
    @Test
    fun p1_sourceRssCapabilitiesRegisteredOnDevice() {
        val adapter = ReaderCoreClient.get().hostAdapter()
        for (capability in sourceRssCapabilities) {
            assertTrue(
                "$capability must be registered on device after ReaderCoreClient.init(context)",
                adapter.isRegistered(capability)
            )
        }
    }

    /**
     * All 8 webdav/backup capabilities must be registered on the
     * device-level [com.reader.host.HostAdapter]. This proves
     * `WebDavContext.registerHandlers` ran on the Dalvik/ART class loader.
     */
    @Test
    fun p1_webDavBackupCapabilitiesRegisteredOnDevice() {
        val adapter = ReaderCoreClient.get().hostAdapter()
        for (capability in webDavBackupCapabilities) {
            assertTrue(
                "$capability must be registered on device after ReaderCoreClient.init(context)",
                adapter.isRegistered(capability)
            )
        }
    }

    /**
     * [AppProvider.ttsSessionController] must be non-null after
     * [AppProvider.init] + [ReaderCoreClient.init] on device. This proves
     * the P1-4 TTS session orchestrator is wired with the real
     * [com.reader.android.data.adapter.AndroidTtsEngine] +
     * [com.reader.android.data.adapter.AndroidAudioFocusController] and
     * ready for the UI to drive via `tts.start` / `tts.pause` / `tts.stop`.
     */
    @Test
    fun p1_ttsSessionControllerAvailableOnDevice() {
        val controller = AppProvider.ttsSessionController
        assertNotNull(
            "AppProvider.ttsSessionController must be non-null on device after init",
            controller
        )
    }
}
