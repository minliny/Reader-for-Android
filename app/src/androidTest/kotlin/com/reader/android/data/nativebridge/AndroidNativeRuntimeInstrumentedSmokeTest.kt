package com.reader.android.data.nativebridge

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidNativeRuntimeInstrumentedSmokeTest {

    @Test
    fun sharedObjectLoadsAndRunsJniHostBusLoopProbe() {
        ReaderNativeRuntimeBridge.resetForTesting()

        val load = ReaderNativeRuntimeBridge.loadLibraryForApp()
        assertTrue(load.loaded)
        assertTrue(load.deviceEvidence)
        assertFalse(load.jvmHostOnly)
        assertEquals("SYSTEM_LOAD_LIBRARY_OK", load.status)

        val evidence = ReaderNativeRuntimeBridge.runHostBusLoopProbe(
            listOf("source.bootstrap", "runtime.tick", "runtime.shutdown")
        )

        assertEquals("NATIVE_JNI_HOST_BUS_LOOP_EXECUTED", evidence.status)
        assertTrue(evidence.nativeRuntimeIdentity!!.contains("reader-native-runtime-evidence/1"))
        assertTrue(evidence.jniRoundTrip)
        assertTrue(evidence.hostBusLoopDispatched)
        assertEquals(3, evidence.dispatchedCount)
        assertFalse(evidence.protocolMutated)
        assertFalse(evidence.rawPayloadExported)
        assertTrue(evidence.deviceEvidence)
        assertFalse(evidence.jvmSmokeOnly)
    }
}
