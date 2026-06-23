# Reader-Core Platform Gap Notes - Android

Snapshot date: 2026-06-23

This document records Android-owned gaps for Reader-Core platform evidence. It is a
host-repo document only and does not modify Reader-Core gates.

## Current Host Evidence

| Area | Current state | Evidence path |
| --- | --- | --- |
| Adapter contract report | Descriptor/exporter exists for Core-aligned evidence slots | `app/src/main/kotlin/com/reader/android/coreadapter/AndroidCoreAdapterContractReport.kt` |
| Contract report tests | Focused tests exist, but full Gradle validation is currently blocked by unrelated dirty UI compile conflicts | `app/src/test/kotlin/com/reader/android/coreadapter/AndroidCoreAdapterContractReportTest.kt` |
| Non-UI backend | README states non-UI backend exists, but current working tree is dirty and needs fresh validation before release claims | `README.md` |

## Remaining Gaps

| Gap | Owner | Current blocker | Required close evidence |
| --- | --- | --- | --- |
| S3 local book file access | Android host app | ContentResolver/SAF persistable URI permission smoke is not attached | Matrix-compatible `localFileAccess` report with permission-denied and granted cases |
| S3 TXT detector | Android adapter | Descriptor references detector capability, but current report does not execute native/content URI byte reads | `textEncodingDetector` metadata/expected/matrix/regressionResult with UTF BOM, UTF-8, GB18030, fallback |
| S3 EPUB/archive adapter | Android adapter | Archive/EPUB parsing is descriptor/test-contract only for this evidence path | `archive` adapter run report with safe-entry-path and OPF/nav/ncx coverage |
| S4 parser/RSS | Android parser/network adapter | jsoup/OkHttp-or-Ktor execution has not been exported as Core matrix-compatible evidence | `markupParser` and `feedParser` run reports plus redacted RSS replay |
| S5 runtime host | Android WebView/CookieManager/Keystore | Runtime CI evidence IDs are present but `notExecuted`; no WebView/CookieManager/Keystore smoke has run | `webview_dom_platform_smoke_runner`, `webview_cookie_mirror_audit`, `secure_storage_platform_audit` |
| S10 release intake | Product governance + CI | Android can emit evidence, but cannot mutate Reader-Core `production_release` | CI artifact, host smoke report, and external evidence ledger entry |

## Non-Goals

- Do not copy, translate, or adapt Legado Android implementation code.
- Do not store raw cookie values, credentials, authorization headers, query strings, HTML bodies, local file paths, or private book content.
- Do not mark Reader-Core release gates as passed from this repository.
- Do not use existing dirty UI/design work as runtime evidence without fresh validation.

## Next Small Android Slice

Stabilize the current dirty UI compile conflicts enough to run
`./gradlew :app:testDebugUnitTest --tests "com.reader.android.coreadapter.AndroidCoreAdapterContractReportTest"`.
After that, add one measured Android adapter run report, preferably `localFileAccess`
or `textEncodingDetector`, before attempting WebView/CookieManager runtime smoke.
