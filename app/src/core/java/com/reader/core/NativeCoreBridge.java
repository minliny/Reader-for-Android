package com.reader.core;

import java.nio.charset.StandardCharsets;

public final class NativeCoreBridge {
    /** Core-owned response asset chunk bound; mirrors the fixed C ABI. */
    public static final int MAX_ASSET_CHUNK_BYTES = 1024 * 1024;
    /** Core-owned response asset total bound; mirrors the fixed C ABI. */
    public static final long MAX_ASSET_TOTAL_BYTES = 128L * 1024 * 1024;

    static {
        System.loadLibrary("reader_core_jni");
    }

    private NativeCoreBridge() {
    }

    public static int abiVersion() {
        return nativeAbiVersion();
    }

    public static ReaderCoreLastError lastError() {
        return nativeLastError();
    }

    /** Bit 0 advertises Core's explicit JSON delivery acknowledgement contract. */
    public static int deliveryCapabilities() {
        return nativeDeliveryCapabilities();
    }

    /**
     * Copies one bounded binary resource from a Host-authorized private original.
     * The caller owns authorization and passes Core's canonical entry unchanged;
     * this wrapper grants no file access and performs no archive parsing.
     *
     * @param maximumBytes positive decompressed byte bound, at most 32 MiB
     * @throws ReaderCoreAssetException when the binary-asset C ABI rejects the read
     */
    public static byte[] readEpubEntry(
            String authorizedArchivePath, String canonicalEntry, long maximumBytes) {
        if (maximumBytes < 1 || maximumBytes > 32L * 1024 * 1024) {
            throw new IllegalArgumentException("maximumBytes must be in 1..32 MiB");
        }
        byte[] archive = assetPathUtf8(authorizedArchivePath, "authorizedArchivePath");
        if (!authorizedArchivePath.startsWith("/")) {
            throw new IllegalArgumentException("authorizedArchivePath must be absolute");
        }
        byte[] entry = assetPathUtf8(canonicalEntry, "canonicalEntry");
        return nativeReadEpubEntry(archive, entry, maximumBytes);
    }

    private static byte[] assetPathUtf8(String value, String field) {
        if (value == null) {
            throw new NullPointerException(field);
        }
        if (value.isEmpty() || value.indexOf('\0') >= 0
                || !StandardCharsets.UTF_8.newEncoder().canEncode(value)) {
            throw new IllegalArgumentException(field + " must be non-empty UTF-8 without NUL");
        }
        return value.getBytes(StandardCharsets.UTF_8);
    }

    public static native String pingSmoke();

    public static native long runtimeCreate(String configJson, Object listener);

    public static native int runtimeSend(long handle, String commandJson);

    public static native int runtimeCancel(long handle, long requestId);

    public static native void runtimeDestroy(long handle);

    /** Begin a Core-owned response asset for one request/operation owner. */
    public static long assetBegin(long handle, long requestId, long operationId, long declaredBytes) {
        requirePositive(requestId, "requestId");
        requirePositive(operationId, "operationId");
        requireDeclaredBytes(declaredBytes);
        return nativeAssetBegin(handle, requestId, operationId, declaredBytes);
    }

    /** Append at most one 1 MiB chunk; the return value is cumulative bytes written. */
    public static long assetWrite(long handle, long requestId, long operationId,
            long assetId, byte[] bytes) {
        requirePositive(requestId, "requestId");
        requirePositive(operationId, "operationId");
        requirePositive(assetId, "assetId");
        if (bytes == null) {
            throw new NullPointerException("bytes");
        }
        if (bytes.length > MAX_ASSET_CHUNK_BYTES) {
            throw new IllegalArgumentException("asset chunk must be at most 1 MiB");
        }
        return nativeAssetWrite(handle, requestId, operationId, assetId, bytes);
    }

    /** Seal a response asset and return its committed byte count. */
    public static long assetCommit(long handle, long requestId, long operationId, long assetId) {
        requirePositive(requestId, "requestId");
        requirePositive(operationId, "operationId");
        requirePositive(assetId, "assetId");
        return nativeAssetCommit(handle, requestId, operationId, assetId);
    }

    /** Read one committed response asset chunk; EOF is returned as an empty array. */
    public static byte[] assetReadChunk(long handle, long requestId, long operationId,
            long assetId, long offset, int limit) {
        requirePositive(requestId, "requestId");
        requirePositive(operationId, "operationId");
        requirePositive(assetId, "assetId");
        if (offset < 0) {
            throw new IllegalArgumentException("offset must be non-negative");
        }
        if (limit < 1 || limit > MAX_ASSET_CHUNK_BYTES) {
            throw new IllegalArgumentException("asset read limit must be in 1..1 MiB");
        }
        return nativeAssetReadChunk(handle, requestId, operationId, assetId, offset, limit);
    }

    /** Release a response asset owned by the request/operation tuple. */
    public static void assetRelease(long handle, long requestId, long operationId, long assetId) {
        requirePositive(requestId, "requestId");
        requirePositive(operationId, "operationId");
        requirePositive(assetId, "assetId");
        nativeAssetRelease(handle, requestId, operationId, assetId);
    }

    static long create(byte[] configJson) {
        return nativeCreate(configJson);
    }

    static void destroy(long handle) {
        nativeDestroy(handle);
    }

    static int send(long handle, byte[] commandJson) {
        return nativeSend(handle, commandJson);
    }

    static int cancel(long handle, long requestId) {
        return nativeCancel(handle, requestId);
    }

    static byte[] pollEvent(long handle, long timeoutMillis) {
        return nativePollEvent(handle, timeoutMillis);
    }

    // These handles belong to ReaderCoreRuntime's queued API, not the legacy
    // direct-listener runtimeCreate/runtimeDestroy API.
    static int enableEventAck(long handle) {
        return nativeEnableEventAck(handle);
    }

    static int ackEvent(long handle, long deliveryId) {
        return nativeAckEvent(handle, deliveryId);
    }

    private static native int nativeAbiVersion();

    private static native int nativeDeliveryCapabilities();

    private static native int nativeEnableEventAck(long handle);

    private static native int nativeAckEvent(long handle, long deliveryId);

    private static native long nativeCreate(byte[] configJson);

    private static native void nativeDestroy(long handle);

    private static native long nativeAssetBegin(
            long handle, long requestId, long operationId, long declaredBytes);

    private static native long nativeAssetWrite(
            long handle, long requestId, long operationId, long assetId, byte[] bytes);

    private static native long nativeAssetCommit(
            long handle, long requestId, long operationId, long assetId);

    private static native byte[] nativeAssetReadChunk(
            long handle, long requestId, long operationId, long assetId, long offset, int limit);

    private static native void nativeAssetRelease(
            long handle, long requestId, long operationId, long assetId);

    private static native int nativeSend(long handle, byte[] commandJson);

    private static native int nativeCancel(long handle, long requestId);

    private static native byte[] nativePollEvent(long handle, long timeoutMillis);

    private static native ReaderCoreLastError nativeLastError();

    private static native byte[] nativeReadEpubEntry(
            byte[] authorizedArchivePathUtf8, byte[] canonicalEntryUtf8, long maximumBytes);

    private static void requirePositive(long value, String field) {
        if (value <= 0) {
            throw new IllegalArgumentException(field + " must be positive");
        }
    }

    private static void requireDeclaredBytes(long value) {
        if (value < 0 || value > MAX_ASSET_TOTAL_BYTES) {
            throw new IllegalArgumentException("declaredBytes must be in 0..128 MiB");
        }
    }
}
