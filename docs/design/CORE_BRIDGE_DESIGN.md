# Reader-Core ↔ Android Bridge Design

**Date**: 2026-05-13
**Status**: DRAFT
**Task**: P1-S3-002
**Decision**: BD-007 (OPEN)

---

## 1. Problem

Reader-Core is a **Swift 5.9 package** targeting iOS 15+ and macOS 13+. Reader for Android is a **Kotlin/JVM project** targeting Android. There is no direct binary interoperability between Swift and Kotlin/Android.

We need a strategy for Android to consume Reader-Core's capabilities without rewriting Core internals.

## 2. What Android Needs from Core

From the audit (`ANDROID_CORE_REALITY_AUDIT.md`):

| Capability | Core Provides | Android Needs |
|------------|-------------|---------------|
| BookSource decode | Swift Codable from JSON | Parse the same JSON in Kotlin |
| NonJS parsing | NonJSParserEngine (takes HTML `Data` → DTOs) | Equivalent parsing of HTML → DTOs |
| Search/TOC/Content pipeline | ParserProtocols | Same pipeline logic, Kotlin implementation |
| Error taxonomy | MappedReaderError, CSSExecutorError | Same error model in Kotlin |
| URL DSL | URL template models | Same URL construction in Kotlin |
| Dynamic runtime | JSExecutionAdapter, WebViewRuntimeProtocol | Platform adapter implementations |

## 3. Integration Strategies

### Strategy A: JSON-Level Contract (RECOMMENDED)

**Concept**: Reader-Core defines the DTO schema and protocol contracts. Android implements equivalent Kotlin DTOs and protocols independently, validating against Core's conformance tests.

```
Reader-Core (Swift)                    Reader-for-Android (Kotlin)
┌──────────────────────┐              ┌──────────────────────────┐
│ BookSource (Codable) │──JSON spec──│ BookSource (kotlinx.ser) │
│ SearchResultItem     │──JSON spec──│ SearchResultItem         │
│ TOCItem              │──JSON spec──│ TOCItem                  │
│ ContentPage          │──JSON spec──│ ContentPage              │
│                      │              │                          │
│ NonJSParserEngine    │──contract──│ NonJSParserEngine (impl) │
│ CSSExecutor          │──contract──│ CSSExecutor (impl)       │
│ XPath evaluator      │──contract──│ XPath evaluator (impl)   │
└──────────────────────┘              └──────────────────────────┘
```

**Pros**:
- No build dependency on Swift toolchain
- No cross-compilation complexity
- Android can use idiomatic Kotlin libraries (Jsoup for CSS, javax.xml for XPath)
- Clean boundary enforcement — no accidental Core coupling
- Can be versioned via JSON schema
- Works offline, no runtime dependency

**Cons**:
- Must reimplement NonJS parser logic in Kotlin (CSS/XPath/Regex pipeline)
- Risk of behavioral divergence from Core
- Must manually keep DTOs in sync with Core
- Larger initial implementation effort for parser

**Mitigation**:
- Core conformance tests define expected output for given inputs
- Android can run same fixture tests
- DTO sync via generated code from JSON schema (future)

### Strategy B: Kotlin Multiplatform (KMP) Bridge

**Concept**: Compile Reader-Core Swift via a shared C interface, expose through Kotlin/Native.

```
Reader-Core (Swift) → C bridge (.dylib/.so) → Kotlin/Native → Android
```

**Pros**:
- True code sharing — no reimplementation
- Single source of truth for parser logic
- Automatic DTO consistency

**Cons**:
- Swift → C → Kotlin/Native bridge is experimental and unsupported
- Swift on Android is not officially supported by Apple
- Requires Swift toolchain on Android build machines
- Massive build complexity (Swift compiler for Android NDK)
- Fragile — breaks with Swift version changes
- Not viable for production in 2026

**Verdict**: NOT VIABLE. Rejected.

### Strategy C: Embedded Local HTTP Service

**Concept**: Package Reader-Core as a local HTTP server (macOS/Linux), Android communicates via localhost REST.

```
Reader-Core (Swift) → HTTP server (localhost:8080) ← OkHttp ← Android App
```

**Pros**:
- True Core execution — no reimplementation
- Clean REST API boundary
- Core updates don't require Android changes

**Cons**:
- Requires Core process running on device — impossible on Android
- Only works in development (desktop Core + Android emulator)
- Production Android can't run Swift binaries
- Adds network latency to every parse call
- Deployment nightmare

**Verdict**: NOT VIABLE for production. Useful for development/testing only.

### Strategy D: Server-Side Core (FUTURE)

**Concept**: Deploy Reader-Core as a cloud service. Android sends HTML, Core parses, returns DTOs.

```
Android App → HTTPS → Reader-Core Service → Response
```

**Pros**:
- Zero client-side parser complexity
- True Core execution
- Centralized updates

**Cons**:
- Requires network for all reading
- Privacy concerns (all reading data goes through server)
- Infrastructure cost
- Latency for every page turn
- Defeats offline reading

**Verdict**: NOT RECOMMENDED as primary strategy. Viable as optional "cloud mode" in S15+.

## 4. Recommended Strategy: A (JSON-Level Contract)

**Immediate (S1-S6)**: Strategy A with FakeCoreBridge for UI development.

**P1 (S3)**: Define Kotlin DTOs matching Core frozen DTOs:
- `BookSource`, `SearchRule`, `BookInfoRule`, `TocRule`, `ContentRule`, `ExploreRule`
- `SearchResultItem`, `TOCItem`, `ContentPage`, `BookInfo`
- `MappedReaderError`, `CSSExecutorError`

**P2 (S5-S6)**: Implement NonJS parser subset in Kotlin:
- CSS selector evaluation (using Jsoup)
- XPath evaluation (using javax.xml.xpath or Jsoup)
- Regex/replace pipeline
- URL template resolution
- Charset detection

**P3 (S7+)**: Dynamic runtime adapters:
- Android WebView adapter (native)
- QuickJS/Hermes adapter (native)
- OkHttp HTTP client adapter
- Cookie/storage adapters

## 5. DTO Synchronization

### Immediate (manual)
- Copy Core DTO field lists
- Define equivalent Kotlin data classes with `@Serializable`
- Preserve `unknownFields: Map<String, JsonElement>` for forward compatibility
- Match field names exactly for JSON round-trip compatibility

### Future (automated)
- Extract JSON schema from Core Swift types
- Generate Kotlin data classes from schema
- CI validation that Kotlin DTOs match Core DTOs

## 6. Parser Parity Strategy

The NonJS parser must be reimplemented in Kotlin. The scope is bounded:

| Component | Kotlin Equivalent | Effort |
|-----------|------------------|--------|
| CSS selector | Jsoup `select()` | Low (library) |
| XPath | Jsoup or javax.xml.xpath | Medium |
| JSONPath | Manual or JsonPath library | Low |
| Regex replace | `kotlin.text.Regex` | Low |
| URL template | Custom (straightforward) | Low |
| Charset detection | juniversalchardet or manual | Low |
| HTML text decode | Jsoup `.text()` | Low |

The Core has ~1300 tests. Android should port the fixture replay tests to validate behavioral parity.

## 7. What This Means for Loop Tasks

| Stage | Bridge State | What's Possible |
|-------|-------------|-----------------|
| S1 | FakeCoreBridge (hardcoded data) | UI development, navigation |
| S2 | Kotlin DTOs defined | Model layer ready |
| S3 | CoreBridge contract defined | Interface defined, fake impl |
| S5 | Real HTTP + CoreBridge (Strategy A) | Real search/detail/TOC/content |
| S7 | Dynamic adapters | JS/WebView/Cookie/Login |

## 8. Decision Required

| ID | Question | Recommendation | Status |
|----|----------|---------------|--------|
| BD-007 | Swift Core ↔ Kotlin bridge strategy | Strategy A: JSON-Level Contract | OPEN |

## 9. References

- Reader-Core Integration Contract: `Reader-Core/docs/architecture/reader_core_integration_contract.md`
- Reader-Core DTO Freeze: `Reader-Core/docs/architecture/DTO_FREEZE_MATRIX.md`
- Reader-Core Scope: `Reader-Core/docs/PLANNING/READER_CORE_SCOPE_AND_BOUNDARIES.md`
- Android Reality Audit: `docs/PLANNING/ANDROID_CORE_REALITY_AUDIT.md`
- Legado Ownership Matrix: `Reader-Core/docs/PLANNING/LEGADO_OWNERSHIP_MATRIX.md`
