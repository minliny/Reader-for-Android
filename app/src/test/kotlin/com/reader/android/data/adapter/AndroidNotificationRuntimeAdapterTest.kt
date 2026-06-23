package com.reader.android.data.adapter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class AndroidNotificationRuntimeAdapterTest {

    private val manifestSource: String by lazy {
        String(Files.readAllBytes(Paths.get("src/main/AndroidManifest.xml")))
    }

    private val adapterSource: String by lazy {
        String(Files.readAllBytes(Paths.get("src/main/kotlin/com/reader/android/data/adapter/AndroidNotificationRuntimeAdapter.kt")))
    }

    @Test
    fun `notification channels cover Android host background capabilities`() {
        val purposes = ReaderNotificationRuntimeContract.channelSpecs.flatMap { it.purposes }.toSet()

        assertEquals(ReaderNotificationPurpose.entries.toSet(), purposes)
        assertEquals(
            ReaderNotificationRuntimeContract.DOWNLOAD_CHANNEL_ID,
            ReaderNotificationRuntimeContract.channelFor(ReaderNotificationPurpose.DOWNLOAD).id
        )
        assertEquals(
            ReaderNotificationRuntimeContract.PLAYBACK_CHANNEL_ID,
            ReaderNotificationRuntimeContract.channelFor(ReaderNotificationPurpose.TTS).id
        )
        assertEquals(
            ReaderNotificationRuntimeContract.SYNC_CHANNEL_ID,
            ReaderNotificationRuntimeContract.channelFor(ReaderNotificationPurpose.WEBDAV_SYNC).id
        )
    }

    @Test
    fun `manifest declares Android thirteen notification permission`() {
        assertTrue("Manifest must declare POST_NOTIFICATIONS", "android.permission.POST_NOTIFICATIONS" in manifestSource)
    }

    @Test
    fun `adapter creates platform channels and checks runtime permission`() {
        listOf(
            "NotificationChannel(",
            "createNotificationChannel",
            "Manifest.permission.POST_NOTIFICATIONS",
            "ContextCompat.checkSelfPermission",
            "NotificationCompat.Builder"
        ).forEach { token ->
            assertTrue("Notification adapter must contain $token", token in adapterSource)
        }
    }

    @Test
    fun `notification evidence stays redacted`() {
        val evidence = AndroidNotificationRuntimeEvidence(
            channelIds = ReaderNotificationRuntimeContract.channelSpecs.map { it.id },
            permissionState = AndroidNotificationPermissionState.GRANTED_BY_RUNTIME_PERMISSION
        )

        assertTrue(evidence.mayRunForegroundWork)
        assertFalse(evidence.rawContentExported)
    }
}
