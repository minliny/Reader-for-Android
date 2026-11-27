package io.reader.rebuild.core

import com.reader.core.ReaderCoreRuntime

/** The queued Host request owns this restricted native asset context. */
internal class CoreMangaAssets(
    private val runtime: ReaderCoreRuntime,
    val request: RequestIdentity,
    val operationId: Long,
) {
    fun begin(bytes: Long): Long {
        require(bytes in 1..ASSET_LIMIT)
        return runtime.assetBegin(request.requestId, operationId, bytes)
    }

    fun write(assetId: Long, bytes: ByteArray): Long {
        require(bytes.size in 1..CHUNK_LIMIT)
        return runtime.assetWrite(request.requestId, operationId, assetId, bytes)
    }

    fun commit(assetId: Long): Long = runtime.assetCommit(request.requestId, operationId, assetId)

    fun read(assetId: Long, offset: Long, limit: Int): ByteArray = read(operationId, assetId, offset, limit)

    /** Output stages retain the asset under the input-stage operation id. */
    fun read(assetOperationId: Long, assetId: Long, offset: Long, limit: Int): ByteArray {
        require(offset in 0..ASSET_LIMIT && limit in 1..CHUNK_LIMIT)
        return runtime.assetReadChunk(request.requestId, assetOperationId, assetId, offset, limit)
    }

    fun release(assetId: Long) = release(operationId, assetId)

    /** Output stages release the asset under the input-stage operation id. */
    fun release(assetOperationId: Long, assetId: Long) =
        runtime.assetRelease(request.requestId, assetOperationId, assetId)

    companion object {
        const val CHUNK_LIMIT = 1024 * 1024
        const val ASSET_LIMIT = 128L * CHUNK_LIMIT
    }
}
