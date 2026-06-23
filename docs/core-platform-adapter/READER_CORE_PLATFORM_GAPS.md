# Reader-Core Platform Gap Notes - Android

Snapshot date: 2026-06-23

This document records Android-owned gaps for Reader-Core platform evidence. It is a
host-repo document only and does not modify Reader-Core gates.

## Current Host Evidence

| Area | Current state | Evidence path |
| --- | --- | --- |
| Adapter contract report | Exports Core-aligned Android feature IDs and device-executed runtime smoke status | `app/src/main/kotlin/com/reader/android/coreadapter/AndroidCoreAdapterContractReport.kt` |
| Complete evidence runner | Covers all Reader-Core Android required feature IDs for archive, localFileAccess, markupParser, feedParser, textEncodingDetector, and runtimeHost | `app/src/main/kotlin/com/reader/android/coreadapter/AndroidLegadoParityEvidence.kt` |
| jsoup markup adapter | CSS selector, bounded XPath, XML selector, attribute extraction, script/style suppression | `app/src/main/kotlin/com/reader/android/data/adapter/JsoupMarkupParserAdapter.kt` |
| Android platform runtime adapters | ContentResolver/SAF, CookieManager, WebView, and AndroidKeyStore clean-room wrappers passed Pixel_10_Pro_XL AVD smoke for WebView DOM, CookieManager mirror, Keystore revoke, and SAF denied-permission redaction | `app/src/main/kotlin/com/reader/android/data/adapter/AndroidPlatformRuntimeAdapters.kt` |
| Internet permission | Manifest declares Android network permission for future authorized OkHttp/live runs | `app/src/main/AndroidManifest.xml` |
| Contract/evidence tests | Focused adapter evidence tests and connected runtime smoke passed after duplicate dirty UI sources were removed | `app/src/test/kotlin/com/reader/android/coreadapter/AndroidLegadoParityEvidenceRunnerTest.kt` |

## Android Evidence Gap Status

| Gap | Owner | Current state | Close evidence |
| --- | --- | --- | --- |
| S3 local book file access | Android host app | `MEASURED_PASS` for redacted content URI, persistable permission audit, and denied-permission mapping; SAF denied-permission redaction executed on Pixel_10_Pro_XL AVD; full picker-grant UX still needs smoke | Core-aligned `localFileAccess` feature IDs are present in `AndroidLegadoParityEvidenceRunner` |
| S3 TXT detector | Android adapter | `MEASURED_PASS` for UTF BOM, UTF-8, GB18030/GBK-like, and fallback fixtures | `textEncodingDetector` evidence cases are exported locally |
| S3 EPUB/archive adapter | Android adapter | `MEASURED_PASS` for EPUB rootfile, OPF manifest/spine, reading order, and safe-entry-path | `archive` evidence cases are exported locally |
| S4 parser/RSS | Android parser/network adapter | `MEASURED_PASS` for jsoup CSS/bounded XPath/attribute extraction and RSS/Atom/JSON Feed local fixtures | `markupParser` and `feedParser` evidence cases are exported locally |
| S5 runtime host | Android WebView/CookieManager/Keystore | `DEVICE_EXECUTED_INSTRUMENTED`; WebView DOM, JavaScript evaluation, CookieManager mirror redaction, AndroidKeyStore save/load/revoke, and SAF denied-permission redaction passed on Pixel_10_Pro_XL AVD | Runtime evidence can feed Core intake; live authorized source and full host UI flows remain separate |
| S10 release intake | Product governance + CI | Android can emit complete local/headless evidence, but cannot mutate Reader-Core `production_release` | CI artifact, host smoke report, and external evidence ledger entry are still required |

## Non-Goals

- Do not copy, translate, or adapt Legado Android implementation code.
- Do not store raw cookie values, credentials, authorization headers, query strings, HTML bodies, local file paths, or private book content.
- Do not mark Reader-Core release gates as passed from this repository.
- Do not use existing dirty UI/design work as runtime evidence without fresh validation.

## Current Boundary

`Core+Android=Legado` is now represented as Android local/headless platform
evidence plus emulator runtime smoke evidence:

- `canFeedCorePlatformEvidence=true`
- `deviceExecutorReady=true`
- `deviceExecutorUsed=true`
- `externalNetworkUsed=false`
- `readerCoreRootArtifactsMutated=false`
- `canMutateProductionReleaseGate=false`

This is enough to close the Android-owned evidence shape gap and the emulator
runtime smoke gap for the four exercised platform seams. It is not enough to
claim release-grade product parity until full SAF picker grant UX, live
authorized book-source, and host UI smoke artifacts are produced.

## Latest Validation

Executed:

```bash
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :app:testDebugUnitTest \
  --tests "com.reader.android.coreadapter.AndroidLegadoParityEvidenceRunnerTest" \
  --tests "com.reader.android.coreadapter.AndroidCoreAdapterContractReportTest" \
  --tests "com.reader.android.data.adapter.JsoupMarkupParserAdapterTest" \
  --no-daemon

JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.reader.android.data.adapter.AndroidPlatformRuntimeInstrumentedSmokeTest \
  --no-daemon
```

Result: focused unit tests passed; connected runtime smoke finished 4 tests on
`Pixel_10_Pro_XL(AVD) - 17` with 0 failures after replacing regex-only content
URI redaction with structured `Uri` redaction.
