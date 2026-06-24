# Reader-Core Reality Audit for Android

**Date**: 2026-05-13
**Core HEAD**: 5269081 (main)
**Audit Method**: File-system inspection of `/Users/minliny/Documents/Reader-Core`

---

## 1. Repository Overview

Reader-Core is a Swift 5.9 cross-platform reading core engine (macOS + Linux). It is **not** a full reader app. It has 1312+ tests, 0 failures, and is in Release Candidate hardening.

## 2. Core Public API Audit

### 2.1 Public Library Modules (from Core/Package.swift)

| Module | Stability | Purpose |
|--------|----------|---------|
| ReaderCoreModels | STABLE | BookSource, typed rules, DTOs |
| ReaderCoreProtocols | STABLE | Parser/Network/Storage/Cache contracts |
| ReaderCoreParser | STABLE | NonJSParserEngine, CSSExecutor |
| ReaderCoreFoundation | STABLE | JSONValue, DynamicCodingKey |
| ReaderCoreNetwork | EXPERIMENTAL | HTTP client, cookie jar, session |
| ReaderCoreCache | EXPERIMENTAL | Response caching |
| ReaderPlatformAdapters | EXPERIMENTAL | URLSession, Keychain, WKWebView (iOS/macOS) |
| ReaderCoreJSRenderer | EXPERIMENTAL | JSC-based JS execution |

### 2.2 Public Protocols (Facade)

| Protocol | Module | Status | Evidence |
|----------|--------|--------|----------|
| BookSourceRepository | ReaderCoreProtocols | READY | Contracts.swift:4-8 |
| BookSourceDecoder | ReaderCoreProtocols | READY | Contracts.swift:10-12 |
| SearchService | ReaderCoreProtocols | READY | Contracts.swift:14-16 |
| TOCService | ReaderCoreProtocols | READY | Contracts.swift:18-20 |
| ContentService | ReaderCoreProtocols | READY | Contracts.swift:22-24 |
| RuleScheduler | ReaderCoreProtocols | READY | ParserProtocols.swift:25-27 |
| SearchParser | ReaderCoreProtocols | READY | ParserProtocols.swift:29-31 |
| TOCParser | ReaderCoreProtocols | READY | ParserProtocols.swift:33-35 |
| ContentParser | ReaderCoreProtocols | READY | ParserProtocols.swift:37-39 |
| BookInfoParser | ReaderCoreProtocols | READY | ParserProtocols.swift:41-43 |

### 2.3 Public DTOs (Frozen)

| DTO | Module | Fields | Status | Evidence |
|-----|--------|--------|--------|----------|
| BookSource | ReaderCoreModels | 20+ fields, Codable | READY (FROZEN) | BookSource.swift:175 |
| SearchRule | ReaderCoreModels | 12 fields | READY (FROZEN) | BookSource.swift:6 |
| BookInfoRule | ReaderCoreModels | 13 fields | READY (FROZEN) | BookSource.swift:50 |
| TocRule | ReaderCoreModels | 10 fields | READY (FROZEN) | BookSource.swift:87 |
| ContentRule | ReaderCoreModels | 11 fields | READY (FROZEN) | BookSource.swift:111 |
| ExploreRule | ReaderCoreModels | 10 fields | READY (FROZEN) | BookSource.swift:135 |
| ReviewRule | ReaderCoreModels | 5 fields | READY (FROZEN) | BookSource.swift:159 |
| SearchResultItem | ReaderCoreModels | 9 fields | READY (FROZEN) | DTO_FREEZE_MATRIX.md |
| TOCItem | ReaderCoreParser | title, url, children | READY (FROZEN) | DTO_FREEZE_MATRIX.md |
| ContentPage | ReaderCoreProtocols | content, title, nextPageUrl | READY (FROZEN) | DTO_FREEZE_MATRIX.md |
| BookInfo | ReaderCoreProtocols | name, author, intro, kind, coverUrl, tocUrl... | READY (FROZEN) | ParserProtocols.swift:45-58 |
| SearchQuery | ReaderCoreModels | keyword, page | READY (FROZEN) | DTO_FREEZE_MATRIX.md |
| JSONValue | ReaderCoreFoundation | string, number, bool, object, array, null | READY | JSONValue.swift |

### 2.4 Error Taxonomy (Frozen)

| Error Type | Codes | Status | Evidence |
|------------|-------|--------|----------|
| MappedReaderError | network, parse, timeout, notFound, unauthorized, forbidden, unknown | READY (FROZEN) | ERROR_TAXONOMY_FREEZE.md |
| CSSExecutorError | selectorParse, xpathError, unsupportedSelector | READY (FROZEN) | ERROR_TAXONOMY_FREEZE.md |
| ReaderErrorCode | 7 codes | READY (FROZEN) | ERROR_TAXONOMY_FREEZE.md |
| ReaderFailureStage | search, toc, content, bookInfo | READY (FROZEN) | ERROR_TAXONOMY_FREEZE.md |

### 2.5 Parser Engine

| Capability | Status | Evidence |
|------------|--------|----------|
| NonJSParserEngine (public entry point) | READY | Core/Package.swift + ParserProtocols.swift |
| Search parsing (HTML→[SearchResultItem]) | READY | NonJSParserEngine.swift |
| TOC parsing (HTML→[TOCItem]) | READY | NonJSParserEngine.swift |
| Content parsing (HTML→ContentPage) | READY | NonJSParserEngine.swift |
| BookInfo parsing (HTML→BookInfo) | READY | NonJSParserEngine.swift |
| CSS Selector evaluation | READY | CSSExecutor.swift |
| XPath evaluation | READY | SimpleXPathEvaluator.swift |
| Regex/replace pipeline | READY | Tests verified |
| URL normalization | READY | URLNormalizer.swift |
| Charset detection (UTF-8/GBK/Big5) | READY | HTMLTextDecoder.swift |

### 2.6 Integration Contract

| Item | Status | Evidence |
|------|--------|----------|
| Minimum integration flow documented | READY | reader_core_integration_contract.md |
| Allowed/Forbidden dependencies | READY | reader_core_integration_contract.md sec 2-3 |
| Versioning policy | READY | reader_core_integration_contract.md sec 8 |
| Platform adapter responsibility list | READY | reader_core_integration_contract.md sec 6 |
| Handoff checklist (12 adapter + 5 app items) | READY | LEGADO_OWNERSHIP_MATRIX.md |

---

## 3. Capability-by-Capability Audit

| Core Capability | Status | Evidence | Android Impact | Required Upstream Work |
|---|---|---|---|---|
| BookSource import/decode from JSON | READY | BookSource is Codable, decoder in Contracts | Android can decode BookSource JSON directly | None |
| BookSource validation | READY | BookSource has typed fields + unknownFields | Android can implement validation UI | None |
| Search (NonJS, static) | READY | NonJSParserEngine.parseSearchResponse | Android fetches HTML, passes to Core | None |
| Detail/BookInfo (NonJS) | READY | NonJSParserEngine.parseBookInfoResponse | Android fetches HTML, passes to Core | None |
| TOC (NonJS) | READY | NonJSParserEngine.parseTOCResponse | Android fetches HTML, passes to Core | None |
| Content/Chapter (NonJS) | READY | NonJSParserEngine.parseContentResponse | Android fetches HTML, passes to Core | None |
| JS Execution (dynamic) | PARTIAL | JSExecutionAdapter protocol exists, but requires platform engine | Android must implement QuickJS/Hermes adapter | None (adapter impl is Android's job) |
| WebView runtime | PARTIAL | WebViewRuntimeProtocol exists, iOS WKWebView adapter exists | Android needs Android WebView adapter | None (adapter impl is Android's job) |
| Cookie/Login | PARTIAL | CookieJar protocol, session manager exist, login model in BookSource | Android needs OkHttp CookieJar + WebView login UI | None |
| Explore/RSS/Subscription | PARTIAL | ExploreRule model exists, pipeline contracts defined | Android needs UI + explore pipeline integration | None |
| Local Books | PARTIAL | LocalBook metadata model, TXT/EPUB contracts exist | Android needs file picker, EPUB parser, TXT parser | None |
| TTS | PARTIAL | TTSAdapter contract exists | Android needs Android TTS engine integration | None (contract exists) |
| WebDAV Backup | PARTIAL | WebDAV layout schema exists, backup models defined | Android needs WebDAV client, WorkManager scheduling | None (schema exists) |
| Progress Cloud Sync | PARTIAL | Sync models exist, transport protocol defined | Android needs sync trigger, conflict resolution UI | None (protocol exists) |
| Remote WebDAV Books | PARTIAL | Model exists, transport protocol defined | Android needs WebDAV book browsing UI | None (model exists) |
| Capability Matrix | READY | LEGADO_CAPABILITY_PARITY_MATRIX.yml (139 caps) | Android can reference for compatibility tracking | None |
| Regression Tests (Core) | READY | 1312+ tests, 0 failures | Android needs own UI/integration tests | None |
| Conformance Tests | READY | Core has conformance test plan | Android should run Core conformance tests against adapters | None |
| NonJS Parser (full Real World compatibility) | READY | Multiple real-pass stages (Case022-030) | Android inherits full NonJS parsing | None |
| Platform HTTP Adapter | PARTIAL | URLSession adapter exists (iOS), no OkHttp adapter | Android must implement OkHttp/kTor adapter | None |

---

## 4. What Reader-Core DOES NOT Provide

From `READER_CORE_SCOPE_AND_BOUNDARIES.md` and `reader_core_integration_contract.md`:

- UI / rendering / reader view → **Android responsibility**
- Book source discovery → **Android responsibility**
- Cloud sync implementation → **Android responsibility**
- Reading progress tracking → **Android responsibility**
- TTS implementation → **Android responsibility**
- EPUB/TXT parsing implementation → **Android responsibility**
- WAF bypass / anti-crawling → **Not in scope**
- Automatic login → **Android responsibility (manual cookie injection only)**
- App release/distribution → **Android responsibility**

---

## 5. Gap Analysis: What Android Needs That Core Doesn't Provide

| Gap | Severity | Status | Android Mitigation |
|-----|----------|--------|-------------------|
| Core is Swift, Android is Kotlin/Java | P0 | BLOCKED | Need cross-platform API bridge (Kotlin Multiplatform? Embedded server? JNI?) — MARKED AS USER DECISION |
| No Gradle/Maven artifact for Core | P0 | BLOCKED | Core is Swift Package; Android cannot consume directly |
| No Kotlin DTO equivalents | P1 | MISSING | Android must define own Kotlin DTOs matching Core DTOs, or generate from JSON schema |
| No OkHttp/Cronet adapter | P1 | MISSING | Android must implement HTTPClient protocol using OkHttp |
| No Android WebView adapter | P2 | MISSING | Android must implement WebViewRuntimeProtocol |
| No Android JS engine adapter | P2 | MISSING | Android must integrate QuickJS or Hermes |
| No Room/DataStore schema | P1 | MISSING | Android must design own persistence layer |
| No Compose/XML UI | P0 | MISSING | Android must build all UI from scratch |
| No cross-platform build pipeline | P2 | MISSING | Android needs Gradle build with AGP |

---

## 6. Core-to-Android Integration Strategy Assessment

**Critical finding**: Reader-Core is a **Swift** package. Android is a **Kotlin/Java** platform. Direct binary integration is not possible.

Three viable strategies:
1. **Kotlin Multiplatform (KMP) bridge**: Compile Core Swift → shared logic, expose to Kotlin
2. **JSON-level contract**: Define a JSON API contract; Android duplicates DTOs in Kotlin and implements the same protocols
3. **Embedded HTTP bridge**: Run Core as a local HTTP service, Android communicates via REST

**Default safe action**: Option 2 (JSON-level contract) for early stages. Android defines equivalent Kotlin DTOs, implements protocols natively, and validates against Core's conformance tests.

---

## 7. Summary

| Metric | Value |
|--------|-------|
| Core STABLE modules | 3 (Models, Protocols, Parser) |
| Core EXPERIMENTAL modules | 4 (Network, Cache, Adapters, JS Renderer) |
| Core public protocols | 10 |
| Core frozen DTOs | 13 |
| Core frozen error types | 4 |
| Core frozen enums | 10 |
| Total Legado capabilities tracked | 139 |
| Owned by Reader-Core | 84.9% |
| Shared with Platform-Adapters | 10.8% |
| Shared with Reader-for-{platform} | 2.9% |
| Android-specific gaps | 9 |
| P0 blocking gaps | 2 (Swift↔Kotlin bridge, no Gradle artifact) |

**Overall status**: Core is READY for NonJS static book source pipeline. Dynamic JS, WebView, WebDAV, and TTS have protocol contracts but need Android-side adapter implementations. The primary architectural question is how to bridge Swift Core ↔ Kotlin Android.
