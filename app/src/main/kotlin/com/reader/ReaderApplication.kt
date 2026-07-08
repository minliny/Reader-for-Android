package com.reader

import android.app.Application
import com.reader.android.AppProvider
import com.reader.api.ReaderCoreClient

class ReaderApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // P0-1: AppProvider MUST be initialized before ReaderCoreClient so that
        // Room DB, DataStore, real cookie store, permission adapter, and
        // book-source repository are all backed by real storage when the Core
        // host runtime is wired. Without this, bookSourceRepository degrades
        // to FakeBookSourceRepository, Room Dao access throws, and no
        // persistence layer works in production.
        AppProvider.init(this)
        // Slice B: pass application context so file/cache/persistence handlers
        // are backed by real storage (context.filesDir).
        ReaderCoreClient.init(this)
    }
}
