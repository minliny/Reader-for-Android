package com.reader.android.data

enum class CapabilityStatus { DONE, PARTIAL, TODO, BLOCKED, OUT_OF_SCOPE }

enum class CapabilityOwner { APP, ADAPTER, STORAGE, PARSER, SYNC, BRIDGE, TEST, DOCS }

data class CapabilityEntry(
    val id: String,
    val name: String,
    val owner: CapabilityOwner,
    val uiRequired: Boolean,
    val status: CapabilityStatus,
    val files: List<String> = emptyList(),
    val taskId: String? = null
)

class CapabilityMatrix {
    private val entries = mutableMapOf<String, CapabilityEntry>()

    fun register(entry: CapabilityEntry) { entries[entry.id] = entry }

    fun get(id: String) = entries[id]
    fun all() = entries.values.toList()
    fun byStatus(status: CapabilityStatus) = entries.values.filter { it.status == status }
    fun nonUiDone() = entries.values.count { !it.uiRequired && it.status == CapabilityStatus.DONE }
    fun nonUiTotal() = entries.values.count { !it.uiRequired }
    fun uiOnlyGaps() = entries.values.filter { it.uiRequired && it.status != CapabilityStatus.DONE }
}
