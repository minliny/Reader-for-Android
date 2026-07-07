package com.reader.host

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.content.ContextCompat
import com.reader.android.data.adapter.AndroidNotificationRuntimeAdapter
import com.reader.android.data.adapter.AndroidPermissionRuntimeAdapter
import com.reader.android.data.adapter.AndroidTtsAdapter
import com.reader.android.data.adapter.AndroidWebDavClient
import com.reader.android.data.adapter.DownloadCacheManager
import com.reader.android.data.adapter.PermissionRuntimeAdapter
import com.reader.android.data.adapter.ReaderForegroundNotificationRequest
import com.reader.android.data.adapter.ReaderNotificationPurpose
import com.reader.android.data.adapter.TtsPlaybackState
import com.reader.android.data.adapter.TtsUtterance
import com.reader.android.data.adapter.WebDavCredentialStore
import com.reader.ui.shell.PermissionKind
import com.reader.ui.shell.PermissionStatus
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

/**
 * Slice C — HostFacade: single aggregation point for all host-owned
 * adapters that the UI/Reducer needs to reach via [HostRequest].
 *
 * **Why a facade**: before Slice C, TTS / Permission / Notification / WebDAV
 * / Credential / Download adapters were scattered across `data/adapter/`
 * and wired independently. The UI Reducer had no single entry point to
 * dispatch `tts.system.*`, `permission.*`, `notification.*`, `share`,
 * `clipboard`, `device`, `background` requests — each caller had to know
 * which adapter to reach and how to serialize its result.
 *
 * HostFacade fixes this: it holds one reference to each adapter and exposes
 * [registerHandlers] so [com.reader.api.ReaderCoreClient] can register all
 * UI-facing capability handlers in one call. The facade itself is a thin
 * router — it does not own lifecycle or threading; each handler blocks on
 * `runBlocking` only when the underlying adapter is suspend (TTS).
 *
 * **Testability**: the facade accepts interface-typed adapters so JVM tests
 * inject fakes (e.g. [FakeAndroidTtsAdapter],
 * [com.reader.android.data.adapter.FakePermissionRuntimeAdapter]). Real
 * adapters are wired in [com.reader.android.AppProvider].
 */
class HostFacade(
    val context: Context?,
    val tts: AndroidTtsAdapter,
    val permission: PermissionRuntimeAdapter,
    val notification: AndroidNotificationRuntimeAdapter?,
    val webDav: AndroidWebDavClient?,
    val credentials: WebDavCredentialStore,
    val downloadCache: DownloadCacheManager?,
    val backgroundRegistry: BackgroundTaskRegistry = BackgroundTaskRegistry()
) {
    /**
     * Registers all UI-facing capability handlers onto [runtime]. Each
     * handler is a thin adapter that parses the [HostRequest] params JSON,
     * calls the facade, and returns a [HostReply].
     *
     * Returns [runtime] for chaining.
     */
    fun registerHandlers(runtime: HostRuntime): HostRuntime = runtime
        .register(TtsSystemStartHandler.CAPABILITY, TtsSystemStartHandler(this))
        .register(TtsSystemStopHandler.CAPABILITY, TtsSystemStopHandler(this))
        .register(TtsSystemPauseHandler.CAPABILITY, TtsSystemPauseHandler(this))
        .register(TtsSystemResumeHandler.CAPABILITY, TtsSystemResumeHandler(this))
        .register(TtsSystemStatusHandler.CAPABILITY, TtsSystemStatusHandler(this))
        .register(PermissionCheckHandler.CAPABILITY, PermissionCheckHandler(this))
        .register(PermissionRequestHandler.CAPABILITY, PermissionRequestHandler(this))
        .register(PermissionOpenSettingsHandler.CAPABILITY, PermissionOpenSettingsHandler(this))
        .register(NotificationShowHandler.CAPABILITY, NotificationShowHandler(this))
        .register(NotificationEnsureChannelsHandler.CAPABILITY, NotificationEnsureChannelsHandler(this))
        .register(NotificationCancelHandler.CAPABILITY, NotificationCancelHandler(this))
        .register(ShareInvokeHandler.CAPABILITY, ShareInvokeHandler(this))
        .register(ClipboardCopyHandler.CAPABILITY, ClipboardCopyHandler(this))
        .register(ClipboardPasteHandler.CAPABILITY, ClipboardPasteHandler(this))
        .register(DeviceVibrateHandler.CAPABILITY, DeviceVibrateHandler(this))
        .register(DeviceScreenKeepOnHandler.CAPABILITY, DeviceScreenKeepOnHandler(this))
        .register(DeviceScreenReleaseHandler.CAPABILITY, DeviceScreenReleaseHandler(this))
        .register(BackgroundScheduleHandler.CAPABILITY, BackgroundScheduleHandler(this))
        .register(BackgroundCancelHandler.CAPABILITY, BackgroundCancelHandler(this))
        .register(CredentialGetHandler.CAPABILITY, CredentialGetHandler(this))
        .register(CredentialSetHandler.CAPABILITY, CredentialSetHandler(this))
        .register(CredentialDeleteHandler.CAPABILITY, CredentialDeleteHandler(this))
        .register(StoragePathHandler.CAPABILITY, StoragePathHandler(this))
}

// ════════════════════════════════════════════════════════════════════════════
// TTS handlers
// ════════════════════════════════════════════════════════════════════════════

class TtsSystemStartHandler(private val facade: HostFacade) : CapabilityHandler {
    override fun handle(request: HostRequest): HostReply {
        val params = try { JSONObject(request.paramsJson()) } catch (e: Exception) {
            return HostReply.error(INTERNAL, "invalid params: ${e.message}", false)
        }
        val text = params.optString("text", "")
        if (text.isEmpty()) return HostReply.error(INTERNAL, "text required", false)
        val utteranceId = params.optString("utteranceId", "")
        try {
            runBlocking {
                facade.tts.init()
                facade.tts.speak(TtsUtterance(text = text, utteranceId = utteranceId))
            }
        } catch (e: Exception) {
            return HostReply.error(INTERNAL, "tts.start failed: ${e.message}", true)
        }
        return HostReply.complete(JSONObject().put("started", true).toString())
    }
    companion object { const val CAPABILITY = "tts.system.start"; private const val INTERNAL = "INTERNAL" }
}

class TtsSystemStopHandler(private val facade: HostFacade) : CapabilityHandler {
    override fun handle(request: HostRequest): HostReply {
        try { runBlocking { facade.tts.stop() } } catch (e: Exception) {
            return HostReply.error(INTERNAL, "tts.stop failed: ${e.message}", true)
        }
        return HostReply.complete(JSONObject().put("stopped", true).toString())
    }
    companion object { const val CAPABILITY = "tts.system.stop"; private const val INTERNAL = "INTERNAL" }
}

class TtsSystemPauseHandler(private val facade: HostFacade) : CapabilityHandler {
    override fun handle(request: HostRequest): HostReply {
        try { runBlocking { facade.tts.pause() } } catch (e: Exception) {
            return HostReply.error(INTERNAL, "tts.pause failed: ${e.message}", true)
        }
        return HostReply.complete(JSONObject().put("paused", true).toString())
    }
    companion object { const val CAPABILITY = "tts.system.pause"; private const val INTERNAL = "INTERNAL" }
}

class TtsSystemResumeHandler(private val facade: HostFacade) : CapabilityHandler {
    override fun handle(request: HostRequest): HostReply {
        try { runBlocking { facade.tts.resume() } } catch (e: Exception) {
            return HostReply.error(INTERNAL, "tts.resume failed: ${e.message}", true)
        }
        return HostReply.complete(JSONObject().put("resumed", true).toString())
    }
    companion object { const val CAPABILITY = "tts.system.resume"; private const val INTERNAL = "INTERNAL" }
}

class TtsSystemStatusHandler(private val facade: HostFacade) : CapabilityHandler {
    override fun handle(request: HostRequest): HostReply {
        val result = JSONObject()
        result.put("available", facade.tts.isAvailable())
        result.put("state", facade.tts.getState().name)
        return HostReply.complete(result.toString())
    }
    companion object { const val CAPABILITY = "tts.system.status" }
}

// ════════════════════════════════════════════════════════════════════════════
// Permission handlers
// ════════════════════════════════════════════════════════════════════════════

class PermissionCheckHandler(private val facade: HostFacade) : CapabilityHandler {
    override fun handle(request: HostRequest): HostReply {
        val params = try { JSONObject(request.paramsJson()) } catch (e: Exception) {
            return HostReply.error(INTERNAL, "invalid params: ${e.message}", false)
        }
        val kindStr = params.optString("kind", "")
        val kind = parsePermissionKind(kindStr)
            ?: return HostReply.error(INTERNAL, "unknown permission kind: $kindStr", false)
        val status = facade.permission.query(kind)
        val result = JSONObject()
        result.put("kind", kindStr)
        result.put("status", status.name)
        return HostReply.complete(result.toString())
    }
    companion object { const val CAPABILITY = "permission.check"; private const val INTERNAL = "INTERNAL" }
}

class PermissionRequestHandler(private val facade: HostFacade) : CapabilityHandler {
    override fun handle(request: HostRequest): HostReply {
        val params = try { JSONObject(request.paramsJson()) } catch (e: Exception) {
            return HostReply.error(INTERNAL, "invalid params: ${e.message}", false)
        }
        val kindStr = params.optString("kind", "")
        val kind = parsePermissionKind(kindStr)
            ?: return HostReply.error(INTERNAL, "unknown permission kind: $kindStr", false)
        // The adapter only reads state; the actual request flow is launched
        // by the UI layer via ActivityResultContracts. Here we return the
        // current status so the Reducer can decide whether to prompt.
        val status = facade.permission.query(kind)
        val result = JSONObject()
        result.put("kind", kindStr)
        result.put("status", status.name)
        result.put("requiresUiPrompt", status == PermissionStatus.DENIED)
        return HostReply.complete(result.toString())
    }
    companion object { const val CAPABILITY = "permission.request"; private const val INTERNAL = "INTERNAL" }
}

class PermissionOpenSettingsHandler(private val facade: HostFacade) : CapabilityHandler {
    override fun handle(request: HostRequest): HostReply {
        // The UI layer launches the settings Intent via ActivityResultContracts;
        // here we just acknowledge the request. The actual Intent is built by
        // AndroidPermissionRuntimeAdapter.settingsIntent(kind).
        val params = try { JSONObject(request.paramsJson()) } catch (e: Exception) {
            return HostReply.error(INTERNAL, "invalid params: ${e.message}", false)
        }
        val kindStr = params.optString("kind", "")
        val result = JSONObject()
        result.put("opened", true)
        result.put("kind", kindStr)
        return HostReply.complete(result.toString())
    }
    companion object { const val CAPABILITY = "permission.open-settings"; private const val INTERNAL = "INTERNAL" }
}

internal fun parsePermissionKind(kind: String): PermissionKind? = when (kind.lowercase()) {
    "notifications" -> PermissionKind.NOTIFICATIONS
    "file_access", "file-access" -> PermissionKind.FILE_ACCESS
    "battery_optimization", "battery-optimization" -> PermissionKind.BATTERY_OPTIMIZATION
    else -> null
}

// ════════════════════════════════════════════════════════════════════════════
// Notification handlers
// ════════════════════════════════════════════════════════════════════════════

class NotificationShowHandler(private val facade: HostFacade) : CapabilityHandler {
    override fun handle(request: HostRequest): HostReply {
        val adapter = facade.notification
            ?: return HostReply.error(INTERNAL, "notification adapter not wired", false)
        val params = try { JSONObject(request.paramsJson()) } catch (e: Exception) {
            return HostReply.error(INTERNAL, "invalid params: ${e.message}", false)
        }
        val purposeStr = params.optString("purpose", "DOWNLOAD")
        val purpose = runCatching { ReaderNotificationPurpose.valueOf(purposeStr) }
            .getOrDefault(ReaderNotificationPurpose.DOWNLOAD)
        val title = params.optString("title", "")
        val message = params.optString("message", "")
        val progressPercent = if (params.has("progressPercent")) params.optInt("progressPercent") else null
        try {
            adapter.buildForegroundNotification(
                ReaderForegroundNotificationRequest(
                    purpose = purpose,
                    title = title,
                    message = message,
                    progressPercent = progressPercent
                )
            )
        } catch (e: Exception) {
            return HostReply.error(INTERNAL, "notification.show failed: ${e.message}", true)
        }
        val result = JSONObject()
        result.put("shown", true)
        result.put("purpose", purpose.name)
        return HostReply.complete(result.toString())
    }
    companion object { const val CAPABILITY = "notification.show"; private const val INTERNAL = "INTERNAL" }
}

class NotificationEnsureChannelsHandler(private val facade: HostFacade) : CapabilityHandler {
    override fun handle(request: HostRequest): HostReply {
        val adapter = facade.notification
            ?: return HostReply.error(INTERNAL, "notification adapter not wired", false)
        val evidence = try {
            adapter.ensureChannels()
        } catch (e: Exception) {
            return HostReply.error(INTERNAL, "ensure-channels failed: ${e.message}", true)
        }
        val channels = JSONArray()
        evidence.channelIds.forEach { channels.put(it) }
        val result = JSONObject()
        result.put("channels", channels)
        result.put("permissionState", evidence.permissionState.name)
        result.put("mayRunForegroundWork", evidence.mayRunForegroundWork)
        return HostReply.complete(result.toString())
    }
    companion object { const val CAPABILITY = "notification.ensure-channels"; private const val INTERNAL = "INTERNAL" }
}

// ════════════════════════════════════════════════════════════════════════════
// Share / Clipboard handlers
// ════════════════════════════════════════════════════════════════════════════

class ShareInvokeHandler(private val facade: HostFacade) : CapabilityHandler {
    override fun handle(request: HostRequest): HostReply {
        val params = try { JSONObject(request.paramsJson()) } catch (e: Exception) {
            return HostReply.error(INTERNAL, "invalid params: ${e.message}", false)
        }
        val text = params.optString("text", "")
        if (text.isEmpty()) return HostReply.error(INTERNAL, "text required", false)
        // The UI layer builds and launches the share Intent; here we
        // acknowledge the request and echo the mimeType so the Reducer can
        // track the intent. Building an actual Intent requires the Android
        // runtime and is deferred to the UI layer.
        val mimeType = params.optString("mimeType", "text/plain")
        val result = JSONObject()
        result.put("shared", true)
        result.put("mimeType", mimeType)
        return HostReply.complete(result.toString())
    }
    companion object { const val CAPABILITY = "share.invoke"; private const val INTERNAL = "INTERNAL" }
}

class ClipboardCopyHandler(private val facade: HostFacade) : CapabilityHandler {
    override fun handle(request: HostRequest): HostReply {
        val ctx = facade.context
            ?: return HostReply.error(INTERNAL, "context not wired (JVM test)", false)
        val params = try { JSONObject(request.paramsJson()) } catch (e: Exception) {
            return HostReply.error(INTERNAL, "invalid params: ${e.message}", false)
        }
        val text = params.optString("text", "")
        if (text.isEmpty()) return HostReply.error(INTERNAL, "text required", false)
        try {
            val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("reader", text))
        } catch (e: Exception) {
            return HostReply.error(INTERNAL, "clipboard.copy failed: ${e.message}", true)
        }
        return HostReply.complete(JSONObject().put("copied", true).toString())
    }
    companion object { const val CAPABILITY = "clipboard.copy"; private const val INTERNAL = "INTERNAL" }
}

class ClipboardPasteHandler(private val facade: HostFacade) : CapabilityHandler {
    override fun handle(request: HostRequest): HostReply {
        val ctx = facade.context
            ?: return HostReply.error("INTERNAL", "context not wired (JVM test)", false)
        val text: String? = try {
            val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.primaryClip?.getItemAt(0)?.text?.toString()
        } catch (e: Exception) { null }
        val result = JSONObject()
        result.put("text", text ?: JSONObject.NULL)
        return HostReply.complete(result.toString())
    }
    companion object { const val CAPABILITY = "clipboard.paste" }
}

// ════════════════════════════════════════════════════════════════════════════
// Device handlers
// ════════════════════════════════════════════════════════════════════════════

class DeviceVibrateHandler(private val facade: HostFacade) : CapabilityHandler {
    override fun handle(request: HostRequest): HostReply {
        val ctx = facade.context
            ?: return HostReply.error(INTERNAL, "context not wired (JVM test)", false)
        val params = try { JSONObject(request.paramsJson()) } catch (e: Exception) {
            return HostReply.error(INTERNAL, "invalid params: ${e.message}", false)
        }
        val durationMillis = params.optLong("durationMillis", 50L)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator?.vibrate(VibrationEffect.createOneShot(durationMillis, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                vibrator?.vibrate(VibrationEffect.createOneShot(durationMillis, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        } catch (e: Exception) {
            return HostReply.error(INTERNAL, "device.vibrate failed: ${e.message}", true)
        }
        return HostReply.complete(JSONObject().put("vibrated", true).toString())
    }
    companion object { const val CAPABILITY = "device.vibrate"; private const val INTERNAL = "INTERNAL" }
}

class DeviceScreenKeepOnHandler(private val facade: HostFacade) : CapabilityHandler {
    override fun handle(request: HostRequest): HostReply {
        val params = try { JSONObject(request.paramsJson()) } catch (e: Exception) {
            return HostReply.error(INTERNAL, "invalid params: ${e.message}", false)
        }
        val keepOn = params.optBoolean("keepOn", true)
        // The UI layer applies FLAG_KEEP_SCREEN_ON on the window; here we
        // acknowledge the request so the Reducer can track the intent.
        val result = JSONObject()
        result.put("applied", true)
        result.put("keepOn", keepOn)
        return HostReply.complete(result.toString())
    }
    companion object { const val CAPABILITY = "device.screen.keep-on"; private const val INTERNAL = "INTERNAL" }
}

/**
 * `device.screen.release` capability handler — counterpart to
 * [DeviceScreenKeepOnHandler]. The UI layer clears `FLAG_KEEP_SCREEN_ON`
 * from the window; here we acknowledge so the Reducer can track the state
 * transition.
 */
class DeviceScreenReleaseHandler(private val facade: HostFacade) : CapabilityHandler {
    override fun handle(request: HostRequest): HostReply {
        val result = JSONObject()
        result.put("released", true)
        return HostReply.complete(result.toString())
    }
    companion object { const val CAPABILITY = "device.screen.release" }
}

// ════════════════════════════════════════════════════════════════════════════
// Notification cancel handler
// ════════════════════════════════════════════════════════════════════════════

/**
 * `notification.cancel` capability handler. Core sends `{notificationId?}`;
 * this handler cancels a previously-posted notification via
 * [android.app.NotificationManager.cancel] (or `cancelAll` when no id).
 */
class NotificationCancelHandler(private val facade: HostFacade) : CapabilityHandler {
    override fun handle(request: HostRequest): HostReply {
        val ctx = facade.context
            ?: return HostReply.error(INTERNAL, "context not wired (JVM test)", false)
        val params = try { JSONObject(request.paramsJson()) } catch (e: Exception) {
            return HostReply.error(INTERNAL, "invalid params: ${e.message}", false)
        }
        try {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            if (params.has("notificationId") && !params.isNull("notificationId")) {
                nm.cancel(params.optInt("notificationId"))
            } else {
                nm.cancelAll()
            }
        } catch (e: Exception) {
            return HostReply.error(INTERNAL, "notification.cancel failed: ${e.message}", true)
        }
        val result = JSONObject()
        result.put("cancelled", true)
        return HostReply.complete(result.toString())
    }
    companion object { const val CAPABILITY = "notification.cancel"; private const val INTERNAL = "INTERNAL" }
}

// ════════════════════════════════════════════════════════════════════════════
// Background handlers (facade bridge — delegate to BackgroundCapabilityHandlers)
// ════════════════════════════════════════════════════════════════════════════

/**
 * Facade-bridge for [BackgroundScheduleHandler]. Delegates to the facade's
 * [BackgroundTaskRegistry].
 */
class BackgroundScheduleHandler(private val facade: HostFacade) : CapabilityHandler {
    override fun handle(request: HostRequest): HostReply {
        val params = try { JSONObject(request.paramsJson()) } catch (e: Exception) {
            return HostReply.error(INTERNAL, "invalid $CAPABILITY params: ${e.message}", false)
        }
        val taskTag = params.optString("taskTag", "")
        if (taskTag.isEmpty()) {
            return HostReply.error(INTERNAL, "$CAPABILITY requires taskTag", false)
        }
        val kind = params.optString("kind", "download")
        facade.backgroundRegistry.schedule(taskTag, kind)
        val result = JSONObject()
        result.put("scheduled", true)
        result.put("taskTag", taskTag)
        result.put("kind", kind)
        return HostReply.complete(result.toString())
    }
    companion object { const val CAPABILITY = "background.schedule"; private const val INTERNAL = "INTERNAL" }
}

class BackgroundCancelHandler(private val facade: HostFacade) : CapabilityHandler {
    override fun handle(request: HostRequest): HostReply {
        val params = try { JSONObject(request.paramsJson()) } catch (e: Exception) {
            return HostReply.error(INTERNAL, "invalid $CAPABILITY params: ${e.message}", false)
        }
        val taskTag = params.optString("taskTag", "")
        if (taskTag.isEmpty()) {
            return HostReply.error(INTERNAL, "$CAPABILITY requires taskTag", false)
        }
        val cancelled = facade.backgroundRegistry.cancel(taskTag)
        val result = JSONObject()
        result.put("cancelled", cancelled)
        result.put("taskTag", taskTag)
        return HostReply.complete(result.toString())
    }
    companion object { const val CAPABILITY = "background.cancel"; private const val INTERNAL = "INTERNAL" }
}

// ════════════════════════════════════════════════════════════════════════════
// Credential handlers (facade bridge)
// ════════════════════════════════════════════════════════════════════════════

class CredentialGetHandler(private val facade: HostFacade) : CapabilityHandler {
    override fun handle(request: HostRequest): HostReply {
        val params = try { JSONObject(request.paramsJson()) } catch (e: Exception) {
            return HostReply.error(INTERNAL, "invalid $CAPABILITY params: ${e.message}", false)
        }
        val identifier = params.optString("identifier", "")
        if (identifier.isEmpty()) {
            return HostReply.error(INTERNAL, "$CAPABILITY requires identifier", false)
        }
        val credential = facade.credentials.load(identifier)
            ?: return HostReply.error(NOT_FOUND, "credential not found: $identifier", false)
        val result = JSONObject()
        result.put("identifier", identifier)
        result.put("serverUrl", credential.serverUrl)
        result.put("authType", when (credential.auth) {
            is com.reader.android.data.adapter.AuthMethod.Basic -> "basic"
            is com.reader.android.data.adapter.AuthMethod.Digest -> "digest"
            is com.reader.android.data.adapter.AuthMethod.Bearer -> "bearer"
        })
        result.put("hasPassword", credential.auth is com.reader.android.data.adapter.AuthMethod.Basic ||
            credential.auth is com.reader.android.data.adapter.AuthMethod.Digest)
        result.put("hasToken", credential.auth is com.reader.android.data.adapter.AuthMethod.Bearer)
        return HostReply.complete(result.toString())
    }
    companion object { const val CAPABILITY = "credential.get"; private const val INTERNAL = "INTERNAL"; private const val NOT_FOUND = "NOT_FOUND" }
}

class CredentialSetHandler(private val facade: HostFacade) : CapabilityHandler {
    override fun handle(request: HostRequest): HostReply {
        val params = try { JSONObject(request.paramsJson()) } catch (e: Exception) {
            return HostReply.error(INTERNAL, "invalid $CAPABILITY params: ${e.message}", false)
        }
        val identifier = params.optString("identifier", "")
        if (identifier.isEmpty()) {
            return HostReply.error(INTERNAL, "$CAPABILITY requires identifier", false)
        }
        val serverUrl = params.optString("serverUrl", "")
        if (serverUrl.isEmpty()) {
            return HostReply.error(INTERNAL, "$CAPABILITY requires serverUrl", false)
        }
        val authType = params.optString("authType", "")
        val auth: com.reader.android.data.adapter.AuthMethod = when (authType.lowercase()) {
            "basic" -> {
                val u = params.optString("username", "")
                val p = params.optString("password", "")
                if (u.isEmpty() || p.isEmpty()) return HostReply.error(INTERNAL, "basic auth requires username and password", false)
                com.reader.android.data.adapter.AuthMethod.Basic(u, p)
            }
            "digest" -> {
                val u = params.optString("username", "")
                val p = params.optString("password", "")
                if (u.isEmpty() || p.isEmpty()) return HostReply.error(INTERNAL, "digest auth requires username and password", false)
                com.reader.android.data.adapter.AuthMethod.Digest(u, p)
            }
            "bearer" -> {
                val t = params.optString("token", "")
                if (t.isEmpty()) return HostReply.error(INTERNAL, "bearer auth requires token", false)
                com.reader.android.data.adapter.AuthMethod.Bearer(t)
            }
            else -> return HostReply.error(INTERNAL, "unknown authType: $authType", false)
        }
        facade.credentials.save(identifier, com.reader.android.data.adapter.WebDavCredential(serverUrl, auth))
        val result = JSONObject()
        result.put("stored", true)
        result.put("identifier", identifier)
        return HostReply.complete(result.toString())
    }
    companion object { const val CAPABILITY = "credential.set"; private const val INTERNAL = "INTERNAL" }
}

class CredentialDeleteHandler(private val facade: HostFacade) : CapabilityHandler {
    override fun handle(request: HostRequest): HostReply {
        val params = try { JSONObject(request.paramsJson()) } catch (e: Exception) {
            return HostReply.error(INTERNAL, "invalid $CAPABILITY params: ${e.message}", false)
        }
        val identifier = params.optString("identifier", "")
        if (identifier.isEmpty()) {
            return HostReply.error(INTERNAL, "$CAPABILITY requires identifier", false)
        }
        val revoked = facade.credentials.revoke(identifier)
        val result = JSONObject()
        result.put("deleted", revoked)
        result.put("identifier", identifier)
        return HostReply.complete(result.toString())
    }
    companion object { const val CAPABILITY = "credential.delete"; private const val INTERNAL = "INTERNAL" }
}

// ════════════════════════════════════════════════════════════════════════════
// Storage path handler (facade bridge)
// ════════════════════════════════════════════════════════════════════════════

class StoragePathHandler(private val facade: HostFacade) : CapabilityHandler {
    override fun handle(request: HostRequest): HostReply {
        val ctx = facade.context
            ?: return HostReply.error(INTERNAL, "storage.path requires Android context", false)
        val params = try { JSONObject(request.paramsJson()) } catch (e: Exception) {
            return HostReply.error(INTERNAL, "invalid params: ${e.message}", false)
        }
        val kind = params.optString("kind", "")
        val result = JSONObject()
        if (kind.isEmpty()) {
            result.put("files", ctx.filesDir.absolutePath)
            result.put("cache", ctx.cacheDir.absolutePath)
            result.put("externalFiles", ctx.getExternalFilesDir(null)?.absolutePath ?: "")
            result.put("externalCache", ctx.externalCacheDir?.absolutePath ?: "")
        } else {
            val path = when (kind) {
                "files" -> ctx.filesDir.absolutePath
                "cache" -> ctx.cacheDir.absolutePath
                "external_files", "externalFiles" -> ctx.getExternalFilesDir(null)?.absolutePath ?: ""
                "external_cache", "externalCache" -> ctx.externalCacheDir?.absolutePath ?: ""
                else -> return HostReply.error(INTERNAL, "unknown storage kind: $kind", false)
            }
            result.put("kind", kind)
            result.put("path", path)
        }
        return HostReply.complete(result.toString())
    }
    companion object { const val CAPABILITY = "storage.path"; private const val INTERNAL = "INTERNAL" }
}

// ════════════════════════════════════════════════════════════════════════════
// BackgroundTaskRegistry — in-memory registry for scheduled background tasks
// ════════════════════════════════════════════════════════════════════════════

/**
 * In-memory registry of scheduled background tasks. Thread-safe.
 *
 * Production wiring will delegate to WorkManager; here we track the
 * schedule/cancel intent so the Reducer can enforce semantics and the
 * UI layer can observe the queue.
 */
class BackgroundTaskRegistry {
    data class Task(val tag: String, val kind: String, val scheduledAtMillis: Long)

    private val tasks = java.util.concurrent.ConcurrentHashMap<String, Task>()

    fun schedule(tag: String, kind: String) {
        tasks[tag] = Task(tag, kind, System.currentTimeMillis())
    }

    fun cancel(tag: String): Boolean = tasks.remove(tag) != null

    fun list(): List<Task> = tasks.values.toList()

    fun size(): Int = tasks.size
}
