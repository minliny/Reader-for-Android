package com.reader.host;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Unified, concurrency-safe facade over both halves of the Core protocol on a
 * single transport. Owns <em>one</em> poll thread that demultiplexes every
 * event off the transport:
 * <ul>
 *   <li>{@code host.request} → {@link HostAdapter#dispatch} → reply via
 *       {@link HostTransport#sendCommand} (the {@link HostEventLoop} half).</li>
 *   <li>{@code result} / {@code error} → complete the matching pending
 *       {@link CompletableFuture} from {@link #sendAndAwait} (the
 *       {@link HostCommander} half).</li>
 * </ul>
 *
 * <p>This resolves the demultiplexing caveat documented on {@link HostCommander}:
 * instead of the loop and commander competing for events, one poll site routes
 * every event by {@code type}. A host app wires this as its single Core entry
 * point:
 * <pre>
 *   HostRuntime rt = HostRuntime.over(transport)
 *       .register("http.execute", new HttpExecuteHandler(fetch))
 *       .start();
 *   CommandResult res = rt.sendAndAwait("runtime.ping", "{}", 1000);
 *   rt.stop();
 * </pre>
 *
 * <p>Pure-JVM: depends only on {@link HostTransport}, so unit-testable with a
 * fake (no NDK). The production transport is {@link ReaderCoreHostTransport}.
 */
public final class HostRuntime {

    private final HostTransport transport;
    private final HostAdapter adapter;
    private final long tickTimeoutMillis;
    private final ConcurrentHashMap<Long, CompletableFuture<CommandResult>> pending =
            new ConcurrentHashMap<>();
    private volatile java.util.concurrent.Executor dispatchExecutor;
    private long counter;
    private Thread worker;
    private volatile boolean running;

    private HostRuntime(HostTransport transport, HostAdapter adapter, long tickTimeoutMillis, long firstRequestId) {
        this.transport = transport;
        this.adapter = adapter;
        this.tickTimeoutMillis = tickTimeoutMillis;
        this.counter = firstRequestId;
    }

    public static HostRuntime over(HostTransport transport) {
        return new HostRuntime(transport, new HostAdapter(), 1000L, 2000L);
    }

    public static HostRuntime over(HostTransport transport, long tickTimeoutMillis) {
        return new HostRuntime(transport, new HostAdapter(), tickTimeoutMillis, 2000L);
    }

    public HostRuntime register(String capability, CapabilityHandler handler) {
        adapter.register(capability, handler);
        return this;
    }

    /**
     * Read-only accessor for the underlying [HostAdapter], so host-app callers
     * (e.g. the UI layer's [HostRequestDispatcher]) can dispatch capability
     * requests through the same registered handler set the poll thread uses,
     * without having to re-register handlers on a separate adapter.
     */
    public HostAdapter adapter() {
        return adapter;
    }

    /**
     * Offload {@code host.request} handler dispatch (and the reply send) to the
     * given executor so a slow capability (e.g. a real HTTP fetch) does not
     * block the single poll thread. When unset (the default), dispatch runs
     * synchronously on the poll thread. The transport must be thread-safe
     * ({@link ReaderCoreHostTransport} is).
     */
    public HostRuntime dispatchExecutor(java.util.concurrent.Executor executor) {
        this.dispatchExecutor = executor;
        return this;
    }

    /**
     * Start a command and retain its numeric Core requestId. The returned
     * handle owns the same pending future used by {@link #sendAndAwait}; callers
     * can await or cancel that exact request without reconstructing ids outside
     * this runtime's single request ledger.
     */
    public CommandHandle beginCommand(String method, String paramsJson) {
        long requestId;
        CompletableFuture<CommandResult> future;
        synchronized (this) {
            requestId = counter++;
            future = new CompletableFuture<>();
            pending.put(requestId, future);
        }
        String command = HostCommander.encodeCommand(requestId, method, paramsJson);
        try {
            transport.sendCommand(command);
        } catch (RuntimeException e) {
            pending.remove(requestId);
            future.complete(CommandResult.error(requestId,
                    "{\"code\":\"INTERNAL\",\"message\":\"send failed: "
                            + e.getMessage() + "\",\"retryable\":true}"));
        }
        return new CommandHandle(this, requestId, future);
    }

    /**
     * Send a command and await its result/error event, routed by the runtime's
     * own poll thread. Kept as the compatibility facade over
     * {@link #beginCommand}; all correlation now lives in one handle path.
     */
    public CommandResult sendAndAwait(String method, String paramsJson, long timeoutMillis) {
        return beginCommand(method, paramsJson).await(timeoutMillis);
    }

    private CommandResult await(
            long requestId,
            CompletableFuture<CommandResult> future,
            long timeoutMillis) {
        try {
            return future.get(Math.max(timeoutMillis, 0L), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            if (!pending.remove(requestId, future)) {
                CommandResult raced = future.getNow(null);
                if (raced != null) return raced;
            }
            return CommandResult.timeout(requestId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            pending.remove(requestId, future);
            return CommandResult.timeout(requestId);
        } catch (ExecutionException e) {
            pending.remove(requestId, future);
            Throwable cause = e.getCause();
            String msg = cause == null ? e.getMessage() : cause.getMessage();
            return CommandResult.error(requestId,
                    "{\"code\":\"INTERNAL\",\"message\":\"" + msg + "\",\"retryable\":true}");
        }
    }

    /**
     * Cancel the exact Core request represented by {@code handle}. Cancellation
     * is sent through Core's JSON {@code runtime.cancel} method using a fresh
     * command id while the target id remains the handle's original id. The
     * local waiter completes promptly with CANCELLED; Core's later cancellation
     * event is safely ignored because the pending entry has been removed.
     */
    private boolean cancel(CommandHandle handle) {
        CompletableFuture<CommandResult> future = handle.future;
        if (!pending.remove(handle.requestId, future)) {
            return false;
        }

        long cancelCommandId;
        synchronized (this) {
            cancelCommandId = counter++;
        }
        String params = "{\"requestId\":" + handle.requestId + "}";
        String command = HostCommander.encodeCommand(cancelCommandId, "runtime.cancel", params);
        try {
            transport.sendCommand(command);
            future.complete(CommandResult.error(handle.requestId,
                    "{\"code\":\"CANCELLED\",\"message\":\"request cancelled\","
                            + "\"retryable\":false}"));
            return true;
        } catch (RuntimeException e) {
            future.complete(CommandResult.error(handle.requestId,
                    "{\"code\":\"INTERNAL\",\"message\":\"cancel send failed: "
                            + e.getMessage() + "\",\"retryable\":true}"));
            return false;
        }
    }

    /** One host-issued Core command, its numeric request id, waiter and cancel. */
    public static final class CommandHandle {
        private final HostRuntime owner;
        private final long requestId;
        private final CompletableFuture<CommandResult> future;

        private CommandHandle(
                HostRuntime owner,
                long requestId,
                CompletableFuture<CommandResult> future) {
            this.owner = owner;
            this.requestId = requestId;
            this.future = future;
        }

        public long requestId() {
            return requestId;
        }

        public CommandResult await(long timeoutMillis) {
            return owner.await(requestId, future, timeoutMillis);
        }

        public boolean cancel() {
            return owner.cancel(this);
        }

        public boolean isDone() {
            return future.isDone();
        }
    }

    /** Start the single poll thread. Idempotent. */
    public synchronized HostRuntime start() {
        if (running) {
            return this;
        }
        running = true;
        worker = new Thread(this::runLoop, "reader-host-runtime");
        worker.setDaemon(true);
        worker.start();
        return this;
    }

    /**
     * Stop the poll thread and fail any pending {@code sendAndAwait} futures
     * promptly with a shutdown error, so blocked callers do not wait for their
     * timeout. Idempotent.
     */
    public synchronized void stop() {
        running = false;
        if (worker != null) {
            worker.interrupt();
            try {
                worker.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            worker = null;
        }
        failPendingOnShutdown();
    }

    private void failPendingOnShutdown() {
        java.util.Map<Long, CompletableFuture<CommandResult>> snapshot =
                new java.util.HashMap<>(pending);
        pending.clear();
        for (java.util.Map.Entry<Long, CompletableFuture<CommandResult>> e : snapshot.entrySet()) {
            e.getValue().complete(CommandResult.error(e.getKey(),
                    "{\"code\":\"INTERNAL\",\"message\":\"runtime stopped\",\"retryable\":true}"));
        }
    }

    public synchronized boolean isRunning() {
        return running;
    }

    public int pendingCount() {
        return pending.size();
    }

    private void runLoop() {
        while (running && !Thread.currentThread().isInterrupted()) {
            String event;
            try {
                event = transport.pollEventJson(tickTimeoutMillis);
            } catch (RuntimeException e) {
                if (!running) break;
                continue;
            }
            if (event == null) {
                continue;
            }
            route(event);
        }
    }

    @SuppressWarnings("unchecked")
    private void route(String eventJson) {
        Object root;
        try {
            root = Json.parse(eventJson);
        } catch (Json.JsonException e) {
            return;
        }
        if (!(root instanceof java.util.Map)) {
            return;
        }
        java.util.Map<String, Object> m = (java.util.Map<String, Object>) root;
        Object type = m.get("type");
        if ("host.request".equals(type)) {
            HostRequest req;
            try {
                req = HostRequest.parse(eventJson);
            } catch (IllegalArgumentException e) {
                return;
            }
            java.util.concurrent.Executor exec = dispatchExecutor;
            if (exec == null) {
                dispatchAndReply(req);
            } else {
                exec.execute(() -> dispatchAndReply(req));
            }
        } else if ("result".equals(type) || "error".equals(type)) {
            Object idVal = m.get("requestId");
            if (idVal instanceof Number) {
                long id = ((Number) idVal).longValue();
                CompletableFuture<CommandResult> future = pending.remove(id);
                if (future != null) {
                    if ("result".equals(type)) {
                        Object data = m.get("data");
                        future.complete(CommandResult.success(id,
                                data == null ? "{}" : Json.stringify(data)));
                    } else {
                        Object err = m.get("error");
                        future.complete(CommandResult.error(id,
                                err == null ? "{}" : Json.stringify(err)));
                    }
                }
            }
        }
    }

    private void dispatchAndReply(HostRequest req) {
        HostReply reply;
        try {
            reply = adapter.dispatch(req);
        } catch (RuntimeException e) {
            // Handler blew up synchronously; report a retryable internal error
            // so Core is not left waiting on this operationId.
            reply = HostReply.error("INTERNAL",
                    "host handler threw: " + e.getMessage(), true);
        }
        long replyRequestId;
        synchronized (this) {
            replyRequestId = counter++;
        }
        try {
            transport.sendCommand(HostReplyCodec.encode(replyRequestId, reply, req.operationId()));
        } catch (RuntimeException e) {
            // Best-effort: a transport failure here can't be surfaced to Core;
            // the poll thread keeps running for subsequent events.
        }
    }
}
