package com.reader.android.data.nativebridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderNativeRuntimeBridgeTest {

    @Test
    fun `packaging evidence binds CMake source ABIs and loadLibrary call`() {
        val packaging = ReaderNativeRuntimeBridge.packagingEvidence()

        assertEquals("libreader_native_runtime_evidence.so", packaging.libraryName)
        assertEquals("app/src/main/cpp/CMakeLists.txt", packaging.cmakePath)
        assertEquals("app/src/main/cpp/reader_native_runtime_evidence.cpp", packaging.sourcePath)
        assertEquals(listOf("arm64-v8a", "x86_64"), packaging.supportedAbis)
        assertTrue(packaging.appPackaging.contains("externalNativeBuild.cmake"))
        assertTrue(packaging.aarPackaging.contains("jni/<abi>/libreader_native_runtime_evidence.so"))
        assertEquals("System.loadLibrary(\"reader_native_runtime_evidence\")", packaging.systemLoadLibraryCall)
    }

    @Test
    fun `JVM host load failure is recorded as not device evidence`() {
        ReaderNativeRuntimeBridge.resetForTesting()

        val load = ReaderNativeRuntimeBridge.loadLibraryForApp()

        assertFalse(load.loaded)
        assertFalse(load.deviceEvidence)
        assertTrue(load.jvmHostOnly)
        assertEquals("JVM_HOST_NOT_DEVICE", load.status)
        assertEquals("java.lang.UnsatisfiedLinkError", load.errorClass)
    }

    @Test
    fun `host bus loop is not claimed when only JVM unit test runs`() {
        ReaderNativeRuntimeBridge.resetForTesting()

        val evidence = ReaderNativeRuntimeBridge.runHostBusLoopProbe(
            listOf("source.bootstrap", "runtime.tick", "runtime.shutdown")
        )

        assertEquals(ReaderNativeRuntimeBridge.HOST_BUS_LOOP_EVIDENCE_ID, evidence.evidenceId)
        assertEquals("JVM_HOST_NOT_DEVICE", evidence.status)
        assertFalse(evidence.jniRoundTrip)
        assertFalse(evidence.hostBusLoopDispatched)
        assertEquals(0, evidence.dispatchedCount)
        assertFalse(evidence.protocolMutated)
        assertFalse(evidence.rawPayloadExported)
        assertFalse(evidence.deviceEvidence)
        assertTrue(evidence.jvmSmokeOnly)
    }
}
