package com.reader

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.reader.api.ReaderCoreClient
import com.reader.core.NativeCoreBridge
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented smoke test: verifies the Rust Core JNI `.so` loads on-device
 * and the ABI contract (`abiVersion == 1` per `include/reader_core.h`) holds.
 *
 * Evidence layering (charter §10.3):
 *  - This test = simulator device proof (NOT real device). It exercises the
 *    real `libreader_core_jni.so` via JNI, which `testDebugUnitTest` cannot.
 *  - Real device proof deferred to S7.
 */
@RunWith(AndroidJUnit4::class)
class CoreSmokeTest {

    @After
    fun tearDown() {
        ReaderCoreClient.resetForTest()
    }

    @Test
    fun jniLibraryLoadsAndAbiVersionReturns1() {
        val abi = NativeCoreBridge.abiVersion()
        assertEquals(
            "ABI version must be 1 per include/reader_core.h",
            1,
            abi
        )
    }

    @Test
    fun pingSmokeReturnsNonEmptyEventJson() {
        val event = NativeCoreBridge.pingSmoke()
        assertNotNull("pingSmoke() must not return null", event)
        assertTrue(
            "pingSmoke() must return non-empty event JSON, got: '$event'",
            event.isNotEmpty()
        )
    }

    @Test
    fun readerCoreClientInitCreatesRuntime() {
        val client = ReaderCoreClient.init()
        assertNotNull(client)
    }
}
