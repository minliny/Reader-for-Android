package com.reader.android.data.nativebridge

import org.json.JSONArray
import org.json.JSONObject

data class NativeRuntimePackagingEvidence(
    val libraryName: String,
    val cmakePath: String,
    val sourcePath: String,
    val appPackaging: String,
    val aarPackaging: String,
    val supportedAbis: List<String>,
    val systemLoadLibraryCall: String
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("libraryName", libraryName)
        put("cmakePath", cmakePath)
        put("sourcePath", sourcePath)
        put("appPackaging", appPackaging)
        put("aarPackaging", aarPackaging)
        put("supportedAbis", JSONArray(supportedAbis))
        put("systemLoadLibraryCall", systemLoadLibraryCall)
    }
}

data class NativeRuntimeLoadEvidence(
    val libraryName: String,
    val status: String,
    val loaded: Boolean,
    val deviceEvidence: Boolean,
    val jvmHostOnly: Boolean,
    val javaVmName: String,
    val systemLoadLibraryCall: String,
    val packaging: NativeRuntimePackagingEvidence,
    val errorClass: String? = null
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("libraryName", libraryName)
        put("status", status)
        put("loaded", loaded)
        put("deviceEvidence", deviceEvidence)
        put("jvmHostOnly", jvmHostOnly)
        put("javaVmName", javaVmName)
        put("systemLoadLibraryCall", systemLoadLibraryCall)
        put("packaging", packaging.toJson())
        errorClass?.let { put("errorClass", it) }
    }
}

data class NativeHostBusLoopEvidence(
    val evidenceId: String,
    val status: String,
    val loadEvidence: NativeRuntimeLoadEvidence,
    val nativeRuntimeIdentity: String?,
    val jniRoundTrip: Boolean,
    val hostBusLoopDispatched: Boolean,
    val dispatchedCount: Int,
    val protocolMutated: Boolean,
    val rawPayloadExported: Boolean,
    val deviceEvidence: Boolean,
    val jvmSmokeOnly: Boolean
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("evidenceId", evidenceId)
        put("status", status)
        put("loadEvidence", loadEvidence.toJson())
        nativeRuntimeIdentity?.let { put("nativeRuntimeIdentity", it) }
        put("jniRoundTrip", jniRoundTrip)
        put("hostBusLoopDispatched", hostBusLoopDispatched)
        put("dispatchedCount", dispatchedCount)
        put("protocolMutated", protocolMutated)
        put("rawPayloadExported", rawPayloadExported)
        put("deviceEvidence", deviceEvidence)
        put("jvmSmokeOnly", jvmSmokeOnly)
    }
}

object ReaderNativeRuntimeBridge {
    const val LIBRARY_NAME = "reader_native_runtime_evidence"
    const val NDK_PACKAGING_EVIDENCE_ID = "android_ndk_shared_library_packaging"
    const val SYSTEM_LOAD_LIBRARY_EVIDENCE_ID = "android_system_load_library_runner"
    const val HOST_BUS_LOOP_EVIDENCE_ID = "android_jni_host_bus_loop_probe"

    private const val SYSTEM_LOAD_LIBRARY_CALL =
        "System.loadLibrary(\"reader_native_runtime_evidence\")"

    @Volatile
    private var cachedLoadEvidence: NativeRuntimeLoadEvidence? = null

    fun packagingEvidence(): NativeRuntimePackagingEvidence =
        NativeRuntimePackagingEvidence(
            libraryName = "lib$LIBRARY_NAME.so",
            cmakePath = "app/src/main/cpp/CMakeLists.txt",
            sourcePath = "app/src/main/cpp/reader_native_runtime_evidence.cpp",
            appPackaging = "AGP externalNativeBuild.cmake packages the shared object into the APK native lib directory for each abiFilter.",
            aarPackaging = "If this app-side bridge is later split into an Android library module, AGP packages the same shared object under jni/<abi>/libreader_native_runtime_evidence.so in the AAR.",
            supportedAbis = listOf("arm64-v8a", "x86_64"),
            systemLoadLibraryCall = SYSTEM_LOAD_LIBRARY_CALL
        )

    fun loadLibraryForApp(): NativeRuntimeLoadEvidence =
        cachedLoadEvidence ?: synchronized(this) {
            cachedLoadEvidence ?: loadLibraryFresh().also { cachedLoadEvidence = it }
        }

    fun runHostBusLoopProbe(commands: List<String>): NativeHostBusLoopEvidence {
        val loadEvidence = loadLibraryForApp()
        if (!loadEvidence.loaded) {
            return NativeHostBusLoopEvidence(
                evidenceId = HOST_BUS_LOOP_EVIDENCE_ID,
                status = if (loadEvidence.jvmHostOnly) "JVM_HOST_NOT_DEVICE" else "NATIVE_LIBRARY_UNAVAILABLE",
                loadEvidence = loadEvidence,
                nativeRuntimeIdentity = null,
                jniRoundTrip = false,
                hostBusLoopDispatched = false,
                dispatchedCount = 0,
                protocolMutated = false,
                rawPayloadExported = false,
                deviceEvidence = false,
                jvmSmokeOnly = loadEvidence.jvmHostOnly
            )
        }

        val requestJson = JSONObject().apply {
            put("schema", "android-native-runtime-evidence.v1")
            put("dispatchCount", commands.size)
            put("commands", JSONArray(commands))
            put("rawPayloadExportRequested", false)
        }.toString()
        val identity = ReaderNativeRuntimeJni.nativeRuntimeIdentity()
        val response = JSONObject(ReaderNativeRuntimeJni.nativeRunHostBusLoopProbe(requestJson))

        return NativeHostBusLoopEvidence(
            evidenceId = HOST_BUS_LOOP_EVIDENCE_ID,
            status = "NATIVE_JNI_HOST_BUS_LOOP_EXECUTED",
            loadEvidence = loadEvidence,
            nativeRuntimeIdentity = identity,
            jniRoundTrip = response.getBoolean("jniRoundTrip"),
            hostBusLoopDispatched = response.getBoolean("hostBusLoopDispatched"),
            dispatchedCount = response.getInt("dispatchedCount"),
            protocolMutated = response.getBoolean("protocolMutated"),
            rawPayloadExported = response.getBoolean("rawPayloadExported"),
            deviceEvidence = loadEvidence.deviceEvidence,
            jvmSmokeOnly = false
        )
    }

    internal fun resetForTesting() {
        cachedLoadEvidence = null
    }

    private fun loadLibraryFresh(): NativeRuntimeLoadEvidence {
        val vmName = System.getProperty("java.vm.name").orEmpty()
        val jvmHostOnly = !vmName.contains("Dalvik", ignoreCase = true)
        return try {
            System.loadLibrary(LIBRARY_NAME)
            NativeRuntimeLoadEvidence(
                libraryName = "lib$LIBRARY_NAME.so",
                status = "SYSTEM_LOAD_LIBRARY_OK",
                loaded = true,
                deviceEvidence = !jvmHostOnly,
                jvmHostOnly = jvmHostOnly,
                javaVmName = vmName,
                systemLoadLibraryCall = SYSTEM_LOAD_LIBRARY_CALL,
                packaging = packagingEvidence()
            )
        } catch (error: UnsatisfiedLinkError) {
            NativeRuntimeLoadEvidence(
                libraryName = "lib$LIBRARY_NAME.so",
                status = if (jvmHostOnly) "JVM_HOST_NOT_DEVICE" else "SYSTEM_LOAD_LIBRARY_FAILED",
                loaded = false,
                deviceEvidence = false,
                jvmHostOnly = jvmHostOnly,
                javaVmName = vmName,
                systemLoadLibraryCall = SYSTEM_LOAD_LIBRARY_CALL,
                packaging = packagingEvidence(),
                errorClass = error::class.java.name
            )
        }
    }
}
