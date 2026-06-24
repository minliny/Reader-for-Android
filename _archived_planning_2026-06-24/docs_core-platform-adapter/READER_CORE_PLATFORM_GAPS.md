# Reader-Core Platform Gap Notes - Android

Snapshot date: 2026-06-24

This document records Android-owned gaps for Reader-Core platform evidence. It is a
host-repo document only and does not modify Reader-Core gates.

## Current Host Evidence

| Area | Current state | Evidence path |
| --- | --- | --- |
| Adapter contract report | Exports Core-aligned Android feature IDs and device-executed runtime smoke status; now includes the `readerShell` kind | `app/src/main/kotlin/com/reader/android/coreadapter/AndroidCoreAdapterContractReport.kt` |
| Complete evidence runner | Covers all Reader-Core Android required feature IDs for archive, localFileAccess, markupParser, feedParser, textEncodingDetector, runtimeHost, and readerShell | `app/src/main/kotlin/com/reader/android/coreadapter/AndroidLegadoParityEvidence.kt` |
| jsoup markup adapter + rule engine | CSS selector, bounded XPath, XML selector, attribute extraction, script/style suppression, and a clean-room `RuleExpression`/`RuleEngine` now wired into the four fetch parsers (`SearchParser`/`BookInfoParser`/`TOCParser`/`ContentParser`) behind a rule-presence branch with regex fallback | `app/src/main/kotlin/com/reader/android/data/adapter/JsoupMarkupParserAdapter.kt`, `RuleExpression.kt`, `app/src/main/kotlin/com/reader/android/data/network/{SearchParser,BookInfoParser,TOCParser,ContentParser}.kt` |
| OkHttp host adapter | GET + POST (`PostRequestBody`→OkHttp), `ScopedOkHttpCookieJar` delegating to the shared `CookieStore`, and a Core-`HTTPClient`-aligned `OkHttpAdapter` with `NetworkErrorMapper` | `app/src/main/kotlin/com/reader/android/data/network/{HttpTransport,OkHttpAdapter,PostRequestBody}.kt` |
| Android platform runtime adapters | ContentResolver/SAF, CookieManager, WebView, AndroidKeyStore, real `AndroidWebRuntimeAdapter` (suspend-bridged), `WebDavCredentialStore` (keystore-backed), and the clean-room `ComposeReaderShellHost` | `app/src/main/kotlin/com/reader/android/data/adapter/{AndroidPlatformRuntimeAdapters,AndroidWebRuntimeAdapter,WebDavCredentialStore,ReaderShellHost}.kt` |
| Reader-shell host | Clean-room `ReaderShellHost` (TXT pagination + jsoup HTML sanitization + CSP, JS off by default, CFI future slot); wired into `ContentAdapterShell` real mode; `ReaderContentAdapter.level = READY_HOST_SHELL` | `app/src/main/kotlin/com/reader/android/data/adapter/ReaderShellHost.kt`, `app/src/main/kotlin/com/reader/android/ui/reader/ContentAdapterShell.kt` |
| Internet permission | Manifest declares Android network permission for future authorized OkHttp/live runs | `app/src/main/AndroidManifest.xml` |
| Contract/evidence tests | Focused adapter evidence tests and connected runtime smoke passed; rule-engine, cookie-jar, error-mapper, WebDAV-credential, and reader-shell unit tests green | `app/src/test/kotlin/com/reader/android/coreadapter/AndroidLegadoParityEvidenceRunnerTest.kt` |

## Android Evidence Gap Status

| Gap | Owner | Current state | Close evidence |
| --- | --- | --- | --- |
| S3 local book file access | Android host app | `MEASURED_PASS` for redacted content URI, persistable permission audit, and denied-permission mapping; SAF denied-permission redaction executed on Pixel_10_Pro_XL AVD; full picker-grant UX still needs smoke | Core-aligned `localFileAccess` feature IDs are present in `AndroidLegadoParityEvidenceRunner` |
| S3 TXT detector | Android adapter | `MEASURED_PASS` for UTF BOM, UTF-8, GB18030/GBK-like, and fallback fixtures | `textEncodingDetector` evidence cases are exported locally |
| S3 EPUB/archive adapter | Android adapter | `MEASURED_PASS` for EPUB rootfile, OPF manifest/spine, reading order, and safe-entry-path | `archive` evidence cases are exported locally |
| S4 parser/RSS + jsoup rule pipeline | Android parser/network adapter | `MEASURED_PASS` — jsoup CSS/bounded XPath/attribute extraction AND the clean-room `RuleEngine` is now wired into the live fetch parsers (rule-presence branch, regex fallback); RSS/Atom/JSON Feed local fixtures | `markupParser` and `feedParser` evidence cases; `RuleEngineTest`, parser contract tests |
| S4 HTTP host adapter (OkHttp POST + cookie jar) | Android network adapter | `MEASURED_PASS` — `OkHttpTransport` supports GET+POST, `ScopedOkHttpCookieJar` mirrors cookies to the shared `CookieStore`, `OkHttpAdapter` is Core-`HTTPClient`-aligned with `NetworkErrorMapper`; live fetch stays gated behind `AppProvider.isNetworkAllowed` | `OkHttpAdapter`, `ScopedOkHttpCookieJar`, `NetworkErrorMapper` + unit tests |
| S5 runtime host (WebView + CookieManager + Keystore) | Android WebView/CookieManager/Keystore | `DEVICE_EXECUTED_INSTRUMENTED`; WebView DOM, JavaScript evaluation, CookieManager mirror redaction, AndroidKeyStore save/load/revoke, real `AndroidWebRuntimeAdapter` (suspend-bridged), `WebDavCredentialStore` keystore persistence, and SAF denied-permission redaction passed on Pixel_10_Pro_XL AVD | Runtime evidence feeds Core intake; the OkHttp cookie jar and WebView host now share one `CookieStore` (live mirror) |
| Reader-shell rendering host | Android adapter | `MEASURED_PASS` — clean-room `ReaderShellHost` paginates TXT, sanitizes HTML (jsoup `Safelist`, scripts/iframes stripped), injects CSP `default-src 'none'; script-src 'none'`, JS off by default; `ContentAdapterShell` real mode routes through it. Readium/EPUB-CFI deferred behind the same contract (CFI slot null) | `readerShell` evidence cases; `ComposeReaderShellHostTest` |
| Room (structured storage) | Android host app | Already complete — `@Database` v4 wired via `AppProvider` with DAOs for progress/cache/bookmarks/sync-log | `app/src/main/kotlin/com/reader/android/data/storage/ReadingProgress.kt`, `AppProvider.kt` |
| S10 release intake | Product governance + CI | Android can emit complete local/headless evidence, but cannot mutate Reader-Core `production_release` | CI artifact, host smoke report, and external evidence ledger entry are still required |

## Non-Goals

- Do not copy, translate, or adapt Legado Android implementation code.
- Do not store raw cookie values, credentials, authorization headers, query strings, HTML bodies, local file paths, or private book content.
- Do not mark Reader-Core release gates as passed from this repository.
- Do not use existing dirty UI/design work as runtime evidence without fresh validation.
- Do not pull Readium into the host; the reader shell is clean-room. EPUB-CFI
  navigation remains a future opt-in that can implement the same `ReaderShellHost`
  contract without changing call sites.

## Current Boundary

`Core+Android=Legado` is now represented as Android local/headless platform
evidence plus emulator runtime smoke evidence, with the host adapters wired into
the live (gated) fetch path:

- `canFeedCorePlatformEvidence=true`
- `deviceExecutorReady=true`
- `deviceExecutorUsed=true`
- `externalNetworkUsed=false`
- `readerCoreRootArtifactsMutated=false`
- `canMutateProductionReleaseGate=false`

This closes the Android-owned evidence shape gap and the emulator runtime smoke
gap for the exercised platform seams, AND wires the host adapters (jsoup rule
pipeline, OkHttp POST + scoped cookie jar, WebView runtime, keystore WebDAV
persistence, clean-room reader shell) into the real fetch path behind the
`AppProvider.isNetworkAllowed` gate (default off). Live authorized source runs,
full SAF picker grant UX, and host UI smoke artifacts remain the next evidence.

## Latest Validation

Executed:

```bash
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :app:testDebugUnitTest \
  --tests "com.reader.android.coreadapter.AndroidLegadoParityEvidenceRunnerTest" \
  --tests "com.reader.android.coreadapter.AndroidCoreAdapterContractReportTest" \
  --tests "com.reader.android.data.adapter.JsoupMarkupParserAdapterTest" \
  --tests "com.reader.android.data.adapter.RuleEngineTest" \
  --tests "com.reader.android.data.network.ScopedOkHttpCookieJarTest" \
  --tests "com.reader.android.data.network.NetworkErrorMapperTest" \
  --tests "com.reader.android.data.adapter.WebDavCredentialStoreTest" \
  --tests "com.reader.android.data.adapter.ComposeReaderShellHostTest" \
  --no-daemon
```

Result: focused unit tests passed. The full `./gradlew test` suite is green
except for pre-existing `FrontendInput*` manifest tests that fail due to dirty
`docs/ui-design/frontend-input/frontend-demo-draft/*` files (unrelated to host
adapters).

