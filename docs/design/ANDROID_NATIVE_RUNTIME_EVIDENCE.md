# Android Native Runtime Evidence

## Scope

This is Android host/app evidence only. It does not modify Reader-Core protocol
files, Reader-Core release gates, iOS, HarmonyOS, or native tooling outside this
Android repo.

## Packaging

The app module builds a minimal NDK shared object through AGP
`externalNativeBuild.cmake`:

- CMake entry: `app/src/main/cpp/CMakeLists.txt`
- Native source: `app/src/main/cpp/reader_native_runtime_evidence.cpp`
- Library name: `libreader_native_runtime_evidence.so`
- ABI filters: `arm64-v8a`, `x86_64`
- Current artifact: APK native library packaging via `:app:assembleDebug`

If this bridge is later moved from the app module into an Android library module,
AGP packages the same shared object in the AAR under
`jni/<abi>/libreader_native_runtime_evidence.so`. This branch does not create a
new AAR module.

## Load Boundary

`ReaderNativeRuntimeBridge.loadLibraryForApp()` is the only app-side load seam:

```kotlin
System.loadLibrary("reader_native_runtime_evidence")
```

JVM unit tests are allowed to exercise the wrapper, but an
`UnsatisfiedLinkError` on the host JVM is recorded as `JVM_HOST_NOT_DEVICE`.
That status is not device completion and must not be used as app or
instrumentation evidence.

## Host Bus Loop

`ReaderNativeRuntimeBridge.runHostBusLoopProbe(...)` sends a redacted command
count and command labels through JNI to the shared object. The native side
returns only bounded evidence:

- JNI round trip status
- native runtime identity
- dispatched command count
- `protocolMutated=false`
- `rawPayloadExported=false`

The probe is an Android host bus loop smoke. It is not a Reader-Core protocol
change and does not export raw source payloads, cookies, credentials, or user
content.

## Evidence Commands

Local JVM wrapper and report boundary:

```bash
./gradlew :app:testDebugUnitTest --tests "com.reader.android.data.nativebridge.ReaderNativeRuntimeBridgeTest" --tests "com.reader.android.coreadapter.AndroidCoreAdapterContractReportTest" --tests "com.reader.android.coreadapter.AndroidLegadoParityEvidenceRunnerTest" --no-daemon
```

APK native packaging:

```bash
./gradlew :app:assembleDebug --no-daemon
unzip -l app/build/outputs/apk/debug/app-debug.apk | grep 'libreader_native_runtime_evidence.so'
```

Device/emulator execution:

```bash
adb devices
./gradlew :app:connectedDebugAndroidTest --tests "com.reader.android.data.nativebridge.AndroidNativeRuntimeInstrumentedSmokeTest" --no-daemon
```

Only the `connectedDebugAndroidTest` command can prove
`NATIVE_JNI_HOST_BUS_LOOP_EXECUTED` on a device or emulator.

Current branch evidence: the native smoke passed on `Pixel_10_Pro_XL(AVD) - 17`
with `tests=1`, `failures=0`, `errors=0`.
