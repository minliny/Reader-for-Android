package io.reader.android.ui.ebookdraft

import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

/** Finite observation of this one Owner; no URL/text, stream reads, Native calls or admission authority. */
internal class ReaderEbookLayoutDiagnostics(private val renderId: String, private val clock: () -> Long) {
    enum class NativePhase { OWNER_START, LAYOUT_BEGIN, SHELL_LOAD_REQUEST, SHELL_PAGE_STARTED,
        SHELL_COMMIT_VISIBLE, SHELL_PAGE_FINISHED, READY_DOM_RECEIVED, VISUAL_REQUEST,
        VISUAL_COMPLETED, NATIVE_DRAW_OBSERVED, NATIVE_DRAW_POSTED, PROBE_REQUEST,
        READY_PAINT_RECEIVED, NATIVE_READY }
    enum class JsPhase { CONFIGURE_ENTER, LAYOUT_FETCH_BEGIN, LAYOUT_FETCH_COMPLETE,
        CONFIG_FETCH_BEGIN, CONFIG_FETCH_COMPLETE, FRAME_LOAD_BEGIN, FRAME_LOAD_COMPLETE,
        DOCUMENT_WAIT_BEGIN, DOCUMENT_WAIT_COMPLETE, DOM_ADMIT_BEGIN, DOCUMENT_WINDOW_BEGIN,
        DOCUMENT_WINDOW_COMPLETE, DOM_CROP_BEGIN, DOM_CROP_COMPLETE, ORIGINAL_MATCH_COMPLETE,
        EDIT_APPLY_BEGIN, EDIT_APPLY_COMPLETE, PROCESSED_MATCH_COMPLETE, DOM_ADMIT_COMPLETE,
        ASSET_WAIT_BEGIN, ASSET_WAIT_COMPLETE, RESTORE_BEGIN, RESTORE_COMPLETE,
        READY_DOM_SEND, READY_PAINT_SEND, READY_CONFIRM }
    enum class Resource { SHELL, RUNTIME_MODULE, DOM_MODULE, CONFIG, LAYOUT, ENTRY,
        RELATED_PUBLICATION_RESOURCE, DENIED_OR_OTHER }
    enum class Outcome { NONE, NATIVE_READY, LAYOUT_DEADLINE, EARLIER_FAILURE }
    class Ticket internal constructor(internal val owner: Any, internal val category: Resource,
        internal val started: Long) { internal val finished = AtomicBoolean(false) }
    private data class Response(var requests: Long = 0, var returned: Long = 0,
        var failed: Long = 0, var pending: Long = 0, var batchSince: Long = 0,
        var lastCompleted: Long = 0, var lastDuration: Long = 0)
    private val lock = Any()
    private val identity = Any()
    private var epoch = 0L
    private var token = ""
    private var outcome = Outcome.NONE
    private var closed = false
    private var rejected = 0L
    private val native = linkedMapOf<NativePhase, Long>()
    private val js = linkedMapOf<JsPhase, Pair<Long, Double>>()
    private val resources = Resource.values().associateWith { Response() }
    private fun time(): Long? = try { clock().takeIf { it >= 0 } } catch (_: Exception) { null }

    fun beginLayout(value: Long, layoutToken: String) {
        val now = time() ?: return
        synchronized(lock) {
            if (closed || value <= epoch || layoutToken.isBlank()) return
            epoch = value; token = layoutToken; outcome = Outcome.NONE
            val ownerStarted = native[NativePhase.OWNER_START]
            native.clear(); js.clear(); if (ownerStarted != null) native[NativePhase.OWNER_START] = ownerStarted
            native[NativePhase.LAYOUT_BEGIN] = now
        }
    }
    fun native(phase: NativePhase) {
        val now = time() ?: return
        synchronized(lock) { if (!closed) native[phase] = now }
    }
    fun js(value: Long, layoutToken: String, phase: String?, elapsed: Double?): Boolean {
        val now = time() ?: return false
        val parsed = JsPhase.values().firstOrNull { it.name == phase }
        synchronized(lock) {
            if (closed || outcome in setOf(Outcome.LAYOUT_DEADLINE, Outcome.EARLIER_FAILURE) ||
                value != epoch || layoutToken != token || parsed == null || elapsed == null ||
                !elapsed.isFinite() || elapsed !in 0.0..86_400_000.0) { rejected++; return false }
            js[parsed] = now to elapsed
            return true
        }
    }
    /** True retires only this diagnostic message from routing, including malformed/stale diagnostics. */
    fun consume(kind: String, valueJson: String, expectedEpoch: Long, expectedToken: String): Boolean {
        if (kind != "phase") return false // Business messages retain their original decoder/failure path.
        try {
            val value = JSONObject(valueJson)
            val keys = value.keys().asSequence().toSet()
            val receivedEpoch = when (val raw = value.opt("layoutEpoch")) { is Int -> raw.toLong(); is Long -> raw; else -> null }
            val receivedToken = value.opt("layoutToken") as? String
            if (keys != setOf("phase", "sinceLayoutStartMs", "layoutEpoch", "layoutToken") ||
                receivedEpoch == null || receivedToken == null || receivedEpoch != expectedEpoch || receivedToken != expectedToken) { rejected(); return true }
            js(receivedEpoch, receivedToken, value.opt("phase") as? String,
                (value.opt("sinceLayoutStartMs") as? Number)?.toDouble())
        } catch (_: Exception) { rejected() }
        return true // A diagnostic can never fall through into business exception handling.
    }
    fun rejected() { synchronized(lock) { rejected++ } }
    fun terminal(value: Outcome) { synchronized(lock) { if (!closed) outcome = value } }
    fun close() { synchronized(lock) { closed = true } }
    fun resourceStarted(category: Resource): Ticket? {
        val now = time() ?: return null
        synchronized(lock) {
            if (closed) return null
            val row = resources.getValue(category)
            row.requests++; if (row.pending++ == 0L) row.batchSince = now
            return Ticket(identity, category, now)
        }
    }
    fun resourceReturned(ticket: Ticket?, failed: Boolean) {
        if (ticket == null || ticket.owner !== identity || !ticket.finished.compareAndSet(false, true)) return
        val now = time() ?: ticket.started
        synchronized(lock) {
            val row = resources.getValue(ticket.category)
            row.pending--; row.returned++; if (failed) row.failed++
            row.lastCompleted = now; row.lastDuration = (now - ticket.started).coerceAtLeast(0)
            if (row.pending == 0L) row.batchSince = 0
        }
    }
    /** Copy only finite primitive data under lock; JSON work happens after releasing it. */
    fun snapshot(): JSONObject {
        val copied = synchronized(lock) {
            Triple(listOf(epoch, token, outcome.name, closed, rejected), native.toMap(),
                js.toMap() to resources.mapValues { it.value.copy() })
        }
        val scope = copied.first; val phases = JSONObject(); val jsPhases = JSONObject(); val counts = JSONObject()
        copied.second.forEach { (phase, at) -> phases.put(phase.name, at) }
        copied.third.first.forEach { (phase, at) -> jsPhases.put(phase.name,
            JSONObject().put("receivedElapsedMs", at.first).put("sinceLayoutStartMs", at.second)) }
        copied.third.second.forEach { (category, row) -> counts.put(category.name,
            JSONObject().put("requests", row.requests).put("responsesReturned", row.returned)
                .put("failedResponses", row.failed).put("pending", row.pending)
                .put("activeBatchSinceElapsedMs", row.batchSince).put("lastCompletedElapsedMs", row.lastCompleted)
                .put("lastDurationMs", row.lastDuration)) }
        return JSONObject().put("schemaVersion", 1).put("renderId", renderId).put("layoutEpoch", scope[0])
            .put("layoutToken", scope[1]).put("outcome", scope[2]).put("closed", scope[3])
            .put("rejectedDiagnosticEvents", scope[4]).put("nativePhases", phases).put("jsPhases", jsPhases)
            .put("resourcesScope", "OWNER_RENDER_LIFETIME_RESPONSE_RETURN_NOT_STREAM_OR_NATIVE_JOIN").put("resources", counts)
    }
}
