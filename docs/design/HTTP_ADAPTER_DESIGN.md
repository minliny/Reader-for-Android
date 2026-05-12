# Android HTTP Client Adapter Design

**Date**: 2026-05-13
**Status**: DRAFT
**Task**: P2-S3-003-DOC
**Depends On**: None (design doc only, no code)

---

## 1. Scope

This document evaluates HTTP client library options for Reader for Android. The Android HTTP adapter must implement `HTTPClient` (from Core's `NetworkProtocols`), providing the transport layer for book source fetching, login flows, and content retrieval.

This doc does NOT introduce any dependency — it only analyzes tradeoffs to inform BD-006.

## 2. What Core Requires

From Reader-Core `NetworkProtocols.swift` and `PlatformAdapterProtocols.swift`:

```
HTTPClient protocol:
  send(HTTPRequest) → async throws HTTPResponse

HTTPRequest contains:
  url, method, headers, requiredHeaders, body (bytes), timeout, cookie flags, scope key

HTTPResponse contains:
  statusCode, headers, data (bytes)

CookieJar protocol:
  getCookies(domain, path) → [Cookie]
  setCookie(Cookie)
  setCookies(headerValue, domain)
  clear()

ScopedCookieJar (extends CookieJar):
  Same operations scoped by (sourceId, host) partition
```

The Android adapter must:

| Requirement | Core Source |
|-------------|------------|
| Send HTTP requests with method, headers, body | `HTTPClient.send(_:)` |
| Return status code, headers, body bytes | `HTTPResponse` |
| Timeout enforcement | `HTTPRequest.timeout` |
| Cookie storage and auto-attachment | `CookieJar` / `ScopedCookieJar` |
| Cookie scope isolation per (sourceId, host) | `CookieJarScopeKey` |
| Follow redirects (configurable) | Swift impl follows by default |
| Error mapping to MappedReaderError | `ErrorMapper` / `NetworkPolicyLayer` |
| URL validation (scheme, host) | `validatedURL(from:)` |

## 3. Candidate Libraries

### 3.1 OkHttp

**Home**: https://square.github.io/okhttp/
**License**: Apache 2.0
**Android**: Native first-class support since Android 2.3+

**Pros**:
- De facto standard Android HTTP client — used by Retrofit, Picasso, Glide, Coil, Signal, etc.
- Built-in `CookieJar` interface — direct alignment with Core's `CookieJar` protocol
- Connection pooling, HTTP/2, HTTP/3 (experimental), response caching
- Interceptor chain for request/response modification (logging, auth, retry, policy injection)
- WebSocket support (useful for S7+ dynamic runtime)
- Battle-tested at massive scale (billions of devices)
- Kotlin coroutine support via `await()` extension or `suspend` wrappers
- Minimal method count for Android (no Guava dependency)
- `OkHttpClient` instances are cheap to clone with shared connection pool
- Redirect policy configurable (follow, don't follow, follow SSL-only)

**Cons**:
- JVM/Android only (no KMP — but irrelevant since Core is Swift, not shared)
- ~1.5 MB JAR size (acceptable; ProGuard/R8 strip unused)
- Imperative API style — needs thin wrapper for `suspend`/coroutine ergonomics

**Dependency**:
```kotlin
implementation("com.squareup.okhttp3:okhttp:4.12.0")
```

### 3.2 kTor Client

**Home**: https://ktor.io/docs/client.html
**License**: Apache 2.0
**Android**: Supported via `ktor-client-okhttp` or `ktor-client-cio` engine

**Pros**:
- Kotlin multiplatform (Android, iOS, desktop, JS, native)
- Coroutine-native API from the ground up
- Plugin architecture (content negotiation, serialization, auth, logging)
- Can swap engines: OkHttp, CIO, Darwin (iOS), Js
- Built-in content negotiation and serialization (kotlinx.serialization)
- Typed request/response builders

**Cons**:
- Plugin abstraction is unnecessary for Core's simple `HTTPRequest → HTTPResponse` contract
- Cookie support is a separate plugin (`ktor-client-cookies`) — less direct alignment
- Scoped cookie isolation (per sourceId+host) requires custom cookie storage implementation
- Smaller Android-specific community — fewer real-world miles
- Engine abstraction adds indirection without benefit (we'll only ever target Android)
- Interceptor model is less granular than OkHttp's chain
- Larger dependency footprint when including engine + plugins
- Less mature HTTP/2 support compared to OkHttp

**Dependency**:
```kotlin
implementation("io.ktor:ktor-client-okhttp:2.3.12")
implementation("io.ktor:ktor-client-cookies:2.3.12")
```

## 4. Comparison Matrix

| Criterion | OkHttp | kTor | Notes |
|-----------|--------|------|-------|
| HTTP/1.1 + HTTP/2 | Excellent | Good (via engine) | Core needs basic HTTP |
| Cookie jar interface | Built-in `CookieJar` | `ktor-client-cookies` plugin | OkHttp matches Core 1:1 |
| Scoped cookie isolation | Custom `CookieJar` impl | Custom storage backend | Both need custom code |
| Redirect control | `followRedirects()`, `followSslRedirects()` | Engine-specific config | OkHttp simpler |
| Timeout control | connect, read, write, call timeouts | Request timeout, socket timeout | Both adequate |
| Interceptor chain | First-class: application + network interceptors | Plugin pipeline | OkHttp more flexible |
| Coroutine support | `Call.await()` extension | Native | kTor slightly cleaner |
| Connection pooling | Built-in, mature | Via engine (OkHttp engine reuses it) | OkHttp direct |
| Response caching | Built-in `Cache` (disk) | Not built-in | OkHttp for chapter cache |
| WebSocket | Built-in | Plugin | Both support |
| Method count | ~5K (OkHttp 4.x) | ~3K (kTor client core) + engine | Similar after R8 |
| Android tested | Billions of devices | Thousands of apps | OkHttp heavy advantage |
| Documentation | Extensive | Good, Ktor-focused | OkHttp easier to onboard |
| Release cadence | Stable, infrequent major | Active, KMP-driven | Both well-maintained |

## 5. Recommendation: OkHttp

**OkHttp is the recommended HTTP client for Reader for Android.**

### Rationale

1. **Protocol alignment**: Core's `HTTPClient` protocol maps directly to `OkHttpClient.newCall(Request).execute()`. The cookie jar interfaces are near-identical in concept.

2. **Cookie scope isolation**: OkHttp's `CookieJar` interface operates on URL, not (sourceId, host). We need a thin `ScopedCookieJarAdapter` that multiplexes multiple OkHttp `CookieJar` instances keyed by `CookieJarScopeKey`. This is straightforward custom code — no library solves this out of the box.

3. **Interceptor chain**: The `NetworkPolicyLayer` (Core's response evaluation, error normalization) maps naturally to OkHttp interceptors. Logging, retry, auth injection are all interceptor concerns.

4. **No KMP benefit**: Since Reader-Core is Swift and Android is Kotlin/JVM, kTor's multiplatform advantage is moot. We are not sharing HTTP client code with iOS.

5. **Ecosystem fit**: If the project later adopts Retrofit (type-safe HTTP client) for REST-like endpoints, it's built on OkHttp. No migration needed.

6. **Proven at scale**: OkHttp runs on literally billions of Android devices. Edge cases (network transitions, IPv6, TLS, proxy) are thoroughly handled.

## 6. Cookie Isolation Strategy

Core's `ScopedCookieJar` requires cookies be isolated by `(sourceId, host)`. This is a security requirement: cookies from BookSource A must not leak to BookSource B, even for the same domain.

OkHttp's built-in `CookieJar` operates on URL alone. Implementation approach:

```
ScopedCookieJarAdapter
├── Map<CookieJarScopeKey, OkHttpCookieJar>  // one jar per (sourceId, host)
├── getCookies(domain, path, scopeKey) → delegates to jar for scopeKey
├── setCookie(cookie, scopeKey)        → delegates to jar for scopeKey
└── clear(scopeKey)                   → removes jar for scopeKey
```

Each per-scope jar is an in-memory `Map<CookieKey, Cookie>` with optional serialization for persistence. This is custom code (~100 lines) and does not require any additional library.

## 7. Error Mapping

Core defines a structured error taxonomy (`MappedReaderError`, `CSSExecutorError`, `ReaderError`). The Android HTTP adapter must map network failures to Core error types.

Swift mapping (from `URLSessionHTTPClient`):

| Swift Error | Core Error |
|------------|------------|
| `URLError.timedOut` | `ReaderError` with `.timeout` failure |
| `URLError.*` | `ReaderError` with `.networkError(msg)` failure |
| HTTP 404 | `ReaderError` with `.CONTENT_FAILED` |
| HTTP 4xx/5xx | `ReaderError` with `.httpStatus(code)` |
| Empty response body | `ReaderError` with `.emptyResponse` |
| Invalid URL | `ReaderError` with `.RULE_INVALID` |

Android equivalent (to be implemented in `NetworkErrorMapper.kt`):

| Android Error | Core Error |
|---------------|------------|
| `SocketTimeoutException` | `MappedReaderError.timeout` |
| `UnknownHostException` | `MappedReaderError.networkError("DNS resolution failed")` |
| `SSLException` | `MappedReaderError.networkError("TLS handshake failed")` |
| `IOException` | `MappedReaderError.networkError(msg)` |
| HTTP 404 | `MappedReaderError.contentFailed` |
| HTTP 4xx/5xx | `MappedReaderError.httpStatus(code)` |
| Empty body | `MappedReaderError.emptyResponse` |

## 8. Implementation Sketch (NOT implementation)

```
// Android HTTP adapter — thin wrapper over OkHttp
class OkHttpAdapter(
    private val client: OkHttpClient,
    private val cookieJar: ScopedCookieJar? = null
) : HTTPClient {

    override suspend fun send(request: HTTPRequest): HTTPResponse {
        val okHttpRequest = request.toOkHttpRequest()
        val call = client.newCall(okHttpRequest)
        return withContext(Dispatchers.IO) {
            val response = call.execute()
            // store cookies from Set-Cookie headers
            cookieJar?.storeFromResponse(request, response)
            response.toHTTPResponse()
        }
    }
}

// Cookie scope adapter
class ScopedCookieJarAdapter : ScopedCookieJar {
    private val jars = ConcurrentHashMap<CookieJarScopeKey, CookieJar>()
    // ... delegates get/set/clear to per-scope jar
}
```

This is a thin adapter (~150 lines). The Core protocol defines the contract; OkHttp provides the transport. The adapter is the glue.

## 9. What This Means for Loop Tasks

| Stage | HTTP Adapter State | What's Possible |
|-------|-------------------|-----------------|
| S2 | Doc written (this doc) | BD-006 informed |
| S3 | OkHttp dependency approved? → implement `OkHttpAdapter` | Real HTTP fetch |
| S5 | `OkHttpAdapter` + `ScopedCookieJarAdapter` + `NetworkErrorMapper` | Real search/detail/TOC/content |

**No HTTP code can be written until BD-006 (OkHttp selection) and BD-008 (network access) are resolved by the user.**

## 10. Decision Required

| ID | Question | Recommendation | Status |
|----|----------|---------------|--------|
| BD-006 | OkHttp vs kTor for HTTP client | OkHttp (see Section 5) | OPEN |
| BD-008 | Allow network access for real HTTP fetch | Deferred to user | OPEN |

## 11. References

- Reader-Core NetworkProtocols: `Reader-Core/Core/Sources/ReaderCoreProtocols/NetworkProtocols.swift`
- Reader-Core PlatformAdapterProtocols: `Reader-Core/Core/Sources/ReaderCoreProtocols/PlatformAdapterProtocols.swift`
- Reader-Core URLSessionHTTPClient: `Reader-Core/Adapters/HTTP/Sources/ReaderPlatformAdapters/URLSessionHTTPClient.swift`
- Reader-Core NetworkPolicyLayer: `Reader-Core/Core/Sources/ReaderCoreNetwork/NetworkPolicyLayer.swift`
- Reader-Core NetworkErrorMapper: `Reader-Core/Core/Sources/ReaderCoreNetwork/NetworkErrorMapper.swift`
- Reader-Core EncryptedCookieJar: `Reader-Core/Core/Sources/ReaderCoreNetwork/EncryptedCookieJar.swift`
- OkHttp: https://square.github.io/okhttp/
- kTor Client: https://ktor.io/docs/client.html
