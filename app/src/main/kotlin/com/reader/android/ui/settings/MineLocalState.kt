package com.reader.android.ui.settings

/**
 * Mine/settings + WebDAV local state. In-memory only.
 * No secrets stored — WebDAV credentials abstracted as config status.
 */
data class MineSettingsLocal(
    val themeName: String = "跟随系统",
    val screenTimeout: String = "5 分钟",
    val ttsEngine: String = "系统默认",
    val syncEnabled: Boolean = false,
    val backupEnabled: Boolean = false
)

enum class WebDavConfigStatus(val label: String) {
    NOT_CONFIGURED("未配置"),
    CONFIGURED("已配置"),
    AUTH_ERROR("授权失败"),
    CONNECTING("连接中"),
    CONNECTED("已连接")
}

data class WebDavLocalState(
    val status: WebDavConfigStatus = WebDavConfigStatus.NOT_CONFIGURED,
    val serverLabel: String = "",
    val errorMessage: String = ""
)

class MineLocalStateAdapter {
    var settings = MineSettingsLocal()
        private set
    var webDav = WebDavLocalState()
        private set

    fun updateSettings(s: MineSettingsLocal) { settings = s }
    fun updateWebDav(w: WebDavLocalState) { webDav = w }
    fun setWebDavStatus(status: WebDavConfigStatus, error: String = "") {
        webDav = webDav.copy(status = status, errorMessage = error)
    }

    companion object {
        val Fixture = MineLocalStateAdapter().apply {
            updateSettings(MineSettingsLocal(themeName = "米色纸张"))
            updateWebDav(WebDavLocalState(WebDavConfigStatus.NOT_CONFIGURED, "未设置"))
        }
    }
}
