package io.reader.rebuild.host.network

import android.annotation.SuppressLint
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebStorage
import androidx.annotation.MainThread
import androidx.webkit.ProfileStore
import androidx.webkit.WebStorageCompat
import androidx.webkit.WebViewFeature
import com.reader.core.protocol.M2HostErrorCode
import com.reader.core.protocol.M2HostErrorPhase
import java.util.UUID
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

internal enum class ReaderWebProfilePurpose(val wire: String) { BACKGROUND("background"), LOGIN("login") }

/** Reader owns only these UUID profile names. Android forbids deleting a loaded profile. */
@SuppressLint("RequiresFeature")
@MainThread
internal object ReaderWebProfilePool {
    private const val MAX_PROFILE_NAMES_PER_PROCESS = 128
    private const val CLEANUP_TIMEOUT_MS = 3_000L
    private val ownedName = Regex("reader-(background|login)-[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}")
    private val cleanProfiles = mutableMapOf<ReaderWebProfilePurpose, ArrayDeque<String>>()
    private val physical = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var alreadyScanned = false
    private var allocatedNames = 0

    fun acquire(purpose: ReaderWebProfilePurpose): String {
        if (!alreadyScanned) {
            val store = ProfileStore.getInstance()
            val previousNames = store.allProfileNames.filter(ownedName::matches)
            for (name in previousNames) {
                runCatching { store.deleteProfile(name) }.onFailure {
                    Log.w("ReaderWebProfile", "Cold profile removal deferred: ${it.javaClass.simpleName}")
                }
            }
            alreadyScanned = true
        }
        cleanProfiles[purpose]?.removeFirstOrNull()?.let { return it }
        if (allocatedNames == MAX_PROFILE_NAMES_PER_PROCESS) throw NetworkHostFailure(
            M2HostErrorCode.CAPABILITY_UNAVAILABLE, M2HostErrorPhase.RUNTIME,
            "Isolated WebView capacity is exhausted until the next app session",
        )
        allocatedNames++
        return "reader-${purpose.wire}-${UUID.randomUUID()}"
    }

    fun retire(purpose: ReaderWebProfilePurpose, name: String, cookies: CookieManager, storage: WebStorage) {
        physical.launch {
            val reusable = runCatching {
                withTimeoutOrNull(CLEANUP_TIMEOUT_MS) {
                    // The view is already destroyed. Until wiping completes this
                    // profile cannot be leased, even to the same source again.
                    suspendCancellableCoroutine { done ->
                        cookies.removeAllCookies { if (done.isActive) done.resume(Unit) }
                    }
                    cookies.flush()
                    if (WebViewFeature.isFeatureSupported(WebViewFeature.DELETE_BROWSING_DATA)) {
                        suspendCancellableCoroutine { done ->
                            WebStorageCompat.deleteBrowsingData(storage) { if (done.isActive) done.resume(Unit) }
                        }
                        true
                    } else {
                        storage.deleteAllData()
                        false
                    }
                } == true
            }.onFailure {
                Log.w("ReaderWebProfile", "Profile cleanup deferred: ${it.javaClass.simpleName}")
            }.getOrDefault(false)
            if (reusable) cleanProfiles.getOrPut(purpose, ::ArrayDeque).addLast(name)
            // Older WebViews lack a browsing-data completion barrier. Cold
            // removal is the first time this physical name may be touched again.
        }
    }
}
