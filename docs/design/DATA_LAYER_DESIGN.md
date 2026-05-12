# Android Data Layer Design

**Date**: 2026-05-13
**Status**: DRAFT
**Task**: P1-S2-002
**Depends On**: None (design doc only, no code)

---

## 1. Scope

This document defines the Android local persistence strategy for Reader for Android. It does NOT introduce any dependency — it only analyzes tradeoffs to inform a future user decision (BD-005).

## 2. What Needs Persistence

| Data | Size | Mutability | Query Needs | Sync Relevance |
|------|------|------------|-------------|----------------|
| BookSource list | Small (<1000 items) | Low (import/edit) | List, filter by group | Yes (backup) |
| Reading progress | Small (per book) | High (every page turn) | Lookup by book ID | Yes (cloud sync) |
| Chapter cache | Large (HTML/text per chapter) | Low (write once, read many) | Lookup by chapter URL | No (ephemeral) |
| Search history | Tiny (<100 items) | Medium | Recent list | Maybe (backup) |
| User preferences | Tiny (key-value) | Low | Lookup by key | Maybe (backup) |
| Book metadata | Small (per book) | Medium | List, lookup | Yes (backup) |
| Cookie/session data | Small | Medium | Lookup by domain | No (sensitive) |
| Backup metadata | Tiny | Low | List backups | Yes |

## 3. Storage Options

### 3.1 Room (SQLite)

**Pros**:
- Full SQL queries — good for structured data with relations
- Compile-time query verification
- Migration support built-in
- Observable queries via Flow
- Well-suited for BookSource list, book metadata with filtering/sorting

**Cons**:
- Heavier setup (entities, DAOs, database class, migrations)
- Overkill for key-value preferences
- Not ideal for large BLOB storage (chapter cache)
- Requires schema migration planning from day one

### 3.2 DataStore (Preferences + Proto)

**Pros**:
- Simple key-value API for preferences
- Coroutine/Flow-based
- No migrations needed (schema-less)
- Good for user preferences, reading progress (small records)
- Replaces SharedPreferences cleanly

**Cons**:
- No relational queries — can't filter/sort BookSource by group, weight, etc.
- Not for large datasets (>1000 items becomes awkward)
- Proto DataStore requires schema definition, Preferences is untyped
- No joins, no complex queries

### 3.3 File-based (JSON + internal storage)

**Pros**:
- Matches Core JSON-based DTOs exactly — no translation layer
- Simple import/export (just copy JSON files)
- Works offline, no schema migrations
- Good for chapter cache (write HTML to files)

**Cons**:
- No query support — must read all sources into memory to filter
- Manual concurrency management
- No observable queries
- Slower for frequent updates (reading progress)

## 4. Recommended Hybrid Strategy

| Data | Storage | Reason |
|------|---------|--------|
| BookSource list | Room | Needs filtering, sorting, group queries |
| Book metadata (shelf) | Room | Needs relational queries, sorting |
| Reading progress | DataStore (Preferences) | Simple key-value, high-frequency writes |
| User preferences | DataStore (Preferences) | Classic key-value use case |
| Chapter cache | File system (internal cache dir) | Large BLOBs, LRU eviction, no queries needed |
| Search history | DataStore (Preferences) | Small list, simple serialization |
| Cookie/session | Encrypted file or EncryptedSharedPreferences | Small, sensitive |
| Backup metadata | Room | Structured, needs listing/filtering |

## 5. DTO Alignment with Reader-Core

Reader-Core DTOs are Swift structs with Codable conformance. The Android data layer must:

1. **Match field names and types** exactly (within Kotlin types)
2. **Preserve unknownFields** — BookSource has `unknownFields: [String: JSONValue]` for forward compatibility
3. **Support round-trip** — JSON → Kotlin object → JSON must not lose data

### Room Entity example (sketch, NOT implementation)

```kotlin
@Entity(tableName = "book_sources")
data class BookSourceEntity(
    @PrimaryKey val id: String,
    val bookSourceName: String,
    val bookSourceUrl: String?,
    // ... all 20+ fields
    val rawJson: String  // preserve unknownFields + full fidelity
)
```

The `rawJson` column preserves Core JSON fidelity while Room handles query fields.

## 6. Migration Policy

- **S1-S5 (fake data)**: No persistence needed; in-memory only
- **S6 (reader experience)**: Introduce DataStore for preferences + progress; file cache for chapters
- **S9 (local books)**: Introduce Room for book metadata + file system for book content
- **S11 (WebDAV backup)**: Room for backup metadata

Migrations are deferred until the stage that needs them. This avoids premature schema commitment.

## 7. What This Means for Loop Tasks

| Stage | Persistence | Task Impact |
|-------|------------|-------------|
| S1-S5 | In-memory only (fake data) | No storage dependency needed |
| S6 | DataStore + file cache | Add DataStore dependency; design doc already done |
| S9 | Room + DataStore + files | Add Room dependency; use rawJson pattern |
| S11 | Room (backup) | Extend existing schema |

**Key decision**: Do NOT add Room or DataStore in S1-S5. FakeCoreBridge returns hardcoded data. This keeps the early project dependency-light and avoids premature schema design.

## 8. Decision Required

| ID | Question | Default | Blocks |
|----|----------|---------|--------|
| BD-005 | Room vs DataStore (or hybrid) | Hybrid: Room for structured, DataStore for prefs, files for cache | P1-S2-001, P2-S6-* |

The default hybrid strategy is documented above. User must confirm or override before any code that adds these dependencies.

## 9. References

- Reader-Core DTOs: `Core/Sources/ReaderCoreModels/BookSource.swift`
- Reader-Core DTO Freeze: `docs/architecture/DTO_FREEZE_MATRIX.md`
- Reader-Core Scope: `docs/PLANNING/READER_CORE_SCOPE_AND_BOUNDARIES.md` sec 2.2 (Platform Database excluded from Core)
