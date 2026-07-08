package com.reader.android

import android.content.Context
import androidx.room.Room
import com.reader.android.data.adapter.AndroidAudioFocusController
import com.reader.android.data.adapter.AndroidCookieManagerStore
import com.reader.android.data.adapter.AndroidPermissionRuntimeAdapter
import com.reader.android.data.adapter.AndroidTtsEngine
import com.reader.android.data.adapter.AndroidWebRuntimeAdapter
import com.reader.android.data.adapter.AudioFocusController
import com.reader.android.data.adapter.CookieStore
import com.reader.android.data.adapter.FakeCookieStore
import com.reader.android.data.adapter.FakeWebRuntimeAdapter
import com.reader.android.data.adapter.PermissionRuntimeAdapter
import com.reader.android.data.adapter.TtsSessionController
import com.reader.android.data.adapter.WebDavCredentialStore
import com.reader.android.data.adapter.WebRuntimeAdapter
import com.reader.android.data.network.RoomRssItemRepository
import com.reader.android.data.network.RoomSubscriptionRepository
import com.reader.android.data.network.RssItemRepository
import com.reader.android.data.network.SubscriptionRepository
import com.reader.android.data.repository.BookGroupRepository
import com.reader.android.data.repository.BookSourceRepository
import com.reader.android.data.repository.DataStoreBookSourceRepository
import com.reader.android.data.repository.FakeBookSourceRepository
import com.reader.android.data.repository.ReadingProgressRepository
import com.reader.android.data.repository.RoomSearchHistoryRepository
import com.reader.android.data.repository.SearchHistoryRepository
import com.reader.android.data.storage.AppDatabase
import com.reader.android.data.storage.BookGroupDao
import com.reader.android.data.storage.BookmarkDao
import com.reader.android.data.storage.CachedChapterDao
import com.reader.android.data.storage.ChapterCacheManager
import com.reader.android.data.storage.RssItemDao
import com.reader.android.data.storage.RssSubscriptionDao
import com.reader.android.data.storage.ReadingProgressDao
import com.reader.android.data.storage.SearchHistoryDao

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
    private var _rssItemRepo: RssItemRepository? = null
    private var _searchHistoryRepo: SearchHistoryRepository? = null
    private var _webDavClient: com.reader.android.data.adapter.AndroidWebDavClient? = null
    private var _backupRestoreManager: com.reader.android.data.adapter.BackupRestoreManager? = null
    private var _ttsEngine: AndroidTtsEngine? = null
    private var _audioFocusController: AudioFocusController? = null
    private var _ttsSessionController: TtsSessionController? = null
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
     * P1-6: OkHttp-backed [com.reader.android.data.adapter.AndroidWebDavClient]
     * wired to [webDavCredentialStore]. Lazily constructed — returns null
     * when AppProvider is not initialized (JVM tests use
     * [com.reader.host.fakeWebDavContext] instead).
     *
     * The credential identifier is the fixed string "webdav.default" so
     * [com.reader.host.WebDavCredentialProvider] and this client resolve
     * to the same stored credential.
     */
    val webDavClient: com.reader.android.data.adapter.AndroidWebDavClient?
        get() = _webDavClient

    /**
     * P1-6: [com.reader.android.data.adapter.BackupRestoreManager] backed
     * by [webDavClient]. Lazily constructed alongside the client.
     */
    val backupRestoreManager: com.reader.android.data.adapter.BackupRestoreManager?
        get() = _backupRestoreManager

    /**
     * P1-6: inject a custom WebDAV client + backup manager for testing.
     */
    fun initForWebDavClient(
        client: com.reader.android.data.adapter.AndroidWebDavClient?,
        manager: com.reader.android.data.adapter.BackupRestoreManager? = null
    ) {
        _webDavClient = client
        _backupRestoreManager = manager
    }

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

    // ── TTS session (P1-4) ──

    /**
     * P1-4: Real TTS engine backed by Android's [android.speech.tts.TextToSpeech].
     * Lazily created — only constructed when TTS is first used. Held as a
     * singleton so the [ttsSessionController] and the HostFacade share the
     * same engine instance.
     */
    val ttsEngine: AndroidTtsEngine
        get() = _ttsEngine ?: error("AppProvider not initialized. Call AppProvider.init(context) first.")

    /**
     * P1-4: Audio focus controller for TTS playback. Uses [AndroidAudioFocusController]
     * on-device; tests inject a fake via [initForAudioFocusController].
     */
    val audioFocusController: AudioFocusController
        get() = _audioFocusController ?: error("AppProvider not initialized. Call AppProvider.init(context) first.")

    fun initForAudioFocusController(controller: AudioFocusController) {
        _audioFocusController = controller
    }

    /**
     * P1-4: Session-level TTS orchestrator. Manages paragraph queue,
     * multi-chapter progression, AudioFocus/BecomingNoisy recovery, and
     * progress writeback via [TtsSessionController.progressFlow].
     * Lazily created — depends on [ttsEngine] + [audioFocusController].
     */
    val ttsSessionController: TtsSessionController
        get() = _ttsSessionController ?: TtsSessionController(
            tts = ttsEngine,
            audioFocusController = audioFocusController,
            context = appContext
        ).also { _ttsSessionController = it }

    private var appContext: Context? = null

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

    val bookGroupDao: BookGroupDao
        get() = requireDb().bookGroupDao()

    /**
     * P0-3: Book group repository. Wraps [bookGroupDao] for group CRUD + book↔group
     * assignment. Groups are local-only definitions; book membership is keyed by
     * `bookUrl` so it stays in sync with the Core-owned bookshelf. Lazily created.
     */
    val bookGroupRepository: BookGroupRepository
        get() = _bookGroupRepo ?: BookGroupRepository(bookGroupDao).also {
            _bookGroupRepo = it
        }
    private var _bookGroupRepo: BookGroupRepository? = null

    val bookmarkDao: BookmarkDao
        get() = requireDb().bookmarkDao()

    val rssSubscriptionDao: RssSubscriptionDao
        get() = requireDb().rssSubscriptionDao()

    val rssItemDao: RssItemDao
        get() = requireDb().rssItemDao()

    /**
     * P4: RSS subscription repository. On-device this is
     * [RoomSubscriptionRepository] (wired in [init]); tests inject a fake
     * via [initForSubscriptionRepository]. The repository persists
     * subscription metadata only — article content is cached separately in
     * [rssItemRepository] (a local fallback until Core lands `rss.list` /
     * `rss.item.read`).
     */
    val subscriptionRepository: SubscriptionRepository
        get() = _subscriptionRepo ?: RoomSubscriptionRepository(rssSubscriptionDao).also {
            _subscriptionRepo = it
        }

    fun initForSubscriptionRepository(repo: SubscriptionRepository) {
        _subscriptionRepo = repo
    }

    /**
     * P4: RSS item repository — local cache of parsed RSS articles so
     * `rss.list` / `rss.item.read` work as a fallback until Core lands
     * these protocol methods. On-device this is [RoomRssItemRepository];
     * tests inject a fake via [initForRssItemRepository]. Populated by
     * `rss.refresh`; read state (`isRead`) is owned here, not on the
     * subscription entity.
     */
    val rssItemRepository: RssItemRepository
        get() = _rssItemRepo ?: RoomRssItemRepository(rssItemDao).also {
            _rssItemRepo = it
        }

    fun initForRssItemRepository(repo: RssItemRepository) {
        _rssItemRepo = repo
    }

    val searchHistoryDao: SearchHistoryDao
        get() = requireDb().searchHistoryDao()

    /**
     * P2: Search history repository — local Room-backed fallback until Core
     * lands `search.history.list` / `add` / `clear` protocol methods. On-device
     * this is [RoomSearchHistoryRepository]; tests inject a fake via
     * [initForSearchHistoryRepository]. Exposed through the Host capability
     * surface (`search.history.*`) so the UI dispatches through HostAdapter.
     */
    val searchHistoryRepository: SearchHistoryRepository
        get() = _searchHistoryRepo ?: RoomSearchHistoryRepository(searchHistoryDao).also {
            _searchHistoryRepo = it
        }

    fun initForSearchHistoryRepository(repo: SearchHistoryRepository) {
        _searchHistoryRepo = repo
    }

    // ── Repository ──

    val bookSourceRepository: BookSourceRepository
        get() = _bookSourceRepo ?: FakeBookSourceRepository()

    // ── Initialization ──

    fun init(context: Context): AppProvider {
        if (initialized) return this
        appContext = context.applicationContext
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
        // P1-4: wire the real TTS engine + audio focus controller so the
        // session controller can manage paragraph queue, multi-chapter
        // progression, AudioFocus/BecomingNoisy recovery, and progress
        // writeback. Tests inject fakes via initForAudioFocusController.
        _ttsEngine = AndroidTtsEngine(context.applicationContext)
        _audioFocusController = AndroidAudioFocusController(context.applicationContext)
        // P1-6: wire the OkHttp-backed WebDAV client + BackupRestoreManager.
        // The credential identifier "webdav.default" matches the one used
        // by WebDavCredentialProvider so credential.resolve + webdav.* share
        // the same stored credential.
        _webDavClient = com.reader.android.data.adapter.AndroidWebDavClient(
            credentialStore = webDavCredentialStore,
            credentialIdentifier = "webdav.default"
        )
        _backupRestoreManager = com.reader.android.data.adapter.BackupRestoreManager(_webDavClient!!)
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
        _ttsSessionController?.shutdown()
        _ttsSessionController = null
        _ttsEngine = null
        _audioFocusController = null
        appContext = null
        db?.close()
        db = null
        _bookSourceRepo = null
        _cookieStore = null
        _webRuntimeAdapter = null
        _webDavCredentialStore = null
        _webDavClient = null
        _backupRestoreManager = null
        _permissionRuntimeAdapter = null
        _subscriptionRepo = null
        _rssItemRepo = null
        _searchHistoryRepo = null
        _readingProgressRepo = null
        _chapterCacheManager = null
        _bookGroupRepo = null
        _networkAllowed = false
        initialized = false
    }

    // ── Internal ──

    private fun requireDb(): AppDatabase =
        db ?: error("AppProvider not initialized. Call AppProvider.init(context) first.")
}
