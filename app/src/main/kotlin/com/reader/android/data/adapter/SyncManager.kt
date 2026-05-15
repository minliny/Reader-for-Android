package com.reader.android.data.adapter

enum class SyncState { IDLE, SYNCING, SUCCESS, FAILED, CONFLICT }

data class SyncOperation(
    val id: String,
    val type: SyncType,
    var state: SyncState = SyncState.IDLE,
    var attempt: Int = 0,
    val maxRetries: Int = 3,
    val backoffPolicy: RetryPolicy = RetryPolicy.DEFAULT
)

enum class SyncType { BACKUP, RESTORE, PROGRESS_PUSH, PROGRESS_PULL }

class SyncManager {
    private val operations = mutableListOf<SyncOperation>()
    var currentState: SyncState = SyncState.IDLE
        private set

    fun start(syncType: SyncType): SyncOperation {
        val op = SyncOperation("sync-${System.currentTimeMillis()}", syncType)
        operations.add(op)
        currentState = SyncState.SYNCING
        op.state = SyncState.SYNCING
        return op
    }

    fun complete(opId: String) {
        operations.find { it.id == opId }?.state = SyncState.SUCCESS
        currentState = if (operations.all { it.state == SyncState.SUCCESS }) SyncState.SUCCESS else SyncState.IDLE
    }

    fun fail(opId: String): Boolean {
        val op = operations.find { it.id == opId } ?: return false
        op.attempt++
        if (op.attempt > op.maxRetries) {
            op.state = SyncState.FAILED
            currentState = SyncState.FAILED
            return false
        }
        return true // can retry
    }

    fun markConflict(opId: String) {
        operations.find { it.id == opId }?.state = SyncState.CONFLICT
        currentState = SyncState.CONFLICT
    }

    fun reset() {
        currentState = SyncState.IDLE
    }
}
