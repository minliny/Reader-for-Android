# Android WebView / JS Platform Adapter Design

**Date**: 2026-05-13
**Status**: DRAFT
**Task**: P3-S7-001-DOC
**Depends On**: None (design doc only, deferred to S7)

---

## 1. Scope

This document evaluates JavaScript execution engine options and WebView strategies for Reader for Android. Reader-Core defines two distinct runtime protocols:

| Protocol | Core Source | Purpose |
|----------|------------|---------|
| `JSExecutionAdapter` | `ReaderCoreProtocols/JSExecutionAdapter.swift` | Execute JS scripts against HTML, return processed HTML |
| `RuntimeJavaScriptExecutorProtocol` | `ReaderCoreModels/RuntimeJavaScriptExecutorProtocol.swift` | Sandboxed JS execution with security constraints, token generation |
| `RuntimeWebViewExecutorProtocol` | `ReaderCoreModels/RuntimeWebViewExecutorProtocol.swift` | Full browser engine for page rendering, interaction, login flows |

This doc does NOT introduce any dependency — it only analyzes tradeoffs to inform BD-009 (JS engine) and BD-026 (WebView support).

## 2. What Core Requires

### 2.1 JSExecutionAdapter (Simple)

```
protocol JSExecutionAdapter:
  execute(html: String, evalScript: String?) throws -> String
```

Used by the JSRenderer pipeline (`ReaderCoreJSRenderer`) to run book-source-defined JavaScript rules against fetched HTML. Single-shot execution: input HTML → script → output HTML.

### 2.2 RuntimeJavaScriptExecutorProtocol (Full)

```
protocol RuntimeJavaScriptExecutorProtocol:
  executorId: String
  executorName: String
  supportsFeature(RuntimeJavaScriptFeature) -> Bool
  capabilities() -> RuntimeJavaScriptExecutorCapabilities
  execute(script, context) -> RuntimeJavaScriptExecutionResult
  generateToken(spec, context) -> RuntimeTokenGenerationResult
  release()
```

Full sandboxed JS runtime with:
- Security constraint validation (pre-execution script check, post-execution result check)
- Concurrency limit enforcement
- Timeout control
- Token generation (HMAC, MD5/SHA, custom algorithms)
- Feature capabilities: JS execution, token generation, DOM manipulation, regex, date, base64, timers
- Deterministic execution mode (fixed Date, locked Math.random)
- Sandbox modes: default, tokenGeneration, domProcessing, relaxed
- Network isolation (block fetch, XHR, WebSocket, Worker)

### 2.3 RuntimeWebViewExecutorProtocol (Full Browser)

```
protocol RuntimeWebViewExecutorProtocol:
  executorId: String
  executorName: String
  supportsFeature(RuntimeWebViewFeature) -> Bool
  capabilities() -> RuntimeWebViewExecutorCapabilities
  execute(RuntimeWebViewRequest) -> RuntimeWebViewResult
  executeInteractionSteps(request, scripts) -> [RuntimeWebViewInteractionResult]
  release()
```

Full browser engine with:
- URL loading and page rendering
- JS interaction scripts (click, input, scroll, waitForElement, evaluate)
- Security policy: allowed host whitelist, HTTPS requirement, block javascript:/data: URLs
- Navigation limit enforcement (max 1)
- Snapshot support
- Timeout handling
- Interaction types: waitForPageLoad, waitForElement, click, input, scroll, evaluate, getContent, getAttribute
- Delegate callbacks: didStart, didUpdateProgress, didComplete, didFail, didSaveSnapshot, didReceiveConsoleMessage

## 3. JS Engine Options

### 3.1 QuickJS

**Home**: https://bellard.org/quickjs/
**License**: MIT
**Android wrapper**: `appspell/quickjs-android` (Kotlin), `seven332/QuickJS-Android` (Java)

**QuickJS characteristics**:
- Small and embeddable: ~210KB compiled (arm64)
- ES2020 support (almost complete)
- No JIT — pure interpreter, safe and predictable
- Built-in sandboxing: separate `JSContext` per execution, no shared mutable state
- Memory limit enforcement per context
- Deterministic execution support (can override Date, Math.random)
- No DOM, no browser APIs — pure JS execution only
- Single-threaded by design (perfect for `maxConcurrentExecutions: 1`)

**Android integration** (`quickjs-android`):
- JNI bridge, Kotlin-friendly API
- Supports `evaluate(script)` with timeout
- Supports `JSContext` isolation (matches Core's sandbox model)
- ~300KB AAR

**Pros**:
- Direct alignment with Core's sandbox model (context isolation, network lockdown, deterministic mode)
- Tiny binary size (~210-300KB) vs full browser engine (~40MB)
- No UI dependency — runs on any thread, no Activity/Window needed
- Fast startup (<1ms to create context) vs WebView (~200ms)
- Token generation is straightforward (no DOM overhead)
- Can create multiple isolated contexts for cookie-isolated JS execution
- ES2020 coverage sufficient for book source JS rules (regex, string, base64, date)

**Cons**:
- No DOM support — can't execute scripts that depend on `document`, `window.location`, `navigator`
- Requires JNI wrapper (maintenance burden if QuickJS ABI changes)
- Smaller community than V8/JSC
- Less battle-tested on Android (mostly used in embedded/IoT)
- Book sources with DOM-dependent JS rules would need WebView fallback

**Dependency**:
```kotlin
implementation("com.github.appspell:quickjs-android:0.6.0")
```

### 3.2 Hermes

**Home**: https://hermesengine.dev/
**License**: MIT
**Android wrapper**: Part of React Native, or standalone via `facebook/hermes`

**Hermes characteristics**:
- Designed for React Native — expects RN bridge and bundle format
- AOT compilation (bytecode precompilation) for fast startup
- Optimized for mobile memory constraints
- Lacks standalone embed API for non-RN use cases
- No sandboxed context isolation API
- No built-in timeout/interrupt mechanism (needs external watchdog)
- DOM support only through React Native's DOM layer

**Pros**:
- Fast startup (AOT compiled bytecode)
- Memory efficient for long-running JS contexts
- Facebook-backed, used in production React Native apps

**Cons**:
- Tightly coupled to React Native ecosystem
- No clean standalone embed API
- No sandbox primitives (context isolation, network lockdown)
- No timeout/interrupt API (Core requires timeouts)
- Deterministic execution not supported
- Token generation not straightforward
- Requires bytecode compilation step (not runtime-eval friendly)
- ~5-8MB AAR (much larger than QuickJS)

**Verdict**: NOT SUITABLE. Hermes is designed for React Native bundles, not for sandboxed single-shot script evaluation. The lack of context isolation APIs and timeout interrupts makes it a poor fit for Core's security model.

### 3.3 V8 (via J2V8 or Android's built-in WebView)

**V8 characteristics**:
- Chrome's JS engine, used by Android WebView
- Full ES2022+ support
- JIT compilation — fast but non-deterministic
- No built-in sandboxing API for embedded use
- Only available through WebView on Android (no standalone embed API)

**Verdict**: NOT SUITABLE as standalone JS engine. Only accessible through WebView, which is heavyweight. The JIT compilation also conflicts with Core's deterministic execution requirement.

## 4. WebView Engine Options

### 4.1 Android WebView (`android.webkit.WebView`)

**Built into Android**: No additional dependency. Available on all Android 5.0+ devices (100% of minSdk=26 devices).

**Android WebView characteristics**:
- Full Chromium-based browser engine
- `evaluateJavascript(script, callback)` for JS execution
- `WebViewClient` for navigation events (page loaded, errors, URL interception)
- `shouldInterceptRequest` for resource blocking
- Cookie management via `CookieManager`
- Requires a View attachment (must be created on main thread, attached to Window)
- Off-screen rendering possible via `onDraw()` → Bitmap
- ~40-80MB memory footprint when active
- 100-300ms cold-start time

**Pros**:
- Zero dependency — built into Android OS
- Full DOM + browser API support (essential for login flows, SPA rendering)
- Battle-tested on billions of devices
- Cookie management built-in
- JavaScript execution with result callbacks
- Security: same-origin policy, HTTPS enforcement, XSS protection
- Supports all `RuntimeWebViewFeature` capabilities

**Cons**:
- Heavyweight (~40-80MB RAM per instance)
- Must run on main thread
- Requires View attachment (or off-screen rendering hacks)
- Cold start 100-300ms (warm reuse is faster)
- Non-deterministic execution (full browser engine, JIT)
- Overkill for simple JS execution (QuickJS handles those cases)
- Auto-updates via Google Play Services (version varies across devices)

**Scope**: Android WebView is the ONLY viable implementor of `RuntimeWebViewExecutorProtocol`. No other Android-native option provides full DOM + JS + interaction support.

### 4.2 GeckoView (Mozilla)

**Home**: https://mozilla.github.io/geckoview/
**License**: MPL 2.0

**Pros**: Independent of Chrome, full Firefox engine.
**Cons**: ~40MB AAR, slower than WebView, niche adoption, maintenance burden.
**Verdict**: NOT RECOMMENDED. WebView is sufficient and zero-cost.

## 5. Recommended Strategy: Two-Engine Architecture

```
                    ┌─────────────────────────────┐
                    │     Reader for Android       │
                    │                              │
 Book Source JS ───►│  QuickJSAdapter              │
 (no DOM needed)    │  implements:                 │
                    │  JSExecutionAdapter          │
                    │  RuntimeJavaScriptExecutor   │
                    │                              │
 Login / SPA ──────►│  AndroidWebViewAdapter       │
 (needs DOM)        │  implements:                 │
                    │  RuntimeWebViewExecutor      │
                    └─────────────────────────────┘
```

| Protocol | Engine | Why |
|----------|--------|-----|
| `JSExecutionAdapter` | QuickJS | Simple, lightweight, no DOM needed |
| `RuntimeJavaScriptExecutorProtocol` | QuickJS | Sandboxed execution, context isolation, token generation |
| `RuntimeWebViewExecutorProtocol` | Android WebView | Full DOM, navigation, interaction, login flows |

### Decision Logic (runtime dispatch)

```
if request.needsDOM():
    use AndroidWebViewAdapter
else:
    use QuickJSAdapter
```

Most book source JS rules (search tokens, content cleaning) only need pure JS execution — QuickJS handles these. Login flows and SPA rendering (where page JS must execute before content is available) require WebView.

## 6. QuickJS Sandbox Alignment with Core

Core's `RuntimeJavaScriptSandbox` maps directly to QuickJS capabilities:

| Core Sandbox Feature | QuickJS Support | Implementation |
|---------------------|-----------------|----------------|
| Timeout | Yes | `JSContext.setTimeout(ms)` or watch-dog thread |
| Memory limit | Yes | `JSContext.setMemoryLimit(mb)` |
| Deterministic mode | Yes | Override `Date.now()` and `Math.random()` in script preamble |
| Network lockdown | Yes | Override `fetch`, `XMLHttpRequest`, etc. in script preamble |
| Block eval | Yes | Don't expose `eval` to context |
| Block Function constructor | Yes | Don't expose `Function` to context |
| Console | Yes | Capture `console.log` output |
| Math, JSON, RegExp, Array, String | Yes | Built-in |
| Base64 | Yes | `btoa`/`atob` available in ES2020 |
| DOM | No | QuickJS doesn't have DOM — but Core's sandbox defaults to `enableDOM: false` |

Core's default sandbox (`enableDOM: false`) is compatible with QuickJS's no-DOM design. The `domProcessing` sandbox mode would require WebView fallback.

## 7. Android WebView Alignment with Core

Core's `WKWebViewRuntimeAdapter` (iOS) maps to Android WebView:

| Core Feature | Android WebView | Notes |
|-------------|-----------------|-------|
| `execute(request)` → page HTML | `WebView.loadUrl()` → `evaluateJavascript("document.documentElement.outerHTML")` | Direct equivalent |
| Security policy validation | `shouldOverrideUrlLoading` | Block javascript:/data: schemes |
| Host whitelist | Check URL host against `authorization.allowedHosts` | Same logic |
| HTTPS requirement | Check URL scheme | Same logic |
| Navigation limit | Track `currentNavigationCount` | Same |
| `executeInteractionSteps` | `evaluateJavascript()` for each step | Sequential execution |
| Timeout | `Handler.postDelayed()` or coroutine `withTimeout` | Android idiomatic |
| Snapshot | Save HTML to file | Same |
| Release | `WebView.destroy()` | Free all resources |

### Off-Screen WebView

Android WebView needs a View attachment. For non-UI execution (login flows in background), use:

```kotlin
// Off-screen WebView — renders to memory, not to screen
val webView = WebView(context).apply {
    layout(0, 0, 1, 1)  // 1x1 pixel virtual size
    // or use VirtualDisplay / Presentation for API 29+
}
```

This is a known pattern used by headless browser SDKs on Android. Not ideal, but functional. API 29+ can use `VirtualDisplay` for better isolation.

## 8. Security Considerations

| Concern | Mitigation |
|---------|-----------|
| Malicious JS in book sources | QuickJS sandbox: network lockdown, no eval, deterministic Date, memory limit |
| WebView loading attacker URLs | Host whitelist, HTTPS-only, block javascript:/data: schemes |
| Cookie leakage between sources | ScopedCookieJarAdapter (see HTTP_ADAPTER_DESIGN.md §6) |
| WebView CVE / 0-day | WebView auto-updates via Play Services; content is static HTML from known book sources |
| QuickJS CVE | QuickJS has small attack surface (interpreter only, no JIT); keep wrapper library updated |
| Resource exhaustion (WebView) | maxConcurrentExecutions=1, timeout, destroy after use |
| User credential exposure in WebView | Never auto-fill credentials; clear cookies after login flow |

## 9. Implementation Sketch (NOT implementation)

```
// QuickJS adapter for sandboxed JS execution
class QuickJSJSAdapter(
    private val sandbox: RuntimeJavaScriptSandbox = .default
) : JSExecutionAdapter, RuntimeJavaScriptExecutorProtocol {

    override suspend fun execute(
        script: String,
        context: RuntimeJavaScriptContext
    ): RuntimeJavaScriptExecutionResult {
        // 1. Validate security constraints
        // 2. Create isolated QuickJS context
        // 3. Inject network lockdown script
        // 4. Inject deterministic Date (if enabled)
        // 5. Execute user script with timeout
        // 6. Capture output / error
        // 7. Validate result constraints
        // 8. Return RuntimeJavaScriptExecutionResult
    }
}

// Android WebView adapter for full browser execution
class AndroidWebViewAdapter(
    private val configuration: WebViewConfiguration
) : RuntimeWebViewExecutorProtocol {

    override suspend fun execute(
        request: RuntimeWebViewRequest
    ): RuntimeWebViewResult {
        // 1. Validate security policy (host, HTTPS, schemes)
        // 2. Check navigation limit
        // 3. Create WebView (main thread)
        // 4. Load URL with WebViewClient
        // 5. Wait for page load or timeout
        // 6. Extract document.documentElement.outerHTML
        // 7. Store snapshot (if configured)
        // 8. Return RuntimeWebViewResult
    }
}
```

Estimated adapter sizes:
- QuickJS adapter: ~200 lines
- Android WebView adapter: ~300 lines
- Security policy validator (shared): ~100 lines
- Sandbox script generator: ~50 lines
- Total: ~650 lines of Kotlin

## 10. What This Means for Loop Tasks

| Stage | Runtime State | What's Possible |
|-------|--------------|-----------------|
| S1-S5 | No JS/WebView needed | Fake data for UI development |
| S7 | QuickJS adapter for `JSExecutionAdapter` | Real JS-based search token generation, content cleaning |
| S7 | Android WebView adapter for `RuntimeWebViewExecutorProtocol` | Real login flows, SPA rendering |
| S8+ | Both engines active with runtime dispatch | Full Core parity for dynamic sources |

**Deferred to S7**: No JS/WebView code should be written until the non-JS parser pipeline (S5) is stable. Fake data serves UI development.

## 11. Decision Required

| ID | Question | Recommendation | Status |
|----|----------|---------------|--------|
| BD-009 | QuickJS vs Hermes for JS engine | QuickJS (see §5) | OPEN |
| BD-026 | Whether to support WebView for JS-based book sources | Yes, using Android WebView (see §7) | OPEN |

## 12. References

- Reader-Core JSExecutionAdapter: `Reader-Core/Core/Sources/ReaderCoreProtocols/JSExecutionAdapter.swift`
- Reader-Core PlatformAdapterProtocols: `Reader-Core/Core/Sources/ReaderCoreProtocols/PlatformAdapterProtocols.swift`
- Reader-Core JSRuntime: `Reader-Core/Core/Sources/ReaderCoreJSRenderer/JSRuntime.swift`
- Reader-Core JSRuntimeDOMBridge: `Reader-Core/Core/Sources/ReaderCoreJSRenderer/JSRuntimeDOMBridge.swift`
- Reader-Core iOSRuntimeJavaScriptExecutor: `Reader-Core/Core/Sources/ReaderPlatformAdapters/iOSRuntimeJavaScriptExecutor.swift`
- Reader-Core RuntimeJavaScriptExecutorProtocol: `Reader-Core/Core/Sources/ReaderCoreModels/RuntimeJavaScriptExecutorProtocol.swift`
- Reader-Core RuntimeJavaScriptSandbox: `Reader-Core/Core/Sources/ReaderCoreModels/RuntimeJavaScriptSandbox.swift`
- Reader-Core WKWebViewRuntimeAdapter: `Reader-Core/Core/Sources/ReaderPlatformAdapters/WKWebViewRuntimeAdapter.swift`
- Reader-Core RuntimeWebViewExecutorProtocol: `Reader-Core/Core/Sources/ReaderCoreModels/RuntimeWebViewExecutorProtocol.swift`
- QuickJS: https://bellard.org/quickjs/
- QuickJS Android: https://github.com/appspell/quickjs-android
- Hermes: https://hermesengine.dev/
- Android WebView: https://developer.android.com/reference/android/webkit/WebView
