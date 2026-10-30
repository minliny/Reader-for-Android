package io.reader.android.runtime

import java.security.MessageDigest

/** Core-owned manga source identity; image bytes and rendering remain Host-owned. */
data class ReaderMangaChapterRef(val sourceId: String, val bookId: String)

data class ReaderMangaPage(
    val url: String,
    val requestRule: String? = null,
    val referrer: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val sourcePageId: String? = null,
)

data class ReaderMangaPagesExtract(
    val chapter: ReaderMangaChapterRef,
    val chapterTitle: String,
    val pages: List<ReaderMangaPage>,
)

data class ReaderMangaExtractRequest(
    val chapter: ReaderMangaChapterRef,
    val chapterTitle: String = "",
    val chapterResponse: String = "",
    val chapterUrl: String? = null,
    val variables: Map<String, String> = emptyMap(),
)

data class ReaderMangaChapterIdentity(val sourceId: String, val bookId: String, val chapterId: String)

/** Manga uses image coordinates and an epoch/revision CAS, never text scalar offsets. */
data class ReaderMangaProgressToken(val epoch: Long, val revision: Long)
data class ReaderMangaLocation(
    val chapter: ReaderMangaChapterIdentity,
    val manifestVersion: String,
    val pageId: String,
    val pageOrdinalFallback: Int,
    val x: Double,
    val y: Double,
    val progressRevision: Long,
)
data class ReaderMangaProgressState(val token: ReaderMangaProgressToken, val location: ReaderMangaLocation?)
data class ReaderMangaProgressSaveRequest(
    val manifest: ReaderMangaManifest,
    val location: ReaderMangaLocation,
    val expected: ReaderMangaProgressToken,
)
data class ReaderMangaProgressPending(val operationId: String, val chapter: ReaderMangaChapterRef,
    val candidateRetained: Boolean, val hasOriginalTerminal: Boolean)
class ReaderMangaProgressUnconfirmed(val operationId: String, cause: Throwable? = null) :
    Exception("MANGA_PROGRESS_ORIGINAL_UNCONFIRMED: $operationId", cause)

enum class ReaderMangaReadPolicy(val wire: String) {
    CACHE_ONLY("cacheOnly"), CACHE_FIRST("cacheFirst"), REFRESH("refresh"),
}

data class ReaderMangaChapterRequest(
    val chapter: ReaderMangaChapterRef,
    val chapterIndex: Int,
    val policy: ReaderMangaReadPolicy = ReaderMangaReadPolicy.CACHE_FIRST,
    val expectedManifestVersion: String? = null,
    val preparationRevision: Long? = null,
)

data class ReaderMangaManifestPage(val pageId: String, val ordinal: Int, val resourceRef: String)

data class ReaderMangaManifest(
    val chapter: ReaderMangaChapterIdentity,
    val sourceRuleVersion: String,
    val manifestVersion: String,
    val decodeRevision: String,
    val pages: List<ReaderMangaManifestPage>,
)

data class ReaderMangaResource(val resourceRef: String, val request: ReaderMangaPage)

data class ReaderMangaChapterData(
    val manifest: ReaderMangaManifest,
    val chapterIndex: Int,
    val chapterTitle: String,
    val cached: Boolean,
    val resources: List<ReaderMangaResource>,
)

data class ReaderMangaPageWindow(
    val manifest: ReaderMangaManifest,
    val chapterIndex: Int,
    val chapterTitle: String,
    val cached: Boolean,
    val pageStart: Int,
    val totalPages: Int,
    val resources: List<ReaderMangaResource>,
    val resumeOnly: Boolean,
)

data class ReaderMangaPageWindowRequest(
    val chapter: ReaderMangaChapterIdentity,
    val manifestVersion: String,
    val start: Int,
    val limit: Int,
)

data class ReaderMangaResourcePrepareRequest(
    val chapter: ReaderMangaChapterIdentity,
    val manifestVersion: String,
    val resourceRef: String,
    val sourceRuleVersion: String,
    val transferId: String,
)

/** Compact Core-safe identity for a Host-owned encoded resource transfer. */
internal data class ReaderMangaTransferIdentity(val wireId: String, val resourceRef: String) {
    init {
        require(wireId.matches(Regex("rmt1:[0-9a-f]{64}"))) { "MANGA_TRANSFER_IDENTITY" }
        require(resourceRef.isNotBlank()) { "MANGA_RESOURCE_REF" }
    }

    companion object {
        fun forPrepare(request: ReaderMangaResourcePrepareRequest): ReaderMangaTransferIdentity {
            val original = request.transferId.trim()
            require(original.isNotEmpty()) { "MANGA_TRANSFER_ID" }
            val canonical = listOf(
                request.chapter.sourceId, request.chapter.bookId, request.chapter.chapterId,
                request.manifestVersion, request.resourceRef, request.sourceRuleVersion, original,
            ).joinToString("\u0000")
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(canonical.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
            return ReaderMangaTransferIdentity("rmt1:$digest", request.resourceRef)
        }

        fun decode(wireId: String, resourceRef: String): ReaderMangaTransferIdentity =
            ReaderMangaTransferIdentity(wireId, resourceRef)
    }
}

data class ReaderMangaResourcePrepareResult(
    val prepared: Boolean,
    val resourceRef: String,
    val transferId: String,
    val bytes: Int,
)

/**
 * A decoded page owned by the current manga reading owner. The lease keeps
 * the decoded file from being evicted until the UI releases this handle.
 */
class ReaderMangaDecodedResource internal constructor(
    val chapter: ReaderMangaChapterIdentity,
    val manifestVersion: String,
    val resourceRef: String,
    val transferId: String,
    bytes: ByteArray,
    private val lease: ReaderMangaResourceStore.DecodedLease,
) : AutoCloseable {
    val bytes: ByteArray = bytes.copyOf()

    private var closed = false

    override fun close() {
        if (!closed) {
            closed = true
            lease.close()
        }
    }
}

data class ReaderMangaResourceInspectRequest(val transferId: String, val sha256: String, val bytes: Int)

enum class ReaderMangaResourceStatus { ABSENT, PRESENT }
enum class ReaderMangaImageFormat { JPEG, PNG, WEBP }
enum class ReaderMangaAllocationClass { SCANLINE, PNG_INTERLACED, FULL_FRAME }

data class ReaderMangaImageDecodeProfile(
    val format: ReaderMangaImageFormat,
    val encodedWidth: Long?,
    val encodedHeight: Long?,
    val allocationClass: ReaderMangaAllocationClass,
)

data class ReaderMangaResourceInspectResult(
    val transferId: String,
    val sha256: String,
    val bytes: Int,
    val status: ReaderMangaResourceStatus,
    val orientation: Int?,
    val decodeProfile: ReaderMangaImageDecodeProfile,
)

enum class ReaderMangaMaterializationIntent(val wire: String) { CACHE("cache"), DOWNLOAD("download") }

data class ReaderMangaMaterializationPrepareRequest(
    val chapter: ReaderMangaChapterIdentity,
    val manifestVersion: String,
    val intent: ReaderMangaMaterializationIntent,
)

data class ReaderMangaMaterializationPrepareResult(
    val token: String,
    val chapter: ReaderMangaChapterIdentity,
    val manifestVersion: String,
    val intent: ReaderMangaMaterializationIntent,
)

data class ReaderMangaMaterializationCompleteRequest(
    val chapter: ReaderMangaChapterIdentity,
    val manifestVersion: String,
    val token: String,
    val resourceRefs: List<String>,
)

data class ReaderMangaMaterializationCompleteResult(val completed: Boolean, val manifestVersion: String)
data class ReaderMangaMaterializationAbortResult(val aborted: Boolean)
