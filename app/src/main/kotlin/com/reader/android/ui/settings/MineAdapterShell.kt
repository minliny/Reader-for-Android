package com.reader.android.ui.settings

/**
 * Fake/real boundary for Mine / WebDAV / Backup / Sync settings.
 */
object MineAdapterShell {

    enum class Mode { FAKE, REAL }
    var mode: Mode = Mode.FAKE
        private set

    private val adapter = MineLocalStateAdapter.Fixture

    fun settings(): MineSettingsLocal = adapter.settings
    fun webDav(): WebDavLocalState = adapter.webDav
    fun webDavStatus(): WebDavConfigStatus = adapter.webDav.status

    fun updateWebDavStatus(status: WebDavConfigStatus, error: String = "") {
        adapter.setWebDavStatus(status, error)
    }

    val isFakeMode: Boolean get() = mode == Mode.FAKE
    fun enableRealMode() { mode = Mode.REAL }
    fun resetToFakeMode() { mode = Mode.FAKE }
}
