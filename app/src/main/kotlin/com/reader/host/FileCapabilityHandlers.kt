package com.reader.host

import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.nio.file.AccessDeniedException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.charset.Charset
import java.util.Base64

/**
 * Host-owned filesystem abstraction. Default impl works against the real
 * [java.io.File] / [java.nio.file.Files] API; JVM tests inject an in-memory
 * double ([InMemoryHostFileSystem]) so they don't touch disk.
 *
 * All paths are resolved relative to the host's sandbox root (provided at
 * construction). Path traversal outside the root is rejected with
 * [SecurityException] so Core cannot escape the host's data directory.
 */
interface HostFileSystem {
    fun read(path: String): ByteArray
    fun write(path: String, content: ByteArray, append: Boolean = false): Long
    fun delete(path: String): Boolean
    fun exists(path: String): Boolean
}

/**
 * Validates that [path] stays inside [root]. Resolves the path canonically
 * and rejects parent-directory traversal (`..`) that would escape the sandbox.
 * Returns the resolved absolute [File] on success.
 */
internal fun resolveSafely(root: File, path: String): File {
    if (path.isBlank()) {
        throw IllegalArgumentException("path must be non-blank")
    }
    val resolved = File(root, path).canonicalFile
    val canonicalRoot = root.canonicalFile
    if (!resolved.path.startsWith(canonicalRoot.path)) {
        throw SecurityException("path escapes host sandbox: $path")
    }
    return resolved
}

/**
 * In-memory [HostFileSystem] double for JVM tests. No disk I/O; path
 * traversal is still enforced so the security contract is tested. Uses
 * the raw [path] string as the storage key (no canonical resolution) so
 * it works identically on macOS/Linux/Windows CI without depending on
 * `File.canonicalFile` behaviour for non-existent paths.
 */
class InMemoryHostFileSystem : HostFileSystem {
    private val files = mutableMapOf<String, ByteArray>()
    private val rootPath: String = "/test-sandbox"

    private fun validate(path: String): String {
        if (path.isBlank()) throw IllegalArgumentException("path must be non-blank")
        // Reject `..` segments that would escape the sandbox.
        val normalized = path.replace("\\", "/").trimStart('/')
        if (normalized.contains("..")) {
            throw SecurityException("path escapes host sandbox: $path")
        }
        return "$rootPath/$normalized"
    }

    override fun read(path: String): ByteArray {
        val key = validate(path)
        return files[key] ?: throw NoSuchFileException(key)
    }

    override fun write(path: String, content: ByteArray, append: Boolean): Long {
        val key = validate(path)
        if (append && files.containsKey(key)) {
            val existing = files[key]!!
            files[key] = existing + content
            return content.size.toLong()
        }
        files[key] = content
        return content.size.toLong()
    }

    override fun delete(path: String): Boolean {
        val key = validate(path)
        return files.remove(key) != null
    }

    override fun exists(path: String): Boolean {
        val key = validate(path)
        return files.containsKey(key)
    }
}

/**
 * Production [HostFileSystem] backed by [java.nio.file.Files]. All operations
 * are scoped under [root]; paths outside the root are rejected.
 *
 * On Android, [root] is typically `context.filesDir` (app-private storage).
 * Tests inject an in-memory double.
 */
class DefaultHostFileSystem(private val root: File) : HostFileSystem {

    override fun read(path: String): ByteArray {
        val file = resolveSafely(root, path)
        if (!file.exists()) {
            throw NoSuchFileException(file)
        }
        if (file.isDirectory) {
            throw IOException("path is a directory: $path")
        }
        return Files.readAllBytes(file.toPath())
    }

    override fun write(path: String, content: ByteArray, append: Boolean): Long {
        val file = resolveSafely(root, path)
        if (file.isDirectory) {
            throw IOException("path is a directory: $path")
        }
        file.parentFile?.mkdirs()
        if (append) {
            Files.write(
                file.toPath(),
                content,
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.APPEND
            )
        } else {
            Files.write(
                file.toPath(),
                content,
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
            )
        }
        return content.size.toLong()
    }

    override fun delete(path: String): Boolean {
        val file = resolveSafely(root, path)
        if (!file.exists()) return false
        if (file.isDirectory) {
            throw IOException("cannot delete directory: $path")
        }
        return file.delete()
    }

    override fun exists(path: String): Boolean = resolveSafely(root, path).exists()
}

/**
 * `file.read` capability handler. Core sends
 * `{path, encoding?, byteOffset?, maxBytes?}`; returns `host.complete` with
 * `{content|contentBase64, encoding?, byteLength}`. Path traversal outside the
 * sandbox is rejected with a non-retryable `SECURITY` error; missing
 * files with a non-retryable `NOT_FOUND`.
 */
class FileReadHandler(
    private val fs: HostFileSystem
) : CapabilityHandler {

    override fun handle(request: HostRequest): HostReply {
        val params: JSONObject
        try {
            params = JSONObject(request.paramsJson())
        } catch (e: Exception) {
            return HostReply.error(INTERNAL, "invalid $CAPABILITY params: ${e.message}", false)
        }
        val path = params.optString("path", "")
        if (path.isEmpty()) {
            return HostReply.error(INTERNAL, "$CAPABILITY requires non-blank path", false)
        }
        val content: ByteArray
        try {
            content = fs.read(path)
        } catch (e: NoSuchFileException) {
            return HostReply.error(NOT_FOUND, "$CAPABILITY file not found: $path", false)
        } catch (e: SecurityException) {
            return HostReply.error(SECURITY, e.message ?: "path rejected", false)
        } catch (e: IOException) {
            return HostReply.error(INTERNAL, "$CAPABILITY read failed: ${e.message}", true)
        }
        val byteOffset = params.optLong("byteOffset", 0L).coerceAtLeast(0L)
        val maxBytes = if (params.has("maxBytes") && !params.isNull("maxBytes")) {
            params.optLong("maxBytes", 0L).takeIf { it > 0 }
        } else {
            null
        }
        val start = byteOffset.coerceAtMost(content.size.toLong()).toInt()
        val endExclusive = maxBytes
            ?.let { (start.toLong() + it).coerceAtMost(content.size.toLong()).toInt() }
            ?: content.size
        val slice = content.copyOfRange(start, endExclusive)
        val encoding = params.optString("encoding", "").takeIf { it.isNotBlank() }
        val result = JSONObject()
        if (encoding == "binary" || encoding == "base64") {
            result.put("contentBase64", Base64.getEncoder().encodeToString(slice))
        } else {
            val charset = Charset.forName(encoding ?: Charsets.UTF_8.name())
            result.put("content", slice.toString(charset))
        }
        encoding?.let { result.put("encoding", it) }
        result.put("byteLength", slice.size)
        return HostReply.complete(result.toString())
    }

    companion object {
        const val CAPABILITY = "file.read"
        private const val INTERNAL = "INTERNAL"
        private const val NOT_FOUND = "NOT_FOUND"
        private const val SECURITY = "SECURITY"
    }
}

/**
 * `file.write` capability handler. Core sends
 * `{path, content|contentBase64, encoding?, createDirectories?, append?}`;
 * returns `host.complete` with `{written: true, byteLength}`.
 */
class FileWriteHandler(
    private val fs: HostFileSystem
) : CapabilityHandler {

    override fun handle(request: HostRequest): HostReply {
        val params: JSONObject
        try {
            params = JSONObject(request.paramsJson())
        } catch (e: Exception) {
            return HostReply.error(INTERNAL, "invalid $CAPABILITY params: ${e.message}", false)
        }
        val path = params.optString("path", "")
        if (path.isEmpty()) {
            return HostReply.error(INTERNAL, "$CAPABILITY requires non-blank path", false)
        }
        val content = when {
            params.has("content") && !params.isNull("content") -> {
                val encoding = params.optString("encoding", "").takeIf { it.isNotBlank() }
                params.getString("content").toByteArray(Charset.forName(encoding ?: Charsets.UTF_8.name()))
            }
            params.has("contentBase64") && !params.isNull("contentBase64") -> {
                Base64.getDecoder().decode(params.getString("contentBase64"))
            }
            else -> {
                return HostReply.error(INTERNAL, "$CAPABILITY requires content or contentBase64", false)
            }
        }
        val append = params.optBoolean("append", false)
        val written: Long
        try {
            written = fs.write(path, content, append)
        } catch (e: SecurityException) {
            return HostReply.error(SECURITY, e.message ?: "path rejected", false)
        } catch (e: IOException) {
            return HostReply.error(INTERNAL, "$CAPABILITY write failed: ${e.message}", true)
        }
        val result = JSONObject()
        result.put("written", true)
        result.put("byteLength", written)
        return HostReply.complete(result.toString())
    }

    companion object {
        const val CAPABILITY = "file.write"
        private const val INTERNAL = "INTERNAL"
        private const val SECURITY = "SECURITY"
    }
}

/**
 * `file.delete` capability handler. Core sends `{path}`; returns
 * `host.complete` with `{deleted: <bool>}` (false if the file did not
 * exist).
 */
class FileDeleteHandler(
    private val fs: HostFileSystem
) : CapabilityHandler {

    override fun handle(request: HostRequest): HostReply {
        val params: JSONObject
        try {
            params = JSONObject(request.paramsJson())
        } catch (e: Exception) {
            return HostReply.error(INTERNAL, "invalid $CAPABILITY params: ${e.message}", false)
        }
        val path = params.optString("path", "")
        if (path.isEmpty()) {
            return HostReply.error(INTERNAL, "$CAPABILITY requires non-blank path", false)
        }
        val deleted: Boolean
        try {
            deleted = fs.delete(path)
        } catch (e: SecurityException) {
            return HostReply.error(SECURITY, e.message ?: "path rejected", false)
        } catch (e: IOException) {
            return HostReply.error(INTERNAL, "$CAPABILITY delete failed: ${e.message}", true)
        }
        val result = JSONObject()
        result.put("deleted", deleted)
        return HostReply.complete(result.toString())
    }

    companion object {
        const val CAPABILITY = "file.delete"
        private const val INTERNAL = "INTERNAL"
        private const val SECURITY = "SECURITY"
    }
}
