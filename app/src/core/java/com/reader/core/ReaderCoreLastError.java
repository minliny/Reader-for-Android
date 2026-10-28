package com.reader.core;

import java.nio.charset.StandardCharsets;

public final class ReaderCoreLastError {
    private final int code;
    private final String message;
    public ReaderCoreLastError(int code, String message) { this.code = code; this.message = message; }
    ReaderCoreLastError(int code, byte[] messageUtf8) {
        this(code, new String(messageUtf8, StandardCharsets.UTF_8));
    }
    public int code() { return code; }
    public String message() { return message; }
    public boolean isPresent() { return code != 0; }
    @Override public String toString() { return "ReaderCoreLastError{code=" + code + ", message='" + message + "'}"; }
}
