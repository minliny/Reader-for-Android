package io.reader.android.runtime

import io.reader.android.runtime.ebookdraft.EbookLeaseGuard
import io.reader.android.runtime.ebookdraft.EbookPresentation
import io.reader.android.runtime.ebookdraft.EbookReadOnlyCommands
import io.reader.android.runtime.ebookdraft.EbookRenderLease
import io.reader.android.runtime.ebookdraft.ReaderEbookAssetProvider
import io.reader.android.runtime.ebookdraft.ReaderEbookPresentationReader
import org.json.JSONObject

/** One existing client transport on its actual held App-owner lane, not another actor/runtime. */
internal class ReaderEbookOwnerCommands(private val transport: CoreTransport) : EbookReadOnlyCommands {
    override val runtimeSession get() = transport.session
    override fun request(method: String, parameters: JSONObject): JSONObject {
        check(method in setOf("local_book.presentation.kind.v1", "local_book.chapter.presentation.v1"))
        return transport.request(method, parameters)
    }
}

internal data class ReaderEbookPreparedPublication(
    val lease: EbookRenderLease,
    val presentation: EbookPresentation,
    val provider: ReaderEbookAssetProvider,
) {
    fun retire() { provider.retire() }
}

/** Called inside the Library's original onIO turn AFTER activeChapter(bookId, sessionToken).
 * The caller's sourceBytes must be bound to its retained original import receipt.
 * This does not add an import path or make a WebView/DOM-ready claim. */
internal class ReaderEbookOwnerPreparation(
    private val client: AppCoreOwner.Client,
    private val guard: EbookLeaseGuard,
) {
    fun prepare(lease: EbookRenderLease, chapter: ReaderChapter, sourceBytes: Long, sourceFormat: String): ReaderEbookPreparedPublication {
        check(chapter.bookId == lease.bookId && chapter.index == lease.chapterIndex &&
            chapter.bodyVersion == lease.bodyVersion && chapter.processingVersion == lease.processingVersion)
        val reader = ReaderEbookPresentationReader(ReaderEbookOwnerCommands(client.transport), guard)
        val kind = reader.kind(lease)
        check(sourceFormat in setOf("epub", "mobi") && kind.format == sourceFormat && kind.kind == "ebook" && !kind.reimportRequired) { "EBOOK_ORIGINAL_REIMPORT_REQUIRED_OR_NOT_EBOOK" }
        val owned = client.authorizeEbookAssets(lease, sourceBytes, guard)
        try {
            val presentation = reader.read(lease, kind, chapter, owned.original)
            return ReaderEbookPreparedPublication(lease, presentation,
                ReaderEbookAssetProvider(lease, owned.original, guard, owned.binary))
        } catch (error: Throwable) {
            owned.binary.retire()
            throw error
        }
    }
}
