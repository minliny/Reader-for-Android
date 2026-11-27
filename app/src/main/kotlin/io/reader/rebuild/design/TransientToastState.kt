package io.reader.rebuild.design

import androidx.annotation.MainThread
import com.reader.core.theme.ReaderDesignMetrics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class TransientMessage(val text: String, val sequence: Long, val expiresAtMillis: Long)

/** One transient presentation state per visible owner. Core outcomes stay with the caller. */
internal class TransientToastState(private val clockMillis: () -> Long = { System.nanoTime() / 1_000_000 }) {
    private val recent = mutableMapOf<String, Long>()
    private val visible = MutableStateFlow<TransientMessage?>(null)
    private var sequence = 0L
    val message = visible.asStateFlow()

    @MainThread
    fun present(key: String, text: String, enabled: Boolean): Boolean {
        if (!enabled) {
            hide()
            return false
        }
        val now = clockMillis()
        val interval = ReaderDesignMetrics.shortFeedbackMillis.toLong()
        recent.entries.removeAll { now - it.value >= interval }
        if (key in recent) return false
        recent[key] = now
        visible.value = TransientMessage(text, ++sequence, now + interval)
        return true
    }

    fun remaining(message: TransientMessage): Long = (message.expiresAtMillis - clockMillis()).coerceAtLeast(0)

    @MainThread
    fun expire(message: TransientMessage) {
        visible.compareAndSet(message, null)
    }

    @MainThread
    fun hide() {
        visible.value = null
    }
}
