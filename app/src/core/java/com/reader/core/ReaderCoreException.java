package com.reader.core;

public final class ReaderCoreException extends RuntimeException {
    public static final int SEND_BUSY = 5;
    public static final int SEND_RESOURCE_EXHAUSTED = 6;
    private final int status;

    public ReaderCoreException(String message) {
        this(-1, message);
    }

    public ReaderCoreException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int status() {
        return status;
    }

    public boolean isBusy() { return status == SEND_BUSY; }
    public boolean isResourceExhausted() { return status == SEND_RESOURCE_EXHAUSTED; }
}
