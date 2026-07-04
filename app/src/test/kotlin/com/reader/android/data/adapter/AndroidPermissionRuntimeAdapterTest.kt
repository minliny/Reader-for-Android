package com.reader.android.data.adapter

import com.reader.ui.shell.PermissionKind
import com.reader.ui.shell.PermissionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class AndroidPermissionRuntimeAdapterTest {

    private val manifestSource: String by lazy {
        String(Files.readAllBytes(Paths.get("src/main/AndroidManifest.xml")))
    }

    private val adapterSource: String by lazy {
        String(
            Files.readAllBytes(
                Paths.get("src/main/kotlin/com/reader/android/data/adapter/AndroidPermissionRuntimeAdapter.kt")
            )
        )
    }

    @Test
    fun `manifest declares notification permission for android thirteen plus`() {
        // The notification permission is the only runtime permission we currently
        // gate on (battery-optimization uses a Settings intent; file access is
        // optional since SAF does not require it).
        assertTrue(
            "Manifest must declare POST_NOTIFICATIONS",
            "android.permission.POST_NOTIFICATIONS" in manifestSource
        )
    }

    @Test
    fun `adapter covers all permission kinds via real platform primitives`() {
        // The adapter must use real Android APIs (not fakes) so the UI's
        // permission badges reflect actual device state.
        listOf(
            "ContextCompat.checkSelfPermission",
            "Manifest.permission.POST_NOTIFICATIONS",
            "PowerManager",
            "isIgnoringBatteryOptimizations",
            "android.os.Environment.isExternalStorageManager"
        ).forEach { token ->
            assertTrue(
                "AndroidPermissionRuntimeAdapter must use $token",
                token in adapterSource
            )
        }
    }

    @Test
    fun `adapter exposes ActivityResultContracts plumbing for ui layer`() {
        // The UI layer launches the request flow via ActivityResultContracts:
        //   - RequestPermission for runtime-permission kinds (notifications,
        //     pre-API-33 file access)
        //   - StartActivityForResult for Settings-based kinds (battery
        //     optimization, all-files-access)
        listOf(
            "runtimePermissionString",
            "settingsIntent",
            "ActivityResultContracts"
        ).forEach { token ->
            assertTrue(
                "AndroidPermissionRuntimeAdapter must expose $token for UI plumbing",
                token in adapterSource
            )
        }
    }

    @Test
    fun `adapter implements PermissionRuntimeAdapter contract`() {
        assertTrue(
            "AndroidPermissionRuntimeAdapter must implement PermissionRuntimeAdapter",
            ": PermissionRuntimeAdapter" in adapterSource
        )
    }

    @Test
    fun `fake adapter returns unknown for unset kinds by default`() {
        val fake = FakePermissionRuntimeAdapter()
        PermissionKind.entries.forEach { kind ->
            assertEquals(
                "Default fake should return UNKNOWN for $kind",
                PermissionStatus.UNKNOWN,
                fake.query(kind)
            )
        }
    }

    @Test
    fun `fake adapter returns injected status per kind`() {
        val fake = FakePermissionRuntimeAdapter(
            mapOf(
                PermissionKind.NOTIFICATIONS to PermissionStatus.GRANTED,
                PermissionKind.FILE_ACCESS to PermissionStatus.DENIED,
                PermissionKind.BATTERY_OPTIMIZATION to PermissionStatus.GRANTED
            )
        )
        assertEquals(PermissionStatus.GRANTED, fake.query(PermissionKind.NOTIFICATIONS))
        assertEquals(PermissionStatus.DENIED, fake.query(PermissionKind.FILE_ACCESS))
        assertEquals(PermissionStatus.GRANTED, fake.query(PermissionKind.BATTERY_OPTIMIZATION))
    }

    @Test
    fun `fake adapter returns unknown for unspecified kind`() {
        val fake = FakePermissionRuntimeAdapter(
            mapOf(PermissionKind.NOTIFICATIONS to PermissionStatus.GRANTED)
        )
        assertEquals(PermissionStatus.GRANTED, fake.query(PermissionKind.NOTIFICATIONS))
        assertEquals(PermissionStatus.UNKNOWN, fake.query(PermissionKind.FILE_ACCESS))
        assertEquals(PermissionStatus.UNKNOWN, fake.query(PermissionKind.BATTERY_OPTIMIZATION))
    }

    @Test
    fun `permission state covers all three kinds in reducer contract`() {
        // The reducer's PermissionState must enumerate exactly the three kinds
        // the adapter knows about — no drift between layers.
        assertEquals(3, PermissionKind.entries.size)
        assertTrue(PermissionKind.NOTIFICATIONS in PermissionKind.entries)
        assertTrue(PermissionKind.FILE_ACCESS in PermissionKind.entries)
        assertTrue(PermissionKind.BATTERY_OPTIMIZATION in PermissionKind.entries)
    }
}
