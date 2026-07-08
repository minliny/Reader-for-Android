package com.reader.android

import android.content.Context
import androidx.room.Room
import com.reader.android.data.adapter.AndroidCookieManagerStore
import com.reader.android.data.adapter.AndroidPermissionRuntimeAdapter
import com.reader.android.data.adapter.AndroidWebRuntimeAdapter
import com.reader.android.data.adapter.CookieStore
import com.reader.android.data.adapter.FakeCookieStore
import com.reader.android.data.adapter.FakeWebRuntimeAdapter
import com.reader.android.data.adapter.PermissionRuntimeAdapter
import com.reader.android.data.adapter.WebDavCredentialStore
import com.reader.android.data.adapter.WebRuntimeAdapter
import com.reader.android.data.network.RoomSubscriptionRepository
import com.reader.android.data.network.SubscriptionRepository
import com.reader.android.data.repository.BookSourceRepository
import com.reader.android.data.repository.DataStoreBookSourceRepository
import com.reader.android.data.repository.FakeBookSourceRepository
import com.reader.android.data.repository.ReadingProgressRepository
import com.reader.android.data.storage.AppDatabase
import com.reader.android.data.storage.BookmarkDao
import com.reader.android.data.storage.CachedChapterDao
import com.reader.android.data.storage.ChapterCacheManager
import com.reader.android.data.storage.RssSubscriptionDao
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
    private var _cookieStore: CookieStore? = null
    private var _webRuntimeAdapter: WebRuntimeAdapter? = null
    private var _webDavCredentialStore: WebDavCredentialStore? = null
    private var _permissionRuntimeAdapter: PermissionRuntimeAdapter? = null
    private var _subscriptionRepo: SubscriptionRepository? = null
    private var _networkAllowed: Boolean = false
    private var initialized = false

    /** P0-3: safe accessor for checking whether [init] has been called. */
    val isInitialized: Boolean get() = initialized

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

    /** Keystore-backed WebDAV credential persistence. */
    val webDavCredentialStore: WebDavCredentialStore
        get() = _webDavCredentialStore ?: WebDavCredentialStore().also { _webDavCredentialStore = it }

    /**
     * P3: Unified permission runtime adapter. On-device this is
     * [AndroidPermissionRuntimeAdapter] (wired in [init]); tests inject a fake
     * via [initForPermissionRuntimeAdapter]. The UI layer reads permission
     * state through this adapter so `SettingsGeneralScreen` can render real
     * permission badges and the reducer can record `PermissionGranted` /
     * `PermissionDenied` outcomes.
     */
    val permissionRuntimeAdapter: PermissionRuntimeAdapter
        get() = _permissionRuntimeAdapter
            ?: com.reader.android.data.adapter.FakePermissionRuntimeAdapter()

    fun initForPermissionRuntimeAdapter(adapter: PermissionRuntimeAdapter) {
        _permissionRuntimeAdapter = adapter
    }

    // ── Database ──

    val readingProgressDao: ReadingProgressDao
        get() = requireDb().readingProgressDao()

    /**
     * P0-3: Reading progress repository. Wraps [readingProgressDao] with field mapping
     * between ReaderContext (in-memory) and ReadingProgress (Room entity). Lazily created
     * so callers that never touch reading progress pay no cost.
     */
    val readingProgressRepository: ReadingProgressRepository
        get() = _readingProgressRepo ?: ReadingProgressRepository(readingProgressDao).also {
            _readingProgressRepo = it
        }
    private var _readingProgressRepo: ReadingProgressRepository? = null

    val cachedChapterDao: CachedChapterDao
        get() = requireDb().cachedChapterDao()

    /**
     * P0-4: Chapter cache manager. Wraps [cachedChapterDao] so the reading VM can
     * consult the disk cache before hitting `bookApi.content()` and persist fetched
     * chapter text for offline / repeat reads. Lazily created.
     */
    val chapterCacheManager: ChapterCacheManager
        get() = _chapterCacheManager ?: ChapterCacheManager(cachedChapterDao).also {
            _chapterCacheManager = it
        }
    private var _chapterCacheManager: ChapterCacheManager? = null

    val bookmarkDao: BookmarkDao
        get() = requireDb().bookmarkDao()

    val rssSubscriptionDao: RssSubscriptionDao
        get() = requireDb().rssSubscriptionDao()

    /**
     * P4: RSS subscription repository. On-device this is
     * [RoomSubscriptionRepository] (wired in [init]); tests inject a fake
     * via [initForSubscriptionRepository]. The repository persists
     * subscription metadata only — article content is DomainState owned
     * by Core (`rss.list` / `rss.item.read`, currently a Core blocker).
     */
    val subscriptionRepository: SubscriptionRepository
        get() = _subscriptionRepo ?: RoomSubscriptionRepository(rssSubscriptionDao).also {
            _subscriptionRepo = it
        }

    fun initForSubscriptionRepository(repo: SubscriptionRepository) {
        _subscriptionRepo = repo
    }

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
        // P3: wire the real Android permission adapter so the UI can read
        // notification / file-access / battery-optimization state. Tests
        // inject a fake via initForPermissionRuntimeAdapter.
        _permissionRuntimeAdapter = AndroidPermissionRuntimeAdapter(context.applicationContext)
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
        _cookieStore = null
        _webRuntimeAdapter = null
        _webDavCredentialStore = null
        _permissionRuntimeAdapter = null
        _subscriptionRepo = null
        _readingProgressRepo = null
        _chapterCacheManager = null
        _networkAllowed = false
        initialized = false
    }

    // ── Internal ──

    private fun requireDb(): AppDatabase =
        db ?: error("AppProvider not initialized. Call AppProvider.init(context) first.")
}
