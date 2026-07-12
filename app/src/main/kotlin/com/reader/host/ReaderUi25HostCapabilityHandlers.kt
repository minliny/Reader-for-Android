package com.reader.host

import org.json.JSONArray
import org.json.JSONObject

/** Register the Reader UI 2.5 additions without changing rollout authority. */
fun HostFacade.registerReaderUi25Handlers(runtime: HostRuntime): HostRuntime = runtime
    .register(FileSelect25Handler.CAPABILITY, FileSelect25Handler(this))
    .register(FontRegisterFile25Handler.CAPABILITY, FontRegisterFile25Handler(this))
    .register(FontUnregisterFile25Handler.CAPABILITY, FontUnregisterFile25Handler(this))
    .register(ClipboardRead25Handler.CAPABILITY, ClipboardRead25Handler(this))
    .register(ClipboardWrite25Handler.CAPABILITY, ClipboardWrite25Handler(this))
    .register(TtsStart25Handler.CAPABILITY, TtsStart25Handler(this))
    .register(TtsStop25Handler.CAPABILITY, TtsStop25Handler(this))
    .register(TtsPause25Handler.CAPABILITY, TtsPause25Handler(this))
    .register(BrightnessSet25Handler.CAPABILITY, BrightnessSet25Handler(this))
    .register(BrightnessGet25Handler.CAPABILITY, BrightnessGet25Handler(this))
    .register(ScreenKeepAwake25Handler.CAPABILITY, ScreenKeepAwake25Handler(this))
    .register(ScreenAllowSleep25Handler.CAPABILITY, ScreenAllowSleep25Handler(this))
    .register(Haptics25Handler.LIGHT_CAPABILITY, Haptics25Handler(this, ReaderHapticStyle.LIGHT))
    .register(Haptics25Handler.MEDIUM_CAPABILITY, Haptics25Handler(this, ReaderHapticStyle.MEDIUM))
    .register(Haptics25Handler.HEAVY_CAPABILITY, Haptics25Handler(this, ReaderHapticStyle.HEAVY))
    .register(NetworkStatus25Handler.CAPABILITY, NetworkStatus25Handler(this))
    // This replaces the legacy webdav.connect registration with a superset
    // handler: 2.5 credentials plus the existing `{url}` payload remain valid.
    .register(WebDavConnect25Handler.CAPABILITY, WebDavConnect25Handler(this))
    .register(WebDavBackup25Handler.CAPABILITY, WebDavBackup25Handler(this))
    .register(WebDavRestore25Handler.CAPABILITY, WebDavRestore25Handler(this))
    .register(ShareText25Handler.CAPABILITY, ShareText25Handler(this))
    .register(ShareFile25Handler.CAPABILITY, ShareFile25Handler(this))
    .register(BackgroundTaskStart25Handler.CAPABILITY, BackgroundTaskStart25Handler(this))
    .register(BackgroundTaskEnd25Handler.CAPABILITY, BackgroundTaskEnd25Handler(this))

class FileSelect25Handler(private val facade: HostFacade) : CapabilityHandler {
    override fun handle(request: HostRequest): HostReply = execute25(request) { params, scope ->
        val mimeTypes = params.optionalStringArray("mimeTypes") ?: listOf("*/*")
        if (mimeTypes.isEmpty() || mimeTypes.size > 32 ||
            mimeTypes.any { it.isBlank() || !it.contains('/') }
        ) {
            invalid("mimeTypes must contain 1..32 non-blank MIME types")
        }
        val allowsMultiple = params.optionalBoolean("allowsMultiple") ?: false
        val activity = facade.readerUi25Services.activityHostProvider()
            ?: failClosed("REQUIRES_UI_CONTEXT", "file.select requires a foreground Activity")
        val selection = activity.selectFiles(scope, mimeTypes, allowsMultiple)
        val files = JSONArray()
        selection.files.forEach { file ->
            files.put(JSONObject()
                .put("path", file.path)
                .put("name", file.name)
                .put("mimeType", file.mimeType ?: JSONObject.NULL)
                .put("size", file.size ?: JSONObject.NULL))
        }
        JSONObject()
            .put("selected", selection.files.isNotEmpty())
            .put("files", files)
    }

    companion object { const val CAPABILITY = "file.select" }
}

class FontRegisterFile25Handler(private val facade: HostFacade) : CapabilityHandler {
    override fun handle(request: HostRequest): HostReply = execute25(request) { params, scope ->
        val path = params.requiredString("path")
        params.requiredString("familyName") // requested logical family; Host returns the actual name.
        val host = facade.readerUi25Services.fontRegistration
            ?: failClosed("NOT_CONFIGURED", "font.registerFile host is not configured")
        val result = host.register(path)
        JSONObject()
            .put("registered", true)
            .put("path", result.path)
            .put("familyName", result.familyName)
            .put("fontNames", JSONArray(result.fontNames))
    }

    companion object { const val CAPABILITY = "font.registerFile" }
}

class FontUnregisterFile25Handler(private val facade: HostFacade) : CapabilityHandler {
    override fun handle(request: HostRequest): HostReply = execute25(request) { params, scope ->
        val path = params.requiredString("path")
        val familyName = params.requiredString("familyName")
        val host = facade.readerUi25Services.fontRegistration
            ?: failClosed("NOT_CONFIGURED", "font.unregisterFile host is not configured")
        val result = host.unregister(path, familyName)
        if (!result.logicalUnregistered || (!result.physicallyUnregistered && !result.restartRequired)) {
            failClosed("EXECUTION_FAILED", "font unregister result violates logical/restart contract")
        }
        JSONObject()
            .put("logicalUnregistered", result.logicalUnregistered)
            .put("physicallyUnregistered", result.physicallyUnregistered)
            .put("restartRequired", result.restartRequired)
    }

    companion object { const val CAPABILITY = "font.unregisterFile" }
}

class ClipboardRead25Handler(private val facade: HostFacade) : CapabilityHandler {
    override fun handle(request: HostRequest): HostReply = execute25(request) { _, _ ->
        when (val alias = ClipboardPasteHandler(facade).handle(request.aliasAs(ClipboardPasteHandler.CAPABILITY))) {
            is HostReply.Complete -> JSONObject(alias.resultJson())
            is HostReply.Error -> throw ReaderUiHostCapabilityFailure(
                alias.code(), alias.message(), alias.retryable()
            )
            else -> failClosed("EXECUTION_FAILED", "clipboard.read returned an unknown reply")
        }
    }

    companion object { const val CAPABILITY = "clipboard.read" }
}

class ClipboardWrite25Handler(private val facade: HostFacade) : CapabilityHandler {
    override fun handle(request: HostRequest): HostReply = execute25(request) { params, _ ->
        val text = params.requiredString("text")
        val aliasRequest = request.aliasAs(
            ClipboardCopyHandler.CAPABILITY,
            JSONObject().put("text", text)
        )
        when (val alias = ClipboardCopyHandler(facade).handle(aliasRequest)) {
            is HostReply.Complete -> JSONObject()
                .put("written", JSONObject(alias.resultJson()).optBoolean("copied", false))
            is HostReply.Error -> throw ReaderUiHostCapabilityFailure(
                alias.code(), alias.message(), alias.retryable()
            )
            else -> failClosed("EXECUTION_FAILED", "clipboard.write returned an unknown reply")
        }
    }

    companion object { const val CAPABILITY = "clipboard.write" }
}

class TtsStart25Handler(private val facade: HostFacade) : CapabilityHandler {
    override fun handle(request: HostRequest): HostReply = execute25(request) { params, scope ->
        val text = params.requiredString("text")
        val rate = params.optionalDouble("rate") ?: 1.0
        val pitch = params.optionalDouble("pitch") ?: 1.0
        if (rate !in 0.1..4.0) invalid("rate must be within 0.1...4.0")
        if (pitch !in 0.5..2.0) invalid("pitch must be within 0.5...2.0")
        val language = params.optionalString("language") ?: "zh-CN"
        if (language.isBlank()) invalid("language must not be blank")

        val canonical = JSONObject()
            .put("text", text)
            .put("correlationId", "reader-ui-2.5-${scope.key}")
            .put("rate", rate)
            .put("pitch", pitch)
            .put("language", language)
        when (val alias = TtsSystemStartHandler(facade).handle(
            request.aliasAs(TtsSystemStartHandler.CAPABILITY, canonical)
        )) {
            is HostReply.Complete -> JSONObject()
                .put("started", JSONObject(alias.resultJson()).optBoolean("started", false))
                .put("rate", rate)
                .put("pitch", pitch)
                .put("language", language)
            is HostReply.Error -> throw ReaderUiHostCapabilityFailure(
                alias.code(), alias.message(), alias.retryable()
            )
            else -> failClosed("EXECUTION_FAILED", "tts.start returned an unknown reply")
        }
    }

    companion object { const val CAPABILITY = "tts.start" }
}

class TtsStop25Handler(private val facade: HostFacade) : CapabilityHandler {
    override fun handle(request: HostRequest): HostReply = aliasNoPayload(
        request,
        TtsSystemStopHandler(facade),
        TtsSystemStopHandler.CAPABILITY,
        "stopped"
    )

    companion object { const val CAPABILITY = "tts.stop" }
}

class TtsPause25Handler(private val facade: HostFacade) : CapabilityHandler {
    override fun handle(request: HostRequest): HostReply = aliasNoPayload(
        request,
        TtsSystemPauseHandler(facade),
        TtsSystemPauseHandler.CAPABILITY,
        "paused"
    )

    companion object { const val CAPABILITY = "tts.pause" }
}

class BrightnessSet25Handler(private val facade: HostFacade) : CapabilityHandler {
    override fun handle(request: HostRequest): HostReply = execute25(request) { params, _ ->
        val value = params.requiredDouble("value")
        if (value !in 0.0..1.0) invalid("value must be within 0...1")
        val host = facade.readerUi25Services.activityHostProvider()
            ?: failClosed("REQUIRES_UI_CONTEXT", "brightness.set requires a foreground Activity")
        JSONObject().put("brightness", host.setBrightness(value.toFloat()))
    }

    companion object { const val CAPABILITY = "brightness.set" }
}

class BrightnessGet25Handler(private val facade: HostFacade) : CapabilityHandler {
    override fun handle(request: HostRequest): HostReply = execute25(request) { _, _ ->
        val host = facade.readerUi25Services.activityHostProvider()
            ?: failClosed("REQUIRES_UI_CONTEXT", "brightness.get requires a foreground Activity")
        JSONObject().put("brightness", host.getBrightness())
    }

    companion object { const val CAPABILITY = "brightness.get" }
}

class ScreenKeepAwake25Handler(private val facade: HostFacade) : CapabilityHandler {
    override fun handle(request: HostRequest): HostReply = execute25(request) { _, _ ->
        val host = facade.readerUi25Services.activityHostProvider()
            ?: failClosed("REQUIRES_UI_CONTEXT", "screen.keepAwake requires a foreground Activity")
        val applied = host.setKeepAwake(true)
        if (!applied) failClosed("EXECUTION_FAILED", "FLAG_KEEP_SCREEN_ON was not applied", true)
        JSONObject().put("applied", true).put("keepAwake", true)
    }

    companion object { const val CAPABILITY = "screen.keepAwake" }
}

class ScreenAllowSleep25Handler(private val facade: HostFacade) : CapabilityHandler {
    override fun handle(request: HostRequest): HostReply = execute25(request) { _, _ ->
        val host = facade.readerUi25Services.activityHostProvider()
            ?: failClosed("REQUIRES_UI_CONTEXT", "screen.allowSleep requires a foreground Activity")
        val applied = host.setKeepAwake(false)
        if (!applied) failClosed("EXECUTION_FAILED", "FLAG_KEEP_SCREEN_ON was not cleared", true)
        JSONObject().put("applied", true).put("keepAwake", false)
    }

    companion object { const val CAPABILITY = "screen.allowSleep" }
}

class Haptics25Handler(
    private val facade: HostFacade,
    private val style: ReaderHapticStyle
) : CapabilityHandler {
    override fun handle(request: HostRequest): HostReply = execute25(request) { _, _ ->
        val host = facade.readerUi25Services.haptics
            ?: failClosed("NOT_CONFIGURED", "haptics host is not configured")
        val performed = host.perform(style)
        if (!performed) failClosed("NOT_AVAILABLE", "device has no usable vibrator")
        JSONObject()
            .put("performed", true)
            .put("style", style.name.lowercase())
    }

    companion object {
        const val LIGHT_CAPABILITY = "haptics.light"
        const val MEDIUM_CAPABILITY = "haptics.medium"
        const val HEAVY_CAPABILITY = "haptics.heavy"
    }
}

class NetworkStatus25Handler(private val facade: HostFacade) : CapabilityHandler {
    override fun handle(request: HostRequest): HostReply = execute25(request) { _, _ ->
        val host = facade.readerUi25Services.networkStatus
            ?: failClosed("NOT_CONFIGURED", "network.status host is not configured")
        val snapshot = host.snapshot()
        JSONObject()
            .put("connected", snapshot.connected)
            .put("status", snapshot.status)
            .put("interface", snapshot.interfaceName)
            .put("isExpensive", snapshot.isExpensive)
            .put("isConstrained", snapshot.isConstrained)
    }

    companion object { const val CAPABILITY = "network.status" }
}

class WebDavConnect25Handler(private val facade: HostFacade) : CapabilityHandler {
    override fun handle(request: HostRequest): HostReply = execute25(request) { params, scope ->
        val override = params.webDavOverride()
        val legacyURL = params.optionalString("url")?.let { validateHttpURL(it, "url") }
        if (override != null && legacyURL != null) {
            invalid("webdav.connect accepts either 2.5 credentials or legacy url, not both")
        }
        val host = facade.readerUi25Services.webDav
            ?: failClosed("NOT_CONFIGURED", "WebDAV 2.5 host is not configured")
        val result = host.connect(scope, override, legacyURL)
        JSONObject()
            .put("connected", result.connected)
            .put("statusCode", result.statusCode)
            .put("message", result.message)
    }

    companion object { const val CAPABILITY = "webdav.connect" }
}

class WebDavBackup25Handler(private val facade: HostFacade) : CapabilityHandler {
    override fun handle(request: HostRequest): HostReply = execute25(request) { params, scope ->
        val host = facade.readerUi25Services.webDav
            ?: failClosed("NOT_CONFIGURED", "WebDAV 2.5 host is not configured")
        val result = host.backup(scope, params.webDavOverride())
        JSONObject()
            .put("backedUp", result.backedUp)
            .put("remoteURL", result.remoteURL)
            .put("statusCode", result.statusCode)
            .put("resourceCount", result.resourceCount)
    }

    companion object { const val CAPABILITY = "webdav.backup" }
}

class WebDavRestore25Handler(private val facade: HostFacade) : CapabilityHandler {
    override fun handle(request: HostRequest): HostReply = execute25(request) { params, scope ->
        val remoteURL = validateHttpURL(params.requiredString("remoteURL"), "remoteURL")
        val host = facade.readerUi25Services.webDav
            ?: failClosed("NOT_CONFIGURED", "WebDAV 2.5 host is not configured")
        val result = host.restore(scope, remoteURL, params.webDavOverride())
        JSONObject()
            .put("restored", result.restored)
            .put("remoteURL", result.remoteURL)
            .put("statusCode", result.statusCode)
            .put("applied", result.restored)
    }

    companion object { const val CAPABILITY = "webdav.restore" }
}

class ShareText25Handler(private val facade: HostFacade) : CapabilityHandler {
    override fun handle(request: HostRequest): HostReply = execute25(request) { params, _ ->
        val text = params.requiredString("text")
        val host = facade.readerUi25Services.activityHostProvider()
            ?: failClosed("REQUIRES_UI_CONTEXT", "share.text requires a foreground Activity")
        if (!host.shareText(text)) failClosed("EXECUTION_FAILED", "share chooser was not launched")
        JSONObject().put("shared", true)
    }

    companion object { const val CAPABILITY = "share.text" }
}

class ShareFile25Handler(private val facade: HostFacade) : CapabilityHandler {
    override fun handle(request: HostRequest): HostReply = execute25(request) { params, _ ->
        val path = params.requiredString("path")
        val host = facade.readerUi25Services.activityHostProvider()
            ?: failClosed("REQUIRES_UI_CONTEXT", "share.file requires a foreground Activity")
        if (!host.shareFile(path)) failClosed("EXECUTION_FAILED", "share chooser was not launched")
        JSONObject().put("shared", true).put("path", path)
    }

    companion object { const val CAPABILITY = "share.file" }
}

class BackgroundTaskStart25Handler(private val facade: HostFacade) : CapabilityHandler {
    override fun handle(request: HostRequest): HostReply = execute25(request) { params, _ ->
        val name = params.requiredString("name")
        if (name.length > 128) invalid("name must contain at most 128 characters")
        val host = facade.readerUi25Services.backgroundTasks
            ?: failClosed("NOT_CONFIGURED", "WorkManager background host is not configured")
        val result = host.start(name)
        JSONObject()
            .put("started", true)
            .put("taskId", result.taskId)
            .put("name", name)
    }

    companion object { const val CAPABILITY = "background.task.start" }
}

class BackgroundTaskEnd25Handler(private val facade: HostFacade) : CapabilityHandler {
    override fun handle(request: HostRequest): HostReply = execute25(request) { params, _ ->
        val taskId = params.requiredString("taskId")
        val host = facade.readerUi25Services.backgroundTasks
            ?: failClosed("NOT_CONFIGURED", "WorkManager background host is not configured")
        val ended = host.end(taskId)
        if (!ended) failClosed("EXECUTION_FAILED", "background task was not cancelled")
        JSONObject().put("ended", true).put("taskId", taskId)
    }

    companion object { const val CAPABILITY = "background.task.end" }
}

private fun aliasNoPayload(
    request: HostRequest,
    handler: CapabilityHandler,
    canonicalCapability: String,
    resultField: String
): HostReply = execute25(request) { _, _ ->
    when (val alias = handler.handle(request.aliasAs(canonicalCapability, JSONObject()))) {
        is HostReply.Complete -> {
            val acknowledged = JSONObject(alias.resultJson()).optBoolean("acknowledged", false)
            if (!acknowledged) failClosed("EXECUTION_FAILED", "$canonicalCapability was not acknowledged")
            JSONObject().put(resultField, true)
        }
        is HostReply.Error -> throw ReaderUiHostCapabilityFailure(
            alias.code(), alias.message(), alias.retryable()
        )
        else -> failClosed("EXECUTION_FAILED", "$canonicalCapability returned an unknown reply")
    }
}

private inline fun execute25(
    request: HostRequest,
    block: (JSONObject, ReaderUiHostRequestScope) -> JSONObject
): HostReply {
    return try {
        val params = try {
            JSONObject(request.paramsJson())
        } catch (error: Exception) {
            invalid("payload must be a JSON object: ${error.message}")
        }
        val scope = ReaderUiHostRequestScope(
            requestId = request.requestId(),
            operationId = request.operationId(),
            generation = params.optionalLong("generation")?.also {
                if (it < 0L) invalid("generation must be >= 0")
            }
        )
        HostReply.complete(block(params, scope).toString())
    } catch (error: ReaderUiHostCapabilityFailure) {
        HostReply.error(error.errorCode, error.message ?: "", error.retryable)
    } catch (error: Exception) {
        HostReply.error(
            "EXECUTION_FAILED",
            "${request.capability()} failed: ${error.message}",
            true
        )
    }
}

private fun HostRequest.aliasAs(capability: String, params: JSONObject? = null): HostRequest =
    HostRequest(requestId(), operationId(), capability, (params ?: JSONObject(paramsJson())).toString())

private fun JSONObject.webDavOverride(): ReaderWebDavConnectionOverride? {
    val fields = listOf("serverURL", "username", "password")
    val present = fields.filter { has(it) && !isNull(it) }
    if (present.isEmpty()) return null
    if (present.size != fields.size) {
        invalid("serverURL, username, and password must be provided together")
    }
    val serverURL = validateHttpURL(requiredString("serverURL"), "serverURL")
    return ReaderWebDavConnectionOverride(
        serverURL = serverURL,
        username = requiredString("username"),
        password = requiredString("password")
    )
}

private fun JSONObject.requiredString(name: String, allowEmpty: Boolean = false): String {
    if (!has(name) || isNull(name) || get(name) !is String) invalid("$name must be a string")
    val value = getString(name)
    if (!allowEmpty && value.isBlank()) invalid("$name must not be blank")
    return value
}

private fun JSONObject.optionalString(name: String): String? {
    if (!has(name) || isNull(name)) return null
    if (get(name) !is String) invalid("$name must be a string")
    return getString(name)
}

private fun JSONObject.optionalBoolean(name: String): Boolean? {
    if (!has(name) || isNull(name)) return null
    if (get(name) !is Boolean) invalid("$name must be a boolean")
    return getBoolean(name)
}

private fun JSONObject.requiredDouble(name: String): Double {
    if (!has(name) || isNull(name) || get(name) !is Number) invalid("$name must be a number")
    val value = (get(name) as Number).toDouble()
    if (!value.isFinite()) invalid("$name must be finite")
    return value
}

private fun JSONObject.optionalDouble(name: String): Double? {
    if (!has(name) || isNull(name)) return null
    if (get(name) !is Number) invalid("$name must be a number")
    val value = (get(name) as Number).toDouble()
    if (!value.isFinite()) invalid("$name must be finite")
    return value
}

private fun JSONObject.optionalLong(name: String): Long? {
    if (!has(name) || isNull(name)) return null
    val raw = get(name)
    if (raw !is Number) invalid("$name must be an integer")
    val double = raw.toDouble()
    val long = raw.toLong()
    if (!double.isFinite() || double != long.toDouble()) invalid("$name must be an integer")
    return long
}

private fun JSONObject.optionalStringArray(name: String): List<String>? {
    if (!has(name) || isNull(name)) return null
    val raw = get(name)
    if (raw !is JSONArray) invalid("$name must be an array")
    return buildList {
        for (index in 0 until raw.length()) {
            val item = raw.get(index)
            if (item !is String) invalid("$name[$index] must be a string")
            add(item)
        }
    }
}

private fun invalid(message: String): Nothing =
    throw ReaderUiHostCapabilityFailure("INVALID_PARAMS", message)

private fun failClosed(code: String, message: String, retryable: Boolean = false): Nothing =
    throw ReaderUiHostCapabilityFailure(code, message, retryable)
