# Android WebDAV Three-Way Design Doc

**Date**: 2026-05-13
**Status**: DRAFT
**Task**: P3-S11-001-DOC
**Depends On**: None (design doc only, deferred to S11)

---

## 1. Scope

This document defines the Android-side WebDAV backup/sync strategy for Reader for Android. Core defines the sync protocol, data models, conflict policy, and WebDAV directory layout. Android implements the concrete `SyncTransportProtocol` using a WebDAV client.

This doc does NOT introduce any dependency — it only analyzes tradeoffs to inform BD-010 (WebDAV client library) and BD-027 (WebDAV support).

## 2. What Core Provides

From Reader-Core's sync architecture (`docs/architecture/sync/`):

### 2.1 Protocols

```
SyncTransportProtocol:
  push(path, data, etag?) → SyncTransportResponse
  pull(path) → SyncTransportResponse
  list(path) → [SyncTransportItem]
  delete(path, etag?) → SyncTransportResponse
  head(path) → SyncTransportResponse
  probe() → Bool
  baseURL: String
  isAvailable: Bool
```

### 2.2 Data Models

| Model | Purpose |
|-------|---------|
| `SyncEntityMetadata` | Global sync metadata |
| `SyncManifest` | Sync manifest with per-entity ETags/revisions |
| `DeviceRecord` | Device registration |
| `BookSourceSyncRecord` | Book source sync |
| `BookshelfBookSyncRecord` | Bookshelf sync |
| `ReadingProgressSyncRecord` | Reading progress sync |
| `SyncTombstone` | Soft-delete record |
| `ConflictRecord` | Conflict tracking |

### 2.3 WebDAV Layout

```
/ReaderSync/
  manifest.json
  devices/{deviceId}.json
  sources/book_sources/{sourceId}.json
  bookshelf/books/{bookId}.json
  progress/latest/{progressId}.json
  progress/devices/{deviceId}/{progressId}.json
  progress/history/{progressId}/{timestamp}.json
  snapshots/sync_state/{deviceId}_sync_state.json
```

### 2.4 Conflict Policy

| Entity | Strategy | User Prompt |
|--------|----------|-------------|
| Book source | Last-write-wins | No |
| Bookshelf book | LWW (metadata) | No |
| Replace rule | LWW | No |
| Rule application | Merge (union) | No |
| Reading progress | LWW + device notice | Yes (if active reading) |
| Tombstone | Time-based undelete | No |

## 3. What Android Must Implement

Per the WebDAV handoff (`WEBDAV_SYNC_HANDOFF.md`) and platform integration guide (`PLATFORM_SYNC_INTEGRATION_GUIDE.md`):

| Responsibility | Android Implementation |
|---------------|----------------------|
| `SyncTransportProtocol` | `AndroidWebDAVSyncTransport` using HTTP client |
| WebDAV credentials | `EncryptedSharedPreferences` or Android Keystore |
| Background sync | `WorkManager` periodic task (15 min) |
| App lifecycle sync | `onPause()` → flush, `onResume()` → pull |
| Progress page triggers | Call `syncEngine.updateProgress()` on page turn |
| Conflict UI | Dialog/bottom sheet showing `ConflictRecord` |
| Local book file sync | User-triggered, `WorkManager` for upload |
| Sync settings UI | Server URL, username, password, sync interval |

## 4. WebDAV Client Library Options

### 4.1 Sardine (Java WebDAV client)

**Home**: https://github.com/lookfirst/sardine
**License**: Apache 2.0

**Pros**:
- Purpose-built WebDAV client (PROPFIND, MKCOL, MOVE, COPY, LOCK, UNLOCK)
- Handles XML response parsing for PROPFIND
- Digest and Basic auth support
- ETag / Last-Modified awareness
- Mature project (since 2011)

**Cons**:
- Java library, not Kotlin-native
- Uses Apache HttpClient (legacy) — large dependency
- Not actively maintained (last release 2021)
- XML parser dependency (DOM or SAX)
- ~2MB + transitive dependencies

**Dependency**:
```kotlin
implementation("com.github.lookfirst:sardine:5.10")
```

### 4.2 OkHttp + Manual WebDAV

**Concept**: Use OkHttp directly for WebDAV operations (PROPFIND, MKCOL, etc.) with manual XML parsing of responses.

**Pros**:
- No additional WebDAV library — reuse OkHttp (already chosen per HTTP_ADAPTER_DESIGN.md)
- Full control over request/response handling
- Minimal dependency footprint
- OkHttp's interceptor chain for auth, logging, retry

**Cons**:
- Must hand-write PROPFIND XML parsing (but PROPFIND response is simple XML)
- Must hand-write MKCOL, MOVE, COPY request bodies
- More implementation effort (~300 lines vs ~50 lines with a library)
- Digest auth must be implemented manually (Basic auth is simpler and sufficient for HTTPS)

**WebDAV methods are simple HTTP**:
```
PROPFIND /path → XML response with file list + ETags
MKCOL /path → 201 Created
MOVE /src → /dst → 201 Created
COPY /src → /dst → 201 Created
PUT /path (If-Match: etag) → 201/412
GET /path → body
DELETE /path → 204
HEAD /path → headers (ETag, Last-Modified, Content-Length)
```

PROPFIND request body (simple XML):
```xml
<?xml version="1.0" encoding="utf-8"?>
<D:propfind xmlns:D="DAV:">
  <D:prop>
    <D:getetag/>
    <D:getlastmodified/>
    <D:getcontentlength/>
    <D:resourcetype/>
  </D:prop>
</D:propfind>
```

### 4.3 kTor Client + WebDAV Plugin

**Concept**: Use kTor client with optional WebDAV features.

**Verdict**: kTor has no built-in WebDAV support. Same manual implementation as OkHttp but with less HTTP maturity on Android. Not recommended.

## 5. Comparison Matrix

| Criterion | Sardine | OkHttp Manual | kTor Manual |
|-----------|---------|---------------|-------------|
| WebDAV support | Built-in | Manual | Manual |
| PROPFIND | Built-in | ~50 lines XML parse | ~50 lines XML parse |
| Auth | Basic + Digest | Basic (HTTPS sufficient) | Basic |
| ETag / If-Match | Built-in | Manual header set | Manual header set |
| Dependency size | ~2MB + transitive | 0 (reuse OkHttp) | ~3MB (new engine + plugins) |
| Kotlin ergonomics | Java API | Kotlin-native | Coroutine-native |
| Maintenance | Stale (2021) | Own code | Own code |
| Security audit surface | Large (Apache HttpClient) | Small (OkHttp only) | Medium (kTor + engine) |

## 6. Recommendation: OkHttp + Manual WebDAV

**Use OkHttp directly for WebDAV operations. No additional WebDAV library.**

### Rationale

1. **WebDAV is simple HTTP**: The operations Core needs (PROPFIND, PUT, GET, DELETE, MKCOL, HEAD) are standard HTTP methods with minor XML payloads. A dedicated WebDAV library is overkill.

2. **Zero additional dependency**: OkHttp is already the chosen HTTP client (see HTTP_ADAPTER_DESIGN.md). Adding Sardine would bring Apache HttpClient + XML parser (~2MB+).

3. **PROPFIND parsing is trivial**: The XML response from PROPFIND is a flat list of files with properties. A simple SAX or Pull parser handles it in ~50 lines.

4. **Basic auth over HTTPS is sufficient**: Core's sync model assumes HTTPS. Basic auth over HTTPS is secure. Digest auth adds complexity without meaningful security gain for this use case.

5. **Full control**: Manual implementation means no surprise behavior from a stale library. ETag handling, redirect policy, timeout — all controlled through OkHttp's interceptor chain.

6. **Maintainability**: The adapter is ~300 lines of Kotlin. Easier to maintain than debugging a stale Java WebDAV library.

### Implementation Sketch

```kotlin
class AndroidWebDAVSyncTransport(
    private val client: OkHttpClient,
    private val baseUrl: String,
    private val credentials: WebDAVCredentials
) : SyncTransportProtocol {

    override suspend fun push(
        path: String, data: ByteArray, etag: String?
    ): SyncTransportResponse {
        val request = Request.Builder()
            .url("$baseUrl/$path")
            .put(data.toRequestBody())
            .apply { etag?.let { header("If-Match", it) } }
            .header("Authorization", credentials.basicAuthHeader())
            .build()
        return client.newCall(request).await().toSyncResponse()
    }

    override suspend fun list(path: String): List<SyncTransportItem> {
        val request = Request.Builder()
            .url("$baseUrl/$path")
            .method("PROPFIND", PROPFIND_BODY)
            .header("Authorization", credentials.basicAuthHeader())
            .header("Depth", "1")
            .build()
        val response = client.newCall(request).await()
        return parsePropfindResponse(response.body!!.string())
    }

    // ... pull, delete, head, probe similarly
}
```

Estimated adapter size: ~300 lines of Kotlin.

## 7. Authentication & Credential Storage

### WebDAV Credentials

```kotlin
data class WebDAVCredentials(
    val serverUrl: String,    // https://dav.example.com/remote.php/dav/
    val username: String,
    val password: String      // stored encrypted, never logged
) {
    fun basicAuthHeader(): String {
        val encoded = Base64.encodeToString(
            "$username:$password".toByteArray(), Base64.NO_WRAP
        )
        return "Basic $encoded"
    }
}
```

### Secure Storage

| Option | Use Case |
|--------|----------|
| `EncryptedSharedPreferences` | Simple, AndroidX Security library |
| Android Keystore + `EncryptedFile` | Stronger isolation per file |
| `DataStore` (future, with encryption) | If DataStore is adopted for preferences |

**Recommendation**: `EncryptedSharedPreferences` from AndroidX Security. Simple API, adequate for WebDAV credentials. Key is stored in Android Keystore (TEE-backed on modern devices).

```kotlin
val masterKey = MasterKey.Builder(context)
    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
    .build()

val prefs = EncryptedSharedPreferences.create(
    context, "webdav_credentials", masterKey,
    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
)
```

## 8. Background Sync Strategy

### WorkManager Periodic Sync

```kotlin
class SyncWorker(
    context: Context, params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            syncEngine.pull()  // Core orchestrator
            syncEngine.flush() // Push local changes
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 5) Result.retry()
            else Result.failure()
        }
    }
}

// Schedule
val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(
    15, TimeUnit.MINUTES
).setConstraints(
    Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()
).build()

WorkManager.getInstance(context).enqueue(syncRequest)
```

### Lifecycle Triggers

| Event | Action |
|-------|--------|
| `onPause()` | `syncEngine.flush()` — push all dirty progress |
| `onResume()` | `syncEngine.pull()` — download manifest, check changes |
| Page turn / scroll | `syncEngine.updateProgress(bookId, chapterIndex, percent)` — debounced upload |
| Chapter switch | `syncEngine.updateProgress(...)` — immediate flush |
| Progress change > 1% | `syncEngine.updateProgress(...)` — immediate flush |
| WebDAV settings saved | `syncEngine.probe()` — test connection |

### Near-Real-Time Progress

Core's WebDAV layout uses HEAD requests for progress polling:

```kotlin
// Reading page timer (every 10-30s)
suspend fun pollProgress(bookId: String) {
    val path = "progress/latest/$bookId.json"
    val response = transport.head(path)
    if (response.etag != localEtag) {
        val latest = transport.pull(path)
        syncEngine.applyRemoteProgress(latest)
    }
}
```

## 9. Security Considerations

| Concern | Mitigation |
|---------|-----------|
| Password at rest | `EncryptedSharedPreferences` (AES-256-GCM, Keystore-backed) |
| Password in transit | HTTPS required (Core's `requireHTTPS` in security constraints) |
| Password in memory | Never log; clear `CharArray` after use |
| Cookie leakage | HTTP-only cookies handled by ScopedCookieJar (not WebDAV) |
| Man-in-the-middle | HTTPS + certificate pinning (optional, future) |
| Self-signed certs | User opt-in with warning (S11 UX concern) |
| Credential export | EncryptedSharedPreferences file is device-bound (Keystore key) |
| Sync data at rest on server | User's own WebDAV server — user controls server security |

## 10. What This Means for Loop Tasks

| Stage | WebDAV State | What's Possible |
|-------|-------------|-----------------|
| S1-S10 | No WebDAV needed | All data is local/fake |
| S11 | `AndroidWebDAVSyncTransport` implemented | Real backup/restore |
| S11 | WebDAV settings UI | User configures server |
| S11 | Background sync via WorkManager | Automatic periodic sync |
| S11 | Conflict UI | User resolves conflicts |

**Deferred to S11**: No WebDAV code should be written until the app has real data (book sources, reading progress, bookshelf) to sync. Fake data has nothing to backup.

## 11. Decision Required

| ID | Question | Recommendation | Status |
|----|----------|---------------|--------|
| BD-010 | WebDAV client library vs manual OkHttp | OkHttp manual (no library) | OPEN |
| BD-027 | Whether to support WebDAV backup/sync | Yes, using strategy above | OPEN |

## 12. References

- Core WebDAV Sync Handoff: `Reader-Core/docs/HANDOFF/WEBDAV_SYNC_HANDOFF.md`
- Core WebDAV Sync Layout: `Reader-Core/docs/architecture/sync/WEBDAV_SYNC_LAYOUT.md`
- Core Sync Scope: `Reader-Core/docs/architecture/sync/SYNC_SCOPE_AND_REQUIREMENTS.md`
- Core Sync Data Model: `Reader-Core/docs/architecture/sync/SYNC_DATA_MODEL_SCHEMA.md`
- Core Sync Conflict Policy: `Reader-Core/docs/architecture/sync/SYNC_CONFLICT_POLICY.md`
- Core Platform Integration Guide: `Reader-Core/docs/architecture/sync/PLATFORM_SYNC_INTEGRATION_GUIDE.md`
- Android HTTP Design: `docs/design/HTTP_ADAPTER_DESIGN.md`
- Android Data Layer Design: `docs/design/DATA_LAYER_DESIGN.md`
- Android EncryptedSharedPreferences: https://developer.android.com/reference/androidx/security/crypto/EncryptedSharedPreferences
- Android WorkManager: https://developer.android.com/topic/libraries/architecture/workmanager
