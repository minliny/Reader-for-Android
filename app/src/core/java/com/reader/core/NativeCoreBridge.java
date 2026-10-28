package com.reader.core;

import java.nio.charset.StandardCharsets;

public final class NativeCoreBridge {
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

    private static native int nativeSend(long handle, byte[] commandJson);

    private static native int nativeCancel(long handle, long requestId);

    private static native byte[] nativePollEvent(long handle, long timeoutMillis);

    private static native ReaderCoreLastError nativeLastError();

    private static native byte[] nativeReadEpubEntry(
            byte[] authorizedArchivePathUtf8, byte[] canonicalEntryUtf8, long maximumBytes);
}
