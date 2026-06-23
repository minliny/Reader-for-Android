package com.reader.android.data.adapter

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

enum class ReaderNotificationPurpose {
    DOWNLOAD,
    TTS,
    WEBDAV_SYNC,
    SOURCE_IMPORT,
    CACHE_MAINTENANCE
}

data class ReaderNotificationChannelSpec(
    val id: String,
    val name: String,
    val description: String,
    val importance: Int,
    val purposes: Set<ReaderNotificationPurpose>
)

data class ReaderForegroundNotificationRequest(
    val purpose: ReaderNotificationPurpose,
    val title: String,
    val message: String,
    val progressPercent: Int? = null,
    val ongoing: Boolean = true
)

enum class AndroidNotificationPermissionState {
    GRANTED_BY_PLATFORM,
    GRANTED_BY_RUNTIME_PERMISSION,
    MISSING_RUNTIME_GRANT
}

data class AndroidNotificationRuntimeEvidence(
    val channelIds: List<String>,
    val permissionState: AndroidNotificationPermissionState,
    val rawContentExported: Boolean = false
) {
    val mayRunForegroundWork: Boolean
        get() = permissionState != AndroidNotificationPermissionState.MISSING_RUNTIME_GRANT
}

object ReaderNotificationRuntimeContract {
    const val DOWNLOAD_CHANNEL_ID = "reader.downloads"
    const val PLAYBACK_CHANNEL_ID = "reader.playback"
    const val SYNC_CHANNEL_ID = "reader.sync"
    const val MAINTENANCE_CHANNEL_ID = "reader.maintenance"

    val channelSpecs: List<ReaderNotificationChannelSpec> = listOf(
        ReaderNotificationChannelSpec(
            id = DOWNLOAD_CHANNEL_ID,
            name = "下载与导入",
            description = "本地书导入、章节缓存和下载进度",
            importance = NotificationManager.IMPORTANCE_LOW,
            purposes = setOf(ReaderNotificationPurpose.DOWNLOAD, ReaderNotificationPurpose.SOURCE_IMPORT)
        ),
        ReaderNotificationChannelSpec(
            id = PLAYBACK_CHANNEL_ID,
            name = "朗读",
            description = "系统 TTS 朗读与播放状态",
            importance = NotificationManager.IMPORTANCE_LOW,
            purposes = setOf(ReaderNotificationPurpose.TTS)
        ),
        ReaderNotificationChannelSpec(
            id = SYNC_CHANNEL_ID,
            name = "同步与备份",
            description = "WebDAV 备份、恢复和阅读进度同步",
            importance = NotificationManager.IMPORTANCE_LOW,
            purposes = setOf(ReaderNotificationPurpose.WEBDAV_SYNC)
        ),
        ReaderNotificationChannelSpec(
            id = MAINTENANCE_CHANNEL_ID,
            name = "缓存维护",
            description = "缓存清理和后台维护任务",
            importance = NotificationManager.IMPORTANCE_MIN,
            purposes = setOf(ReaderNotificationPurpose.CACHE_MAINTENANCE)
        )
    )

    fun channelFor(purpose: ReaderNotificationPurpose): ReaderNotificationChannelSpec =
        channelSpecs.first { purpose in it.purposes }
}

class AndroidNotificationRuntimeAdapter(
    private val context: Context,
    private val notificationManager: NotificationManager =
        context.getSystemService(NotificationManager::class.java)
) {

    fun permissionState(): AndroidNotificationPermissionState {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return AndroidNotificationPermissionState.GRANTED_BY_PLATFORM
        }
        return if (
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            AndroidNotificationPermissionState.GRANTED_BY_RUNTIME_PERMISSION
        } else {
            AndroidNotificationPermissionState.MISSING_RUNTIME_GRANT
        }
    }

    fun ensureChannels(): AndroidNotificationRuntimeEvidence {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ReaderNotificationRuntimeContract.channelSpecs.forEach { spec ->
                val channel = NotificationChannel(spec.id, spec.name, spec.importance).apply {
                    description = spec.description
                }
                notificationManager.createNotificationChannel(channel)
            }
        }
        return AndroidNotificationRuntimeEvidence(
            channelIds = ReaderNotificationRuntimeContract.channelSpecs.map { it.id },
            permissionState = permissionState()
        )
    }

    fun buildForegroundNotification(request: ReaderForegroundNotificationRequest): NotificationCompat.Builder {
        val channel = ReaderNotificationRuntimeContract.channelFor(request.purpose)
        return NotificationCompat.Builder(context, channel.id)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(request.title)
            .setContentText(request.message)
            .setOnlyAlertOnce(true)
            .setOngoing(request.ongoing)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .also { builder ->
                request.progressPercent?.let { percent ->
                    builder.setProgress(100, percent.coerceIn(0, 100), false)
                }
            }
    }
}
