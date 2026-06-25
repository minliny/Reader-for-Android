package com.reader.android

import android.content.Context
import androidx.room.Room
import com.reader.android.data.adapter.AndroidCookieManagerStore
import com.reader.android.data.adapter.AndroidWebRuntimeAdapter
import com.reader.android.data.adapter.CookieStore
import com.reader.android.data.adapter.FakeCookieStore
import com.reader.android.data.adapter.FakeWebRuntimeAdapter
import com.reader.android.data.adapter.WebDavCredentialStore
import com.reader.android.data.adapter.WebRuntimeAdapter
import com.reader.android.data.bridge.CoreBridge
import com.reader.android.data.bridge.FakeCoreBridge
import com.reader.android.data.bridge.RealCoreBridge
import com.reader.android.data.nativebridge.NativeHostBusLoopEvidence
import com.reader.android.data.nativebridge.NativeRuntimeLoadEvidence
import com.reader.android.data.nativebridge.NativeRuntimePackagingEvidence
import com.reader.android.data.nativebridge.ReaderNativeRuntimeBridge
import com.reader.android.data.network.OkHttpTransport
import com.reader.android.data.repository.BookSourceRepository
import com.reader.android.data.repository.DataStoreBookSourceRepository
import com.reader.android.data.repository.FakeBookSourceRepository
import com.reader.android.data.storage.AppDatabase
import com.reader.android.data.storage.BookmarkDao
import com.reader.android.data.storage.CachedChapterDao
import com.reader.android.data.storage.ReadingProgressDao

/**
 * P1 Runtime Wiring: Central dependency provider.
 * Production uses Room + DataStore. Tests use in-memory/fake providers.
 *
 * Usage:
 *   val provider = AppProvider.init(applicationContext)  // once in Application
 *   val bookshelfDao = AppProvider.bookshelfBooks         // anywhere in app layer
 */
object AppProvider {

    // ── Runtime state ──
    private var db: AppDatabase? = null
    private var _bookSourceRepo: BookSourceRepository? = null
    private var _coreBridge: CoreBridge? = null
    private var _cookieStore: CookieStore? = null
    private var _webRuntimeAdapter: WebRuntimeAdapter? = null
    private var _webDavCredentialStore: WebDavCredentialStore? = null
    private var _networkAllowed: Boolean = false
    private var initialized = false

    // ── Core Bridge ──

    /**
     * Provides [CoreBridge] based on network state.
     * - Network disabled: returns [FakeCoreBridge] (deterministic, no I/O)
     * - Network enabled: returns [RealCoreBridge] with OkHttp transport wired to
     *   the shared [cookieStore], so OkHttp and the WebView mirror the same cookies.
     *
     * Tests can inject a custom bridge via [initForBridge].
     */
    val coreBridge: CoreBridge
        get() = _coreBridge ?: if (_networkAllowed) {
            RealCoreBridge(OkHttpTransport.withCookieStore(cookieStore), cookieStore = cookieStore)
        } else {
            FakeCoreBridge()
        }

    /** Inject a custom bridge for tests. Overrides network-state default. */
    fun initForBridge(bridge: CoreBridge) {
        _coreBridge = bridge
    }

    // ── Network gate ──

    /** Default false. Tests must NOT bypass this without explicit opt-in. */
    val isNetworkAllowed: Boolean get() = _networkAllowed

    fun enableNetworkForTestingOnly() {
        _networkAllowed = true
    }

    // ── Host adapters (Android platform seams) ──

    /**
     * Shared cookie store. On-device this is [AndroidCookieManagerStore] (wired
     * in [init]) so that OkHttp's [com.reader.android.data.network.ScopedOkHttpCookieJar]
     * and the WebView host read/write the same CookieManager. In tests it stays
     * [FakeCookieStore] (the default) so no Android runtime is touched — even
     * when a test opts into the network gate, the cookie store remains a fake
     * unless explicitly injected via [initForCookieStore].
     */
    val cookieStore: CookieStore
        get() = _cookieStore ?: FakeCookieStore()

    fun initForCookieStore(store: CookieStore) {
        _cookieStore = store
    }

    /**
     * WebView-backed [WebRuntimeAdapter]. On-device this is
     * [AndroidWebRuntimeAdapter]; tests inject a fake. The adapter is only
     * constructed on demand (it needs a WebView on the main thread).
     */
    val webRuntimeAdapter: WebRuntimeAdapter
        get() = _webRuntimeAdapter ?: if (_networkAllowed) {
            // Real WebView adapter requires a WebView constructed on the UI thread;
            // callers obtain it via webRuntimeAdapterFor(webView). Fallback to fake
            // until a WebView is supplied.
            FakeWebRuntimeAdapter()
        } else {
            FakeWebRuntimeAdapter()
        }

    /** Build a real [AndroidWebRuntimeAdapter] bound to [webView]. */
    fun webRuntimeAdapterFor(webView: android.webkit.WebView): WebRuntimeAdapter {
        val adapter = AndroidWebRuntimeAdapter(webView, cookieStore)
        _webRuntimeAdapter = adapter
        return adapter
    }

    fun initForWebRuntimeAdapter(adapter: WebRuntimeAdapter) {
        _webRuntimeAdapter = adapter
    }

    /** Android app-side NDK/JNI packaging descriptor. Does not load the library. */
    val nativeRuntimePackagingEvidence: NativeRuntimePackagingEvidence
        get() = ReaderNativeRuntimeBridge.packagingEvidence()

    /** Explicit app-side native load seam; JVM unit tests are not device evidence. */
    fun loadNativeRuntimeForApp(): NativeRuntimeLoadEvidence =
        ReaderNativeRuntimeBridge.loadLibraryForApp()

    /** Executes the native JNI host bus loop probe when the shared object is loaded. */
    fun runNativeRuntimeHostBusLoopProbe(commands: List<String>): NativeHostBusLoopEvidence =
        ReaderNativeRuntimeBridge.runHostBusLoopProbe(commands)

    /** Keystore-backed WebDAV credential persistence. */
    val webDavCredentialStore: WebDavCredentialStore
        get() = _webDavCredentialStore ?: WebDavCredentialStore().also { _webDavCredentialStore = it }

    // ── Database ──

    val readingProgressDao: ReadingProgressDao
        get() = requireDb().readingProgressDao()

    val cachedChapterDao: CachedChapterDao
        get() = requireDb().cachedChapterDao()

    val bookmarkDao: BookmarkDao
        get() = requireDb().bookmarkDao()

    // ── Repository ──

    val bookSourceRepository: BookSourceRepository
        get() = _bookSourceRepo ?: FakeBookSourceRepository()

    // ── Initialization ──

    fun init(context: Context): AppProvider {
        if (initialized) return this
        @Suppress("DEPRECATION")
        db = Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "reader.db")
            .fallbackToDestructiveMigration()
            .build()
        _bookSourceRepo = DataStoreBookSourceRepository(context.applicationContext).also {
            it.loadBlocking()
        }
        // On-device: wire the real CookieManager-backed cookie store so OkHttp
        // and the WebView share one cookie source. Tests use FakeCookieStore.
        _cookieStore = AndroidCookieManagerStore()
        initialized = true
        return this
    }

    /** For tests: inject fake/in-memory implementations. */
    fun initForTesting(
        testDb: AppDatabase? = null,
        bookSourceRepo: BookSourceRepository? = null
    ): AppProvider {
        db = testDb
        _bookSourceRepo = bookSourceRepo ?: FakeBookSourceRepository()
        _networkAllowed = false
        initialized = true
        return this
    }

    /** Clean up between tests. */
    fun close() {
        db?.close()
        db = null
        _bookSourceRepo = null
        _coreBridge = null
        _cookieStore = null
        _webRuntimeAdapter = null
        _webDavCredentialStore = null
        _networkAllowed = false
        initialized = false
    }

    // ── Internal ──

    private fun requireDb(): AppDatabase =
        db ?: error("AppProvider not initialized. Call AppProvider.init(context) first.")
}
