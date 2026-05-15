package com.reader.android.data.adapter

enum class QueueEvent { ENQUEUED, STARTED, COMPLETED, SKIPPED }

data class QueueItem(
    val utterance: TtsUtterance,
    val priority: Int = 0,
    var state: QueueEvent = QueueEvent.ENQUEUED
)

class TtsQueue {
    private val items = mutableListOf<QueueItem>()

    fun enqueue(utterance: TtsUtterance, priority: Int = 0) {
        items.add(QueueItem(utterance, priority))
        items.sortByDescending { it.priority }
    }

    fun dequeue(): QueueItem? {
        val item = items.firstOrNull() ?: return null
        item.state = QueueEvent.STARTED
        return item
    }

    fun markCompleted(utteranceId: String) {
        items.find { it.utterance.utteranceId == utteranceId }?.state = QueueEvent.COMPLETED
        items.removeAll { it.state == QueueEvent.COMPLETED }
    }

    fun clear() { items.clear() }
    fun size(): Int = items.size
    fun isEmpty(): Boolean = items.isEmpty()
    fun all(): List<QueueItem> = items.toList()
}
