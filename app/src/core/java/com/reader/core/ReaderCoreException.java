package com.reader.core;

public final class ReaderCoreException extends RuntimeException {
    public static final int SEND_BUSY = 5;
    public static final int SEND_RESOURCE_EXHAUSTED = 6;
    private final int status;
    private final String operation;
    private final ReaderCoreLastError lastError;

    public ReaderCoreException(String message) {
        this(-1, message);
    }

    public ReaderCoreException(int status, String message) {
        this(null, status, message, null);
    }

    public ReaderCoreException(String operation, int status, String message, ReaderCoreLastError lastError) {
        super(message);
        this.operation = operation;
        this.status = status;
        this.lastError = lastError;
    }

    public int status() {
        return status;
    }

    public String operation() { return operation; }
    public ReaderCoreLastError lastError() { return lastError; }

    public boolean isBusy() { return isSendStatus() && status == SEND_BUSY; }
    public boolean isResourceExhausted() { return isSendStatus() && status == SEND_RESOURCE_EXHAUSTED; }

    private boolean isSendStatus() { return operation == null || operation.equals("rc_runtime_send"); }
}
