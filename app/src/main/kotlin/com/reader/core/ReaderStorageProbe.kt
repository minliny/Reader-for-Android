package com.reader.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Versioned projection of the read-only rc_storage_probe_v1 response. */
@Serializable
public data class ReaderStorageProbe(
    val schemaVersion: UInt,
    val observation: ReaderStorageAdmission,
) {
    public companion object {
        private val json = Json { ignoreUnknownKeys = true }

        public fun read(dataDirectory: String): ReaderStorageProbe =
            json.decodeFromString<ReaderStorageProbe>(ReaderCoreRuntime.probeStorage(dataDirectory))
                .also { require(it.schemaVersion == 1u) { "Unsupported storage probe version" } }
    }
}

@Serializable
public data class ReaderStorageAdmission(
    val freshStorage: Boolean,
    val requiresMigration: Boolean,
    val layout: ReaderStorageLayout?,
)

@Serializable
public data class ReaderStorageLayout(
    val schemaVersion: UInt,
    val layoutId: String,
    val databaseIdentity: String?,
    val syncProtocolVersions: List<UInt>,
    val syncLogReceipts: Boolean,
)
