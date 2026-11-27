package io.reader.rebuild.host.storage

import com.reader.core.ReaderStorageProbe
import com.reader.core.protocol.CoreInfoData
import com.reader.core.protocol.CoreStorageFormatInfoFormat
import com.reader.core.protocol.EmptyParams
import com.reader.core.protocol.ReaderFreshStorageFormat
import com.reader.core.protocol.ReaderM1
import com.reader.core.protocol.ReaderRuntimeConfig
import io.reader.rebuild.core.CoreRuntimeActor
import io.reader.rebuild.core.HostCapabilities
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class LibraryRuntime private constructor(
    val core: CoreRuntimeActor,
    val observed: ReaderStorageProbe,
    val admitted: ReaderStorageProbe,
    val info: CoreInfoData,
) {
    suspend fun close() = core.close()

    companion object {
        suspend fun open(directory: File, capabilities: HostCapabilities, report: (Throwable) -> Unit = {}): LibraryRuntime {
            var opened: CoreRuntimeActor? = null
            try {
                return withContext(Dispatchers.IO) {
                    val path = directory.canonicalPath
                    val before = ReaderStorageProbe.read(path)
                    check(!before.observation.requiresMigration)
                    val core = CoreRuntimeActor.open(ReaderRuntimeConfig(
                        dataDirectory = path, freshStorageFormat = ReaderFreshStorageFormat.LIBRARY47,
                    ), capabilities, report)
                    opened = core
                    val info = core.call(ReaderM1.coreInfo, EmptyParams())
                    val after = ReaderStorageProbe.read(path)
                    val layout = requireNotNull(after.observation.layout)
                    check(!after.observation.freshStorage && layout.schemaVersion == 47u)
                    check(info.sqliteUserVersion == 47L && info.storageFormat?.format == CoreStorageFormatInfoFormat.LIBRARY47)
                    val previous = before.observation.layout
                    if (before.observation.freshStorage) check(layout.layoutId == "reader-sqlite-47-remote")
                    else check(requireNotNull(previous).layoutId == layout.layoutId && previous.databaseIdentity == layout.databaseIdentity)
                    LibraryRuntime(core, before, after, info)
                }
            } catch (failure: Throwable) {
                opened?.close()
                throw failure
            }
        }
    }
}
