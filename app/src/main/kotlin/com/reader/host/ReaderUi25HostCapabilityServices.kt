package com.reader.host

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.graphics.Typeface
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.OpenableColumns
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.reader.android.data.adapter.AndroidWebDavClient
import com.reader.android.data.adapter.AuthMethod
import com.reader.android.data.adapter.WebDavClient
import com.reader.android.data.adapter.WebDavCredential
import com.reader.android.data.adapter.WebDavCredentialStore
import com.reader.android.data.adapter.WebDavMethod
import com.reader.android.data.adapter.WebDavRequest
import com.reader.android.data.repository.BookSourceRepository
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/** Structured fail-closed signal converted to HostReply.Error by handlers. */
class ReaderUiHostCapabilityFailure(
    val errorCode: String,
    message: String,
    val retryable: Boolean = false
) : IllegalStateException(message)

data class ReaderUiHostRequestScope(
    val requestId: Long,
    val operationId: Long,
    val generation: Long?
) {
    val key: String = buildString {
        append(requestId).append(':').append(operationId)
        generation?.let { append(':').append(it) }
    }
}

data class ReaderSelectedFile(
    val path: String,
    val name: String,
    val mimeType: String?,
    val size: Long?
)

data class ReaderFileSelectionResult(val files: List<ReaderSelectedFile>)

/** Activity-owned operations. No Activity means no acknowledgement. */
interface ReaderUiActivityCapabilityHost {
    fun selectFiles(
        scope: ReaderUiHostRequestScope,
        mimeTypes: List<String>,
        allowsMultiple: Boolean
    ): ReaderFileSelectionResult

    fun setBrightness(value: Float): Float
    fun getBrightness(): Float
    fun setKeepAwake(keepAwake: Boolean): Boolean
    fun shareText(text: String): Boolean
    fun shareFile(path: String): Boolean
    fun close()
}

/**
 * Generation-scoped process binding for the current foreground Activity.
 * A destroyed Activity cannot unbind a newer Activity instance.
 */
object ReaderUiActivityCapabilityBinding {
    private data class Bound(
        val generation: Long,
        val host: ReaderUiActivityCapabilityHost
    )

    private val counter = AtomicLong(0L)
    private val current = AtomicReference<Bound?>(null)

    fun bind(host: ReaderUiActivityCapabilityHost): Long {
        val generation = counter.incrementAndGet()
        current.getAndSet(Bound(generation, host))?.host?.close()
        return generation
    }

    fun unbind(host: ReaderUiActivityCapabilityHost, generation: Long) {
        val bound = current.get()
        if (bound != null && bound.generation == generation && bound.host === host &&
            current.compareAndSet(bound, null)
        ) {
            host.close()
        }
    }

    fun currentHost(): ReaderUiActivityCapabilityHost? = current.get()?.host

    internal fun clearForTest() {
        current.getAndSet(null)?.host?.close()
    }
}

/**
 * Real Activity Result / Window / chooser implementation.
 *
 * Construct this as a ComponentActivity property, before `onCreate`, because
 * Activity Result launchers must be registered before the Activity reaches
 * STARTED. `selectFiles` is intentionally blocking and must be called from an
 * IO/host thread; AppShell performs that dispatcher hop.
 */
class AndroidReaderUiActivityCapabilityHost(
    private val activity: ComponentActivity,
    private val selectionTimeoutMs: Long = DEFAULT_SELECTION_TIMEOUT_MS
) : ReaderUiActivityCapabilityHost {
    private data class PendingSelection(
        val scope: ReaderUiHostRequestScope,
        val future: CompletableFuture<List<Uri>>
    )

    private val pendingSelection = AtomicReference<PendingSelection?>(null)

    private val singleDocumentLauncher = activity.registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> completeSelection(uri?.let(::listOf).orEmpty()) }

    private val multipleDocumentLauncher = activity.registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> completeSelection(uris.orEmpty()) }

    override fun selectFiles(
        scope: ReaderUiHostRequestScope,
        mimeTypes: List<String>,
        allowsMultiple: Boolean
    ): ReaderFileSelectionResult {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw ReaderUiHostCapabilityFailure(
                "INVALID_DISPATCH_CONTEXT",
                "file.select must execute off the Android main thread"
            )
        }
        if (!activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            throw ReaderUiHostCapabilityFailure(
                "REQUIRES_UI_CONTEXT",
                "file.select requires a started foreground Activity"
            )
        }

        val pending = PendingSelection(scope, CompletableFuture())
        if (!pendingSelection.compareAndSet(null, pending)) {
            throw ReaderUiHostCapabilityFailure(
                "BUSY",
                "another file.select request is already active"
            )
        }

        activity.runOnUiThread {
            try {
                if (allowsMultiple) {
                    multipleDocumentLauncher.launch(mimeTypes.toTypedArray())
                } else {
                    singleDocumentLauncher.launch(mimeTypes.toTypedArray())
                }
            } catch (error: Exception) {
                if (pendingSelection.compareAndSet(pending, null)) {
                    pending.future.completeExceptionally(error)
                }
            }
        }

        val uris = try {
            pending.future.get(selectionTimeoutMs, TimeUnit.MILLISECONDS)
        } catch (error: TimeoutException) {
            pendingSelection.compareAndSet(pending, null)
            throw ReaderUiHostCapabilityFailure(
                "TIMEOUT",
                "file.select exceeded ${selectionTimeoutMs}ms",
                retryable = true
            )
        } catch (error: InterruptedException) {
            pendingSelection.compareAndSet(pending, null)
            Thread.currentThread().interrupt()
            throw ReaderUiHostCapabilityFailure("CANCELLED", "file.select was cancelled")
        } catch (error: Exception) {
            pendingSelection.compareAndSet(pending, null)
            val cause = error.cause ?: error
            if (cause is ReaderUiHostCapabilityFailure) throw cause
            throw ReaderUiHostCapabilityFailure(
                "EXECUTION_FAILED",
                "file.select failed: ${cause.message}",
                retryable = true
            )
        }
        return ReaderFileSelectionResult(uris.map(::describeUri))
    }

    override fun setBrightness(value: Float): Float = callOnMain {
        val attributes = activity.window.attributes
        attributes.screenBrightness = value
        activity.window.attributes = attributes
        activity.window.attributes.screenBrightness.coerceIn(0f, 1f)
    }

    override fun getBrightness(): Float = callOnMain {
        val windowValue = activity.window.attributes.screenBrightness
        if (windowValue >= 0f) {
            windowValue.coerceIn(0f, 1f)
        } else {
            val system = runCatching {
                Settings.System.getInt(
                    activity.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS
                )
            }.getOrDefault(128)
            (system / 255f).coerceIn(0f, 1f)
        }
    }

    override fun setKeepAwake(keepAwake: Boolean): Boolean = callOnMain {
        if (keepAwake) {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        val flagSet = activity.window.attributes.flags and
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0
        flagSet == keepAwake
    }

    override fun shareText(text: String): Boolean = callOnMain {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        activity.startActivity(Intent.createChooser(send, null))
        true
    }

    override fun shareFile(path: String): Boolean = callOnMain {
        val uri = shareableUri(path)
        val mimeType = activity.contentResolver.getType(uri)
            ?: java.net.URLConnection.guessContentTypeFromName(uri.lastPathSegment)
            ?: "application/octet-stream"
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("reader-share", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        activity.startActivity(Intent.createChooser(send, null))
        true
    }

    override fun close() {
        val pending = pendingSelection.getAndSet(null) ?: return
        pending.future.completeExceptionally(
            ReaderUiHostCapabilityFailure(
                "REQUIRES_UI_CONTEXT",
                "file.select Activity was destroyed"
            )
        )
    }

    private fun completeSelection(uris: List<Uri>) {
        val pending = pendingSelection.getAndSet(null) ?: return
        uris.forEach { uri ->
            runCatching {
                activity.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
        }
        pending.future.complete(uris)
    }

    private fun describeUri(uri: Uri): ReaderSelectedFile {
        var displayName: String? = null
        var size: Long? = null
        var cursor: Cursor? = null
        try {
            cursor = activity.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null
            )
            if (cursor?.moveToFirst() == true) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex >= 0 && !cursor.isNull(nameIndex)) {
                    displayName = cursor.getString(nameIndex)
                }
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                    size = cursor.getLong(sizeIndex)
                }
            }
        } finally {
            cursor?.close()
        }
        return ReaderSelectedFile(
            path = uri.toString(),
            name = displayName ?: uri.lastPathSegment ?: "selected-file",
            mimeType = activity.contentResolver.getType(uri),
            size = size
        )
    }

    private fun shareableUri(path: String): Uri {
        val parsed = Uri.parse(path)
        if (parsed.scheme == "content") return parsed
        val file = when (parsed.scheme) {
            null, "" -> File(path)
            "file" -> File(requireNotNull(parsed.path))
            else -> throw ReaderUiHostCapabilityFailure(
                "INVALID_PARAMS",
                "share.file path must be an app file or content URI"
            )
        }
        if (!file.isFile || !file.canRead()) {
            throw ReaderUiHostCapabilityFailure("NOT_FOUND", "share.file path is not readable")
        }
        return try {
            FileProvider.getUriForFile(
                activity,
                "${activity.packageName}.fileprovider",
                file
            )
        } catch (error: Exception) {
            throw ReaderUiHostCapabilityFailure(
                "INVALID_PARAMS",
                "share.file path is outside configured app roots: ${error.message}"
            )
        }
    }

    private fun <T> callOnMain(block: () -> T): T {
        if (Looper.myLooper() == Looper.getMainLooper()) return block()
        val future = CompletableFuture<T>()
        activity.runOnUiThread {
            runCatching(block)
                .onSuccess(future::complete)
                .onFailure(future::completeExceptionally)
        }
        return try {
            future.get(MAIN_OPERATION_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (error: Exception) {
            val cause = error.cause ?: error
            if (cause is ReaderUiHostCapabilityFailure) throw cause
            throw ReaderUiHostCapabilityFailure(
                "EXECUTION_FAILED",
                "Activity capability failed: ${cause.message}",
                retryable = true
            )
        }
    }

    companion object {
        private const val DEFAULT_SELECTION_TIMEOUT_MS = 120_000L
        private const val MAIN_OPERATION_TIMEOUT_MS = 5_000L
    }
}

data class ReaderRegisteredFont(
    val path: String,
    val familyName: String,
    val fontNames: List<String>
)

data class ReaderUnregisteredFont(
    val logicalUnregistered: Boolean,
    val physicallyUnregistered: Boolean,
    val restartRequired: Boolean
)

fun interface ReaderFontRegistrationHost {
    fun register(path: String): ReaderRegisteredFont

    /** Android Typeface has no process-wide unload API; logical removal is the stable guarantee. */
    fun unregister(path: String, familyName: String): ReaderUnregisteredFont =
        ReaderUnregisteredFont(true, false, true)
}

/** Copies a selected font into app storage and validates it through Typeface. */
class AndroidReaderFontRegistrationHost(private val context: Context) : ReaderFontRegistrationHost {
    private val registered = java.util.concurrent.ConcurrentHashMap<String, Typeface>()

    override fun register(path: String): ReaderRegisteredFont {
        val sourceUri = Uri.parse(path)
        val displayName = sourceDisplayName(sourceUri, path)
        val extension = displayName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        if (extension !in SUPPORTED_EXTENSIONS) {
            throw ReaderUiHostCapabilityFailure(
                "INVALID_PARAMS",
                "font.registerFile supports ttf, otf, and ttc files"
            )
        }

        val bytes = openFont(sourceUri, path).use { it.readBytes() }
        if (bytes.isEmpty()) {
            throw ReaderUiHostCapabilityFailure("INVALID_PARAMS", "font file is empty")
        }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
        val fontDir = File(context.filesDir, "reader-fonts").apply { mkdirs() }
        val destination = File(fontDir, "$digest.$extension")
        if (!destination.exists()) {
            FileOutputStream(destination).use { it.write(bytes) }
        }

        val typeface = try {
            Typeface.createFromFile(destination)
        } catch (error: Exception) {
            destination.delete()
            throw ReaderUiHostCapabilityFailure(
                "INVALID_PARAMS",
                "font file could not be loaded: ${error.message}"
            )
        }
        // Host-derived identity: never trust a UI label as the registered family.
        val fontName = "reader-font-${digest.take(20)}"
        registered[fontName] = typeface
        return ReaderRegisteredFont(destination.absolutePath, fontName, listOf(fontName))
    }

    override fun unregister(path: String, familyName: String): ReaderUnregisteredFont {
        registered.remove(familyName)
        val file = File(path)
        val fontRoot = File(context.filesDir, "reader-fonts").canonicalFile
        runCatching {
            if (file.canonicalFile.toPath().startsWith(fontRoot.toPath())) file.delete()
        }
        // Existing Text/Compose objects may retain the Typeface until restart.
        return ReaderUnregisteredFont(true, false, true)
    }

    fun resolve(fontName: String): Typeface? = registered[fontName]

    private fun openFont(uri: Uri, rawPath: String) = when (uri.scheme) {
        "content" -> context.contentResolver.openInputStream(uri)
            ?: throw ReaderUiHostCapabilityFailure("NOT_FOUND", "font content URI is unreadable")
        "file" -> File(requireNotNull(uri.path)).inputStream()
        null, "" -> File(rawPath).inputStream()
        else -> throw ReaderUiHostCapabilityFailure(
            "INVALID_PARAMS",
            "font path must be a file path or content URI"
        )
    }

    private fun sourceDisplayName(uri: Uri, rawPath: String): String {
        if (uri.scheme == "content") {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0 && !cursor.isNull(index)) return cursor.getString(index)
                }
            }
        }
        return File(uri.path ?: rawPath).name
    }

    private companion object {
        val SUPPORTED_EXTENSIONS = setOf("ttf", "otf", "ttc")
    }
}

data class ReaderNetworkSnapshot(
    val connected: Boolean,
    val status: String,
    val interfaceName: String,
    val isExpensive: Boolean,
    val isConstrained: Boolean
)

fun interface ReaderNetworkStatusHost {
    fun snapshot(): ReaderNetworkSnapshot
}

class AndroidReaderNetworkStatusHost(context: Context) : ReaderNetworkStatusHost {
    private val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as?
        ConnectivityManager

    override fun snapshot(): ReaderNetworkSnapshot {
        val manager = connectivity ?: throw ReaderUiHostCapabilityFailure(
            "NOT_AVAILABLE",
            "ConnectivityManager is unavailable"
        )
        val network = manager.activeNetwork
        val capabilities = network?.let(manager::getNetworkCapabilities)
        if (capabilities == null) {
            return ReaderNetworkSnapshot(false, "offline", "none", false, false)
        }
        val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val validated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        val connected = hasInternet && validated
        val status = when {
            connected -> "online"
            hasInternet -> "limited"
            else -> "offline"
        }
        val interfaceName = when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "vpn"
            else -> "other"
        }
        return ReaderNetworkSnapshot(
            connected = connected,
            status = status,
            interfaceName = interfaceName,
            isExpensive = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
            isConstrained = manager.restrictBackgroundStatus ==
                ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED
        )
    }
}

enum class ReaderHapticStyle { LIGHT, MEDIUM, HEAVY }

fun interface ReaderHapticsHost {
    fun perform(style: ReaderHapticStyle): Boolean
}

class AndroidReaderHapticsHost(private val context: Context) : ReaderHapticsHost {
    override fun perform(style: ReaderHapticStyle): Boolean {
        val vibrator = vibrator() ?: return false
        if (!vibrator.hasVibrator()) return false
        val effect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val predefined = when (style) {
                ReaderHapticStyle.LIGHT -> VibrationEffect.EFFECT_CLICK
                ReaderHapticStyle.MEDIUM -> VibrationEffect.EFFECT_DOUBLE_CLICK
                ReaderHapticStyle.HEAVY -> VibrationEffect.EFFECT_HEAVY_CLICK
            }
            VibrationEffect.createPredefined(predefined)
        } else {
            val (duration, amplitude) = when (style) {
                ReaderHapticStyle.LIGHT -> 20L to 64
                ReaderHapticStyle.MEDIUM -> 35L to 128
                ReaderHapticStyle.HEAVY -> 50L to 255
            }
            VibrationEffect.createOneShot(duration, amplitude)
        }
        vibrator.vibrate(effect)
        return true
    }

    private fun vibrator(): Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
            ?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
}

data class ReaderBackgroundTaskStartResult(val taskId: String)

interface ReaderBackgroundTaskHost {
    fun start(name: String): ReaderBackgroundTaskStartResult
    fun schedule(name: String, delayMs: Long): ReaderBackgroundTaskStartResult {
        if (delayMs != 0L) {
            throw ReaderUiHostCapabilityFailure(
                "NOT_SUPPORTED",
                "delayed background scheduling is not supported by this host"
            )
        }
        return start(name)
    }
    fun end(taskId: String): Boolean
}

class AndroidWorkManagerBackgroundTaskHost(context: Context) : ReaderBackgroundTaskHost {
    private val workManager = WorkManager.getInstance(context.applicationContext)

    override fun start(name: String): ReaderBackgroundTaskStartResult {
        return schedule(name, 0L)
    }

    override fun schedule(name: String, delayMs: Long): ReaderBackgroundTaskStartResult {
        if (delayMs < 0L) {
            throw ReaderUiHostCapabilityFailure("INVALID_PARAMS", "delayMs must be >= 0")
        }
        val builder = OneTimeWorkRequestBuilder<ReaderUiHostBackgroundWorker>()
            .setInputData(workDataOf(ReaderUiHostBackgroundWorker.KEY_NAME to name))
            .addTag("reader-ui-host-task")
            .addTag("reader-ui-host-task:$name")
        if (delayMs > 0L) builder.setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
        val request = builder.build()
        try {
            workManager.enqueueUniqueWork(
                "reader-ui-host-task:$name",
                ExistingWorkPolicy.REPLACE,
                request
            ).result.get(WORK_OPERATION_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (error: Exception) {
            throw ReaderUiHostCapabilityFailure(
                "EXECUTION_FAILED",
                "background.task.start could not enqueue WorkManager work: ${error.message}",
                retryable = true
            )
        }
        return ReaderBackgroundTaskStartResult(request.id.toString())
    }

    override fun end(taskId: String): Boolean {
        val id = try {
            UUID.fromString(taskId)
        } catch (_: IllegalArgumentException) {
            throw ReaderUiHostCapabilityFailure("INVALID_PARAMS", "taskId must be a UUID")
        }
        val workInfo = try {
            workManager.getWorkInfoById(id).get(WORK_OPERATION_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (error: Exception) {
            throw ReaderUiHostCapabilityFailure(
                "EXECUTION_FAILED",
                "background.task.end could not query WorkManager: ${error.message}",
                retryable = true
            )
        } ?: throw ReaderUiHostCapabilityFailure("NOT_FOUND", "background task not found")
        if (workInfo.state.isFinished) {
            throw ReaderUiHostCapabilityFailure("NOT_ACTIVE", "background task is already finished")
        }
        try {
            workManager.cancelWorkById(id).result
                .get(WORK_OPERATION_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (error: Exception) {
            throw ReaderUiHostCapabilityFailure(
                "EXECUTION_FAILED",
                "background.task.end could not cancel WorkManager work: ${error.message}",
                retryable = true
            )
        }
        return true
    }

    private companion object {
        const val WORK_OPERATION_TIMEOUT_MS = 5_000L
    }
}

class ReaderUiHostBackgroundWorker(
    appContext: Context,
    params: WorkerParameters
) : Worker(appContext, params) {
    override fun doWork(): Result {
        val name = inputData.getString(KEY_NAME)
        return if (name.isNullOrBlank()) Result.failure() else Result.success()
    }

    companion object { const val KEY_NAME = "name" }
}

data class ReaderWebDavConnectionOverride(
    val serverURL: String,
    val username: String,
    val password: String
)

data class ReaderWebDavConnectResult(
    val connected: Boolean,
    val statusCode: Int,
    val message: String
)

data class ReaderWebDavBackupResult(
    val backedUp: Boolean,
    val remoteURL: String,
    val statusCode: Int,
    val resourceCount: Int
)

data class ReaderWebDavRestoreResult(
    val restored: Boolean,
    val remoteURL: String,
    val statusCode: Int,
    val importedSources: Int
)

interface ReaderWebDav25Host {
    fun connect(
        scope: ReaderUiHostRequestScope,
        override: ReaderWebDavConnectionOverride?,
        legacyURL: String? = null
    ): ReaderWebDavConnectResult

    fun backup(
        scope: ReaderUiHostRequestScope,
        override: ReaderWebDavConnectionOverride?
    ): ReaderWebDavBackupResult

    fun restore(
        scope: ReaderUiHostRequestScope,
        remoteURL: String,
        override: ReaderWebDavConnectionOverride?
    ): ReaderWebDavRestoreResult
}

interface ReaderBackupDataSource {
    fun exportSnapshot(): String
    fun restoreSnapshot(snapshot: String): Int
}

/** 2.5 backup currently owns the durable book-source collection. */
class AndroidReaderBackupDataSource(
    private val bookSources: BookSourceRepository
) : ReaderBackupDataSource {
    override fun exportSnapshot(): String = JSONObject()
        .put("schemaVersion", 1)
        .put("createdAt", System.currentTimeMillis())
        .put("bookSources", JSONArray(bookSources.exportJson()))
        .toString()

    override fun restoreSnapshot(snapshot: String): Int {
        val root = try {
            JSONObject(snapshot)
        } catch (error: Exception) {
            throw ReaderUiHostCapabilityFailure(
                "INVALID_BACKUP",
                "WebDAV backup is not valid JSON: ${error.message}"
            )
        }
        if (root.optInt("schemaVersion", -1) != 1) {
            throw ReaderUiHostCapabilityFailure("INVALID_BACKUP", "unsupported backup schemaVersion")
        }
        val sourceArray = root.optJSONArray("bookSources")
            ?: throw ReaderUiHostCapabilityFailure("INVALID_BACKUP", "bookSources array missing")
        return bookSources.importJson(sourceArray.toString())
    }
}

/**
 * Real WebDAV executor. Stored credentials are reused by default; request
 * credentials are scoped to the exact Host operation and revoked in `finally`.
 */
class AndroidReaderWebDav25Host(
    private val credentials: WebDavCredentialStore,
    private val defaultClient: WebDavClient?,
    private val backupDataSource: ReaderBackupDataSource
) : ReaderWebDav25Host {
    override fun connect(
        scope: ReaderUiHostRequestScope,
        override: ReaderWebDavConnectionOverride?,
        legacyURL: String?
    ): ReaderWebDavConnectResult = withClient(scope, override) { client, baseURL ->
        val target = legacyURL ?: baseURL
        val response = runBlocking {
            client.execute(WebDavRequest(target, WebDavMethod.PROPFIND))
        }
        val connected = response.statusCode == 200 || response.statusCode == 207
        ReaderWebDavConnectResult(
            connected = connected,
            statusCode = response.statusCode,
            message = if (connected) "OK" else "WebDAV HTTP ${response.statusCode}"
        )
    }

    override fun backup(
        scope: ReaderUiHostRequestScope,
        override: ReaderWebDavConnectionOverride?
    ): ReaderWebDavBackupResult = withClient(scope, override) { client, baseURL ->
        val remoteURL = "${baseURL.trimEnd('/')}/ReaderBackup/ReaderAndroid/reader-backup-v1.json"
        val snapshot = backupDataSource.exportSnapshot()
        val response = runBlocking {
            client.execute(WebDavRequest(remoteURL, WebDavMethod.PUT, body = snapshot))
        }
        ReaderWebDavBackupResult(
            backedUp = response.statusCode in 200..299,
            remoteURL = remoteURL,
            statusCode = response.statusCode,
            resourceCount = 1
        )
    }

    override fun restore(
        scope: ReaderUiHostRequestScope,
        remoteURL: String,
        override: ReaderWebDavConnectionOverride?
    ): ReaderWebDavRestoreResult = withClient(scope, override) { client, _ ->
        val response = runBlocking {
            client.execute(WebDavRequest(remoteURL, WebDavMethod.GET))
        }
        if (response.statusCode !in 200..299 || response.body == null) {
            return@withClient ReaderWebDavRestoreResult(
                restored = false,
                remoteURL = remoteURL,
                statusCode = response.statusCode,
                importedSources = 0
            )
        }
        val imported = backupDataSource.restoreSnapshot(response.body)
        ReaderWebDavRestoreResult(true, remoteURL, response.statusCode, imported)
    }

    private fun <T> withClient(
        scope: ReaderUiHostRequestScope,
        override: ReaderWebDavConnectionOverride?,
        block: (WebDavClient, String) -> T
    ): T {
        if (override == null) {
            val stored = credentials.load(DEFAULT_CREDENTIAL_ID)
                ?: throw ReaderUiHostCapabilityFailure(
                    "NOT_CONFIGURED",
                    "WebDAV credential webdav.default is not configured"
                )
            val client = defaultClient ?: throw ReaderUiHostCapabilityFailure(
                "NOT_CONFIGURED",
                "WebDAV client is not configured"
            )
            return block(client, stored.serverUrl)
        }

        val scopedCredentialId = "reader-ui-2.5-${scope.operationId}-${scope.generation ?: 0L}"
        credentials.save(
            scopedCredentialId,
            WebDavCredential(
                serverUrl = override.serverURL,
                auth = AuthMethod.Basic(override.username, override.password)
            )
        )
        val client = AndroidWebDavClient(credentials, scopedCredentialId)
        return try {
            block(client, override.serverURL)
        } finally {
            credentials.revoke(scopedCredentialId)
        }
    }

    private companion object { const val DEFAULT_CREDENTIAL_ID = "webdav.default" }
}

/** Injectable service aggregate used by the 22 2.5 handlers. */
data class ReaderUi25HostServices(
    val activityHostProvider: () -> ReaderUiActivityCapabilityHost? =
        { ReaderUiActivityCapabilityBinding.currentHost() },
    val fontRegistration: ReaderFontRegistrationHost? = null,
    val networkStatus: ReaderNetworkStatusHost? = null,
    val haptics: ReaderHapticsHost? = null,
    val backgroundTasks: ReaderBackgroundTaskHost? = null,
    val webDav: ReaderWebDav25Host? = null
) {
    companion object {
        fun production(
            context: Context,
            credentials: WebDavCredentialStore,
            webDavClient: WebDavClient?,
            bookSources: BookSourceRepository
        ): ReaderUi25HostServices = ReaderUi25HostServices(
            fontRegistration = AndroidReaderFontRegistrationHost(context.applicationContext),
            networkStatus = AndroidReaderNetworkStatusHost(context.applicationContext),
            haptics = AndroidReaderHapticsHost(context.applicationContext),
            backgroundTasks = AndroidWorkManagerBackgroundTaskHost(context.applicationContext),
            webDav = AndroidReaderWebDav25Host(
                credentials = credentials,
                defaultClient = webDavClient,
                backupDataSource = AndroidReaderBackupDataSource(bookSources)
            )
        )
    }
}

internal fun validateHttpURL(value: String, field: String): String {
    val scheme = try { URI(value).scheme?.lowercase(Locale.ROOT) } catch (_: Exception) { null }
    if ((scheme != "https" && scheme != "http") || value.isBlank()) {
        throw ReaderUiHostCapabilityFailure("INVALID_PARAMS", "$field must be an http(s) URL")
    }
    return value
}
