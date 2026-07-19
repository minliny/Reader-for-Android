package com.reader.android.data.adapter

import android.content.ContentResolver
import android.net.Uri
import com.reader.api.ReaderCoreClient
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * A file selected by Android's Storage Access Framework.
 *
 * The URI is intentionally opaque. It is consumed only by [LocalBookDocumentReader]
 * and is never persisted in Reader UI state or forwarded to Core. Core receives the
 * original bytes through its canonical `bytesBase64` field.
 */
data class LocalBookSelectedDocument(
    val uri: String,
    val displayName: String,
    val mimeType: String? = null,
    val declaredSize: Long? = null
)

enum class LocalBookImportFailureCode {
    INVALID_SELECTION,
    UNSUPPORTED_FORMAT,
    FORMAT_CONFLICT,
    EMPTY_FILE,
    FILE_TOO_LARGE,
    PERMISSION_DENIED,
    READ_FAILED,
    CORE_UNAVAILABLE,
    CORE_REJECTED,
    CORE_PROTOCOL_MISMATCH,
    FORMAT_RENDERER_UNAVAILABLE
}

data class LocalBookImportFailure(
    val code: LocalBookImportFailureCode,
    val message: String,
    val retryable: Boolean = false
)

sealed interface LocalBookReaderAdmission {
    /** The existing canonical text reader may open a TXT import. */
    data object Text : LocalBookReaderAdmission

    /**
     * Non-TXT formats stay blocked until a format-specific renderer + locator
     * protocol is present. This is an explicit Slice 9 fail-closed boundary;
     * EPUB/PDF/MOBI/UMD must never be silently routed to ReadingTextFlow.
     */
    data class Blocked(val failure: LocalBookImportFailure) : LocalBookReaderAdmission
}

data class LocalBookImportSuccess(
    val bookId: String,
    val title: String,
    val format: LocalBookFormat,
    val encoding: String,
    val byteLength: Long,
    val characterLength: Long,
    val chapterCount: Int,
    val readerAdmission: LocalBookReaderAdmission
)

sealed interface LocalBookImportOutcome {
    data class Imported(val value: LocalBookImportSuccess) : LocalBookImportOutcome
    data class Failed(val failure: LocalBookImportFailure) : LocalBookImportOutcome
}

data class LocalBookScanResult(
    val document: LocalBookSelectedDocument,
    val format: LocalBookFormat?,
    val failure: LocalBookImportFailure? = null
) {
    val isCandidate: Boolean get() = format != null && failure == null
}

/** Host-owned byte access. Tests inject a pure in-memory reader. */
fun interface LocalBookDocumentReader {
    @Throws(IOException::class, SecurityException::class)
    suspend fun read(document: LocalBookSelectedDocument, maxBytes: Long): ByteArray
}

/** Thin Core command seam so JVM tests do not load JNI. */
fun interface LocalBookCoreCommandClient {
    suspend fun send(method: String, params: JSONObject): JSONObject
}

class ReaderCoreLocalBookCommandClient(
    private val coreProvider: () -> ReaderCoreClient = ReaderCoreClient::get
) : LocalBookCoreCommandClient {
    override suspend fun send(method: String, params: JSONObject): JSONObject =
        coreProvider().sendAndAwait(method, params)
}

/**
 * Production SAF reader. Only `content://` documents selected through the
 * foreground picker are admitted. Raw filesystem paths and `file://` URIs are
 * rejected so imports cannot escape Android's document-provider boundary.
 */
class ContentResolverLocalBookDocumentReader(
    private val contentResolver: ContentResolver
) : LocalBookDocumentReader {
    override suspend fun read(document: LocalBookSelectedDocument, maxBytes: Long): ByteArray =
        withContext(Dispatchers.IO) {
            val uri = Uri.parse(document.uri)
            if (uri.scheme != ContentResolver.SCHEME_CONTENT) {
                throw SecurityException("local import requires a content URI")
            }
            val declaredSize = document.declaredSize
            if (declaredSize != null && declaredSize > maxBytes) {
                throw LocalBookFileTooLargeException(maxBytes)
            }
            val input = contentResolver.openInputStream(uri)
                ?: throw IOException("document provider returned no stream")
            input.use { stream ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0L
                while (true) {
                    ensureActive()
                    val read = stream.read(buffer)
                    if (read < 0) break
                    if (read == 0) continue
                    total += read
                    if (total > maxBytes) throw LocalBookFileTooLargeException(maxBytes)
                    output.write(buffer, 0, read)
                }
                output.toByteArray()
            }
        }
}

private class LocalBookFileTooLargeException(maxBytes: Long) :
    IOException("local book exceeds the $maxBytes byte import limit")

private class LocalBookContractException(message: String) : IllegalStateException(message)

/**
 * Slice 9 Android import boundary.
 *
 * Responsibilities intentionally stay narrow:
 * - validate SAF selections and supported five-format hints;
 * - read the exact bytes under a bounded Host operation;
 * - call the real Core-owned `local_book.import` command;
 * - validate the returned canonical format/identity/TOC summary;
 * - keep unavailable format renderers explicitly blocked.
 *
 * It does not parse metadata, chapters or locators locally and does not invent
 * download/media/audio DTOs that are still absent from the Reader UI contract.
 */
class CoreLocalBookImportService(
    private val documentReader: LocalBookDocumentReader,
    private val core: LocalBookCoreCommandClient,
    private val maxImportBytes: Long = DEFAULT_MAX_IMPORT_BYTES
) {
    init {
        require(maxImportBytes > 0) { "maxImportBytes must be positive" }
    }

    fun scan(documents: List<LocalBookSelectedDocument>): List<LocalBookScanResult> =
        documents.map(::scanOne)

    suspend fun import(document: LocalBookSelectedDocument): LocalBookImportOutcome {
        val candidate = scanOne(document)
        candidate.failure?.let { return LocalBookImportOutcome.Failed(it) }
        val expectedFormat = requireNotNull(candidate.format)

        val bytes = try {
            documentReader.read(document, maxImportBytes)
        } catch (error: CancellationException) {
            throw error
        } catch (error: LocalBookFileTooLargeException) {
            return failed(
                LocalBookImportFailureCode.FILE_TOO_LARGE,
                "文件超过当前导入上限",
                retryable = false
            )
        } catch (error: SecurityException) {
            return failed(
                LocalBookImportFailureCode.PERMISSION_DENIED,
                "无法读取所选文件，请重新授权",
                retryable = true
            )
        } catch (error: IOException) {
            return failed(
                LocalBookImportFailureCode.READ_FAILED,
                "读取所选文件失败",
                retryable = true
            )
        }
        if (bytes.isEmpty()) {
            return failed(LocalBookImportFailureCode.EMPTY_FILE, "所选文件为空")
        }
        if (bytes.size.toLong() > maxImportBytes) {
            return failed(LocalBookImportFailureCode.FILE_TOO_LARGE, "文件超过当前导入上限")
        }

        val bookId = canonicalBookId(bytes)
        val params = JSONObject()
            .put("bookId", bookId)
            .put("fileName", document.displayName)
            .put("bytesBase64", Base64.getEncoder().encodeToString(bytes))
            .put("format", expectedFormat.coreWireName())

        val result = try {
            core.send(LOCAL_BOOK_IMPORT_METHOD, params)
        } catch (error: CancellationException) {
            throw error
        } catch (error: IllegalStateException) {
            return failed(
                LocalBookImportFailureCode.CORE_UNAVAILABLE,
                "Reader Core 尚未就绪",
                retryable = true
            )
        } catch (error: Exception) {
            return failed(
                LocalBookImportFailureCode.CORE_REJECTED,
                "Reader Core 拒绝导入该文件",
                retryable = false
            )
        }

        return try {
            LocalBookImportOutcome.Imported(validateResult(result, expectedFormat, bookId))
        } catch (error: LocalBookContractException) {
            failed(
                LocalBookImportFailureCode.CORE_PROTOCOL_MISMATCH,
                "Reader Core 返回了不完整的本地书结果"
            )
        }
    }

    private fun scanOne(document: LocalBookSelectedDocument): LocalBookScanResult {
        if (document.uri.isBlank() || document.displayName.isBlank()) {
            return rejected(
                document,
                LocalBookImportFailureCode.INVALID_SELECTION,
                "文件选择结果不完整"
            )
        }
        if (document.declaredSize != null && document.declaredSize < 0) {
            return rejected(
                document,
                LocalBookImportFailureCode.INVALID_SELECTION,
                "文件大小无效"
            )
        }
        if (document.declaredSize != null && document.declaredSize > maxImportBytes) {
            return rejected(
                document,
                LocalBookImportFailureCode.FILE_TOO_LARGE,
                "文件超过当前导入上限"
            )
        }

        val extensionFormat = formatFromExtension(document.displayName)
        val mimeFormat = formatFromMimeType(document.mimeType)
        if (extensionFormat != null && mimeFormat != null && extensionFormat != mimeFormat) {
            return rejected(
                document,
                LocalBookImportFailureCode.FORMAT_CONFLICT,
                "文件扩展名与 MIME 类型冲突"
            )
        }
        val format = extensionFormat ?: mimeFormat
        return if (format == null) {
            rejected(
                document,
                LocalBookImportFailureCode.UNSUPPORTED_FORMAT,
                "仅支持 TXT、EPUB、PDF、MOBI、UMD"
            )
        } else {
            LocalBookScanResult(document, format)
        }
    }

    private fun validateResult(
        result: JSONObject,
        expectedFormat: LocalBookFormat,
        expectedBookId: String
    ): LocalBookImportSuccess {
        val actualFormat = formatFromCoreWireName(result.requiredNonBlankString("format"))
            ?: throw LocalBookContractException("unknown Core format")
        if (actualFormat != expectedFormat) {
            throw LocalBookContractException("Core format differs from selected format")
        }
        val book = result.optJSONObject("book")
            ?: throw LocalBookContractException("missing book")
        val actualBookId = book.requiredNonBlankString("bookId")
        if (actualBookId != expectedBookId) {
            throw LocalBookContractException("Core bookId drift")
        }
        val chapterCount = result.optInt("chapterCount", -1)
        if (chapterCount <= 0) throw LocalBookContractException("empty chapter list")
        val toc = result.optJSONArray("toc")
            ?: throw LocalBookContractException("missing toc")
        if (toc.length() != chapterCount) {
            throw LocalBookContractException("toc count drift")
        }
        val byteLength = result.optLong("byteLen", -1L)
        val characterLength = result.optLong("charLen", -1L)
        if (byteLength <= 0L || characterLength < 0L) {
            throw LocalBookContractException("invalid length summary")
        }
        return LocalBookImportSuccess(
            bookId = actualBookId,
            title = book.requiredNonBlankString("title"),
            format = actualFormat,
            encoding = result.requiredNonBlankString("encoding"),
            byteLength = byteLength,
            characterLength = characterLength,
            chapterCount = chapterCount,
            readerAdmission = readerAdmission(actualFormat)
        )
    }

    companion object {
        const val LOCAL_BOOK_IMPORT_METHOD = "local_book.import"
        const val DEFAULT_MAX_IMPORT_BYTES: Long = 256L * 1024L * 1024L

        fun readerAdmission(format: LocalBookFormat): LocalBookReaderAdmission = when (format) {
            LocalBookFormat.TXT -> LocalBookReaderAdmission.Text
            LocalBookFormat.EPUB,
            LocalBookFormat.PDF,
            LocalBookFormat.MOBI,
            LocalBookFormat.UMD -> LocalBookReaderAdmission.Blocked(
                LocalBookImportFailure(
                    LocalBookImportFailureCode.FORMAT_RENDERER_UNAVAILABLE,
                    "${format.name} 专属阅读器与 locator 合同尚未完成"
                )
            )
            LocalBookFormat.UNKNOWN -> LocalBookReaderAdmission.Blocked(
                LocalBookImportFailure(
                    LocalBookImportFailureCode.UNSUPPORTED_FORMAT,
                    "未知格式不能进入阅读器"
                )
            )
        }

        fun supportedMimeTypes(): List<String> = listOf(
            "text/plain",
            "application/epub+zip",
            "application/pdf",
            "application/x-mobipocket-ebook",
            "application/x-umd"
        )

        private fun canonicalBookId(bytes: ByteArray): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            return "local-" + digest.take(16).joinToString("") { "%02x".format(it) }
        }

        private fun formatFromExtension(fileName: String): LocalBookFormat? = when (
            fileName.substringAfterLast('.', "").lowercase()
        ) {
            "txt" -> LocalBookFormat.TXT
            "epub" -> LocalBookFormat.EPUB
            "pdf" -> LocalBookFormat.PDF
            "mobi" -> LocalBookFormat.MOBI
            "umd" -> LocalBookFormat.UMD
            else -> null
        }

        private fun formatFromMimeType(mimeType: String?): LocalBookFormat? = when (
            mimeType?.substringBefore(';')?.trim()?.lowercase()
        ) {
            "text/plain", "application/txt" -> LocalBookFormat.TXT
            "application/epub+zip" -> LocalBookFormat.EPUB
            "application/pdf" -> LocalBookFormat.PDF
            "application/x-mobipocket-ebook", "application/vnd.amazon.ebook" -> LocalBookFormat.MOBI
            "application/x-umd", "application/vnd.umd" -> LocalBookFormat.UMD
            else -> null
        }

        private fun formatFromCoreWireName(value: String): LocalBookFormat? = when (value.lowercase()) {
            "txt", "html" -> LocalBookFormat.TXT
            "epub" -> LocalBookFormat.EPUB
            "pdf" -> LocalBookFormat.PDF
            "mobi", "azw", "azw3" -> LocalBookFormat.MOBI
            "umd" -> LocalBookFormat.UMD
            else -> null
        }

        private fun LocalBookFormat.coreWireName(): String = when (this) {
            LocalBookFormat.TXT -> "txt"
            LocalBookFormat.EPUB -> "epub"
            LocalBookFormat.PDF -> "pdf"
            LocalBookFormat.MOBI -> "mobi"
            LocalBookFormat.UMD -> "umd"
            LocalBookFormat.UNKNOWN -> throw IllegalArgumentException("unknown local book format")
        }

        private fun JSONObject.requiredNonBlankString(key: String): String {
            val value = optString(key, "")
            if (value.isBlank()) throw LocalBookContractException("missing $key")
            return value
        }

        private fun rejected(
            document: LocalBookSelectedDocument,
            code: LocalBookImportFailureCode,
            message: String
        ): LocalBookScanResult = LocalBookScanResult(
            document = document,
            format = null,
            failure = LocalBookImportFailure(code, message)
        )

        private fun failed(
            code: LocalBookImportFailureCode,
            message: String,
            retryable: Boolean = false
        ): LocalBookImportOutcome.Failed = LocalBookImportOutcome.Failed(
            LocalBookImportFailure(code, message, retryable)
        )
    }
}
