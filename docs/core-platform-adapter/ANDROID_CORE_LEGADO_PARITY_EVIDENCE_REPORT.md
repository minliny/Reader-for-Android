# Android Core+Legado Parity Evidence Report

Snapshot date: 2026-06-23

Status: `DEVICE_EXECUTED_INSTRUMENTED`

This report records the Android-owned platform evidence added for the
Reader-Core Android adapter manifest. It is clean-room work: no Legado
implementation code was copied, translated, or adapted. The local Legado checkout
was used only to identify comparable platform capability categories such as
jsoup, OkHttp, Room, WebView/CookieManager, and Android keystore boundaries.

## Implemented Evidence

| Area | Current evidence | Files |
| --- | --- | --- |
| Core manifest alignment | Android required feature IDs are mirrored from Reader-Core: archive, localFileAccess, markupParser, feedParser, textEncodingDetector, runtimeHost | `app/src/main/kotlin/com/reader/android/coreadapter/AndroidLegadoParityEvidence.kt` |
| jsoup markup adapter | CSS selector, bounded HTML XPath, bounded XML XPath, attribute extraction, script/style suppression | `app/src/main/kotlin/com/reader/android/data/adapter/JsoupMarkupParserAdapter.kt` |
| OkHttp/feed boundary | Existing OkHttp transport is referenced by the Core-aligned feed evidence path; RSS/Atom/JSON Feed evidence uses local fixtures and no live network | `app/src/main/kotlin/com/reader/android/data/network/HttpTransport.kt`, `AndroidLegadoParityEvidence.kt` |
| Local book Android boundary | ContentResolver/SAF wrapper, content URI descriptor, persistable read permission audit, denied-permission mapping, TXT BOM/UTF-8/GB18030/fallback evidence, EPUB container/OPF/safe-entry evidence | `app/src/main/kotlin/com/reader/android/data/adapter/AndroidPlatformRuntimeAdapters.kt`, `AndroidLegadoParityEvidence.kt`, existing local-book adapters |
| Runtime host boundary | Android WebView wrapper, CookieManager store, AndroidKeyStore credential store, JavaScript, cookieJar, sessionPersistence, loginFlow, snapshotWrite evidence IDs with Pixel_10_Pro_XL AVD instrumented smoke validation | `app/src/main/kotlin/com/reader/android/data/adapter/AndroidPlatformRuntimeAdapters.kt`, `app/src/androidTest/kotlin/com/reader/android/data/adapter/AndroidPlatformRuntimeInstrumentedSmokeTest.kt`, `AndroidLegadoParityEvidence.kt`, existing runtime/cookie contracts |
| Release gate safety | Android evidence can feed Core platform evidence intake but cannot mutate Reader-Core root release gates | `AndroidLegadoParityEvidenceBundle.canFeedCorePlatformEvidence` |

## Core Required Feature Coverage

| Adapter kind | Required feature IDs | Status |
| --- | --- | --- |
| `archive` | `epub.zip`, `localbook.archive`, `safe-entry-path` | `MEASURED_PASS` |
| `localFileAccess` | `localbook.content-uri`, `persistable-uri-permission`, `permission-denied-mapping` | `MEASURED_PASS` |
| `markupParser` | `html.css`, `html.xpath`, `xml.xpath`, `attribute-extraction` | `MEASURED_PASS` |
| `feedParser` | `rss`, `atom`, `json-feed`, `feed-pagination-metadata`, `cookie-login-diagnostic` | `MEASURED_PASS` |
| `textEncodingDetector` | `txt.bom`, `txt.utf8`, `txt.gb18030`, `txt.fallback` | `MEASURED_PASS` |
| `runtimeHost` | `webView`, `javascript`, `cookieJar`, `sessionPersistence`, `loginFlow`, `snapshotWrite` | `DEVICE_EXECUTED_INSTRUMENTED` on Pixel_10_Pro_XL AVD |

## Execution Boundary

The runtime execution mode is `DEVICE_EXECUTED_INSTRUMENTED`.

| Field | Value |
| --- | --- |
| `deviceExecutorReady` | `true` |
| `deviceExecutorUsed` | `true` |
| `externalNetworkUsed` | `false` |
| `readerCoreRootArtifactsMutated` | `false` |
| `canMutateProductionReleaseGate` | `false` |
| `cleanRoomMaintained` | `true` |
| `externalGplCodeCopied` | `false` |
| `legadoSourceCopied` | `false` |

This closes the Android repository's local/headless Core evidence gap and the
emulator runtime smoke gap for WebView DOM, CookieManager mirror, AndroidKeyStore
revoke, and SAF denied-permission redaction. It does not claim that live external
book-source flows, full SAF picker UX, or host UI/product flows have reached
Legado parity.

## Validation

Commands executed:

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

Result: focused unit tests passed after duplicate dirty UI sources were removed;
the connected AndroidTest ran on `Pixel_10_Pro_XL(AVD) - 17` and finished 4
tests with 0 failures after fixing content URI redaction.

## Remaining Product Evidence

For a release-grade `Core+Android=Legado` claim, the next evidence must cover
live source and host-product behavior:

| Evidence | Required proof |
| --- | --- |
| WebView DOM live source smoke | Android WebView loads an explicitly authorized live page and exports redacted DOM snapshot |
| CookieManager/OkHttp live mirror | WebView `CookieManager` and OkHttp cookie jar mirror the same opaque scoped cookie reference during authorized source execution |
| SAF file picker grant smoke | Host file picker grants persistable URI permission; denial path already maps fail-closed |
| Live authorized book-source run | Explicitly authorized external run with redacted audit, no anti-bot bypass, and source-backed diff |
| Host UI smoke | Bookshelf, reader shell, import, downloads, TTS, notifications, settings, login/session smoke on device/emulator |
