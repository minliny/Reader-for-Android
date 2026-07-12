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
import com.reader.android.data.adapter.TtsChapterRequest
import com.reader.android.data.adapter.TtsPlaybackState
import com.reader.android.data.adapter.TtsProgressUpdate
import com.reader.android.data.adapter.TtsSessionController
import com.reader.android.data.adapter.TtsUtterance
import com.reader.android.data.adapter.WebDavCredentialStore
import com.reader.ui.shell.PermissionKind
import com.reader.ui.shell.PermissionStatus
import com.reader.WebViewHostActivity
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

private const val OPAQUE_CREDENTIAL_SENTINEL = "https://reader.invalid/opaque-credential"

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
    /**
     * P1-4: Session-level TTS orchestrator. When present, `tts.system.*`
     * handlers delegate to this controller (paragraph queue, multi-chapter
     * progression, AudioFocus/BecomingNoisy recovery, progress writeback).
     * When null (JVM test path), handlers fall back to direct engine calls.
     */
    val ttsSessionController: TtsSessionController? = null,
    val backgroundRegistry: BackgroundTaskRegistry = BackgroundTaskRegistry(),
    /** Canonical foreground one-shot timers; never mapped to background.schedule. */
    val foregroundTimerRegistry: ForegroundTimerRegistry = ForegroundTimerRegistry(),
    val uiWebViewSession: UiWebViewSession = UnavailableUiWebViewSession,
    /** Reader UI 2.5 additions; defaults remain registered but fail closed. */
    val readerUi25Services: ReaderUi25HostServices = ReaderUi25HostServices()
) {
    /**
     * Registers all UI-facing capability handlers onto [runtime]. Each
     * handler is a thin adapter that parses the [HostRequest] params JSON,
     * calls the facade, and returns a [HostReply].
     *
     * Returns [runtime] for chaining.
     */
    fun registerHandlers(runtime: HostRuntime): HostRuntime {
        val canonical = runtime
        .register(TtsSystemStartHandler.CAPABILITY, TtsSystemStartHandler(this))
        .register(TtsSystemStopHandler.CAPABILITY, TtsSystemStopHandler(this))
        .register(TtsSystemPauseHandler.CAPABILITY, TtsSystemPauseHandler(this))
        .register(TtsSystemResumeHandler.CAPABILITY, TtsSystemResumeHandler(this))
        .register(TtsSystemStatusHandler.CAPABILITY, TtsSystemStatusHandler(this))
        .register(TtsSystemProgressHandler.CAPABILITY, TtsSystemProgressHandler(this))
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
        .register(
            ForegroundTimerArmHandler.CAPABILITY,
            ForegroundTimerArmHandler(foregroundTimerRegistry)
        )
        .register(
            ForegroundTimerCancelHandler.CAPABILITY,
            ForegroundTimerCancelHandler(foregroundTimerRegistry)
        )
        .register(CredentialGetHandler.CAPABILITY, CredentialGetHandler(this))
        .register(CredentialSetHandler.CAPABILITY, CredentialSetHandler(this))
        .register(CredentialDeleteHandler.CAPABILITY, CredentialDeleteHandler(this))
        .register(StoragePathHandler.CAPABILITY, StoragePathHandler(this))
        .register(WebViewOpenHandler.CAPABILITY, WebViewOpenHandler(this))
        .register(WebViewCloseHandler.CAPABILITY, WebViewCloseHandler(this))
        .register(WebViewEvaluateHandler.CAPABILITY, WebViewEvaluateHandler(this))
        return registerReaderUi25Handlers(canonical)
    }
}

// ════════════════════════════════════════════════════════════════════════════
// TTS handlers — P1-4: delegate to TtsSessionController when present;
// fall back to direct engine calls when null (JVM test path).
// ════════════════════════════════════════════════════════════════════════════

class TtsSystemStartHandler(private val facade: HostFacade) : CapabilityHandler {
    override fun handle(request: HostRequest): HostReply {
        val params = try { JSONObject(request.paramsJson()) } catch (e: Exception) {
            return HostReply.error(INTERNAL, "invalid params: ${e.message}", false)
        }
        val text = params.optString("text", "")
        if (text.isEmpty()) return HostReply.error(INTERNAL, "text required", false)
        val chapterTitle = params.optString("chapterTitle", "")
        val chapterIndex = params.optInt("chapterIndex", 0)
        val language = params.optString("language", "zh-CN")
        val speechRate = params.optDouble("rate", 1.0).toFloat()
        val pitch = params.optDouble("pitch", 1.0).toFloat()
        if (!speechRate.isFinite() || speechRate !in 0.1f..4.0f) {
            return HostReply.error(INTERNAL, "rate must be within 0.1...4.0", false)
        }
        if (!pitch.isFinite() || pitch !in 0.5f..2.0f) {
            return HostReply.error(INTERNAL, "pitch must be within 0.5...2.0", false)
        }
        if (params.optString("voice", "").isNotBlank()) {
            return HostReply.error("NOT_SUPPORTED", "explicit Android TTS voice selection is not wired", false)
        }
        try {
            val controller = facade.ttsSessionController
            if (controller != null) {
                // P1-4: session-level start — paragraph queue + multi-chapter
                // + AudioFocus + BecomingNoisy + progress writeback.
                val started = runBlocking {
                    controller.start(
                        TtsChapterRequest(
                            text = text,
                            title = chapterTitle,
                            index = chapterIndex,
                            language = language,
                            speechRate = speechRate,
                            pitch = pitch
                        )
                    )
                }
                if (!started) return HostReply.error(INTERNAL, "TTS engine init failed", true)
            } else {
                // Legacy direct-speak path (JVM tests without controller).
                val utteranceId = params.optString("correlationId", "")
                    .ifBlank { "host-${request.operationId()}" }
                runBlocking {
                    val initialized = facade.tts.init()
                    if (!initialized.success) {
                        throw IllegalStateException(initialized.errorMessage ?: "TTS engine init failed")
                    }
                    facade.tts.speak(TtsUtterance(
                        text = text,
                        utteranceId = utteranceId,
                        language = language,
                        speechRate = speechRate,
                        pitch = pitch
                    ))
                }
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
        try {
            val controller = facade.ttsSessionController
            if (controller != null) {
                runBlocking { controller.stop() }
            } else {
                runBlocking { facade.tts.stop() }
            }
        } catch (e: Exception) {
            return HostReply.error(INTERNAL, "tts.stop failed: ${e.message}", true)
        }
        return HostReply.complete(JSONObject().put("acknowledged", true).toString())
    }
    companion object { const val CAPABILITY = "tts.system.stop"; private const val INTERNAL = "INTERNAL" }
}

class TtsSystemPauseHandler(private val facade: HostFacade) : CapabilityHandler {
    override fun handle(request: HostRequest): HostReply {
        try {
            val controller = facade.ttsSessionController
            if (controller != null) {
                runBlocking { controller.pause() }
            } else {
                runBlocking { facade.tts.pause() }
            }
        } catch (e: Exception) {
            return HostReply.error(INTERNAL, "tts.pause failed: ${e.message}", true)
        }
        return HostReply.complete(JSONObject().put("acknowledged", true).toString())
    }
    companion object { const val CAPABILITY = "tts.system.pause"; private const val INTERNAL = "INTERNAL" }
}

class TtsSystemResumeHandler(private val facade: HostFacade) : CapabilityHandler {
    override fun handle(request: HostRequest): HostReply {
        try {
            val controller = facade.ttsSessionController
            if (controller != null) {
                runBlocking { controller.resume() }
            } else {
                runBlocking { facade.tts.resume() }
            }
        } catch (e: Exception) {
            return HostReply.error(INTERNAL, "tts.resume failed: ${e.message}", true)
        }
        return HostReply.complete(JSONObject().put("acknowledged", true).toString())
    }
    companion object { const val CAPABILITY = "tts.system.resume"; private const val INTERNAL = "INTERNAL" }
}

class TtsSystemStatusHandler(private val facade: HostFacade) : CapabilityHandler {
    override fun handle(request: HostRequest): HostReply {
        val controller = facade.ttsSessionController
        val result = JSONObject()
        result.put("available", facade.tts.isAvailable())
        result.put("state", facade.tts.getState().name)
        if (controller != null) {
            result.put("started", controller.isStarted())
            result.put("paused", controller.isPaused())
        }
        return HostReply.complete(result.toString())
    }
    companion object { const val CAPABILITY = "tts.system.status" }
}

/**
 * `tts.system.progress` — returns the current TTS playback progress
 * (chapter index, paragraph index, total paragraphs, chapter title).
 * Used by the UI to poll progress when the flow-based writeback is not
 * available (e.g. process restart).
 */
class TtsSystemProgressHandler(private val facade: HostFacade) : CapabilityHandler {
    override fun handle(request: HostRequest): HostReply {
        val controller = facade.ttsSessionController
            ?: return HostReply.error(INTERNAL, "tts session controller not wired", false)
        val progress = controller.getCurrentProgress()
        val result = JSONObject()
        if (progress != null) {
            result.put("chapterIndex", progress.chapterIndex)
            result.put("paragraphIndex", progress.paragraphIndex)
            result.put("totalParagraphs", progress.totalParagraphs)
            result.put("chapterTitle", progress.chapterTitle)
        }
        result.put("started", controller.isStarted())
        result.put("paused", controller.isPaused())
        return HostReply.complete(result.toString())
    }
    companion object { const val CAPABILITY = "tts.system.progress"; private const val INTERNAL = "INTERNAL" }
}

// ════════════════════════════════════════════════════════════════════════════
// Permission handlers
// ════════════════════════════════════════════════════════════════════════════

class PermissionCheckHandler(private val facade: HostFacade) : CapabilityHandler {
    override fun handle(request: HostRequest): HostReply {
        val params = try { JSONObject(request.paramsJson()) } catch (e: Exception) {
            return HostReply.error(INTERNAL, "invalid params: ${e.message}", false)
        }
        val scope = params.optString("scope", "")
        val kind = parsePermissionKind(scope)
            ?: return HostReply.error(INTERNAL, "unknown permission scope: $scope", false)
        val status = facade.permission.query(kind)
        return HostReply.complete(JSONObject().put("granted", status == PermissionStatus.GRANTED).toString())
    }
    companion object { const val CAPABILITY = "permission.check"; private const val INTERNAL = "INTERNAL" }
}

class PermissionRequestHandler(private val facade: HostFacade) : CapabilityHandler {
    override fun handle(request: HostRequest): HostReply {
        val params = try { JSONObject(request.paramsJson()) } catch (e: Exception) {
            return HostReply.error(INTERNAL, "invalid params: ${e.message}", false)
        }
        val scope = params.optString("scope", "")
        val kind = parsePermissionKind(scope)
            ?: return HostReply.error(INTERNAL, "unknown permission scope: $scope", false)
        // This adapter can only query state. It must not acknowledge a request
        // it did not launch; an absent grant therefore fails closed.
        val status = facade.permission.query(kind)
        if (status != PermissionStatus.GRANTED) {
            return HostReply.error(
                "REQUIRES_UI_CONTEXT",
                "permission.request requires the foreground Activity permission launcher for scope=$scope",
                false
            )
        }
        return HostReply.complete(JSONObject().put("granted", true).toString())
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
    "storage", "file_access", "file-access" -> PermissionKind.FILE_ACCESS
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
        val id = params.optString("id", "")
        if (id.isBlank()) return HostReply.error(INTERNAL, "notification.show requires id", false)
        val title = params.optString("title", "")
        val body = params.optString("body", "")
        try {
            adapter.show(
                id,
                ReaderForegroundNotificationRequest(
                    purpose = ReaderNotificationPurpose.DOWNLOAD,
                    title = title,
                    message = body,
                    ongoing = false
                )
            )
        } catch (e: Exception) {
            return HostReply.error(INTERNAL, "notification.show failed: ${e.message}", true)
        }
        val result = JSONObject()
        result.put("shown", true)
        result.put("id", id)
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
        if (params.has("url") || params.has("files")) {
            return HostReply.error("NOT_SUPPORTED", "share.invoke url/files require a typed share surface", false)
        }
        val host = facade.readerUi25Services.activityHostProvider()
            ?: return HostReply.error("REQUIRES_UI_CONTEXT", "share.invoke requires a foreground Activity", false)
        return try {
            if (!host.shareText(text)) {
                HostReply.error(INTERNAL, "share chooser was not launched", true)
            } else {
                HostReply.complete(JSONObject().put("shared", true).toString())
            }
        } catch (e: Exception) {
            HostReply.error(INTERNAL, "share.invoke failed: ${e.message}", true)
        }
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
        result.put("text", text ?: "")
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
        val durationMillis = params.optLong("durationMs", 50L)
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (vibrator == null || !vibrator.hasVibrator()) {
                return HostReply.error("NOT_AVAILABLE", "device has no usable vibrator", false)
            }
            vibrator.vibrate(VibrationEffect.createOneShot(durationMillis, VibrationEffect.DEFAULT_AMPLITUDE))
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
        val enabled = params.optBoolean("enabled", true)
        val host = facade.readerUi25Services.activityHostProvider()
            ?: return HostReply.error("REQUIRES_UI_CONTEXT", "device.screen.keep-on requires a foreground Activity", false)
        return try {
            if (!host.setKeepAwake(enabled)) {
                HostReply.error(INTERNAL, "screen keep-awake flag was not applied", true)
            } else {
                HostReply.complete(JSONObject().put("enabled", enabled).toString())
            }
        } catch (e: Exception) {
            HostReply.error(INTERNAL, "device.screen.keep-on failed: ${e.message}", true)
        }
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
        val host = facade.readerUi25Services.activityHostProvider()
            ?: return HostReply.error("REQUIRES_UI_CONTEXT", "device.screen.release requires a foreground Activity", false)
        return try {
            if (!host.setKeepAwake(false)) {
                HostReply.error("INTERNAL", "screen keep-awake flag was not cleared", true)
            } else {
                HostReply.complete(JSONObject().put("released", true).toString())
            }
        } catch (e: Exception) {
            HostReply.error("INTERNAL", "device.screen.release failed: ${e.message}", true)
        }
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
        val adapter = facade.notification
            ?: return HostReply.error(INTERNAL, "notification adapter not wired", false)
        val params = try { JSONObject(request.paramsJson()) } catch (e: Exception) {
            return HostReply.error(INTERNAL, "invalid params: ${e.message}", false)
        }
        try {
            val id = params.optString("id", "")
            if (id.isBlank()) return HostReply.error(INTERNAL, "notification.cancel requires id", false)
            adapter.cancel(id)
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
        val taskId = params.optString("taskId", "")
        if (taskId.isEmpty()) {
            return HostReply.error(INTERNAL, "$CAPABILITY requires taskId", false)
        }
        val delayMs = params.optLong("delayMs", 0L)
        if (delayMs < 0L) return HostReply.error(INTERNAL, "delayMs must be >= 0", false)
        val host = facade.readerUi25Services.backgroundTasks
            ?: return HostReply.error("NOT_CONFIGURED", "WorkManager background host is not configured", false)
        return try {
            val started = host.schedule(taskId, delayMs)
            facade.backgroundRegistry.schedule(taskId, "workmanager", started.taskId)
            HostReply.complete(JSONObject().put("scheduled", true).toString())
        } catch (e: ReaderUiHostCapabilityFailure) {
            HostReply.error(e.errorCode, e.message ?: "background.schedule failed", e.retryable)
        } catch (e: Exception) {
            HostReply.error(INTERNAL, "background.schedule failed: ${e.message}", true)
        }
    }
    companion object { const val CAPABILITY = "background.schedule"; private const val INTERNAL = "INTERNAL" }
}

class BackgroundCancelHandler(private val facade: HostFacade) : CapabilityHandler {
    override fun handle(request: HostRequest): HostReply {
        val params = try { JSONObject(request.paramsJson()) } catch (e: Exception) {
            return HostReply.error(INTERNAL, "invalid $CAPABILITY params: ${e.message}", false)
        }
        val taskId = params.optString("taskId", "")
        if (taskId.isEmpty()) {
            return HostReply.error(INTERNAL, "$CAPABILITY requires taskId", false)
        }
        val task = facade.backgroundRegistry.find(taskId)
            ?: return HostReply.complete(JSONObject().put("cancelled", false).toString())
        val host = facade.readerUi25Services.backgroundTasks
            ?: return HostReply.error("NOT_CONFIGURED", "WorkManager background host is not configured", false)
        return try {
            val cancelled = host.end(task.hostTaskId ?: taskId)
            if (cancelled) facade.backgroundRegistry.cancel(taskId)
            HostReply.complete(JSONObject().put("cancelled", cancelled).toString())
        } catch (e: ReaderUiHostCapabilityFailure) {
            HostReply.error(e.errorCode, e.message ?: "background.cancel failed", e.retryable)
        } catch (e: Exception) {
            HostReply.error(INTERNAL, "background.cancel failed: ${e.message}", true)
        }
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
        val key = params.optString("key", "")
        if (key.isEmpty()) {
            return HostReply.error(INTERNAL, "$CAPABILITY requires key", false)
        }
        val credential = facade.credentials.load(key)
            ?: return HostReply.complete(JSONObject().put("exists", false).toString())
        val auth = credential.auth
        if (credential.serverUrl != OPAQUE_CREDENTIAL_SENTINEL ||
            auth !is com.reader.android.data.adapter.AuthMethod.Bearer
        ) {
            return HostReply.error("INCOMPATIBLE_CREDENTIAL", "credential $key is a structured WebDAV record", false)
        }
        return HostReply.complete(JSONObject().put("exists", true).put("value", auth.token).toString())
    }
    companion object { const val CAPABILITY = "credential.get"; private const val INTERNAL = "INTERNAL" }
}

class CredentialSetHandler(private val facade: HostFacade) : CapabilityHandler {
    override fun handle(request: HostRequest): HostReply {
        val params = try { JSONObject(request.paramsJson()) } catch (e: Exception) {
            return HostReply.error(INTERNAL, "invalid $CAPABILITY params: ${e.message}", false)
        }
        val key = params.optString("key", "")
        if (key.isEmpty()) {
            return HostReply.error(INTERNAL, "$CAPABILITY requires key", false)
        }
        if (!params.has("value") || params.isNull("value") || params.opt("value") !is String) {
            return HostReply.error(INTERNAL, "$CAPABILITY requires string value", false)
        }
        val value = params.getString("value")
        return try {
            facade.credentials.save(
                key,
                com.reader.android.data.adapter.WebDavCredential(
                    OPAQUE_CREDENTIAL_SENTINEL,
                    com.reader.android.data.adapter.AuthMethod.Bearer(value)
                )
            )
            HostReply.complete(JSONObject().put("stored", true).toString())
        } catch (e: Exception) {
            HostReply.error(INTERNAL, "credential.set failed: ${e.message}", true)
        }
    }
    companion object { const val CAPABILITY = "credential.set"; private const val INTERNAL = "INTERNAL" }
}

class CredentialDeleteHandler(private val facade: HostFacade) : CapabilityHandler {
    override fun handle(request: HostRequest): HostReply {
        val params = try { JSONObject(request.paramsJson()) } catch (e: Exception) {
            return HostReply.error(INTERNAL, "invalid $CAPABILITY params: ${e.message}", false)
        }
        val key = params.optString("key", "")
        if (key.isEmpty()) {
            return HostReply.error(INTERNAL, "$CAPABILITY requires key", false)
        }
        return try {
            HostReply.complete(JSONObject().put("deleted", facade.credentials.revoke(key)).toString())
        } catch (e: Exception) {
            HostReply.error(INTERNAL, "credential.delete failed: ${e.message}", true)
        }
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
        val scope = params.optString("scope", "")
        val path = when (scope) {
            "files" -> ctx.filesDir.absolutePath
            "cache" -> ctx.cacheDir.absolutePath
            "external" -> ctx.getExternalFilesDir(null)?.absolutePath
                ?: return HostReply.error("NOT_AVAILABLE", "external files directory is unavailable", false)
            else -> return HostReply.error(INTERNAL, "unknown storage scope: $scope", false)
        }
        return HostReply.complete(JSONObject().put("path", path).toString())
    }
    companion object { const val CAPABILITY = "storage.path"; private const val INTERNAL = "INTERNAL" }
}

// ════════════════════════════════════════════════════════════════════════════
// WebView handlers (schema UI contract names: webview.open / close / evaluate)
// ════════════════════════════════════════════════════════════════════════════

/**
 * `webview.open` capability handler. Launches [WebViewHostActivity] to host
 * a WebView for the requested URL. Mirrors the iOS/HarmonyOS `webview.open`
 * contract — Core sends `{ url }` and the host acknowledges with
 * `{ opened, url }`.
 *
 * Uses `FLAG_ACTIVITY_NEW_TASK` because the facade's [HostFacade.context] is
 * typically an Application context. When the context is null (JVM test path)
 * the handler fails closed with a non-retryable INTERNAL error, matching the
 * pattern of [ClipboardCopyHandler] / [DeviceVibrateHandler].
 */
class WebViewOpenHandler(private val facade: HostFacade) : CapabilityHandler {
    override fun handle(request: HostRequest): HostReply {
        val ctx = facade.context
            ?: return HostReply.error(INTERNAL, "context not wired (JVM test)", false)
        val params = try { JSONObject(request.paramsJson()) } catch (e: Exception) {
            return HostReply.error(INTERNAL, "invalid params: ${e.message}", false)
        }
        val url = params.optString("url", "")
        if (url.isEmpty()) return HostReply.error(INTERNAL, "url required", false)
        if (params.optString("profileId", "").isNotBlank()) {
            return HostReply.error("NOT_SUPPORTED", "Android UI WebView does not expose isolated profileId sessions", false)
        }
        val scheme = try { URI(url).scheme?.lowercase() } catch (_: Exception) { null }
        if (scheme != "https" && scheme != "http") {
            return HostReply.error(INTERNAL, "webview.open requires an http(s) url", false)
        }
        try {
            val intent = Intent(ctx, WebViewHostActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                putExtra(WebViewHostActivity.EXTRA_URL, url)
            }
            ctx.startActivity(intent)
        } catch (e: Exception) {
            return HostReply.error(INTERNAL, "webview.open failed: ${e.message}", true)
        }
        val result = JSONObject()
        result.put("opened", true)
        return HostReply.complete(result.toString())
    }
    companion object { const val CAPABILITY = "webview.open"; private const val INTERNAL = "INTERNAL" }
}

/**
 * `webview.close` capability handler. Closes the active WebView Activity.
 *
 * Returns success only after the active session has accepted a real
 * `Activity.finish()` request. A missing/destroyed session fails closed.
 */
class WebViewCloseHandler(private val facade: HostFacade) : CapabilityHandler {
    override fun handle(request: HostRequest): HostReply {
        val params = try { JSONObject(request.paramsJson()) } catch (e: Exception) {
            return HostReply.error(INTERNAL, "invalid params: ${e.message}", false)
        }
        if (params.optString("profileId", "").isNotBlank()) {
            return HostReply.error("NOT_SUPPORTED", "Android UI WebView does not expose isolated profileId sessions", false)
        }
        val closed = try { facade.uiWebViewSession.close() } catch (e: Exception) {
            return HostReply.error(INTERNAL, "webview.close failed: ${e.message}", true)
        }
        if (!closed) {
            return HostReply.error(
                REQUIRES_UI_CONTEXT,
                "webview.close requires an active WebView Activity",
                false
            )
        }
        val result = JSONObject()
        result.put("closed", true)
        return HostReply.complete(result.toString())
    }
    companion object {
        const val CAPABILITY = "webview.close"
        private const val INTERNAL = "INTERNAL"
        private const val REQUIRES_UI_CONTEXT = "REQUIRES_UI_CONTEXT"
    }
}

/**
 * `webview.evaluate` capability handler (schema UI contract name). Evaluates
 * JavaScript in the active WebView.
 *
 * Delegates to the active user-visible WebView session and returns its actual
 * JavaScript result. Contract payload is `{ url, script, timeoutMs? }`.
 */
class WebViewEvaluateHandler(private val facade: HostFacade) : CapabilityHandler {
    override fun handle(request: HostRequest): HostReply {
        val params = try { JSONObject(request.paramsJson()) } catch (e: Exception) {
            return HostReply.error(INTERNAL, "invalid params: ${e.message}", false)
        }
        val url = params.optString("url", "")
        if (url.isBlank()) return HostReply.error(INTERNAL, "url required", false)
        val scheme = try { URI(url).scheme?.lowercase() } catch (_: Exception) { null }
        if (scheme != "https" && scheme != "http") {
            return HostReply.error(INTERNAL, "webview.evaluate requires an http(s) url", false)
        }
        val script = params.optString("script", "")
        if (script.isBlank()) return HostReply.error(INTERNAL, "script required", false)
        if (params.optString("profileId", "").isNotBlank()) {
            return HostReply.error("NOT_SUPPORTED", "Android UI WebView does not expose isolated profileId sessions", false)
        }
        val timeoutMillis = if (params.has("timeoutMs") && !params.isNull("timeoutMs")) {
            params.optLong("timeoutMs", 0L).takeIf { it > 0L }
                ?: return HostReply.error(INTERNAL, "timeoutMs must be greater than 0", false)
        } else {
            null
        }
        val evaluation = try {
            runBlocking { facade.uiWebViewSession.evaluate(url, script, timeoutMillis) }
        } catch (e: WebViewExecutorError) {
            return webViewExecutorErrorReply(e)
        } catch (e: Exception) {
            return HostReply.error(INTERNAL, "webview.evaluate failed: ${e.message}", true)
        }
        val result = JSONObject()
        val rawValue = evaluation.value
        val canonicalResult = when (rawValue) {
            null -> JSONObject()
            is JSONObject -> rawValue
            is Map<*, *> -> JSONObject(rawValue)
            is String -> runCatching { JSONObject(rawValue) }
                .getOrElse { JSONObject().put("value", rawValue.removeSurrounding("\"")) }
            else -> JSONObject().put("value", rawValue)
        }
        result.put("result", canonicalResult)
        evaluation.finalUrl?.let { result.put("finalUrl", it) }
        evaluation.title?.let { result.put("title", it) }
        return HostReply.complete(result.toString())
    }
    companion object { const val CAPABILITY = "webview.evaluate"; private const val INTERNAL = "INTERNAL" }
}

private fun webViewExecutorErrorReply(error: WebViewExecutorError): HostReply = when (error) {
    is WebViewExecutorError.Timeout -> HostReply.error(
        "TIMEOUT", error.message ?: "webview.evaluate timed out", true
    )
    is WebViewExecutorError.NotImplemented -> HostReply.error(
        "NOT_IMPLEMENTED", error.message ?: "webview.evaluate unavailable", false
    )
    is WebViewExecutorError.ExecutionFailed -> HostReply.error(
        "EXECUTION_FAILED", error.message ?: "webview.evaluate failed", true
    )
    is WebViewExecutorError.RequiresUiContext -> HostReply.error(
        "REQUIRES_UI_CONTEXT", error.message ?: "webview.evaluate requires UI context", false
    )
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
    data class Task(
        val tag: String,
        val kind: String,
        val scheduledAtMillis: Long,
        val hostTaskId: String? = null
    )

    private val tasks = java.util.concurrent.ConcurrentHashMap<String, Task>()

    fun schedule(tag: String, kind: String, hostTaskId: String? = null) {
        tasks[tag] = Task(tag, kind, System.currentTimeMillis(), hostTaskId)
    }

    fun cancel(tag: String): Boolean = tasks.remove(tag) != null

    fun list(): List<Task> = tasks.values.toList()

    fun find(tag: String): Task? = tasks[tag]

    fun size(): Int = tasks.size
}
