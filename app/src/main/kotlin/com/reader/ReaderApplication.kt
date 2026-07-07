package com.reader

import android.app.Application
import com.reader.api.ReaderCoreClient

class ReaderApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Slice B: pass application context so file/cache/persistence handlers
        // are backed by real storage (context.filesDir).
        ReaderCoreClient.init(this)
    }
}
