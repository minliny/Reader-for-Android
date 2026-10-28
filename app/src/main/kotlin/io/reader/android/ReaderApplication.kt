package io.reader.android

import android.app.Application
import io.reader.android.runtime.AppCoreOwner
import io.reader.android.runtime.CoreProtocolRecoveryFeed

/** Sole manifest authority. Observing protocol status never initializes the lazy Native owner. */
class ReaderApplication : Application() {
    internal val coreProtocolRecovery = CoreProtocolRecoveryFeed()
    internal val coreOwner by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { AppCoreOwner(this, coreProtocolRecovery) }
    // No Application.onTerminate / Activity / ViewModel hook destroys Native on a physical device.
}
