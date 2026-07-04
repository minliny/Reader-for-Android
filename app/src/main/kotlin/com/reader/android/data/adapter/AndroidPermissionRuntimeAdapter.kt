package com.reader.android.data.adapter

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.reader.ui.shell.PermissionKind
import com.reader.ui.shell.PermissionStatus

/**
 * P3: Real Android implementation of [PermissionRuntimeAdapter].
 *
 * Maps each [PermissionKind] to the platform primitive that reads its current
 * state:
 *
 * - [PermissionKind.NOTIFICATIONS]: Android 13+ checks
 *   `Manifest.permission.POST_NOTIFICATIONS` via [ContextCompat.checkSelfPermission];
 *   on earlier API levels notifications are granted by the platform (no runtime
 *   permission exists).
 * - [PermissionKind.FILE_ACCESS]: Pre-API 33 checks
 *   `Manifest.permission.READ_EXTERNAL_STORAGE`; API 33+ checks the media-read
 *   family (`READ_MEDIA_IMAGES/VIDEO/AUDIO`) and the optional "all files
 *   access" grant (`Environment.isExternalStorageManager()`). For book files
 *   imported via the Storage Access Framework no runtime permission is needed,
 *   so this status is informational rather than gating.
 * - [PermissionKind.BATTERY_OPTIMIZATION]: Uses
 *   [PowerManager.isIgnoringBatteryOptimizations] for the calling package.
 *
 * The adapter only reads state. The actual request flow is launched by the UI
 * layer via [runtimePermissionString] (passed to
 * `ActivityResultContracts.RequestPermission`) or [settingsIntent] (passed to
 * `ActivityResultContracts.StartActivityForResult`). Tests inject
 * [FakePermissionRuntimeAdapter].
 */
class AndroidPermissionRuntimeAdapter(
    private val context: Context
) : PermissionRuntimeAdapter {

    override fun query(kind: PermissionKind): PermissionStatus = when (kind) {
        PermissionKind.NOTIFICATIONS -> queryNotifications()
        PermissionKind.FILE_ACCESS -> queryFileAccess()
        PermissionKind.BATTERY_OPTIMIZATION -> queryBatteryOptimization()
    }

    private fun queryNotifications(): PermissionStatus {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            // Pre-API 33: no runtime permission exists; platform grants notifications.
            return PermissionStatus.GRANTED
        }
        return if (isPermissionGranted(Manifest.permission.POST_NOTIFICATIONS)) {
            PermissionStatus.GRANTED
        } else {
            PermissionStatus.DENIED
        }
    }

    private fun queryFileAccess(): PermissionStatus {
        // API 33+: READ_EXTERNAL_STORAGE is deprecated; media-read family replaces it.
        // For book files (epub/txt/pdf) the Storage Access Framework is the primary
        // path and does not require a runtime permission.
        val mediaReadGranted = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> listOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO
            ).any { isPermissionGranted(it) }
            else -> isPermissionGranted(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        val allFilesGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            android.os.Environment.isExternalStorageManager()
        } else false
        return if (mediaReadGranted || allFilesGranted) PermissionStatus.GRANTED
               else PermissionStatus.DENIED
    }

    private fun queryBatteryOptimization(): PermissionStatus {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            ?: return PermissionStatus.UNKNOWN
        return if (pm.isIgnoringBatteryOptimizations(context.packageName)) {
            PermissionStatus.GRANTED
        } else {
            PermissionStatus.DENIED
        }
    }

    private fun isPermissionGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /**
     * Returns the manifest permission string the UI layer should pass to
     * `ActivityResultContracts.RequestPermission` for [kind], or `null` if
     * the kind uses a Settings-based intent instead (battery optimization,
     * all-files-access on API 30+, or no permission required on the current
     * API level).
     */
    fun runtimePermissionString(kind: PermissionKind): String? = when (kind) {
        PermissionKind.NOTIFICATIONS ->
            Manifest.permission.POST_NOTIFICATIONS
                .takeIf { Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU }
        PermissionKind.FILE_ACCESS ->
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> null
                else -> Manifest.permission.READ_EXTERNAL_STORAGE
            }
        PermissionKind.BATTERY_OPTIMIZATION -> null
    }

    /**
     * Returns the [Intent] the UI layer should launch via
     * `ActivityResultContracts.StartActivityForResult` for kinds that cannot
     * be requested through `RequestPermission`. Returns `null` if the kind
     * uses the runtime-permission flow instead.
     */
    fun settingsIntent(kind: PermissionKind): Intent? = when (kind) {
        PermissionKind.NOTIFICATIONS -> null
        PermissionKind.FILE_ACCESS ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
            } else null
        PermissionKind.BATTERY_OPTIMIZATION ->
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
    }
}
