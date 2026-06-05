package com.reader.android

import android.content.Context
import androidx.room.Room
import com.reader.android.data.bridge.CoreBridge
import com.reader.android.data.bridge.FakeCoreBridge
import com.reader.android.data.bridge.RealCoreBridge
import com.reader.android.data.network.OkHttpTransport
import com.reader.android.data.repository.BookSourceRepository
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
    private var _networkAllowed: Boolean = false
    private var initialized = false

    // ── Core Bridge ──

    /**
     * Provides [CoreBridge] based on network state.
     * - Network disabled: returns [FakeCoreBridge] (deterministic, no I/O)
     * - Network enabled: returns [RealCoreBridge] (live network, respects [isNetworkAllowed] guard)
     *
     * Tests can inject a custom bridge via [initForBridge].
     */
    val coreBridge: CoreBridge
        get() = _coreBridge ?: if (_networkAllowed) {
            RealCoreBridge(OkHttpTransport())
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
        _bookSourceRepo = FakeBookSourceRepository() // TODO: DataStoreBookSourceRepository(context)
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
        _networkAllowed = false
        initialized = false
    }

    // ── Internal ──

    private fun requireDb(): AppDatabase =
        db ?: error("AppProvider not initialized. Call AppProvider.init(context) first.")
}
