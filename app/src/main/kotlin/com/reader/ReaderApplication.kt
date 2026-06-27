package com.reader

import android.app.Application
import com.reader.api.ReaderCoreClient

class ReaderApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ReaderCoreClient.init()
    }
}
