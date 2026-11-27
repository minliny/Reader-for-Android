package com.reader.core;

import java.nio.charset.StandardCharsets;

/** Binary-asset status and same-thread C ABI diagnostic; distinct from runtime send status. */
public final class ReaderCoreAssetException extends RuntimeException {
    private final int assetStatus;
    private final int errorCode;

    public ReaderCoreAssetException(int assetStatus, int errorCode, byte[] diagnosticUtf8) {
        super(new String(diagnosticUtf8, StandardCharsets.UTF_8));
        this.assetStatus = assetStatus;
        this.errorCode = errorCode;
    }

    public int assetStatus() { return assetStatus; }
    public int errorCode() { return errorCode; }
    public String diagnostic() { return getMessage(); }
}
