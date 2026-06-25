package com.reader.android.data.nativebridge;

public final class ReaderNativeRuntimeJni {
    private ReaderNativeRuntimeJni() {
    }

    public static native String nativeRuntimeIdentity();

    public static native String nativeRunHostBusLoopProbe(String requestJson);
}
