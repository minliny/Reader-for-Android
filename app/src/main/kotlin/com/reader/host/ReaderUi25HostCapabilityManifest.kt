package com.reader.host

import io.reader.ui.contract.HostRequestType

/**
 * Android proof boundary for every Reader UI 2.5 Host request.
 *
 * This is deliberately independent from rollout authority. A capability can
 * be registered and still require a physical-device proof before it may be
 * described as device-proven.
 */
enum class AndroidHostCapabilityProofTier {
    /** Pure handler / injected-executor proof; no Android framework required. */
    JVM,

    /** Android framework behavior is deterministic under Robolectric API 35. */
    ROBOLECTRIC_API35,

    /** Hardware, a user-facing Activity, or OS lifecycle behavior is required. */
    PHYSICAL_DEVICE
}

/**
 * Exhaustive 2.5 HostRequest manifest.
 *
 * [wireName] is an exhaustive `when` over the generated enum. Adding a new
 * contract type therefore fails this source at compile time until Android has
 * explicitly named and tiered it. [entries] must contain exactly the 58
 * contract types; host-private capabilities are intentionally excluded.
 */
object ReaderUi25HostCapabilityManifest {
    data class Entry(
        val type: HostRequestType,
        val capability: String,
        val tier: AndroidHostCapabilityProofTier
    )

    val entries: List<Entry> = HostRequestType.entries.map { type ->
        Entry(type, wireName(type), proofTier(type))
    }

    val capabilities: Set<String> = entries.mapTo(linkedSetOf()) { it.capability }

    init {
        check(entries.size == 58) {
            "Reader UI 2.5 HostRequest manifest must contain exactly 58 types; got ${entries.size}"
        }
        check(capabilities.size == 58) {
            "Reader UI 2.5 HostRequest wire names must be unique; got ${capabilities.size}"
        }
    }

    fun missingFrom(adapter: HostAdapter): Set<String> =
        capabilities.filterNotTo(linkedSetOf()) { adapter.isRegistered(it) }

    fun entriesAt(tier: AndroidHostCapabilityProofTier): List<Entry> =
        entries.filter { it.tier == tier }

    fun wireName(type: HostRequestType): String = when (type) {
        HostRequestType.HttpExecute -> "http.execute"
        HostRequestType.HttpCancel -> "http.cancel"
        HostRequestType.WebviewOpen -> "webview.open"
        HostRequestType.WebviewClose -> "webview.close"
        HostRequestType.WebviewEvaluate -> "webview.evaluate"
        HostRequestType.CookieGet -> "cookie.get"
        HostRequestType.CookieSet -> "cookie.set"
        HostRequestType.CookieClear -> "cookie.clear"
        HostRequestType.FileRead -> "file.read"
        HostRequestType.FileWrite -> "file.write"
        HostRequestType.FileDelete -> "file.delete"
        HostRequestType.StoragePath -> "storage.path"
        HostRequestType.PersistenceGet -> "persistence.get"
        HostRequestType.PersistencePut -> "persistence.put"
        HostRequestType.CredentialGet -> "credential.get"
        HostRequestType.CredentialSet -> "credential.set"
        HostRequestType.CredentialDelete -> "credential.delete"
        HostRequestType.TtsSystemStart -> "tts.system.start"
        HostRequestType.TtsSystemStop -> "tts.system.stop"
        HostRequestType.TtsSystemPause -> "tts.system.pause"
        HostRequestType.TtsSystemResume -> "tts.system.resume"
        HostRequestType.PermissionRequest -> "permission.request"
        HostRequestType.PermissionCheck -> "permission.check"
        HostRequestType.BackgroundSchedule -> "background.schedule"
        HostRequestType.BackgroundCancel -> "background.cancel"
        HostRequestType.TimerForegroundArm -> "timer.foreground.arm"
        HostRequestType.TimerForegroundCancel -> "timer.foreground.cancel"
        HostRequestType.NotificationShow -> "notification.show"
        HostRequestType.NotificationCancel -> "notification.cancel"
        HostRequestType.ShareInvoke -> "share.invoke"
        HostRequestType.ClipboardCopy -> "clipboard.copy"
        HostRequestType.ClipboardPaste -> "clipboard.paste"
        HostRequestType.DeviceVibrate -> "device.vibrate"
        HostRequestType.DeviceScreenKeepOn -> "device.screen.keep-on"
        HostRequestType.DeviceScreenRelease -> "device.screen.release"
        HostRequestType.FileSelect -> "file.select"
        HostRequestType.FontRegisterFile -> "font.registerFile"
        HostRequestType.FontUnregisterFile -> "font.unregisterFile"
        HostRequestType.ClipboardRead -> "clipboard.read"
        HostRequestType.ClipboardWrite -> "clipboard.write"
        HostRequestType.TtsStart -> "tts.start"
        HostRequestType.TtsStop -> "tts.stop"
        HostRequestType.TtsPause -> "tts.pause"
        HostRequestType.BrightnessSet -> "brightness.set"
        HostRequestType.BrightnessGet -> "brightness.get"
        HostRequestType.ScreenKeepAwake -> "screen.keepAwake"
        HostRequestType.ScreenAllowSleep -> "screen.allowSleep"
        HostRequestType.HapticsLight -> "haptics.light"
        HostRequestType.HapticsMedium -> "haptics.medium"
        HostRequestType.HapticsHeavy -> "haptics.heavy"
        HostRequestType.NetworkStatus -> "network.status"
        HostRequestType.WebdavConnect -> "webdav.connect"
        HostRequestType.WebdavBackup -> "webdav.backup"
        HostRequestType.WebdavRestore -> "webdav.restore"
        HostRequestType.ShareText -> "share.text"
        HostRequestType.ShareFile -> "share.file"
        HostRequestType.BackgroundTaskStart -> "background.task.start"
        HostRequestType.BackgroundTaskEnd -> "background.task.end"
    }

    fun proofTier(type: HostRequestType): AndroidHostCapabilityProofTier = when (type) {
        HostRequestType.CookieGet,
        HostRequestType.CookieSet,
        HostRequestType.CookieClear,
        HostRequestType.CredentialGet,
        HostRequestType.CredentialSet,
        HostRequestType.CredentialDelete,
        HostRequestType.TimerForegroundArm,
        HostRequestType.TimerForegroundCancel,
        HostRequestType.PersistenceGet,
        HostRequestType.PersistencePut -> AndroidHostCapabilityProofTier.JVM

        HostRequestType.HttpExecute,
        HostRequestType.HttpCancel,
        HostRequestType.FileRead,
        HostRequestType.FileWrite,
        HostRequestType.FileDelete,
        HostRequestType.StoragePath,
        HostRequestType.PermissionRequest,
        HostRequestType.PermissionCheck,
        HostRequestType.NotificationShow,
        HostRequestType.NotificationCancel,
        HostRequestType.ShareInvoke,
        HostRequestType.ClipboardCopy,
        HostRequestType.ClipboardPaste,
        HostRequestType.FontRegisterFile,
        HostRequestType.FontUnregisterFile,
        HostRequestType.ClipboardRead,
        HostRequestType.ClipboardWrite,
        HostRequestType.NetworkStatus,
        HostRequestType.WebdavConnect,
        HostRequestType.WebdavBackup,
        HostRequestType.WebdavRestore,
        HostRequestType.ShareText,
        HostRequestType.ShareFile -> AndroidHostCapabilityProofTier.ROBOLECTRIC_API35

        HostRequestType.WebviewOpen,
        HostRequestType.WebviewClose,
        HostRequestType.WebviewEvaluate,
        HostRequestType.TtsSystemStart,
        HostRequestType.TtsSystemStop,
        HostRequestType.TtsSystemPause,
        HostRequestType.TtsSystemResume,
        HostRequestType.BackgroundSchedule,
        HostRequestType.BackgroundCancel,
        HostRequestType.DeviceVibrate,
        HostRequestType.DeviceScreenKeepOn,
        HostRequestType.DeviceScreenRelease,
        HostRequestType.FileSelect,
        HostRequestType.TtsStart,
        HostRequestType.TtsStop,
        HostRequestType.TtsPause,
        HostRequestType.BrightnessSet,
        HostRequestType.BrightnessGet,
        HostRequestType.ScreenKeepAwake,
        HostRequestType.ScreenAllowSleep,
        HostRequestType.HapticsLight,
        HostRequestType.HapticsMedium,
        HostRequestType.HapticsHeavy,
        HostRequestType.BackgroundTaskStart,
        HostRequestType.BackgroundTaskEnd -> AndroidHostCapabilityProofTier.PHYSICAL_DEVICE
    }
}
